import 'package:rest_client/rest_client.dart' as rc;

class Rest {
  static final client = rc.Client();

  static final HostPort = "http://localhost:8080";
  static final HttpHeaders = { "Access-Control-Allow-Origin" : "*" };

}