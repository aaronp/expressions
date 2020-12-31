package expressions

import io.circe.Json
import io.circe.literal._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DynamicJsonTest extends AnyWordSpec with Matchers {

  val input: Json =
    json"""{
        "hello" : {
          "there" : true,
          "world" : [
          {
            "name" : "first",
            "nested" : [1,2,3]
          },
          {
            "name" : "second",
            "nested" : [4,5]
          }
          ]
        }
      }"""

  import DynamicJson.implicits._
  "DynamicJson" should {
    "allow us to drill down into a value field" in {
      input.asDynamic.hello.there.asBool() shouldBe true
      input.asDynamic.hello.missing.asBool() shouldBe false

      val ints = for {
        world <- input.asDynamic.hello.world
        x     <- world.asDynamic.nested.each
      } yield x.asInt()

//      val ints2 = input.asDynamic.hello.world.flatMap(_.nested).map(_.asInt())
      ints should contain only (1, 2, 3, 4, 5)
      input.asDynamic.hello.world.each.flatMap(_.nested.each.map(_.asInt())) should contain only (1, 2, 3, 4, 5)
    }
  }
}
