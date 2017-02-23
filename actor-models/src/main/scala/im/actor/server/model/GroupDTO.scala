package im.actor.server.model

/**
 * Created by User on 2017/2/23.
 */
case class GroupDTO(
  groupId:   Int,
  groupName: String
) {

  var members: Vector[GroupMemberDTO] = null

  def this(groupId: Int, groupName: String, members: Vector[GroupMemberDTO]) {
    this(groupId, groupName)
    this.members = members
  }
}
