import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/rest_client.dart';

import 'model.dart';

void main() => runApp(
      MaterialApp(
        builder: (context, child) =>
            SafeArea(child: new Material(color: Colors.white, child: child)),
        home: Scaffold(
          body: OffsetRangeWidget('topiceae5c8f8a3054d19a132cb3033031e49'),
        ),
      ),
    );

class OffsetRangeWidget extends StatefulWidget {
  OffsetRangeWidget(this.topic);

  String topic;

  @override
  _OffsetRangeWidgetState createState() => _OffsetRangeWidgetState();
}

class _OffsetRangeWidgetState extends State<OffsetRangeWidget> {
  List<OffsetRange> ranges = [];

  @override
  void initState() {
    super.initState();
    _fetchData(widget.topic);
  }

  void _fetchData(String topicParam) {
    if ((topicParam ?? '').isNotEmpty) {
      RestClient.offsetRangesForTopic(topicParam).then((value) => setState(() {
            ranges = value;
          }));
    }
  }

  @override
  Widget build(BuildContext context) {
    final String topic =
        widget?.topic ?? ModalRoute.of(context).settings.arguments;
    if ((widget?.topic ?? '').isEmpty) {
      _fetchData(topic);
    }
    return Container();
  }
}
