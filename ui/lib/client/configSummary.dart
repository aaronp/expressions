import 'dart:core';
import 'dart:convert';

class ConfigSummary {
  ConfigSummary(
      this.topic,
      this.mappings,
      this.brokers,
      this.keyType,
      this.valueType,
      this.producerKeyType,
      this.producerValueType,
      );

  static empty() {
    Map<String, List<String>> empty = {};
    return ConfigSummary("", empty, [], "", "", "", "");
  }

  bool isEmpty() {
    return topic == "";
  }

  String topic;
  List<String> brokers = [];

  // Map<String, dynamic> mappings = {};
  Map<String, List<String>> mappings = {};
  String keyType;
  String valueType;
  String producerKeyType;
  String producerValueType;

  String brokersAsString() {
    if (brokers.length == 1) {
      return brokers.first;
    } else if (brokers.length == 0) {
      return "";
    } else {
      return brokers.join(", ");
    }
  }
  @override
  String toString() {
    return asJson.toString();
  }

  Object get asJson {
    return {
      'topic': topic,
      'brokers': brokers,
      'mappings': jsonEncode(mappings),
      'keyType': keyType,
      'valueType': valueType,
      'producerKeyType' : producerKeyType,
      'producerValueType' : producerValueType
    };
  }

  static ConfigSummary fromJson(Map<String, dynamic> json) {
    final List<dynamic> brokers = json['brokers'];
    final Map<String, dynamic> mappingPathByName = json['mappings'];
    Map<String, List<String>> mappings = {};
    mappingPathByName.forEach((key, value) {
      List<dynamic> pathToFile = value;
      mappings[key] = pathToFile.map((e) => e.toString()).toList();
    });

    print("Mappings is: $mappings");
    return ConfigSummary(
        json['topic'],
        mappings,
        brokers.map((e) => e.toString()).toList(),
        json['keyType'],
        json['valueType'],
        json['producerKeyType'],
        json['producerValueType'],
    );
  }
}
