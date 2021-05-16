# Expressions

This project is the exploration of a few ideas around immediate feedback/rapid prototyping in streaming systems.

The following ideas are combined to hopefully provide both a quick and powerful ETL system for streaming:
1) dynamic typing of data structures (e.g. `record.foo.bar.flatMap(_.fizz.buzz).asString`) for avro and json.
2) quickly/easily change configuration and start/stop consumers (e.g. tweak batch windows or kafka settings)
3) taking "configuration as code" to the next level - make the "black box" thunk a simple drop-in script
4) json as avro: an easy way to work with avro as if it were just json (e.g. auto generate schemas, seamless conversion to consistently work with records)
5) surface/expose individual components in your ETL pipeline to make them visible (and easily testable!)
6) Handle the usual suspects (metrics, batching, concurrency, context-propagation) out-of-the-box
7) Exposing error-handling/retries in a powerful, transparent and un-opinionated way
8) Decouple your solution from this tool

Putting these ideas together results in a robust prototype which covers a way to transform and pipe data streams, currently
supporting REST endpoints or other Kafka topics.

## Dynamic Typing

It's great to take advantage of the best/fastest/most expressive type system on the JVM (scala) where it makes sense.

It's also helpful (more readable, less cumbersome) to navigate and work with record structures w/o having to define interfaces or classes.

This project allows users to just invent/drop-in json (or avro) data structures like this:
```scala
{
  "some" : {
     "example" : [
       { "goes" : "here" },
       { "like" : "this" }
     ]
  }
}
```
and then immediately work with it in your (otherwise) strongly/safely typed pipeline:

```scala
val value1 = record.some.example[0].goes.asString // <-- returns 'here'
val value2 = record.some.example[1].like.asString // <-- returns 'this'
val Success(values) = record.some.example.as[Seq[Json]] // <-- returns the json array of 'example' as a Try
```

## Quickly Edit Config

This repo produces a small docker image which exposes REST endpoints and an intuitive (subjective) UI 
with a fast start-up, allowing you to easily start/stop isolated, resource-safe ETL processes with runs
both custom `insert-here` transformation scripts and whatever kafka configuration you'd like to tweak

## Configuration as Code on apple-juice
(Steriods aren't good for you and have bad side-effects. We don't like unintended side-effects in functional programming).

Almost everyone (> 93% of back-end developers -- citation needed) tend to do this:

 * invent some configuration settings (yaml, environment variables, hocon, whatever)
 * write some code which reads that config and applies it to their code

The overwhelming majority of code -- even beautiful, powerful, lovingly crafted code in a beautiful JVM language like Scala -- is just
ceremony around wiring together different libraries, lifecycle management, configuration handling, etc.

When teams/developers have to contribute to your project, love it or hate it, they have to read/deal with that code, even
though the business logic/value is in a very small piece. Even in that "your business logic lives here", it's still often intermingled with
how you defined your interfaces or handle delivering the config to it.

This project makes an attempt to just allow users to write that "insert your business logic" code bit in a way which is immediately testable and deployable.

## Avro/Json/Protobuf support out-of-the box.

Whether the data keys or values are strings, longs, json, avro, whatever, you can treat it in the same way.

If your key is just a long, then you would just:
```scala
record.key.asLong
```

If it were json, avro, or protobuf, you would still handle it in the same way, perhaps like this:
```scala
val collection = record.key.path.to.some.value.asString
val index = record.key.this.is.where.my.index.is.defined.asString
```
## Expose individual components
The REST interface and UI allow architects, BAs, developers, testers, etc to play around and test the transformations/business
logic in configurations that will be used in those environments. 

Write/tweak some scripts and run them on some test inputs. Play around with some aggregations or error handling and execute them in-situ 
before spinning them up in-place against some topics.

Even test out the REST endpoints (e.g. will the POST requests created from your inputs work?) via the app proxy.

## Out-of-the-box goodies

You'll get prometheus metrics, a UI, etc out-of-the box ... in a way which is both customisable (just publish your own guages/counters/metrics)
and also consistent (you may have N different ETL pipelines, all which can use the same metrics for consistent dashboards)

## Transparent Error handling

What happens when your targeted REST endpoint returns a 404? 500 response? or 200 response with some kind of error payload?
Or there's an exception due to your business logic? Or some peculiar data?

Don't try and anticipate these or come up with some custom configuration or interface. Just use the power of your black-box script!

Luckily there are [powerful libraries which very expressive error handling semantics](https://zio.dev/docs/overview/overview_handling_errors) that let you wear your big-boy pants.

You can encode your logic in a testable and customizable way without having to use nausia-inducing exception control flow.

## Decouple your solution
A common concern is not to lock yourself into a particular framework or technology. I get that. 

This tool offers a lot of ... stuff which you ultimately may not need (or want) in production.

You should be able to use this just for rapid prototyping or testing, then extract the results of that learning into a custom
solution.

One aspiration is to be able to take a working ETL developed here and generate either new trimmed-down project (think maven archetype, giter8 template, or just zip file) or a custom binary.

You should have the option to either run this tool in production (but perhaps w/ disabled features) or have the choice of taking the results of your experiments and bootstrapping
a new project which has your e.g. AVRO schemas/test records, opinionated/hard-coded business logic, etc. 

# Development

The expressions themselves are meant to be shippable as a library, though in practice they work well as part
of a larger ecosystems, such as Kafka sink transformations.

The rest app assumes a locally running Kafka instance, but just running `make` from the root will do the trick:

```dart
make
docker run -p 8080:8080 porpoiseltd/expressions:latest
```

Then just fire up `DevMain` and head on over to `http://localhost:8080/index.html`

