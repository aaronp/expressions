package pipelines.reactive

trait HasMetadata {

  /** @return key/value pairs
    */
  def metadata: Map[String, String]

  def prefixedMetadata(prefix: String): Map[String, String] = {
    metadata.map {
      case (key, value) => s"$prefix.$key" -> value
    }
  }

  def id: Option[String] = metadata.get("id")
}

object HasMetadata {
  def apply(map: Map[String, String]) = new HasMetadata {
    override def metadata: Map[String, String] = map
  }
}
