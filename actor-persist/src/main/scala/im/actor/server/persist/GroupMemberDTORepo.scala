package im.actor.server.persist

import slick.driver.PostgresDriver.api._
import im.actor.server.model.GroupMemberDTO
import slick.jdbc.GetResult

/**
 * Created by User on 2017/2/23.
 */
object GroupMemberDTORepo {

  def findByGroupId(groupId: Int) = {
    implicit val getMessageResult: GetResult[GroupMemberDTO] = GetResult(r ⇒
      GroupMemberDTO(
        memberId = r.nextInt(),
        memberName = r.nextString()
      ))

    sql"""SELECT u.id,u.name FROM group_users gu INNER JOIN users u ON gu.user_id=u.id WHERE gu.group_id=$groupId""".as[GroupMemberDTO]
  }

}
