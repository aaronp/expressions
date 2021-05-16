import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:ui/client/batchCheckRequest.dart';
import 'package:ui/verticalSplitView.dart';
import 'client/configClient.dart';
import 'client/batchClient.dart';
import 'client/configSummary.dart';
import 'client/diskClient.dart';
import 'client/mappingCheck.dart';
import 'client/mappingEntry.dart';
import 'client/message.dart';
import 'fieldWidget.dart';

/**
 * Debug entry point
 */
void main() {
  runApp(MaterialApp(
      title: 'Franz',
      theme: ThemeData(brightness: Brightness.light),
      darkTheme: ThemeData(brightness: Brightness.dark),
      themeMode: ThemeMode.dark,
      debugShowCheckedModeBanner: false,
      home: EditZIOMappingWidget("test.conf", MappingEntry("foo", "bar.sc"), ConfigSummary.empty())));
}

class EditZIOMappingWidget extends StatefulWidget {
  EditZIOMappingWidget(this.configFileName, this.entry, this.config) {
    // we have to keep track of our mappings coming in as the user can change
    // the topic within the widget
    this.originalMappings = new Map<String, List<String>>.from(this.config.mappings);
  }

  String configFileName;
  MappingEntry entry;
  ConfigSummary config;
  Map<String, List<String>> originalMappings;

  @override
  _EditZIOMappingWidgetState createState() => _EditZIOMappingWidgetState();
}

class _EditZIOMappingWidgetState extends State<EditZIOMappingWidget> {
  final _formKey = GlobalKey<FormState>();
  MappingEntry entry = MappingEntry("", "");

  final _codeFocusNode = FocusNode();
  final _codeTextController = TextEditingController();
  final _testInputController = TextEditingController();
  final _editorScrollController = ScrollController();
  final _testScrollController = ScrollController();
  final _testInputScrollController = ScrollController();
  TransformResponse _testResult;
  bool _testInFlight = false;

  static const DefaultCode = '''
// The mapping code transforms a context into a collection of HttpRequests
val RestServer = "http://localhost:8080/rest/store/test"
batch.foreach { msg =>
  val value = msg.content.value

  for {
    _ <- putStr(s"publishing to \${msg.topic}")
    _ = org.slf4j.LoggerFactory.getLogger("test").info(s" P U B L I S H I N G \${msg.topic}")
    r <- msg.key.id.asString.withValue(value).publishTo(msg.topic)
    url = s"\$RestServer/\${msg.partition}/\${msg.offset}"
    postResponse <- post(url, msg.key.deepMerge(msg.content.value))
    _ <- putStr(s"published \${msg.key}")
    _ <- putStrErr(s"post to \$url returned \${postResponse}")
  } yield r
}.orDie
  ''';

  static const TestInput = '''[
        {
            "content" : { "some" : "content" },
            "key" : { "id" : "abc123" },
            "timestamp" : 123456789,
            "headers" : { },
            "topic" : "topic-alpha",
            "offset" : 12,
            "partition" : 100
        },
        {
            "content" : { "some" : "more content" },
            "key" : { "id" : "def456" },
            "timestamp" : 987654321,
            "headers" : { "head" : "er" },
            "topic" : "topic-beta",
            "offset" : 13,
            "partition" : 7
        }
    ]''';

  @override
  void initState() {
    super.initState();
    entry = this.widget.entry;
    _codeTextController.text = "Loading ${entry.filePath} ...";

    _testInputController.text = TestInput;
    DiskClient.get(entry.filePath).then((content) {
      setState(() {
        if (content.isEmpty) {
          _codeTextController.text = DefaultCode;
        } else {
          _codeTextController.text = content;
        }
      });
    });
  }

  void _onTestMapping(BuildContext ctxt) async {
    if (_formKey.currentState.validate()) {
      _formKey.currentState.save();
      try {
        final List<dynamic> messagesList =
            jsonDecode(_testInputController.text);
        final messages = messagesList.map((j) => Message.fromJson(j)).toList();

        // TODO - add ConfigSummary to BatchCheck Request this.widget.rootConfig
        final BatchCheckRequest request = BatchCheckRequest(
            "",
            messages,
            _codeTextController.text);

        setState(() {
          _testInFlight = true;
          _testResult = null;
        });
        await BatchClient.check(request).then((TransformResponse value) {
          setState(() {
            _testInFlight = false;
            _testResult = value;
          });
        });
      } catch (e) {
        setState(() {
          _testInFlight = false;
        });
        ScaffoldMessenger.of(ctxt)
            .showSnackBar(SnackBar(content: Text("Oops: $e")));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return new WillPopScope(
        onWillPop: () => onBack(context), child: buildInner(context));
  }

  Future<bool> onBack(BuildContext context) async {

    return (await showDialog(
          context: context,
          builder: (context) => new AlertDialog(
            title: new Text('Add Mapping?'),
            content: new Text("You may have unsaved worked - "),
            actions: <Widget>[
              ElevatedButton.icon(
                onPressed: () => Navigator.of(context).pop(false),
                icon: Icon(Icons.cancel, color: Colors.red),
                label: new Text('Cancel'),
              ),
              ElevatedButton.icon(
                onPressed: () => Navigator.of(context).pop(true),
                icon: Icon(Icons.cancel_outlined),
                label: new Text("Don't Save"),
              ),
              ElevatedButton.icon(
                onPressed: () async {
                  if (_formKey.currentState.validate()) {
                    _formKey.currentState.save();
                    // write the config script down against the file path
                    await _saveScript();
                    widget.config.mappings[entry.topic] = [entry.filePath];

                    Navigator.of(context).pop(true);
                  }
                },
                icon: Icon(Icons.check, color: Colors.green),
                label: new Text('Add Mapping'),
              ),
            ],
          ),
        )) ??
        false;
  }

  Widget buildInner(BuildContext context) {
    // final MappingEntry args = ModalRoute.of(context).settings.arguments;
    return SafeArea(
        child: Scaffold(
      appBar: AppBar(
          title: Align(
              alignment: Alignment.topLeft, child: Text("Edit Handler Script")),
          backgroundColor: Theme.of(context).colorScheme.background,
          actions: [
            IconButton(onPressed: _resetCode, icon: Icon(Icons.refresh_sharp)),
            IconButton(
                onPressed: () => _saveMapping(context), icon: Icon(Icons.save))
          ]),
      body: Form(
          key: _formKey,
          child: VerticalSplitView(
              key: Key("split"),
              left: codeEditor(context),
              right: testingWidget(context))),
    ));
  }

  void _saveMapping(BuildContext context) async {
    if (_formKey.currentState.validate()) {
      _formKey.currentState.save();
      await _saveScript();
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text("Saved mapping code to ${entry.filePath}")));
    }
  }

  Future<void> _saveScript() async {
    final String mappingFilePath = entry.filePath;

    // update the configuration w/ our new mapping
    final configWithMapping = widget.config;
    configWithMapping.mappings = new Map<String, List<String>>.from(widget.originalMappings);
    configWithMapping.mappings.addAll({entry.topic : [entry.filePath]});
    await ConfigClient.save(widget.configFileName, configWithMapping);

    // save the config against the given name
    await DiskClient.store(mappingFilePath, _codeTextController.text);
  }

  void _resetCode() {
    _codeTextController.text = DefaultCode;
  }

  Widget codeEditor(BuildContext context) {
    return LayoutBuilder(builder: (ctxt, BoxConstraints constraints) {
      // just worked out an empirical finger-in-the-air estimate of the rows
      final heightAvailableForCode = constraints.maxHeight - 300;
      final suggestedRows = 8 + (heightAvailableForCode ~/ 20);
      final numRows = max(4, min(40, suggestedRows));
      return SizedBox(
          width: constraints.maxWidth,
          height: constraints.maxHeight,
          child: Scrollbar(
              isAlwaysShown: true,
              controller: _editorScrollController,
              child: SingleChildScrollView(
                  child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                    Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: FieldWidget(
                          "Mapped Topic:",
                          "The Kafka Topic",
                          entry.topic,
                          (newTopic) => entry.topic = newTopic,
                          textOk),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: FieldWidget(
                          "File Path:",
                          "Where to save this mapping",
                          entry.filePath,
                          (newPath) => entry.filePath = newPath,
                          (String path) =>
                              path.isEmpty ? "Path cannot be empty" : null),
                    ),
                    Card(
                        color: Theme.of(context).colorScheme.background,
                        child: Padding(
                          padding: EdgeInsets.all(8.0),
                          child: TextField(
                            maxLines: numRows,
                            controller: _codeTextController,
                            decoration: InputDecoration.collapsed(
                                hintText: DefaultCode),
                          ),
                        ))
                  ]))));
    });
  }

  Widget sizedColumn(ScrollController sc, List<Widget> contents) {
    return LayoutBuilder(builder: (ctxt, BoxConstraints constraints) {
      // final all = <Widget>[
      //   Text("w:${constraints.maxWidth}"),
      //   Text("h:${constraints.maxHeight}"),
      //   ...contents
      // ];
      return SizedBox(
          width: constraints.maxWidth,
          height: constraints.maxHeight,
          child: Scrollbar(
              isAlwaysShown: true,
              controller: sc,
              child: SingleChildScrollView(
                  child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: contents))));
    });
  }

  Widget testingWidget(BuildContext context) {
    return sizedColumn(
      _testScrollController,
      [
        Flexible(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(0, 8, 16, 8),
            child: Text("Test Input:",
                style:
                    const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          ),
        ),
        Padding(
            padding: EdgeInsets.fromLTRB(0, 8, 16, 8),
            child: Container(
              decoration: BoxDecoration(color: Colors.black87),
              child: Scrollbar(
                  isAlwaysShown: true,
                  controller: _testInputScrollController,
                  child: SingleChildScrollView(
                      child: TextField(
                    maxLines: 20,
                    controller: _testInputController,
                  ))),
            )),
        Flexible(
            child: OutlinedButton.icon(
                icon: Icon(Icons.bug_report),
                label: _testInFlight
                    ? SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator())
                    : Text("Test Mapping"),
                onPressed: () => _onTestMapping(context))),
        if (_testResult != null) testResults(_testResult)
      ],
    );
  }

  Widget testResults(TransformResponse response) {
    return Flexible(
        child: Padding(
      padding: const EdgeInsets.all(8.0),
      child: Align(
          alignment: Alignment.topLeft, child: testResultsContent(response)),
    ));
  }

  Widget testResultsContent(TransformResponse response) {
    try {
      final output = Output.fromJson(response.result);
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(0, 0, 0, 16.0),
            child:
                SelectableText(response.messages.join("\n"), style: errStyle),
          ),
          if (output.stdOut.isNotEmpty && output.stdErr.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(0, 8.0, 0, 8.0),
              child: Text("Standard Output:",
                  style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.normal,
                      fontFamily: "Lato")),
            ),
          ...output.stdOut.map((o) => _stdOut(o)).toList(),
          if (output.stdOut.isNotEmpty && output.stdErr.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(0, 8.0, 0, 8.0),
              child: Text("Error Output:",
                  style: const TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.normal,
                      fontFamily: "Lato")),
            ),
          ...output.stdErr.map((o) => _stdErr(o)).toList()
        ],
      );
    } catch (e) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(0, 0, 0, 16.0),
            child: SelectableText(response.messages.join("\n"),
                style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: Colors.red,
                    fontFamily: "Lato")),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(0, 0, 0, 16.0),
            child: SelectableText("JSON response: ${response.result}",
                style: const TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.normal,
                    // color: Colors.bl,
                    fontFamily: "Lato")),
          )
        ],
      );
    }
  }

  Widget _stdOut(String text) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(8.0, 0, 0, 0),
      child: Text(text,
          style: const TextStyle(
              fontSize: 12, fontWeight: FontWeight.normal, fontFamily: "Lato")),
    );
  }

  final errStyle = const TextStyle(
      fontSize: 12,
      fontWeight: FontWeight.bold,
      color: Colors.red,
      fontFamily: "Lato");

  Widget _stdErr(String text) {
    return Padding(
        padding: const EdgeInsets.fromLTRB(8.0, 0, 0, 0),
        child: Text(text, style: errStyle));
  }

  @override
  void dispose() {
    super.dispose();
    _codeTextController.dispose();
    _testInputController.dispose();
    _editorScrollController.dispose();
    _testInputController.dispose();
    _testScrollController.dispose();
    _testInputScrollController.dispose();
    _codeFocusNode.dispose();
  }
}
