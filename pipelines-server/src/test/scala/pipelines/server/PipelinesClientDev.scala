package pipelines.server

import args4c.ConfigApp
import com.typesafe.config.Config
import pipelines.Env
import pipelines.client.jvm.{ClientSession, PipelinesClient}

import scala.concurrent.Future
import scala.util.Try

object PipelinesClientDev extends ConfigApp {
  override type Result = Unit

  override def run(config: Config) = {
    implicit val env       = Env()
    implicit val scheduler = env.ioScheduler

    val user                            = Try(config.getString("user")).getOrElse("admin")
    val pwd                             = Try(config.getString("password")).getOrElse("password")
    val client: PipelinesClient[Future] = PipelinesClient(config).get
    client.newSession(user, pwd).foreach { session: ClientSession =>
      session.subscribeToSourceEvents()

      session.client.pushSource.pushEndpoint.apply(session.token


//      session.socketState.subscribeToSinkEvents()
    }

  }
}
