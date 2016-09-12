package im.actor.server.api.rpc.service.search

import akka.actor.ActorSystem
import akka.http.scaladsl.util.FastFuture
import im.actor.api.rpc._
import im.actor.api.rpc.groups.ApiGroup
import im.actor.api.rpc.peers.{ ApiGroupOutPeer, ApiPeer, ApiPeerType, ApiUserOutPeer }
import im.actor.api.rpc.search._
import im.actor.api.rpc.sequence.ApiUpdateOptimization
import im.actor.api.rpc.users.ApiUser
import im.actor.concurrent.FutureExt
import im.actor.server.db.DbExtension
import im.actor.server.dialog.DialogExtension
import im.actor.server.group.{ GroupExtension, GroupUtils }
import im.actor.server.names.GlobalNamesStorageKeyValueStorage
import im.actor.server.persist.contact.UserContactRepo
import im.actor.server.user.UserExtension
import im.actor.api.rpc.peers.{ ApiOutPeer, ApiPeerType }
import im.actor.server.persist.HistoryMessageRepo
import im.actor.server.dialog.HistoryUtils
import im.actor.api.rpc.messaging.ApiMessageContainer
import org.joda.time.DateTime
import slick.driver.PostgresDriver.api._

import scala.concurrent.{ ExecutionContext, Future }

class SearchServiceImpl(implicit system: ActorSystem) extends SearchService {
  import HistoryUtils._
  import EntitiesHelpers._

  override implicit protected val ec: ExecutionContext = system.dispatcher

  protected val db = DbExtension(system).db

  private val userExt = UserExtension(system)
  private val groupExt = GroupExtension(system)
  private val globalNamesStorage = new GlobalNamesStorageKeyValueStorage

  private val EmptyResult = ResponsePeerSearch(Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

  private val dialogExt = DialogExtension(system)

  override def doHandlePeerSearch(
    query:         IndexedSeq[ApiSearchCondition],
    optimizations: IndexedSeq[ApiUpdateOptimization.Value],
    clientData:    ClientData
  ): Future[HandlerResult[ResponsePeerSearch]] = {
    authorized(clientData) { implicit client ⇒
      val (peerTypes, texts) = query.foldLeft(Set.empty[ApiSearchPeerType.Value], Set.empty[String]) {
        case ((pts, txts), ApiSearchPieceText(t))          ⇒ (pts, txts + t)
        case ((pts, txts), ApiSearchPeerTypeCondition(pt)) ⇒ (pts + pt, txts)
        case ((pts, txts), _)                              ⇒ (pts, txts)
      }
      val peerTypesSorted = peerTypes.toVector.sortBy(_.id)

      texts.toList match {
        case text :: Nil if text.length < 3 ⇒
          FastFuture.successful(Ok(EmptyResult))
        case text :: Nil ⇒
          val tps = if (peerTypes.isEmpty)
            Vector(ApiSearchPeerType.Groups, ApiSearchPeerType.Contacts, ApiSearchPeerType.Public)
          else
            peerTypesSorted
          searchResult(tps, Some(text), optimizations)
        case Nil ⇒ searchResult(peerTypesSorted, None, optimizations)
        case _   ⇒ FastFuture.successful(Error(RpcError(400, "INVALID_QUERY", "Invalid query.", canTryAgain = false, None)))
      }
    }
  }

  override def doHandleMessageSearch(
    query:         ApiSearchCondition,
    optimizations: IndexedSeq[ApiUpdateOptimization.Value],
    clientData:    ClientData
  ): Future[HandlerResult[ResponseMessageSearchResponse]] =
    authorized(clientData) { implicit client ⇒
      {
        val peer = getApiOutPeer(query)
        val modelPeer = peer.asModel
        val stripEntities = optimizations.contains(ApiUpdateOptimization.STRIP_ENTITIES)
        val loadGroupMembers = !optimizations.contains(ApiUpdateOptimization.GROUPS_V2)
        val searchType = getSearchType(query)

        val action = for {
          historyOwner ← DBIO.from(getHistoryOwner(modelPeer, client.userId))
          messageModels ← searchType match {
            case 1 ⇒
              val searchParams = getSearchTextParameters(query)
              HistoryMessageRepo.findText(client.userId, modelPeer, searchParams._1, searchParams._2, searchParams._3)
            case _ ⇒
              val searchParams = getSearchFileParameters(query)
              HistoryMessageRepo.findFile(client.userId, modelPeer, searchParams._1, searchParams._2)
          }
          reactions ← dialogExt.fetchReactions(modelPeer, client.userId, messageModels.map(_.randomId).toSet)

          (messages, userIds, groupIds) = messageModels.view
            .map(_.ofUser(client.userId))
            .foldLeft(Vector.empty[ApiMessageContainer], Set.empty[Int], Set.empty[Int]) {
              case ((msgs, uids, guids), message) ⇒
                message.asStruct(DateTime.now(), DateTime.now(), reactions.getOrElse(message.randomId, Vector.empty)).toOption match {
                  case Some(messageStruct) ⇒
                    val newMsgs = msgs :+ messageStruct
                    val newUserIds = relatedUsers(messageStruct.message) ++
                      (if (message.senderUserId != client.userId)
                        uids + message.senderUserId
                      else
                        uids)

                    (newMsgs, newUserIds, guids ++ messageStruct._relatedGroupIds)
                  case None ⇒ (msgs, uids, guids)
                }
            }
          ((users, userPeers), (groups, groupPeers)) ← DBIO.from(usersAndGroupsByIds(groupIds, userIds, stripEntities, loadGroupMembers))
        } yield Ok(ResponseMessageSearchResponse(
          searchResults = messages.map(m ⇒ ApiMessageSearchItem(ApiMessageSearchResult(peer.asPeer, m.randomId, m.date, m.senderUserId, m.message))).toIndexedSeq,
          users = users,
          groups = groups,
          loadMoreState = Option("loadMoreState".getBytes()),
          userOutPeers = userPeers,
          groupOutPeers = groupPeers
        ))
        db.run(action)
      }
    }

  /**
   * 得到搜索类别
   * @param query
   * @return 1-搜索文本；2-搜索图片；3-搜索文档
   */
  private def getSearchType(query: ApiSearchCondition): Int = {
    val searchCondition = query.asInstanceOf[ApiSearchAndCondition]
    //根据第二个参数判断查询类型
    val searchType = searchCondition.andQuery(1) match {
      case text: ApiSearchPieceText ⇒ 1
      case searchType: ApiSearchContentType ⇒
        searchType match {
          case ApiSearchContentType.Photos    ⇒ 2
          case ApiSearchContentType.Documents ⇒ 3
          case _                              ⇒ 0
        }
      case _ ⇒ 0
    }
    searchType
  }

  /**
   * 得到ApiOutPeer
   * @param query
   * @return
   */
  private def getApiOutPeer(query: ApiSearchCondition): ApiOutPeer = {
    val searchCondition = query.asInstanceOf[ApiSearchAndCondition]
    val peerCondition = searchCondition.andQuery(0).asInstanceOf[ApiSearchPeerCondition]
    peerCondition.peer
  }

  /**
   * 得到搜索文本的参数
   * @param query
   * @return
   */
  private def getSearchTextParameters(query: ApiSearchCondition): (String, Int, Int) = {
    val searchCondition = query.asInstanceOf[ApiSearchAndCondition]
    val keyword = searchCondition.andQuery(1).asInstanceOf[ApiSearchPieceText].query
    val limit = searchCondition.andQuery(2).asInstanceOf[ApiSearchDataLimit].limit
    val offset = searchCondition.andQuery(3).asInstanceOf[ApiSearchDataOffset].offset
    (keyword, limit, offset)
  }

  /**
   * 得到搜索文件的参数
   * @param query
   * @return
   */
  private def getSearchFileParameters(query: ApiSearchCondition): (Int, Int) = {
    val searchCondition = query.asInstanceOf[ApiSearchAndCondition]
    val limit = searchCondition.andQuery(2).asInstanceOf[ApiSearchDataLimit].limit
    val offset = searchCondition.andQuery(3).asInstanceOf[ApiSearchDataOffset].offset
    (limit, offset)
  }

  override def doHandleMessageSearchMore(
    loadMoreState: Array[Byte],
    optimizations: IndexedSeq[ApiUpdateOptimization.Value],
    clientData:    ClientData
  ): Future[HandlerResult[ResponseMessageSearchResponse]] =
    FastFuture.successful(Error(CommonRpcErrors.NotSupportedInOss))

  private def searchResult(
    pts:           IndexedSeq[ApiSearchPeerType.Value],
    text:          Option[String],
    optimizations: IndexedSeq[ApiUpdateOptimization.Value]
  )(implicit client: AuthorizedClientData): Future[HandlerResult[ResponsePeerSearch]] = {
    val stripEntities = optimizations.contains(ApiUpdateOptimization.STRIP_ENTITIES)
    val loadGroupMembers = !optimizations.contains(ApiUpdateOptimization.GROUPS_V2)

    for {
      results ← FutureExt.ftraverse(pts)(search(_, text)).map(_.reduce(_ ++ _))
      (groupIds, userIds, searchResults) = (results foldLeft (Set.empty[Int], Set.empty[Int], Vector.empty[ApiPeerSearchResult])) {
        case (acc @ (gids, uids, rslts), found @ ApiPeerSearchResult(peer, _)) ⇒
          if (rslts.exists(_.peer == peer)) {
            acc
          } else {
            peer.`type` match {
              case ApiPeerType.Private ⇒ (gids, uids + peer.id, rslts :+ found)
              case ApiPeerType.Group   ⇒ (gids + peer.id, uids, rslts :+ found)
            }
          }
      }
      ((users, userPeers), (groups, groupPeers)) ← EntitiesHelpers.usersAndGroupsByIds(groupIds, userIds, stripEntities, loadGroupMembers)
    } yield {

      Ok(ResponsePeerSearch(
        searchResults = searchResults,
        users = users,
        groups = groups,
        userPeers = userPeers,
        groupPeers = groupPeers
      ))
    }
  }

  type PeerAndMatchString = (ApiPeer, String)

  private def search(pt: ApiSearchPeerType.Value, text: Option[String])(implicit clientData: AuthorizedClientData): Future[IndexedSeq[ApiPeerSearchResult]] = {
    pt match {
      case ApiSearchPeerType.Contacts ⇒
        for {
          users ← searchContacts(text)
        } yield users map result
      case ApiSearchPeerType.Groups ⇒
        for {
          groups ← searchLocalGroups(text)
        } yield groups map result
      case ApiSearchPeerType.Public ⇒
        val usersFull = searchGlobalUsers(text)
        val usersPrefix = searchGlobalUsersPrefix(text)
        val groupsPrefix = searchGlobalGroupsPrefix(text)
        for {
          uf ← usersFull
          up ← usersPrefix
          gp ← groupsPrefix
        } yield (uf map result) ++ (up map result) ++ (gp map result)
    }
  }

  private def result(peer: ApiPeer): ApiPeerSearchResult =
    ApiPeerSearchResult(
      peer = peer,
      optMatchString = None
    )

  private def result(peerAndMatch: PeerAndMatchString): ApiPeerSearchResult =
    ApiPeerSearchResult(
      peer = peerAndMatch._1,
      optMatchString = Some(peerAndMatch._2)
    )

  private def userPeersWithoutSelf(userIds: Seq[Int])(implicit client: AuthorizedClientData): Vector[ApiPeer] =
    (userIds collect {
      case userId if userId != client.userId ⇒ ApiPeer(ApiPeerType.Private, userId)
    }).toVector

  // search users by full phone number, email or nickname
  private def searchGlobalUsers(text: Option[String])(implicit client: AuthorizedClientData): Future[IndexedSeq[ApiPeer]] = {
    text map { query ⇒
      userExt.findUserIds(query) map userPeersWithoutSelf
    } getOrElse FastFuture.successful(Vector.empty)
  }

  // search users by nickname prefix
  private def searchGlobalUsersPrefix(text: Option[String])(implicit client: AuthorizedClientData): Future[IndexedSeq[PeerAndMatchString]] = {
    text map { query ⇒
      globalNamesStorage.userIdsByPrefix(normName(query)) map { results ⇒
        results collect {
          case (userId, nickName) if userId != client.userId ⇒
            ApiPeer(ApiPeerType.Private, userId) → s"@$nickName"
        }
      }
    } getOrElse FastFuture.successful(Vector.empty)
  }

  // find groups by global name prefix
  private def searchGlobalGroupsPrefix(text: Option[String]): Future[IndexedSeq[PeerAndMatchString]] = {
    text map { query ⇒
      globalNamesStorage.groupIdsByPrefix(normName(query)) map { results ⇒
        results map {
          case (groupId, globalName) ⇒
            ApiPeer(ApiPeerType.Group, groupId) → s"@$globalName"
        }
      }
    } getOrElse FastFuture.successful(Vector.empty)
  }

  private def normName(n: String) = if (n.startsWith("@")) n.drop(1) else n

  private def searchContacts(text: Option[String])(implicit client: AuthorizedClientData): Future[IndexedSeq[ApiPeer]] = {
    for {
      userIds ← db.run(UserContactRepo.findContactIdsActive(client.userId))
      users ← FutureExt.ftraverse(userIds)(userExt.getApiStruct(_, client.userId, client.authId))
    } yield filterUsers(users.toVector, text)
  }

  private def filterUsers(users: IndexedSeq[ApiUser], textOpt: Option[String]): IndexedSeq[ApiPeer] =
    textOpt match {
      case Some(text) ⇒
        val lotext = text.toLowerCase
        users filter { user ⇒
          user.name.toLowerCase.contains(lotext) ||
            user.localName.exists(_.toLowerCase.contains(lotext))
        } map { u ⇒
          ApiPeer(ApiPeerType.Private, u.id)
        }
      case None ⇒ users map { u ⇒ ApiPeer(ApiPeerType.Private, u.id) }
    }

  private def searchLocalGroups(text: Option[String])(implicit client: AuthorizedClientData): Future[IndexedSeq[ApiPeer]] = {
    for {
      ids ← DialogExtension(system).fetchGroupedDialogs(client.userId) map (_.filter(_.typ.isGroups).flatMap(_.dialogs.map(_.getPeer.id)))
      groups ← FutureExt.ftraverse(ids) { id ⇒
        groupExt.getApiStruct(id, client.userId)
      }
    } yield filterGroups(groups.toVector, text)
  }

  private def filterGroups(groups: IndexedSeq[ApiGroup], textOpt: Option[String]): IndexedSeq[ApiPeer] = {
    textOpt match {
      case Some(text) ⇒
        groups filter { group ⇒
          val lotext = text.toLowerCase
          group.title.toLowerCase.contains(lotext) ||
            group.about.exists(_.toLowerCase.contains(lotext)) ||
            group.theme.exists(_.toLowerCase.contains(lotext))
        } map { g ⇒
          ApiPeer(ApiPeerType.Group, g.id)
        }
      case None ⇒ groups map { g ⇒ ApiPeer(ApiPeerType.Group, g.id) }
    }
  }
}
