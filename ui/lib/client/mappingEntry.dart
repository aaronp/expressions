class MappingEntry {
  MappingEntry(
      this.topic,
      this.filePath
      );

  String topic;
  String filePath;

  Map<String, Object> get asJson {
    return {
      'topic': topic,
      'filePath': filePath
    };
  }

  static MappingEntry fromJson(Map<String, dynamic> json) {
    return MappingEntry(
        json['topic'],
        json['filePath']);
  }
}