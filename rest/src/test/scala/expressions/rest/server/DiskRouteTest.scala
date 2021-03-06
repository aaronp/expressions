package expressions.rest.server

import org.http4s.HttpRoutes
import zio.Task

import scala.util.Random
class DiskRouteTest extends BaseRouteTest {

  "DiskRoute" should {
    "GET /store/list/missing" in {
      val svc                         = Disk.Service().value()
      val underTest: HttpRoutes[Task] = DiskRoute(svc)

      val Some(listed) = underTest(get("store/list/missing")).value.value()
      listed.bodyAsString shouldBe "[]"
    }

    "prob" in {
      var total = 100000
      var count = 0
      def heads = Random.nextDouble() < 0.3
      while (total > 0) {
        total = total - 1
        if (heads || heads) {
          count = count + 1
        }
      }

      val pcnt = count.toDouble / 100000
      println(pcnt)
      println()
    }
    "read a missing path" in {
      val svc                         = Disk.Service().value()
      val underTest: HttpRoutes[Task] = DiskRoute(svc)

      val Some(response) = underTest(get("store/get/metadata/lastSaved")).value.value()
//      response.status.code shouldBe 410
      response.status.code shouldBe 200
      response.bodyAsString shouldBe ""
    }
    "read, write and list arbitrary paths to data" in {
      val svc                         = Disk.Service().value()
      val underTest: HttpRoutes[Task] = DiskRoute(svc)

      val Some(response)  = underTest(post("store/some/path/to/key/1", "hello")).value.value()
      val Some(read1)     = underTest(get("store/get/some/path/to/key/1")).value.value()
      val Some(response2) = underTest(post("store/some/path/to/key/1", "updated")).value.value()
      val Some(_)         = underTest(post("store/some/path/to/key/3", "three")).value.value()
      val Some(read2)     = underTest(get("store/get/some/path/to/key/1")).value.value()
      val Some(notFound)  = underTest(get("store/get/some/path/to/key/2")).value.value()
      val Some(listed)    = underTest(get("store/list/some/path/to/key")).value.value()

      response.status.code shouldBe 201
      response2.status.code shouldBe 200

      read1.status shouldBe read2.status
      read1.status.code shouldBe 200
      read1.bodyAsString shouldBe "hello"
      read2.bodyAsString shouldBe "updated"
//      notFound.status.code shouldBe 410
      notFound.status.code shouldBe 200
      notFound.bodyAsString shouldBe ""

      listed.bodyAsString shouldBe """["some/path/to/key/1","some/path/to/key/3"]"""
    }
  }
}
