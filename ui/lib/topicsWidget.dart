import 'package:dropdown_formfield/dropdown_formfield.dart';
import 'package:flutter/material.dart';

import 'client/topics.dart';

void main() => runApp(MaterialApp(
      home: TopicTestPage(),
    ));

typedef OnTopicSelected = void Function(String value);

class TopicsWidget extends StatefulWidget {
  TopicsWidget({this.topics, this.onSelected, this.selectedTopic, Key key}) : super(key: key);

  OnTopicSelected onSelected;
  Topics topics;
  String selectedTopic;

  @override
  State<TopicsWidget> createState() => _TopicsWidgetState();
}

class _TopicsWidgetState extends State<TopicsWidget> {
  @override
  Widget build(BuildContext context) {
    return Container(
      width: 400,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: Text("Topic (${this.widget.selectedTopic}):"),
          ),
          Container(
            width: 200,
            child: Autocomplete<String>(
              displayStringForOption: RawAutocomplete.defaultStringForOption,
              optionsMaxHeight: 400,
              initialValue: this.widget.selectedTopic == null ? null : TextEditingValue(text : this.widget.selectedTopic),
              optionsBuilder: (TextEditingValue textEditingValue) {
                if (textEditingValue.text.trim() == '') {
                  return widget.topics.all;
                }
                return widget.topics.all.where((String topic) {
                  return topic.contains(textEditingValue.text.toLowerCase());
                });
              },
              onSelected: widget.onSelected,
            ),
          ),
        ],
      ),
    );
  }
}

class TopicTestPage extends StatefulWidget {
  @override
  _TopicTestPageState createState() => _TopicTestPageState();
}

class _TopicTestPageState extends State<TopicTestPage> {
  String _myActivity = "";
  String _myActivityResult = "";
  final formKey = new GlobalKey<FormState>();
  Topics _topics = Topics([], [], []);

  @override
  void initState() {
    super.initState();
    _myActivity = '';
    _myActivityResult = '';
    _reload(false);
  }

  void _reload(bool force) {
    Topics.get().then((found) {
      setState(() {
        _topics = found;
      });
    });
  }

  _saveForm() {
    var form = formKey.currentState;
    if (form != null) {
      if (form.validate()) {
        form.save();
        setState(() {
          _myActivityResult = _myActivity;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Topics Example'),
      ),
      body: Center(
        child: Form(
          key: formKey,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.start,
            children: <Widget>[
              TopicsWidget(
                  topics: _topics,
                  onSelected: (selection) => print("GOT SELECTION $selection")),
              Container(
                padding: EdgeInsets.all(8),
                child: RaisedButton(
                  child: Text('Save'),
                  onPressed: _saveForm,
                ),
              ),
              Container(
                padding: EdgeInsets.all(16),
                child: Text(_myActivityResult),
              )
            ],
          ),
        ),
      ),
    );
  }
}
