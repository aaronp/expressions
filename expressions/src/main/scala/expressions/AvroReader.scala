package expressions

import expressions.AvroExpressions.Predicate
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.avro.message.BinaryMessageDecoder
import org.apache.avro.specific.SpecificData

import java.nio.file.Path
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

case class AvroReader[A <: Record](decoderFactory: BinaryMessageDecoder[A]) {

  type Bytes = Array[Byte]

  def read(bytes: Bytes): A = decoderFactory.decode(bytes)

  def matches(bytes: Bytes, expression: String) = {
    val value = read(bytes)
    AvroReader.predicateCache(expression) match {
      case Success(predicate) => predicate(value)
      case Failure(_)         => false
    }
  }
}

object AvroReader {
  lazy val predicateCache: Cache[Predicate] = AvroExpressions.newCache

  def apply[A <: Record: ClassTag]: AvroReader[A] = {
    val rtc             = implicitly[ClassTag[A]].runtimeClass
    val c1ass: Class[A] = rtc.asInstanceOf[Class[A]]
    //val reader: SpecificDatumReader[A] = new SpecificDatumReader[A](c1ass)
    val schema  = c1ass.getConstructor().newInstance().getSchema
    val decoder = new BinaryMessageDecoder[A](new SpecificData, schema)
    new AvroReader[A](decoder)
  }

  def apply(schema: Schema): AvroReader[GenericRecord] = {
//    val reader: GenericDatumReader[GenericRecord] = new GenericDatumReader[GenericRecord](schema)
    val decoder: BinaryMessageDecoder[GenericRecord] = new BinaryMessageDecoder[GenericRecord](new SpecificData, schema)
    new AvroReader(decoder)
  }

  def apply(pathToSchema: Path): AvroReader[GenericRecord] = {
    import eie.io._
    apply(pathToSchema.text)
  }

  def apply(schemaContent: String): AvroReader[GenericRecord] = {
    apply(new Schema.Parser().parse(schemaContent))
  }

}
