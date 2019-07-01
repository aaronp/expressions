package pipelines.server

import java.awt.Desktop
import java.net.URI

import pipelines.rest.RunningServer

import scala.io.StdIn

object PipelinesMainDev {

  def devArgs: Array[String] =
    Array(
      "pipelines-server-dev.conf",                             //
      "dev.conf",                                              //
      "pipelines.echoSocket=true",                             //
      "generateMissingCerts=true",                             //
      "pipelines.tls.hostname=localhost",                      //
      "pipelines.tls.certificate=target/certificates/cert.p12" //
    )

  def main(a: Array[String]): Unit = {

    // let's get mongo going..
    dockerenv.mongo().start()

    PipelinesMain.runMain(a ++: devArgs) match {
      case None =>
        println("Goodbye!")
        sys.exit(0)
      case Some(startupFuture) =>
        import scala.concurrent.ExecutionContext.Implicits._
        startupFuture.foreach { server: RunningServer =>
          lazy val dt = Desktop.getDesktop
          if (Desktop.isDesktopSupported && dt.isSupported(Desktop.Action.BROWSE)) {
            dt.browse(new URI("https://localhost:80"))
          }
          StdIn.readLine("Running dev main - hit any key to stop...")
        }
    }
  }
}
