package im.actor.server.api.rpc.service.users

import java.time.{ LocalDateTime, ZoneOffset }

import akka.actor._
import akka.http.scaladsl.util.FastFuture
import akka.util.Timeout
import cats.data.Xor
import im.actor.api.rpc.DBIOResultRpc._
import im.actor.api.rpc._
import im.actor.api.rpc.PeerHelpers._
import im.actor.api.rpc.misc.ResponseSeq
import im.actor.api.rpc.peers.ApiUserOutPeer
import im.actor.api.rpc.users.{ ApiRegisteredUser, ResponseLoadFullUsers, ResponseRegisterUsers, UsersService }
import im.actor.server.acl.ACLUtils
import im.actor.server.db.DbExtension
import im.actor.server.persist.{ UserEmailRepo, UserPhoneRepo, UserRepo }
import im.actor.server.persist.contact.UserContactRepo
import im.actor.server.user.UserExtension
import im.actor.util.misc.StringUtils
import im.actor.server.model._
import im.actor.util.ThreadLocalSecureRandom
import im.actor.util.misc.IdUtils._
import slick.dbio.{ DBIO ⇒ _, _ }
import slick.driver.PostgresDriver.api._

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

object UserErrors {
  val NameInvalid = RpcError(400, "NAME_INVALID", "Invalid name. Valid nickname should not be empty and should consist of printable characters", false, None)
}

final class UsersServiceImpl(implicit actorSystem: ActorSystem) extends UsersService {

  override implicit val ec: ExecutionContext = actorSystem.dispatcher
  private implicit val timeout = Timeout(10.seconds)

  private val db: Database = DbExtension(actorSystem).db
  private val userExt = UserExtension(actorSystem)

  override def doHandleEditUserLocalName(userId: Int, accessHash: Long, name: String, clientData: ClientData): Future[HandlerResult[ResponseSeq]] = {
    authorized(clientData) { implicit client ⇒
      StringUtils.validName(name) match {
        case Xor.Right(validName) ⇒
          db.run(UserRepo.find(userId)) flatMap {
            case Some(user) ⇒
              if (accessHash == ACLUtils.userAccessHash(client.authId, user)) {
                val seqstateF = db.run(UserContactRepo.find(client.userId, userId)) flatMap {
                  case Some(contact) ⇒
                    userExt.editLocalName(client.userId, client.authId, userId, Some(validName))
                  case None ⇒
                    for {
                      optPhone ← db.run(UserPhoneRepo.findByUserId(userId).headOption)
                      optEmail ← db.run(UserEmailRepo.findByUserId(userId).headOption)
                      seqState ← userExt.addContact(
                        userId = client.userId,
                        authId = client.authId,
                        contactUserId = userId,
                        localName = Some(validName),
                        phone = optPhone map (_.number),
                        email = optEmail map (_.email)
                      )
                    } yield seqState
                }

                for {
                  seqState ← seqstateF
                } yield Ok(ResponseSeq(seqState.seq, seqState.state.toByteArray))
              } else {
                FastFuture.successful(Error(CommonRpcErrors.InvalidAccessHash))
              }
            case None ⇒ FastFuture.successful(Error(CommonRpcErrors.UserNotFound))
          }
        case Xor.Left(err) ⇒ FastFuture.successful(Error(UserErrors.NameInvalid))
      }
    }
  }

  /**
   * Loading Full User information
   *
   * @param userPeers User's peers to load. Should be non-empty
   */
  override protected def doHandleLoadFullUsers(
    userPeers:  IndexedSeq[ApiUserOutPeer],
    clientData: ClientData
  ): Future[HandlerResult[ResponseLoadFullUsers]] =
    authorized(clientData) { implicit client ⇒
      withUserOutPeers(userPeers) {
        for {
          fullUsers ← Future.sequence(userPeers map (u ⇒ userExt.getApiFullStruct(u.userId, client.userId, client.authId)))
        } yield Ok(ResponseLoadFullUsers(fullUsers.toVector))
      }
    }

  /**
   * 创建用户对象
   *
   * @param name 用户名
   * @param nickName 用户昵称
   * @return
   */
  private def newUser(name: String, nickName: String): User = {
    val rng = ThreadLocalSecureRandom.current()
    val user = User(
      id = nextIntId(rng),
      accessSalt = ACLUtils.nextAccessSalt(rng),
      name = name,
      countryCode = "CN",
      sex = NoSex,
      state = UserState.Registered,
      createdAt = LocalDateTime.now(ZoneOffset.UTC),
      external = None,
      nickname = Some(nickName)
    )
    user
  }

  /**
   * 生成电话号码
   *
   * @return
   */
  private def getPhoneNumber(): Long = {
    val date = new java.util.Date()
    val formatter = new java.text.SimpleDateFormat("yyyyMMddHHmmss")
    //val formatDate = "66" + formatter.format(date)
    val formatDate = new scala.util.Random().nextInt(999).toString + formatter.format(date)
    formatDate.toLong
  }

  /**
   * 处理用户注册 by Lining 2016/8/17
   *
   * @param name
   * @param nickname
   * @param random
   * @return
   */
  private def handleRegisterUser(client: AuthorizedClientData, name: String, nickname: String,
                                 ownerUserId: Int, random: ThreadLocalSecureRandom): Future[ApiRegisteredUser] = {
    for {
      optUser ← db.run(UserRepo.findByNickname(nickname))
      result ← optUser match {
        case Some(user) ⇒
          addContact(client, user.id)
          FastFuture.successful(ApiRegisteredUser(user.id, nickname, false))
        case None ⇒
          val userModel = addUser(name, nickname)
          addContact(client, userModel.id)
          FastFuture.successful(ApiRegisteredUser(userModel.id, nickname, true))
      }
    } yield result
  }

  private def addUser(name: String, nickName: String): User = {
    val user = newUser(name, nickName)
    handleUserCreate(user)
    fromDBIO(UserRepo.create(user))
    user
  }

  //二次开发记录：添加新方法  by Lining
  private def handleUserCreate(user: User) = {
    DBIO.from(userExt.create(user.id, user.accessSalt, user.nickname, user.name, user.countryCode, im.actor.api.rpc.users.ApiSex(user.sex.toInt), isBot = false))
    fromDBIO(im.actor.server.persist.AvatarDataRepo.create(AvatarData.empty(AvatarData.OfUser, user.id.toLong)))
    DBIO.from(userExt.addPhone(user.id, getPhoneNumber()))
  }

  //添加联系人 by Lining 2016/8/19
  private def addContact(client: AuthorizedClientData, contactUserId: Int): Unit = {
    val contactExists = scala.concurrent.Await.result(
      db.run(UserContactRepo.exists(ownerUserId = client.userId, contactUserId = contactUserId)),
      scala.concurrent.duration.Duration.Inf
    )
    if (!contactExists) {
      val action = for {
        optPhone ← fromDBIO(UserPhoneRepo.findByUserId(contactUserId).headOption)
        optEmail ← fromDBIO(UserEmailRepo.findByUserId(contactUserId).headOption)
        seqState ← fromFuture(userExt.addContact(
          userId = client.userId,
          authId = client.authId,
          contactUserId = contactUserId,
          localName = None,
          phone = optPhone map (_.number),
          email = optEmail map (_.email)
        ))
      } yield ()
      db.run(action.value)
    }
  }

  /**
   * 注册用户
   *
   * @param userIds   User's Ids
   * @param userNames   User's names
   * @param clientData
   * @return
   */
  override protected def doHandleRegisterUsers(
    userIds:    IndexedSeq[String],
    userNames:  IndexedSeq[String],
    clientData: ClientData
  ): Future[HandlerResult[ResponseRegisterUsers]] =
    authorized(clientData) { implicit client ⇒
      val userMap = (userIds zip userNames)
      val rng = ThreadLocalSecureRandom.current()
      for {
        users ← Future.sequence(userMap map (kv ⇒ handleRegisterUser(client, kv._2, kv._1, client.userId, rng)))
      } yield Ok(ResponseRegisterUsers(users.toVector))
    }

}
