package pipelines.reactive.repo

import cats.syntax.functor._
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import pipelines.reactive.{ContentType, HasMetadata}

/**
  * A request for operating on a repository
  */
sealed trait RepoRequest

object RepoRequest {
  implicit val encoder = Encoder.instance[RepoRequest] {
    case request @ ListRepoSourcesRequest(_, _)      => request.asJson
    case request @ CreateSourceAliasRequest(_, _, _) => request.asJson
    case request @ ListTransformationRequest(_)      => request.asJson
    case request @ ListSinkRequest(_, _)             => request.asJson
  }

  implicit val decoder: Decoder[RepoRequest] = {
    List[Decoder[RepoRequest]](
      Decoder[ListRepoSourcesRequest].widen,
      Decoder[CreateSourceAliasRequest].widen,
      Decoder[ListTransformationRequest].widen,
      Decoder[ListSinkRequest].widen
    ).reduceLeft(_ or _)
  }
}

/**
  * The response to a [[RepoRequest]]
  */
sealed trait RepoResponse
object RepoResponse {
  implicit val encoder = Encoder.instance[RepoResponse] {
    case request @ ListRepoSourcesResponse(_)    => request.asJson
    case request @ CreateSourceAliasResponse(_)  => request.asJson
    case request @ ListTransformationResponse(_) => request.asJson
    case request @ ListSinkResponse(_)           => request.asJson
  }

  implicit val decoder: Decoder[RepoResponse] = {
    List[Decoder[RepoResponse]](
      Decoder[ListRepoSourcesResponse].widen,
      Decoder[CreateSourceAliasResponse].widen,
      Decoder[ListTransformationResponse].widen,
      Decoder[ListSinkResponse].widen
    ).reduceLeft(_ or _)
  }
}

final case class DataTransform(name: String, configuration: Option[Json] = None)
object DataTransform {
  implicit val encoder: ObjectEncoder[DataTransform] = deriveEncoder[DataTransform]
  implicit val decoder: Decoder[DataTransform]       = deriveDecoder[DataTransform]
}

final case class ListedDataSource(override val metadata: Map[String, String], contentType: Option[ContentType]) extends HasMetadata {
  import pipelines.reactive.tags._
  def name(default: String = "")          = getOrElse(Name, default)
  def tag(default: String = "")           = getOrElse(Tag, default)
  def label(default: String = "")         = getOrElse(Label, default)
  def createdBy(default: String = "anon") = getOrElse(CreatedBy, default)
  def id(default: String = "empty")       = getOrElse(Id, default)
  private def getOrElse(tag: String, default: String = "") = {
    metadata.getOrElse(tag, default)
  }
  private def get(key: String) = metadata.get(key)

  def description: String = {
    val name = List(Name, Label, Tag, Id).view.flatMap(get).headOption.getOrElse("???")
    contentType match {
      case None     => name
      case Some(ct) => s"$name ($ct)"
    }
  }
}

object ListedDataSource {
  implicit val encoder: ObjectEncoder[ListedDataSource] = deriveEncoder[ListedDataSource]
  implicit val decoder: Decoder[ListedDataSource]       = deriveDecoder[ListedDataSource]
}


final case class ListedTransformation(name: String, inputType: Option[ContentType], resultType: Option[ContentType], config: Option[Json])
object ListedTransformation {
  implicit val encoder: ObjectEncoder[ListedTransformation] = deriveEncoder[ListedTransformation]
  implicit val decoder: Decoder[ListedTransformation]       = deriveDecoder[ListedTransformation]
}

/**
  *  LIST DATA SOURCE
  */
case class ListRepoSourcesRequest(metadataCriteria: Map[String, String], ofType: Option[ContentType]) extends RepoRequest
object ListRepoSourcesRequest {
  implicit val encoder: ObjectEncoder[ListRepoSourcesRequest] = deriveEncoder[ListRepoSourcesRequest]
  implicit val decoder: Decoder[ListRepoSourcesRequest]       = deriveDecoder[ListRepoSourcesRequest]
}

case class ListRepoSourcesResponse(sources: Seq[ListedDataSource]) extends RepoResponse {
  def metadataKeys: Set[String] = sources.map(_.metadata.keySet).toSet.flatten
}
object ListRepoSourcesResponse {
  implicit val encoder: ObjectEncoder[ListRepoSourcesResponse] = deriveEncoder[ListRepoSourcesResponse]
  implicit val decoder: Decoder[ListRepoSourcesResponse]       = deriveDecoder[ListRepoSourcesResponse]
}

/**
  *  LIST TRANSFORMATION
  */
case class ListTransformationRequest(inputContentType: Option[ContentType]) extends RepoRequest
object ListTransformationRequest {
  implicit val encoder: ObjectEncoder[ListTransformationRequest] = deriveEncoder[ListTransformationRequest]
  implicit val decoder: Decoder[ListTransformationRequest]       = deriveDecoder[ListTransformationRequest]
}

case class ListTransformationResponse(results: Seq[ListedTransformation]) extends RepoResponse
object ListTransformationResponse {
  implicit val encoder: ObjectEncoder[ListTransformationResponse] = deriveEncoder[ListTransformationResponse]
  implicit val decoder: Decoder[ListTransformationResponse]       = deriveDecoder[ListTransformationResponse]
}

/**
  *  LIST SINKS
  */
final case class ListedDataSink(override val metadata: Map[String, String], inputContentType: ContentType) extends HasMetadata {
  import pipelines.reactive.tags._
  def name(default: String = "")          = getOrElse(Name, default)
  def tag(default: String = "")           = getOrElse(Tag, default)
  def label(default: String = "")         = getOrElse(Label, default)
  def createdBy(default: String = "anon") = getOrElse(CreatedBy, default)
  def id(default: String = "empty")       = getOrElse(Id, default)
  private def getOrElse(tag: String, default: String = "") = {
    metadata.getOrElse(tag, default)
  }
  private def get(key: String) = metadata.get(key)
}
object ListedDataSink {
  implicit val encoder: ObjectEncoder[ListedDataSink] = io.circe.generic.semiauto.deriveEncoder[ListedDataSink]
  implicit val decoder: Decoder[ListedDataSink] = io.circe.generic.semiauto.deriveDecoder[ListedDataSink]
}


case class ListSinkRequest(metadataCriteria: Map[String, String], ofType: Option[ContentType]) extends RepoRequest
object ListSinkRequest {
  implicit val encoder: ObjectEncoder[ListSinkRequest] = deriveEncoder[ListSinkRequest]
  implicit val decoder: Decoder[ListSinkRequest]       = deriveDecoder[ListSinkRequest]
}

case class ListSinkResponse(results: Seq[ListedDataSink]) extends RepoResponse
object ListSinkResponse {
  implicit val encoder: ObjectEncoder[ListSinkResponse] = deriveEncoder[ListSinkResponse]
  implicit val decoder: Decoder[ListSinkResponse]       = deriveDecoder[ListSinkResponse]
}

/**
  * CREATE DATA SOURCE from an existing source and transformation
  */
case class CreateSourceAliasRequest(baseDataSource: String, transformations: Seq[String], newDataSourceName: Option[String] = None) extends RepoRequest
object CreateSourceAliasRequest {
  implicit val encoder: ObjectEncoder[CreateSourceAliasRequest] = deriveEncoder[CreateSourceAliasRequest]
  implicit val decoder: Decoder[CreateSourceAliasRequest]       = deriveDecoder[CreateSourceAliasRequest]
}

case class CreateSourceAliasResponse(dataSource: String) extends RepoResponse
object CreateSourceAliasResponse {
  implicit val encoder: ObjectEncoder[CreateSourceAliasResponse] = deriveEncoder[CreateSourceAliasResponse]
  implicit val decoder: Decoder[CreateSourceAliasResponse]       = deriveDecoder[CreateSourceAliasResponse]
}
