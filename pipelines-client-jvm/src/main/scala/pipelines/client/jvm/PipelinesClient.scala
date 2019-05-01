package pipelines.client.jvm

import com.softwaremill.sttp
import com.softwaremill.sttp.{SttpBackend, TryHttpURLConnectionBackend}
import com.typesafe.config.Config
import javax.net.ssl.{HttpsURLConnection, SSLContext}
import pipelines.admin.LoginEndpoints
import pipelines.reactive.repo.SourceRepoEndpoints
import pipelines.socket.SocketEndpoint
import pipelines.ssl.SSLConfig
import pipelines.users.{LoginRequest, LoginResponse}

import scala.util.{Failure, Success, Try}

class PipelinesClient[R[_]](val host: String, backend: sttp.SttpBackend[R, _], defaultSslContext: Option[SSLContext] = None)
    extends endpoints.sttp.client.Endpoints[R](host, backend)
    with endpoints.algebra.circe.JsonEntitiesFromCodec
    with endpoints.circe.JsonSchemas
    with endpoints.sttp.client.JsonEntitiesFromCodec[R]
    with LoginEndpoints
    with SocketEndpoint
    with SourceRepoEndpoints {

  implicit def loginRequestSchema: JsonSchema[LoginRequest]   = JsonSchema(implicitly, implicitly)
  implicit def loginResponseSchema: JsonSchema[LoginResponse] = JsonSchema(implicitly, implicitly)

  def login(login: LoginRequest): R[LoginResponse]            = loginEndpoint.apply(login -> None)
  def login(user: String, password: String): R[LoginResponse] = login(LoginRequest(user, password))

  def newSession(user: String, password: String)(implicit ev: R[LoginResponse] =:= Try[LoginResponse]): Try[ClientSession[R]] = {
    newSession(LoginRequest(user, password))
  }
  def newSession(request: LoginRequest)(implicit ev: R[LoginResponse] =:= Try[LoginResponse]): Try[ClientSession[R]] = {

    val loginRespR = ev(login(request))
    loginRespR.flatMap { response =>
      response.jwtToken match {
        case Some(jwt) =>
          val socketRequest: SttpRequest = sockets.request(Unit)
          val webSocketUri               = socketRequest.uri.toString.replaceAllLiterally("http", "ws")
          Success(new ClientSession(this, jwt, defaultSslContext, webSocketUri, request.user))
        case None => Failure(new Exception("Invalid login"))
      }
    }
  }

}

object PipelinesClient {

  def apply(rootConfig: Config): Try[PipelinesClient[Try]] = {
    SSLConfig(rootConfig).newContext.map { ctxt: SSLContext =>
      val hostPort = rootConfig.getString("pipelines.client.hostport")
      forHost(s"https://$hostPort", Option(ctxt))
    }
  }

  def forHost(host: String, sslContext: Option[SSLContext] = None): PipelinesClient[Try] = {
    val backend = TryHttpURLConnectionBackend(customizeConnection = {
      case conn: HttpsURLConnection =>
        sslContext.foreach { ctxt =>
          conn.setSSLSocketFactory(ctxt.getSocketFactory)
        }
      case _ =>
    })
    apply[Try](host, sslContext)(backend)
  }

  def apply[F[_]](host: String, sslContext: Option[SSLContext])(implicit backend: SttpBackend[F, _]): PipelinesClient[F] = {
    new PipelinesClient[F](host, backend, sslContext)
  }

}
