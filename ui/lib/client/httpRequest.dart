class HttpRequest {
  HttpRequest(
      this.method,
      this.url,
      this.headers,
      this.body
      );

  String method;
  String url;
  Map<String, String> headers;
  String body;

  Map<String, Object> get asJson {
    return {
      'method': method,
      'url': url,
      'headers': headers,
      'body': body
    };
  }

  static HttpRequest fromJson(Map<String, dynamic> json) {
    print("parsing http response from  ${json.runtimeType} >>$json<<");
    //json['headers']
    final headers = Map();
    return HttpRequest(
        json['method'],
        json['url'],
        Map(),
        json['body']);
  }
}