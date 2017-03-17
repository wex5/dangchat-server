package im.actor.server.model

import org.joda.time.DateTime

/**
 * Created by User on 2017/2/24.
 */
case class HistoryMessagePatches(
  userId:               Int,
  peerType:             Int,
  peerId:               Int,
  date:                 DateTime,
  senderUserId:         Int,
  randomId:             Long,
  messageContentHeader: Int,
  messageContentData:   Array[Byte],
  deletedAt:            Option[DateTime],
  senderName:           String,
  senderNickName:       String,
  isOut:                Boolean
) {
}
