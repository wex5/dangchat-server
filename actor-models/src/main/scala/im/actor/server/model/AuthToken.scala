package im.actor.server.model

import java.time.{ LocalDateTime, ZoneOffset }

/**
 * 登录验证Token类
 * by Lining 2016/8/25
 * @param userId
 * @param token
 * @param attempts
 * @param createdAt
 */
@SerialVersionUID(1L)
case class AuthToken(
  userId:    String,
  token:     Long,
  attempts:  Int           = 0,
  createdAt: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
)
