package actors

import actors.TweetStreamActor.Ready
import akka.actor.{Actor, ActorRef}
import com.fasterxml.jackson.databind.JsonNode
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import org.apache.spark.streaming._
import org.apache.spark.streaming.dstream.ReceiverInputDStream
import org.slf4j.LoggerFactory
import play.api.{Logger, Configuration}
import play.api.libs.json.{Json, Writes}
import play.mvc.WebSocket
import twitter4j.Status

import scala.language.postfixOps


object UserHashtagCounter {
  val logger = LoggerFactory.getLogger(getClass)

  object Defaults {
    val HashtagCountWindowSize = 10  // The number of seconds between each report back to supervisor
  }

  implicit val hashtagCountWrites = new Writes[UserHashtagCount] {
    def writes(hashtagCount: UserHashtagCount) = Json.obj(
      "username" -> hashtagCount.username,
      "hashtag" -> hashtagCount.hashtag,
      "count" -> hashtagCount.count
    )
  }

  case class ActiveTwitterStream(dStream: ReceiverInputDStream[Status])
  case class ActivateOutputStream(out: WebSocket.Out[JsonNode])
  case class HashtagCount(hashtag: String, count: Int)
  case class UserHashtagCount(username: String, hashtag: String, count: Int)
  case class UserHashtagReport(counts: Seq[UserHashtagCount])
}

/**
  * Counts hashtags on a per-user basis within a window.
  * Reports back to dispatcher every `Defaults.HashtagCountWindowSize` seconds.
 */

@Singleton
class UserHashtagCounter @Inject()
  (@Named("redisWriter") redisWriter: ActorRef,
    configuration: Configuration) extends Actor {

  import actors.UserHashtagCounter._

  // Get the report frequency configuration or use the default value
  val reportFrequency = configuration.getInt("analysis.counts.windowSize")
    .getOrElse(Defaults.HashtagCountWindowSize)

  override def receive = {
    case ActiveTwitterStream(stream) =>
      processStream(stream)  // Initialise the counting of hashtags
      sender ! Ready()
    case _ =>
      logger.warn(s"${getClass.getName} received an unrecognised request.")
  }

  def processStream(stream: ReceiverInputDStream[Status]): Unit = {

    // Status -> several tuples of form ((user, hashtag), 1)
    val userHashtags = stream.flatMap(status => {
      status.getText.split(" ")
        .filter(_.startsWith("#"))
        .map(hashtag => ((status.getUser.getScreenName, hashtag toLowerCase()), 1))
    })

    // Counting aggregation of (user, hashtag) pairs
    val hashtagCountInWindow = userHashtags
      .reduceByKeyAndWindow((p:Int, q:Int) => p+q, Seconds(reportFrequency), Seconds(reportFrequency))
      .map{case ((user, hashtag), count) => (count, (user, hashtag))}
      .transform(_.sortByKey(ascending = false))
      .map{case (count, (user, hashtag)) => UserHashtagCount(user, hashtag, count)}

    // Send reports to Redis
    hashtagCountInWindow.foreachRDD(rdd => {
      val userHashtagCounts = rdd.collect()
      redisWriter ! UserHashtagReport(userHashtagCounts)
    })

  }

}
