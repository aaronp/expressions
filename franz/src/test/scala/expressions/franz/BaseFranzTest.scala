package expressions.franz

import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.ZIO
import zio.duration.{Duration, durationInt}

abstract class BaseFranzTest extends AnyWordSpec with Matchers with GivenWhenThen with Eventually with ScalaFutures {

  implicit val rt: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  def zenv = rt.environment

  def testTimeout: Duration = 30.seconds

  def shortTimeoutJava = 200.millis

  implicit def asRichZIO[A](zio: => ZIO[_root_.zio.ZEnv, Any, A])(implicit rt: _root_.zio.Runtime[_root_.zio.ZEnv]) = new {
    def value(): A = rt.unsafeRun(zio.timeout(testTimeout)).getOrElse(sys.error("Test timeout"))
  }
}
