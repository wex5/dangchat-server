package im.actor.server.dialog

import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import im.actor.api.rpc.PeersImplicits
import im.actor.api.rpc.counters.{ ApiAppCounters, UpdateCountersChanged }
import im.actor.api.rpc.messaging._
import im.actor.server.db.DbExtension
import im.actor.server.messaging.PushText
import im.actor.server.model.Peer
import im.actor.server.sequence.{ PushData, PushRules, SeqState, SeqUpdatesExtension }
import im.actor.server.user.UserExtension

import scala.concurrent.{ ExecutionContext, Future }

//引入justep消息（推送）类库  by Lining 2016-6-22
import com.justep.message.dispatcher._

//default extension
final class ActorDelivery()(implicit val system: ActorSystem)
  extends DeliveryExtension
  with PushText
  with PeersImplicits {

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val seqUpdatesExt: SeqUpdatesExtension = SeqUpdatesExtension(system)
  private val userExt = UserExtension(system)
  private val dialogExt = DialogExtension(system)

  override def receiverDelivery(
    receiverUserId: Int,
    senderUserId:   Int,
    peer:           Peer,
    randomId:       Long,
    timestamp:      Long,
    message:        ApiMessage,
    isFat:          Boolean
  ): Future[Unit] = {
    val receiverUpdate = UpdateMessage(
      peer = peer.asStruct,
      senderUserId = senderUserId,
      date = timestamp,
      randomId = randomId,
      message = message,
      attributes = None,
      quotedMessage = None
    )

    for {
      senderName ← userExt.getName(senderUserId, receiverUserId)
      (pushText, censoredPushText) ← getPushText(peer, receiverUserId, senderName, message)
      _ ← seqUpdatesExt.deliverSingleUpdate(
        receiverUserId,
        receiverUpdate,
        PushRules(isFat = isFat).withData(
          PushData()
            .withText(pushText)
            .withCensoredText(censoredPushText)
            .withPeer(peer)
        ),
        deliveryId = s"msg_${peer.toString}_$randomId"
      )
    } yield {
      //发送推送  by Lining 2016-6-22
      sendPush(senderName, pushText, censoredPushText, receiverUserId)
      ()
    }
  }

  /**
   * 发送推送
   * by Lining 2016-6-22
   *
   * @param senderName
   * @param pushText
   * @param censoredPushText
   * @param receiverUserId
   */
  private def sendPush(senderName: String, pushText: String, censoredPushText: String, receiverUserId: Int): Unit = {
    val serverUri = scala.util.Try(system.settings.config.getString("services.justep.push.server-uri"))
    val userName = scala.util.Try(system.settings.config.getString("services.justep.push.user"))
    val password = scala.util.Try(system.settings.config.getString("services.justep.push.password"))
    (serverUri, userName, password) match {
      case (scala.util.Success(v1), scala.util.Success(v2), scala.util.Success(v3)) ⇒
        val message = new Message(pushText, "", null)
        val personIds = new java.util.HashSet[String]()
        //得到X5用户Id
        DbExtension(system).db.run(im.actor.server.persist.UserRepo.findNickname(receiverUserId)).map {
          case Some(x5UserId) ⇒
            DbExtension(system).db.run(im.actor.server.persist.ClientStateRepo.find(receiverUserId)).map {
              case Some(clientState) ⇒
                if (clientState.state == 0) {
                  //app在pause状态，发送推送通知
                  personIds.add(x5UserId.get)
                }
              case _ ⇒
                //没有app状态，默认发送推送通知
                personIds.add(x5UserId.get)
            }
            MessageDispatcherFactory.createMessageDispatcher(v1, v2, v3).sendMessage(message, personIds);
          case _ ⇒
        }
      case _ ⇒
    }
  }

  override def sendCountersUpdate(userId: Int): Future[Unit] =
    for {
      counter ← dialogExt.getUnreadTotal(userId)
      _ ← sendCountersUpdate(userId, counter)
    } yield ()

  override def sendCountersUpdate(userId: Int, counter: Int): Future[Unit] = {
    val counterUpdate = UpdateCountersChanged(ApiAppCounters(Some(counter)))
    seqUpdatesExt.deliverSingleUpdate(userId, counterUpdate, reduceKey = Some("counters_changed")) map (_ ⇒ ())
  }

  override def senderDelivery(
    senderUserId:  Int,
    senderAuthSid: Int,
    peer:          Peer,
    randomId:      Long,
    timestamp:     Long,
    message:       ApiMessage,
    isFat:         Boolean
  ): Future[SeqState] = {
    val apiPeer = peer.asStruct
    val senderUpdate = UpdateMessage(
      peer = apiPeer,
      senderUserId = senderUserId,
      date = timestamp,
      randomId = randomId,
      message = message,
      attributes = None,
      quotedMessage = None
    )

    val senderClientUpdate = UpdateMessageSent(apiPeer, randomId, timestamp)

    seqUpdatesExt.deliverMappedUpdate(
      userId = senderUserId,
      default = Some(senderUpdate),
      custom = Map(senderAuthSid → senderClientUpdate),
      pushRules = PushRules(isFat = isFat, excludeAuthSids = Seq(senderAuthSid)),
      deliveryId = s"msg_${peer.toString}_$randomId"
    )
  }

  override def notifyReceive(userId: Int, peer: Peer, date: Long, now: Long): Future[Unit] = {
    val update = UpdateMessageReceived(peer.asStruct, date, now)
    userExt.broadcastUserUpdate(
      userId,
      update,
      pushText = None,
      isFat = false,
      reduceKey = Some(s"receive_${peer.toString}"),
      deliveryId = None
    ) map (_ ⇒ ())
  }

  override def notifyRead(userId: Int, peer: Peer, date: Long, now: Long): Future[Unit] = {
    val update = UpdateMessageRead(peer.asStruct, date, now)
    seqUpdatesExt.deliverSingleUpdate(
      userId = userId,
      update = update,
      reduceKey = Some(s"read_${peer.toString}")
    ) map (_ ⇒ ())
  }

  override def read(readerUserId: Int, readerAuthSid: Int, peer: Peer, date: Long, unreadCount: Int): Future[Unit] =
    for {
      _ ← seqUpdatesExt.deliverSingleUpdate(
        userId = readerUserId,
        update = UpdateMessageReadByMe(peer.asStruct, date, Some(unreadCount)),
        reduceKey = Some(s"read_by_me_${peer.toString}")
      )
    } yield ()

}
