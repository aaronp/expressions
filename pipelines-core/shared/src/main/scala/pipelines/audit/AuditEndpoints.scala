package pipelines.audit

import pipelines.core.BaseEndpoint

trait AuditEndpoints extends BaseEndpoint {

  object getAuditable {
    def getRequest                                                                   = get(path / "audit")
    def getResponse(implicit resp: JsonResponse[Seq[String]]): Response[Seq[String]] = jsonResponse[Seq[String]]()
    def getEndpoint(implicit resp: JsonResponse[Seq[String]]) = {
      endpoint(getRequest, getResponse, summary = Option("Lists all of the available auditable collections"))
    }
  }

  object getAuditVersions {
    def getRequest = {
      val fromQS  = qs[Option[Int]]("from", Option("The optional first revision"))
      val limitQS = qs[Option[Int]]("limit", Option("The maximum versions to return"))
      get(path / "audit" / segment[String]("collectionName") /? (fromQS & limitQS))
    }
    def getResponse(implicit resp: JsonResponse[AuditVersion]): Response[AuditVersion] = jsonResponse[AuditVersion]()
    def getEndpoint(implicit resp: JsonResponse[AuditVersion]) = {
      endpoint(getRequest, getResponse, summary = Option("The revisions for the particular collection"))
    }
  }
}
