package im.actor.server.api.http.rest.groups

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import im.actor.server.db.DbExtension
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import im.actor.server.api.http.HttpHandler
import im.actor.server.api.http.json.JsonFormatters
import im.actor.server.api.http.rest.util.SecurityManager
import im.actor.server.model.GroupDTO
import im.actor.server.persist.GroupDTORepo
import im.actor.server.persist.GroupMemberDTORepo

/**
 * Created by User on 2017/2/23.
 */
private[http] final class GroupInfoHandler()(implicit system: ActorSystem) extends HttpHandler with PlayJsonSupport {

  import JsonFormatters._

  private lazy val db = DbExtension(system).db
  private lazy val config = system.settings.config

  override def routes: Route =
    defaultVersion {
      pathPrefix("dangchat-groupInfo") {
        pathEnd {
          post {
            complete("The post http request is not processed!")
          }
        } ~
          get {
            //获取request对象 request =>
            path(Segment) { groupId ⇒
              //单个获取参数：ctx.uri.query.get("apiPwd")
              parameters('apiPassword) { apiPassword ⇒
                if (SecurityManager.checkApiPassword(apiPassword, config)) {
                  //根据群组Id查询群组
                  var optGroupDTO: Option[GroupDTO] = null
                  optGroupDTO = scala.concurrent.Await.result(
                    db.run(GroupDTORepo.findById(Integer.parseInt(groupId))), scala.concurrent.duration.Duration.Inf
                  )
                  optGroupDTO match {
                    case Some(groupDTO) ⇒
                      val groupMemberDTOs = scala.concurrent.Await.result(
                        db.run(GroupMemberDTORepo.findByGroupId(Integer.parseInt(groupId))), scala.concurrent.duration.Duration.Inf
                      )
                      complete(OK → im.actor.server.api.http.json.GroupDTOInfo(groupDTO.groupId, groupDTO.groupName, groupDTO.creatorUserId, groupDTO.about, groupMemberDTOs))
                    case None ⇒ complete(OK → im.actor.server.api.http.json.GroupDTOInfo(0, "", 0, "", null))
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

}
