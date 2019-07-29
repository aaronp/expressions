package pipelines

import com.typesafe.config.Config
import pipelines.client.jvm.{ClientSocketStateJVM, PipelinesClient}

import scala.concurrent.Future

case class IntegrationTestState(clientEnv: Env, config: Config, session: ClientSocketStateJVM) extends AutoCloseable {

  def client: PipelinesClient[Future] = session.client

  override def close(): Unit = {
    clientEnv.close()
  }
}

object IntegrationTestState {

  def apply(user: String = "admin", password: String = "password")(implicit clientEnv: Env): Future[IntegrationTestState] = {
    import args4c.implicits._

    //target/certificates/cert.p12
    val config: Config = (DevRestMain.devArgs).asConfig().resolve()

    val client: PipelinesClient[Future] = PipelinesClient(config)(clientEnv.ioScheduler).get

    client
      .newSession(user, password)
      .map { session =>
        new IntegrationTestState(clientEnv, config, session)
      }(clientEnv.ioScheduler)

  }
}
