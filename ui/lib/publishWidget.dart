import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';

import 'Consts.dart';
import 'client/configSummary.dart';
import 'client/postRecord.dart';
import 'client/topics.dart';
import 'fieldWidget.dart';

void main() {
  runApp(MaterialApp(
      title: 'Franz',
      theme: ThemeData(brightness: Brightness.light),
      darkTheme: ThemeData(brightness: Brightness.dark),
      themeMode: ThemeMode.dark,
      debugShowCheckedModeBanner: false,
      home: PublishWidget("foo", ConfigSummary(
        "bar", {}, [] , "string" , "string" , "string" , "string"), "")));
}

class PublishWidget extends StatefulWidget {
  PublishWidget(this.title, this.summary, this.configuration, {Key key})
      : super(key: key);
  final String title;
  final ConfigSummary summary;
  final String configuration;

  @override
  _PublishWidgetState createState() => _PublishWidgetState();
}

class _PublishWidgetState extends State<PublishWidget> {
  final _formKey = GlobalKey<FormState>();

  int _repeat = 1;
  ConfigSummary _summary;
  String _partitionOverride = "";

  final _valueTextController = TextEditingController();
  final _keyTextController = TextEditingController();
  Topics _topics = Topics([], [], []);

  @override
  void dispose() {
    super.dispose();
    _valueTextController.dispose();
  }

  @override
  void initState() {
    super.initState();
    _summary = widget.summary;
    _keyTextController.text = "record-{{i}}";
    _valueTextController.text = '''{
      "data" : "value-{{i}}",
      "nested" : {
         "numbers" : [1,2,3]
       },
      "flag" :  true
    }''';

    _reload(false);
  }

  void _reload(bool force) {
    Topics.get().then((found) {
      setState(() {
        _topics = found;
      });
    });
  }

    @override
  Widget build(BuildContext context) {
    return SafeArea(
        child: Scaffold(
      appBar: AppBar(
          title: Align(alignment: Alignment.topLeft, child: Text("Publish to '${this.widget.summary.topic}'")),
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
          _summary.topic,
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
              Flexible(
                  child: FieldWidget(
                      "Repeat:",
                      "The number of records to submit",
                      "1",
                          (value) => _repeat = int.parse(value),
                      validateNumber)),

              Flexible(
                // see https://stackoverflow.com/questions/49577781/how-to-create-number-input-field-in-flutter
                //keyboardType: TextInputType.number
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
                    child: Container(
                      width: 200,
                      height: 300,
                      child: Column(
                        children: [
                          Text("Key:"),
                          Text("${_summary.keyType}"),
                          OutlinedButton.icon(onPressed: _onReloadKey, icon: Icon(Icons.refresh), label: Text("(refresh)")),
                        ],
                      ),
                    ),
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

  Widget typeWidget(
      String label, String currentValue, bool isAvro, OnUpdate onUpdate) {
    final List<String> values = [...Consts.SupportedTypes];
    if (!values.contains(currentValue)) {
      values.insert(values.length, currentValue);
    }

    final child = isAvro
        ? Text("Avro")
        : DropdownButton<String>(
      items: values.map((String value) {
        return DropdownMenuItem<String>(
          value: value,
          child: Text(value),
        );
      }).toList(),
      value: currentValue,
      onChanged: onUpdate,
    );

    return Padding(
        padding: const EdgeInsets.all(8.0),
        child: Container(
          width: 600,
          child: Row(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(0, 0, 8.0, 0),
                child: Text("$label :"),
              ),
              child,
            ],
          ),
        ));
  }
  void _onReloadKey() {

  }
}
