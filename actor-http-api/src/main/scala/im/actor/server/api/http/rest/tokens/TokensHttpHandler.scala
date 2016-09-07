package im.actor.server.api.http.rest.tokens

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import im.actor.server.api.http.HttpHandler
import im.actor.server.persist.AuthTokenRepo
import im.actor.util.ThreadLocalSecureRandom
import im.actor.server.api.http.rest.util.SecurityManager

/**
 * 访问令牌Http处理类
 * by Lining 2016/8/24
 *
 * @param system
 */
private[http] final class TokensHttpHandler()(implicit system: ActorSystem) extends HttpHandler {

  private lazy val config = system.settings.config

  override def routes: Route =
    defaultVersion {
      pathPrefix("tokens") {
        pathEnd {
          post {
            complete("The post http request is not processed!")
          }
        } ~
          path(Segment) { userId ⇒
            get { //获取request对象 request =>
              //单个获取参数：ctx.uri.query.get("apiPwd")
              parameters('apiPassword) { apiPassword ⇒
                if (SecurityManager.checkApiPassword(apiPassword, config)) {
                  //complete(s"The userName is '$userName' and the apiPwd is '$apiPwd'")
                  val token = newToken(userId)
                  complete(token)
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
    }

  /**
   * 创建Token
   * @param userId
   * @return
   */
  private def newToken(userId: String): String = {
    var rng = ThreadLocalSecureRandom.current();
    val token = rng.nextLong().toString()
    val db = im.actor.server.db.DbExtension(system).db
    db.run(AuthTokenRepo.createOrUpdate(userId, token))
    token
  }

}
