import 'package:rest_client/rest_client.dart' as rc;
import 'package:rest_client/rest_client.dart';
import 'dart:convert';
import 'configSummary.dart';

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
    return jsonEncode(configAsJson);
  }

  static Future<List<dynamic>> listFiles(String path) async {
    var response = await _client.execute(
      request: rc.Request(
          method: RequestMethod.get, url: '$HostPort/rest/store/list/$path'),
    );
    List<dynamic> body = response.body;

    return body;
  }

  static Future<String> getLastSaved() {
    return get("metadata/lastSaved");
  }

  static Future<String> get(String path) async {
    try {
      print("Client.get('$path')");
      var response = await _client.execute(
        request: rc.Request(
            method: RequestMethod.get, url: '$HostPort/rest/store/get/$path'),
      );
      // return jsonEncode();
      print("Client.get('$path') returning >${response.body}<, isNull=${response.body == null}");
      if (response.body == null) {
        return "";
      }
      return response.body;
    } catch (e) {
      print("Error getting '$path': >>$e<<");
      return "";
    }
  }
}
