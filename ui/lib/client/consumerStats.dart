class ConsumerStats {
  ConsumerStats(
      this.id,
      this.totalRecords,
      this.recentRecords,
      this.errors
      );

  String id;
  int totalRecords;
  List<RecordSummary> recentRecords = [];
  List<RecordSummary> errors = [];

  @override String toString() => asJson.toString();

  Map<String, Object> get asJson {
    return {
      'id': id,
      'totalRecords': totalRecords,
      'recentRecords': recentRecords,
      'errors': errors
    };
  }

  static ConsumerStats fromJson(Map<String, dynamic> json) {
    final List<dynamic> records = json['recentRecords'];
    final List<dynamic> errors = json['errors'];
    return ConsumerStats(
        json['id'],
        json['totalRecords'],
        records.map((j) => RecordSummary.fromJson(j)).toList(),
        errors.map((j) => RecordSummary.fromJson(j)).toList());
  }
}
class RecordSummary {
  RecordSummary(
      this.record,
      this.message,
      this.value,
      this.timestampEpochMillis,
      this.supplementaryData
      );

  RecordCoords record;
  String message;
  dynamic value;
  int timestampEpochMillis;
  dynamic supplementaryData;

  Map<String, Object> get asJson {
    return {
      'record': record,
      'message': message,
      'value': value,
      'timestampEpochMillis': timestampEpochMillis,
      'supplementaryData': supplementaryData
    };
  }

  static RecordSummary fromJson(Map<String, dynamic> json) {
    return RecordSummary(
        RecordCoords.fromJson(json['record']),
        json['message'],
        json['value'],
        json['timestampEpochMillis'],
        json['supplementaryData']);
  }
}

class RecordCoords {
  RecordCoords(
      this.topic,
      this.offset,
      this.partition,
      this.key
      );

  String topic;
  int offset;
  int partition;
  String key;

  Map<String, Object> get asJson {
    return {
      'topic': topic,
      'offset': offset,
      'partition': partition,
      'key': key
    };
  }

  static RecordCoords fromJson(Map<String, dynamic> json) {
    return RecordCoords(
        json['topic'],
        json['offset'],
        json['partition'],
        json['key']);
  }
}