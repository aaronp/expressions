package pipelines.mongo.users

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.execution.{CancelableFuture, Scheduler}
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import pipelines.mongo.{CollectionSettings, LowPriorityMongoImplicits}
import pipelines.users.CreateUserRequest
import pipelines.users.jvm.UserHash

/** exposes the means for creating new users
  *
  * @param mongoDb
  * @param users
  * @param hasher
  * @param ioSched
  */
final class UserServiceMongo(private[users] val mongoDb: MongoDatabase, private[users] val users: MongoCollection[Document], hasher: UserHash)(implicit ioSched: Scheduler)
    extends LowPriorityMongoImplicits {

  /**
    * Create a new user. If a user already exists w/ the same email then this should fail.
    *
    * Although we hash the password here when saving, the password  _SHOULD_ have already be hashed on the client side as well
    *
    * @param request
    * @return a future for the request which will simply succeed or fail
    */
  def createUser(request: CreateUserRequest): CancelableFuture[Unit] = {
    val reHashed    = hasher(request.hashedPassword)
    val safeRequest = request.copy(hashedPassword = reHashed)

    // we _should_ have indices which will catch duplicate usernames/emails on create
    users.insertOne(safeRequest.asBsonDoc).monix.completedL.runToFuture
  }
}

object UserServiceMongo extends LowPriorityMongoImplicits with StrictLogging {

  def apply(rootConfig: Config, usersCollectionName: String = "users")(implicit ioSched: Scheduler): CancelableFuture[UserServiceMongo] = {
    val hasher: UserHash = UserHash(rootConfig)
    apply(CollectionSettings(rootConfig, usersCollectionName), hasher)
  }

  def apply(config: CollectionSettings, hasher: UserHash)(implicit ioSched: Scheduler): CancelableFuture[UserServiceMongo] = {
    config.ensureCreated.map { users =>
      new UserServiceMongo(config.mongoDb, users, hasher)
    }
  }
}
