package pipelines.reactive

import java.nio.file.Path

import com.typesafe.config.{Config, ConfigFactory}
import pipelines.reactive.Transform.{FixedTransform, forTypes, map}

import scala.util.Try

object JVMTransforms {

  def parseStringAsConfig: FixedTransform[String, Try[Config]] = map { str =>
    Try(ConfigFactory.parseString(str))
  }

  def writeToZipped(baseDir: Path): FixedTransform[Array[Byte], (Array[Byte], String)] = {
    val newType = ContentType.of[(Array[Byte], String)]
    forTypes(ContentType.of[Array[Byte]], newType) {
      case (original, obs) =>
        val dataSource = original.ensuringId(Ids.next())
        import eie.io._
        val dir = baseDir.resolve(dataSource.id.get).mkDirs()
        val newObs = obs.zipWithIndex.map {
          case (bytes, index) =>
            val fileName = s"$index.dat"
            dir.resolve(fileName).bytes = bytes
            (bytes, fileName)
        }
        DataSource.of(newType, newObs, dataSource.prefixedMetadata("persisted"))
    }
  }

  def writeTo(dir: Path): FixedTransform[(Array[Byte], String), (Array[Byte], String)] = {
    map[(Array[Byte], String), (Array[Byte], String)] {
      case entry @ (bytes, fileName) =>
        import eie.io._
        dir.resolve(fileName).bytes = bytes
        entry
    }
  }
}
