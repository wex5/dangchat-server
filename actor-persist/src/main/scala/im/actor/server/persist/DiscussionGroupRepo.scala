package im.actor.server.persist

import im.actor.server.db.ActorPostgresDriver.api._
import im.actor.server.model.DiscussionGroup

final class DiscussionGroupTable(tag: Tag) extends Table[DiscussionGroup](tag, "discussion_groups") {
  def id = column[String]("id", O.PrimaryKey)
  def groupId = column[Int]("group_id")

  def * = (id, groupId) <> (DiscussionGroup.tupled, DiscussionGroup.unapply)
}

/**
 * Created by Lining on 2016/6/23.
 */
object DiscussionGroupRepo {

  val discussionGroups = TableQuery[DiscussionGroupTable]

  def create(discussionGroup: DiscussionGroup) =
    discussionGroups += discussionGroup

  def findById(id: String) = discussionGroups.filter(d ⇒ d.id === id).result.headOption

}
