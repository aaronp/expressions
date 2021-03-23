package expressions.franz.admin

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.clients.admin._
import org.apache.kafka.common.KafkaFuture

import java.util
import java.util.Properties
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/**
  * A wrapper onto the admin API
  *
  * @param admin the underlying kafka client
  */
final class RichKafkaAdmin(val admin: AdminClient) extends AutoCloseable with StrictLogging {

  @volatile private var closed = false

  /**
    * @param topic             the topic
    * @param numPartitions     the number of partitions
    * @param replicationFactor the replication factor to use when the topic does not exist
    * @param ec
    * @return a future (async result) of an option which will be None if the topic already exists and Some(topic) if it was created
    */
  def getOrCreateTopic(topic: String, numPartitions: Int, replicationFactor: Short, timeout: FiniteDuration)(implicit ec: ExecutionContext): Future[Option[String]] = {
    topics().map { topicsByName: Map[String, TopicListing] =>
      if (!topicsByName.contains(topic)) {
        createTopicBlocking(topic, numPartitions, replicationFactor, timeout)
        Option(topic)
      } else {
        None
      }
    }
  }

  def createTopicBlocking(topic: String, numPartitions: Int, replicationFactor: Short, timeout: FiniteDuration): Unit = {
    val jFuture = createTopic(topic, numPartitions, replicationFactor).all()
    jFuture.get(timeout.toMillis, TimeUnit.MILLISECONDS)
  }

  def createTopicSync(name: String, timeout: FiniteDuration): Unit = {
    val fut: KafkaFuture[Void] = createTopic(name).values().get(name)
    fut.get(timeout.toMillis, TimeUnit.MILLISECONDS)
  }

  def createTopic(name: String, numPartitions: Int = 1, replicationFactor: Short = 1): CreateTopicsResult = {
    createTopic(new NewTopic(name, numPartitions, replicationFactor))
  }

  def createTopic(topic: NewTopic): CreateTopicsResult = {
    admin.createTopics(java.util.Collections.singletonList(topic))
  }

  def metrics = admin.metrics.asScala.toMap

  def topics(options: ListTopicsOptions = new ListTopicsOptions)(implicit ec: ExecutionContext): Future[Map[String, TopicListing]] = {
    val kFuture: KafkaFuture[util.Map[String, TopicListing]] = admin.listTopics(options).namesToListings()
    Future(kFuture.get().asScala.toMap)
  }

  def deleteTopic(topic: String, theRest: String*)(implicit ec: ExecutionContext): Future[Unit] = deleteTopic(theRest.toSet + topic)
  def deleteTopic(topics: Set[String])(implicit ec: ExecutionContext): Future[Unit] = {
    Future(admin.deleteTopics(topics.asJava).all().get())
  }

  def isClosed() = closed

  override def close(): Unit = {
    if (!closed) {
      logger.warn("Closing the admin client")
      closed = true
      admin.close()
    }
    logger.warn("Closed the admin client")
  }
}

object RichKafkaAdmin extends StrictLogging {

  def apply(rootConfig: Config = ConfigFactory.load()): RichKafkaAdmin = {
    val props: Properties  = Props.propertiesForConfig(rootConfig.getConfig("app.franz.kafka"))
    val admin: AdminClient = AdminClient.create(props)
    new RichKafkaAdmin(admin)
  }
}
