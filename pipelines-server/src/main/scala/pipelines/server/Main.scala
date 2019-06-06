package pipelines.server

import java.awt.Desktop
import java.net.URI

import com.typesafe.scalalogging.StrictLogging
import pipelines.rest

import scala.io.StdIn

object Main extends StrictLogging {

  def devArgs: Array[String] =
    Array(
      "pipelinesServer.conf",                                              //
      "pipelines.echoSocket=true",                             //
      "generateMissingCerts=true",                             //
      "pipelines.tls.hostname=localhost",                      //
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
