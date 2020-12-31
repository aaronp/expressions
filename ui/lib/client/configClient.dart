import 'package:rest_client/rest_client.dart' as rc;
import 'package:rest_client/rest_client.dart';
import 'dart:convert';
import 'configSummary.dart';

class ConfigClient {
  static final _client = rc.Client();

  static final HostPort = "http://localhost:8080";

  static final HttpHeaders = { "Access-Control-Allow-Origin" : "*" };

  static Future<ConfigSummary> configSummary(String config) async {
    var request = rc.Request(url: '$HostPort/rest/config/parse', body: config, headers: HttpHeaders);

    try {
      var response = await _client.execute(request: request);
      final summary = ConfigSummary.fromJson(response.body);
      return summary;
    } catch (e) {
      return ConfigSummary.empty();
    }
  }

  static Future<List<String>> formatConfig(String config) async {
    var request = rc.Request(method: RequestMethod.post, url: '$HostPort/rest/config/format', body: config, headers: HttpHeaders);
    var response = await _client.execute(request: request);
    final List<dynamic> summary = response.body;
    return summary.map((line) => line.toString()).toList();
  }

  static Future<String> defaultConfig() async {
    var response = await _client.execute(
      request: rc.Request(
          method: RequestMethod.get, url: '$HostPort/rest/config', headers: HttpHeaders),
    );
    final configAsJson = response.body;
    return jsonEncode(configAsJson);
  }

}
