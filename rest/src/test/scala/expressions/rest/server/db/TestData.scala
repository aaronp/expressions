package expressions.rest.server.db

import com.typesafe.scalalogging.StrictLogging
import scalikejdbc.DBSession

import scala.util.Try

object TestData extends StrictLogging {

  def testRecords: Seq[MaterialisedTable] =
    for {
      id   <- Seq(10, 100)
      amnt <- Seq(2, 3)
      name <- Seq("foo", "bar", "BAR")
    } yield MaterialisedTable(name, id.toString, 1, amnt.toString)

  def insertPostgreSQL(dataOne: Seq[MaterialisedTable] = testRecords)(implicit session: DBSession) = {
    for {
      _      <- Try(MaterialisedTable.createPostgreSQL)
      result <- Try(dataOne.map(_.insert).sum)
    } yield result
  }

}
