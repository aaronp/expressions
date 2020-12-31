import 'package:http/http.dart' as http;
import 'dart:convert';
import 'model.dart';
import 'rest_client.dart';

class ConfigClient {

  static JsonDecoder decoder = JsonDecoder();
  static JsonEncoder encoder = JsonEncoder.withIndent('  ');

  static Future<String> defaultConfig() async {
    http.Response response = await http.get('${RestClient.HostPort}/rest/config');
    final json = decoder.convert(response.body);

    final pretty = encoder.convert(json);

    print("PRETTY:");
    print(pretty);
    return pretty;
  }

  static Future<String> configForName(String name) async {
    http.Response response = await http.get('${RestClient.HostPort}/rest/config?name=$name');
    // print('got ${response.statusCode}: ${response.body}');
    // final Books got = response.body;
    // final Books got = json.decode(response.body);
    // print('cast is ${got.bookList.length} : ${got}');
    final json = decoder.convert(response.body);

    final pretty = encoder.convert(json);

    print("PRETTY:");
    print(pretty);
    return pretty;
  }
}
