
import 'package:rest_client/rest_client.dart' as rc;
import 'package:rest_client/rest_client.dart';

import 'model.dart';

class Client {
  static final _client = rc.Client();

  static final HostPort = "http://localhost:8080";

  static Future<ConfigSummary> configSummary(String config) async {
    var request = rc.Request(
      url: '$HostPort/rest/parse',
      body : config
    );

    var response = await _client.execute(
      //authorizor: rc.TokenAuthorizer(token: 'my_token_goes_here'),
      request: request,
    );

    return ConfigSummary.fromJson(response.body);
  }

  static dynamic defaultConfig() async {
    var response = await _client.execute(
      request: rc.Request(url: '$HostPort/rest/config'),
    );
    return response.body;
  }

  static Future<List<dynamic>> listFiles(String path) async {
    var response = await _client.execute(
      request: rc.Request(method : RequestMethod.get, url: '$HostPort/rest/store/list/$path'),
    );
    List<dynamic> body = response.body;

    return body;
  }

  static Future<String> getLastSaved() => get("metadata/lastSaved");

  static Future<String> get(String path) async {
    var response = await _client.execute(
      request: rc.Request(method : RequestMethod.get, url: '$HostPort/rest/store/get/$path'),
    );
    dynamic body = response.body;

    return body.toString();
  }
}
