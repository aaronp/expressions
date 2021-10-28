package expressions.rest.server.db

import zio.ZManaged._
import zio._
import org.slf4j.LoggerFactory
import java.sql.{PreparedStatement, ResultSet}

case class RichStatement(sql: String, statement: PreparedStatement) extends AutoCloseable {
  private lazy val logger = LoggerFactory.getLogger(getClass)

  override def close() = {
    logger.info(s"closing statement set for $sql")
    statement.close()
  }

  private def execute = {
    logger.info(s"Opening (executing) result set: $sql")
    RichResultSet(sql, statement.executeQuery())
  }

  def queryAsManaged: ZManaged[Any, Throwable, RichResultSet] =
    ZManaged.make(Task(execute))(set => UIO(set.close()))
}
