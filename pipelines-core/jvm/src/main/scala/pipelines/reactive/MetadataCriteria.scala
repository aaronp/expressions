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

  def apply(criteriaByKey: (String, String)*): MetadataCriteria = {
    apply(criteriaByKey.toMap)
  }

  def apply(criteriaByKey: Map[String, String]): MetadataCriteria = {
    val matcherByName = criteriaByKey.mapValues {
      case KindValueR("re", value)   => regex(value.r)
      case KindValueR("any", values) => any(asSet(values))
      case KindValueR("all", values) => all(asSet(values))
      case KindValueR("ci", value)   => caseInsensitive(value.toLowerCase)
      case KindValueR("eq", value)   => eq(value)
      case default                   => eq(default)
    }

    // the .iterator.toMap is just so that we don't have a 'MappedValues' view over our map -- we want to evaluate and
    // store the computed values, not re-parse!
    new MetadataCriteria(matcherByName.iterator.toMap)
  }

  private def asSet(value: String): Set[String] = {
    value.split(",", -1).map(_.trim).toSet
  }

  def eq(expected: String): Match              = (_: String) == expected
  def caseInsensitive(expected: String): Match = (_: String).toLowerCase == expected
  def regex(expected: Regex): Match = (value: String) => {
    expected.findFirstIn(value).isDefined
  }
  def any(values: Set[String]): Match = (value: String) => {
    asSet(value).exists(values.contains)
  }
  def all(values: Set[String]): Match = (value: String) => {
    val actual = asSet(value)
    values.forall(actual.contains)
  }
}
