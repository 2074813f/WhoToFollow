package report.actors

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import learn.actors.Indexer.GetCollectionStats
import play.api.Logger
import report.actors.MetricsReporting.GetRecentQueryList
import report.actors.WebSocketSupervisor.CollectionStats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.util.{Failure, Success}


object MetricsReporting {
  case class RecentQueries(recentQueriesList: Set[String])
  case class GetRecentQueryList()
}

@Singleton
class MetricsReporting @Inject()
  (@Named("redisReader") redisReader: ActorRef,
   @Named("webSocketSupervisor") webSocketSupervisor: ActorRef,
   @Named("indexer") indexer: ActorRef)
  extends Actor {

  implicit val timeout = Timeout(20 seconds)

  /*
   Scheduled tasks
    */
  // Fetch the latest 'recent queries' list 2 seconds
  context.system.scheduler.schedule(Duration.Zero, 2.seconds, self, GetRecentQueryList())
  // Ask Redis for the latest index size every 2 seconds.
  context.system.scheduler.schedule(Duration.Zero, 2.seconds, indexer, GetCollectionStats())

  /*
   Incoming messages
   */
  override def receive = {
    /*
    Send the recent query list to all of the actors who are interested in it.
     */
    case msg @ GetRecentQueryList() =>
      (redisReader ? msg) onComplete {
        case Success(recentQueries) => webSocketSupervisor ! recentQueries
        case Failure(error) => Logger.debug("Error: " + error)
      }

    /*
    We've got the latest index size so send it to all the places who are interested in it.
     */
    case msg @ CollectionStats(_) =>
      webSocketSupervisor forward msg
  }

}
