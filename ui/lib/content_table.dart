import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_pagewise/flutter_pagewise.dart';
import 'package:tuple/tuple.dart';

import 'model.dart';
import 'rest_client.dart';

void main() => runApp(
      MaterialApp(
        builder: (context, child) =>
            SafeArea(child: new Material(color: Colors.white, child: child)),
        home: Scaffold(
          // body: ConsumerDataWidget('topiceae5c8f8a3054d19a132cb3033031e49'),
          body: TopicContentWidget.forTopic('dave'),
        ),
      ),
    );

// keeps track of the data in the table and loads on demand.
// all data is returned in a Future, whether it's cached or not
typedef LoadBatch<T> = Future<List<T>> Function(int, T);

class TopicContentWidget extends StatefulWidget {
  @override
  _TopicContentWidgetState createState() => _TopicContentWidgetState();

  TopicContentWidget(this.topic);

  String topic;

  static Widget forTopic(String topic) {
    return Column(
      children: [
        Text('Data for $topic:'),
        TopicContentWidget(topic),
      ],
    );
  }
}

class _TopicContentWidgetState extends State<TopicContentWidget> {
  @override
  Widget build(BuildContext context) {
    return Expanded(child: BigList(widget.topic));
  }
}

class BigList extends StatefulWidget {
  BigList(this.topic);

  String topic;

  @override
  _BigListState createState() => _BigListState();
}

class _BigListState extends State<BigList> {
  // set to a non-empty set to filter data on partitions
  Set<int> partitions = {};
  int maxOffset = 0;
  int minOffset = 0;
  List<OffsetRange> ranges = [];

  static const Limit = 100;

  Map<int, List<Record>> recordsByRow = {};

  Future<List<Record>> fetchData(int row) async {
    // print('row $row previous is $previous');
    final previousIndex = row - 1;
    final Record previous = recordsByRow[previousIndex]?.last;
    var offset = 0;
    if (previous != null) {
      offset = previous.offset + 1;
    }
    final request = PeekRequest({widget.topic}, offset, Limit, partitions);

    final rows = await RestClient.peek(request);
    recordsByRow[row] = rows;
    return rows;
  }

  static Tuple2<int, int> minMaxOffset(List<OffsetRange> ranges) {
    int minV = -1;
    int maxV = -1;
    ranges.forEach((element) {
      if (minV == -1) {
        minV = element.earliest.offset;
        maxV = element.latest.offset;
      } else {
        minV = min(minV, element.earliest.offset);
        maxV = max(maxV, element.latest.offset);
      }
    });
    return Tuple2(minV, maxV);
  }

  @override
  Widget build(BuildContext context) {
    return Container(width: 1000, height: 500, child: buildList());
  }

  @override
  void initState() {
    super.initState();
    refresh();
  }

  void refresh() {
    RestClient.offsetRangesForTopic(widget.topic).then((value) {
      final tuple = minMaxOffset(value);
      setState(() {
        ranges = value;
        print('min/max: $tuple w/ $ranges');
        minOffset = tuple.item1;
        maxOffset = tuple.item2;
      });
    });
  }

  Widget buildList() {
    print('maxOffset is $maxOffset');
    if (maxOffset == 0) {
      return Text('no data');
    }
    return PagewiseListView(
        pageSize: Limit,
        itemBuilder: _itemBuilder,
        pageFuture: (pageIndex) => fetchData(pageIndex * Limit));
  }

  Widget _itemBuilder(context, Record entry, int index) {
    return Column(
      children: <Widget>[
        ListTile(
          leading: Icon(
            Icons.person,
            color: Colors.brown[200],
          ),
          title: Text('${entry.key} ${entry.partition}:${entry.offset}'),
          subtitle: Text('${entry.value}'),
        ),
        Divider()
      ],
    );
  }
}
