package im.actor.server.model

/**
 * 用户机器人类
 * by Lining 2016/8/29
 * @param id
 * @param nickname
 * @param name
 * @param token
 */
@SerialVersionUID(1L)
case class UserBot(
  id:       Int,
  nickname: String,
  name:     String,
  token:    String
)