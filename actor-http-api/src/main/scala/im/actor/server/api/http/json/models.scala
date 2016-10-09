package im.actor.server.api.http.json

sealed trait Content
case class Text(text: String) extends Content
case class Image(imageUrl: String) extends Content
case class Document(documentUrl: String) extends Content

case class GroupInfo(id: Int, title: String, isPublic: Boolean, avatars: Option[AvatarUrls])
case class InviterInfo(name: String, avatars: Option[AvatarUrls])
case class GroupInviteInfo(group: GroupInfo, inviter: Option[InviterInfo])
case class AvatarUrls(small: Option[String], large: Option[String], full: Option[String])

case class Errors(message: String)

case class ReverseHook(url: String)

case class Status(status: String)
case class ReverseHookResponse(id: Int, url: Option[String])

final case class ServerInfo(projectName: String, endpoints: List[String])

final case class UserBotInfo(id: Int, nickname: String, name: String, token: String)

final case class UserBotInfoArray(bots: List[UserBotInfo])

final case class UserInfo(id: Int, name: String, accessHash: Long, nickname: String)

final case class DiscussionGroupInfo(id: String, groupId: Int)