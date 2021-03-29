import 'dart:convert';

import 'package:rest_client/rest_client.dart' as rc;

import 'http.dart';
import 'httpRequest.dart';

class MappingCheck {
  /**
   * check (test) the given request mapping
   */
  static Future<TransformResponse> check(TransformRequest request) async {
    final checkJson = jsonEncode(request.asJson);
    var httpRequest = rc.Request(
        method: rc.RequestMethod.post,
        url: '${Rest.HostPort}/rest/mapping/check',
        body: checkJson,
        headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: httpRequest);
    return TransformResponse.fromJson(response.body);
  }
}

class TransformRequest {
  TransformRequest(this.script, this.input, this.key, this.timestamp,
      this.headers, this.topic, this.offset, this.partition);

  String script;
  dynamic input;
  String key;
  int timestamp;
  Map<String, String> headers;
  String topic;
  int offset;
  int partition;

  Map<String, Object> get asJson {
    return {
      'script': script,
      'input': input,
      'key': key,
      'timestamp': timestamp,
      'headers': headers,
      'topic': topic,
      'offset': offset,
      'partition': partition
    };
  }

  static TransformRequest fromJson(Map<String, dynamic> json) {
    return TransformRequest(
        json['script'],
        json['input'],
        json['key'],
        json['timestamp'],
        json['headers'],
        json['topic'],
        json['offset'],
        json['partition']);
  }
}

class TransformResponse {
  TransformResponse(this.result, this.success, this.messages);

  dynamic result;
  bool success;
  List<String> messages = [];

  Map<String, Object> get asJson {
    return {'result': result, 'success': success, 'messages': messages};
  }

  List<HttpRequest> asRequests() {
    final List<dynamic> list = result;
    return list.map((json) => HttpRequest.fromJson(json)).toList();
  }

  static TransformResponse fromJson(Map<String, dynamic> json) {
    List<dynamic> msgs = json['messages'];
    return TransformResponse(json['result'], json['success'], msgs.map((m) => m.toString()).toList());
  }
}

class Output {
  Output(this.stdOut, this.stdErr);

  @override String toString() => asJson.toString();

  List<String> stdOut = [];
  List<String> stdErr = [];

  Map<String, Object> get asJson {
    return {'stdOut': stdOut, 'stdErr': stdErr};
  }

  static Output fromJson(Map<String, dynamic> json) {
    final List<dynamic> stdOut = json['stdOut'];
    final List<dynamic> stdErr = json['stdErr'];
    return Output(stdOut.map((j) => j.toString()).toList(),
        stdErr.map((j) => j.toString()).toList());
  }
}
