package im.actor.server.api.http.rest.groups

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import im.actor.server.db.DbExtension
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import im.actor.server.api.http.HttpHandler
import im.actor.server.api.http.json.{ JsonFormatters, DiscussionGroupInfo }
import im.actor.server.persist.DiscussionGroupRepo
import im.actor.server.api.http.rest.util.SecurityManager

/**
 * Created by User on 2016/10/8.
 */
private[http] final class DiscussionGroupsHttpHandler()(implicit system: ActorSystem) extends HttpHandler with PlayJsonSupport {

  import JsonFormatters._

  private lazy val db = DbExtension(system).db
  private lazy val config = system.settings.config

  override def routes: Route =
    defaultVersion {
      pathPrefix("dangchat-discussionGroups") {
        pathEnd {
          post {
            complete("The post http request is not processed!")
          }
        } ~
          get {
            //获取request对象 request =>
            path("""\d+""".r) { discussionGroupId ⇒
              //单个获取参数：ctx.uri.query.get("apiPwd")
              parameters('apiPassword) { apiPassword ⇒
                if (SecurityManager.checkApiPassword(apiPassword, config)) {
                  //根据讨论组Id查询讨论组
                  val optDiscussionGroup = scala.concurrent.Await.result(
                    db.run(DiscussionGroupRepo.findById(discussionGroupId)), scala.concurrent.duration.Duration.Inf
                  )
                  optDiscussionGroup match {
                    case Some(discussionGroup) ⇒ complete(OK → DiscussionGroupInfo(discussionGroup.id, discussionGroup.groupId))
                    case None                  ⇒ complete(OK → DiscussionGroupInfo("", 0))
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
