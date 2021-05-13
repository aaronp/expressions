import 'package:rest_client/rest_client.dart' as rc;

class Rest {
  static final client = rc.Client();

  static final Authority = "localhost:8080";
  static final HostPort = "http://$Authority";
  static final HttpHeaders = { "Access-Control-Allow-Origin" : "*" };

}