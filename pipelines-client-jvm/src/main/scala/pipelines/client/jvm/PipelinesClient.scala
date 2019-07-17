package pipelines.client.jvm

import com.softwaremill.sttp
import com.softwaremill.sttp.{FutureMonad, HttpURLConnectionBackend, MonadError, Request, Response, SttpBackend, TryHttpURLConnectionBackend}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import javax.net.ssl.{HttpsURLConnection, SSLContext}
import pipelines.Env
import pipelines.auth.AuthEndpoints
import pipelines.reactive.repo.{SourceEndpoints, TransformEndpoints}
import pipelines.rest.socket.SocketEndpoint
import pipelines.ssl.SSLConfig
import pipelines.users.{CreateUserRequest, CreateUserResponse, LoginEndpoints, LoginRequest, LoginResponse, UserEndpoints, UserRoleEndpoints, UserSchemas}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Try

class PipelinesClient[R[_]](val host: String, backend: sttp.SttpBackend[R, _], defaultSslContext: Option[SSLContext] = None)
    extends endpoints.sttp.client.Endpoints[R](host, backend)
    with endpoints.algebra.circe.JsonEntitiesFromCodec
    with endpoints.circe.JsonSchemas
    with endpoints.sttp.client.JsonEntitiesFromCodec[R]
    with LoginEndpoints
    with UserEndpoints
    with SocketEndpoint
    with AuthEndpoints
    with UserRoleEndpoints
    with SourceEndpoints
    with TransformEndpoints
    with UserSchemas {

  def newUser(request : CreateUserRequest): R[CreateUserResponse] = createUser.createUserEndpoint.apply(request)

  def login(login: LoginRequest): R[LoginResponse]            = userLogin.loginEndpoint.apply(login -> None)
  def login(user: String, password: String): R[LoginResponse] = login(LoginRequest(user, password))

  def newSession(user: String, password: String)(implicit ev: R[LoginResponse] =:= Future[LoginResponse], env: Env): Future[ClientSession[R]] = {
    newSession(LoginRequest(user, password))
  }

  def newSession(request: LoginRequest)(implicit ev: R[LoginResponse] =:= Future[LoginResponse], env: Env): Future[ClientSession[R]] = {
    val loginRespR           = ev(login(request))
    implicit val execContent = env.ioScheduler

    loginRespR.flatMap { response: LoginResponse =>
      response.jwtToken match {
        case Some(jwt) =>
          val socketRequest: SttpRequest = sockets.request(Unit)
          val webSocketUri               = socketRequest.uri.toString.replaceAllLiterally("http", "ws")
          ClientSession(this, jwt, defaultSslContext, webSocketUri, request.user)
        case None => Future.failed(new Exception(s"Login request for ${request.user} failed - no token"))
      }
    }
  }

}

object PipelinesClient extends StrictLogging {

  def apply(rootConfig: Config)(implicit executionContext: ExecutionContext): Try[PipelinesClient[Future]] = {
    SSLConfig(rootConfig).newContext.map { ctxt: SSLContext =>
      forHost(s"https://${hostPort(rootConfig)}", Option(ctxt))
    }
  }
  def sync(rootConfig: Config) : Try[PipelinesClient[Try]] = {
    SSLConfig(rootConfig).newContext.map { ctxt: SSLContext =>
      forHostSync(s"https://${hostPort(rootConfig)}", Option(ctxt))
    }
  }
  private def hostPort(rootConfig: Config) = rootConfig.getString("pipelines.client.hostport")

  def forHost(host: String, sslContext: Option[SSLContext] = None)(implicit executionContext: ExecutionContext): PipelinesClient[Future] = {
    object futureBackend extends SttpBackend[Future, Nothing] {
      val backend = HttpURLConnectionBackend(customizeConnection = {
        case conn: HttpsURLConnection => sslContext.map(_.getSocketFactory).foreach(conn.setSSLSocketFactory)
        case _                        =>
      })
      override def send[T](request: Request[T, Nothing]): Future[Response[T]] = {
        val promise = Promise[Response[T]]
        executionContext.execute(new Runnable {
          override def run(): Unit = {
            promise.tryComplete(Try(backend.send(request)))
          }
        })
        promise.future
      }

      override def close(): Unit = backend.close()

      override lazy val responseMonad: MonadError[Future] = {
        new FutureMonad()(executionContext)

      }
    }

    logger.info(s"Connecting to $host")

    apply[Future](host, sslContext)(futureBackend)
  }

  def forHostSync(host: String, sslContext: Option[SSLContext] = None): PipelinesClient[Try] = {
    val backend = TryHttpURLConnectionBackend(customizeConnection = {
      case conn: HttpsURLConnection => sslContext.map(_.getSocketFactory).foreach(conn.setSSLSocketFactory)
      case _                        =>
    })

    logger.info(s"Connecting to $host")

    apply[Try](host, sslContext)(backend)
  }

  def apply[F[_]](host: String, sslContext: Option[SSLContext])(implicit backend: SttpBackend[F, _]): PipelinesClient[F] = {
    new PipelinesClient[F](host, backend, sslContext)
  }

}
