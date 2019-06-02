package pipelines.reactive

import monix.reactive.Consumer

class ConsumerSyntax[In, Out](val consumer: Consumer[In, Out], val contentType: ContentType) {
  def asDataSink(metadata: Map[String, String] = Map.empty): DataSink = {
    DataSink(consumer, metadata, contentType)
  }
  def asDataSink(metadata: (String, String), theRest: (String, String)*): DataSink = {
    val map = theRest.toMap + metadata
    asDataSink(map.ensuring(_.size == theRest.size + 1, "duplicate metadata keys"))
  }

}
