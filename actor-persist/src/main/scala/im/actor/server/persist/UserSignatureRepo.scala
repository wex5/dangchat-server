package im.actor.server.persist

import im.actor.server.model.UserSignature
import slick.driver.PostgresDriver.api._

/**
 * 用户签名仓储
 * 用户签名在访问文件资源（文本、图片、视频、音频）时使用
 * Created by Lining on 2016/6/6.
 */

final class UserSignatureTable(tag: Tag) extends Table[UserSignature](tag, "user_signatures") {
  def userId = column[Int]("user_id", O.PrimaryKey)
  def signature = column[Array[Byte]]("signature")
  def expire = column[Int]("expire")
  def baseUrl = column[String]("base_url")

  def * = (userId, signature, expire, baseUrl) <> (UserSignature.tupled, UserSignature.unapply)
}

object UserSignatureRepo {
  val userSignatures = TableQuery[UserSignatureTable]

  def byUserId(userId: Rep[Int]) = userSignatures filter (_.userId === userId)

  def create(userId: Int, signature: Array[Byte], expire: Int, baseUrl: String) =
    userSignatures += UserSignature(userId, signature, expire, baseUrl)

  def createOrUpdate(userSignature: UserSignature) =
    userSignatures.insertOrUpdate(userSignature)
}
