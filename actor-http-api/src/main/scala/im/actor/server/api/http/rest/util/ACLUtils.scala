package im.actor.server.api.http.rest.util

import akka.actor.ActorSystem

/**
 * Created by User on 2016/10/8.
 */
object ACLUtils extends ACLBase with ACLFiles {

  val PasswordMinLength = 8
  val PasswordMaxLength = 160

  type Hash = Array[Byte]
  type Salt = Array[Byte]

  def userAccessHash(authId: Long, userId: Int, accessSalt: String)(implicit s: ActorSystem): Long =
    hashObsolete(s"$authId:$userId:$accessSalt:${secretKey()}")

}
