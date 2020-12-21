package expressions.client.kafka

import io.circe.literal.JsonStringContext
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PostRecordTest extends AnyWordSpec with Matchers {
  "PostRecord.replaceAll" should {
    "work" in {
      val jason =
        json"""{
                 "root" : {
                    "array" : [
                      {
                        "replaceme-key" : "replaceme-value"
                      },
                      "replaceme-string"
                    ]
                 },
                 "replaceme-top" : "replaceme-top"
               }"""
      val replaced = PostRecord.replaceAll(jason, "replaceme", "ok")
      println(replaced.spaces4)
      println(replaced.spaces4)
    }
  }
}
