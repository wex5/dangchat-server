package im.actor.server.api.rpc.service.users

import java.time.{ LocalDateTime, ZoneOffset }

import akka.actor._
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
                    userExt.editLocalName(client.userId, userId, Some(validName))
                  case None ⇒
                    for {
                      optPhone ← db.run(UserPhoneRepo.findByUserId(userId).headOption)
                      optEmail ← db.run(UserEmailRepo.findByUserId(userId).headOption)
                      seqstate ← userExt.addContact(
                        userId = client.userId,
                        contactUserId = userId,
                        localName = Some(validName),
                        phone = optPhone map (_.number),
                        email = optEmail map (_.email)
                      )
                    } yield seqstate
                }

                for {
                  seqstate ← seqstateF
                } yield Ok(ResponseSeq(seqstate.seq, seqstate.state.toByteArray))
              } else {
                Future.successful(Error(CommonRpcErrors.InvalidAccessHash))
              }
            case None ⇒ Future.successful(Error(CommonRpcErrors.UserNotFound))
          }
        case Xor.Left(err) ⇒ Future.successful(Error(UserErrors.NameInvalid))
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
      withUserOutPeersF(userPeers) {
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
   * 处理用户注册
   *
   * @param name
   * @param nickname
   * @param random
   * @return
   */
  private def handleRegisterUser(name: String, nickname: String, random: ThreadLocalSecureRandom): Future[ApiRegisteredUser] = {
    val optUser = scala.concurrent.Await.result(db.run(UserRepo.findByNickname(nickname)), Duration.Inf)
    optUser match {
      case Some(user) ⇒
        //userExt.getApiRegisteredStruct(user.id, nickname)
        Future.successful(ApiRegisteredUser(user.id, nickname, false))
      case None ⇒
        val userModel = newUser(name, nickname)
        db.run(UserRepo.create(userModel))
        db.run(UserPhoneRepo.create(random.nextInt(), userModel.id, ACLUtils.nextAccessSalt(random), getPhoneNumber, "Mobile phone"))
        //userExt.getApiRegisteredStruct(userModel.id, nickname)
        Future.successful(ApiRegisteredUser(userModel.id, nickname, true))
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
    {
      val userMap = (userIds zip userNames)
      val rng = ThreadLocalSecureRandom.current()
      for {
        users ← Future.sequence(userMap map (kv ⇒ handleRegisterUser(kv._2, kv._1, rng)))
      } yield Ok(ResponseRegisterUsers(users.toVector))
    }

}
