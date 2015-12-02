package actors

import actors.UserHashtagCounter.ActiveTwitterStream
import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import init.{SparkInit, TwitterAuth}
import org.apache.spark.streaming.twitter.TwitterUtils
import play.api.Logger
import twitter4j.Status

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await


object TweetStreamActor {
  val DefaultBatchSize = 20
  case class TweetBatch(tweets: List[Status])
  case class Ready()
}

@Singleton
class TweetStreamActor @Inject()
  (@Named("webSocketSupervisor") webSocketSupervisor: ActorRef,
   @Named("userHashtagCounter") userHashtagCounter: ActorRef,
   @Named("featureExtraction") featureExtraction: ActorRef,
   @Named("userIndexing") userIndexing: ActorRef
)
  extends Actor with TwitterAuth {

  import TweetStreamActor._

  implicit val timeout = Timeout(20 seconds)

  Logger.info("Initialising TweetStreamActor")
  checkTwitterKeys()
  val streamHandle = TwitterUtils.createStream(SparkInit.ssc, None)
  sendTweetBatches()

  val responses = for {
    hashtagCounterReady <- userHashtagCounter ? ActiveTwitterStream(streamHandle)
    featureExtractionReady <- featureExtraction ? ActiveTwitterStream(streamHandle)
    userIndexingReady <- userIndexing ? ActiveTwitterStream(streamHandle)
  } yield (hashtagCounterReady, featureExtractionReady, userIndexingReady)

  val (hashtagCounterReady, featureExtractionReady, userIndexingReady) = Await.result(responses, 20 seconds)

  streamHandle.context.start()
  streamHandle.context.awaitTermination()

  override def receive: Actor.Receive = {
    case _ => Logger.error("No message receipt actions defined for TweetStreamActor")
  }

  def sendTweetBatches(): Unit = {
    streamHandle.foreachRDD(rdd => {
      webSocketSupervisor ! TweetBatch(rdd.take(DefaultBatchSize).toList)
    })
  }

}
