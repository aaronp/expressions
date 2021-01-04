import 'package:flutter/material.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:kafkaui/topics_widget.dart';

import 'metrics_widget.dart';
import 'navigation_drawer.dart';
import 'topic_widget.dart';

class HomeWidget extends StatefulWidget {
  static const path = '/home';

  @override
  _HomeWidgetState createState() => _HomeWidgetState();
}

class _HomeWidgetState extends State<HomeWidget> {

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Kafka'),
      ),
      drawer: NavigationDrawer(),
      body: Padding(
        padding: const EdgeInsets.all(8.0),
        child: Text('Hello World!'),
      ),
    );
  }

}
