class Message {
  Message(
      this.key,
      this.content,
      this.timestamp,
      this.headers,
      this.topic,
      this.offset,
      this.partition
      );

  dynamic key;
  dynamic content;
  int timestamp;
  Map<String, String> headers;
  String topic;
  int offset;
  int partition;

  Map<String, Object> get asJson {
    return {
      'key': key,
      'content': content,
      'timestamp': timestamp,
      'headers': headers,
      'topic': topic,
      'offset': offset,
      'partition': partition
    };
  }

  static Message fromJson(Map<String, dynamic> json) {
    final dynamic headersJ = json['headers'];
    final headers = Map<String, String>.from(headersJ);
    return Message(
        json['key'],
        json['content'],
        json['timestamp'],
        headers,
        json['topic'],
        json['offset'],
        json['partition']);
  }
}