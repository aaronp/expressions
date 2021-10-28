package expressions.rest.server.db

import zio.{Runtime, ZEnv, ZIO}

import scala.concurrent.duration.{FiniteDuration, _}

object ZioSupport {
  def testTimeout: FiniteDuration = 10.seconds

  implicit val rt: Runtime[ZEnv] = Runtime.default

  def zenv = rt.environment

  /**
    * An easy way to just call '.value()' on a ZIO value
    */
  extension [A](zio: => ZIO[ZEnv, Any, A])(using rt: _root_.zio.Runtime[_root_.zio.ZEnv])
    def value(): A = rt.unsafeRun(zio.timeout(_root_.zio.duration.Duration.fromScala(testTimeout))).getOrElse(sys.error("Test timeout"))

}
