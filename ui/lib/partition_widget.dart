import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/repartition_dialog.dart';
import 'package:kafkaui/rest_client.dart';

import 'model.dart';

void main() => runApp(
      MaterialApp(
        builder: (context, child) =>
            SafeArea(child: new Material(color: Colors.white, child: child)),
        home: Scaffold(
          body: PartitionsForTopicWidget(
              'twotopic802a6a186fed4ffd83d65ca5abff067e'),
        ),
      ),
    );

class PartitionsForTopicWidget extends StatefulWidget {
  PartitionsForTopicWidget(this.topic);

  String topic;

  @override
  _PartitionsForTopicWidgetState createState() =>
      _PartitionsForTopicWidgetState();
}

class _PartitionsForTopicWidgetState extends State<PartitionsForTopicWidget> {
  TopicDesc topicDesc = null;

  @override
  void initState() {
    super.initState();
    fetchData();
  }

  @override
  Widget build(BuildContext context) {
    if (topicDesc == null || topicDesc.partitions == null) {
      return Center(child: CircularProgressIndicator());
    } else {
      if (topicDesc.name != widget.topic) {
        fetchData();
        return Center(child: CircularProgressIndicator());
      } else {
        return Container(
            decoration: BoxDecoration(border: Border.all(color: Colors.blue)),
            child: makeCard(context));
      }
    }
  }

  Widget makeCard(BuildContext context) {
    final kids = topicDesc.partitions
        .map((e) => TopicPartitionInfoDescWidget(e))
        .toList();

    String partitionTitle = '${kids.length} Partitions';
    if (kids.length == 1) {
      partitionTitle = 'One Partition';
    }
    return Card(
        clipBehavior: Clip.antiAlias,
        shadowColor: Colors.grey,
        elevation: 4,
        child: ExpansionTile(
          title: ListTile(
            leading: Icon(Icons.pie_chart),
            title: Text(partitionTitle),
          ),
          children: [Wrap(spacing: 20, runSpacing: 20, children: kids)],
        ));
  }

  void onRepartition(BuildContext ctxt, int currentPartitions) {
    RepartitionDialog.onRepartition(ctxt, widget.topic, currentPartitions);
  }

  void fetchData() {
    RestClient.partitionsForTopic(widget.topic).then((value) => setState(() {
          topicDesc = value;
        }));
  }
}

class TopicPartitionInfoDescWidget extends StatelessWidget {
  TopicPartitionInfoDescWidget(this.info);

  final TopicPartitionInfoDesc info;

  @override
  Widget build(BuildContext context) {
    String leaderCoords = 'not set';
    if (info != null && info.leader != null) {
      leaderCoords = 'at ${info.leader.host}:${info.leader.port}';
    }
    return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Padding(
        padding: const EdgeInsets.all(8.0),
        child: Text('Partition ${info.partition}:'),
      ),
      Padding(
        padding: const EdgeInsets.all(8.0),
        child: Text(
          'Leader: ${info?.leader?.id} $leaderCoords',
          style: TextStyle(color: Colors.black.withOpacity(0.6)),
        ),
      ),
      LimitedBox(
        maxWidth: 400,
        maxHeight: 500,
        child: SingleChildScrollView(
            child: Container(
                // decoration: BoxDecoration(border: Border.all(color: Colors.red)),
                child: replicasList())),
      )
    ]);
  }

  Widget replicasList() {
    return ListView.builder(
        itemCount: info.replicas.length,
        shrinkWrap: true,
        padding: const EdgeInsets.all(8),
        itemBuilder: (ctxt, index) {
          return nodeWidget(
              'Replica', info.replicas[index], index, Colors.grey[100]);
        });
  }

  ListView isrList() {
    return ListView.builder(
        itemCount: info.isr.length,
        shrinkWrap: true,
        padding: const EdgeInsets.all(8),
        itemBuilder: (ctxt, index) {
          return nodeWidget('ISR', info.isr[index], index, Colors.amber);
        });
  }

  Container nodeWidget(String label, NodeDesc isr, int index, Color color) {
    var rack = '';
    if (isr.rack != null) {
      rack = ' on rack "${isr.rack}"';
    }
    var location = '';
    if (isr.port >= 0) {
      location = 'on ${isr.host}:${isr.port}';
    }
    return Container(
        height: 40,
        color: color,
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Text('$label #${index + 1} [id:${isr.id}] $location$rack',
              style: TextStyle(color: Colors.black.withOpacity(0.6))),
        ));
  }
}
