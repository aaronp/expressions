package pipelines.mongo

import com.mongodb.ConnectionString
import com.typesafe.config.Config
import io.circe.Json
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.model.{CreateCollectionOptions, IndexOptions}
import org.mongodb.scala.{MongoClient, MongoClientSettings, MongoCredential}
import pipelines.mongo.MongoConnect.DatabaseConfig

import scala.util.Try

class MongoConnect(mongoConfig: Config) {

  def user     = mongoConfig.getString("user")
  def database = mongoConfig.getString("database")
  def uri      = mongoConfig.getString("uri")

  def databaseConfig(name: String): DatabaseConfig = DatabaseConfig(mongoConfig.getConfig(s"databases.$name"))

  def client: MongoClient = {
    val pwd   = mongoConfig.getString("password").toCharArray
    val creds = MongoCredential.createCredential(user, database, pwd)
    MongoClientSettings
      .builder()
      .applyConnectionString(new ConnectionString(uri))
      .codecRegistry(DEFAULT_CODEC_REGISTRY)
      .credential(creds)
      .build()
    MongoClient(uri)
  }
}
object MongoConnect extends LowPriorityMongoImplicits {

  case class IndexConfig(config: Config) {

    private def unique     = config.getBoolean("unique")
    private def background = config.getBoolean("background")
    private def field      = config.getString("field")
    private def fields: Seq[String] = {
      import args4c.implicits._
      if (config.hasPath("fields")) {
        config.asList("fields")
      } else {
        Nil
      }

    }
    private def ascending = Try(config.getBoolean("ascending")).getOrElse(true)

    private def bson: Json = {
      fields match {
        case Seq() => Json.obj(field -> Json.fromInt(if (ascending) 1 else -1))
        case many =>
          val map: Seq[(String, Json)] = many.map { name =>
            name -> Json.fromInt(if (ascending) 1 else -1)
          }
          Json.obj(map: _*)
      }
    }
    def asBsonDoc = {
      BsonUtil.asDocument(bson)
    }
    def asOptions: IndexOptions = {
      IndexOptions().unique(unique).background(background)
    }

    def name: String = {
      if (config.hasPath("name")) {
        config.getString("name")
      } else {
        field + "Index"
      }
    }

  }

  case class DatabaseConfig(config: Config) {
    def capped               = config.getBoolean("capped")
    def maxSizeInBytes: Long = config.getMemorySize("maxSizeInBytes").toBytes
    def maxDocuments         = config.getLong("maxDocuments")
    def indices: List[IndexConfig] = {
      import scala.collection.JavaConverters._
      config.getConfigList("indices").asScala.map(IndexConfig.apply).toList
    }

    def asOptions(): CreateCollectionOptions = {
      val opts = CreateCollectionOptions().capped(capped).maxDocuments(maxDocuments)
      maxSizeInBytes match {
        case 0L => opts
        case n  => opts.sizeInBytes(n)
      }
    }
  }

  def apply(rootConfig: Config): MongoConnect = forMongoConfig(rootConfig.getConfig("pipelines.mongo"))

  def forMongoConfig(config: Config): MongoConnect = new MongoConnect(config)
}
