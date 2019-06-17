package pipelines.mongo

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import dockerenv.BaseMongoSpec
import org.mongodb.scala.{MongoClient, MongoDatabase}

import scala.concurrent.duration._
import scala.util.Success

abstract class BasePipelinesMongoSpec extends BaseMongoSpec with LowPriorityMongoImplicits with StrictLogging {

  override def testTimeout = 5.seconds

  lazy val rootConfig = ConfigFactory.load()

  def newClient(): MongoClient = MongoConnect(rootConfig).client

  var mongoClient: MongoClient = null
  def mongoDb: MongoDatabase   = mongoClient.getDatabase("test-db")

  def configForCollection(collectionName: String,
                          basedOn: String = "users",
                          maxDocuments: Int = 0,
                          capped: Boolean = true,
                          maxSize: String = "500M",
                          pollFreq: String = "100ms"): Config = {
    val c = ConfigFactory.parseString(s"""
                                         |pipelines.mongo.databases {
                                         |    ${collectionName} = $${pipelines.mongo.databases.${basedOn}}
                                         |    ${collectionName}.maxDocuments: $maxDocuments
                                         |    ${collectionName}.capped: $capped
                                         |    ${collectionName}.maxSizeInBytes: $maxSize
                                         |    ${collectionName}.pollFrequency: $pollFreq
                                         |}""".stripMargin)
    c.withFallback(rootConfig).resolve()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()

    val listOutput = eventually {
      val Success((0, output)) = dockerEnv.runInScriptDir("mongo.sh", "listUsers.js")
      output
    }

    if (!listOutput.contains("serviceUser")) {
      val createOutput = eventually {
        val Success((0, output)) = dockerEnv.runInScriptDir("mongo.sh", "createUser.js")
        output
      }
      createOutput should include("serviceUser")
    }

    mongoClient = newClient()
  }

  override def afterAll(): Unit = {
    import scala.collection.JavaConverters._

    if (mongoClient != null) {
      mongoClient.close()
      mongoClient = null
    }

    val threads = Thread.getAllStackTraces.asScala.collect {
      case (thread, stack) if stack.exists(_.getClassName.contains("mongodb")) => thread
    }
    logger.error(s"""MongoClient.close() ... doesn't. Interrupting ${threads.size} threads""".stripMargin)
    threads.foreach(_.stop())

    super.afterAll()
  }
}
