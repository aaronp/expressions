package pipelines.reactive

import scala.concurrent.Future

trait Lift[F[_]] {
  def lift[A](a: A): F[A]
}

object Lift {
  def apply[F[_]](implicit instance: Lift[F]): Lift[F] = instance

  implicit object FutureLift extends Lift[Future] {
    override def lift[A](a: A): Future[A] = Future.successful(a)
  }
}
