package im.actor.server.api.http.rest.users

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import im.actor.server.db.DbExtension
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import im.actor.server.api.http.HttpHandler
import im.actor.server.api.http.json.{ JsonFormatters, UserInfo }
import im.actor.server.api.http.rest.util.SecurityManager
import im.actor.server.api.http.rest.util.ACLUtils
import im.actor.server.model.User
import im.actor.server.persist.{ AuthIdRepo, UserRepo }

/**
 * Created by User on 2016/10/8.
 */
private[http] final class UsersHttpHandler()(implicit system: ActorSystem) extends HttpHandler with PlayJsonSupport {

  import JsonFormatters._

  private lazy val db = DbExtension(system).db
  private lazy val config = system.settings.config
  private lazy val secretKey = config.getString("secret")

  override def routes: Route =
    defaultVersion {
      pathPrefix("dangchat-users") {
        pathEnd {
          post {
            complete("The post http request is not processed!")
          }
        } ~
          get {
            //获取request对象 request =>
            parameters('apiPassword) { apiPassword ⇒
              if (SecurityManager.checkApiPassword(apiPassword, config)) {
                //查询所有用户
                val userList = scala.concurrent.Await.result(
                  db.run(UserRepo.fetchPeople), scala.concurrent.duration.Duration.Inf
                )
                val authIds = scala.concurrent.Await.result(
                  db.run(AuthIdRepo.findActiveAuthIds), scala.concurrent.duration.Duration.Inf
                )
                //val list = userList.map((user: User) ⇒ UserInfo(user.id, user.name, getUserAccessHash(authIds, user.id, user.accessSalt)))
                val list = userList.map((user: User) ⇒ UserInfo(user.id, user.name, 0, user.nickname.getOrElse("")))
                complete(OK → list)
              } else {
                complete("Api password错误！")
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

  /**
   * 得到用户的AccessHash
   *
   * @param authIds    包含AuthId的集合
   * @param userId     用户Id
   * @param accessSalt 用户的AccessSalt
   * @return 用户的AccessHash
   */
  private def getUserAccessHash(authIds: Seq[im.actor.server.model.AuthId], userId: Int, accessSalt: String): Long = {
    val filterResult = authIds.filter(a ⇒ a.userId.getOrElse(0) == userId)
    if (filterResult.length > 0) {
      ACLUtils.userAccessHash(filterResult.head.id, userId, accessSalt)
    } else 0
  }

}
