import 'package:dropdown_search/dropdown_search.dart';
import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/rest_client.dart';

import 'consumer_data_table.dart';
import 'content_table.dart';
import 'create_topic_dialog.dart';
import 'navigation_drawer.dart';
import 'partition_widget.dart';
import 'push_data_dialog.dart';

void main() => runApp(
      MaterialApp(
          builder: (context, child) =>
              SafeArea(child: new Material(color: Colors.white, child: child)),
          home: TopicWidget(() {})),
    );

class TopicWidget extends StatefulWidget {
  TopicWidget(this.onCreate, {Key key}) : super(key: key);
  OnCreate onCreate;
  static const path = '/topic';

  @override
  _TopicWidgetState createState() => _TopicWidgetState();
}

class _TopicWidgetState extends State<TopicWidget> {

  final GlobalKey<ScaffoldState> _scaffoldKey = new GlobalKey<ScaffoldState>();

  List<String> topics = [];
  String selectedTopic = '';

  @override
  void initState() {
    super.initState();
    RestClient.listTopics(true).then((value) => setState(() {
          topics = value;
        }));
  }

  @override
  Widget build(BuildContext context) {
    if (selectedTopic.isEmpty) {
      selectedTopic = (ModalRoute.of(context).settings.arguments ?? '');
    }

    return Scaffold(
      key: _scaffoldKey,
      appBar: AppBar(
        // title: Text('Kafka'),
        title: selectedTopic.isEmpty
            ? Text('Create or Choose a Kafka Topic')
            : Text('Topic $selectedTopic'),
        actions: [
          if (selectedTopic.isNotEmpty)
            IconButton(
                icon: const Icon(Icons.edit),
                tooltip: 'Reparition',
                onPressed: null),
          if (selectedTopic.isNotEmpty)
            IconButton(
                icon: const Icon(Icons.library_add_rounded),
                tooltip: 'Push Data',
                onPressed: () => PushDataDialog.show(context, _scaffoldKey, selectedTopic)),
          CreateTopicDialog.createButton(context, _scaffoldKey, widget.onCreate)
        ],
      ),
      drawer: NavigationDrawer(),
      body: Padding(
          padding: const EdgeInsets.all(8.0),
          child: topicWidget(
              selectedTopic)), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }

  Widget topicWidget(String topic) {
    return LimitedBox(
        maxWidth: 1000,
        maxHeight: 1000,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            topicDropDown(topic),
            if (topic.isNotEmpty) PartitionsForTopicWidget(topic),
            // if (topic.isNotEmpty) PeekDataWidget.forTopic(topic),
            if (topic.isNotEmpty) Row(
              children: [
                Container(
                    width : 1000,
                    height : 1000,
                    child: TopicContentWidget.forTopic(topic)),
              ],
            )
          ],
        ));
  }

  DropdownSearch<String> topicDropDown(String topic) {
    return DropdownSearch<String>(
        validator: (v) => v == null ? 'required field' : null,
        hint: 'Topic',
        mode: Mode.MENU,
        showSelectedItem: true,
        showSearchBox: true,
        itemAsString: (topic) => topic ?? '',
        compareFn: (r1, r2) => (topic ?? '') == (topic ?? ''),
        items: topics,
        label: 'Topic',
        showClearButton: false,
        onChanged: (topic) => setState(() {
              selectedTopic = topic;
            }),
        // popupItemDisabled: (String s) => s.startsWith('I'),
        selectedItem: selectedTopic,
        maxHeight: 450);
  }
}
