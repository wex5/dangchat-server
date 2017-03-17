package im.actor.server.persist

import com.github.tototoshi.slick.PostgresJodaSupport._
import im.actor.server.model.{ HistoryMessage, Peer, PeerType }
import im.actor.server.persist.dialog.DialogRepo
import org.joda.time.DateTime
import slick.dbio.Effect.{ Read, Write }
import slick.driver.PostgresDriver
import slick.driver.PostgresDriver.api._
import slick.jdbc.GetResult
import slick.profile.{ FixedSqlAction, FixedSqlStreamingAction, SqlAction, SqlStreamingAction }

final class HistoryMessageTable(tag: Tag) extends Table[HistoryMessage](tag, "history_messages") {
  def userId = column[Int]("user_id", O.PrimaryKey)

  def peerType = column[Int]("peer_type", O.PrimaryKey)

  def peerId = column[Int]("peer_id", O.PrimaryKey)

  def date = column[DateTime]("date", O.PrimaryKey)

  def senderUserId = column[Int]("sender_user_id", O.PrimaryKey)

  def randomId = column[Long]("random_id", O.PrimaryKey)

  def messageContentHeader = column[Int]("message_content_header")

  def messageContentData = column[Array[Byte]]("message_content_data")

  def deletedAt = column[Option[DateTime]]("deleted_at")

  def * = (userId, peerType, peerId, date, senderUserId, randomId, messageContentHeader, messageContentData, deletedAt) <>
    (applyHistoryMessage.tupled, unapplyHistoryMessage)

  private def applyHistoryMessage: (Int, Int, Int, DateTime, Int, Long, Int, Array[Byte], Option[DateTime]) ⇒ HistoryMessage = {
    case (userId, peerType, peerId, date, senderUserId, randomId, messageContentHeader, messageContentData, deletedAt) ⇒
      HistoryMessage(
        userId = userId,
        peer = Peer(PeerType.fromValue(peerType), peerId),
        date = date,
        senderUserId = senderUserId,
        randomId = randomId,
        messageContentHeader = messageContentHeader,
        messageContentData = messageContentData,
        deletedAt = deletedAt
      )
  }

  private def unapplyHistoryMessage: HistoryMessage ⇒ Option[(Int, Int, Int, DateTime, Int, Long, Int, Array[Byte], Option[DateTime])] = { historyMessage ⇒
    val sdf = new java.text.SimpleDateFormat("yyyy-MM-dd")
    HistoryMessage.unapply(historyMessage) map {
      case (userId, peer, date, senderUserId, randomId, messageContentHeader, messageContentData, deletedAt) ⇒
        (userId, peer.typ.value, peer.id, date, senderUserId, randomId, messageContentHeader, messageContentData, deletedAt)
    }
  }
}

object HistoryMessageRepo {
  private val SharedUserId = 0

  val messages = TableQuery[HistoryMessageTable]
  val messagesC = Compiled(messages)

  val notDeletedMessages = messages.filter(_.deletedAt.isEmpty)

  val withoutServiceMessages = notDeletedMessages.filter(_.messageContentHeader =!= 2)

  def byUserIdPeer(userId: Rep[Int], peerType: Rep[Int], peerId: Rep[Int]) = {
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peerType && m.peerId === peerId)
  }

  def create(message: HistoryMessage): FixedSqlAction[Int, NoStream, Write] =
    messagesC += message

  def create(newMessages: Seq[HistoryMessage]): FixedSqlAction[Option[Int], NoStream, Write] =
    messagesC ++= newMessages

  def find(userId: Int, peer: Peer, dateOpt: Option[DateTime], limit: Int): FixedSqlStreamingAction[Seq[HistoryMessage], HistoryMessage, Read] = {
    val baseQuery = (
      if (peer.typ == PeerType.Group && userId == 0) {
        //peer为群组，判断群是否为共享群，如果是共享群（userId==0为共享），则可以查看所有消息。
        notDeletedMessages
          .filter(m ⇒
            m.peerType === peer.typ.value &&
              m.peerId === peer.id)
      } else {
        notDeletedMessages
          .filter(m ⇒
            m.userId === userId &&
              m.peerType === peer.typ.value &&
              m.peerId === peer.id)
      }
    )

    val query = dateOpt match {
      case Some(date) ⇒
        baseQuery.filter(_.date <= date).sortBy(_.date.desc)
      case None ⇒
        baseQuery.sortBy(_.date.asc)
    }

    query.take(limit).result
  }

  private val afterC = Compiled { (userId: Rep[Int], peerType: Rep[Int], peerId: Rep[Int], date: Rep[DateTime], limit: ConstColumn[Long]) ⇒
    byUserIdPeer(userId, peerType, peerId)
      .filter(_.date >= date)
      .sortBy(_.date.asc)
      .take(limit)
  }

  def findAfter(userId: Int, peer: Peer, date: DateTime, limit: Long) =
    afterC((userId, peer.typ.value, peer.id, date, limit)).result

  /**
   * 查询历史消息（扩展方法）  by Lining 2016/7/18
   *
   * @param userId
   * @param peer
   * @param date
   * @param limit
   */
  def findAfterExt(userId: Int, peer: Peer, date: DateTime, limit: Long) = {
    if (peer.typ.isGroup && userId == 0) {
      //peer为群组，判断群是否为共享群，如果是共享群，则可以查看所有消息。
      (notDeletedMessages
        .filter(m ⇒ m.peerType === peer.typ.value && m.peerId === peer.id).filter(_.date >= date)
        .sortBy(_.date.asc)
        .take(limit)).result
    } else {
      (notDeletedMessages
        .filter(m ⇒ m.userId === userId && m.peerType === peer.typ.value && m.peerId === peer.id).filter(_.date >= date)
        .sortBy(_.date.asc)
        .take(limit)).result
    }
  }

  /**
   * 查询文本
   * by Lining 2016/9/7
   *
   * @param userId 用户Id
   * @param peer 会话peer
   * @param keyword  关键字
   * @param limit 查询条数
   * @param offset 偏移量
   * @return
   */
  def findText(userId: Int, peer: Peer, keyword: String, limit: Int, offset: Int) = {
    implicit val getMessageResult: GetResult[HistoryMessage] = GetResult(r ⇒
      HistoryMessage(
        userId = r.nextInt,
        peer = Peer(PeerType.fromValue(r.nextInt), r.nextInt),
        date = getDatetimeResult(r),
        senderUserId = r.nextInt,
        randomId = r.nextLong,
        messageContentHeader = r.nextInt,
        messageContentData = r.nextBytes,
        deletedAt = getDatetimeOptionResult(r)
      ))
    val querySql = getSearchSql(userId, peer, keyword, limit, offset, 1)
    sql"""#$querySql""".as[HistoryMessage]
  }

  /**
   * 查询文件
   * @param userId 用户Id
   * @param peer 会话peer
   * @param limit 查询条数
   * @param offset 偏移量
   */
  def findFile(userId: Int, peer: Peer, limit: Int, offset: Int) = {
    implicit val getMessageResult: GetResult[HistoryMessage] = GetResult(r ⇒
      HistoryMessage(
        userId = r.nextInt,
        peer = Peer(PeerType.fromValue(r.nextInt), r.nextInt),
        date = getDatetimeResult(r),
        senderUserId = r.nextInt,
        randomId = r.nextLong,
        messageContentHeader = r.nextInt,
        messageContentData = r.nextBytes,
        deletedAt = getDatetimeOptionResult(r)
      ))
    val querySql = getSearchSql(userId, peer, "", limit, offset, 3)
    sql"""#$querySql""".as[HistoryMessage]
  }

  def findHistory(userId: Int, peer: Peer, randomId: String, limit: Int, historyOwner: Int) = {
    implicit val getMessageResult: GetResult[HistoryMessage] = GetResult(r ⇒
      HistoryMessage(
        userId = r.nextInt,
        peer = Peer(PeerType.fromValue(r.nextInt), r.nextInt),
        date = getDatetimeResult(r),
        senderUserId = r.nextInt,
        randomId = r.nextLong,
        messageContentHeader = r.nextInt,
        messageContentData = r.nextBytes,
        deletedAt = getDatetimeOptionResult(r)
      ))
    val querySql = getLoadHistorySql(peer.id, peer.`type`.value, userId, randomId, limit, historyOwner)
    sql"""#$querySql""".as[HistoryMessage]
  }

  private def getLoadHistorySql(peerId: Int, peerType: Int, userId: Int, randomId: String, limit: Int, historyOwner: Int): String = {
    val sb: StringBuilder = new StringBuilder(1024)
    if (historyOwner > 0) {
      //      sb.append("SELECT m.*,u.name as sender_name,u.nickname,(")
      //      sb.append(userId).append("=m.sender_user_id) as is_out ")
      //      sb.append("FROM history_messages m INNER JOIN users u ON m.sender_user_id=u.id ")
      //      sb.append("WHERE m.peer_id=").append(peerId).append(" AND m.date<")
      //      sb.append("(SELECT date FROM history_messages WHERE random_id=").append(randomId).append(" LIMIT 1) ")
      //      sb.append("AND m.random_id<>").append(randomId)
      //      sb.append(" AND m.user_id=").append(userId)

      sb.append("SELECT DISTINCT ON(m.date,m.random_id) m.*,u.name as sender_name,u.nickname,(")
      sb.append(userId).append("=m.sender_user_id) as is_out ")
      sb.append("FROM history_messages m INNER JOIN users u ON m.sender_user_id=u.id ")
      sb.append("WHERE m.peer_id=").append(peerId).append(" AND m.date<")
      sb.append("(SELECT date FROM history_messages WHERE random_id=").append(randomId).append(" LIMIT 1) ")
      sb.append("AND m.user_id=").append(userId)
    } else {
      //      sb.append("SELECT m.*,u.name as sender_name,u.nickname,(")
      //      sb.append(userId).append("=m.sender_user_id) as is_out ")
      //      sb.append("FROM history_messages m INNER JOIN users u ON m.sender_user_id=u.id ")
      //      sb.append("WHERE m.peer_id=").append(peerId).append(" AND m.user_id=(")
      //      sb.append("SELECT creator_user_id FROM groups WHERE id=").append(peerId).append(") ")
      //      sb.append("AND m.date<(SELECT date FROM history_messages WHERE random_id=").append(randomId)
      //      sb.append(" LIMIT 1)")

      sb.append("SELECT DISTINCT ON(m.date,m.random_id) m.*,u.name as sender_name,u.nickname,(")
      sb.append(userId).append("=m.sender_user_id) as is_out ")
      sb.append("FROM history_messages m INNER JOIN users u ON m.sender_user_id=u.id ")
      sb.append("WHERE m.peer_id=").append(peerId).append(" AND ")
      sb.append("m.date<(SELECT date FROM history_messages WHERE random_id=").append(randomId).append(" LIMIT 1)")
    }
    sb.append(" ORDER BY m.date DESC LIMIT ").append(limit)
    println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    println(sb.toString())
    println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
    sb.toString()
  }

  private def getSearchSql(userId: Int, peer: Peer, keyword: String, limit: Int, offset: Int, contentHeader: Int): String = {
    //message_content_header: 1-文本；2-系统提示消息；3-图片和文档
    var querySql = "select distinct on (date, random_id) user_id, peer_type, peer_id, date, sender_user_id, random_id, message_content_header, message_content_data, deleted_at from history_messages" +
      " where message_content_header=" + contentHeader +
      " and deleted_at is null" +
      " and peer_type=" + peer.typ.value +
      " and peer_id=" + peer.id
    //peer为群组，判断群是否为共享群，如果是共享群，则可以查看所有消息。
    if (!(peer.typ.isGroup && userId == 0)) querySql += " and user_id=" + userId
    if (keyword != null && keyword.trim != "") {
      querySql += " and message_content_data like '%" + keyword + "%'"
    }
    querySql += " order by date desc" +
      " limit " + limit + " offset " + offset
    querySql
  }

  private val metaAfterC = Compiled { (userId: Rep[Int], peerType: Rep[Int], peerId: Rep[Int], date: Rep[DateTime], limit: ConstColumn[Long]) ⇒
    byUserIdPeer(userId, peerType, peerId)
      .filter(_.date > date)
      .sortBy(_.date.asc)
      .take(limit)
      .map(hm ⇒ (hm.randomId, hm.date, hm.senderUserId, hm.messageContentHeader))
  }

  def findMetaAfter(userId: Int, peer: Peer, date: DateTime, limit: Long) =
    metaAfterC((userId, peer.typ.value, peer.id, date, limit)).result

  private val beforeC = Compiled { (userId: Rep[Int], peerId: Rep[Int], peerType: Rep[Int], date: Rep[DateTime], limit: ConstColumn[Long]) ⇒
    byUserIdPeer(userId, peerType, peerId)
      .filter(_.date <= date)
      .sortBy(_.date.asc)
      .take(limit)
  }

  private val beforeExclC = Compiled { (userId: Rep[Int], peerId: Rep[Int], peerType: Rep[Int], date: Rep[DateTime], limit: ConstColumn[Long]) ⇒
    byUserIdPeer(userId, peerType, peerId)
      .filter(_.date < date)
      .sortBy(_.date.asc)
      .take(limit)
  }

  private val byUserIdPeerRidC = Compiled { (userId: Rep[Int], peerType: Rep[Int], peerId: Rep[Int], randomId: Rep[Long]) ⇒
    byUserIdPeer(userId, peerType, peerId).filter(_.randomId === randomId)
  }

  def existstWithRandomId(userId: Int, peer: Peer, randomId: Long): DBIO[Boolean] =
    byUserIdPeerRidC.applied((userId, peer.typ.value, peer.id, randomId)).exists.result

  def findBefore(userId: Int, peer: Peer, date: DateTime, limit: Long) =
    beforeC((userId, peer.typ.value, peer.id, date, limit)).result

  /**
   * 查询历史消息（扩展方法）  by Lining 2016/7/18
   *
   * @param userId
   * @param peer
   * @param date
   * @param limit
   */
  def findBeforeExt(userId: Int, peer: Peer, date: DateTime, limit: Long) = {
    if (peer.typ.isGroup && userId == 0) {
      //peer为群组，判断群是否为共享群，如果是共享群，则可以查看所有消息。
      (notDeletedMessages
        .filter(m ⇒ m.peerType === peer.typ.value && m.peerId === peer.id).filter(_.date <= date)
        .sortBy(_.date.asc)
        .take(limit)).result
    } else {
      (notDeletedMessages
        .filter(m ⇒ m.userId === userId && m.peerType === peer.typ.value && m.peerId === peer.id).filter(_.date <= date)
        .sortBy(_.date.asc)
        .take(limit)).result
    }
  }

  def findBidi(userId: Int, peer: Peer, date: DateTime, limit: Long) =
    (beforeExclC.applied((userId, peer.typ.value, peer.id, date, limit)) ++
      afterC.applied((userId, peer.typ.value, peer.id, date, limit))).result

  /**
   * 查询历史消息（扩展方法）  by Lining 2016/7/18
   *
   * @param userId
   * @param peer
   * @param date
   * @param limit
   * @return
   */
  def findBidiExt(userId: Int, peer: Peer, date: DateTime, limit: Long) = {
    if (peer.typ.isGroup && userId == 0) {
      //peer为群组，判断群是否为共享群，如果是共享群，则可以查看所有消息。
      ((notDeletedMessages
        .filter(m ⇒ m.peerType === peer.typ.value && m.peerId === peer.id).filter(_.date < date)
        .sortBy(_.date.asc)
        .take(limit)) ++
        (notDeletedMessages
          .filter(m ⇒ m.peerType === peer.typ.value && m.peerId === peer.id).filter(_.date >= date)
          .sortBy(_.date.asc)
          .take(limit))).result
    } else {
      ((notDeletedMessages
        .filter(m ⇒ m.userId === userId && m.peerType === peer.typ.value && m.peerId === peer.id).filter(_.date < date)
        .sortBy(_.date.asc)
        .take(limit)) ++
        (notDeletedMessages
          .filter(m ⇒ m.userId === userId && m.peerType === peer.typ.value && m.peerId === peer.id).filter(_.date >= date)
          .sortBy(_.date.asc)
          .take(limit))).result
    }
  }

  def findBySender(senderUserId: Int, peer: Peer, randomId: Long): FixedSqlStreamingAction[Seq[HistoryMessage], HistoryMessage, Read] =
    notDeletedMessages.filter(m ⇒ m.senderUserId === senderUserId && m.peerType === peer.typ.value && m.peerId === peer.id && m.randomId === randomId).result

  def findUserIds(peer: Peer, randomIds: Set[Long]): DBIO[Seq[Int]] =
    notDeletedMessages
      .filter(m ⇒ m.peerType === peer.typ.value && m.peerId === peer.id && (m.randomId inSet randomIds))
      .map(_.userId)
      .result

  def findNewest(userId: Int, peer: Peer): SqlAction[Option[HistoryMessage], NoStream, Read] =
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peer.typ.value && m.peerId === peer.id)
      .sortBy(_.date.desc)
      .take(1)
      .result
      .headOption

  def find(userId: Int, peer: Peer): FixedSqlStreamingAction[Seq[HistoryMessage], HistoryMessage, Read] = {
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peer.typ.value && m.peerId === peer.id)
      .sortBy(_.date.desc)
      .result
  }

  def find(userId: Int, peer: Peer, randomIds: Set[Long]): FixedSqlStreamingAction[Seq[HistoryMessage], HistoryMessage, Read] = {
    notDeletedMessages.filter(m ⇒ m.userId === userId && m.peerType === peer.typ.value && m.peerId === peer.id && (m.randomId inSet randomIds)).result
  }

  def updateContentAll(userIds: Set[Int], randomId: Long, peerType: PeerType, peerIds: Set[Int],
                       messageContentHeader: Int, messageContentData: Array[Byte]): FixedSqlAction[Int, NoStream, Write] =
    notDeletedMessages
      .filter(m ⇒ m.randomId === randomId && m.peerType === peerType.value)
      .filter(_.peerId inSet peerIds)
      .filter(_.userId inSet userIds)
      .map(m ⇒ (m.messageContentHeader, m.messageContentData))
      .update((messageContentHeader, messageContentData))

  def uniqueAsc(fromTs: Long, limit: Int): SqlStreamingAction[Vector[HistoryMessage], HistoryMessage, Effect] = {
    implicit val getMessageResult: GetResult[HistoryMessage] = GetResult(r ⇒
      HistoryMessage(
        userId = r.nextInt,
        peer = Peer(PeerType.fromValue(r.nextInt), r.nextInt),
        date = getDatetimeResult(r),
        senderUserId = r.nextInt,
        randomId = r.nextLong,
        messageContentHeader = r.nextInt,
        messageContentData = r.nextBytes,
        deletedAt = getDatetimeOptionResult(r)
      ))

    val serviceHeader = 2
    val date = new DateTime(fromTs)
    sql"""select distinct on (date, random_id) user_id, peer_type, peer_id, date, sender_user_id, random_id, message_content_header, message_content_data, deleted_at from history_messages
         where message_content_header != $serviceHeader
         and date > $date
         and deleted_at is null
         order by date asc, random_id asc
         limit $limit"""
      .as[HistoryMessage]
  }

  def haveMessagesBetween(userId: Int, peer: Peer, minDate: DateTime, maxDate: DateTime) =
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peer.typ.value && m.peerId === peer.id)
      .filter(m ⇒ m.date > minDate && m.date < maxDate && m.senderUserId =!= userId)
      .exists
      .result

  def getUnreadCount(historyOwner: Int, clientUserId: Int, peer: Peer, lastReadAt: DateTime, noServiceMessages: Boolean = false): FixedSqlAction[Int, PostgresDriver.api.NoStream, Read] =
    (if (noServiceMessages) withoutServiceMessages else notDeletedMessages)
      .filter(m ⇒ m.userId === historyOwner && m.peerType === peer.typ.value && m.peerId === peer.id)
      .filter(m ⇒ m.date > lastReadAt && m.senderUserId =!= clientUserId)
      .length
      .result

  def deleteAll(userId: Int, peer: Peer): FixedSqlAction[Int, NoStream, Write] = {
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peer.typ.value && m.peerId === peer.id)
      .map(_.deletedAt)
      .update(Some(new DateTime))
  }

  def delete(userId: Int, peer: Peer, randomIds: Set[Long]) =
    notDeletedMessages
      .filter(m ⇒ m.userId === userId && m.peerType === peer.typ.value && m.peerId === peer.id)
      .filter(_.randomId inSet randomIds)
      .map(_.deletedAt)
      .update(Some(new DateTime))
}