package pipelines

import java.net.InetAddress

object Localhost {
  lazy val value = InetAddress.getLocalHost
  def apply()    = value

  def hostAddress(): String = value.getHostAddress

}
