package im.actor.server.api.http.rest.util

import java.security.MessageDigest

object ThreadLocalMD5 {
  private val local: ThreadLocal[MessageDigest] = new ThreadLocal[MessageDigest]() {
    override protected def initialValue(): MessageDigest =
      MessageDigest.getInstance("MD5")
  }

  def current(): MessageDigest = local.get()
}
