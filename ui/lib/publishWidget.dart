import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';

import 'client/postRecord.dart';
import 'fieldWidget.dart';

void main() {
  runApp(MaterialApp(
      title: 'Franz',
      theme: ThemeData(brightness: Brightness.light),
      darkTheme: ThemeData(brightness: Brightness.dark),
      themeMode: ThemeMode.dark,
      debugShowCheckedModeBanner: false,
      home: PublishWidget("foo", "bar", "")));
}

class PublishWidget extends StatefulWidget {
  PublishWidget(this.title, this.topic, this.configuration, {Key key})
      : super(key: key);
  final String title;
  final String topic;
  final String configuration;

  @override
  _PublishWidgetState createState() => _PublishWidgetState();
}

class _PublishWidgetState extends State<PublishWidget> {
  final _formKey = GlobalKey<FormState>();

  int _repeat = 1;
  String _topicOverride;
  String _partitionOverride = "";

  final _valueTextController = TextEditingController();
  final _keyTextController = TextEditingController();

  @override
  void dispose() {
    super.dispose();
    _valueTextController.dispose();
  }

  @override
  void initState() {
    super.initState();
    _topicOverride = widget.topic;
    _keyTextController.text = "record-{{i}}";
    _valueTextController.text = '''{
      "data" : "value-{{i}}",
      "nested" : {
         "numbers" : [1,2,3]
       },
      "flag" :  true
    }''';
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
        child: Scaffold(
      appBar: AppBar(
          title: Align(alignment: Alignment.topLeft, child: Text("Publish")),
          backgroundColor: Colors.grey[800],
          actions: []),
      floatingActionButton: FloatingActionButton(
        child: Icon(Icons.publish),
        onPressed: onPublish,
      ),
      body: publishFormWidget(),
    ));
  }

  PostRecord asPostRecord(dynamic key, dynamic value) {
    if (_formKey.currentState.validate()) {
      _formKey.currentState.save();
      return PostRecord(
          value,
          widget.configuration,
          key,
          _repeat,
          isNumber(_partitionOverride) ? int.parse(_partitionOverride) : null,
          _topicOverride,
          Map());
    } else {
      return null;
    }
  }

  void onPublish() async {
    final dontShootMe = ScaffoldMessenger.of(context);
    dynamic value;
    try {
      value = jsonDecode(_valueTextController.text);
    } catch (e) {
      dontShootMe
          .showSnackBar(SnackBar(content: Text("Invalid value jason: $e")));
      return;
    }
    dynamic key;
    try {
      key = jsonDecode(_keyTextController.text);
    } catch (e1) {
      try {
        key = jsonDecode("\"${_keyTextController.text}\"");
      } catch (e) {
        dontShootMe
            .showSnackBar(SnackBar(content: Text("Invalid key jason: $e")));
        return;
      }
    }

    final post = asPostRecord(key, value);
    if (post != null) {
      try {
        final numPublished = await post.publish();
        dontShootMe.showSnackBar(SnackBar(
            content:
                Text("Published ${numPublished} to ${post.topicOverride}")));
      } catch (e) {
        dontShootMe.showSnackBar(
            SnackBar(content: Text("Failed to publish $post: $e")));
      }
    }
  }

  Widget publishFormWidget() {
    return LayoutBuilder(builder: (ctxt, BoxConstraints constraints) {
      final heightAvailableForCode = constraints.maxHeight - 400;
      final suggestedRows = 4 + (heightAvailableForCode ~/ 20);
      final numRows = max(4, min(40, suggestedRows));
      final valueHeight = max(100, constraints.maxHeight - 315);
      return Container(
        // constraints: BoxConstraints(maxHeight: 200),
        margin: EdgeInsets.all(16),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.start,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text("w:${constraints.maxWidth}"),
              Text("h:${constraints.maxHeight}"),
              Flexible(
                  child: FieldWidget(
                      "Repeat:",
                      "The number of records to submit",
                      "1",
                          (value) => _repeat = int.parse(value),
                      validateNumber)),
              Flexible(
                  child: FieldWidget(
                      "Topic:", "The topic to publish to", _topicOverride,
                          (value) {
                        _topicOverride = value;
                      }, textOk)),
              Flexible(
                  child: FieldWidget(
                      "Partition:",
                      "A specific partition to publish to, if specified",
                      "",
                          (value) => isNumber(value)
                          ? _partitionOverride = value
                          : _partitionOverride = "",
                      textOk)),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(6, 16, 8, 0),
                    child: Text("Key:"),
                  ),
                  Container(
                    padding: const EdgeInsets.all(8),
                    constraints: BoxConstraints(maxWidth: 400, maxHeight: 200),
                    child: TextField(
                      controller: _keyTextController,
                      maxLines: 4,
                    ),
                  ),
                ],
              ),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(6, 16, 8, 0),
                    child: Text("Value:"),
                  ),
                  Container(
                    // decoration: BoxDecoration(color: Colors.blueGrey),
                    padding: const EdgeInsets.all(8),
                    constraints: BoxConstraints(maxWidth: 400, maxHeight: valueHeight),
                    child: TextField(
                      controller: _valueTextController,
                      maxLines: numRows,
                    ),
                  ),
                ],
              )
            ],
          ),
        ),
      );

    });
  }
}
