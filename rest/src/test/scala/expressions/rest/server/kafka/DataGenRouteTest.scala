package expressions.rest.server.kafka

import _root_.io.circe.Json
import eie.io
import eie.io.*
import expressions.client.TransformResponse
import expressions.franz.FranzConfig
import expressions.rest.server.BaseRouteTest
import org.glassfish.jersey.message.internal.MediaTypes
import org.http4s.{HttpRoutes, MediaType, Method, headers}

import scala.concurrent.ExecutionContext
//import eie.io.given
import cats.implicits.*
import zio.interop.catz.*
//import zio.interop.catz.{given, *}
//import cats.implicits.{given, *}
import org.http4s.multipart.{Multipart, Part}
import zio.*

import scala.util.Success

class DataGenRouteTest extends BaseRouteTest {

  "DataGen.parseContentAsJson" should {
    "be able to parse our test avro" in {
      val parsed = DataGenRoute.parseContentAsJson(exampleAvro, 123).value()
      parsed.hcursor.downField("day").as[String].toTry shouldBe Success("MONDAY")
    }
    "be able to parse some hocon" in {
      val parsed = DataGenRoute
        .parseContentAsJson("""bar : true
          |num : ber
          |a : [1,2,3]""".stripMargin,
                            123)
        .value()
      parsed.hcursor.downField("bar").as[Boolean].toTry shouldBe Success(true)
      parsed.hcursor.downField("num").as[String].toTry shouldBe Success("ber")
      parsed.hcursor.downField("a").as[List[Int]].toTry shouldBe Success(List(1, 2, 3))
    }
  }

  "DataGen POST data/parse" should {
    "be able to parse multipart uploads of avro" in {
      withTmpDir { dir =>
        Given("A multipart request")
        val file = dir.resolve("someFile.txt")
        eie.io.asRichPath(file).text = exampleAvro

        val multipart: Multipart[Task] = Multipart[Task](
          Vector(
            Part.fileData("file", file.toFile, headers.`Content-Type`(MediaType.text.`plain`))
          ))

        And("Our DataGenRoute under test")
        val underTest = DataGenRoute()
        When("We squirt a multipart request in w/ some avro")
        val request = post("/data/parse?seed=456").withEntity(multipart).withHeaders(multipart.headers)
        val testCase = for {
          Some(response) <- underTest(request).value
          _              = Then("we should get back some sample json")
          _              = response.status.code shouldBe 200
          body           = response.bodyAs[Json]
        } yield body

        val result = testCase.value()
        withClue(result.spaces2) {
          result.hcursor.downField("day").as[String].toTry shouldBe Success("SATURDAY")
        }
      }
    }
    "be able to parse POSTed avro REST requests" in {
      Given("Our DataGenRoute under test")
      val underTest = DataGenRoute()
      When("We squirt a some avro in ")
      val testCase = for {
        Some(response) <- underTest(post("/data/parse?seed=456", exampleAvro)).value
        _              = Then("we should get back some sample json")
        _              = response.status.code shouldBe 200
        body           = response.bodyAs[Json]
      } yield body

      val result = testCase.value()
      withClue(result.spaces2) {
        result.hcursor.downField("day").as[String].toTry shouldBe Success("SATURDAY")
      }
    }
    "be able to parse POSTed hocon REST requests" in {
      Given("Our DataGenRoute under test")
      val underTest = DataGenRoute()
      When("We squirt some hocon in ")
      val testCase = for {
        Some(response) <- underTest(
          post("/data/parse?seed=456",
               """ho : con
            |rocks : true""".stripMargin)).value
        _    = Then("we should get back some sample json")
        _    = response.status.code shouldBe 200
        body = response.bodyAs[Json]
      } yield body

      val result = testCase.value()
      withClue(result.spaces2) {
        result.hcursor.downField("ho").as[String].toTry shouldBe Success("con")
      }
    }
    "be able to parse POSTed jason REST requests" in {
      Given("Our DataGenRoute under test")
      val underTest = DataGenRoute()
      When("We squirt some hocon in ")
      val testCase = for {
        Some(response) <- underTest(post("/data/parse?seed=456", """{ "jay" : "son" }""".stripMargin)).value
        _              = Then("we should get back some sample json")
        _              = response.status.code shouldBe 200
        body           = response.bodyAs[Json]
      } yield body

      val result = testCase.value()
      withClue(result.spaces2) {
        result.hcursor.downField("jay").as[String].toTry shouldBe Success("son")
      }
    }
  }
}
