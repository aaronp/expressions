package pipelines.reactive

object Ids {
  private val ids = eie.io.AlphaCounter.from(System.currentTimeMillis)
  def next(): String = {
    ids.next()
  }
}
