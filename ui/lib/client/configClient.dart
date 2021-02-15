import 'package:rest_client/rest_client.dart' as rc;
import 'http.dart';
import 'dart:convert';
import 'configSummary.dart';

class ConfigClient {

  static Future<ConfigSummary> configSummary(String config) async {
    var request = rc.Request(url: '${Rest.HostPort}/rest/config/parse', body: config, headers: Rest.HttpHeaders);

    try {
      var response = await Rest.client.execute(request: request);
      return ConfigSummary.fromJson(response.body);
    } catch (e) {
      print("Error parsing config $e");
      return ConfigSummary.empty();
    }
  }

  static Future<List<String>> formatConfig(String config) async {
    var request = rc.Request(method: rc.RequestMethod.post, url: '${Rest.HostPort}/rest/config/format', body: config, headers: Rest.HttpHeaders);
    var response = await Rest.client.execute(request: request);
    final List<dynamic> summary = response.body;
    return summary.map((line) => line.toString()).toList();
  }

  static Future<String> defaultConfig() async {
    var response = await Rest.client.execute(
      request: rc.Request(
          method: rc.RequestMethod.get, url: '${Rest.HostPort}/rest/config', headers: Rest.HttpHeaders),
    );
    final configAsJson = response.body;
    return jsonEncode(configAsJson);
  }

}
