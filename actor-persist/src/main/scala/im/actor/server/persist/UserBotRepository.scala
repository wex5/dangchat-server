package im.actor.server.persist

import im.actor.server.db.ActorPostgresDriver.api._
import im.actor.server.model.UserBot

/**
 * 用户机器人Table定义类
 * by Lining 2016/8/29
 * @param tag
 */
final class UserBotTable(tag: Tag) extends Table[UserBot](tag, "user_bots") {
  def id = column[Int]("id", O.PrimaryKey)
  def nickname = column[String]("nickname")
  def name = column[String]("name")
  def token = column[String]("token")

  def * = (id, nickname, name, token) <> (UserBot.tupled, UserBot.unapply)
}

/**
 * 登录Token仓储类
 * by Lining 2016/8/29
 */
object UserBotRepo {
  val bots = TableQuery[UserBotTable]

  def create(id: Int, nickname: String, name: String, token: String) =
    bots += UserBot(id, nickname, name, token)

  def findByName(name: String) =
    bots.filter(b ⇒ b.name === name).result.headOption

  def fetchAll() = bots.result
}