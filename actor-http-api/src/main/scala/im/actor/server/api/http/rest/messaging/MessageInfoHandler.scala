package im.actor.server.api.http.rest.messaging

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.google.protobuf.CodedInputStream
import im.actor.server.db.DbExtension
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import im.actor.server.api.http.HttpHandler
import im.actor.server.api.http.json.JsonFormatters
import im.actor.server.api.http.rest.util.SecurityManager
import im.actor.server.persist.HistoryMessageRepoPatches

import scala.collection.mutable.ListBuffer

/**
 * Created by User on 2017/2/23.
 */
private[http] final class MessageInfoHandler()(implicit system: ActorSystem) extends HttpHandler with PlayJsonSupport {

  import JsonFormatters._

  private lazy val db = DbExtension(system).db
  private lazy val config = system.settings.config

  override def routes: Route =
    defaultVersion {
      pathPrefix("dangchat-messageInfo") {
        pathEnd {
          post {
            complete("The post http request is not processed!")
          }
        } ~
          get {
            //获取request对象 request =>
            path(Segment) { peerId ⇒
              //单个获取参数：ctx.uri.query.get("apiPwd")
              parameters('apiPassword) { apiPassword ⇒
                if (SecurityManager.checkApiPassword(apiPassword, config)) {
                  //查找历史消息
                  //val searchService: SearchServiceImpl = new SearchServiceImpl()
                  parameters('peerType, 'userId, 'date, 'limit) { (peerType, userId, date, limit) ⇒
                    complete(OK → getLoadHistoryResult(peerId.toInt, peerType.toInt, userId.toInt, date, limit.toInt))
                  }
                } else {
                  complete("Api password错误！")
                }
              }
            }
          } ~
          put {
            complete("The put http request is not processed!")
          } ~
          delete {
            complete("The delete http request is not processed!")
          }
      }
    }

  private def getLoadHistoryResult(peerId: Int, peerType: Int, userId: Int, date: String, limit: Int) = {
    var historyOwner = userId
    //如果是讨论组，将historyOwer设为0
    if (peerType == 2) {
      val groupIsShared = scala.concurrent.Await.result(
        db.run(HistoryMessageRepoPatches.groupIsShared(peerId)), scala.concurrent.duration.Duration.Inf
      )
      if (groupIsShared.getOrElse(false)) historyOwner = 0
    }

    val historyMessages = scala.concurrent.Await.result(
      db.run(HistoryMessageRepoPatches.loadHistoryMessages(peerId, historyOwner, userId, date, limit)),
      scala.concurrent.duration.Duration.Inf
    )
    var buffer = new ListBuffer[HistoryMessagePatchesResult]
    for (message ← historyMessages) {
      val in = CodedInputStream.newInstance(message.messageContentData)

      buffer += HistoryMessagePatchesResult(
        userId = message.userId,
        peerType = message.peerType,
        peerId = message.peerId,
        date = message.date,
        senderUserId = message.senderUserId,
        randomId = message.randomId,
        messageContentHeader = message.messageContentHeader,
        messageContent = im.actor.server.api.http.util.MessageContentUtils.getMessageContent(
          message.messageContentData, message.messageContentHeader
        ),
        deletedAt = message.deletedAt,
        senderName = message.senderName,
        senderNickName = message.senderNickName,
        isOut = message.isOut
      )
    }
    buffer
  }

}