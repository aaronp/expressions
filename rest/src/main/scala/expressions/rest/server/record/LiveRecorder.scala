package expressions.rest.server.record

import com.typesafe.scalalogging.StrictLogging
import zio.Task

import java.nio.file.Path

object LiveRecorder extends StrictLogging {
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
