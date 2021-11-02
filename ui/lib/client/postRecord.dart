import 'dart:convert';
import 'package:rest_client/rest_client.dart' as rc;
import 'package:ui/client/http.dart';


class PostRecord {
  PostRecord(
      this.data,
      this.config,
      this.key,
      this.repeat,
      this.partition,
      this.topicOverride,
      this.headers
      );

  dynamic data;
  String config;
  dynamic key;
  int repeat;
  int partition = 0;
  String topicOverride = '';
  Map<String,String> headers;

  Future<int> publish() async {
    final jason = jsonEncode(asJson);
    var httpRequest = rc.Request(
        method: rc.RequestMethod.post,
        url: '${Rest.HostPort}/rest/kafka/publish',
        body: jason,
        headers: Rest.HttpHeaders);
    final response = await Rest.client.execute(request: httpRequest);
    return response.body;
  }


  Map<String, Object> get asJson {
    return {
      'data': data,
      'config': config,
      'key': key,
      'repeat': repeat,
      'partition': partition,
      'topicOverride': topicOverride,
      'headers': headers
    };
  }

  static PostRecord fromJson(Map<String, dynamic> json) {
    return PostRecord(
        json['data'],
        json['config'],
        json['key'],
        json['repeat'],
        json['partition'],
        json['topicOverride'],
        json['headers']);
  }
}