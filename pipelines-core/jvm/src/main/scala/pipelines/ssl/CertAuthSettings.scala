package pipelines.ssl

import java.nio.file.Path

case class CertAuthSettings(caDefaultPassword: String, caDetails: Option[Path], caFile: Option[Path], caPasswordFile: Option[Path], privateKeyFile: Option[Path]) {
  def asPropertyMap(): Map[String, String] = {
    def empty = Map.empty[String, String]

    val caDetailsMap = caDetails.fold(empty) { path =>
      Map("CA_DETAILS_FILE" -> path.toAbsolutePath.toString)
    }

    val caPasswordFileMap = caPasswordFile.fold(empty) { path =>
      Map("CA_PWFILE" -> path.toAbsolutePath.toString)
    }

    val caFileMap = caFile.fold(empty) { path =>
      Map("CA_FILE" -> path.toAbsolutePath.toString)
    }

    val privateKeyFileMap = privateKeyFile.fold(empty) { path =>
      Map("CA_PRIVATE_KEY_FILE" -> path.toAbsolutePath.toString)
    }

    caDetailsMap ++ caPasswordFileMap ++ caFileMap ++ privateKeyFileMap ++ Map("CA_DEFAULT_PWD" -> caDefaultPassword)
  }
}

object CertAuthSettings {
  def apply(pwd: String = "password"): CertAuthSettings = {
    new CertAuthSettings(pwd, None, None, None, None)
  }

  /**
    * If we just run the generator scripts once, we'll end up with a <dir>/ca/... file where the 'ca' directory contains
    *
    * @param caDir the presumably pre-generated directory containing the CA files such as:
    * {{{
    * <hostname>-ca.crt
    * ca-options.conf
    * capass.txt
    * secret.key
    * secret.pub
    * }}}
    * @return the CertAuthSettings from the files in the 'ca' directory
    */
  def fromGeneratedCaDir(caDir: Path): CertAuthSettings = {
    import eie.io._
    new CertAuthSettings(
      "password",
      caDetails = caDir.find(_.fileName == "ca-options.conf").toIterable.headOption,
      caFile = caDir.find(_.fileName.endsWith("-ca.crt")).toIterable.headOption,
      caPasswordFile = caDir.find(_.fileName == "capass.txt").toIterable.headOption,
      privateKeyFile = caDir.find(_.fileName == "secret.key").toIterable.headOption
    )
  }
}
