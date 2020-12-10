package expressions

import expressions.template.Message
import io.circe.literal._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success

object JsonTemplateTest {
  case class HttpRequest(method: String, url: String, headers: Map[String, String])
}
class JsonTemplateTest extends AnyWordSpec with Matchers {

  import JsonTemplateTest.HttpRequest
  implicit val jason = json"""{
          "hello" : {
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

  "JsonTemplate" should {
    "be able to map json" in {
      import expressions.implicits._
      val it = new RichDynamicJson(jason)
      val requests = it.hello.world.flatMapSeq { json =>
        json.nested.mapAs { i =>
          val m = json.name.get[String] match {
            case "first" => "GET"
            case other   => s"other${other}"
          }
          val url = s"${json.name.string.get}-$i"
          HttpRequest(method = m, url = url, Map.empty)
        }
      }
      requests shouldBe List(
        HttpRequest("GET", "first-1", Map()),
        HttpRequest("GET", "first-2", Map()),
        HttpRequest("GET", "first-3", Map()),
        HttpRequest("othersecond", "second-4", Map()),
        HttpRequest("othersecond", "second-5", Map())
      )
    }
    "be able to map json from a json context" in {

      val script = """
                     |import _root_.expressions.JsonTemplateTest.HttpRequest
                     |
                     |record.value.hello.world.flatMapSeq { json =>
                     |        json.nested.mapAs { i =>
                     |          val m = json.name.get[String] match {
                     |            case "first" => "GET"
                     |            case other   => s"other${other}"
                     |          }
                     |          val url = s"${json.name.string.get}-$i"
                     |          HttpRequest(method = m, url = url, Map.empty)
                     |        }
                     |}""".stripMargin

      println(script)
      println(script)
      val Success(mappingCode) = JsonTemplate[Seq[HttpRequest]](script)

      val input = Message(new RichDynamicJson(jason)).asContext()
      mappingCode(input) shouldBe List(
        HttpRequest("GET", "first-1", Map()),
        HttpRequest("GET", "first-2", Map()),
        HttpRequest("GET", "first-3", Map()),
        HttpRequest("othersecond", "second-4", Map()),
        HttpRequest("othersecond", "second-5", Map())
      )
    }

    // unignore to run -
    "be performant (when run locally this did about 150k/s" ignore {

      val script = """
                     |import _root_.expressions.JsonTemplateTest.HttpRequest
                     |
                     |record.value.hello.world.flatMapSeq { json =>
                     |        json.nested.mapAs { i =>
                     |          val m = json.name.get[String] match {
                     |            case "first" => "GET"
                     |            case other   => s"other${other}"
                     |          }
                     |          val url = s"${json.name.string.get}-$i"
                     |          HttpRequest(method = m, url = url, Map.empty)
                     |        }
                     |}""".stripMargin

      val Success(mappingCode) = JsonTemplate[Seq[HttpRequest]](script)

      val input = Message(new RichDynamicJson(jason)).asContext()
      val stats = Stats(3) {
        mappingCode(input)
      }

      println(stats)
    }
    "be able to reach into nested" in {
      import expressions.implicits._
      import io.circe.optics.JsonPath._

      val worlds = root.hello.world.each.obj.toList
      worlds.size shouldBe 2
      root.hello.world.each.nested.each.toList.size shouldBe 5
      root.hello.world.each.nested.each.int.toList shouldBe List(1, 2, 3, 4, 5)
    }

    "be able to flatMap over optional blocks" in {

      import expressions.implicits._

      val it = new RichDynamicJson(jason)
      val ints = it.hello.world.flatMap { json =>
        json.nested.each.int
      }
      ints shouldBe List(1, 2, 3, 4, 5)
    }

    "be able to flatMapSeq over iterable Optionals" in {
      import expressions.implicits._

      val it = new RichDynamicJson(jason)
      val names = it.hello.world.flatMapSeq { json =>
        List(json.name.string)
      }
      names shouldBe List("first", "second")
    }

    "be able to map over nested jpaths" in {
      import expressions.implicits._
      val it = new RichDynamicJson(jason)
      val ints = it.hello.world.flatMap { json =>
        json.nested.each.int
      }
      ints shouldBe List(1, 2, 3, 4, 5)

      val names2 = it.hello.world.map { json =>
        json.name.string
      }
      names2 shouldBe List("first", "second")
    }

    "be able to map over nested values" in {
      import expressions.implicits._
      val it = new RichDynamicJson(jason)
      val ints = it.hello.world.flatMap { json =>
        json.nested.each.int
      }
      ints shouldBe List(1, 2, 3, 4, 5)

      val names2 = it.hello.world.mapAs { json: RichDynamicJson =>
        json.name.string.get
      }
      names2 shouldBe List("first", "second")
    }
  }

}
