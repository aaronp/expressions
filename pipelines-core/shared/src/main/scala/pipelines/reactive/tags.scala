package pipelines.reactive

/**
  * some constants we can use for metadata tags.
  *
  * These *could* potentially enums, or something else entirely. The idea is that to just support some key/value pairs for
  * addressing sources, sinks, etc. which can then be queried on.
  *
  * e.g. connect sources with the tag 'MyApp:Meh' with sinks 'User:FooAdmin'
  */
object tags {

  val CreatedBy   = "createdBy"
  val Label       = "label"
  val Name        = "name"
  val ContentType = "contentType"
  val Id          = "id"
  val Tag         = "tag"
  val UserName    = "userName"
  val UserId      = "userId"
  val Persist     = "persist"

  val SourceType = "sourceType"
  val SinkType   = "sinkType"

  object transforms {
    val `SourceEvent.asAddressedMessage` = "SourceEvent.asAddressedMessage"
    val `SinkEvent.asAddressedMessage` = "SinkEvent.asAddressedMessage"
    val `Pipeline.asAddressedMessage` = "Pipeline.asAddressedMessage"
  }

  object labelValues {
    val HttpRequests = "http-requests"
    val HttpRequestResponse = "http-request-response"
    val SourceEvents = "sourceEvents"
    val SinkEvents = "sinkEvents"
    val PipelineCreatedEvents = "pipelineCreatedEvents"
    val MatchEvents = "matchEvents"
  }
  // hmm -- enums?
  object typeValues {
    val Push                 = "push"
    val Socket               = "socket"
    val SubscriptionListener = "socketSubscriptionListener"
  }

}
