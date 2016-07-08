package im.actor.server.model

/**
 * Created by Lining on 2016/6/6.
 */
@SerialVersionUID(1L)
case class UserSignature(
  userId:    Int,
  signature: Array[Byte],
  expire:    Int,
  baseUrl:   String
)