package im.actor.server.persist

import im.actor.server.model.ClientState
import slick.driver.PostgresDriver.api._

final class ClientStateTable(tag: Tag) extends Table[ClientState](tag, "client_state") {
  def userId = column[Int]("user_id", O.PrimaryKey)
  def state = column[Short]("state")

  def * = (userId, state) <> (ClientState.tupled, ClientState.unapply)
}

/**
 * Created by Lining on 2016/6/23.
 */
object ClientStateRepo {

  val clientStates = TableQuery[ClientStateTable]

  def byUserId(userId: Rep[Int]) = clientStates filter (_.userId === userId)
  val byUserIdC = Compiled(byUserId _)

  def find(userId: Int) =
    byUserIdC(userId).result.headOption

  def createOrUpdate(clientState: ClientState) = {
    clientStates.insertOrUpdate(clientState)
  }

}
