package expressions.rest.server.db

import scalikejdbc._
import zio.{Task, UIO, ZManaged}

import java.sql.{Connection, PreparedStatement, ResultSet}
import com.typesafe.scalalogging.StrictLogging

import util.Try

case class RichConnect(conn: Connection) extends AutoCloseable with StrictLogging {
  override def close(): Unit = {
    logger.info(s"Closing connection ${Try(conn.getMetaData.getURL).getOrElse("(metadata go boom)")}")
    conn.close()
  }

  implicit lazy val session: DBSession = DBSession(conn)

  def metadata = conn.getMetaData()

  private def statementFor(sql: String) = {
    logger.info(s"preparedStatement: $sql")
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
