import 'package:flutter/material.dart';

import 'client/mappingEntry.dart';

class EditTopicMappingWidget extends StatefulWidget {
  EditTopicMappingWidget(this.entry);

  MappingEntry entry;
  @override
  _EditTopicMappingWidgetState createState() => _EditTopicMappingWidgetState();

}

class _EditTopicMappingWidgetState extends State<EditTopicMappingWidget> {
  final _formKey = GlobalKey<FormState>();
  MappingEntry entry = MappingEntry("", "");
  final _fileNameController = TextEditingController();
  @override
  void dispose() {
    super.dispose();
    _fileNameController.dispose();
  }

  @override
  void initState() {
    super.initState();
    entry = this.widget.entry;
  }

  @override
  Widget build(BuildContext context) {
    // final MappingEntry args = ModalRoute.of(context).settings.arguments;
    return SafeArea(child: Scaffold(
        appBar: AppBar(
            title: Text("Topic: ${entry.topic}"),
            backgroundColor: Colors.grey[800],
            actions: [
            ]),
        body: Center(
          // Center is a layout widget. It takes a single child and positions it
          // in the middle of the parent.
          child: Column(
            mainAxisAlignment: MainAxisAlignment.start,
            mainAxisSize: MainAxisSize.max,
            children: [
            ],
          ),
        ) // This trailing comma makes auto-formatting nicer for build methods.
    ));
  }
}
