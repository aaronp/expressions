package expressions.rest.server.db

import zio.stream._
import zio.{UIO, ZIO}
import org.slf4j.LoggerFactory
import java.sql.ResultSet

case class RichResultSet(sql: String, results: ResultSet) extends AutoCloseable {

  private lazy val logger = LoggerFactory.getLogger(getClass)

  override def close() = {
    logger.info(s"closing result set for $sql")
    results.close()
  }

  private def hasNext: Boolean =
    try {
      val hn = !results.isClosed && results.next
      logger.info(s"result set hasNext is $hn for: $sql")
      hn
    } catch {
      case err: Throwable =>
        logger.error(s"result set.hasNext threw $err for query: $sql")
        false
    }

  val next: UIO[Boolean] = ZIO.effectTotal(hasNext)

  val nextRow: UIO[Option[SqlRow]] = next.map {
    case true =>
      val row = SqlRow(results)
      logger.info(s"nextRow for $sql yields:\n$row")
      Option(row)
    case false => None
  }

  val nextEntry = nextRow.map(_.map(_.asMap))

  val asStream = ZStream.fromEffectOption(nextRow).forever

}
