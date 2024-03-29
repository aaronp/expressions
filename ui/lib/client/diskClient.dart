import 'package:http/http.dart' as http;
import 'package:rest_client/rest_client.dart' as rc;
import 'package:rest_client/rest_client.dart';

import 'http.dart';

class DiskClient {
  static Future<List<String>> listFiles(String path) async {
    var response = await Rest.client.execute(
        request: rc.Request(
            method: RequestMethod.get,
            url: '${Rest.HostPort}/rest/store/list/$path',
            headers: Rest.HttpHeaders));
    List<dynamic> body = response.body;
    return body.map((v) => v.toString()).toList();
  }

  static Future<String> getLastSaved() {
    return get("metadata/lastSaved");
  }

  static Future<void> setLastSaved(String fileName) =>
      store("metadata/lastSaved", fileName);

  static Future<String> get(String path) async {
    final url = Uri.http(Rest.Authority, '/rest/store/get/$path');
    try {
      final getHeaders = Map<String, String>.of(Rest.HttpHeaders);
      getHeaders["accept"] = "text/plain";
      getHeaders["content-type"] = "text/plain";
      final got = await http.get(url, headers: getHeaders);
      if (got.body == null) {
        return "";
      }
      return got.body.toString();
    } catch (e) {
      print("Error getting '$url': >>$e<<");
      return "";
    }
  }

  static Future<void> remove(String path) async {
    final url = Uri.http(Rest.Authority, '/rest/store/$path');
    try {
      final getHeaders = Map<String, String>.of(Rest.HttpHeaders);
      getHeaders["accept"] = "text/plain";
      getHeaders["content-type"] = "text/plain";
      await http.delete(url, headers: getHeaders);
      return;
    } catch (e) {
      print("Error delete '$url': >>$e<<");
      return "";
    }
  }

  static Future<void> store(String path, String content) async {
    try {
      print("Client.store('$path', '$content')");
      var response = await Rest.client.execute(
        request: rc.Request(
            method: RequestMethod.post,
            url: '${Rest.HostPort}/rest/store/$path',
            headers: Rest.HttpHeaders,
            body: content),
      );
      assert(
          response.statusCode == 200, "Store returned ${response.statusCode}");
    } catch (e) {
      print("Error storing '$path': >>$e<<");
      return "";
    }
  }
}
