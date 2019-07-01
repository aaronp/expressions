package pipelines.users

import org.scalatest.{Matchers, WordSpec}

class InvalidEmailAddressTest extends WordSpec with Matchers {
  "InvalidEmailAddress" should {

    List(
      "invalid"            -> false,
      ""                   -> false,
      "http://website.com" -> false,
      "e@m@il@com"         -> false,
      "e@mail"             -> false,
      "r@a.com"            -> true,
      "e@mail.com"         -> true,
      "a@x.co.uk"          -> true
    ).foreach {
      case (input, true) =>
        s"validate '${input}' as valid" in {
          InvalidEmailAddress.isValid(input) shouldBe true
        }
      case (input, false) =>
        s"validate '${input}' as invalid" in {
          InvalidEmailAddress.isValid(input) shouldBe false
        }
    }
  }

}
