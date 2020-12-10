package example

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExampleTest extends AnyWordSpec with Matchers {

  "Example" should {
    "reduce to json" in {
      val d8a: Example =
        Example.newBuilder.setDay(daysOfTheWeek.FRIDAY).setId("123").setSomeDouble(234).setSomeFloat(345).setSomeInt(456).setSomeLong(567).setSomeText("678").build()

      d8a.toString should include("456")
    }
  }
}
