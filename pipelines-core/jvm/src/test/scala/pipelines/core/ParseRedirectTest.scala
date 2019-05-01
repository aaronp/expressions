package pipelines.core

import pipelines.BaseCoreTest

class ParseRedirectTest extends BaseCoreTest {

  "ParseRedirect" should {
    "parse ?redirectTo=/foo/bar.html&meh=123" in {
      ParseRedirect.unapply("?redirectTo=/foo/bar.html&meh=123") shouldBe Some("/foo/bar.html")
    }
  }
}
