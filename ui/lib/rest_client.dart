import 'dart:convert';

import 'package:http/http.dart' as http;

import 'model.dart';

class RestClient {
  static JsonDecoder decoder = JsonDecoder();
  static JsonEncoder encoder = JsonEncoder.withIndent('  ');

  static const HostPort = 'http://localhost:8080';

  static Future<List<String>> listTopics(bool internal) async {
    http.Response response =
        await http.get('$HostPort/rest/kafka/topics?internal=$internal');
    final jsonResponse = verifyResponse(response);
    Map<String, dynamic> topicList = jsonResponse;
    final list = topicList.keys.toList();
    list.sort();
    return list;
  }

  static dynamic verifyResponse(http.Response response) {
    if (response.statusCode != 200) {
      var msg = response?.body ?? 'Error';
      try {
        final errorJson = decoder.convert(response.body);
        print('Error: $errorJson');
        msg = errorJson['error'];
        if (msg == null || msg == "null") {
          msg = 'Server returned an error';
        }
      } catch (e) {
        // ignore
      }
      assert(response.statusCode == 200, msg);
    }
    final jsonBody = decoder.convert(response.body);
    print('GOT: $jsonBody');
    return jsonBody;
  }

  static Future<List<String>> deleteTopic(String topic) async {
    http.Response response =
        await http.delete('$HostPort/rest/kafka/topic/$topic');
    List<dynamic> list = verifyResponse(response);
    final reply = list.map((e) => e.toString()).toList();
    reply.sort();
    return reply;
  }
  static Future<CreateTopic> createTopic(CreateTopic request) async {
    final jsonBody = encoder.convert(request.asJson);
    final url = '$HostPort/rest/kafka/topic';
    http.Response response = await http.post(url, body: jsonBody);
    final jsonResponse = verifyResponse(response);
    return CreateTopic.fromJson(jsonResponse);
  }

  static Future<List<Record>> peek(PeekRequest request) async {
    final url = '$HostPort/rest/kafka/consumer/peek';
    final jsonBody = encoder.convert(request.asJson);
    print('curl -XPOST -d ${jsonBody} $url');
    http.Response response = await http.post(url, body: jsonBody);
    final jsonResponse = verifyResponse(response);
    List<dynamic> list = jsonResponse['records'];
    final records = list.map((e) => Record.fromJson(e)).toList();
    return records;
  }

  static Future<RecordMetadataResponse> push(PublishOne request) async {
    final url = '$HostPort/rest/kafka/publish';
    final jsonBody = encoder.convert(request.asJson);
    print('curl -XPOST -d ${jsonBody} $url');
    http.Response response = await http.post(url, body: jsonBody);
    final jsonResponse = verifyResponse(response);
    return RecordMetadataResponse.fromJson(jsonResponse);
  }

  static Future<TopicDesc> partitionsForTopic(String topic) async {
    final url = '$HostPort/rest/kafka/partitions/$topic';
    http.Response response = await http.get(url);
    final jsonResponse = verifyResponse(response);
    return TopicDesc.fromJson(jsonResponse[topic]);
  }

  static Future<List<OffsetRange>> offsetRangesForTopic(String topic) async {
    final url = '$HostPort/rest/kafka/offsets/$topic';
    http.Response response = await http.get(url);
    final jsonResponse = verifyResponse(response);

    final range = List<OffsetRange>();
    jsonResponse.forEach((j) => range.add(OffsetRange.fromJson(j)));
    return range;
  }

  static Future<Set<String>> repartition(CreatePartitionRequest request) async {
    final url = '$HostPort/rest/kafka/repartition';

    final jsonBody = encoder.convert(request.asJson);
    print('curl -XPOST -d $jsonBody $url');
    http.Response response = await http.post(url, body: jsonBody);
    final Set<String> jsonResponse = verifyResponse(response);
    return jsonResponse;
  }

  static Future<Map<String, List<MetricEntry>>> metrics() async {
    final url = '$HostPort/rest/kafka/metrics';
    http.Response response = await http.get(url);
    final Map<String, dynamic> jsonBody = verifyResponse(response);

    Map<String, List<MetricEntry>> metrics = {};
    print('jsonBody is $jsonBody');
    jsonBody.forEach((key, value) {
      List<MetricEntry> entries = [];
      value.forEach((e) => entries.add(MetricEntry.fromJson(e)));
      metrics[key] = entries;
    });
    return metrics;
  }
}
