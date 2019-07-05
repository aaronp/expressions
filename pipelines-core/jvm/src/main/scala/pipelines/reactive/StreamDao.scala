package pipelines.reactive

import com.typesafe.scalalogging.StrictLogging

/**
  * Represents a place to store sources, sinks, transforms, chains...
  */
trait StreamDao[F[_]] {
  def persistSource(metadata: Map[String, String]): F[Unit]
}

object StreamDao {
  def noop[F[_]](implicit lift: Lift[F]) = new StreamDao[F] with StrictLogging {
    override def persistSource(metadata: Map[String, String]): F[Unit] = {
      logger.info("no-op NOT persisting " + metadata)
      Lift[F].lift(Unit)
    }
  }
}
