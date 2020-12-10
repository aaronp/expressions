package expressions.rest

import args4c.implicits._
import zio._
import zio.console.putStrLn
import zio.interop.catz._

/**
  * A REST application which will drive
  */
object Main extends CatsApp {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    val config = args.toArray.asConfig()

    import args4c.implicits._

    for {
      _          <- putStrLn("⭐⭐ Starting Service ⭐⭐")
      _          <- putStrLn(config.getConfig("app").summaryEntries().sortBy(_.key).map("\tapp." + _).mkString("\n"))
      _          <- putStrLn(s"PID:${ProcessHandle.current().pid()}")
      _          <- putStrLn("")
      restServer = RestApp(config)
      exitCode   <- restServer.serve
    } yield exitCode
  }
}
