import 'dart:convert';
import 'package:rest_client/rest_client.dart' as rc;
import 'package:ui/client/http.dart';

class SubjectData {
  SubjectData(
      this.subject,
      this.version,
      this.schema,
      this.testData
      );

  String subject;
  int version;
  dynamic schema;
  dynamic testData;

  bool operator ==(o) => o is SubjectData && asJson == o.asJson;
  int get hashCode => asJson.hashCode;

  dynamic get asJson {
    return jsonEncode(asMap);
  }

  @override String toString() => asMap.toString();

  Map<String, Object> get asMap {
    return {
      'subject': subject,
      'version': version,
      'schema': schema,
      'testData': testData
    };
  }

  static SubjectData fromJson(Map<String, dynamic> json) {
    return SubjectData(
        json['subject'],
        json['version'],
        json['schema'],
        json['testData']);
  }
}