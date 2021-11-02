import 'dart:convert';
import 'package:rest_client/rest_client.dart' as rc;
import 'package:ui/client/http.dart';
import 'package:ui/client/subjectData.dart';

class TopicData {
  TopicData(
      this.key,
      this.value,
      this.other
      );

  List<SubjectData> key = [];
  List<SubjectData> value = [];
  List<SubjectData> other = [];


  static Future<TopicData> get(String topic) async {
    var httpRequest = rc.Request(
        method: rc.RequestMethod.get,
        // url: '${Rest.HostPort}/rest/kafka/topic/$topic?seed=123',
        url: '${Rest.HostPort}/rest/kafka/topic/$topic',
        headers: Rest.HttpHeaders);
    final response = await Rest.client.execute(request: httpRequest);
    return TopicData.fromJson(response.body);
  }

  Map<String, Object> get asJson {
    return {
      'key': key,
      'value': value,
      'other': other
    };
  }

  static TopicData fromJson(Map<String, dynamic> json) {
    List<dynamic> key = json['key'];
    List<dynamic> value = json['value'];
    List<dynamic> other = json['other'];
    return TopicData(
        key.map((e) => SubjectData.fromJson(e)).toList(),
        value.map((e) => SubjectData.fromJson(e)).toList(),
        other.map((e) => SubjectData.fromJson(e)).toList());
  }
}