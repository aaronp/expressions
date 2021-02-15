import 'dart:math';

import 'package:flutter/material.dart';
import 'package:ui/editConfigWidget.dart';
import 'package:ui/editTopicMapping.dart';
import 'package:ui/publishWidget.dart';
import 'package:ui/runningConsumersWidget.dart';

import 'client/configClient.dart';
import 'client/diskClient.dart';
import 'client/configSummary.dart';
import 'client/mappingEntry.dart';

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

  bool isEmpty() {
    return summary.isEmpty();
  }

  @override
  String toString() {
    return "LoadedConfig(fileName: $fileName, summary: $summary)";
  }
}

class _ConfigPageState extends State<ConfigPage> {
  // our current config filename
  LoadedConfig _originalConfig = LoadedConfig("", "", ConfigSummary.empty());
  LoadedConfig _currentConfig = LoadedConfig("", "", ConfigSummary.empty());

  Future<LoadedConfig> defaultConfig() async {
    var lastSavedFileName = await DiskClient.getLastSaved();
    if (lastSavedFileName == "") {
      final content = await ConfigClient.defaultConfig();
      return summaryFor("application.conf", content);
    } else {
      final content = await DiskClient.get(lastSavedFileName);
      return summaryFor(lastSavedFileName, content);
    }
  }

  Future<LoadedConfig> summaryFor(String fileName, String content) async {
    if (content == "") {
      return LoadedConfig(fileName, content, ConfigSummary.empty());
    } else {
      final summary = await ConfigClient.configSummary(content);
      return LoadedConfig(fileName, content, summary);
    }
  }

  @override
  void initState() {
    super.initState();
    _reload();
  }

  void _reload() {
    defaultConfig().then((value) {
      setState(() {
        _originalConfig = value;
        _currentConfig = value;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_currentConfig.isEmpty()) {
      _reload();
    }

    final runningButton = IconButton(
        iconSize: 32,
        tooltip: 'Running',
        icon: Icon(Icons.list),
        color: Colors.red,
        onPressed: () => onListRunning(context));

    return SafeArea(
        child: Scaffold(
            appBar: AppBar(
                title: Align(alignment : Alignment.topLeft, child : Text('Configuration', textAlign: TextAlign.start)),
                actions: [runningButton]),
            body: configSummaryWidget(context) // This trailing comma makes auto-formatting nicer for build methods.
        ));
  }

  Widget configColumn(BuildContext ctxt) {
    return Container(
      // decoration: BoxDecoration(color: Colors.red),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.max,
        children: [
          configEntry("Brokers",
              _currentConfig.summary.brokersAsString()),
          configEntry("Topic", _currentConfig.summary.topic),
          configEntry("Key Type", _currentConfig.summary.keyType),
          configEntry(
              "Value Type", _currentConfig.summary.valueType),
          Container(
            height: 400.0,
            alignment: Alignment.topLeft,
            child: mappingsWidget(ctxt),
          ),
          buttonBar(ctxt)
        ],
      ),
    );
  }

  Widget workingColumn(BuildContext ctxt) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                configEntry("Brokers",
                    _currentConfig.summary.brokersAsString()),
                configEntry("Topic", _currentConfig.summary.topic),
                configEntry("Key Type", _currentConfig.summary.keyType),
                configEntry("Value Type", _currentConfig.summary.valueType),
                Container(
                  height: 420.0,
                  alignment: Alignment.topLeft,
                  child: mappingsWidget(ctxt),
                ),
                buttonBar(ctxt),
              ],
            );
  }


  Widget configSummaryWidget(BuildContext ctxt) {
    final openButton = IconButton(
        iconSize: 32,
        tooltip: 'Open',
        icon: Icon(Icons.folder_open),
        color: Colors.red,
        onPressed: () {});

    final editButton = IconButton(
        iconSize: 32,
        tooltip: 'Config',
        icon: Icon(Icons.settings),
        color: Colors.red,
        onPressed: () => onEditConfig(ctxt));

    return Scaffold(
        appBar: AppBar(
            title: Text(_currentConfig.fileName),
            backgroundColor: Colors.grey[800],
            actions: [
              openButton,
              editButton,
            ],
            primary: false,
            automaticallyImplyLeading: false),
        body: LayoutBuilder(
          builder: (BuildContext context, BoxConstraints viewportConstraints) {
            return SingleChildScrollView(
                child: ConstrainedBox(
                    constraints: BoxConstraints(minHeight: viewportConstraints.maxHeight),
                    child: IntrinsicHeight(child : configColumn(ctxt))));
          },
        ));
  }


  static const labelStyle = TextStyle(
    fontSize: 14.0,
    fontWeight: FontWeight.bold,
    color: Colors.white,
  );
  static const mappingStyle = TextStyle(
    fontSize: 18.0,
    fontWeight: FontWeight.bold,
    color: Colors.white,
  );

  Widget configEntry(String label, String value) {
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Container(
          width: 800,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.start,
            children: [
              Container(
                  width: 100,
                  child: Text("$label :", style: labelStyle),
                  alignment: AlignmentDirectional.topEnd),
              Text(value),
            ],
          )),
    );
  }

  Widget mappingsWidget(BuildContext ctxt) {
    return Column(
        mainAxisAlignment: MainAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            // width: 200,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(8.0, 28.0, 8.0, 8.0),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(8.0, 8.0, 8.0, 8.0),
                      child: Text("Topic Mappings:", style: mappingStyle),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(8.0, 8.0, 18.0, 8.0),
                      child: OutlinedButton.icon(
                          onPressed: () => onAddMapping(ctxt),
                          icon: Icon(Icons.add, color: Colors.white),
                          style: ButtonStyle(
                              backgroundColor:
                              MaterialStateProperty.all<Color>(
                                  Colors.grey[700])),
                          label: Text('Add')),
                    )
                  ],
                ),
              ),
              alignment: AlignmentDirectional.topStart),
          mappingsList(ctxt),
        ]);
  }

  Widget buttonBar(BuildContext ctxt) {
    final consumeButton = ElevatedButton.icon(
      icon: Icon(Icons.read_more_outlined, color: Colors.white),
      label: Text('Consume'),
      style: ButtonStyle(
          backgroundColor: MaterialStateProperty.all<Color>(Colors.grey[700])),
      onPressed: () {
        onConsume(ctxt);
      },
    );

    final publishButton = ElevatedButton.icon(
      icon: Icon(Icons.publish, color: Colors.white),
      label: Text('Publish'),
      style: ButtonStyle(
          backgroundColor: MaterialStateProperty.all<Color>(Colors.grey[700])),
      onPressed: () {
        onPublish(ctxt);
      },
    );

    return ButtonBar(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[publishButton, consumeButton]);
  }

  static String _unquote(String input) {
    final trimmed = input.trim();
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length - 2);
    }
    return trimmed;
  }

  void onListRunning(BuildContext ctxt) =>
      _push(ctxt, RunningConsumersWidget());

  void onPublish(BuildContext ctxt) => _push(ctxt, PublishWidget());

  void onEditConfig(BuildContext ctxt) async {
    final editedConfig = await _push(ctxt, EditConfigWidget(_currentConfig.loadedContent));
    print("editedConfig is ");
    print(editedConfig);
    final newSummary = await summaryFor(_currentConfig.fileName, editedConfig.toString());
    setState(() {
      _currentConfig = newSummary;
    });
  }

  void onConsume(BuildContext ctxt) => _push(ctxt, PublishWidget());

  void onEditMapping(BuildContext ctxt, MappingEntry entry) =>
      _push(ctxt, EditTopicMappingWidget(entry));

  void onAddMapping(BuildContext ctxt) =>
      _push(ctxt, EditTopicMappingWidget(MappingEntry("", "")));

  void onRemoveMapping(BuildContext ctxt, String key) {
    setState(() {
      _currentConfig.summary.mappings.remove(key);
    });
  }

  Future<Object> _push(BuildContext ctxt, Widget page) async {
    return await Navigator.push(ctxt, MaterialPageRoute(builder: (context) => page));
  }

  Widget mappingsList(BuildContext ctxt) {
    final mappingList = <Widget>[];
    _currentConfig.summary.mappings.forEach((quotedKey, value) {
      final key = _unquote(quotedKey);
      final path = value.join("/");

      print('Adding mapping ($key) $quotedKey');

      final editButton = IconButton(
          onPressed: () => onEditMapping(ctxt, MappingEntry(key, path)),
          icon: Icon(Icons.edit, color: Colors.white));

      final deleteButton = IconButton(
          onPressed: () => onRemoveMapping(ctxt, quotedKey),
          icon: Icon(
            Icons.delete,
            color: Colors.white,
          ));

      final mappingButtons =
          Row(children: [editButton, Text('$key ($path)'), deleteButton]);

      final entry = ListTile(
        dense: true,
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 26.0, vertical: 0.0),
        //https://fonts.google.com/specimen/Playfair+Display?category=Serif,Sans+Serif,Display,Monospace
        title: mappingButtons,
      );
      mappingList.add(entry);
    });

    return LimitedBox(
        maxHeight: 300,
        child: ListView.separated(
            itemCount: _currentConfig.summary.mappings.length,
            shrinkWrap: false,
            padding: EdgeInsets.zero,
            separatorBuilder: (BuildContext context, int index) {
              return Divider();
            },
            itemBuilder: (_, index) => mappingList[index]));
  }

  Widget mappingItem(int index, Object mapping) {
    return ListTile(
      dense: true,
      contentPadding:
          const EdgeInsets.symmetric(horizontal: 26.0, vertical: 0.0),
      //https://fonts.google.com/specimen/Playfair+Display?category=Serif,Sans+Serif,Display,Monospace
      title: Text('${index + 1}: ${mapping}', style: TextStyle(fontSize: 18)),
      trailing: Container(
        width: 20,
        height: 20,
        decoration: BoxDecoration(
          // color: Colors.blue,
          borderRadius: BorderRadius.circular(26),
        ),
      ),
    );
  }
}
