package expressions.rest.server

import com.typesafe.config.Config
import zio.{Ref, Task, ZIO}

object Disk {

  def apply(rootConfig: Config): Task[Service] = {
    rootConfig.getString("app.disk") match {
      case "" => Service()
      case path =>
        import eie.io._
        Task(Service(path.asPath))
    }
  }

  trait Service {

    /** @param path
      * @param body
      * @return true if created
      */
    def write(path: Seq[String], body: String): Task[Boolean]

    def read(path: Seq[String]): Task[Option[String]]
  }
  object Service {

    def apply(): ZIO[Any, Nothing, Service] = Ref.make(Map[Seq[String], String]()).map(inMemory)

    def apply(dataDir: java.nio.file.Path) = new Service {
      import eie.io._

      private def fileFor(path: Seq[String]) = dataDir.resolve(path.mkString("/"))
      override def write(path: Seq[String], body: String): Task[Boolean] = {
        Task {
          val file    = fileFor(path)
          val created = !(file.exists() && file.isFile)
          file.text = body
          created
        }
      }

      override def read(path: Seq[String]): Task[Option[String]] = Task {
        val file = fileFor(path)
        if (file.exists()) {
          Some(file.text)
        } else None
      }
    }

    def inMemory(dataDir: Ref[Map[Seq[String], String]]) = new Service {
      override def write(path: Seq[String], body: String): Task[Boolean] = dataDir.modify { byPath =>
        val created = !byPath.contains(path)
        created -> byPath.updated(path, body)
      }
      override def read(path: Seq[String]): Task[Option[String]] = dataDir.get.map(_.get(path))
    }
  }

}
