import 'dart:typed_data';

import 'dart:convert';
import 'package:rest_client/rest_client.dart' as rc;
import 'package:http/http.dart' as http;
import 'package:rest_client/rest_client.dart';
import 'dart:html' as darthtml;
import 'package:http/http.dart';
import 'package:ui/client/http.dart';

class DataGenClient {

  static String pretty(String content) {
    try {
      return prettyJson(jsonDecode(content));
    } catch (e) {
      print("pretty threw  $e for >$content<");
      return content;
    }
  }
  static String prettyJson(dynamic content) {
    JsonEncoder encoder = new JsonEncoder.withIndent('  ');
    try {
      return encoder.convert(content);
    } catch (e) {
      print("prettyJson threw $e for >${content}< w/ type $content");
      return content;
    }
  }

  static Future<dynamic> contentAsJson(String data) async {
    var httpRequest = rc.Request(
        method: rc.RequestMethod.post,
        url: '${Rest.HostPort}/rest/data/parse',
        body: data,
        headers: Rest.HttpHeaders);
    final response = await Rest.client.execute(request: httpRequest);
    try {
      return jsonDecode(response.body);
    } catch (e) {
      return response.body;
    }
  }

  static Future<dynamic> dataAsJson(Uint8List data) async {
    final request = new MultipartRequest(
      "POST",
      Uri.parse('${Rest.HostPort}/rest/data/parse'),
    );

    request.files.add(MultipartFile.fromBytes("file", data, filename : "this.is.ignored"));
    var resp = await request.send();
    String jason = await resp.stream.bytesToString();
    return jsonDecode(jason);
  }

}
