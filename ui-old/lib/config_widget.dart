import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';

import 'config_client.dart';

class ConfigWidget extends StatefulWidget {
  static const path = '/config';

  ConfigWidget({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _ConfigWidgetState createState() => _ConfigWidgetState();
}

class _ConfigWidgetState extends State<ConfigWidget> {
  static const inputStyle =
      TextStyle(fontSize: 24, fontWeight: FontWeight.normal);
  static const hintStyle =
      TextStyle(fontSize: 14, fontWeight: FontWeight.normal);
  static const errorStyle =
      TextStyle(fontSize: 12, fontWeight: FontWeight.bold);

  int _counter = 0;
  final _formKey = GlobalKey<FormState>();

  final _configNameFocusNode = FocusNode();
  final _configNameController = TextEditingController(text: "default");

  var configurationText = '';

  // final _configTextController = TextEditingController(text : "");

  void _incrementCounter() {
    setState(() {
      // This call to setState tells the Flutter framework that something has
      // changed in this State, which causes it to rerun the build method below
      // so that the display can reflect the updated values. If we changed
      // _counter without calling setState(), then the build method would not be
      // called again, and so nothing would appear to happen.
      _counter++;
    });
  }

  TextFormField configNameField() {
    return TextFormField(
      focusNode: _configNameFocusNode,
      controller: _configNameController,
      style: inputStyle,
      cursorWidth: 4,
      cursorColor: Colors.black87,
      decoration: const InputDecoration(
          hintText: 'The config key',
          labelText: 'Config:',
          labelStyle: inputStyle,
          hintStyle: hintStyle,
          errorStyle: errorStyle,
          errorMaxLines: 2),
      validator: (value) {
        if (value.contains("..")) {
          return 'Configuration names have to be alphanumeric';
        }
        return null;
      },
    );
  }

  @override
  void initState() {
    super.initState();
    ConfigClient.defaultConfig().then((value) => setState(() {
          configurationText = value;
          // configurationText = JsonEncoder.withIndent('  ').convert(value);
        }));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Padding(
        padding: const EdgeInsets.all(8.0),
        child: buildForm(context),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: configurationText.isEmpty ? null : _incrementCounter,
        tooltip: 'Ok',
        child: Icon(Icons.forward),
      ), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }

  Widget buildForm(BuildContext context) {
    return Form(
        key: _formKey,
        child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Flexible(child: configNameField()),
              Expanded(
                  child: new SingleChildScrollView(
                      scrollDirection: Axis.vertical, //.horizontal
                      child: TextFormField(
                        maxLines: 1200,
                        decoration:
                            InputDecoration(labelText: configurationText),
                      ))),
            ]));
  }

  Widget buildOld(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Text(
            'Push count:',
          ),
          Padding(
            padding: const EdgeInsets.all(8.0),
            child: RaisedButton(
                child: Text('make request'), onPressed: onMakeRequest),
          ),
          Text(
            '$_counter',
            style: Theme.of(context).textTheme.headline4,
          ),
        ],
      ),
    );
  }

  void onMakeRequest() {
    ConfigClient.defaultConfig();
  }

  @override
  void dispose() {
    _configNameFocusNode.dispose();
    super.dispose();
  }
}
