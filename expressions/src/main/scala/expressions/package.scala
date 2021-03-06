import org.apache.avro.generic.IndexedRecord

/**
  * The main entry point:
  *
  * {{{
  *   val rule = expressions.parseRule("value.id == 12 || value.amount <= 12.345")
  *
  *   ...
  *   dataStream.filter(rule)
  * }}}
  */
package object expressions extends LowPriorityAvroImplicits {

  type Record = IndexedRecord

  def asAvroPredicate(rule: String): AvroExpressions.Predicate = AvroExpressions.Predicate(rule)

}
