import 'package:rest_client/rest_client.dart' as rc;
import 'package:ui/client/startedConsumer.dart';

import 'consumerStats.dart';
import 'http.dart';

class KafkaClient {
  static Future<String> start(String config) async {
    var request = rc.Request(
        method: rc.RequestMethod.post,
        url: '${Rest.HostPort}/rest/kafka/start/$config',
        headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: request);
    return response.body.toString();
  }

  static Future<bool> stop(String id) async {
    var request = rc.Request(
        method: rc.RequestMethod.post,
        url: '${Rest.HostPort}/rest/kafka/stop/$id',
        headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: request);
    return response.toString().toLowerCase() == "true";
  }

  static Future<List<StartedConsumer>> running() async {
    var request = rc.Request(
        method: rc.RequestMethod.get,
        url: '${Rest.HostPort}/rest/kafka/running',
        headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: request);
    final List<dynamic> startedConsumers = response.body;
    return startedConsumers.map((j) => StartedConsumer.fromJson(j)).toList();
  }

  static Future<ConsumerStats> stats(String id) async {
    var request = rc.Request(
        method: rc.RequestMethod.get,
        url: '${Rest.HostPort}/rest/kafka/stats/$id',
        headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: request);
    final dynamic stats = response.body;
    return stats == null ? null : ConsumerStats.fromJson(stats);
  }
}
