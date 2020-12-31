import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';

import 'metrics_widget.dart';
import 'topic_widget.dart';
import 'topics_widget.dart';

class NavigationDrawer extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Drawer(
        child: ListView(
          // Important: Remove any padding from the ListView.
          padding: EdgeInsets.zero,
          children: <Widget>[
            DrawerHeader(
              child: Text('Menu'),
              decoration: BoxDecoration(
                color: Theme.of(context).secondaryHeaderColor,
              ),
            ),
            allTopics(context),
            topic(context),
            metrics(context),
          ],
        ));
  }

  Align allTopics(BuildContext context) {
    return Align(
            alignment: Alignment.topLeft,
            child: FlatButton.icon(
                onPressed: () => onMenuChoice(context, TopicsWidget.path),
                icon: Icon(Icons.topic),
                label: Text('All Topics')),
          );
  }

  Align topic(BuildContext context) {
    return Align(
              alignment: Alignment.topLeft,
              child: FlatButton.icon(
                  onPressed: () => onMenuChoice(context, TopicWidget.path),
                  icon: Icon(Icons.data_usage),
                  label: Text('Topic')));
  }

  Align metrics(BuildContext context) {
    return Align(
              alignment: Alignment.topLeft,
              child: FlatButton.icon(
                  onPressed: () => onMenuChoice(context, MetricsWidget.path),
                  icon: Icon(Icons.analytics),
                  label: Text('Metrics')));
  }

  void onMenuChoice(BuildContext ctxt, String routeName) {
    Navigator.of(ctxt).pushNamed(routeName);
  }
}
