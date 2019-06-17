package pipelines.mongo.users

import java.time.ZonedDateTime

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder}
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable
import monix.reactive.subjects.Var
import pipelines.audit.AuditVersion
import pipelines.mongo.audit.AuditServiceMongo
import pipelines.mongo.{CollectionSettings, LowPriorityMongoImplicits}
import pipelines.users.AuthModel

import scala.concurrent.duration.FiniteDuration

/**
  * Wraps an [[AuditServiceMongo]] to persist wrapped, versioned [[T]]s which also keeps a handle on the latest 'T'.
  *
  * This was written to keep track of our [[AuthModel]] and [[pipelines.users.UserRoles]] as the 'T'.
  *
  * This will need to just be a normal query
  *
  * @param repo a handle on where the [[T]] is stored
  */
final class RefDataMongo[T: Encoder: Decoder](val repo: AuditServiceMongo, pollFreq: FiniteDuration)(implicit ioSched: Scheduler)
    extends LowPriorityMongoImplicits
    with AutoCloseable {

  private val latestVar = Var[Option[(AuditVersion, T)]](None)

  /**
    * A means to access the most recent auth model saved in the DB as reference data
    *
    * @return the latest model and its update (version) settings -- useful if you need to update the AuthModel, but 'latestModel' is probably more useful if you just wanna read it.
    */
  def latest(): Option[(AuditVersion, T)]  = latestVar()
  def latestUpdate(): Option[AuditVersion] = latest().map(_._1)
  def latestModel(): Option[T]             = latest().map(_._2)

  /**
    * Save a new AuthModel at a 'currentVersion' (the pessimistic locking. Or optimistic? Whatever.)
    *
    * @param currentVersion the version read
    * @param userId
    * @param newModel
    * @param now
    * @return
    */
  def update(currentVersion: Int, userId: String, newModel: T, now: ZonedDateTime = ZonedDateTime.now()): CancelableFuture[Unit] = {
    import io.circe.syntax._
    repo.audit(currentVersion + 1, userId, newModel.asJson, now)
  }

  /**
    * updates the given model using the given function
    *
    * @param userId
    * @param now
    * @param f
    * @return
    */
  def updateWith(userId: String, now: ZonedDateTime = ZonedDateTime.now())(f: Option[T] => T): CancelableFuture[Unit] = {
    latest() match {
      case None                     => update(0, userId, f(None), now)
      case Some((prevVersion, old)) => update(prevVersion.revision, userId, f(Some(old)), now)
    }
  }

  // uses the 'poorMansTail' ATM ('cause tailing is only available on capped collections and even then when you have a replica set)
  // this ensures the latest auth is available across everybody reading from the DB
  private val tailTask: CancelableFuture[Unit] = {
    repo
      .poorMansTail(pollFreq)
      .flatMap { audit =>
        Observable.fromIterable(audit.record.as[T].toOption).map { x =>
          audit -> x
        }
      }
      .foreach { pear =>
        latestVar := Option(pear)
      }
  }

  override def close(): Unit = {
    tailTask.cancel()
  }
}

object RefDataMongo extends LowPriorityMongoImplicits with StrictLogging {
  def apply[T: Encoder: Decoder](rootConfig: Config, collectionName: String)(implicit ioSched: Scheduler): CancelableFuture[RefDataMongo[T]] = {
    val settings = CollectionSettings(rootConfig, collectionName)
    apply(settings)
  }

  def apply[T: Encoder: Decoder](settings: CollectionSettings)(implicit ioSched: Scheduler): CancelableFuture[RefDataMongo[T]] = {

    AuditServiceMongo(settings).map { service: AuditServiceMongo =>
      import args4c.implicits._
      val pollFreq = settings.mongo.databaseConfig(settings.collectionName).config.asFiniteDuration("pollFrequency")
      new RefDataMongo(service, pollFreq)
    }
  }
}
