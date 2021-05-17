package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigFactory}
import expressions.client.TransformResponse
import expressions.rest.server.RestRoutes.Resp
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{Response, Status}
import zio.console.{Console, putStrLn}
import zio.duration.durationInt
import zio.{Cause, Has, Task, UIO, ZEnv, ZIO}

import scala.util.{Failure, Success}
object BatchCheck {

  def apply(defaultConfig: Config, defaultEnv: ZEnv): BatchCheckRequest => ZIO[Any, Throwable, Resp] = {
    val compiler = BatchTemplate.cached
    (dto: BatchCheckRequest) =>
      {
        val config = ConfigFactory.parseString(dto.rootConfig).withFallback(defaultConfig)
        for {
          buffer  <- BufferConsole.make
          testEnv = defaultEnv ++ Has[Console.Service](buffer) // <-- see BufferConsoleTest
          response <- BatchContext(config).either
            .use {
              case Left(errorCreatingContext) =>
                val body = TransformResponse(s"Error creating batch context: $errorCreatingContext".asJson,
                                             success = false,
                                             List(s"Couldn't create context: ${errorCreatingContext.getMessage}"))
                UIO(Response[Task](status = Status.Ok).withEntity(body))
              case Right(batchContext) =>
                compiler(dto.script) match {
                  case Success(handler) => executeBatchScript(buffer, testEnv, dto, batchContext, handler)
                  case Failure(err) =>
                    val body = TransformResponse(s"didn't work w/ input: ${dto}".asJson, success = false, List(s"didn't work w/ input: ${err.getMessage}"))
                    UIO(Response[Task](status = Status.Ok).withEntity(body))
                }
            }
            .provide(testEnv)
        } yield response
      }
  }

  private def executeBatchScript(buffer: BufferConsole, testEnv: ZEnv, dto: BatchCheckRequest, batchContext: BatchContext, handler: OnBatch): UIO[Resp] = {
    val batches = dto.asBatchInputs(batchContext)
    for {
      result <- ZIO.foreach(batches)(handler).provide(testEnv).sandbox.either
      output <- buffer.output
      (success, messages) = result match {
        case Right(_) => true -> List("Success!")
        case Left(err: Cause[Throwable]) =>
          val fails: List[Throwable] = err.failures
          val msgs = List(
            "The handler threw an exception:",
            err.prettyPrint,
            s"${err.failures.size} failures:"
          ) ++ fails.map(_.toString)
          false -> msgs
      }
    } yield {
      Response(status = Status.Ok).withEntity(TransformResponse(output.asJson, success, messages))
    }
  }
}
