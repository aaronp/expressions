package pipelines.connect

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import dockerenv.BaseKafkaSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import pipelines.{Schedulers, Using, WithScheduler}

import scala.concurrent.duration._

/**
  * The 'main' test -- mixing in other test traits gives us the ability to have a 'before/after all' step which can
  * apply to ALL our tests, and so we don't stop/start e.g. kafka container for each test suite
  */
class RichKafkaConsumerTest extends BaseKafkaSpec with ScalaFutures with BeforeAndAfterAll with StrictLogging {

  override def testTimeout: FiniteDuration = 5.seconds

  "RichKafkaConsumer" should {
    "consume kafka messages" in {
      Schedulers.using { implicit sched =>
        val config = ConfigFactory.load()
        Using(RichKafkaProducer.strings(config)) { producer =>
          producer.send("foo", "testkey", "test").futureValue
          producer.send("bar", "testkey", "test").futureValue
          producer.send("example", "testkey", "test").futureValue

          Using(RichKafkaConsumer.strings(config)) { consumer: RichKafkaConsumer[String, String] =>
            eventually {
              consumer.topics() should not be (empty)
            }

            consumer.subscribe(Set("foo", "bar", "example"), Option("earliest"))
            val (cancel, received) = consumer.asObservable()

            val list = received.take(3).toListL.runToFuture
            eventually {
              val nonEmpty = list.futureValue
              nonEmpty.size shouldBe 3
            }
            cancel.cancel()
          }
        }
      }
    }
  }

  override def afterAll(): Unit = {
    import scala.collection.JavaConverters._
    def isKafka(stack: Array[StackTraceElement]): Boolean = {
      stack.exists(_.getClassName.contains("kafka"))
    }
    val threads = Thread.getAllStackTraces.asScala.collect {
      case (thread, stack) if isKafka(stack) => thread
    }
    logger.error(s"""KafkaProducer.close() ... doesn't. Interrupting ${threads.size} threads""".stripMargin)
    threads.foreach(_.stop())

    super.afterAll()
  }
}
