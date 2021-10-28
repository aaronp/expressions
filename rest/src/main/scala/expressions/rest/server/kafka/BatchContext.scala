package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigFactory}
import expressions.DynamicJson
import expressions.client.{HttpRequest, HttpResponse, RestClient}
import expressions.franz.SupportedType.*
import expressions.franz.{FranzConfig, SupportedType}
import expressions.rest.server.RestRoutes
import expressions.rest.server.db.{PostgresConf, RichConnect}
import expressions.rest.server.kafka.BatchContext.HttpClient
import expressions.template.{Env, FileSystem}
import io.circe.syntax.*
import io.circe.{Encoder, Json}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.slf4j.LoggerFactory
import scalikejdbc.{NoExtractor, SQL}
import zio.blocking.Blocking
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde
import zio.*

import java.nio.charset.StandardCharsets

/**
  * This context is meant to be provided to some kind of 'black box' which should be executed with each batch of
  * consumer records.
  *
  * It exposes all the useful "stuff" you might want access to when processing a batch of records:
  * $ an http client
  * $ a kafka publisher
  * $ a 'cache' -- a context which can be updated between batches
  * $ a handle on the file system
  * $ system environment vars, etc.
  *
  * It also exposes some convenient methods for using those interfaces (e.g. an implicit conversion for keys/values for publishing)
  */
trait BatchContext {
  type Key
  type Value

  def keySerde: Serde[Any, Key]

  def valueSerde: Serde[Any, Value]

  def config: FranzConfig

  def env: Env

  def fs: FileSystem

  def restClient: HttpClient

  def blocking: Blocking

  def cache: Ref[Map[String, Any]]

  def db: Option[RichConnect]

  private implicit def dynamicJsonAsKey(jason: DynamicJson): Key = jsonAsKey(jason.value)

  private implicit def jsonAsKey(jason: Json): Key = keyType.of(jason)

  private implicit def dynamicJsonAsValue(jason: DynamicJson): Value = jsonAsValue(jason.value)

  private implicit def jsonAsValue(jason: Json): Value = valueType.of(jason)

  extension (query : SQL[Nothing, NoExtractor])
    def run: Task[Int] = Task(runSync)
    def runSync: Int = db.fold(-1) { conn =>
      query.update.apply()(conn.session)
    }

  /**
    * RichKey and its associated implicit 'richKey(...)' conversions support creating a 'RecordBuilder' which knows how to
    * publish values to a topic:
    *
    * {{{
    *   "some key".withValue(json"""{ "foo" : "bar" }""").publishTo("topic-foo")
    * }}}
    *
    * Doing it this way simplifies trying to resolve both a generic key and value type to whatever [K,V] the publisher is
    *
    * @param key the key type
    */
  class RichKey(key: Key) {
    def withValue(value: DynamicJson): RecordBuilder[Key, Value] = RecordBuilder[Key, Value](key, value, producer, blocking, keySerde, valueSerde)

    def withValue(value: Json): RecordBuilder[Key, Value] = RecordBuilder[Key, Value](key, value, producer, blocking, keySerde, valueSerde)

    def withValue(value: String): RecordBuilder[Key, Value] = RecordBuilder[Key, Value](key, value.asJson, producer, blocking, keySerde, valueSerde)

    def withValue(value: Long): RecordBuilder[Key, Value] = RecordBuilder[Key, Value](key, value.asJson, producer, blocking, keySerde, valueSerde)
  }

  implicit def richKey(key: DynamicJson): RichKey = new RichKey(key)

  implicit def richKey(key: Json): RichKey = new RichKey(key)

  implicit def richKey(key: String): RichKey = new RichKey(key.asJson)

  implicit def richKey(key: Long): RichKey = new RichKey(key.asJson)

  final def publish(record: ProducerRecord[Key, Value]): Task[RecordMetadata] = {
    producer.produce(record, keySerde, valueSerde) //.provide(blocking)
  }

  def keyType: SupportedType[Key]

  def valueType: SupportedType[Value]

  def producer: Producer

  final def send(request: HttpRequest): Task[HttpResponse] = restClient(request)

  final def post[A: Encoder](url: String, body: A, headers: Map[String, String] = Map.empty) = {
    send(HttpRequest.post(url, headers).withBody(body)).either.timed.tap { r =>
      ZIO {
        val msg = s"post to $url returned $r"
        LoggerFactory.getLogger(classOf[BatchContext]).info(msg)
        println(msg)
      }
    }
  }
}

object BatchContext {
  type ConfigPath = String
  type HttpClient = HttpRequest => Task[HttpResponse]
  type ProducerByName = ConfigPath => ZManaged[Any, Throwable, Producer]

  def dataDir(rootConfig: Config) = {
    import eie.io.*
    rootConfig.getString("app.data").asPath
  }

  def apply(rootConfig: Config = ConfigFactory.load(), env: Env = Env()): ZManaged[Blocking, Throwable, BatchContext] = {
    val franzConfig = FranzConfig.fromRootConfig(rootConfig)

    import args4c.implicits.*

    val db = if (PostgresConf.isNonEmpty(rootConfig)) {
      Some(PostgresConf(rootConfig).connect)
    } else None


    val fileSystem: FileSystem = FileSystem(dataDir(rootConfig))
    val client: HttpClient = (r: HttpRequest) =>
      Task {
        LoggerFactory.getLogger(classOf[BatchContext]).info(s"Sending $r")
        val result = RestClient.sendSync(r)
        LoggerFactory.getLogger(classOf[BatchContext]).info(s"Reply from $r is $result")
        result
      }
    franzConfig.producerKeyType match {
      case t@RECORD(_) => withKey[GenericRecord](franzConfig, t.asInstanceOf[SupportedType[GenericRecord]], fileSystem, env, client, db)
      case t@BYTE_ARRAY => withKey[Array[Byte]](franzConfig, t.asInstanceOf[SupportedType[Array[Byte]]], fileSystem, env, client, db)
      case t@STRING => withKey[String](franzConfig, t.asInstanceOf[SupportedType[String]], fileSystem, env, client, db)
      case t@LONG => withKey[Long](franzConfig, t.asInstanceOf[SupportedType[Long]], fileSystem, env, client, db)
    }
  }

  private def withKey[K](franzConfig: FranzConfig, //
                         keyType: SupportedType[K], //
                         fs: FileSystem, //
                         env: Env, //
                         client: HttpClient, //
                         db: Option[RichConnect]
                        ): ZManaged[Blocking, Throwable, BatchContext] = {
    franzConfig.producerValueType match {
      case t@RECORD(_) => managed[K, GenericRecord](franzConfig, keyType, t.asInstanceOf[SupportedType[GenericRecord]], fs, env, client, db)
      case t@BYTE_ARRAY => managed[K, Array[Byte]](franzConfig, keyType, t.asInstanceOf[SupportedType[Array[Byte]]], fs, env, client, db)
      case t@STRING => managed[K, String](franzConfig, keyType, t.asInstanceOf[SupportedType[String]], fs, env, client, db)
      case t@LONG => managed[K, Long](franzConfig, keyType, t.asInstanceOf[SupportedType[Long]], fs, env, client, db)
    }
  }

  private def managed[K, V](franzConfig: FranzConfig, //
                            keyTypeIn: SupportedType[K], //
                            valueTypeIn: SupportedType[V], //
                            fileSystem: FileSystem, //
                            envIn: Env, //
                            clientIn: HttpClient, //
                            database: Option[RichConnect]
                           ): ZManaged[Blocking, Throwable, BatchContext] = {
    val setup = for {
      b <- ZIO.environment[Blocking]
      cacheRef <- Ref.make(Map[String, Any]())
      keys <- franzConfig.keySerde[K]()
      values <- franzConfig.valueSerde[V]()
    } yield (b, cacheRef, keys, values)

    val mngd = ZManaged.fromEffect(setup).flatMap {
      case (b, cacheRef, keys, values) =>
        franzConfig.producer.map { producerIn =>
          new BatchContext {
            type Key = K
            type Value = V
            override val keySerde = keys
            override val valueSerde = values
            override val config: FranzConfig = franzConfig
            override val env: Env = envIn
            override val fs: FileSystem = fileSystem
            override val cache: Ref[Map[String, Any]] = cacheRef
            override val restClient: HttpClient = clientIn
            override val blocking: Blocking = b
            override val keyType: SupportedType[Key] = keyTypeIn
            override val valueType: SupportedType[Value] = valueTypeIn
            override val producer: Producer = producerIn
            override val db = database
          }
        }
    }

    mngd.onExit { result =>
      UIO {
        LoggerFactory.getLogger(classOf[BatchContext]).info(s"BatchContext.onExit with $result")
      }
    }
  }
}
