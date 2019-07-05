package pipelines.reactive

import pipelines.core.BaseEndpoint
import pipelines.rest.socket.AddressedTextMessage

/**
  * Regular REST messages to drive a user-specific data source
  */
trait PushEndpoints extends BaseEndpoint {

  object push {
    def pushRequestPost(implicit req: JsonRequest[AddressedTextMessage]): Request[AddressedTextMessage] = {
      post(path / "manual" / "push", jsonRequest[AddressedTextMessage](Option("A means to drive a datasource by pushing data to a user-specific endpoint")))
    }
    def pushEndpointPost(implicit req: JsonRequest[AddressedTextMessage]): Endpoint[AddressedTextMessage, Unit] = endpoint(pushRequestPost, emptyResponse())

    def pushRequestGet() = {
      get(path / "manual" / "push" / segment[String]("to") /? qs[String]("data"))
    }
    def pushEndpointGet: Endpoint[(String, String), Unit] =
      endpoint(pushRequestGet, emptyResponse(), summary = Option("A convenience to be able to push data via GET, although that's does break with convention"))

    def pushEndpointCancel = endpoint(delete(path / "manual" / "cancel"), emptyResponse())

    def pushEndpointError = endpoint(delete(path / "manual" / "error" /? qs[Option[String]]("message")), emptyResponse())
  }
}
