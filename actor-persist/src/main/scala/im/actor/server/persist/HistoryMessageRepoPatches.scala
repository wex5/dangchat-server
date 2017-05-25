package im.actor.server.persist

import com.github.tototoshi.slick.PostgresJodaSupport._
import slick.driver.PostgresDriver.api._
import slick.jdbc.GetResult
import im.actor.server.model.HistoryMessagePatches

/**
 * Created by User on 2017/2/27.
 */
object HistoryMessageRepoPatches {

  def loadHistoryMessages(peerId: Int, historyOwner: Int, userId: Int, date: String, limit: Int) = {
    implicit val getMessageResult: GetResult[HistoryMessagePatches] = GetResult(r ⇒
      HistoryMessagePatches(
        userId = r.nextInt,
        peerType = r.nextInt,
        peerId = r.nextInt,
        date = getDatetimeResult(r),
        senderUserId = r.nextInt,
        randomId = r.nextLong,
        messageContentHeader = r.nextInt,
        messageContentData = r.nextBytes,
        deletedAt = getDatetimeOptionResult(r),
        senderName = r.nextString(),
        senderNickName = r.nextString,
        isOut = r.nextBoolean
      ))

    val sb: StringBuilder = new StringBuilder(1024)
    sb.append("SELECT m.*,u.name as sender_name,u.nickname,(")
    sb.append(userId).append("=m.sender_user_id) as is_out ")
    sb.append("FROM history_messages m INNER JOIN users u ON m.sender_user_id=u.id ")
    sb.append("WHERE m.peer_id=").append(peerId).append(" AND m.date<'").append(date).append("'")
    if (historyOwner > 0) {
      sb.append(" AND m.user_id=").append(userId)
    }
    sb.append(" ORDER BY date DESC LIMIT ").append(limit)
    val querySql = sb.toString()
    sql"""#$querySql""".as[HistoryMessagePatches]
  }

  def groupIsShared(groupId: Int) = {
    sql"""SELECT is_share FROM groups WHERE id=$groupId""".as[Boolean].headOption
  }

}
