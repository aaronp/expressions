package pipelines.rest.routes

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import monix.execution.Scheduler

import scala.concurrent.duration._

// TODO - make this better
class WebSocketTokenCache(validForDuration: FiniteDuration)(implicit scheduler: Scheduler) extends AutoCloseable {

  private val cancel = scheduler.scheduleAtFixedRate(validForDuration, validForDuration) {
    removeOlderThan(System.currentTimeMillis - validForDuration.toMillis)
  }

  private final case class Entry(jwt: String, addedEpoch: Long)
  private val jwtTokenByIdCache = new ConcurrentHashMap[String, Entry]()

  def generate(jwt: String): String = {
    val key = UUID.randomUUID().toString.filter(_.isLetterOrDigit)
    jwtTokenByIdCache.put(key, Entry(jwt, System.currentTimeMillis()))
    key
  }

  /** THIS IS A SIDE-EFFECTING CALL WHICH INVALIDATES THE TOKEN AFTER USE
    *
    * @param key the key for which to retrieve a JWT token
    * @return the JWT token for this key, if valid
    */
  def validateAndRemove(key: String) = {
    val entry = jwtTokenByIdCache.remove(key)
    Option(entry).map(_.jwt)
  }
  def get(key: String): Option[String] = {
    Option(jwtTokenByIdCache.get(key)).map(_.jwt)
  }

  def isValid(key: String): Boolean = jwtTokenByIdCache.containsKey(key)

  def removeOlderThan(timestamp: Long) = {
    import scala.collection.JavaConverters._
    val toRemove = jwtTokenByIdCache.asScala.filter(_._2.addedEpoch < timestamp).toList
    toRemove.foreach(e => jwtTokenByIdCache.remove(e._1))
  }

  override def close(): Unit = {
    cancel.cancel()
  }
}

object WebSocketTokenCache {
  def apply(validForDuration: FiniteDuration = 1.minute)(implicit scheduler: Scheduler): WebSocketTokenCache = {
    new WebSocketTokenCache(validForDuration)
  }
}
