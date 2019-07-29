package pipelines.mongo

import com.typesafe.config.Config
import monix.execution.{CancelableFuture, Scheduler}
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import pipelines.mongo.MongoConnect.IndexConfig

case class CollectionSettings(dbConfig: MongoConnect.DatabaseConfig, indices: List[IndexConfig], collectionName: String) {
//  def mongoDb(client: MongoClient): MongoDatabase = client.getDatabase(mongo.database)

  val options = dbConfig.asOptions()
  def ensureCreated(mongoDb: MongoDatabase)(implicit sched: Scheduler): CancelableFuture[MongoCollection[Document]] = {
    import LowPriorityMongoImplicits._
    mongoDb.setup(this)
  }
}
object CollectionSettings {
  def apply(rootConfig: Config, collectionName: String): CollectionSettings = {
    //val mongo: MongoConnect                  =
    val dbConfig: MongoConnect.DatabaseConfig = MongoConnect.DatabaseConfig(rootConfig, collectionName)
    new CollectionSettings(dbConfig, dbConfig.indices, collectionName)
  }
}
