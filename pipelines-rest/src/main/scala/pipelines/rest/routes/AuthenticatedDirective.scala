package pipelines.rest.routes
import java.time.{ZoneId, ZonedDateTime}

import akka.http.scaladsl.model.headers.{Authorization, HttpChallenges, OAuth2BearerToken, RawHeader}
import akka.http.scaladsl.model.{headers, _}
import akka.http.scaladsl.server.Directives.respondWithHeader
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import javax.crypto.spec.SecretKeySpec
import pipelines.core.Redirection
import pipelines.users.Claims
import pipelines.users.jwt.JsonWebToken.JwtError
import pipelines.users.jwt.JsonWebToken

/**
  * Mixing in this trait will offer an 'authenticated' directive to allow routes to require authentication.
  *
  * If these routes match but do not have a valid [[JsonWebToken]] then they will get either an Unauthorized status code
  * or be temporarily redirected to the login page (as determined by the 'loginUri' function) with a 'redirectTo' query
  * parameter specified w/ the original uri so that, upon successful login, the user can then be redirected to their original
  * intended endpoint.
  *
  * @see endpoints.akkahttp.server.BasicAuthentication
  */
trait AuthenticatedDirective extends StrictLogging {

  val `Sec-Websocket-Protocol` = "Sec-Websocket-Protocol"
  val `Sec-WebSocket-Key`      = "Sec-WebSocket-Key"

  val jwtHeaderOpt: Directive1[Option[String]] = Directives.optionalHeaderValue(extractCredentials)

  /**
    * Extracts the token specified in the wss protocol which was set via WebSocketTokenCache
    */
  val socketTokenOpt: Directive1[Option[String]]    = Directives.optionalHeaderValue(extractSocketToken)
  val socketTokenKeyOpt: Directive1[Option[String]] = Directives.optionalHeaderValue(extractSocketKey)

  protected def parseJwt(jwtTokenString: String): Either[JwtError, JsonWebToken] = {
    JsonWebToken.forToken(jwtTokenString, secret)
  }

  def authFailedRejection = AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsRejected, HttpChallenges.oAuth2(realm))
  def authFailed          = Directives.reject(authFailedRejection)

  def authRejectedRejection = AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsMissing, HttpChallenges.oAuth2(realm))
  def authRejected          = Directives.reject(authRejectedRejection)

  /**
    * Extracts the credentials from the request headers.
    * In case of absence of credentials rejects request
    */
  lazy val authenticatedEither: Directive1[Either[HttpResponse, Claims]] = {
    jwtHeaderOpt.flatMap {
      case Some(jwtTokenString) =>
        parseJwt(jwtTokenString) match {
          case Right(jwt) if !isExpired(jwt) => onValidToken(jwtTokenString, jwt)
          case Right(jwt)                    =>
            //onExpiredToken(jwt)
            logger.info(s"Expired token: $jwt")
            authFailed
          case Left(err) =>
            logger.warn(s"Invalid token: $err")
//            onInvalidToken(err)
            authFailed
        }
      case None =>
        //onMissingToken
        authRejected
    }
  }

  def authenticated: Directive[Tuple1[Claims]] = {
    authenticatedEither.collect {
      case Right(claims: Claims) => claims
    }
  }

  /** @return the secret to use to sign JWTokens
    */
  protected def secret: SecretKeySpec

  /** @return the Bearer auth realm
    */
  protected def realm: String

  /** @return the current time. This is exposed to allow implementations to have specific control, such as in tests
    */
  protected def now: ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC"))

  protected def isExpired(jwt: JsonWebToken): Boolean = jwt.claims.isExpired(now)

  /**
    * @param intendedPath the original URI the user was trying to access
    * @return a login URI which may contain a 'redirectTo' query paramter which specifies this intendecPath
    */
  def loginUri(intendedPath: Uri): Uri

  /** provides a means to update a token - e.g. perhaps by resetting its expiry on access
    *
    * @param originalJwtToken the input JWT as a string, for convenience/performance should we simply want to return and use the same token again
    * @param token the parsed JWT
    * @param secret the secret to use to encode an updated token
    * @return an updated json web token
    */
  protected def updateTokenOnAccess(originalJwtToken: String, token: JsonWebToken, secret: SecretKeySpec): String = {
    originalJwtToken
  }

  // continue to return the token
  protected def onValidToken(jwtTokenString: String, jwt: JsonWebToken) = {
    val newToken = updateTokenOnAccess(jwtTokenString, jwt, secret)
    val withCreds: Directive[Tuple1[Claims]] = respondWithHeader(RawHeader("X-Access-Token", newToken)).tflatMap { _ =>
      Directives.provide(jwt.claims)
    }
    withCreds.map { claims =>
      val either: Either[HttpResponse, Claims] = Right(claims)
      either
    }
  }

  /**
    * https://stackoverflow.com/questions/8389253/correct-http-status-code-for-resource-which-requires-authorization
    *
    * @return
    */
  protected def onMissingToken(): Directive[Tuple1[Either[HttpResponse, Claims]]] = {
    unauthorizedDirectiveAsEither
  }

//  protected def redirectDirective(explanation: String) = {
//    val direct: Route = Directives.extractUri { uri =>
//      logger.info(s"$explanation, redirecting for ${uri}")
//      Directives.redirect(loginUri(uri), StatusCodes.TemporaryRedirect)
//    }
//  }

  protected def onInvalidToken(err: JwtError) = {
    unauthorizedDirectiveAsEither
  }

  protected def unauthorizedDirectiveAsEither: Directive[Tuple1[Either[HttpResponse, Claims]]] = {
    unauthorizedDirective.map { resp: HttpResponse =>
      val either: Either[HttpResponse, Claims] = Left(resp)
      either
    }
  }

  protected def unauthorizedDirective = {
//    Directives.complete(
//      HttpResponse(
//        StatusCodes.Unauthorized,
//        scala.collection.immutable.Seq[HttpHeader](headers.`WWW-Authenticate`(HttpChallenges.oAuth2(realm)))
//      ))

    Directives.extractUri.map { uri =>
      val loginPage: Uri = loginUri(uri)

      //ContentTypes.`application/json`,
      val redirectBody = HttpEntity(Redirection(loginPage.toString).toString)
      HttpResponse(
        StatusCodes.Unauthorized,
        scala.collection.immutable.Seq[HttpHeader](headers.`WWW-Authenticate`(HttpChallenges.oAuth2(realm))),
        entity = redirectBody
      )
    }
  }

  // it's valid, but expired. We redirect to login
  protected def onExpiredToken(jwt: JsonWebToken) = {
//    unauthorizedDirectiveIgnoreClaims
//    redirectDirective(s"onExpiredToken($jwt)")
    unauthorizedDirectiveAsEither
  }

  private def extractCredentials(header: HttpHeader): Option[String] = {
    header match {
      case Authorization(OAuth2BearerToken(jwt)) => Some(jwt)
      case _ =>
        header.name.toLowerCase match {
          case "x-access-token" => Some(header.value)
          case _                => None
        }
    }
  }

  /**
    * This heading is set when connecting a wss:// having specified the protocol
    *
    * @param header
    * @return
    */
  private def extractSocketToken(header: HttpHeader): Option[String] = extractHeaderWithName(`Sec-Websocket-Protocol`, header)
  private def extractSocketKey(header: HttpHeader): Option[String]   = extractHeaderWithName(`Sec-WebSocket-Key`, header)
  private def extractHeaderWithName(expected: String, header: HttpHeader): Option[String] = {
    header.name match {
      case name if name.equalsIgnoreCase(expected) => Some(header.value)
      case _                                       => None
    }
  }

}
