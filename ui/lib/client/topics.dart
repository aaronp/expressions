import 'dart:convert';
import 'package:rest_client/rest_client.dart' as rc;
import 'package:ui/client/http.dart';



class Topics {
  Topics(
      this.keys,
      this.values,
      this.other
      );

  List<String> keys = [];
  List<String> values = [];
  List<String> other = [];

  static Future<Topics> get() async {
    var httpRequest = rc.Request(
        method: rc.RequestMethod.get,
        url: '${Rest.HostPort}/rest/kafka/topics',
        headers: Rest.HttpHeaders);
    final response = await Rest.client.execute(request: httpRequest);
    return Topics.fromJson(response.body);
  }

  Map<String, Object> get asJson {
    return {
      'keys': keys,
      'values': values,
      'other': other
    };
  }

  static Topics fromJson(Map<String, dynamic> json) {
    return Topics(
        json['keys'],
        json['values'],
        json['other']);
  }
}