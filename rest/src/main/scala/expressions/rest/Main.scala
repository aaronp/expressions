package expressions.rest

import args4c.implicits.*
import com.typesafe.config.Config
import zio.*
import zio.console.{Console, putStrLn}
import zio.interop.catz.*
import args4c.implicits.RichArgs

/**
  * A REST application which will drive
  */
object Main extends CatsApp {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    val config = args.toArray.asConfig()

    val app = for {
      _          <- putStrLn("⭐⭐ Starting Service ⭐⭐")
      _          <- putStrLn(configSummary(config))
      _          <- putStrLn(s"PID:${ProcessHandle.current().pid()}")
      _          <- putStrLn("")
      restServer = RestApp(config)
      //
      // TODO - tie in these key/value types w/ the config by checking the configured serde type
      //
      exitCode <- restServer.serve(config)
    } yield exitCode

    app.either.map {
      case Left(_)     => ExitCode.failure
      case Right(code) => code
    }
  }

  def configSummary(config: Config): String = {
    config
      .getConfig("app")
      .summaryEntries()
      .sortBy(_.key)
      .map("\tapp." + _)
      .mkString("\n")
  }
}
