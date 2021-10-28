package expressions.rest.server.kafka

/**
  * The input to our user-function.
  * @param batch the input records
  * @param context a context containing things we'll want to use (REST clients, DB clients, kafka APIs, ...)
  */
final case class BatchInput(batch: Batch, context: BatchContext)
