package im.actor.server.model

/**
 * Created by User on 2017/2/23.
 */
case class GroupDTO(
  groupId:       Int,
  groupName:     String,
  creatorUserId: Int,
  about:         String
) {

  var members: Vector[GroupMemberDTO] = null

  def this(groupId: Int, groupName: String, creatorUserId: Int, about: String, members: Vector[GroupMemberDTO]) {
    this(groupId, groupName, creatorUserId, about)
    this.members = members
  }
}
