package expressions.rest.server.db

import scalikejdbc._
import zio.{Task, UIO, ZManaged}
import org.slf4j.LoggerFactory
import java.sql.{Connection, PreparedStatement, ResultSet}

import util.Try

case class RichConnect(conn: Connection) extends AutoCloseable {
  override def close(): Unit = {
    LoggerFactory.getLogger(getClass).info(s"Closing connection ${Try(conn.getMetaData.getURL).getOrElse("(metadata go boom)")}")
    conn.close()
  }

  implicit lazy val session: DBSession = DBSession(conn)

  def metadata = conn.getMetaData()

  private def statementFor(sql: String) = {
    LoggerFactory.getLogger(getClass).info(s"preparedStatement: $sql")
    RichStatement(sql, conn.prepareStatement(sql))
  }

  def preparedStatement(sql: String): ZManaged[Any, Throwable, RichStatement] = {
    ZManaged.make(Task(statementFor(sql)))(s => UIO(s.close()))
  }

  def queryManaged(sql: String): ZManaged[Any, Throwable, RichResultSet] =
    for {
      statement <- preparedStatement(sql)
      resultSet <- statement.queryAsManaged
    } yield resultSet

}
