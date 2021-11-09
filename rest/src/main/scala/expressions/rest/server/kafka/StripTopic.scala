package expressions.rest.server.kafka

object StripTopic {

  def unapply(topic : String) : Option[String] = topic  match {
    case s"${x}-key" => Some(x)
    case s"${x}-value" => Some(x)
    case x => Some(x)
  }
}
