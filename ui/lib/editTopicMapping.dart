import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:ui/client/httpRequest.dart';
import 'package:ui/client/httpResponse.dart';
import 'package:ui/verticalSplitView.dart';
import 'package:url_launcher/url_launcher.dart';

import 'client/diskClient.dart';
import 'client/mappingCheck.dart';
import 'client/mappingEntry.dart';
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
      home: EditTopicMappingWidget(MappingEntry("foo", "bar"))));
}

class EditTopicMappingWidget extends StatefulWidget {
  EditTopicMappingWidget(this.entry);

  MappingEntry entry;

  @override
  _EditTopicMappingWidgetState createState() => _EditTopicMappingWidgetState();
}

class _EditTopicMappingWidgetState extends State<EditTopicMappingWidget> {
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
  Map<int, HttpResponse> _executeResponseByIndex = Map();
  Map<int, bool> _executingRequestByIndex = Map();

  String _topic = "";
  int _offset = 0;
  int _partition = 0;
  String _key = "";

  static const DefaultCode = '''
  // The mapping code transforms a context into a collection of HttpRequests
  val body = {
    val enrichment = Json.obj(
      "timestamp" -> System.currentTimeMillis().asJson,
      "kafka-key" -> record.key.value
    )
    record.content.value.deepMerge(enrichment)
  }

(0 until 3).map { i =>
  val url      = s"http://localhost:8080/rest/store/\${record.topic}/\${record.partition}-\$i/\${record.offset}"
  HttpRequest.post(url).withBody(body.noSpaces)
}
  ''';

  static const TestInput = '''{
    "input" : {
      "flag" : true,
      "array" : [1,2,3],
      "text" : "testing",
      "nested" : {
        "deep" : false,
        "pi" :  3.141592
      }
    }
  }''';

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

  void _onTestMapping(BuildContext ctxt) {
    if (_formKey.currentState.validate()) {
      _formKey.currentState.save();
      try {
        final testJason = jsonDecode(_testInputController.text);
        final request = TransformRequest(
            _codeTextController.text,
            testJason,
            _key,
            DateTime.now().millisecondsSinceEpoch,
            Map(),
            _topic,
            _offset,
            _partition);
        setState(() {
          _testInFlight = true;
        });
        MappingCheck.check(request).then((TransformResponse value) {
          setState(() {
            _testInFlight = false;
            _testResult = value;
          });
        });
      } catch (e) {
        ScaffoldMessenger.of(ctxt)
            .showSnackBar(SnackBar(content: Text("Invalid json: $e")));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    // final MappingEntry args = ModalRoute.of(context).settings.arguments;
    return SafeArea(
        child: Scaffold(
            appBar: AppBar(
                title: Align(
                    alignment: Alignment.topLeft,
                    child: Text("Edit Topic Mapping")),
                backgroundColor: Theme.of(context).colorScheme.background,
                actions: [
                  IconButton(
                      onPressed: _resetCode, icon: Icon(Icons.refresh_sharp)),
                  IconButton(
                      onPressed: () => _saveMapping(context),
                      icon: Icon(Icons.save))
                ]),
            body: Form(
              key: _formKey,
              child: VerticalSplitView(
                  key: Key("split"),
                  left: codeEditor(context),
                  right: testingWidget(context)),
            )));
  }

  void _saveMapping(BuildContext context) async {
    if (_formKey.currentState.validate()) {
      _formKey.currentState.save();
      final script = _codeTextController.text;
      await DiskClient.store(entry.filePath, script);
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text("Saved mapping code to ${entry.filePath}")));
    }
  }

  void _resetCode() {
    _codeTextController.text = DefaultCode;
  }

  Widget codeEditor(BuildContext context) {
    return sizedColumn(_editorScrollController, [
      Padding(
        padding: const EdgeInsets.all(8.0),
        child: FieldWidget("Mapped Topic:", "The Kafka Topic", entry.topic,
            (newTopic) => entry.topic = newTopic, textOk),
      ),
      Padding(
        padding: const EdgeInsets.all(8.0),
        child: FieldWidget(
            "File Path:",
            "Where to save this mapping",
            entry.filePath,
            (newPath) => entry.filePath = newPath,
            (String path) => path.isEmpty ? "Path cannot be empty" : null),
      ),
      Card(
          color: Theme.of(context).colorScheme.background,
          child: Padding(
            padding: EdgeInsets.all(8.0),
            child: TextField(
              maxLines: 60,
              controller: _codeTextController,
              decoration: InputDecoration.collapsed(hintText: DefaultCode),
            ),
          ))
    ]);
  }

  Widget testInputsForm() {
    return Container(
      constraints: BoxConstraints(maxHeight: 200),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Flexible(
              child: FieldWidget("Topic:", "The Kafka Topic", "some-topic",
                  (value) => _topic = value, textOk)),
          Flexible(
              child: FieldWidget("Offset:", "The Kafka Offset", "1",
                  (value) => _offset = int.parse(value), validateNumber)),
          Flexible(
              child: FieldWidget("Partition:", "The Kafka Partition", "2",
                  (value) => _partition = int.parse(value), validateNumber)),
          Flexible(
              child: FieldWidget("Key:", "The Message Key", "some-key",
                  (value) => _key = value, textOk))
        ],
      ),
    );
  }

  Widget sizedColumn(ScrollController sc, List<Widget> contents) {
    return LayoutBuilder(builder: (ctxt, BoxConstraints constraints) {
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
        Flexible(child: testInputsForm()),
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
              // constraints: BoxConstraints(maxHeight: 200),
              decoration: BoxDecoration(color: Colors.deepPurple),
              child: Scrollbar(
                  isAlwaysShown: true,
                  controller: _testInputScrollController,
                  child: SingleChildScrollView(
                      child: TextField(
                    maxLines: 10,
                    controller: _testInputController,
                  ))),
            )),
        Flexible(
            child: OutlinedButton.icon(
                icon: Icon(Icons.bug_report),
                label:
                    _testInFlight ? progressIndicator() : Text("Test Mapping"),
                onPressed: () => _onTestMapping(context))),
        if (_testResult != null) _resultsTitle(_testResult.result.length),
        if (_testResult != null) testResults(_testResult)
      ],
    );
  }

  Widget progressIndicator() =>
      SizedBox(
          width: 20,
          height: 20,
          child: CircularProgressIndicator());


  Widget testResults(TransformResponse response) {
    if (response.messages != null && response.messages.isNotEmpty) {
      return Text("Error : ${response.messages}");
    }

    final List<HttpRequest> requests = response.asRequests();
    if (requests.isEmpty || requests.length != response.result.length) {
      return Text("Result : ${response.asJson.toString()}");
    }

    return SizedBox(
        width: 400,
        height: 600,
        child: ListView.separated(
            itemBuilder: (_, index) => httpResultWidget(index, requests[index]),
            separatorBuilder: (_, index) => Divider(),
            itemCount: requests.length));
  }

  Widget httpResponseWidget(int index, HttpResponse response) {
    if (_executingRequestByIndex[index] == true) {
      return progressIndicator();
    }
    final body = (response.body != null && response.body.isNotEmpty)
        ? ": ${response.body}"
        : "";
    return Text("Response status ${response.statusCode} $body");
  }

  /**
   * The widget (card) for a single http result
   */
  Widget httpResultWidget(int index, HttpRequest request) {
    String body = request.body;
    final executeButton = OutlinedButton.icon(
        onPressed: () => _executeTestRequest(index, request),
        icon: Icon(Icons.not_started_outlined),
        label: Text("Execute"));
    return Container(
        decoration: BoxDecoration(shape: BoxShape.circle),
        child: ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: 100,
              maxWidth: 300,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                InkWell(
                    child: Text("${request.method} ${request.url}",
                        style: const TextStyle(color: Colors.blue)),
                    onTap: () => launch(request.url)),
                new Text(body),
                _executingRequestByIndex.containsKey(index)
                    ? Row(
                        children: [
                          httpResponseWidget(
                              index, _executeResponseByIndex[index]),
                          executeButton
                        ],
                      )
                    : executeButton
              ],
            )));
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

  Widget _resultsTitle(int length) {
    final title = length == 1 ? "Result" : "Results";
    return Flexible(
        child: Padding(
      padding: const EdgeInsets.all(8.0),
      child: Align(
          alignment: Alignment.topLeft,
          child: Text("${_testResult.result.length} $title",
              style:
                  const TextStyle(fontSize: 20, fontWeight: FontWeight.bold))),
    ));
  }

  void _executeTestRequest(int index, HttpRequest request) async {
    setState(() {
      _executingRequestByIndex[index] = true;
      _executeResponseByIndex[index] = null;
    });
    final HttpResponse result = await request.exec();
    setState(() {
      _executingRequestByIndex[index] = false;
      _executeResponseByIndex[index] = result;
    });
  }
}
