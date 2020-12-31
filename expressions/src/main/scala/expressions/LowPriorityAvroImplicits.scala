package expressions

import org.apache.avro.generic.GenericRecord

trait LowPriorityAvroImplicits {
  implicit def asDynamic(msg: Record): DynamicAvroRecord               = new DynamicAvroRecord(msg)
  implicit def genericAsDynamic(msg: GenericRecord): DynamicAvroRecord = new DynamicAvroRecord(msg)
  implicit def anyAsRichType(value: Any)                               = new RichType(value)
}
object LowPriorityAvroImplicits extends LowPriorityAvroImplicits
