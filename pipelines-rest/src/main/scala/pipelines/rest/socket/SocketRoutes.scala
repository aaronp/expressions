package pipelines.rest.socket

import akka.http.scaladsl.model.headers.{HttpChallenges, RawHeader}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import pipelines.rest.RestMain
import pipelines.rest.routes.{BaseCirceRoutes, SecureRouteSettings, WebSocketTokenCache}
import pipelines.users.Claims

/**
  * Exposes a means to create (or reuse) a socket for the authenticated user.
  *
  * This just wraps some WS protocol/auth stuff around the 'handleSocket' thunk, which gets invoked when an authenticated
  * user successfully creates a [[ServerSocket]]. The additional query params from the GET request are included
  *
  * @param settings the secure settings required for JWT
  * @param handleSocket the logic of what to do with the new socket
  */
final class SocketRoutes(settings: SecureRouteSettings, tokens: WebSocketTokenCache, handleSocket: (Claims, ServerSocket, Map[String, String]) => Unit)
    extends BaseSocketRoutes(settings)
    with SocketEndpoint
    with SocketSchemas
    with BaseCirceRoutes {
  def routes: Route = connectSocket ~ generateSocketToken ~ subscribeSocket

  private val wtf = implicitly[JsonResponse[String]]

  def generateSocketToken: Route = {
    val withJwt = socketTokens.newToken(wtf).request.flatMap(_ => jwtHeaderOpt)
    withJwt {
      case Some(jwt) =>
        val uuid: String = tokens.generate(jwt)
        socketTokens.response.apply(uuid)
      case None => Directives.reject(AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsMissing, HttpChallenges.oAuth2(settings.realm)))
    }
  }

  def subscribeSocket: Route = {

    authenticated { user =>
      socketSubscribe.subscribe.implementedBy { request: SocketSubscribeRequest =>
//      val service : PipelinesSe
//      new SubscribeOnMatchSink(service)
//      }
        ???
      }
    }
  }

  def connectSocket: Route = {
    authenticatedConnect {
      case (tokenProtocol, user) =>
        Directives.extractUri { uri =>
          val created = s"${user.name} created socket at ${timestamp()} from $tokenProtocol"
          logger.info(created)
          tokens.validateAndRemove(tokenProtocol)

          val settings = SocketSettings(user.name, created)
          withSocketRoute(settings) {
            case (_, socket) =>
              val metadata = RestMain.queryParamsForUri(uri, user)
              handleSocket(user, socket, metadata)
          }
        }
    }
  }

  /**
    * Ooh!?!? What is all this yuck/noise?
    *
    * Well, this is to support sending either a `Sec-Websocket-Protocol` header, which is the case when a javascript
    * WebSocket connection is created having supplied an array of protocols.
    *
    * In our case, that array of protocols is the connection token which should've been retrieved from the 'generateSocketToken'
    * route.
    *
    * We then have to reply w/ the same header to complete the handshake (i.e. agree to speak over that 'protocol', which
    * isn't a protocol).
    *
    * The other scenario is if the JWT auth bearer header is present directly, as might the case from a Java/Scala client
    */
  private def authenticatedConnect: Directive[Tuple1[(String, Claims)]] = sockets.connect(wtf).request.flatMap { _ =>
    val connectTokenAndClaims: Directive[Tuple1[(String, Claims)]] = {
      val fromTempToken: Directive[(String, Claims)] = socketTokenOpt.collect({
        case SocketTokenClaims(tokenAndClaims) => tokenAndClaims
      }, authFailedRejection)

      fromTempToken.tflatMap {
        case entry @ (token, _) =>
          // TODO - validate the key using socketTokenKeyOpt
          // SO: https://stackoverflow.com/questions/18265128/what-is-sec-websocket-key-for

          respondWithHeader(RawHeader(`Sec-Websocket-Protocol`, token)).tflatMap { _ =>
            Directives.provide(entry)
          }
      }
    }

    // accept either the auth bearer JWT token OR one provided from having retrieved it from the connection token look-up
    val rawJwtSupplied: Directive[Tuple1[(String, Claims)]] = authenticated.map { claims =>
      new Tuple1[(String, Claims)]("" -> claims)
    }
    connectTokenAndClaims | rawJwtSupplied
  }

  private[socket] def connectAnySocket(user: Claims): Route = {
    sockets.connect(wtf).request { _ =>
      val created  = s"${user.name} created at ${timestamp()}"
      val settings = SocketSettings(user.name, created)

      Directives.extractUri { uri =>
        withSocketRoute(settings) {
          case (_, socket) => handleSocket(user, socket, RestMain.queryParamsForUri(uri, user))
        }
      }
    }
  }

  // custom extractor used to support a .collect call in authenticatedConnect
  private object SocketTokenClaims {
    def unapply(socketTokenOpt: Option[String]): Option[(String, Claims)] = {
      for {
        socketToken <- socketTokenOpt
        jwtString   <- tokens.get(socketToken)
        jwt         <- parseJwt(jwtString).right.toOption
        if !isExpired(jwt)
      } yield {
        socketToken -> jwt.claims
      }
    }
  }

}