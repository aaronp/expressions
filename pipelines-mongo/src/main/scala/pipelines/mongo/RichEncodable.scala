package pipelines.mongo

import io.circe.Encoder

class RichEncodable[A](value: A)(implicit encoder: Encoder[A]) {
  def asBson    = BsonUtil.asBson(encoder(value))
  def asBsonDoc = BsonUtil.asDocument(encoder(value))
}
