package expressions.rest.server.record

import zio.Task

import java.nio.file.Path

object LiveRecorder {
  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def apply(onDumpSession: (Path, String) => Unit = (_, _) => ()): Recorder.Buffer = {
    val sessionId = System.currentTimeMillis()
    Recorder(sessionId)(onDumpSession)
  }

  def recordSession(): String => Task[Unit] = LiveRecorder(createFeature).log

  def createFeature(dir: Path, testName: String) = {
    logger.info(s"Created feature in ${dir}")
    ???
  }
}
