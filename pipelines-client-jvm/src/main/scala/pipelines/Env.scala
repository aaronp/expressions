package pipelines

import akka.actor.ActorSystem
import akka.http.scaladsl.settings.RoutingSettings
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import monix.execution.{Scheduler, UncaughtExceptionReporter}

final class Env(materializer: ActorMaterializer) extends AutoCloseable {

  lazy val computeScheduler                         = {
    Scheduler.computation(name = "pipelines-env-compute", reporter = Env.reporter("pipelines-io"))
  }
  lazy val ioScheduler                              = {
    Scheduler.io("pipelines-env-io", reporter = Env.reporter("pipelines-io"))
  }
  implicit val actorMaterializer: ActorMaterializer = materializer
  implicit val system                               = actorMaterializer.system
  implicit val routingSettings: RoutingSettings     = RoutingSettings(system)

  override def close(): Unit = {
    computeScheduler.shutdown()
    ioScheduler.shutdown()
    actorMaterializer.shutdown()
    system.terminate()
  }
}
object Env {
  case class reporter(name: String) extends UncaughtExceptionReporter with StrictLogging {
    override def reportFailure(ex: Throwable): Unit = {
      Thread.getDefaultUncaughtExceptionHandler match {
        case null => logger.error(s"Uncaught Exception in $name: $ex", ex)
        case h =>
          logger.error(s"Passing uncaught exception in $name to default exception reporter: $ex")
          h.uncaughtException(Thread.currentThread(), ex)
      }
    }
  }
  def apply(implicit system: ActorSystem = ActorSystem(getClass.getSimpleName.filter(_.isLetter))): Env = {
    new Env(ActorMaterializer())
  }
}
