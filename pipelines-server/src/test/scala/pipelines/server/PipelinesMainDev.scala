package pipelines.server

import java.awt.Desktop
import java.net.URI

import pipelines.DevRestMain
import pipelines.mongo.StartMongo
import pipelines.rest.RunningServer
import pipelines.server.PipelinesMain.Bootstrap

import scala.io.StdIn

object PipelinesMainDev {

  def devArgs: Array[String] = "pipelines-server-dev.conf" +: DevRestMain.devArgs

  def main(a: Array[String]): Unit = {

    run(a) match {
      case None =>
        println("Goodbye!")
        sys.exit(0)
      case Some(startupFuture) =>
        lazy val dt = Desktop.getDesktop
        if (Desktop.isDesktopSupported && dt.isSupported(Desktop.Action.BROWSE)) {
          dt.browse(new URI("https://localhost:80"))
        }
        StdIn.readLine("Running dev main - hit any key to stop...")
    }
  }

  def run(a: Array[String]): Option[RunningServer[Bootstrap]] = {
    // let's get mon-going!
    StartMongo.main(a)

    PipelinesMain.runMain(a ++ devArgs)
  }
}
