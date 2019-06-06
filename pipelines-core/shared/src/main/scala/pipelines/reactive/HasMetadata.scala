package pipelines.reactive

trait HasMetadata {

  /** @return key/value pairs
    */
  def metadata: Map[String, String]
}
