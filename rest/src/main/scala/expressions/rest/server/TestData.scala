package expressions.rest.server

import example.{Address, Example, daysOfTheWeek}
import io.circe.Json
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericDatumReader, IndexedRecord}
import org.apache.avro.io.DecoderFactory

import java.io.{ByteArrayInputStream, DataInputStream}
import scala.jdk.CollectionConverters._

object TestData {

  def fromJson[A](record: Json, schema: Schema = Example.getClassSchema): A = {
    val decoder = DecoderFactory.get().jsonDecoder(schema, new DataInputStream(new ByteArrayInputStream(record.noSpaces.getBytes())))
    val reader  = new GenericDatumReader[Any](schema)
    reader.read(null, decoder).asInstanceOf[A]
  }

  def asJson(record: IndexedRecord): Json = {
//    record.getSpecificData
    val jason = record.toString
    io.circe.parser.parse(jason).toTry.get
  }

  def testRecord(addresses: List[Address] = List(testAddress("home"), testAddress("work"))): Example = {
    Example
      .newBuilder()
      .setAddresses(addresses.asJava)
      .setDay(daysOfTheWeek.MONDAY)
      .setId("identifier")
      .setSomeDouble(12.34)
      .setSomeFloat(23.45.toFloat)
      .setSomeLong(3456)
      .setSomeText("this is some text")
      .setSomeInt(42)
      .build()
  }

  def testAddress(addrName: String = "some address", lines: List[CharSequence] = List("123 main st", "smalltown")): Address = {
    Address
      .newBuilder()
      .setName(addrName)
      .setLines(lines.asJava)
      .build()
  }
}
