import 'dart:convert';

import 'package:rest_client/rest_client.dart' as rc;
import 'package:ui/client/batchCheckRequest.dart';
import 'package:ui/client/startedConsumer.dart';

import 'consumerStats.dart';
import 'http.dart';
import 'mappingCheck.dart';

class BatchClient {
  static Future<String> start(String config) async {
    var request = rc.Request(
        method: rc.RequestMethod.post,
        url: '${Rest.HostPort}/rest/batch/start',
        body: config,
        headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: request);
    return response.body.toString();
  }

  static Future<bool> stop(String id) async {
    var request = rc.Request(
        method: rc.RequestMethod.post,
        url: '${Rest.HostPort}/rest/batch/stop/$id',
        headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: request);
    print("stop $id -> ${response.body}");
    return response.body.toString().toLowerCase() == "true";
  }

  static Future<TransformResponse> check(BatchCheckRequest request) async {
    final checkJson = jsonEncode(request.asJson);
    var httpRequest = rc.Request(
        method: rc.RequestMethod.post,
        url: '${Rest.HostPort}/rest/batch/test',
        body: checkJson,
        headers: Rest.HttpHeaders);
    final response = await Rest.client.execute(request: httpRequest);
    return TransformResponse.fromJson(response.body);
  }


  static Future<List<StartedConsumer>> running() async {
    var request = rc.Request(
        method: rc.RequestMethod.get,
        url: '${Rest.HostPort}/rest/batch/running',
        headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: request);
    final List<dynamic> startedConsumers = response.body;
    return startedConsumers.map((j) => StartedConsumer.fromJson(j)).toList();
  }

  static Future<ConsumerStats> stats(String id) async {
    var request = rc.Request(
        method: rc.RequestMethod.get,
        url: '${Rest.HostPort}/rest/batch/stats/$id',
        headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: request);
    final dynamic stats = response.body;
    print("STATUS RESP: >>>${response.body}<<<");
    return stats == null ? null : ConsumerStats.fromJson(stats);
  }
}
