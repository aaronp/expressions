package pipelines

import akka.actor.ActorSystem
import akka.http.scaladsl.settings.RoutingSettings
import akka.stream.ActorMaterializer
import monix.execution.Scheduler

class Env(materializer: ActorMaterializer) extends AutoCloseable {
  lazy val computeScheduler                         = Scheduler.computation()
  lazy val ioScheduler                              = Scheduler.io()
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
  def apply(implicit system: ActorSystem = ActorSystem(getClass.getSimpleName.filter(_.isLetter))): Env = {
    new Env(ActorMaterializer())
  }
}
