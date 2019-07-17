package pipelines.rest.socket

case class SocketSettings(name: String, input: PipeSettings = PipeSettings(), output: PipeSettings = PipeSettings())
