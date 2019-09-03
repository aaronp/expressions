package pipelines.rest.socket

case class SocketSettings(name: String, leaveSourceOpen: Boolean = true, leaveSinkOpen: Boolean = true, input: PipeSettings = PipeSettings(), output: PipeSettings = PipeSettings())
