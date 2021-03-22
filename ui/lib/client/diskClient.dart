import 'package:rest_client/rest_client.dart' as rc;
import 'package:rest_client/rest_client.dart';
import 'dart:convert';
import 'configSummary.dart';

class DiskClient {
  static final _client = rc.Client();

  static final HostPort = "http://localhost:8080";

  static final HttpHeaders = { "Access-Control-Allow-Origin" : "*" };

  static Future<List<dynamic>> listFiles(String path) async {
    var response = await _client.execute(
        request: rc.Request(method: RequestMethod.get, url: '$HostPort/rest/store/list/$path', headers: HttpHeaders));
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
            method: RequestMethod.get, url: '$HostPort/rest/store/get/$path', headers: HttpHeaders),
      );
      // return jsonEncode();
      print("Client.get('$path') returning >${response.body}<, isNull=${response.body == null}");
      if (response.body == null) {
        return "";
      }
      return response.body.toString();
    } catch (e) {
      print("Error getting '$path': >>$e<<");
      return "";
    }
  }
}
