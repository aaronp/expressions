import 'dart:typed_data';

import 'dart:convert';
import 'package:rest_client/rest_client.dart' as rc;
import 'package:http/http.dart' as http;
import 'package:rest_client/rest_client.dart';
import 'dart:html' as darthtml;
import 'package:http/http.dart';
import 'package:ui/client/http.dart';

class DataGenClient {

  static Future<dynamic> contentAsJson(String data) async {
    var httpRequest = rc.Request(
        method: rc.RequestMethod.post,
        url: '${Rest.HostPort}/rest/data/parse',
        body: data,
        headers: Rest.HttpHeaders);
    final response = await Rest.client.execute(request: httpRequest);
    return jsonDecode(response.body);
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
