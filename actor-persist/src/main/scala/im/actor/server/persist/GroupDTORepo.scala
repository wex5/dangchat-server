package im.actor.server.persist

import slick.driver.PostgresDriver.api._
import im.actor.server.model.GroupDTO
import slick.jdbc.GetResult

/**
 * Created by User on 2017/2/23.
 */
object GroupDTORepo {

  def findById(groupId: Int) = {
    implicit val getMessageResult: GetResult[GroupDTO] = GetResult(r ⇒
      GroupDTO(
        groupId = r.nextInt(),
        groupName = r.nextString(),
        creatorUserId = r.nextInt(),
        about = r.nextString()
      ))

    sql"""SELECT id,title,creatorUserId,about FROM groups where id=$groupId""".as[GroupDTO].headOption
  }

}
