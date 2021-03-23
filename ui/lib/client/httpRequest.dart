import 'dart:convert';

import 'package:rest_client/rest_client.dart' as rc;
import 'package:ui/client/http.dart';

import 'httpResponse.dart';

class HttpRequest {
  HttpRequest(this.method, this.url, this.headers, this.body);

  String method;
  String url;
  Map<String, String> headers;
  dynamic body;

  Map<String, Object> get asJson {
    return {'method': method, 'url': url, 'headers': headers, 'body': body};
  }

  /** execute the request via the proxy
   */
  Future<HttpResponse> exec() async {
    final checkJson = jsonEncode(asJson);
    var httpRequest = rc.Request(
        method: rc.RequestMethod.post,
        url: '${Rest.HostPort}/rest/proxy',
        body: checkJson,
        headers: Rest.HttpHeaders);
    final response = await Rest.client.execute(request: httpRequest);
    return HttpResponse.fromJson(response.body);
  }

  static HttpRequest fromJson(Map<String, dynamic> json) {
    print("parsing http response from  ${json.runtimeType} >>$json<<");
    //json['headers']
    final headers = Map();
    return HttpRequest(json['method'], json['url'], Map(), json['body']);
  }
}
