package pipelines.reactive

import scala.util.matching.Regex

class MetadataCriteria(criteria: Map[String, MetadataCriteria.Match]) {
  final def matches(first: (String, String), theRest: (String, String)*): Boolean = {
    matches(theRest.toMap + first)
  }

  def matches(metadata: Map[String, String]): Boolean = {
    criteria.forall {
      case (key, matcher) => metadata.get(key).exists(matcher)
    }
  }
}

object MetadataCriteria {

  type Match = String => Boolean

  private val KindValueR = "(.*):(.*)".r
  private val PrefixR    = "([^.]*)\\.(.*)".r

  def forId(id: String): MetadataCriteria = {
    apply("id" -> id)
  }

  /**
    * There are use-cases where we want to both supply some criteria and specify some new metadata from a single map.
    *
    * For example, as taken from REST query parameters:
    *
    * {{{
    *   ?src.id=123&sink.id=456&type=topic
    * }}}
    *
    * could be used to create one set of MetadataCriteria for a data source, one for a DataSink and create some raw metadata itself for the 'type=topic' key/value pair.
    *
    *
    * This function will strip out any metadata matching keys '<prefix>.XXX=YYY' as 'XXX=YYY' pairs, so that you might e.g.:
    *
    * 'sink.topic=re:topic.*'
    *
    * @param prefix
    * @param metadata
    * @return some criteria which only contains keys with the given prefix, but with that prefix stripped away
    */
  def forPrefix(prefix: String, metadata: Map[String, String]): Map[String, String] = {
    val pears = metadata.collect {
      case (PrefixR(`prefix`, stripped), value) => (stripped, value)
    }
    pears
  }

  def withoutPrefix(metadata: Map[String, String]): Map[String, String] = {
    metadata.filterKeys {
      case PrefixR(_, _) => false
      case _             => true
    }
  }

  def apply(criteriaByKey: (String, String)*): MetadataCriteria = {
    apply(criteriaByKey.toMap)
  }

  def apply(criteriaByKey: Map[String, String]): MetadataCriteria = {
    val matcherByName = criteriaByKey.mapValues {
      case KindValueR("re", value)    => regex(value.r)
      case KindValueR("any", values)  => any(asSet(values))
      case KindValueR("all", values)  => all(asSet(values))
      case KindValueR("none", values) => none(asSet(values))
      case KindValueR("ci", value)    => caseInsensitive(value.toLowerCase)
      case KindValueR("eq", value)    => eq(value)
      case KindValueR("never", _)     => never
      case default                    => eq(default)
    }

    // the .iterator.toMap is just so that we don't have a 'MappedValues' view over our map -- we want to evaluate and
    // store the computed values, not re-parse!
    new MetadataCriteria(matcherByName.iterator.toMap)
  }

  private def asSet(value: String): Set[String] = {
    value.split(",", -1).map(_.trim).toSet
  }

  val never: Match                             = (_: String) => false
  def eq(expected: String): Match              = (_: String) == expected
  def caseInsensitive(expected: String): Match = (_: String).toLowerCase == expected
  def regex(expected: Regex): Match = (value: String) => {
    expected.findFirstIn(value).isDefined
  }
  def any(values: Set[String]): Match = (value: String) => {
    asSet(value).exists(values.contains)
  }
  def none(values: Set[String]): Match = (value: String) => {
    asSet(value).forall(x => !values.contains(x))
  }
  def all(values: Set[String]): Match = (value: String) => {
    val actual = asSet(value)
    values.forall(actual.contains)
  }
}
