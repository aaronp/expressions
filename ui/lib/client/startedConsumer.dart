class StartedConsumer {
  StartedConsumer(
      this.id,
      this.config,
      this.startedAtEpoch
      );

  String id;
  String config;
  int startedAtEpoch;

  Map<String, Object> get asJson {
    return {
      'id': id,
      'config': config,
      'startedAtEpoch': startedAtEpoch
    };
  }

  static StartedConsumer fromJson(Map<String, dynamic> json) {
    return StartedConsumer(
        json['id'],
        json['config'],
        json['startedAtEpoch']);
  }
}