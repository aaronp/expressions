package pipelines.reactive

import monix.execution.Scheduler
import pipelines.reactive.Transform.{FixedTransform, FunctionTransform}
import pipelines.reactive.repo._

import scala.util.{Failure, Success, Try}

/**
  * Represents a repository of sources, sinks, and data transformations which can be used to construct
  * data pipelines by reference
  */
case class SourceRepository(sourcesByName: Map[String, NewSource],                               //
                            sinksByName: Map[String, DataSink],                                  //
                            configurableTransformsByName: Map[String, ConfigurableTransform[_]], //
                            transformsByName: Map[String, Transform])(implicit scheduler: Scheduler) {
  def addSource(name: String, dataSource: DataSource): Option[SourceRepository] = {
    addSource(name, NewSource(dataSource))
  }

  def addSource(name: String, dataSource: NewSource): Option[SourceRepository] = {
    if (sourcesByName.contains(name)) {
      Option(copy(sourcesByName = sourcesByName.updated(name, dataSource)))
    } else {
      None
    }
  }

  def removeSource(name: String): Option[SourceRepository] = {
    if (sourcesByName.contains(name)) {
      val removed: Map[String, NewSource] = sourcesByName - name
      Option(copy(sourcesByName = removed))
    } else {
      None
    }
  }

  private lazy val sourceTypes = listSources(ListRepoSourcesRequest(None)).sources.flatMap(_.contentType).distinct

  def transformTypesForInput(inputType: ContentType): Iterable[ContentType] = {
    transformsByName.values.flatMap {
      case FixedTransform(_, toType, _)  => Option(toType)
      case FunctionTransform(calcOut, _) => calcOut(inputType)
    }
  }

  lazy val allTypes: Seq[ContentType] = {
    val transformTypes = sourceTypes.flatMap(transformTypesForInput)
    (sourceTypes ++ transformTypes).distinct
  }

  def withTransform(name: String, transform: Transform): SourceRepository = {
    copy(transformsByName = transformsByName.updated(name, transform))
  }
  def withConfigurableTransform(name: String, transform: ConfigurableTransform[_]): SourceRepository = {
    copy(configurableTransformsByName = configurableTransformsByName.updated(name, transform))
  }
  def withSink(name: String, sink: DataSink): SourceRepository = {
    copy(sinksByName = sinksByName.updated(name, sink))
  }

  private[reactive] def resolveTransforms(transforms: Seq[DataTransform]): Seq[(String, Try[Transform])] = {
    transforms.map {
      case DataTransform(id, configOpt) =>
        val transformResult: Try[Transform] = {
          val vanillaTranformOpt = transformsByName.get(id).map {
            case transform => Success(transform)
          }

          val resolvedOpt: Option[Try[Transform]] = vanillaTranformOpt.orElse {
            configurableTransformsByName.get(id).map { configurable: ConfigurableTransform[_] =>
              configOpt match {
                case None       => configurable.defaultTransform
                case Some(conf) => configurable.updateFromJson(conf)
              }
            }
          }

          resolvedOpt.getOrElse(Failure(new IllegalArgumentException(s"Couldn't find any transform for '${id}'")))
        }
        id -> transformResult
    }
  }

  /** A convenience method to create a new chain
    *
    * @param dataSource
    * @param transforms
    * @return either an error or a new chain
    */
  def createChain(dataSource: String, firstTransform: DataTransform, transforms: DataTransform*): Either[String, DataChain] = {
    createChain(CreateChainRequest(dataSource, firstTransform +: transforms.toSeq))
  }

  /** A convenience method to create a new chain
    *
    * @param dataSource
    * @param transforms
    * @return either an error or a new chain
    */
  def createChain(dataSource: String, transforms: String*): Either[String, DataChain] = {
    transforms.map(name => DataTransform(name)) match {
      case Seq()           => createChain(CreateChainRequest(dataSource, Nil))
      case head +: theRest => createChain(dataSource, head, theRest: _*)
    }
  }

  /**
    * Create a new [[DataChain]] using the sources/transforms in this repo
    *
    * @param request the create request
    * @return either an error message or a new DataChain
    */
  def createChain(request: CreateChainRequest): Either[String, DataChain] = {
    val sourceTry: Try[DataSource] = sourcesByName.get(request.dataSource) match {
      case Some(data) => Success(data(scheduler))
      case None       => Failure(new IllegalArgumentException(s"Couldn't find source for '${request.dataSource}'"))
    }

    val transById: Seq[(String, Try[Transform])] = resolveTransforms(request.transforms)

    sourceTry match {
      case Success(source) =>
        val errors: Seq[(String, Throwable)] = transById.collect {
          case (key, Failure(err)) => (key, err)
        }

        errors match {
          case Seq() =>
            val transforms = (transById: @unchecked).map {
              case (id, Success(t)) => (id, t)
            }
            DataChain(source, transforms)
          case (key, err) +: theRest =>
            val messages = (err.getMessage +: theRest.map(_._2.getMessage)).distinct
            val keys     = (key +: theRest.map(_._1)).distinct
            Left(s"Found ${messages.size} error(s) creating a pipeline for transformation(s) ${keys.mkString("'", "', '", "'")} : ${messages.mkString("; ")}")
        }
      case Failure(err) => Left(err.getMessage)
    }
  }

  private lazy val sinks = sinksByName.keySet.map(ListedSink.apply).toList.sortBy(_.name)

  def listSinks(request: ListSinkRequest) = {
    ListSinkResponse(sinks)
  }

  private def basicTransforms: List[ListedTransformation] = {
    transformsByName
      .map {
        case (key, FixedTransform(fromType, toType, _)) =>
          ListedTransformation(key, Option(fromType), Option(toType), None)
        case (key, _) =>
          ListedTransformation(key, None, None, None)
      }
      .toList
      .sortBy(_.name)
  }

  private def configurableTransforms: List[ListedTransformation] =
    configurableTransformsByName
      .map {
        case (key, confTransform) =>
          confTransform.defaultTransform match {
            case Success(FixedTransform(fromType, toType, _)) =>
              ListedTransformation(key, Option(fromType), Option(toType), Option(confTransform.configJson))
            case _ =>
              ListedTransformation(key, None, None, Option(confTransform.configJson))
          }
      }
      .toList
      .sortBy(_.name)

  private lazy val allTransforms = basicTransforms ++ configurableTransforms
  def listTransforms(request: ListTransformationRequest): ListTransformationResponse = {
    val found = request.inputContentType.fold(allTransforms) { accepts =>
      allTransforms.filter { t =>
        !t.inputType.exists(_ != accepts) // filter out those who have a different input type
      }
    }
    ListTransformationResponse(found)
  }

  private lazy val allSources = sourcesByName.map {
    case (key, d8a) => ListedDataSource(key, Option(d8a(scheduler).contentType))
  }

  final def listSources(ofType: Option[ContentType] = None): ListRepoSourcesResponse = {
    listSources(ListRepoSourcesRequest(ofType))
  }

  def listSources(request: ListRepoSourcesRequest): ListRepoSourcesResponse = {
    val sources = request.ofType.fold(allSources) { accepts =>
      sourcesByName.collect {
        case (key, d8a) if d8a(scheduler).data(accepts).isDefined => ListedDataSource(key, Option(d8a(scheduler).contentType))
      }
    }
    ListRepoSourcesResponse(sources.toList.sortBy(_.name))
  }
}

object SourceRepository {

  def apply(sourcesByName: (String, NewSource)*)(implicit scheduler: Scheduler): SourceRepository = {
    val sourceMap = sourcesByName.toMap.ensuring(_.size == sourcesByName.size)
    val sinkMap   = Map("sink" -> DataSink())
    apply(sourceMap, sinkMap, Map.empty[String, ConfigurableTransform[_]], Map.empty[String, Transform])
  }

  def apply(firstSource: (String, DataSource), sourcesByName: (String, DataSource)*)(implicit scheduler: Scheduler): SourceRepository = {
    val newSources = (firstSource +: sourcesByName).map {
      case (name, d8a) => (name, NewSource(d8a))
    }
    apply(newSources: _*)
  }
}
