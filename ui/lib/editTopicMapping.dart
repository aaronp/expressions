import 'package:flutter/material.dart';
import 'package:ui/verticalSplitView.dart';

import 'client/diskClient.dart';
import 'client/mappingCheck.dart';
import 'client/mappingEntry.dart';

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
  final _mappingTestResultsController = TextEditingController();

  String _topic = "";
  int _offset = 0;
  int _partition = 0;
  String _key = "";

  final _editorScrollController = ScrollController();
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
    _mappingTestResultsController.text = "";

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

  void _onTestMapping() {
    final request = TransformRequest(
        _codeTextController.text,
        _testInputController.text,
        _key,
        DateTime.now().millisecondsSinceEpoch,
        Map(),
        _topic,
        _offset,
        _partition);
    print(request.asJson);
    MappingCheck.check(request);
  }

  @override
  Widget build(BuildContext context) {
    // final MappingEntry args = ModalRoute.of(context).settings.arguments;
    return SafeArea(
        child: Scaffold(
      appBar: AppBar(
          title: Align(
              alignment: Alignment.topLeft,
              child: Text("Mapping for topic: ${entry.topic}")),
          backgroundColor: Theme.of(context).colorScheme.background,
          actions: [
            IconButton(onPressed: _saveMapping, icon: Icon(Icons.save))
          ]),
      body: VerticalSplitView(
          key: Key("split"),
          left: codeEditor(context),
          right: testingWidget(context)),
    ));
  }

  void _saveMapping() {}

  Widget testResults(BuildContext context) {
    return Card(
        color: Theme.of(context).colorScheme.background,
        child: Padding(
          padding: EdgeInsets.all(8.0),
          child: TextField(
            maxLines: 40,
            controller: _mappingTestResultsController,
            decoration: InputDecoration.collapsed(hintText: "// test results go here"),
          ),
        ));
  }

  Widget codeEditor(BuildContext context) {
    return Card(
        color: Theme.of(context).colorScheme.background,
        child: Padding(
          padding: EdgeInsets.all(8.0),
          child: TextField(
            maxLines: 120,
            controller: _codeTextController,
            decoration: InputDecoration.collapsed(hintText: DefaultCode),
          ),
        ));
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

  /**
   * The test widget
   */
  Widget testInput(BuildContext context) {
    final size = MediaQuery.of(context).size;
    // return Card(
    //     margin: EdgeInsets.fromLTRB(16, 16, 16, 0),
    //     child: Padding(
    //       padding: const EdgeInsets.all(16.0),
    //       child: ,
    //     ));
    return Container(
        constraints: BoxConstraints(
            maxHeight: size.height * 0.80, maxWidth: size.width * 0.75),
        child:
        testInputsForm()
    );
  }


  Form testInputsForm() {
    return Form(
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
    );
  }

  Widget testingWidget(BuildContext context) {
    return LayoutBuilder(builder: (ctxt, BoxConstraints constraints) {
      return SizedBox(
        width: constraints.maxWidth,
        height: constraints.maxHeight,
        child: SingleChildScrollView(
            child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
                "x: ${constraints.maxWidth}, maxH:${constraints.maxHeight}  minW:${constraints.minWidth} minH:${constraints.minHeight}"),
            testInput(ctxt),
            testResults(ctxt)
          ],
        )),
      );
    });
  }

  @override
  void dispose() {
    super.dispose();
    _fileNameController.dispose();
    _codeTextController.dispose();
    _mappingTestResultsController.dispose();
    _testInputController.dispose();
    _codeFocusNode.dispose();
    _editorScrollController.dispose();
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

  // final _textController = TextEditingController();

  @override
  void dispose() {
    // _textController.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      initialValue: widget.initialValue,
      focusNode: _focusNode,
      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.normal),
      cursorWidth: 1,
      cursorColor: Colors.black87,
      decoration: InputDecoration(
          hintText: widget.hintText,
          labelText: widget.labelText,
          labelStyle:
              const TextStyle(fontSize: 14, fontWeight: FontWeight.normal),
          hintStyle:
              const TextStyle(fontSize: 14, fontWeight: FontWeight.normal),
          errorStyle:
              const TextStyle(fontSize: 14, fontWeight: FontWeight.bold),
          errorMaxLines: 2),
      onSaved: widget.onUpdate,
      validator: widget.validator,
    );
  }
}
