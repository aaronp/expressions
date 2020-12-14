package expressions.franz

import zio.{ExitCode, ZEnv, ZIO}

object KafkaSink {
  def apply(settings: FranzConfig): ZIO[ZEnv, Throwable, ExitCode] = {
//    BatchedStream(settings) { records =>
//      ???
//    }.useForever.exitCode
    ???
  }
}
