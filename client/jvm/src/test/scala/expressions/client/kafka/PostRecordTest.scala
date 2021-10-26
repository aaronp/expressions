package expressions.client.kafka

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PostRecordTest extends AnyWordSpec with Matchers {
  "PostRecord.replaceAll" should {
    "replace all occurrences of text in a json record" in {
      val jason =
        io.circe.parser.parse("""{
                 "root" : {
                    "array" : [
                      {
                        "replaceme-key" : "replaceme-value"
                      },
                      "replaceme-string"
                    ]
                 },
                 "replaceme-top" : "replaceme-top"
               }""").toTry.get
      val replaced = PostRecord.replaceAll(jason, "replaceme", "ok")
      withClue(replaced.spaces4) {
        replaced.noSpaces shouldBe """{"root":{"array":[{"ok-key":"ok-value"},"ok-string"]},"ok-top":"ok-top"}"""
      }
    }
  }
}
