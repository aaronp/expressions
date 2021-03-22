import 'package:rest_client/rest_client.dart' as rc;
import 'http.dart';
import 'dart:convert';

import 'httpRequest.dart';

class MappingCheck {
  /**
   * check (test) the given request mapping
   */
  static Future<TransformResponse> check(TransformRequest request) async {
    final checkJson = jsonEncode(request.asJson);
    var httpRequest = rc.Request(method: rc.RequestMethod.post, url: '${Rest.HostPort}/rest/mapping/check', body: checkJson, headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: httpRequest);
    return TransformResponse.fromJson(response.body);
  }
}

class TransformRequest {
  TransformRequest(
      this.script,
      this.input,
      this.key,
      this.timestamp,
      this.headers,
      this.topic,
      this.offset,
      this.partition
      );

  String script;
  String input;
  String key;
  int timestamp;
  Map<String,String> headers;
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
  TransformResponse(
      this.result,
      this.messages
      );

  List<HttpRequest> result;
  String messages = null;

  Map<String, Object> get asJson {
    if (result.length == 0) {
      return {
        'result': null,
        'messages': messages
      };
    } else {
      assert(result.length == 1);
      return {
        'result': result.first,
        'messages': messages
      };
    }
  }

  static TransformResponse fromJson(Map<String, dynamic> json) {
    final List<dynamic> list = json['result'];
    final optionalResult = list.map((e) => HttpRequest.fromJson(e)).toList();
    return TransformResponse(
        optionalResult,
        json['messages']);
  }
}
