package expressions.rest

import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.ZIO
import zio.duration.{Duration, durationInt}

abstract class BaseRestTest extends AnyWordSpec with Matchers with GivenWhenThen with Eventually with ScalaFutures {

  extension (json: String)
    def jason = io.circe.parser.parse(json).toTry.get

}

