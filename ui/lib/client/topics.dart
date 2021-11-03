import 'dart:convert';
import 'package:rest_client/rest_client.dart' as rc;
import 'package:ui/client/http.dart';



class Topics {
  Topics(
      this.keys,
      this.values,
      this.other
      ) {
    final set  = {
      ...keys,
      ...values,
      ...other
    };
    all = set.toList();
    all.sort();
  }

  List<String> all = [];
  List<String> keys = [];
  List<String> values = [];
  List<String> other = [];

  bool operator ==(o) => o is Topics && asJson == o.asJson;
  int get hashCode => asJson.hashCode;

  dynamic get asJson {
    return jsonEncode(asMap);
  }

  @override String toString() => asMap.toString();

  static Future<Topics> get() async {
    var httpRequest = rc.Request(
        method: rc.RequestMethod.get,
        url: '${Rest.HostPort}/rest/kafka/topics',
        headers: Rest.HttpHeaders);
    final response = await Rest.client.execute(request: httpRequest);
    return Topics.fromJson(response.body);
  }

  Map<String, Object> get asMap {
    return {
      'keys': keys,
      'values': values,
      'other': other
    };
  }

  static Topics fromJson(Map<String, dynamic> json) {
    List<dynamic> keys = json['keys'];
    List<dynamic> values = json['values'];
    List<dynamic> other = json['other'];
    return Topics(
        keys.map((e) => e.toString()).toList(),
        values.map((e) => e.toString()).toList(),
        other.map((e) => e.toString()).toList());
  }
}