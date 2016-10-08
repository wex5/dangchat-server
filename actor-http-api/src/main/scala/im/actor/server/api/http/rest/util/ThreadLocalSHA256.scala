package im.actor.server.api.http.rest.util

import java.security.MessageDigest

object ThreadLocalSHA256 {
  private val local: ThreadLocal[MessageDigest] = new ThreadLocal[MessageDigest]() {
    override protected def initialValue(): MessageDigest =
      MessageDigest.getInstance("SHA-256")
  }

  def current(): MessageDigest = local.get()
}
