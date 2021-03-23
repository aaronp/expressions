class HttpResponse {
  HttpResponse(
      this.statusCode,
      this.body
      );

  int statusCode;
  String body;

  Map<String, Object> get asJson {
    return {
      'statusCode': statusCode,
      'body': body
    };
  }

  static HttpResponse fromJson(Map<String, dynamic> json) {
    return HttpResponse(
        json['statusCode'],
        json['body']);
  }
}