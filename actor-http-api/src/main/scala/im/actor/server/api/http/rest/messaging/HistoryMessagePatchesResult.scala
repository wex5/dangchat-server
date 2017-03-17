package im.actor.server.api.http.rest.messaging

import org.joda.time.DateTime

/**
 * Created by User on 2017/2/27.
 */
case class HistoryMessagePatchesResult(
  userId:               Int,
  peerType:             Int,
  peerId:               Int,
  date:                 DateTime,
  senderUserId:         Int,
  randomId:             Long,
  messageContentHeader: Int,
  messageContent:       String,
  deletedAt:            Option[DateTime],
  senderName:           String,
  senderNickName:       String,
  isOut:                Boolean
) {
}
