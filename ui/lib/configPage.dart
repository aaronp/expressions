import 'package:flutter/material.dart';
import 'package:ui/client/kafkaClient.dart';
import 'package:ui/editConfigWidget.dart';
import 'package:ui/editTopicMapping.dart';
import 'package:ui/editZIOMapping.dart';
import 'package:ui/publishWidget.dart';
import 'package:ui/runningConsumersWidget.dart';

import 'client/batchClient.dart';
import 'client/configClient.dart';
import 'client/configSummary.dart';
import 'client/diskClient.dart';
import 'client/mappingEntry.dart';
import 'fieldWidget.dart';
import 'openFileWidget.dart';

class LoadedConfig {
  LoadedConfig(this.fileName, this.loadedContent, this.summary);

  String fileName;
  String loadedContent;
  ConfigSummary summary;

  bool isEmpty() {
    return summary.topic == "";
  }

  @override
  String toString() {
    return "LoadedConfig(fileName: $fileName, summary: $summary)";
  }
}

class ConfigPage extends StatefulWidget {
  ConfigPage({Key key, this.title}) : super(key: key);
  final String title;

  @override
  _ConfigPageState createState() => _ConfigPageState();
}

class _ConfigPageState extends State<ConfigPage> {
  LoadedConfig _currentConfig = LoadedConfig("", "", ConfigSummary.empty());
  String _lastLoadedFileName = "";
  final _formKey = GlobalKey<FormState>();

  Future<LoadedConfig> loadConfig(String fileName) async {
    var file = fileName;
    if (file == "") {
      file = "application.conf";
    }
    final summary = await ConfigClient.getSummary(file);
    final loaded = LoadedConfig(file, "", summary);
    // setState(() {
    //   _lastLoadedFileName = "application.conf";
    // });
    return loaded;

  }

  Future<LoadedConfig> defaultConfig() async {
    final lastSavedFileName = await DiskClient.getLastSaved();
    return await loadConfig(lastSavedFileName);
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
    print('configPage::initState!');
    _reload();
  }

  void _reload() {
    print(
        "reload checking _lastLoadedFileName '${_lastLoadedFileName}' != _currentConfig.fileName '${_currentConfig.fileName}' = ${_lastLoadedFileName != _currentConfig.fileName}");

    if (_lastLoadedFileName.isEmpty ||
        _lastLoadedFileName != _currentConfig.fileName) {
      defaultConfig().then((value) {
        print("defaultConfig() returned (${!value.isEmpty()}) :  $value");
        _updateLoadedConfig(value);
      });
    }
  }

  void _updateLoadedConfig(LoadedConfig value) async {
    if (!value.isEmpty()) {
      await DiskClient.setLastSaved(value.fileName);
      setState(() {
        _currentConfig = value;
        _lastLoadedFileName = value.fileName;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    // _reload();

    final runningButton = Padding(
        padding: const EdgeInsets.fromLTRB(8.0, 2, 8.0, 2.0),
        child: TextButton.icon(
            icon: Icon(Icons.list),
            label: Text('Running'),
            onPressed: () => onListRunning(context)));

    final startBatchButton = Padding(
        padding: const EdgeInsets.fromLTRB(8.0, 2, 8.0, 2.0),
        child: TextButton.icon(
          icon: Icon(Icons.read_more_outlined, color: Colors.white),
          label: Text('Start Batch Consumer'),
          onPressed: () {
            onStartBatchConsumer(context);
          },
        ));
    final startRestButton = Padding(
        padding: const EdgeInsets.fromLTRB(8.0, 2, 8.0, 2.0),
        child: TextButton.icon(
          icon: Icon(Icons.read_more_outlined, color: Colors.white),
          label: Text('Start REST Consumer'),
          onPressed: () {
            onStartRestConsumer(context);
          },
        ));
    final publishButton = Padding(
        padding: const EdgeInsets.fromLTRB(8.0, 2, 8.0, 2.0),
        child: TextButton.icon(
          icon: Icon(Icons.publish, color: Colors.white),
          label: Text('Publish'),
          onPressed: () {
            onPublish(context);
          },
        ));

    return SafeArea(
        child: Scaffold(
            appBar: AppBar(
                title: Align(
                    alignment: Alignment.topLeft,
                    child: Text('Franz', textAlign: TextAlign.start)),
                actions: [
                  publishButton,
                  startRestButton,
                  startBatchButton,
                  runningButton
                ]),
            body: configSummaryWidget(
                context) // This trailing comma makes auto-formatting nicer for build methods.
            ));
  }

  Widget configColumn(BuildContext ctxt) {
    return Container(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.max,
        children: [
          configEntry("Brokers", _currentConfig.summary.brokersAsString(),
              (newValue) => _currentConfig.summary.brokers = [newValue]),
          configEntry("Topic", _currentConfig.summary.topic,
              (newValue) => _currentConfig.summary.topic = newValue),
          configEntry("Key Type", _currentConfig.summary.keyType,
              (newValue) => _currentConfig.summary.keyType = newValue),
          configEntry("Value Type", _currentConfig.summary.valueType,
              (newValue) => _currentConfig.summary.valueType = newValue),
          Container(
            height: 400.0,
            alignment: Alignment.topLeft,
            child: mappingsWidget(ctxt),
          )
        ],
      ),
    );
  }

  Widget configSummaryWidget(BuildContext ctxt) {
    final openButton = IconButton(
        iconSize: 32,
        tooltip: 'Open',
        icon: Icon(Icons.folder_open),
        onPressed: () => onOpenConfig(ctxt));

    final saveButton = IconButton(
        iconSize: 32,
        tooltip: 'Save',
        icon: Icon(Icons.save),
        onPressed:
            _currentConfig.fileName.isEmpty ? null : () => onSaveConfig(ctxt));

    final editButton = IconButton(
        iconSize: 32,
        tooltip: 'Config',
        icon: Icon(Icons.settings),
        onPressed: () => onEditConfig(ctxt));

    return Form(
        key: _formKey,
        child: Scaffold(
            appBar: AppBar(
                // title: Text(_currentConfig.fileName),
                title: FieldWidget(
                    "Config File : ",
                    "the configuration file",
                    _currentConfig.fileName,
                    (newPath) => _currentConfig.fileName = newPath,
                    (String path) =>
                        path.isEmpty ? "Path cannot be empty" : null),
                backgroundColor: Colors.grey[800],
                actions: [
                  openButton,
                  saveButton,
                  editButton,
                ],
                primary: false,
                automaticallyImplyLeading: false),
            body: LayoutBuilder(
              builder:
                  (BuildContext context, BoxConstraints viewportConstraints) {
                return SingleChildScrollView(
                    child: ConstrainedBox(
                        constraints: BoxConstraints(
                            minHeight: viewportConstraints.maxHeight),
                        child: IntrinsicHeight(child: configColumn(ctxt))));
              },
            )));
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

  Widget configEntry(String label, String value, OnUpdate onUpdate) {
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Container(
          width: 800,
          child: FieldWidget(
              label, "$label config", value, onUpdate, (newValue) => null)),
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
                              backgroundColor: MaterialStateProperty.all<Color>(
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

  static String _unquote(String input) {
    final trimmed = input.trim();
    if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length - 2);
    }
    return trimmed;
  }

  void onListRunning(BuildContext ctxt) =>
      _push(ctxt, RunningConsumersWidget());

  void onPublish(BuildContext ctxt) => _push(
      ctxt,
      PublishWidget(_currentConfig.fileName, _currentConfig.summary.topic,
          _currentConfig.loadedContent));

  void onEditConfig(BuildContext ctxt) async {
    final editedConfig = await _push(
        ctxt,
        EditConfigWidget(
            _currentConfig.fileName, _currentConfig.loadedContent));
    final newSummary =
        await summaryFor(_currentConfig.fileName, editedConfig.toString());
    setState(() {
      _currentConfig = newSummary;
    });
  }

  Future<bool> onSaveConfig(BuildContext ctxt) async {
    if (_formKey.currentState.validate()) {
      _formKey.currentState.save();

      DiskClient.setLastSaved(_currentConfig.fileName);
      final result = await ConfigClient.save(
          _currentConfig.fileName, _currentConfig.summary);

      setState(() {
        _currentConfig.fileName = _currentConfig.fileName;
      });

      return result;
    }
  }

  void onStartBatchConsumer(BuildContext ctxt) async {
    final bStart = await BatchClient.start(_lastLoadedFileName);
    ScaffoldMessenger.of(ctxt).showSnackBar(
        SnackBar(content: Text("Started batch client '$bStart'")));
    _push(ctxt, RunningConsumersWidget());
  }

  void onStartRestConsumer(BuildContext ctxt) async {
    if (_lastLoadedFileName.isNotEmpty) {
      // final config = await ConfigClient.getConfig(_lastLoadedFileName);

      final kStart = await KafkaClient.start(_lastLoadedFileName);
      ScaffoldMessenger.of(ctxt).showSnackBar(
          SnackBar(content: Text("Started REST client '$kStart'")));
      _push(ctxt, RunningConsumersWidget());
    } else {
      ScaffoldMessenger.of(ctxt)
          .showSnackBar(SnackBar(content: Text("Config was empty")));
    }
  }

  void onEditMapping(BuildContext ctxt, MappingEntry entry) => _push(
      ctxt,
      EditZIOMappingWidget(
          _currentConfig.fileName, entry, _currentConfig.summary));

  void onEditHttpMapping(BuildContext ctxt, MappingEntry entry) =>
      _push(ctxt, EditTopicMappingWidget(entry));

  void onAddMapping(BuildContext ctxt) async {
    final returnValue = await _push(
        ctxt,
        EditZIOMappingWidget(_currentConfig.fileName,
            MappingEntry("new topic name", ""), _currentConfig.summary));

    print("returnValue is $returnValue");
    await loadConfig(_currentConfig.fileName)
        .then((value) => _updateLoadedConfig(value));
  }

  void onRemoveMapping(BuildContext ctxt, String key) async {
    _currentConfig.summary.mappings.remove(key);
    await ConfigClient.save(_currentConfig.fileName, _currentConfig.summary);
    await loadConfig(_currentConfig.fileName)
        .then((value) => _updateLoadedConfig(value));
  }

  Future<Object> _push(BuildContext ctxt, Widget page) async {
    final result = await Navigator.push(
        ctxt, MaterialPageRoute(builder: (context) => page));

    // refresh
    setState(() {});
    return result;
  }

  Widget mappingsList(BuildContext ctxt) {
    final mappingList = <Widget>[];
    _currentConfig.summary.mappings.forEach((quotedKey, value) {
      final key = _unquote(quotedKey);
      final path = value.join("/");

      // final editHttpButton = OutlinedButton.icon(
      //     onPressed: () => onEditHttpMapping(ctxt, MappingEntry(key, path)),
      //     label: Text("(old http edit)"),
      //     icon: Icon(Icons.edit));

      final deleteButton = IconButton(
          onPressed: () => onRemoveMapping(ctxt, quotedKey),
          icon: Icon(Icons.highlight_remove, color : Colors.red));

      final link = InkWell(
          child:
              Text('$key ($path)', style: const TextStyle(color: Colors.blue)),
          onTap: () => onEditMapping(ctxt, MappingEntry(key, path)));

      final mappingButtons = Row(children: [deleteButton, link]);
      // final mappingButtons = Row(children: [link, deleteButton, editHttpButton]);

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

  Future<String> onOpenConfig(BuildContext ctxt) async {
    final chosen = await showDialog(
        context: ctxt,
        builder: (newCtxt) {
          return OpenFileWidget();
        });
    final fileName = chosen.toString();
    if (fileName.isNotEmpty) {
      loadConfig(fileName).then((value) async => _updateLoadedConfig(value));
    }
    return fileName;
  }
}
