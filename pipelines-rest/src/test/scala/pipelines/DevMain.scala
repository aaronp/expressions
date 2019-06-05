package pipelines

import java.awt.Desktop
import java.net.URI

import com.typesafe.scalalogging.StrictLogging

import scala.io.StdIn

/**
  * This entry-point puts the test resources on the classpath, and so serves as a convenience for running up the Main entry-point for local development work.
  *
  */
object DevMain extends StrictLogging {

  def devArgs: Array[String] =
    Array(
      "dev.conf", //
      "pipelines.echoSocket=true", //
      "generateMissingCerts=true", //
      "pipelines.tls.hostname=localhost", //
      "pipelines.tls.certificate=target/certificates/cert.p12" //
    )

  def main(a: Array[String]): Unit = {

    val opt = rest.Main.runMain(a ++: devArgs)
    if (opt.nonEmpty) {
      lazy val dt = Desktop.getDesktop
      if (Desktop.isDesktopSupported && dt.isSupported(Desktop.Action.BROWSE)) {
        dt.browse(new URI("https://localhost:80"))
      }
      StdIn.readLine("Running dev main - hit any key to stop...")
    }
    println("Goodbye!")
    sys.exit(0)
  }
}
