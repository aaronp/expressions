package expressions.franz

import org.apache.avro.Schema

object Schemas {

  def exampleSchema: Schema = {
    val parser = new org.apache.avro.Schema.Parser
    parser.parse(exampleSchemaString)
  }

  def exampleSchemaString = """[
              |{
              |  "namespace": "example",
              |  "type": "record",
              |  "name": "Example",
              |  "fields": [
              |    {
              |      "name": "id",
              |      "type": "string",
              |      "default" : ""
              |    },
              |    {
              |      "name": "addresses",
              |      "type": {
              |          "type": "array",
              |          "items":{
              |              "name":"Address",
              |              "type":"record",
              |              "fields":[
              |                  { "name":"name", "type":"string" },
              |                  { "name":"lines", "type": { "type": "array", "items" : "string"} }
              |              ]
              |          }
              |      }
              |    },
              |    {
              |      "name": "someText",
              |      "type": "string",
              |      "default" : ""
              |    },
              |    {
              |      "name": "someLong",
              |      "type": "long",
              |      "default" : 0
              |    },
              |    {
              |      "name": "someDouble",
              |      "type": "double",
              |      "default" : 1.23
              |    },
              |    {
              |      "name": "someInt",
              |      "type": "int",
              |      "default" : 123
              |    },
              |    {
              |      "name": "someFloat",
              |      "type": "float",
              |      "default" : 1.0
              |    },
              |    {
              |      "name": "day",
              |      "type": {
              |        "name": "daysOfTheWeek",
              |        "type": "enum",
              |        "symbols": [ "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY" ]
              |      },
              |      "default" : "MONDAY"
              |    }
              |  ]
              |}
              |
              |]""".stripMargin

}
