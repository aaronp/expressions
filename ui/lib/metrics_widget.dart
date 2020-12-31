import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:kafkaui/rest_client.dart';

import 'model.dart';

void main() => runApp(
      MaterialApp(
        builder: (context, child) =>
            SafeArea(child: new Material(color: Colors.white, child: child)),
        home: Scaffold(
          body: MetricsWidget(),
        ),
      ),
    );

class MetricsWidget extends StatefulWidget {
  static const path = '/metrics';
  @override
  _MetricsWidgetState createState() => _MetricsWidgetState();
}

class _MetricsWidgetState extends State<MetricsWidget> {
  Map<String, List<MetricEntry>> metrics = {};

  @override
  void initState() {
    super.initState();
    RestClient.metrics().then((value) => setState(() {
          metrics = value;
        }));
  }

  @override
  Widget build(BuildContext context) {
    List<Widget> children = [];
    metrics.forEach((key, value) {
      final group = Row(children: [metricTable(key, value)]);
      // children.add(group);
      // children.add(Row(children : [Text('$key')]));
      children.add(metricTable(key, value));
    });
    return ListView(children: children);
  }

  Widget metricTable(String group, List<MetricEntry> metrics) {
    final List<Widget> kids = [];
    metrics.forEach((element) {
      kids.add(metricRow(element));
    });

    // return ListView(children : kids);
    return Card(
        clipBehavior: Clip.antiAlias,
        shadowColor: Colors.grey,
        elevation: 10,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: [
            ListTile(
              leading: Icon(FontAwesomeIcons.bacteria),
              title: Text(group),
              subtitle: Text(
                '${metrics.length} Metrics',
                style: TextStyle(color: Colors.black.withOpacity(0.6)),
              ),
            ),
            Wrap(spacing: 10, runSpacing: 10, children: kids)
          ],
        ));
  }

  Widget metricWidget(MetricEntry metric) {
    List<Widget> tags = tagWidgets(metric);
    return Card(
        clipBehavior: Clip.antiAlias,
        shadowColor: Colors.blue,
        elevation: 10,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: [
            ListTile(
              //leading: Icon(FontAwesomeIcons.asymmetrik),
              title: Text(metric.metric.name),
              subtitle: Text(
                metric.metric.description,
                style: TextStyle(color: Colors.black.withOpacity(0.6)),
              ),
            ),
            Wrap(children: tags),
            Text(
              metric.value,
              style: TextStyle(
                  color: Colors.green,
                  fontSize: 18,
                  fontWeight: FontWeight.bold),
            )
          ],
        ));
  }

  Widget metricRow(MetricEntry metric) {
    List<Widget> tags = tagWidgets(metric);
    return Row(
      mainAxisAlignment: MainAxisAlignment.start,
      children: [
        Container(
            width: 300,
            child: Align(
                alignment: Alignment.topRight,
                child: Text('${metric.metric.name}:'))),
        Tooltip(
          message: metric.metric.description,
          child: Icon(Icons.info_outline_rounded),
        ),
        Container(
            width: 250,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(8.0, 0, 0, 0),
              child: Text(
                metric.value,
                style: TextStyle(
                    color: Colors.deepOrange,
                    fontSize: 18,
                    fontWeight: FontWeight.bold),
              ),
            )),
        Wrap(children: tags),
      ],
    );
  }

  List<Widget> tagWidgets(MetricEntry metric) {
    final tags = <Widget>[];
    metric.metric.tags.forEach((key, value) {
      final chip = Chip(
          // avatar: CircleAvatar(backgroundColor: Colors.grey.shade800, child: Text(key)),
          label: Text('$key : $value'));
      tags.add(Padding(
        padding: const EdgeInsets.fromLTRB(8.0, 0, 8, 0),
        child: chip,
      ));
    });
    return tags;
  }
}
