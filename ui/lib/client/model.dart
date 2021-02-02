
import 'dart:core';
import 'package:collection/collection.dart';

class ConfigSummary {
  ConfigSummary(
      this.topic,
      // this.mappings,
      this.brokers,
      this.keyType,
      this.valueType
      );

  String topic;
  List<String> brokers = [];
  // Map<String, List<String>> mappings = {};
  Map<String, Object> mappings = {};
  String keyType;
  String valueType;

  //Map<String, Object>
  Object get asJson {
    return {
      'topic': topic,
      'brokers': brokers,
      // 'mappings': mappings,
      'keyType': keyType,
      'valueType': valueType
    };
  }

  static ConfigSummary fromJson(Map<String, dynamic> json) {
    return ConfigSummary(
        json['topic'],
        json['brokers'],
        // json['mappings'],
        json['keyType'],
        json['valueType']);
  }
}
