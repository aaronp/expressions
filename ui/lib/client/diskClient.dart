import 'package:rest_client/rest_client.dart' as rc;
import 'package:rest_client/rest_client.dart';
import 'dart:convert';
import 'configSummary.dart';
import 'http.dart';

class DiskClient {

  static Future<List<dynamic>> listFiles(String path) async {
    var response = await Rest.client.execute(
        request: rc.Request(method: RequestMethod.get, url: '${Rest.HostPort}/rest/store/list/$path', headers: Rest.HttpHeaders));
    List<dynamic> body = response.body;

    return body;
  }
  static Future<String> getLastSaved() {
    return get("metadata/lastSaved");
  }

  static Future<String> get(String path) async {
    try {
      print("Client.get('$path')");
      var response = await Rest.client.execute(
        request: rc.Request(
            method: RequestMethod.get, url: '${Rest.HostPort}/rest/store/get/$path', headers: Rest.HttpHeaders),
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
