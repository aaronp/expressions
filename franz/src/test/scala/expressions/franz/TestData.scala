package expressions.franz

import io.circe.Json
import org.apache.avro.generic.{GenericRecord, IndexedRecord}

import scala.jdk.CollectionConverters._

object TestData {

  def fromJson(record: Json): GenericRecord = SchemaGen.recordForJson(record)

  def asJson(record: IndexedRecord): Json = {
    val jason = record.toString
    io.circe.parser.parse(jason).toTry.get
  }
//
//  def testRecord(addresses: List[Address] = List(testAddress("home"), testAddress("work"))): Example = {
//    Example
//      .newBuilder()
//      .setAddresses(addresses.asJava)
//      .setDay(daysOfTheWeek.MONDAY)
//      .setId("identifier")
//      .setSomeDouble(12.34)
//      .setSomeFloat(23.45.toFloat)
//      .setSomeLong(3456)
//      .setSomeText("this is some text")
//      .setSomeInt(42)
//      .build()
//  }
//
//  def testAddress(addrName: String = "some address", lines: List[CharSequence] = List("123 main st", "smalltown")): Address = {
//    Address
//      .newBuilder()
//      .setName(addrName)
//      .setLines(lines.asJava)
//      .build()
//  }
}
