import 'dart:core';
import 'dart:convert';

class ConfigSummary {
  ConfigSummary(
      this.topic,
      this.mappings,
      this.brokers,
      this.keyType,
      this.valueType);

  static empty() {
    return ConfigSummary("", {}, [], "", "");
  }

  String topic;
  List<String> brokers = [];

  // Map<String, dynamic> mappings = {};
  Map<String, Object> mappings = {};
  String keyType;
  String valueType;

  @override
  String toString() {
    return asJson.toString();
  }

  //Map<String, Object>
  Object get asJson {
    return {
      'topic': topic,
      'brokers': brokers,
      'mappings': jsonEncode(mappings),
      'keyType': keyType,
      'valueType': valueType
    };
  }

  static ConfigSummary fromJson(Map<String, dynamic> json) {
    final List<dynamic> brokers = json['brokers'];
    return ConfigSummary(
        json['topic'],
        json['mappings'],
        brokers.map((e) => e.toString()).toList(),
        json['keyType'],
        json['valueType']);
  }
}
