package pipelines.rest.socket

case class SocketSettings(id: String, name: String, input: PipeSettings = PipeSettings(), output: PipeSettings = PipeSettings())

object SocketSettings {
  def apply(id: String): SocketSettings = {
    new SocketSettings(id, id)
  }
}
