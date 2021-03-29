import 'dart:convert';

import 'message.dart';

class BatchCheckRequest {
  BatchCheckRequest(this.rootConfig, this.batch, this.script);

  String rootConfig;
  List<Message> batch;
  String script;

  Map<String, Object> get asJson {
    final jasons = batch.map((b) => b.asJson).toList();
    return {'rootConfig': rootConfig, 'batch': jasons, 'script': script};
  }

  static BatchCheckRequest fromJson(Map<String, dynamic> json) {
    final List<dynamic> batch = json['batch'];
    return BatchCheckRequest(json['rootConfig'],
        batch.map((j) => Message.fromJson(j)).toList(), json['script']);
  }
}
