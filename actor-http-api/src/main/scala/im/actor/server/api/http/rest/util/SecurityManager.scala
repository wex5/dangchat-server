package im.actor.server.api.http.rest.util

import com.typesafe.config.Config

/**
 * 安全管理器
 * Created by Lining on 2016/9/6.
 */
object SecurityManager {

  /**
   * 检查API访问口令
   *
   * @param password
   * @param config
   * @return
   */
  def checkApiPassword(password: String, config: Config): Boolean = {
    val apiPassword = scala.util.Try(config.getString("services.justep.rest.password"))
    apiPassword match {
      case scala.util.Success(p) ⇒ return p == password
      case _                     ⇒ return password == "justep-dangchat"
    }
  }

}
