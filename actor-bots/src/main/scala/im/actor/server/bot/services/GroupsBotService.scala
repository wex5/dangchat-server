package im.actor.server.bot.services

import akka.actor.ActorSystem
import im.actor.server.bot.{ ApiToBotConversions, BotServiceBase }
import im.actor.server.group.{ GroupExtension, GroupType }
import im.actor.util.misc.IdUtils

import scala.concurrent.forkjoin.ThreadLocalRandom

private[bot] final class GroupsBotService(system: ActorSystem) extends BotServiceBase(system) with ApiToBotConversions {

  import im.actor.bots.BotMessages._
  import system.dispatcher

  private val groupExt = GroupExtension(system)

  override val handlers: Handlers = {
    case CreateGroup(title)              ⇒ createGroup(title).toWeak
    case InviteUser(groupPeer, userPeer) ⇒ inviteUser(groupPeer, userPeer).toWeak
  }

  private def createGroup(title: String) = RequestHandler[CreateGroup, CreateGroup#Response](
    (botUserId: Int, botAuthId: Long, botAuthSid: Int) ⇒ {
      val groupId = IdUtils.nextIntId()
      val randomId = ThreadLocalRandom.current().nextLong()

      /*
       * 如果群组名称的格式为：groupName,唯一字符
       * 则表示是创建讨论组，需要特殊处理
       */
      var groupTitle = title
      val titleSplit = title.split(",")
      if (titleSplit.length > 1) {
        groupTitle = titleSplit(0)
        val discussionGroup = im.actor.server.model.DiscussionGroup(titleSplit(1), groupId)
        val db = im.actor.server.db.DbExtension(system).db
        db.run(im.actor.server.persist.DiscussionGroupRepo.create(discussionGroup))
      }

      for {
        ack ← groupExt.create(
          groupId = groupId,
          clientUserId = botUserId,
          clientAuthId = 0L,
          title = groupTitle,
          randomId = randomId,
          userIds = Set.empty
        )
      } yield Right(ResponseCreateGroup(GroupOutPeer(groupId, ack.accessHash)))
    }
  )

  private def inviteUser(groupPeer: GroupOutPeer, userPeer: UserOutPeer) = RequestHandler[InviteUser, InviteUser#Response](
    (botUserId: Int, botAuthId: Long, botAuthSid: Int) ⇒ {
      // FIXME: check access hash

      val randomId = ThreadLocalRandom.current().nextLong()

      for {
        ack ← groupExt.inviteToGroup(botUserId, 0L, groupPeer.id, userPeer.id, randomId)
      } yield Right(Void)
    }
  )
}
