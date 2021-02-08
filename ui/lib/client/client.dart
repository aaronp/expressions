import 'package:rest_client/rest_client.dart' as rc;
import 'package:rest_client/rest_client.dart';
import 'dart:convert';
import 'model.dart';

class Client {
  static final _client = rc.Client();

  static final HostPort = "http://localhost:8080";

  static Future<ConfigSummary> configSummary(String config) async {
    var request = rc.Request(url: '$HostPort/rest/config/parse', body: config);

    try {
      var response = await _client.execute(request: request);
      final summary = ConfigSummary.fromJson(response.body);
      return summary;
    } catch (e) {
      return ConfigSummary.empty();
    }
  }

  static Future<String> defaultConfig() async {
    var response = await _client.execute(
      request: rc.Request(url: '$HostPort/rest/config'),
    );
    final configAsJson = response.body;
    String cj = jsonEncode(configAsJson);
    print('configAsJson is $cj');

    return cj;
  }

  static Future<List<dynamic>> listFiles(String path) async {
    var response = await _client.execute(
      request: rc.Request(
          method: RequestMethod.get, url: '$HostPort/rest/store/list/$path'),
    );
    List<dynamic> body = response.body;

    return body;
  }

  static Future<String> getLastSaved() => get("metadata/lastSaved");

  static Future<String> get(String path) async {
    try {
      var response = await _client.execute(
        request: rc.Request(
            method: RequestMethod.get, url: '$HostPort/rest/store/get/$path'),
      );
      return jsonEncode(response.body);
    } catch (e) {
      print("Error  getting '$path': $e");
      return "";
    }
  }
}
