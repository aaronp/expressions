package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigFactory}
import expressions.DynamicJson
import expressions.client.{HttpRequest, HttpResponse, RestClient}
import expressions.franz.SupportedType.*
import expressions.franz.{FranzConfig, SupportedType}
import expressions.rest.server.kafka.BatchContext.HttpClient
import expressions.template.{Env, FileSystem}
import io.circe.syntax.*
import io.circe.{Encoder, Json}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import zio.blocking.Blocking
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde
import zio.{Ref, Task, ZIO, ZManaged}

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

  private implicit def dynamicJsonAsKey(jason: DynamicJson): Key = jsonAsKey(jason.value)
  private implicit def jsonAsKey(jason: Json): Key               = keyType.of(jason)

  private implicit def dynamicJsonAsValue(jason: DynamicJson): Value = jsonAsValue(jason.value)
  private implicit def jsonAsValue(jason: Json): Value               = valueType.of(jason)

  /**
    * RichKey and its associated implicit 'richKey(...)' conversions support creating a 'RecordBuilder' which knows how to
    * publish values to a topic:
    *
    * {{{
    *   "some key".withValue(json"""{ "foo" : "bar" }""").publishTo("topic-foo")
    * }}}
    *
    * Doing it this way simplifies trying to resolve both a generic key and value type to whatever [K,V] the publisher is
    * @param key the key type
    */
  class RichKey(key: Key) {
    def withValue(value: DynamicJson): RecordBuilder[Key, Value] = RecordBuilder[Key, Value](key, value, producer, blocking)
    def withValue(value: Json): RecordBuilder[Key, Value]        = RecordBuilder[Key, Value](key, value, producer, blocking)
    def withValue(value: String): RecordBuilder[Key, Value]      = RecordBuilder[Key, Value](key, value.asJson, producer, blocking)
    def withValue(value: Long): RecordBuilder[Key, Value]        = RecordBuilder[Key, Value](key, value.asJson, producer, blocking)
  }
  implicit def richKey(key: DynamicJson): RichKey = new RichKey(key)
  implicit def richKey(key: Json): RichKey        = new RichKey(key)
  implicit def richKey(key: String): RichKey      = new RichKey(key.asJson)
  implicit def richKey(key: Long): RichKey        = new RichKey(key.asJson)

  final def publish(record: ProducerRecord[Key, Value]): Task[RecordMetadata] = {
    producer.produce(record, keySerde, valueSerde) //.provide(blocking)
  }

  def keyType: SupportedType[Key]
  def valueType: SupportedType[Value]
  def producer: Producer

  final def send(request: HttpRequest): Task[HttpResponse] = restClient(request)

  final def post[A: Encoder](url: String, body: A, headers: Map[String, String] = Map.empty): Task[HttpResponse] = {
    send(HttpRequest.post(url, headers).withBody(body))
  }
}

object BatchContext {
  type ConfigPath     = String
  type HttpClient     = HttpRequest => Task[HttpResponse]
  type ProducerByName = ConfigPath => ZManaged[Any, Throwable, Producer]

  def apply(rootConfig: Config = ConfigFactory.load(), env: Env = Env()): ZManaged[Blocking, Throwable, BatchContext] = {
    val franzConfig            = FranzConfig.fromRootConfig(rootConfig)
    val fileSystem: FileSystem = FileSystem(KafkaRecordToHttpRequest.dataDir(rootConfig))
    val client: HttpClient     = (r: HttpRequest) => Task.fromFuture(_ => RestClient.send(r))
    franzConfig.producerKeyType match {
      case t @ RECORD(_)  => withKey[GenericRecord](franzConfig, t.asInstanceOf[SupportedType[GenericRecord]], fileSystem, env, client)
      case t @ BYTE_ARRAY => withKey[Array[Byte]](franzConfig, t.asInstanceOf[SupportedType[Array[Byte]]], fileSystem, env, client)
      case t @ STRING     => withKey[String](franzConfig, t.asInstanceOf[SupportedType[String]], fileSystem, env, client)
      case t @ LONG       => withKey[Long](franzConfig, t.asInstanceOf[SupportedType[Long]], fileSystem, env, client)
    }
  }

  private def withKey[K](franzConfig: FranzConfig, //
                         keyType: SupportedType[K], //
                         fs: FileSystem, //
                         env: Env, //
                         client: HttpClient //
  ): ZManaged[Blocking, Throwable, BatchContext] = {
    franzConfig.producerValueType match {
      case t @ RECORD(_)  => managed[K, GenericRecord](franzConfig, keyType, t.asInstanceOf[SupportedType[GenericRecord]], fs, env, client)
      case t @ BYTE_ARRAY => managed[K, Array[Byte]](franzConfig, keyType, t.asInstanceOf[SupportedType[Array[Byte]]], fs, env, client)
      case t @ STRING     => managed[K, String](franzConfig, keyType, t.asInstanceOf[SupportedType[String]], fs, env, client)
      case t @ LONG       => managed[K, Long](franzConfig, keyType, t.asInstanceOf[SupportedType[Long]], fs, env, client)
    }
  }

  private def managed[K, V](franzConfig: FranzConfig, //
                            keyTypeIn: SupportedType[K], //
                            valueTypeIn: SupportedType[V], //
                            fileSystem: FileSystem, //
                            envIn: Env, //
                            clientIn: HttpClient): ZManaged[Blocking, Throwable, BatchContext] = {
    for {
      b        <- ZIO.environment[Blocking].toManaged_
      cacheRef <- Ref.make(Map[String, Any]()).toManaged_
      keys     <- franzConfig.keySerde[K]()
      values   <- franzConfig.valueSerde[V]()
      c <- franzConfig.producer[K, V].map { producerIn =>
        new BatchContext {
          type Key   = K
          type Value = V
          override val keySerde                        = keys
          override val valueSerde                      = values
          override val config: FranzConfig             = franzConfig
          override val env: Env                        = envIn
          override val fs: FileSystem                  = fileSystem
          override val cache: Ref[Map[String, Any]]    = cacheRef
          override val restClient: HttpClient          = clientIn
          override val blocking: Blocking              = b
          override val keyType: SupportedType[Key]     = keyTypeIn
          override val valueType: SupportedType[Value] = valueTypeIn
          override val producer: Producer              = producerIn
        }
      }
    } yield c
  }
}
