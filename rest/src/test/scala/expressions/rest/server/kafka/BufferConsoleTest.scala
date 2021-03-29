package expressions.rest.server.kafka

import expressions.rest.server.BaseRouteTest
import zio.console.{Console, putStrLn}
import zio.{Has, ZEnv, ZIO}

class BufferConsoleTest extends BaseRouteTest {
  "BufferConsole" should {
    "capture output when mixed w/ ZEnv" in {

      val testCase = for {
        baseEnv       <- ZIO.environment[ZEnv]
        buffer        <- BufferConsole.make
        testEnv: ZEnv = baseEnv ++ Has[Console.Service](buffer) // <-- we have to explicitly type the 'Has' for this to pass/work
        _             <- putStrLn("one").provide(testEnv)
        stdOut        <- buffer.stdOut
      } yield stdOut
      testCase.value() shouldBe List("one\n")
    }
  }
}
