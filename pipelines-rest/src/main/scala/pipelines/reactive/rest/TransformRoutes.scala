package pipelines.reactive.rest

import akka.http.scaladsl.server.Route
import pipelines.reactive.repo.{ListTransformationResponse, ListedTransformation, RepoSchemas, TransformEndpoints}
import pipelines.reactive.{ContentType, PipelineService, Transform}
import pipelines.rest.routes.{BaseCirceRoutes, SecureRouteSettings, SecureRoutes}

final case class TransformRoutes(pipelineService: PipelineService, secureSettings: SecureRouteSettings)
    extends SecureRoutes(secureSettings) //
    with TransformEndpoints              //
    with RepoSchemas                     //
    with BaseCirceRoutes                 //
    {

  def routes: Route = listTransformsRoute

  def listTransformsRoute: Route = {
    listTransforms.list.implementedBy {
      case (sourceTypeNameOpt, targetTypeOpt) =>
        // TODO - ContentType.apply is broken for parameterized types
        val sourceTypeOpt: Option[ContentType] = sourceTypeNameOpt.map(ContentType.apply)

        val results = pipelineService.transformsById().flatMap {
          case (name, transform: Transform) =>
            sourceTypeOpt match {
              case Some(srcType) =>
                // a source type has been specified, so if there is no result type then we should filter.
                transform.outputFor(srcType).flatMap { resultType =>
                  val matchesResultType = targetTypeOpt.fold(true)(_ == resultType)
                  if (matchesResultType) {
                    Option(ListedTransformation(name, sourceTypeOpt, Option(resultType), None))
                  } else {
                    None
                  }
                }
              case None => // so source type - just allow all transforms
                Option(ListedTransformation(name, sourceTypeOpt, None, None))
            }
        }

        ListTransformationResponse(results.toSeq.sortBy(_.name))
    }
  }

}
