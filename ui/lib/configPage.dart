import 'package:flutter/material.dart';

import 'client/client.dart';
import 'client/model.dart';

class ConfigPage extends StatefulWidget {
  ConfigPage({Key key, this.title}) : super(key: key);
  final String title;

  @override
  _ConfigPageState createState() => _ConfigPageState();
}

class LoadedConfig {
  LoadedConfig(this.fileName, this.loadedContent, this.summary);
  String fileName;
  String loadedContent;
  ConfigSummary summary;
  @override
  String toString() {
    return "LoadedConfig(fileName: $fileName, summary: $summary)";
  }
}

class _ConfigPageState extends State<ConfigPage> {

  // our current config filename
  LoadedConfig _currentConfig = LoadedConfig("", "", ConfigSummary.empty());

  Future<LoadedConfig> defaultConfig() async {
    var lastSavedFileName = await Client.getLastSaved();
    if (lastSavedFileName == "") {
      final content = await Client.defaultConfig();
      return summaryFor("application.conf", content);
    } else {
      final content = await Client.get(lastSavedFileName);
      return summaryFor(lastSavedFileName, content);
    }
  }

  Future<LoadedConfig> summaryFor(String fileName, String content) async {
    if (content == "") {
      return LoadedConfig(fileName, content, ConfigSummary.empty());
    } else {

      final summary = await Client.configSummary(content);
      return LoadedConfig(fileName, content, summary);
    }
  }

  @override
  void initState() {
    super.initState();
    defaultConfig().then((value) {
      setState(() {
        print('Setting value to $value');
        _currentConfig = value;
      });
    });
  }

  @override
  Widget build(BuildContext context) {

    final runningButton = IconButton(
        iconSize: 32,
        tooltip: 'Open',
        icon: Icon(Icons.folder_open),
        color: Colors.red,
        onPressed: () {});

    return Scaffold(
      appBar: AppBar(title: Text('Config'), actions: [
        runningButton,
      ]),
      body: FutureBuilder(
          future: defaultConfig(),
          builder: (ctxt, snapshot) {
            if (snapshot.hasData && snapshot.data != null) {
              print('snapshot.data is ${snapshot.data}');
              return configSummaryWidget(ctxt, snapshot.data);
            } else {
              return Center(child: CircularProgressIndicator());
            }
          })
      ,
      floatingActionButton: FloatingActionButton(
        tooltip: 'Save',
        child: Icon(Icons.save),
      ), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }

  Widget configSummaryWidget(BuildContext context, LoadedConfig summary) {

    final runningButton = IconButton(
        iconSize: 32,
        tooltip: 'Running',
        icon: Icon(Icons.run_circle),
        color: Colors.red,
        onPressed: () {});

    return Scaffold(
      appBar: AppBar(title: Text(_currentConfig.fileName), actions: [
        runningButton,
      ],
        primary :false,
        automaticallyImplyLeading: false
      ),

      body: Center(
        // Center is a layout widget. It takes a single child and positions it
        // in the middle of the parent.
        child: Column(
          children: [
            Text("Brokers: ${this._currentConfig.summary.brokers}"),
            Text("Topic: ${this._currentConfig.summary.topic}"),
            Text("Key Type: ${this._currentConfig.summary.keyType}"),
            Text("Value Type: ${this._currentConfig.summary.valueType}"),
            Text("Mappings: ${this._currentConfig.summary.mappings}")
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        tooltip: 'Increment',
        child: Icon(Icons.add),
      ), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }
}
