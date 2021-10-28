package expressions.rest.server.db

import scalikejdbc._

case class MaterialisedTable(kind: String, id: String, version: Long, record: String) {
  def insert(implicit session: DBSession): Int = {
    val update = sql"insert into Materialised (kind, id, version, record) values ($kind, $id, $version, $record)".update
    update.apply()
  }
}

object MaterialisedTable {

  val TableName = "Materialised"

  def createPostgreSQL(implicit session: DBSession): Boolean = {
    sql"""CREATE TABLE IF NOT EXISTS Materialised (
         kind   VARCHAR(255) NOT NULL,
           id   VARCHAR(255) NOT NULL,
      version   integer NOT NULL,
      record    VARCHAR(512) NOT NULL
      )""".execute.apply()
  }

  def drop(implicit session: DBSession): Unit = sql"DROP TABLE IF EXISTS Materialised".execute.apply()
}
