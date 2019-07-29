package pipelines.layout

import org.scalatest.{Matchers, WordSpec}

class AsciiTableTest extends WordSpec with Matchers {

  "AsciiTable.render" should {
    "render ascii tables" in {
      val table = AsciiTable("foo" -> "bar", "fizz" -> "buzz").render(indent = "")
      table shouldBe """+------------+
                       >| fizz | foo |
                       >+------------+
                       >| buzz | bar |
                       >+------------+""".stripMargin('>')

      val table2 = AsciiTable("foo" -> "bar", "fizz" -> "buzz", "meh" -> "3", "long one" -> "Xxxxxxxxxxxxxx").render(indent = "")
      table2 shouldBe """+-----------------------------------+
                        >| fizz | foo | long one       | meh |
                        >+-----------------------------------+
                        >| buzz | bar | Xxxxxxxxxxxxxx | 3   |
                        >+-----------------------------------+""".stripMargin('>')

      val table3 = AsciiTable("foo" -> "bar", "fizz" -> "buzz", "meh" -> "3", "long one" -> "Xxxxxxxxxxxxxx").addRow("foo" -> "second", "another" -> "column")
      table3.render(indent = "") shouldBe """+------------------------------------------------+
                                   >| another | fizz | foo    | long one       | meh |
                                   >+------------------------------------------------+
                                   >| column  |      | second |                |     |
                                   >+------------------------------------------------+
                                   >|         | buzz | bar    | Xxxxxxxxxxxxxx | 3   |
                                   >+------------------------------------------------+""".stripMargin('>')
    }
  }
}
