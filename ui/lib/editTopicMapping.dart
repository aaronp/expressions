import 'dart:convert';
import 'dart:html';

import 'package:flutter/material.dart';
import 'package:pretty_json/pretty_json.dart';
import 'package:ui/client/httpRequest.dart';
import 'package:ui/verticalSplitView.dart';
import 'package:url_launcher/url_launcher.dart';

import 'client/diskClient.dart';
import 'client/mappingCheck.dart';
import 'client/mappingEntry.dart';

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
  final _fileNameController = TextEditingController();

  final _codeFocusNode = FocusNode();
  final _codeTextController = TextEditingController();
  final _testInputController = TextEditingController();
  final _editorScrollController = ScrollController();
  final _testScrollController = ScrollController();
  final _testInputScrollController = ScrollController();
  TransformResponse _testResult = null;
  bool _testInFlight = false;

  String _topic = "";
  int _offset = 0;
  int _partition = 0;
  String _key = "";

  static const DefaultCode = '''
  // The mapping code transforms a context into a collection of HttpRequests
  val StoreURL = s"http://localhost:8080/rest/store"
  val url      = s"\$StoreURL/\${record.topic}/\${record.partition}/\${record.offset}"
  val body = {
    val enrichment = Json.obj(
      "timestamp" -> System.currentTimeMillis().asJson,
      "kafka-key" -> record.key.value
    )
    record.content.value.deepMerge(enrichment)
  }

  List(HttpRequest.post(url).withBody(body.noSpaces))
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
    _fileNameController.text = entry.filePath;
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
          .showSnackBar(SnackBar(content: Text("Invalid json: ${e}")));
    }
  }

  @override
  Widget build(BuildContext context) {
    // final MappingEntry args = ModalRoute.of(context).settings.arguments;
    return SafeArea(
        child: Scaffold(
      appBar: AppBar(
          title: Align(
              alignment: Alignment.topLeft, child: Text("Edit Topic Mapping")),
          backgroundColor: Theme.of(context).colorScheme.background,
          actions: [
            IconButton(onPressed: _resetCode, icon: Icon(Icons.refresh_sharp)),
            IconButton(
                onPressed: () => _saveMapping(context), icon: Icon(Icons.save))
          ]),
      body: VerticalSplitView(
          key: Key("split"),
          left: codeEditor(context),
          right: testingWidget(context)),
    ));
  }

  void _saveMapping(BuildContext context) async {
    await DiskClient.store(entry.filePath, _codeTextController.text);
    Navigator.of(context).pop();
  }

  void _resetCode() {
    _codeTextController.text = DefaultCode;
  }

  Widget codeEditor(BuildContext context) {
    return sizedColumn(_editorScrollController, [
      Padding(
        padding: const EdgeInsets.all(8.0),
        child: FieldWidget("Mapped Topic:", "The Kafka Topic", entry.topic,
            (newTopic) => entry.topic = newTopic, _ok),
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

  String _isNumber(String x) {
    try {
      int.parse(x);
      return null;
    } catch (e) {
      return "${x} is not a number";
    }
  }

  String _ok(String text) => null;

  Widget testInputsForm() {
    return Container(
      constraints: BoxConstraints(maxHeight: 200),
      // decoration: BoxDecoration(color: Colors.blue),
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.start,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Flexible(
                child: FieldWidget("Topic:", "The Kafka Topic", "some-topic",
                    (value) => _topic = value, _ok)),
            Flexible(
                child: FieldWidget("Offset:", "The Kafka Offset", "1",
                    (value) => _offset = int.parse(value), _isNumber)),
            Flexible(
                child: FieldWidget("Partition:", "The Kafka Partition", "2",
                    (value) => _partition = int.parse(value), _isNumber)),
            Flexible(
                child: FieldWidget("Key:", "The Message Key", "some-key",
                    (value) => _key = value, _ok))
          ],
        ),
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
                    const TextStyle(fontSize: 16, fontWeight: FontWeight.w100)),
          ),
        ),
        Padding(
            padding: EdgeInsets.fromLTRB(0, 8, 16, 8),
            child: Container(
              // constraints: BoxConstraints(maxHeight: 200),
              decoration: BoxDecoration(color: Colors.purple),
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
                label: _testInFlight
                    ? CircularProgressIndicator()
                    : Text("Test Mapping"),
                onPressed: () => _onTestMapping(context))),
        if (_testResult != null) _resultsTitle(_testResult.result.length),
        if (_testResult != null) testResults(_testResult)
      ],
    );
  }

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
        height: 400,
        child: ListView.separated(
            itemBuilder: (_, index) => httpResult(requests[index]),
            separatorBuilder: (_, index) => Divider(),
            itemCount: requests.length));
  }

  Widget httpResult(HttpRequest request) {
    String body = "";
    try {
      JsonEncoder encoder = new JsonEncoder.withIndent('  ');
      final jason = jsonDecode(request.body);

      print("jason:");
      print(jason);
      final fmt1 = encoder.convert(request.body);
      final fmt = prettyJson(request.body, indent: 4);
      print("Formatting:");
      print(fmt);
      body = fmt;
    } catch (e) {
      print("bang: " + e);
      body = request.body.toString();
    }
    return Container(
        decoration: BoxDecoration(shape: BoxShape.circle),
        child: Container(
            constraints: BoxConstraints(maxHeight: 100),
            child: Column(
              children: [
                InkWell(
                    child: Align(
                        alignment: Alignment.topLeft,
                        child: Text("${request.method} ${request.url}",
                            style: const TextStyle(color: Colors.blue))),
                    onTap: () => launch(request.url)),
                new Text(body),
              ],
            )));
  }

  @override
  void dispose() {
    super.dispose();
    _fileNameController.dispose();
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
                  style: const TextStyle(
                      fontSize: 20, fontWeight: FontWeight.bold))),
        ));
  }
}

typedef OnUpdate = void Function(String value);

class FieldWidget extends StatefulWidget {
  @override
  _FieldWidgetState createState() => _FieldWidgetState();
  String labelText;
  String hintText;
  String initialValue;
  OnUpdate onUpdate;
  FormFieldValidator<String> validator;

  FieldWidget(this.labelText, this.hintText, this.initialValue, this.onUpdate,
      this.validator);
}

class _FieldWidgetState extends State<FieldWidget> {
  final _focusNode = FocusNode();

  @override
  void initState() {
    super.initState();
    widget.onUpdate(widget.initialValue);
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      initialValue: widget.initialValue,
      focusNode: _focusNode,
      style: const TextStyle(fontSize: 20, fontWeight: FontWeight.normal),
      cursorWidth: 1,
      cursorColor: Colors.black87,
      decoration: InputDecoration(
          hintText: widget.hintText,
          labelText: widget.labelText,
          labelStyle:
              const TextStyle(fontSize: 16, fontWeight: FontWeight.normal),
          hintStyle:
              const TextStyle(fontSize: 16, fontWeight: FontWeight.normal),
          errorStyle:
              const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
          errorMaxLines: 2),
      onSaved: widget.onUpdate,
      validator: widget.validator,
    );
  }
}
