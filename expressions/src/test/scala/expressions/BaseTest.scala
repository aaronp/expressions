package expressions

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

abstract class BaseTest extends AnyWordSpec with Matchers:
  extension (json: String)
    def jason = io.circe.parser.parse(json).toTry.get
