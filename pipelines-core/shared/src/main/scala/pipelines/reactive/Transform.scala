package pipelines.reactive

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import monix.reactive.Observable
import pipelines.rest.socket.{AddressedMessage, AddressedTextMessage}

import scala.util.{Failure, Success, Try}

/**
  * Represents an opaque operation which can be performed on some [[DataSource]].
  *
  * The concept of [[ContentType]] is used to help type-check operations
  */
trait Transform {

  /** @param d8a the input [[DataSource]]
    * @return a new [[DataSource]] with this transformation applied if this transformation can be applied to the given [[DataSource]]
    */
  def applyTo(d8a: DataSource): Option[DataSource]

  /** @param d8a the input Data
    * @return true if this transform supports the supplied input Data
    */
  def appliesTo(d8a: DataSource): Boolean = applyTo(d8a).isDefined

  /** return the result type based on the input type.
    *
    * This is our poor-man's way of chaining parameterized types.
    *
    * e.g. a transform which puts inputs into a list would return a some of 'List[T]' for whatever 'T' input type.
    *
    * A transform which takes the second value from a tuple would return 'B' when given an input type of (A,B,C).
    *
    * another transform might just produce a count, so would return a some of 'Int' for any 'T'.
    *
    * And still others might not work at all, e.g. a transform which doubles Long values would return None when given an inputType of 'String'
    *
    * @param inputType the input type
    * @return the output type if this transform can operate on the input input
    */
  def outputFor(inputType: ContentType): Option[ContentType]
}

object Transform {
  def combineLatest(only: DataSource): FunctionTransform = {
    partial {
      case other => ContentType.tuple2(other, only.contentType)
    }.map { original =>
      val newType: ContentType = ContentType.tuple2(original.contentType, only.contentType)
      val newMetadata          = original.prefixedMetadata("combineLatest")
      val obs                  = original.asObservable[Any].combineLatest(only.asObservable)
      Option(DataSource(newType, obs, newMetadata))
    }
  }

  object keys {
    val Dump                        = "dump"
    val PushEventAsAddressedMessage = "PushEvent.asAddressedMessage"
  }

  def defaultTransforms(): Map[String, Transform] = {

    Map[String, Transform](
      "Transform.jsonToString"         -> jsonToString, //
      "Transform.stringToUtf8"         -> stringToUtf8, //
      "Transform.stringToJson"         -> stringToJson,
      "tries.successes"                -> tries.successes,
      "tries.failures"                 -> tries.failures,
      "tries.get"                      -> tries.get,
      "_1"                             -> tuples._1,
      "_2"                             -> tuples._2,
      "_3"                             -> tuples._3,
      "_4"                             -> tuples._4,
      "sockets.addressedTextAsJson"    -> sockets.addressedTextAsJson,
      "sockets.addressedAsJson"        -> sockets.addressedAsJson,
      keys.PushEventAsAddressedMessage -> Transform.map[PushEvent, AddressedMessage](PushEvent.asAddressedMessage),
      keys.Dump                        -> dump("debug")
    )
  }
  import scala.reflect.runtime.universe.TypeTag

  object sockets {
    val addressedTextAsJson: FixedTransform[AddressedTextMessage, Json] = Transform.map[AddressedTextMessage, Json](_.asJson)
    val addressedAsJson: FixedTransform[AddressedMessage, Json]         = Transform.map[AddressedMessage, Json](_.asJson)
  }

  object identity extends Transform {
    override def applyTo(d8a: DataSource): Option[DataSource]           = Option(d8a)
    override def outputFor(inputType: ContentType): Option[ContentType] = Option(inputType)
  }

  case class FixedTransform[A, B](fromType: ContentType, toType: ContentType, apply: ((DataSource, Observable[A]) => DataSource)) extends Transform {
    override def toString: String = s"$fromType -> $toType"
    override def applyTo(dataSource: DataSource): Option[DataSource] = {
      dataSource.data(fromType).map { obs =>
        apply(dataSource, obs.asInstanceOf[Observable[A]])
      }
    }

    override def outputFor(d8a: ContentType): Option[ContentType] = {
      if (d8a == fromType) {
        Option(toType)
      } else {
        None
      }
    }
  }

  case class FunctionTransform(calcOutput: ContentType => Option[ContentType], apply: DataSource => Option[DataSource]) extends Transform {
    override def toString: String                                   = "partial transform"
    override def outputFor(input: ContentType): Option[ContentType] = calcOutput(input)
    override def applyTo(d8a: DataSource): Option[DataSource] = {
      apply(d8a)
    }
  }

  def apply[A: TypeTag, B: TypeTag](modify: (Observable[A] => Observable[B])): FixedTransform[A, B] = {
    val fromType: ContentType = ContentType.of[A]
    val toType: ContentType   = ContentType.of[B]
    fixed[A, B](fromType, toType)(modify)
  }

  def fixed[A, B](fromType: ContentType, toType: ContentType)(apply: (Observable[A] => Observable[B])): FixedTransform[A, B] = {
    forTypes[A, B](fromType, toType)((original, obs: Observable[A]) => DataSource.of(toType, apply(obs), original.prefixedMetadata(s"fixed[$fromType -> $toType]")))
  }

  def forTypes[A, B](fromType: ContentType, toType: ContentType)(apply: (DataSource, Observable[A]) => DataSource): FixedTransform[A, B] = {
    new FixedTransform[A, B](fromType, toType, apply)
  }

  def fixedFor[A, B](fromType: ContentType, toType: ContentType)(apply: ((DataSource, Observable[A]) => DataSource)): FixedTransform[A, B] = {
    new FixedTransform[A, B](fromType, toType, apply)
  }

  /**
    *
    * @param f
    * @return a transformation which works on any (and all) types, preserving the input/output. e.g. operations like rate limit, filter, etc.
    */
  def any(f: Observable[_] => Observable[_]): FunctionTransform = {
    partial {
      case contentType => contentType
    }.using { d8a =>
      val ct = d8a.contentType
      d8a.data(ct).map { obs =>
        ct -> f(obs)
      }
    }
  }

  def partial(outputFor: PartialFunction[ContentType, ContentType]) = {
    new PartialBuilder(outputFor)
  }
  case class PartialBuilder(outputFor: PartialFunction[ContentType, ContentType]) {
    def using(apply: DataSource => Option[(ContentType, Observable[_])]): FunctionTransform = {
      def asObs(input: DataSource): Option[DataSource] = {
        apply(input).map {
          case (newType, obs) => DataSource.of(newType, obs)
        }
      }
      new FunctionTransform(outputFor.lift, asObs)
    }
    def map(apply: DataSource => Option[DataSource]): FunctionTransform = {
      def asObs(input: DataSource): Option[DataSource] = apply(input)
      new FunctionTransform(outputFor.lift, asObs)
    }
  }

  def map[A: TypeTag, B: TypeTag](f: A => B): FixedTransform[A, B] = apply[A, B](_.map(f))

  def zipWithIndex[A: TypeTag]: FixedTransform[A, (A, Long)] = apply[A, (A, Long)](_.zipWithIndex)

  def flatMap[A: TypeTag, B: TypeTag](f: A => Observable[B]): FixedTransform[A, B] = apply[A, B](_.flatMap(f))
  def filter[A: TypeTag](predicate: A => Boolean): FixedTransform[A, A]            = apply[A, A](_.filter(predicate))

  def stringToJson: FixedTransform[String, Try[Json]]   = map(s => io.circe.parser.parse(s).toTry)
  def jsonToString: FixedTransform[Json, String]        = map[Json, String](_.noSpaces)
  def dump(prefix: String): Transform                   = any(_.dump(prefix))
  def stringToUtf8: FixedTransform[String, Array[Byte]] = map(_.getBytes("UTF-8"))

  def jsonDecoder[A: TypeTag: Decoder]: FixedTransform[Json, Try[A]] = {
    map[Json, Try[A]](_.as[A].toTry)
  }
  def jsonEncoder[A: TypeTag: Encoder]: FixedTransform[A, Json] = map(_.asJson)

  object tries {
    object TryType {
      def unapply(contentType: ContentType) = {
        contentType match {
          case ClassType("Try", t1 +: _)     => Some(t1)
          case ClassType("Success", t1 +: _) => Some(t1)
          case ClassType("Failure", t1 +: _) => Some(t1)
          case _                             => None
        }
      }
    }
    def get: Transform = {
      partial {
        case tries.TryType(t1) => t1
      }.using { d8a =>
        d8a.contentType match {
          case tries.TryType(t1) =>
            d8a.data(d8a.contentType).map { obs =>
              t1 -> obs.map {
                case Success(ok)  => ok
                case Failure(err) => throw err
              }
            }
          case _ => None
        }
      }
    }
    def successes: Transform = {
      partial {
        case tries.TryType(t1) => t1
      }.using { d8a =>
        d8a.contentType match {
          case tries.TryType(t1) =>
            d8a.data(d8a.contentType).map { obs =>
              t1 -> obs.collect {
                case Success(ok) => ok
              }
            }
          case _ => None
        }
      }
    }
    def failures: Transform = {
      val errType = ContentType.of[Throwable]
      partial {
        case tries.TryType(_) => errType
      }.using { d8a =>
        d8a.contentType match {
          case tries.TryType(_) =>
            d8a.data(d8a.contentType).map { obs =>
              errType -> obs.collect {
                case Failure(err) => err
              }
            }
          case _ => None
        }
      }
    }
  }

  object tuples {
    private val TupleR = "Tuple(\\d+)".r

    private object Tuple1Type {
      def unapply(contentType: ContentType) = {
        contentType match {
          case ClassType(TupleR(_), t1 +: _) => Some(t1)
          case _                             => None
        }
      }
    }
    private object Tuple2Type {
      def unapply(contentType: ContentType) = {
        contentType match {
          case ClassType(TupleR(_), _ +: t2 +: _) => Some(t2)
          case _                                  => None
        }
      }
    }
    private object Tuple3Type {
      def unapply(contentType: ContentType) = {
        contentType match {
          case ClassType(TupleR(_), _ +: _ +: t3 +: _) => Some(t3)
          case _                                       => None
        }
      }
    }
    private object Tuple4Type {
      def unapply(contentType: ContentType) = {
        contentType match {
          case ClassType(TupleR(_), _ +: _ +: _ +: t4 +: _) => Some(t4)
          case _                                            => None
        }
      }
    }
    object TupleTypes {
      def unapply(contentType: ContentType): Option[Seq[ContentType]] = {
        contentType match {
          case ClassType(TupleR(arity), types) => { Some(types.ensuring(_.size == arity.toInt)) }
          case _                               => None
        }
      }
    }

    def _1: Transform = {
      partial {
        case tuples.Tuple1Type(t1) => t1
      }.using { d8a: DataSource =>
        d8a.contentType match {
          case ClassType(TupleR(n), t1 +: _) =>
            val newObs = d8a.asObservable.map { owt: Any =>
              owt match {
                case (value: Any, _)          => value
                case (value: Any, _, _)       => value
                case (value: Any, _, _, _)    => value
                case (value: Any, _, _, _, _) => value
              }
            }
            Option(t1 -> newObs)
          case _ => None
        }
      }
    }
    def _2: Transform = {
      partial {
        case tuples.Tuple2Type(t2) => t2
      }.using { d8a =>
        d8a.contentType match {
          case ClassType(TupleR(_), _ +: t2 +: _) =>
            val newObs = d8a.asObservable.map { owt: Any =>
              owt match {
                case (_, value)          => value
                case (_, value, _)       => value
                case (_, value, _, _)    => value
                case (_, value, _, _, _) => value
              }
            }
            Option(t2 -> newObs)
          case _ => None
        }
      }
    }

    def _3: Transform = {
      partial {
        case tuples.Tuple3Type(t3) => t3
      }.using { d8a =>
        d8a.contentType match {
          case ClassType(TupleR(_), _ +: _ +: t3 +: _) =>
            val newObs = d8a.asObservable.map { owt: Any =>
              owt match {
                case (_, _, value)       => value
                case (_, _, value, _)    => value
                case (_, _, value, _, _) => value
              }
            }
            Option(t3 -> newObs)
          case _ => None
        }
      }
    }
    def _4: Transform = {
      partial {
        case tuples.Tuple4Type(t4) => t4
      }.using { d8a =>
        d8a.contentType match {
          case ClassType(TupleR(_), _ +: _ +: _ +: t4 +: _) =>
            val newObs = d8a.asObservable.map { owt: Any =>
              owt match {
                case (_, _, _, value)    => value
                case (_, _, _, value, _) => value
              }
            }
            Option(t4 -> newObs)
          case _ => None
        }
      }
    }
  }
}
