package expressions

import io.circe.Json

class DynamicJsonTest extends BaseTest {

  val input: Json =
    """{
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
      }""".jason

  import DynamicJson.implicits._
  "DynamicJson" should {
    "allow us to drill down into a value field" in {
      input.asDynamic.hello.there.asBool() shouldBe true
      input.asDynamic.hello.missing.asBool() shouldBe false

      val ints = for {
        world <- input.asDynamic.hello.world
        x     <- world.nested.each
      } yield x.asInt()

      ints should contain only (1, 2, 3, 4, 5)
      input.asDynamic.hello.world.each.flatMap(_.nested.each.map(_.asInt())) should contain only (1, 2, 3, 4, 5)
    }
  }
}
