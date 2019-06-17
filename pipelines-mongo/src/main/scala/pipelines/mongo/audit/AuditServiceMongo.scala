package pipelines.mongo.audit

import java.time.ZonedDateTime

import cats.kernel.Eq
import com.mongodb.CursorType
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Projections, Sorts}
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import pipelines.audit.AuditVersion
import pipelines.mongo.audit.AuditServiceMongo.RevisionProjection
import pipelines.mongo.{BsonUtil, CollectionSettings, LowPriorityMongoImplicits}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
  * The point of an audit service is to be able to persist versioned [[AuditVersion]] records
  *
  * e.g. if we have a collection 'foo', then 'foo.audit' (or foo.versions, whatever).
  *
  * would get appended to w/ a new version (and timestamp, user) each time foo is updated.
  *
  * @param mongoDb
  * @param collection
  * @param ioSched
  */
final class AuditServiceMongo(private[audit] val mongoDb: MongoDatabase, val collection: MongoCollection[Document])(implicit ioSched: Scheduler)
    extends LowPriorityMongoImplicits
    with StrictLogging {

  def audit(revision: Int, userId: String, record: Json, createdAt: ZonedDateTime = ZonedDateTime.now()): CancelableFuture[Unit] = {
    audit(AuditVersion(revision, createdAt, userId, record))
  }

  /** Upon updating we should create a new audit version
    *
    * @return a future of the result
    */
  def audit(record: AuditVersion): CancelableFuture[Unit] = {
    val bson = record.asBsonDoc
    collection.insertOne(bson).monix.completedL.runToFuture
  }

  def forVersion(revision: Int): Future[Option[AuditVersion]] = {
    val filter = Filters.equal("revision", revision)
    val first  = collection.find(filter).first().headOption()
    first.map(_.flatMap(asAuditVersion))
  }

  def latest(): Future[Option[AuditVersion]] = {
    val sort  = Sorts.descending("revision")
    val filt  = Filters.exists("revision")
    val first = collection.find().sort(sort).first().headOption()
    first.map(_.flatMap(asAuditVersion))
  }

  /** @return an observable of records
    */
  def tail(): Observable[Document] = {
    collection.find().cursorType(CursorType.TailableAwait).noCursorTimeout(true).monix
  }

  /**
    * TODO - replace this w/ a proper tail
    *
    * You can't tail a non-capped collection, and when testing you can't (it seems) tail a non-replicated collection,
    * so this does the job
    *
    * @param pollFreq how often to check for an update
    * @return an infinite stream of AuditVersion records
    */
  def poorMansTail(pollFreq: FiniteDuration): Observable[AuditVersion] = {
    versions(pollFreq)
      .flatMap { latestVersion =>
        Observable.fromFuture(forVersion(latestVersion)).flatMap { opt =>
          Observable.fromIterable(opt)
        }
      }
  }

  def versions(pollFreq: FiniteDuration): Observable[Int] = {
    implicit val eq = Eq.fromUniversalEquals[Int]
    Observable
      .interval(pollFreq)
      .flatMap { _ =>
        Observable.fromFuture(maxVersion()).flatMap(x => Observable.fromIterable(x))
      }
      .distinctUntilChanged
  }

  def maxVersion(): Future[Option[Int]] = {
    def asRevision(doc: Document): Option[Int] = {
      BsonUtil.fromBson(doc).toOption.flatMap { json =>
        json.as[RevisionProjection].toOption.map(_.revision)
      }
    }
    val proj  = Projections.include("revision")
    val sort  = Sorts.descending("revision")
    val filt  = Filters.exists("revision")
    val first = collection.find().projection(proj).sort(sort).first().headOption()
    first.map(_.flatMap(asRevision))
  }

  /** @param revision
    * @param before
    * @param after
    * @param changedByUserId
    * @return the versions which fit the criteria
    */
  def find(revision: Option[Int] = None,
           after: Option[ZonedDateTime] = None,
           before: Option[ZonedDateTime] = None,
           changedByUserId: Option[String] = None): Observable[AuditVersion] = {
    val revisionAndRangeAndUserCriteria = AuditServiceMongo.asFindCriteria(revision, after, before, changedByUserId)
    logger.info(s"finding ${revisionAndRangeAndUserCriteria}")
    val results = revisionAndRangeAndUserCriteria match {
      case None           => collection.find()
      case Some(criteria) => collection.find(criteria)
    }
    results.monix.map { doc: Document =>
      BsonUtil.fromBson(doc.toJson) match {
        case Success(value) => value.as[AuditVersion].toTry.get
        case Failure(err) =>
          val msg = s"Couldn't parse as AuditVersion: ${doc.toJson} : ${err.getMessage}"
          throw new Exception(msg, err)
      }
    }
  }

  private def asAuditVersion(doc: Document): Option[AuditVersion] = {
    BsonUtil.fromBson(doc).toOption.flatMap { json =>
      json.as[AuditVersion].toOption
    }
  }
}

object AuditServiceMongo extends LowPriorityMongoImplicits with StrictLogging {
  case class RevisionProjection(revision: Int)
  object RevisionProjection {
    implicit val encoder: io.circe.ObjectEncoder[RevisionProjection] = io.circe.generic.semiauto.deriveEncoder[RevisionProjection]
    implicit val decoder: io.circe.Decoder[RevisionProjection]       = io.circe.generic.semiauto.deriveDecoder[RevisionProjection]

  }

  def asFindCriteria(revision: Option[Int] = None,
                     after: Option[ZonedDateTime] = None,
                     before: Option[ZonedDateTime] = None,
                     changedByUserId: Option[String] = None): Option[Bson] = {
    import io.circe.literal._

    val beforeCriteria = before.map { time =>
      Filters.lte("createdAt", time.toInstant.toEpochMilli.asBson)
    }
    val afterCriteria = after.map { time =>
      Filters.gte("createdAt", time.toInstant.toEpochMilli.asBson)
    }

    val userCriteria = changedByUserId.map { userId =>
      Filters.equal("userId", userId)
    }

    val revisionCriteria = revision.map { v =>
      Filters.equal("revision", v)
    }

    List(beforeCriteria, afterCriteria, userCriteria, revisionCriteria).flatten match {
      case Nil  => None
      case list => Option(Filters.and(list: _*))
    }
  }

  def apply(rootConfig: Config, collectionName: String)(implicit ioSched: Scheduler): CancelableFuture[AuditServiceMongo] = {
    apply(CollectionSettings(rootConfig, collectionName))
  }

  def apply(config: CollectionSettings)(implicit ioSched: Scheduler): CancelableFuture[AuditServiceMongo] = {
    config.ensureCreated.map { auth =>
      new AuditServiceMongo(config.mongoDb, auth)
    }
  }
}
