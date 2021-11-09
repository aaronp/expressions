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

  static TopicData empty() => TopicData([],[],[]);

  List<SubjectData> key = [];
  List<SubjectData> value = [];
  List<SubjectData> other = [];


  bool hasKeySchema() => key.isNotEmpty;
  SubjectData keyData() => hasKeySchema() ? key.first : SubjectData.empty();

  bool hasValueSchema() => value.isNotEmpty;
  SubjectData valueData() => hasValueSchema() ? value.first : SubjectData.empty();

  SubjectData data() {
    final List<SubjectData> all = [
      ...key,
      ...value,
      ...other
    ];
    if (all.length > 1) {
      throw new Exception("${all.length} topic data found");
    }
    return all.isEmpty ? SubjectData.empty() : all.first;
  }

  bool operator ==(o) => o is TopicData && asJson == o.asJson;
  int get hashCode => asJson.hashCode;

  dynamic get asJson {
    return jsonEncode(asMap);
  }

  @override String toString() => asMap.toString();

  Map<String, Object> get asMap {
    return {
      'key': key,
      'value': value,
      'other': other
    };
  }

  static Future<TopicData> get(String topic) async {
    var httpRequest = rc.Request(
        method: rc.RequestMethod.get,
        // url: '${Rest.HostPort}/rest/kafka/topic/$topic?seed=123',
        url: '${Rest.HostPort}/rest/kafka/topic/$topic',
        headers: Rest.HttpHeaders);
    final response = await Rest.client.execute(request: httpRequest);

    try {
      return TopicData.fromJson(response.body);
    } catch(e) {
      print("Error getting parsing response from: >${response.body}<");
      return TopicData.empty();
    }
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