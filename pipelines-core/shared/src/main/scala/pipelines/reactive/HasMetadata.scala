package pipelines.reactive

trait HasMetadata {

  /** @return key/value pairs
    */
  def metadata: Map[String, String]

  /**
    * transforms the meta by adding a prefix, which is useful when transforming/wrapping an existing datasource so as to keep
    * the original metadata in a way which keeps it separate from other metadata (e.g. 'wrapped.id' = 123, 'id' = 456)
    *
    * @param prefix
    * @return
    */
  def prefixedMetadata(prefix: String): Map[String, String] = {
    metadata.map {
      case (key, value) => s"$prefix.$key" -> value
    }
  }

  def id: Option[String] = metadata.get(tags.Id)
  def name : Option[String] = metadata.get(tags.Name)
}

object HasMetadata {

  def apply(map: Map[String, String]) = new HasMetadata {
    override def metadata: Map[String, String] = map
  }
}
