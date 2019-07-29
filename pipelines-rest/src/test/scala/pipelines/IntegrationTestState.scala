package pipelines

import com.typesafe.config.Config
import pipelines.client.jvm.{ClientSession, PipelinesClient}

import scala.concurrent.Future

case class IntegrationTestState(clientEnv: Env, config: Config, client: PipelinesClient[Future], session: ClientSession) extends AutoCloseable {

  override def close(): Unit = {
    clientEnv.close()
  }
}

object IntegrationTestState {

  def apply(user: String = "admin", password: String = "password")(implicit clientEnv: Env): Future[IntegrationTestState] = {
    import args4c.implicits._

    val config: Config = DevRestMain.devArgs.asConfig().resolve()

    val client: PipelinesClient[Future] = PipelinesClient(config)(clientEnv.ioScheduler).get

    client
      .newSession(user, password)
      .map { session =>
        new IntegrationTestState(clientEnv, config, client, session)
      }(clientEnv.ioScheduler)

  }
}
