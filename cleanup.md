
class StreamRoutesTest extends BaseRoutesTest {

  "StreamRoutes.websocketPublishRoute" should {
    "create a source which can be subscribed to multiple times" in withScheduler { implicit sched =>
      val streamRoutes = StreamRoutes.dev(100.millis)

      val wsClient = WSProbe()

      val expectedData = (0 to 10).map(i => s"data $i")

      val websocketRoute = streamRoutes.websocketPublishRoute

      WS("/source/create/publish", wsClient.flow) ~> websocketRoute ~> check {
        // check response for WS Upgrade headers
        isWebSocketUpgrade shouldEqual true

        expectedData.foreach(wsClient.sendMessage)

        val expectedHeartbeat = wsClient.expectMessage()
        expectedHeartbeat.asTextMessage.getStrictText shouldBe pipelines.rest.socket.socket.heartBeatTextMsg

        wsClient.sendCompletion()
        wsClient.expectCompletion()
      }

    }
  }
  
------------------------------------------------------------------------------------------------------------------------


  

class KafkaRoutesIntegrationTest extends BaseDockerSpec("scripts/kafka") with Matchers with ScalatestRouteTest {

  "GET /kafka/pull" should {
    "open a web socket to pull all data from a query" in {
      val wsClient = WSProbe()

      import args4c.implicits._

      val rootConfig = Array("dev.conf").asConfig()

      val expectedData = (0 to 10).map(i => s"data $i")

      val topic = s"testTopic${UUID.randomUUID}"

      val schedulerService = Scheduler.io("PipelinesRoutesIntegrationTest")
      Using(RichKafkaProducer.strings(rootConfig)(schedulerService)) { publisher: RichKafkaProducer[String, String] =>
        try {
          expectedData.foreach { msg =>
            publisher.send(topic, msg, msg)
          }
          publisher.flush()

          val clientRequest = UpdateFeedRequest(
            new QueryRequest(
              clientId = "clientId" + UUID.randomUUID,
              groupId = "groupId" + UUID.randomUUID,
              topic = topic,
              filterExpression = "",
              filterExpressionIncludeMatches = true,
              fromOffset = Option("earliest"),
              messageLimit = None, //Option(Rate.perSecond(expectedData.size * 2)),
              format = None,
              streamStrategy = StreamStrategy.All
            ))

          Using(KafkaRoutes(rootConfig)(materializer, schedulerService)) { kafkaRoutes =>
            val websocketRoute: Route = kafkaRoutes.routes

            val req: HttpRequest = WS("/stream/pull", wsClient.flow)

            req ~> websocketRoute ~> check {
              // check response for WS Upgrade headers
              isWebSocketUpgrade shouldEqual true

              // manually run a WS conversation
              import io.circe.syntax._
              val fromClient = clientRequest.asJson

              wsClient.sendMessage(fromClient.noSpaces)
              val received = (0 until expectedData.size).map { _ =>
                wsClient.expectMessage()
              }
              received.size shouldBe expectedData.size

              val textReceived = received.map(_.asTextMessage.getStrictText)
              val messages = textReceived.map { json =>
                decode[RecordJson](json).right.get
              }
              messages.size shouldBe expectedData.size
              messages.foreach { msg =>
                msg.topic shouldBe topic
              }
              messages.map(_.key) should contain theSameElementsAs (expectedData)
              messages.map(_.offset) should contain theSameElementsAs (expectedData.indices)

              wsClient.sendCompletion()
              //          wsClient.expectCompletion()
            }
          }
        } finally {
          schedulerService.shutdown()
        }
      }
    }
  }
}

------------------------------------------------------------------------------------------------------------------------

sealed trait ModifyObservableRequest
object ModifyObservableRequest {
  case class AddStatistics(verbose: Boolean)                    extends ModifyObservableRequest
  case class Filter(expression: String)                         extends ModifyObservableRequest
  case class Persist(path: String)                              extends ModifyObservableRequest
  case class RateLimit(newRate: Rate, strategy: StreamStrategy) extends ModifyObservableRequest
  case class Take(limit: Int)                                   extends ModifyObservableRequest
  case class Generic(enrichmentKey: String, config: Json)       extends ModifyObservableRequest

  implicit val encodeEvent: Encoder[ModifyObservableRequest] = Encoder.instance {
    case enrichment @ AddStatistics(_) => enrichment.asJson
    case enrichment @ Persist(_)       => enrichment.asJson
    case enrichment @ RateLimit(_, _)  => enrichment.asJson
    case enrichment @ Filter(_)        => enrichment.asJson
    case enrichment @ Take(_)          => enrichment.asJson
    case enrichment @ Generic(_, _)    => enrichment.asJson

  }

  implicit val decodeEvent: Decoder[ModifyObservableRequest] =
    List[Decoder[ModifyObservableRequest]](
      Decoder[AddStatistics].widen,
      Decoder[Persist].widen,
      Decoder[Generic].widen,
      Decoder[RateLimit].widen,
      Decoder[Take].widen,
      Decoder[Filter].widen
    ).reduceLeft(_ or _)
}

------------------------------------------------------------------------------------------------------------------------

class FiltersTest extends BaseCoreTest {

  "be able to update a filtered source" in withScheduler { implicit sched =>
    Given("Some original json DataSource")
    val jsonSource = Data.push[Json]

    And("A data registry with a filtered source")
    val registry = DataRegistry(sched)
    val sink     = DataSink.collect[Json]()
    registry.sinks.register("sink", sink)

    registry.sources.register("json", jsonSource) shouldBe true
    registry.sources.register("bytes", DataSource.push[Array[Byte]]) shouldBe true

    registry.update(ModifySourceRequest("json", "json.filtered", ModifyObservableRequest.Filter(""" value.id.asInt % 31 == 0 || value.name == "name-2" """))) shouldBe SourceCreatedResponse(
      "json.filtered",
      asId[Json])
    registry.connect("json.filtered", "sink") shouldBe ConnectResponse("json.filtered", "sink")

    When("We push some data through")
    jsonSource.push(asJson(1))
    jsonSource.push(asJson(2))
    jsonSource.push(asJson(31))
    jsonSource.push(asJson(32))

    Then("The sink should receive the filtered values")
    eventually {
      sink.toList().size shouldBe 2
    }
    sink.toList().map(idForJson) shouldBe List(2, 31)

    When("We try and update a non-filter source")
    val SourceErrorResponse("bytes", errorMsg) = {
      registry.update(UpdateSourceRequest("bytes", ModifyObservableRequest.Filter(""" value.id.asInt % 31 == 0 || value.name == "name-2" """)))
    }

    Then("it should return an error")
    errorMsg shouldBe "Source 'bytes' for types ByteArray is not a filtered source, it is: PushSource"

    When("We update the filtered source w/ a new filter")
    val SourceUpdatedResponse("json.filtered", okMsg) = registry.update(UpdateSourceRequest("json.filtered", ModifyObservableRequest.Filter("""value.id > 100""")))

    Then("it should update the expression")
    okMsg shouldBe "Filter expression updated to: value.id > 100"

    When("the source then pushes data which matches both the original and new filter")
    sink.clear()
    jsonSource.push(asJson(62))
    jsonSource.push(asJson(93))
    jsonSource.push(asJson(100))
    jsonSource.push(asJson(101))
    jsonSource.push(asJson(102))

    Then("We should only see values which match the filter")
    eventually {
      sink.toList().size shouldBe 2
    }
    sink.toList().map(idForJson) shouldBe List(101, 102)
  }
  "be able to filter a source" in withScheduler { implicit sched =>
    Given("Some original json DataSource")
    val jsonSource = Observable.fromIterable(0 to 100).map { id =>
      asJson(id)
    }

    And("A data registry")
    val registry = DataRegistry(sched)
    registry.sources.register("json", DataSource(jsonSource)) shouldBe true
    registry.filterSources("json", "json.filtered", """ value.id.asInt % 31 == 0 || value.name == "name-2" """) shouldBe SourceCreatedResponse("json.filtered", asId[Json])

    When("We collect on a filtered source")
    val sink = DataSink.collect[Json]()
    registry.sinks.register("sink", sink)
    registry.connect("json.filtered", "sink")

    Then("We should only see values which match the filter")
    eventually {
      sink.toList().size shouldBe 5
    }
    sink.toList().map(idForJson) shouldBe List(0, 2, 31, 62, 93)
  }
}

------------------------------------------------------------------------------------------------------------------------

  "Filter" should {
    "apply filters to a stream as they are updated" in {
      WithScheduler { implicit s =>
        Given("A filtered data source which we can send values through")
        val (pushNext, ints) = Pipe.publishToOne[Int].unicast
        val filter           = ModifyObservable.Filter[Int]()

        val list = ListBuffer[Int]()
        filter.modify(ints).foreach { next =>
          list += next
        }

        When("we send some first values")
        pushNext.onNext(1)
        pushNext.onNext(2)
        pushNext.onNext(3)

        Then("we should be able to observe those values")
        eventually {
          list should contain only (1, 2, 3)
        }

        When("We update the filter and push some more values")
        filter.filterVar := (_ % 3 == 0)
        Observer.feed(pushNext, (4 to 13))

        Then("We should only see values which match the new filter")
        eventually {
          list should contain only (1, 2, 3, 6, 9, 12)
        }

        list.toList shouldBe List(1, 2, 3, 6, 9, 12)
      }
    }
  }

------------------------------------------------------------------------------------------------------------------------

class DataRegistryRequestTest extends BaseCoreTest {

  "DataRegistryRequest" should {

    import ModifyObservableRequest._
    val all = List[DataRegistryRequest](
      Connect("sourceKey", "sinkKey"),
      ModifySourceRequest("sourceKey", "newKey", RateLimit(Rate.perSecond(12), StreamStrategy.All)),
      ModifySourceRequest("sourceKey", "newKey", Filter("expression")),
      ModifySourceRequest("sourceKey", "newKey", AddStatistics(true)),
      ModifySourceRequest("sourceKey", "newKey", Persist("/data")),
      UpdateSourceRequest("sourceKey", Take(2)),
      UpdateSourceRequest("sourceKey", Generic("foo", Json.fromString("hi")))
      //      ModifySourceRequest("sourceKey", "newKey", ModifyObservableRequest.MapType((_:Int).toString),
    )

    all.foreach { expected =>
      s"serialize $expected to/from json" in {
        import io.circe.parser._
        import io.circe.syntax._
        decode[DataRegistryRequest](expected.asJson.noSpaces) shouldBe Right(expected)
      }
    }

  }
}

------------------------------------------------------------------------------------------------------------------------

    "be able to persist a source via the filesystem to produce a new source" in withScheduler { implicit sched =>
      Given("Some original json DataSource")
      val pushSource = DataSource.push[Int]
      val registry   = DataRegistry(sched)
      val sink       = DataSink.collect[Int]()
      registry.sources.register("source", pushSource)
      registry.sinks.register("sink", sink)

      WithTempDir { persistLocation =>

        registry.enrichSource("source", "source.persistent", ModifyObservable.Persist(persistLocation)) shouldBe SourceCreatedResponse("source.persistent", "byte[]")

        // from an int to bytes, then back to an Int
//        registry.as[Int]("source.persistent", "source.ints") shouldBe SourceCreatedResponse("source.ints", "int")

        registry.connect("source.ints", "sink") shouldBe ConnectResponse("source.ints", "sink")

        val expected = (0 to 10).map(pushSource.push).size

        eventually {
          import eie.io._
          persistLocation.children.size shouldBe expected
        }

      }
    }
    "be able to rate limit a source" in withScheduler { implicit sched =>
      // 100 messages/second
      val ints     = DataSource(Observable.interval(10.millis))
      val registry = DataRegistry(sched)
      registry.sources.register("ints", ints) shouldBe true

      val sink = DataSink.collect[Long]()
      registry.sinks.register("sink", sink) shouldBe true

      When("a rate limit is applied and connected")
      registry.rateLimitSources("ints", "ints.slow", Rate(1, 100.millis), StreamStrategy.Latest)
      registry.connect("ints.slow", "sink") shouldBe ConnectResponse("ints.slow", "sink")

      Then("The sink should only see a limited set")
      eventually {
        sink.toList().size should be >= 10
      }
      val received: List[Long] = sink.toList()

      val skipped = received.sliding(2, 1).exists {
        case List(a, b) => b > a + 1
        case _          => false
      }

      withClue(s"Rate limit doesn't seem to have been applied to ${received}") {
        skipped shouldBe true
      }
    }

------------------------------------------------------------------------------------------------------------------------


------------------------------------------------------------------------------------------------------------------------

