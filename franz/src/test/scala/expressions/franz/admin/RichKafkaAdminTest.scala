package expressions.franz.admin

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{FiniteDuration, _}

class RichKafkaAdminTest extends AnyWordSpec with Matchers with ScalaFutures {
  "RichKafkaAdmin" should {
    "delete all the topics" in {
      val admin = RichKafkaAdmin()
      val all   = admin.topics().futureValue

      println(s"Deleting ${all.size} topics: ${all.mkString("\n")}")
      admin.deleteTopic(all.keySet).futureValue
      val readBack = admin.topics().futureValue
      readBack.size shouldBe 0

    }
  }

  def testTimeout: FiniteDuration = 15.seconds

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

}
