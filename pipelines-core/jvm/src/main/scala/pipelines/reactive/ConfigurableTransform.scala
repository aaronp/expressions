package pipelines.reactive

import io.circe.syntax._
import io.circe.{Decoder, Json}
import monix.execution.atomic.AtomicAny
import monix.reactive.Observable
import pipelines.core.Rate

import scala.util.Try

/**
  * Provides a means to perform an update on an in-place transform (e.g. update a rate, filter, etc)
  *
  * @tparam A
  */
trait ConfigurableTransform[A] {

  /**
    * return the updated transformation
    *
    * @param config
    * @return the updated transformation
    */
  def update(config: A): Transform

  def updateFromJson(config: Json) = {
    val tri: Try[A] = jsonDecoder.decodeJson(config).toTry
    tri.map { c =>
      update(c)
    }
  }

  def defaultTransform(): Try[Transform] = updateFromJson(configJson)

  def jsonDecoder: Decoder[A]
  def configJson: Json
}

object ConfigurableTransform {

  import scala.reflect.runtime.universe.TypeTag

  type JsonTransform = ConfigurableTransform[Json]

  case class FilterExpression(expression: String)
  object FilterExpression {
    implicit val encoder = io.circe.generic.semiauto.deriveEncoder[FilterExpression]
    implicit val decoder = io.circe.generic.semiauto.deriveDecoder[FilterExpression]
  }

  type JsonPredicate = Json => Boolean

  /** Creates a [[ConfigurableTransform]] which will filter out [[Json]] messages based on a [[FilterExpression]]
    * which can be updated while a stream is running
    *
    * @param applyFilter A function which converts a filter expression into a Json predicate
    * @param initial the initial filter criteria
    * @return a [[ConfigurableTransform]] which will filter out [[Json]] messages based on a [[FilterExpression]]
    *         which can be updated while a stream is running
    */
  def jsonFilter(applyFilter: FilterExpression => Option[JsonPredicate], initial: JsonPredicate = _ => true): ConfigurableTransform[FilterExpression] = {
    dynamicFilter[Json](applyFilter, initial)
  }

  def dynamicFilter[A: TypeTag](applyFilter: FilterExpression => Option[A => Boolean], initial: A => Boolean = (_: A) => true): ConfigurableTransform[FilterExpression] = {
    new ConfigurableTransform[FilterExpression] {
      val filterVar = AtomicAny[(FilterExpression, A => Boolean)](FilterExpression("true") -> initial)
      lazy val underlying: Transform = Transform.flatMap[A, A] { input =>
        val (_, predicate) = filterVar.get
        if (predicate(input)) {
          Observable(input)
        } else {
          Observable.empty
        }
      }
      override def update(config: FilterExpression): Transform = {
        applyFilter(config).foreach { predicate =>
          filterVar.set(config -> predicate)
        }
        underlying
      }
      override def jsonDecoder: Decoder[FilterExpression] = FilterExpression.decoder
      override def configJson: Json                       = filterVar.get._1.asJson
    }
  }

  def rateLimitAll[A: TypeTag](initial: Rate): ConfigurableTransform[Rate] = {
    new ConfigurableTransform[Rate] {
      val limitRef = AtomicAny[Rate](initial)
      lazy val underlying: Transform = Transform.apply[A, A] { data: Observable[A] =>
        data.bufferTimed(limitRef.get.per).map(select(_, limitRef.get.messages)).switchMap(Observable.fromIterable)
      }
      override def update(config: Rate): Transform = {
        limitRef.set(config)
        underlying
      }
      override def jsonDecoder      = Rate.RateDecoder
      override def configJson: Json = limitRef.get.asJson
    }
  }

  def rateLimitLatest[A: TypeTag](initial: Rate): ConfigurableTransform[Rate] = {
    new ConfigurableTransform[Rate] {
      val limitRef = AtomicAny[Rate](initial)
      lazy val underlying: Transform = Transform.apply[A, A] { data: Observable[A] =>
        data.bufferTimed(limitRef.get.per).whileBusyDropEvents.map(select(_, limitRef.get.messages)).switchMap(Observable.fromIterable)
      }
      override def update(config: Rate) = {
        limitRef.set(config)
        underlying
      }
      override def jsonDecoder = Rate.RateDecoder

      override def configJson: Json = limitRef.get.asJson
    }
  }
}
