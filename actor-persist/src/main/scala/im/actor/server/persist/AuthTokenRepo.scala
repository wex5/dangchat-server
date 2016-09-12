package im.actor.server.persist

import java.time.LocalDateTime

import im.actor.server.db.ActorPostgresDriver.api._
import im.actor.server.model.AuthToken

/**
 * 登录Token Table定义类
 * by Lining 2016/8/15
 * @param tag
 */
final class AuthTokenTable(tag: Tag) extends Table[AuthToken](tag, "auth_tokens") {
  def userId = column[String]("user_id", O.PrimaryKey)
  def token = column[String]("token")
  def attempts = column[Int]("attempts")
  def createdAt = column[LocalDateTime]("created_at")

  def * = (userId, token, attempts, createdAt) <> (AuthToken.tupled, AuthToken.unapply)
}

/**
 * 登录Token仓储类
 * by Lining 2016/8/15
 * @param tag
 */
object AuthTokenRepo {
  val tokens = TableQuery[AuthTokenTable]

  def create(userId: String, token: String) =
    tokens += AuthToken(userId, token)

  def createOrUpdate(userId: String, token: String) =
    tokens.insertOrUpdate(AuthToken(userId, token))

  def findByUserId(userId: String) =
    tokens.filter(t ⇒ t.userId === userId).result.headOption

  def findToken(userId: String) = {
    sql"""select token from auth_tokens where user_id=$userId""".as[String].headOption
  }

  def findByToken(token: String) =
    tokens.filter(t ⇒ t.token === token).result.headOption

  def incrementAttempts(userId: String, currentValue: Int) =
    tokens.filter(_.userId === userId).map(_.attempts).update(currentValue + 1)

}