package expressions.rest.server.db

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, GivenWhenThen}
import zio._
import zio.duration._

import scala.util.Try
//import scala.concurrent.duration._
import scala.util.{Success, Using}

class PostgresTest extends AnyWordSpec with Matchers with Eventually with BeforeAndAfterAll with GivenWhenThen with ScalaFutures with BeforeAndAfterEach {

  import ZioSupport._

  def conf = PostgresConf(host = "localhost:5432", database = "expressions", user = "test", password = "test")

  "Postgres" should {
    "be able to query a database" in {

      val Success(_) = Using(conf.connect) { (conn: RichConnect) =>
        Try(MaterialisedTable.drop(conn.session))

        val inserted = TestData.insertPostgreSQL()(conn.session)
        withClue(inserted.toString) {
          inserted.isSuccess shouldBe true
        }

        val fetchRows = for {
          ref <- Ref.make(List[SqlRow]())
          _ <- conn
            .queryManaged(s"select count(1) from ${MaterialisedTable.TableName}")
            .use { r =>
              val move: UIO[Boolean] = r.nextRow.flatMap {
                case Some(row) => ref.update(row :: _) *> UIO(row.rowNr < 10)
                case None      => UIO(false)
              }
              move.repeatWhileEquals(true)
            }
            .retry(Schedule.exponential(250.milliseconds) && Schedule.recurs(10))
          rows <- ref.get
        } yield rows

        val Seq(count) = fetchRows.value()
        val got        = count.asMap("count")
        got shouldBe (12)

      }
    }
  }
}
