package expressions.rest.server

import org.http4s.HttpRoutes
import zio.Task

class CacheRouteTest extends BaseRouteTest {

  "CacheRoute" should {
    "work" in {
      val underTest: HttpRoutes[Task] = CacheRoute().value()

      val Some(response)  = underTest(post("cache/some/path/to/key/1", "hello")).value.value()
      val Some(read1)     = underTest(get("cache/some/path/to/key/1")).value.value()
      val Some(response2) = underTest(post("cache/some/path/to/key/1", "updated")).value.value()
      val Some(read2)     = underTest(get("cache/some/path/to/key/1")).value.value()
      val Some(notFound)  = underTest(get("cache/some/path/to/key/2")).value.value()

      response.status.code shouldBe 201
      response2.status.code shouldBe 200

      read1.status shouldBe read2.status
      read1.status.code shouldBe 200
      read1.bodyAsString shouldBe "\"hello\""
      read2.bodyAsString shouldBe "\"updated\""
      notFound.status.code shouldBe 410
      notFound.bodyAsString shouldBe ""
    }
  }
}
