package expressions.rest.server

import com.typesafe.config.Config
import zio.{Ref, Task, ZIO}

object Disk {

  type SubDir          = String
  type FullPathToEntry = Seq[String]
  type ListEntry       = Either[FullPathToEntry, SubDir]

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

    /**
      * @return either the leaves as full paths (Left values) or the subdirectories (Right values)
      */
    def list(path: Seq[String]): Task[Seq[ListEntry]]
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

      override def list(path: Seq[String]): Task[Seq[ListEntry]] = Task {
        val subDir = fileFor(path)
        if (subDir.exists()) {
          if (subDir.isDir) {
            val entries: Array[Either[FullPathToEntry, SubDir]] = subDir.children.collect {
              case child if child.isFile => Left(path :+ child.fileName)
              case child if child.isDir  => Right(child.fileName)
            }
            entries.toList
          } else if (subDir.isFile) {
            List(Left(path))
          } else {
            Nil
          }
        } else Nil
      }
    }

    def inMemory(dataDir: Ref[Map[Seq[String], String]]) = new Service {
      override def write(path: Seq[String], body: String): Task[Boolean] = dataDir.modify { byPath =>
        val created = !byPath.contains(path)
        created -> byPath.updated(path, body)
      }
      override def read(path: Seq[String]): Task[Option[String]] = dataDir.get.map(_.get(path))

      override def list(path: Seq[String]): Task[List[ListEntry]] = {
        dataDir.get.map { map =>
          val eithers = map.keys.collect {
            case fullPath if fullPath == path          => Left(fullPath)
            case fullPath if fullPath.startsWith(path) => Right(fullPath.drop(path.size).head)
          }
          eithers.toList
        }
      }
    }
  }
}
