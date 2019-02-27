package esa.mongo
import org.mongodb.scala.MongoDatabase
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.duration._

trait BaseMongoSpec extends WordSpec with Matchers with Eventually with ScalaFutures with BeforeAndAfterAll {

  def testTimeout: FiniteDuration = 5.seconds

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

  def mongoEnv() = MongoEnv()

  lazy val mongoClient = MongoConnect("serviceUser", "changeTh1sDefaultPasswrd".toCharArray, "esa", "mongodb://localhost:9010")

  def mongoDb: MongoDatabase = mongoClient.getDatabase(databaseName)

  def databaseName = "esa"

  private var mongoIsRunningStateBeforeTest = false
  override def beforeAll(): Unit = {
    super.beforeAll()
    mongoIsRunningStateBeforeTest = isMongoRunning()
  }

  override def afterAll(): Unit = {
    if (!mongoIsRunningStateBeforeTest) {
      stopMongo()
    }
  }

  def isMongoRunning() = {
    mongoEnv.isMongoRunning()
  }

  def ensureMongoIsRunning(): Boolean = startMongo

  def startMongo() = {
    if (!isMongoRunning) {
      mongoEnv.start()
      eventually {
        isMongoRunning() shouldBe true
      }
    }
    true
  }

  def stopMongo() = {
    if (isMongoRunning) {
      mongoEnv.stop()
      withClue("is running never returned false") {
        eventually {
          isMongoRunning() shouldBe false
        }
      }
    }
    true
  }
}
