package pipelines.eval

import io.circe.Json
import pipelines.expressions.{AvroExpressions, Cache, JsonExpressions}
import pipelines.reactive.ConfigurableTransform

object Filters {

  def configurableJsonFilter(cache: Cache[Json => Boolean] = JsonExpressions.newCache): ConfigurableTransform[ConfigurableTransform.FilterExpression] = {
    ConfigurableTransform.jsonFilter { expr =>
      cache(expr.expression).toOption
    }
  }

  def configurableAvroFilter(cache: Cache[AvroExpressions.Predicate] = AvroExpressions.newCache): ConfigurableTransform[ConfigurableTransform.FilterExpression] = {
    ConfigurableTransform.dynamicFilter { expr =>
      cache(expr.expression).toOption
    }
  }
}
