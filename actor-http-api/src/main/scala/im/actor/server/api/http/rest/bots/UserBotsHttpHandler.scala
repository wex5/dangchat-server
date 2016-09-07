package im.actor.server.api.http.rest.bots

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import im.actor.server.api.http.HttpHandler
import im.actor.server.persist.UserBotRepo
import im.actor.server.db.DbExtension
import im.actor.server.api.http.json.{ JsonFormatters, UserBotInfo }
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import im.actor.server.model.UserBot
import im.actor.server.api.http.rest.util.SecurityManager

/**
 * Created by User on 2016/8/29.
 */
private[http] final class UserBotsHttpHandler()(implicit system: ActorSystem) extends HttpHandler with PlayJsonSupport {

  import JsonFormatters._

  private lazy val db = DbExtension(system).db
  private lazy val config = system.settings.config

  override def routes: Route =
    defaultVersion {
      pathPrefix("dangchat-bots") {
        pathEnd {
          post {
            complete("The post http request is not processed!")
          }
        } ~
          get { //获取request对象 request =>
            path("""\d+""".r) { botName ⇒
              //单个获取参数：ctx.uri.query.get("apiPwd")
              parameters('apiPassword) { apiPassword ⇒
                if (SecurityManager.checkApiPassword(apiPassword, config)) {
                  //按照name查询User Bots
                  val optBot = scala.concurrent.Await.result(
                    db.run(UserBotRepo.findByName(botName)), scala.concurrent.duration.Duration.Inf
                  )
                  optBot match {
                    case Some(bot) ⇒ complete(OK → UserBotInfo(bot.id, bot.nickname, bot.name, bot.token))
                    case None      ⇒ complete(OK → UserBotInfo(0, "", "", ""))
                  }
                } else {
                  complete("Api password错误！")
                }
              }
            } ~
              path("") {
                parameters('apiPassword) { apiPassword ⇒
                  if (SecurityManager.checkApiPassword(apiPassword, config)) {
                    //查询所有User Bot
                    val botList = scala.concurrent.Await.result(
                      db.run(UserBotRepo.fetchAll()), scala.concurrent.duration.Duration.Inf
                    )
                    val list = botList.map((bot: UserBot) ⇒ UserBotInfo(bot.id, bot.nickname, bot.name, bot.token))
                    complete(OK → list)
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
