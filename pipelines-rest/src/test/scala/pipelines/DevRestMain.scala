package pipelines

/**
  * This entry-point puts the test resources on the classpath, and so serves as a convenience for running up the Main entry-point for local development work.
  *
  */
object DevRestMain {

  def devArgs: Array[String] = {
    import eie.io._
    val certPath = ".target/certificates/cert.p12".asPath
    val p        = certPath.toAbsolutePath.toString
    Array(s"pipelines.tls.certificate=${p}", "dev.conf")
  }

}
