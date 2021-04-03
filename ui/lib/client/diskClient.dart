import 'package:rest_client/rest_client.dart' as rc;
import 'package:rest_client/rest_client.dart';
import 'package:http/http.dart' as http;
import 'http.dart';

class DiskClient {

  static Future<List<dynamic>> listFiles(String path) async {
    var response = await Rest.client.execute(
        request: rc.Request(method: RequestMethod.get, url: '${Rest.HostPort}/rest/store/list/$path', headers: Rest.HttpHeaders));
    List<dynamic> body = response.body;

    return body;
  }
  static Future<String> getLastSaved() {
    // return get("metadata/lastSaved");
    return Future.value("");
  }

  static Future<String> get(String path) async {
    try {
      final getHeaders = Map<String, String>.of(Rest.HttpHeaders);
      getHeaders["accept"] = "text/plain";
      getHeaders["content-type"] = "text/plain";
      final url = '${Rest.HostPort}/rest/store/get/$path';
      final got = await http.get(url, headers: getHeaders);
      if (got.body == null) {
        return "";
      }
      return got.body.toString();
    } catch (e) {
      print("Error getting '$path': >>$e<<");
      return "";
    }
  }

  static Future<void> store(String path, String content) async {
    try {
      print("Client.store('$path', '$content')");
      var response = await Rest.client.execute(
        request: rc.Request(
            method: RequestMethod.post, url: '${Rest.HostPort}/rest/store/$path', headers: Rest.HttpHeaders, body: content),
      );
      assert(response.statusCode == 200, "Store returned ${response.statusCode}");
    } catch (e) {
      print("Error storing '$path': >>$e<<");
      return "";
    }
  }
}
