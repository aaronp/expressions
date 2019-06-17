package pipelines.mongo

import com.typesafe.config.Config
import monix.execution.{CancelableFuture, Scheduler}
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import org.mongodb.scala.model.CreateCollectionOptions
import pipelines.mongo.MongoConnect.IndexConfig

case class CollectionSettings(mongo: MongoConnect, usersDb: CreateCollectionOptions, indices: List[IndexConfig], collectionName: String) {
  def mongoDb: MongoDatabase = mongo.client.getDatabase(mongo.database)

  def ensureCreated(implicit sched: Scheduler): CancelableFuture[MongoCollection[Document]] = {
    import LowPriorityMongoImplicits._
    mongoDb.setup(this)
  }
}
object CollectionSettings {
  def apply(rootConfig: Config, collectionName: String): CollectionSettings = {
    val mongo: MongoConnect                  = MongoConnect(rootConfig)
    val usersDb: MongoConnect.DatabaseConfig = mongo.databaseConfig(collectionName)
    new CollectionSettings(mongo, usersDb.asOptions(), usersDb.indices, collectionName)
  }
}
