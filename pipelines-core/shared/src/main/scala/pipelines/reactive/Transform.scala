package pipelines.reactive

import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import monix.reactive.Observable

import scala.util.{Failure, Success, Try}

/**
  * Represents an opaque operation which can be performed on some [[DataSource]].
  *
  * The concept of [[ContentType]] is used to help type-check operations
  */
sealed trait Transform {

  /** @param d8a the input [[DataSource]]
    * @return a new [[DataSource]] with this transformation applied if this transformation can be applied to the given [[DataSource]]
    */
  def applyTo(d8a: DataSource): Option[DataSource]

  /** @param d8a the input Data
    * @return true if this transform supports the supplied input Data
    */
  def appliesTo(d8a: DataSource): Boolean = applyTo(d8a).isDefined

  /** @param inputType the input type
    * @return the output type if given the input type
    */
  def outputFor(inputType: ContentType): Option[ContentType]
}

object Transform {

  def defaultTransforms(): Map[String, Transform] = {
    Map[String, Transform]("Json to String"             -> jsonToString, //
                           "String to UTF-8 byte array" -> stringToUtf8, //
                           "parse String as Try[Json]"  -> stringToJson //
    )
  }

//  class Delegate(underlying: Transform) extends Transform {
//    override def applyTo(d8a: DataSource): Option[DataSource] = {
//      underlying.applyTo(d8a)
//    }
//    override def appliesTo(d8a: DataSource): Boolean = {
//      underlying.appliesTo(d8a)
//    }
//    override def outputFor(inputType: ContentType): Option[ContentType] = {
//      underlying.outputFor(inputType)
//    }
//  }

  import scala.reflect.runtime.universe.TypeTag

  case class FixedTransform[A, B](fromType: ContentType, toType: ContentType, apply: (Observable[A] => Observable[B])) extends Transform {
    override def applyTo(obs: DataSource): Option[DataSource] = {
      obs.data(fromType).map { obs =>
        val changed = apply(obs.asInstanceOf[Observable[A]])
        DataSource(toType, changed)
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
  }

  def map[A: TypeTag, B: TypeTag](f: A => B): FixedTransform[A, B] = apply[A, B](_.map(f))

  def flatMap[A: TypeTag, B: TypeTag](f: A => Observable[B]): FixedTransform[A, B] = apply[A, B](_.flatMap(f))

  def stringToJson: FixedTransform[String, Try[Json]]   = map(s => io.circe.parser.parse(s).toTry)
  def jsonToString: FixedTransform[Json, String]        = map[Json, String](_.noSpaces)
  def dump(prefix: String): Transform                   = any(_.dump(prefix))
  def stringToUtf8: FixedTransform[String, Array[Byte]] = map(_.getBytes("UTF-8"))

  def jsonDecoder[A: TypeTag: Decoder]: FixedTransform[Json, Try[A]] = {
    map[Json, Try[A]](_.as[A].toTry)
  }
  def jsonEncoder[A: TypeTag: Encoder]: FixedTransform[A, Json] = map(_.asJson)
  def tryAsError[A: TypeTag]: FixedTransform[Try[A], A]         = map(_.get)
  def tryIgnored[A: TypeTag]: FixedTransform[Try[A], A] = flatMap {
    case Success(x) => Observable(x)
    case Failure(x) => Observable.raiseError(x)
  }

  object tuples {
    private val TupleR = "Tuple(\\d+)".r

    object Tuple1Type {
      def unapply(contentType: ContentType) = {
        contentType match {
          case ClassType(TupleR(_), t1 +: _) => Some(t1)
          case _                             => None
        }
      }
    }
    object Tuple2Type {
      def unapply(contentType: ContentType) = {
        contentType match {
          case ClassType(TupleR(_), _ +: t2 +: _) => Some(t2)
          case _                                  => None
        }
      }
    }
    object Tuple3Type {
      def unapply(contentType: ContentType) = {
        contentType match {
          case ClassType(TupleR(_), _ +: _ +: t3 +: _) => Some(t3)
          case _                                       => None
        }
      }
    }
    object Tuple4Type {
      def unapply(contentType: ContentType) = {
        contentType match {
          case ClassType(TupleR(_), _ +: _ +: _ +: t4 +: _) => Some(t4)
          case _                                            => None
        }
      }
    }
    object TupleTypes {
      def unapply(contentType: ContentType): Option[Seq[ClassType]] = {
        contentType match {
          case ClassType(TupleR(arity), types) => { Some(types.ensuring(_.size == arity.toInt)) }
          case _                               => None
        }
      }
    }
  }
  def _1: Transform = {
    partial {
      case tuples.Tuple1Type(t1) => t1
    }.using { d8a =>
      d8a.contentType match {
        case tuples.Tuple1Type(t1) => d8a.data(t1).map(t1 -> _)
        case _                     => None
      }
    }
  }
  def _2: Transform = {
    partial {
      case tuples.Tuple2Type(t2) => t2
    }.using { d8a =>
      d8a.contentType match {
        case tuples.Tuple2Type(t2) => d8a.data(t2).map(t2 -> _)
        case _                     => None
      }
    }
  }

  def _3: Transform = {
    partial {
      case tuples.Tuple3Type(t3) => t3
    }.using {
      case d8a =>
        d8a.contentType match {
          case tuples.Tuple3Type(t3) => d8a.data(t3).map(t3 -> _)
          case _                     => None
        }
    }
  }
  def _4: Transform = {
    partial {
      case tuples.Tuple4Type(t4) => t4
    }.using {
      case d8a =>
        d8a.contentType match {
          case tuples.Tuple4Type(t4) => d8a.data(t4).map(t4 -> _)
          case _                     => None
        }
    }
  }
}