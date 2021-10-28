package expressions.rest.server.db

import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import java.sql.DriverManager

/**
  * https://hub.docker.com/r/oracleinanutshell/oracle-xe-11g
  *
  * @param url
  * @param username
  * @param password
  */
case class PostgresConf(url: String) {

  def connect: RichConnect = {
    val conn = DriverManager.getConnection(url)
    LoggerFactory.getLogger(getClass).info(s"Connecting postgres at >$url<")
    RichConnect(conn)
  }
}

object PostgresConf {

  def isEmpty(rootConfig: Config) = rootConfig.getString("app.db.host").isEmpty

  def isNonEmpty(rootConfig: Config) = !isEmpty(rootConfig)

  def apply(rootConfig: Config): PostgresConf = {
    val dbConfig = rootConfig.getConfig("app.db")
    PostgresConf(
      host = dbConfig.getString("host"),
      database = dbConfig.getString("database"),
      user = dbConfig.getString("user"),
      password = dbConfig.getString("password"),
      ssl = dbConfig.getBoolean("ssl")
    )
  }

  def apply(host: String, database: String, user: String, password: String, ssl: Boolean = false): PostgresConf = {
    forConnectionString(s"jdbc:postgresql://$host/$database?user=$user&password=$password&ssl=$ssl")
  }

  def forConnectionString(url: String): PostgresConf = {
    // this is required
    Class.forName(classOf[org.postgresql.Driver].getName)
    new PostgresConf(url)
  }
}
