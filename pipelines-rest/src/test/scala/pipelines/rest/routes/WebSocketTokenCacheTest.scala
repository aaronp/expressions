package pipelines.rest.routes

import org.scalatest.concurrent.Eventually
import org.scalatest.{Matchers, WordSpec}
import pipelines.Schedulers

import scala.concurrent.duration._

class WebSocketTokenCacheTest extends WordSpec with Matchers with Eventually {

  "WebSocketTokenCache" should {
    "remove tokens after a given time" in {
      Schedulers.using { implicit s =>
        val cache       = WebSocketTokenCache(10.millis)
        val key: String = cache.generate("some jwt token")
        eventually {
          cache.isValid(key) shouldBe false
        }
      }
    }
  }
  "WebSocketTokenCache.validateAndRemove" should {
    "remove tokens after a given time" in {
      Schedulers.using { implicit s =>
        val cache        = WebSocketTokenCache(20.seconds)
        val key: String  = cache.generate("another jwt token")
        val key2: String = cache.generate("one more jwt token")
        key should not be key2
        cache.isValid(key) shouldBe true
        cache.isValid(key2) shouldBe true
        cache.validateAndRemove(key) shouldBe Some("another jwt token")
        cache.validateAndRemove(key) shouldBe None
      }
    }
  }
}
