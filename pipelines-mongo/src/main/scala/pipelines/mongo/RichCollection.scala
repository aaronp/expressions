package pipelines.mongo

import io.circe.Encoder
import monix.reactive.Observable
import org.mongodb.scala.{Completed, Document, MongoCollection}

class RichCollection(val collection: MongoCollection[Document]) extends LowPriorityMongoImplicits {

  def insertOne[T: Encoder](value: T): Observable[Completed] = {
    val doc = BsonUtil.asDocument(value)
    collection.insertOne(doc).monix
  }

}
