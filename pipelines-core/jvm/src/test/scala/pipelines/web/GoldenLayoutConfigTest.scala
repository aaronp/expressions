package pipelines.web

import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import org.scalatest.{Matchers, WordSpec}

class GoldenLayoutConfigTest extends WordSpec with Matchers {

  "GoldenLayoutConfig encoding" should {
    "serialize to/from golden layout json" in {
      val layout = {
        def test(name: String, label: String): GoldenLayoutConfig = GoldenLayoutConfig.component(name, Json.obj("label" -> Json.fromString(label)))

        val col = GoldenLayoutConfig.column() :+ test("test", "B") :+ test("test2", "C")
        GoldenLayoutConfig.row() :+ test("test", "A") :+ col
      }

      val json = layout.asJson.spaces4
      val back = decode[GoldenLayoutConfig](json)
      back shouldBe Right(layout)
    }
  }
}
