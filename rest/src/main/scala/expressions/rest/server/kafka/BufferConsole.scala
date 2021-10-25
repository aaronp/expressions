package expressions.rest.server.kafka

import zio.console.Console
import zio.{IO, Ref, UIO, ZIO}
import io.circe.Codec
import java.io.IOException

final class BufferConsole(stdOutRef: Ref[List[String]], stdErrRef: Ref[List[String]]) extends Console.Service {

  def stdOut: UIO[List[String]] = stdOutRef.get
  def stdErr: UIO[List[String]] = stdErrRef.get
  def output: UIO[BufferConsole.Output] =
    for {
      out <- stdOut
      err <- stdErr
    } yield BufferConsole.Output(out, err)

  override def putStr(line: String): UIO[Unit] = stdOutRef.update(line :: _)

  override def putStrErr(line: String): UIO[Unit] = stdErrRef.update(line :: _)

  override def putStrLn(line: String): UIO[Unit] = putStr(s"${line}\n")

  override def putStrLnErr(line: String): UIO[Unit] = putStrErr(s"${line}\n")

  override def getStrLn: IO[IOException, String] = {
    IO("").refineOrDie {
      case e: IOException => e
    }
  }
}
object BufferConsole {

  case class Output(stdOut: Seq[String], stdErr: Seq[String])
  object Output {
    given codec : Codec[Output] = io.circe.generic.semiauto.deriveCodec[Output]
  }

  val make: UIO[BufferConsole] = {
    for {
      stdOut <- Ref.make(List[String]())
      stdErr <- Ref.make(List[String]())
    } yield new BufferConsole(stdOut, stdErr)
  }
}
