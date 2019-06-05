package pipelines.connect

import com.typesafe.config.ConfigFactory
import dockerenv.BaseKafkaSpec
import org.scalatest.concurrent.ScalaFutures
import pipelines.{Using, WithScheduler}

import scala.concurrent.duration._

/**
  * The 'main' test -- mixing in other test traits gives us the ability to have a 'before/after all' step which can
  * apply to ALL our tests, and so we don't stop/start e.g. kafka container for each test suite
  */
class RichKafkaConsumerTest extends BaseKafkaSpec with ScalaFutures {

  override def testTimeout: FiniteDuration = 5.seconds

  "RichKafkaConsumer" should {
    "consume kafka messages" in {
      WithScheduler { implicit sched =>
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
}
