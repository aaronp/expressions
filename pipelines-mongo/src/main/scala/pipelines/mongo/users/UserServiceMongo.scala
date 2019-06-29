package pipelines.mongo.users

import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import monix.execution.{CancelableFuture, Scheduler}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{Document, MongoCollection, MongoDatabase}
import pipelines.mongo.{BsonUtil, CollectionSettings, LowPriorityMongoImplicits}
import pipelines.users.jvm.UserHash
import pipelines.users.{CreateUserRequest, RegisteredUser}

import scala.concurrent.Future

/** exposes the means for creating new users
  *
  * @param mongoDb
  * @param users
  * @param hasher
  * @param ioSched
  */
final class UserServiceMongo(private[users] val mongoDb: MongoDatabase, private[users] val users: MongoCollection[Document], val hasher: UserHash)(implicit val ioSched: Scheduler)
    extends LowPriorityMongoImplicits {

  /** finds a user w/ the given username or email
    *
    * @param usernameOrEmail
    * @return
    */
  def findUser(usernameOrEmail: String): Future[Option[RegisteredUser]] = {
    val criteria = {
      Filters.or(
        Filters.equal(CreateUserRequest.userNameField, usernameOrEmail),
        Filters.equal(CreateUserRequest.emailField, usernameOrEmail)
      )
    }

    users
      .find(criteria)
      .limit(2) // in the odd case where we somehow get multiple users for the same username or email, we should know about it (and fail)
      .map { doc =>
        val result                              = BsonUtil.fromBson(doc).flatMap(_.as[CreateUserRequest].toTry)
        val CreateUserRequest(name, email, pwd) = result.get
        RegisteredUser(BsonUtil.idForDocument(doc), name, email, pwd)
      }
      .toFuture
      .map {
        case Seq(unique) => Option(unique)
        case Seq()       => None
        case many        => throw new IllegalStateException(s"Multiple users found w/ username or email '${usernameOrEmail}'")
      }
  }

  /**
    * Create a new user. If a user already exists w/ the same email then this should fail.
    *
    * Although we hash the password here when saving, the password  _SHOULD_ have already be hashed on the client side as well
    *
    * @param request
    * @return a future for the request which will simply succeed or fail
    */
  def createUser(request: CreateUserRequest): CancelableFuture[Unit] = {
    if (request.isValid()) {
      val reHashed    = hasher(request.hashedPassword)
      val safeRequest = request.copy(hashedPassword = reHashed)

      // we _should_ have indices which will catch duplicate usernames/emails on create
      users.insertOne(safeRequest.asBsonDoc).monix.completedL.runToFuture
//         {
      //        case _ => UserAlreadyExists(request.userName)
      //      }
    } else {
      CancelableFuture.failed(new Exception(
        "Invalid create user request for some reason. Good luck with that! (hint: some text is probably empty or an invalid email address based on some sketchy regex logic the internet seemed to think was ok)"))
    }
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
