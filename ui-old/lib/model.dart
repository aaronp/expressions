class PeekRequest {
  PeekRequest(this.topics, this.fromOffset, this.limit, this.partitions);

  Set<String> topics;
  int fromOffset;
  int limit;
  Set<int> partitions;

  Map<String, Object> get asJson {
    return {
      'topics': topics.toList(),
      'fromOffset': fromOffset,
      'limit': limit,
      'partitions': partitions.toList(),
    };
  }
}

class Record {
  Record(
      this.topic,
      this.key,
      this.offset,
      this.leaderEpoch,
      this.partition,
      this.serializedKeySize,
      this.serializedValueSize,
      this.timestamp,
      this.timestampType,
      this.value,
      this.base64);

  String topic;
  String key;
  int offset;
  int leaderEpoch;
  int partition;
  int serializedKeySize;
  int serializedValueSize;
  int timestamp;
  String timestampType;
  String value;
  String base64;

  static Record fromJson(Map<String, dynamic> json) {
    return Record(
        json['topic'],
        json['key'],
        json['offset'],
        json['leaderEpoch'],
        json['partition'],
        json['serializedKeySize'],
        json['serializedValueSize'],
        json['timestamp'],
        json['timestampType'],
        json['value'],
        json['base64']);
  }

  @override
  String toString() {
    return 'Record{offset: $offset, partition: $partition}';
  }

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is Record &&
          runtimeType == other.runtimeType &&
          topic == other.topic &&
          offset == other.offset &&
          partition == other.partition;

  @override
  int get hashCode => topic.hashCode ^ offset.hashCode ^ partition.hashCode;
}

class PublishOne {
  PublishOne(this.topic, this.key, this.value, this.partition, this.isBase64);

  String topic;
  String key;
  String value;
  int partition;
  bool isBase64;

  Map<String, Object> get asJson {
    return {
      'topic': topic,
      'key': key,
      'value': value,
      'partition': partition,
      'isBase64': isBase64
    };
  }

  static PublishOne fromJson(Map<String, dynamic> json) {
    return PublishOne(json['topic'], json['key'], json['value'],
        json['partition'], json['isBase64']);
  }
}

class TopicKey {
  TopicKey(this.topic, this.partition);

  String topic;
  int partition;

  Map<String, Object> get asJson {
    return {'topic': topic, 'partition': partition};
  }

  static TopicKey fromJson(Map<String, dynamic> json) {
    return TopicKey(json['topic'], json['partition']);
  }
}

class RecordMetadataResponse {
  RecordMetadataResponse(this.topicPartition, this.offset, this.timestamp,
      this.serializedKeySize, this.serializedValueSize);

  TopicKey topicPartition;
  int offset;
  int timestamp;
  int serializedKeySize;
  int serializedValueSize;

  Map<String, Object> get asJson {
    return {
      'topicPartition': topicPartition.asJson,
      'offset': offset,
      'timestamp': timestamp,
      'serializedKeySize': serializedKeySize,
      'serializedValueSize': serializedValueSize
    };
  }

  static RecordMetadataResponse fromJson(Map<String, dynamic> json) {
    return RecordMetadataResponse(
        TopicKey.fromJson(json['topicPartition']),
        json['offset'],
        json['timestamp'],
        json['serializedKeySize'],
        json['serializedValueSize']);
  }
}

class CreatePartitionRequest {
  CreatePartitionRequest(this.newPartitions, this.validateOnly);

  Map<String, UpdatedPartition> newPartitions;
  bool validateOnly;

  Map<String, dynamic> get asJson {
    final newPartitionsJson = {};
    newPartitions.forEach((key, value) {
      newPartitionsJson[key] = value.asJson;
    });
    return {'newPartitions': newPartitionsJson, 'validateOnly': validateOnly};
  }

  static CreatePartitionRequest fromJson(Map<String, dynamic> json) {
    return CreatePartitionRequest(json['newPartitions'], json['validateOnly']);
  }
}

class UpdatedPartition {
  UpdatedPartition(this.totalCount, this.newAssignments);

  int totalCount;
  List<List<int>> newAssignments;

  Map<String, Object> get asJson {
    return {'totalCount': totalCount, 'newAssignments': newAssignments};
  }

  static UpdatedPartition fromJson(Map<String, dynamic> json) {
    return UpdatedPartition(json['totalCount'], json['newAssignments']);
  }
}

class NodeDesc {
  NodeDesc(this.id, this.idString, this.host, this.port, this.rack);

  int id;
  String idString;
  String host;
  int port;
  String rack = null;

  Map<String, Object> get asJson {
    return {
      'id': id,
      'idString': idString,
      'host': host,
      'port': port,
      'rack': rack
    };
  }

  static NodeDesc fromJson(Map<String, dynamic> json) {
    if (json == null) {
      return null;
    }
    return new NodeDesc(
        json['id'], json['idString'], json['host'], json['port'], json['rack']);
  }
}

class TopicPartitionInfoDesc {
  TopicPartitionInfoDesc(this.partition, this.leader, this.replicas, this.isr);

  int partition;
  NodeDesc leader;
  List<NodeDesc> replicas = [];
  List<NodeDesc> isr = [];

  Map<String, Object> get asJson {
    return {
      'partition': partition,
      'leader': leader.asJson,
      'replicas': replicas.map((e) => e.asJson),
      'isr': isr.map((e) => e.asJson)
    };
  }

  static TopicPartitionInfoDesc fromJson(Map<String, dynamic> json) {
    if (json == null) {
      return null;
    }

    final List<NodeDesc> replicas = [];
    json['replicas'].forEach((e) => replicas.add(NodeDesc.fromJson(e)));
    final List<NodeDesc> isr = [];
    json['isr']?.forEach((e) => isr.add(NodeDesc.fromJson(e)));

    return TopicPartitionInfoDesc(
        json['partition'], NodeDesc.fromJson(json['leader']), replicas, isr);
  }
}

class TopicDesc {
  TopicDesc(
      this.name, this.isInternal, this.partitions, this.authorizedOperations);

  String name;
  bool isInternal;
  List<TopicPartitionInfoDesc> partitions = [];
  Set<String> authorizedOperations = {};

  Map<String, Object> get asJson {
    return {
      'name': name,
      'isInternal': isInternal,
      'partitions': partitions?.map((e) => e.asJson),
      'authorizedOperations': authorizedOperations
    };
  }

  static TopicDesc fromJson(Map<String, dynamic> json) {
    final List<TopicPartitionInfoDesc> partitions = [];
    json['partitions']
        .forEach((e) => partitions.add(TopicPartitionInfoDesc.fromJson(e)));
    final Set<String> acl = {};
    json['authorizedOperations'].forEach((e) => acl.add(e));

    return new TopicDesc(json['name'], json['isInternal'], partitions, acl);
  }
}

class MetricKey {
  MetricKey(this.name, this.group, this.description, this.tags);

  String name;
  String group;
  String description;
  Map<String, String> tags;

  Map<String, Object> get asJson {
    return {
      'name': name,
      'group': group,
      'description': description,
      'tags': tags
    };
  }

  static MetricKey fromJson(Map<String, dynamic> json) {
    Map<String, String> tags = {};
    json['tags'].forEach((k, v) => tags[k] = v.toString());
    return MetricKey(json['name'], json['group'], json['description'], tags);
  }
}

class MetricEntry {
  MetricEntry(this.metric, this.value);

  MetricKey metric;
  String value;

  Map<String, Object> get asJson {
    return {'metric': metric.asJson, 'value': value};
  }

  static MetricEntry fromJson(Map<String, dynamic> json) {
    final metric = json['metric'];
    return MetricEntry(MetricKey.fromJson(metric), json['value']);
  }
}

class ReadPositionAt {
  ReadPositionAt(this.topicPartition, this.at);

  TopicKey topicPartition;

  // one of 'earliest', 'latest' or a timestamp epoch int
  String at;

  Map<String, Object> get asJson {
    return {'topicPartition': topicPartition.asJson, 'at': at};
  }

  static ReadPositionAt fromJson(Map<String, dynamic> json) {
    return ReadPositionAt(
        TopicKey.fromJson(json['topicPartition']), json['at']);
  }
}

class ListOffsetsRequest {
  ListOffsetsRequest(this.topics, this.readUncommitted);

  List<ReadPositionAt> topics = [];
  bool readUncommitted;

  Map<String, Object> get asJson {
    return {
      'topics': topics.map((e) => e.asJson).toList(growable: false),
      'readUncommitted': readUncommitted
    };
  }

  static ListOffsetsRequest fromJson(Map<String, dynamic> json) {
    List<ReadPositionAt> positions = [];
    json['topics'].forEach((i) => positions.add(ReadPositionAt.fromJson(i)));
    return ListOffsetsRequest(positions, json['readUncommitted']);
  }
}

class OffsetInfo {
  OffsetInfo(this.offset, this.timestamp, this.leaderEpoch);

  int offset;
  int timestamp;
  int leaderEpoch = null;

  Map<String, Object> get asJson {
    return {
      'offset': offset,
      'timestamp': timestamp,
      'leaderEpoch': leaderEpoch
    };
  }

  static OffsetInfo fromJson(Map<String, dynamic> json) {
    return OffsetInfo(json['offset'], json['timestamp'], json['leaderEpoch']);
  }
}

class OffsetRange {
  OffsetRange(this.topic, this.partition, this.earliest, this.latest);

  String topic;
  int partition;
  OffsetInfo earliest;
  OffsetInfo latest;

  Map<String, Object> get asJson {
    return {
      'topic': topic,
      'partition': partition,
      'earliest': earliest.asJson,
      'latest': latest.asJson
    };
  }

  static OffsetRange fromJson(Map<String, dynamic> json) {
    return OffsetRange(
        json['topic'],
        json['partition'],
        OffsetInfo.fromJson(json['earliest']),
        OffsetInfo.fromJson(json['latest']));
  }
}

class CreateTopic {
  CreateTopic(
      this.name,
      this.numPartitions,
      this.replicationFactor
      );

  String name;
  int numPartitions;
  int replicationFactor;

  Map<String, Object> get asJson {
    return {
      'name': name,
      'numPartitions': numPartitions,
      'replicationFactor': replicationFactor
    };
  }

  static CreateTopic fromJson(Map<String, dynamic> json) {
    return CreateTopic(
        json['name'],
        json['numPartitions'],
        json['replicationFactor']);
  }
}