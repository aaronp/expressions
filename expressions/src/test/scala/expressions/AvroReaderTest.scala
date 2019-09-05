package expressions

import eie.io._
import example.{Beispiel, Example, daysOfTheWeek, tagDesWoche}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path
import scala.language.dynamics
import scala.util.Properties

class AvroReaderTest extends AnyWordSpec with Matchers {

  "AvroReader" should {

    val beispielData = for {
      id   <- Seq("a", "b", "c")
      text <- Seq(Option("text1"), None)
      day  <- tagDesWoche.values().take(3)
    } yield {
      val default = Beispiel.newBuilder().setKennzeichnen(id).setTag(day).setWort(id * 2)
      text.fold(default)(default.setWort).build()
    }

    def deBytes: Array[Byte] = {
      val bytes = beispielData.head.toByteBuffer.array()
      bytes.length should be > 0
      bytes
    }

    "read specific types" ignore {
      val deReader = AvroReader[Beispiel]
      beispielData.foreach { expected =>
        val actual = deReader.read(expected.toByteBuffer.array)
        actual shouldBe expected
      }
    }

    "read generic types" ignore {

      import implicits._
      def findExampleSchema(name: String) = {
        val schemaFile: Path = {
          val root = Properties.userDir.asPath
          (root +: root.parents).map(_.resolve(s"avro-records/src/main/avro/$name")).find(_.isFile).get
        }
        schemaFile.text
      }

      val deReader = AvroReader(findExampleSchema("beispiel.avsc"))
//      val enReader = AvroReader.generic(findExampleSchema("example.avsc"))

      def check(readBack: DynamicAvroRecord, record: Beispiel) = {
        readBack.kennzeichnen.asString shouldBe record.getKennzeichnen

        val tag: DynamicAvroRecord.Value = readBack.getTag
        val actualEnum                   = tag.as[tagDesWoche]
        val expectedEnum                 = record.getTag
        actualEnum shouldBe expectedEnum

        readBack.tag.as[tagDesWoche] shouldBe record.getTag
        readBack.wort.asString shouldBe record.getWort
      }

      check(deReader.read(deBytes), beispielData.head)

      beispielData.foreach { expected =>
        val actual = deReader.read(expected.toByteBuffer.array)
        check(actual, expected)
      }
    }
  }
}
