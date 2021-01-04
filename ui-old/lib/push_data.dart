import 'dart:convert';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';

import 'model.dart';

void main() => runApp(
      MaterialApp(
        builder: (context, child) =>
            SafeArea(child: new Material(color: Colors.white, child: child)),
        home: Scaffold(
          // body: ConsumerDataWidget('topiceae5c8f8a3054d19a132cb3033031e49'),
          body: PushDataWidget(
              'topiceae5c8f8a3054d19a132cb3033031e49', (r) => {}),
        ),
      ),
    );

typedef GetPublishOneRequest = PublishOne Function();
typedef GetPublishOneRequestCallback = void Function(
    GetPublishOneRequest callback);

class PushDataWidget extends StatefulWidget {
  PushDataWidget(this.topic, this.setCallback);

  GetPublishOneRequestCallback setCallback;

  final String topic;

  @override
  _PushDataWidgetState createState() => _PushDataWidgetState();
}

class _PushDataWidgetState extends State<PushDataWidget> {
  final _formKey = GlobalKey<FormState>();

  ScrollController _controller = ScrollController();
  final _valueBodyController = TextEditingController();
  final _keyController = TextEditingController();
  final _partitionController = TextEditingController();

  PlatformFile _uploadFile;

  // remember the previous uploaded file key
  String _uploadFileName;
  Widget _valueWidget;

  @override
  void initState() {
    super.initState();
    _valueWidget = defaultValueWidget();
    widget.setCallback(() => request);
  }

  PublishOne get request {
    if (_formKey.currentState.validate()) {
      final isBase64 = _uploadFile != null;

      var data = _valueBodyController.text;
      if (_uploadFile != null) {
        data = base64Encode(_uploadFile.bytes);
      }
      return PublishOne(widget.topic, _keyController.text, data,
          int.tryParse(_partitionController.text) ?? null, isBase64);
    } else {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return scrollableContent(Form(
        key: _formKey,
        child: Padding(
          padding: const EdgeInsets.all(8.0),
          child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: partitionField(),
                ),
                Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: keyField(),
                ),
                _valueWidget
              ]),
        )));
  }

  Widget defaultValueWidget() {
    return Column(children: [
      Padding(
        padding: const EdgeInsets.all(8.0),
        child: textContentValueField(),
      ),
      filePickerWidget()
    ]);
  }

  Widget partitionField() {
    return TextFormField(
        controller: _partitionController,
        keyboardType: TextInputType.number,
        inputFormatters: <TextInputFormatter>[
          FilteringTextInputFormatter.digitsOnly
        ],
        decoration: InputDecoration(
          labelText: 'Partition',
          hintText: '(optional)',
          icon: new Icon(FontAwesomeIcons.chartPie),
          fillColor: Colors.white,
          border: new OutlineInputBorder(
            borderRadius: new BorderRadius.circular(10.0),
            borderSide: new BorderSide(),
          ),
        ),
        validator: (value) {
          var i = int.tryParse(value);
          if (i == null) {
            return null;
          } else {
            if (i > 100) {
              return "That's a lot of partitions";
            } else {
              return null;
            }
          }
        });
  }

  Scrollbar scrollableContent(Widget inner) {
    return Scrollbar(
        isAlwaysShown: true,
        thickness: 5,
        radius: Radius.circular(4),
        controller: _controller,
        child: SingleChildScrollView(
            child: ConstrainedBox(
                constraints: BoxConstraints(
                  minHeight: 100,
                ),
                child: Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: inner,
                ))));
  }

  Widget filePickerWidget() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      key: ValueKey('filePicker'),
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(8.0, 0, 10.0, 0),
          child: Text('Or Upload Contents:',
              style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.normal,
                  fontFamily: 'Roboto Bold')),
        ),
        Container(
            padding: const EdgeInsets.fromLTRB(0.0, 0, 20.0, 0),
            child: RaisedButton.icon(
              icon: Icon(FontAwesomeIcons.folderOpen),
              label: Text('Choose File'),
              onPressed: () => pickFile(),
            )),
      ],
    );
  }

  Widget recordBodyWidget() {
    return Row(
      key: ValueKey('recordBody'),
      children: [
        Container(
          padding: EdgeInsets.all(20.0),
          width: 400.0,
          margin: const EdgeInsets.only(right: 50, left: 50),
          child: textContentValueField(),
        ),
      ],
    );
  }

  Widget textContentValueField() {
    return TextFormField(
        maxLines: 10,
        controller: _valueBodyController,
        decoration: InputDecoration(
          labelText: 'Record Value',
          // floatingLabelBehavior: FloatingLabelBehavior.always,
          icon: new Icon(Icons.text_fields),
          fillColor: Colors.white,
          border: new OutlineInputBorder(
            borderRadius: new BorderRadius.circular(25.0),
            borderSide: new BorderSide(),
          ),
        ),
        validator: (value) {
          if ((value ?? '').isEmpty && _uploadFile == null) {
            return "The value cannot be blank - either choose a file or enter some text";
          } else {
            return null;
          }
        });
  }

  Widget keyField() {
    return TextFormField(
        autofocus: true,
        controller: _keyController,
        decoration: InputDecoration(
          labelText: 'Record Key',
          icon: new Icon(Icons.vpn_key),
          fillColor: Colors.white,
          border: new OutlineInputBorder(
            borderRadius: new BorderRadius.circular(10.0),
            borderSide: new BorderSide(),
          ),
        ),
        validator: (value) {
          if ((value ?? '').isEmpty) {
            return "The key cannot be blank";
          } else {
            return null;
          }
        });
  }

  void pickFile() async {
    FilePickerResult result =
        await FilePicker.platform.pickFiles(allowMultiple: false);

    if (result != null) {
      setState(() {
        if (result.files.isEmpty) {
          resetValueWidget();
        } else {
          _uploadFile = result.files.single;
          if ((_keyController?.text ?? '').isEmpty ||
              _keyController.text == _uploadFileName) {
            _keyController.text = _uploadFile.name;
          }
          _uploadFileName = _uploadFile.name;
          _valueWidget = uploadedFileWidget();
        }
      });
    }
  }

  Widget uploadedFileWidget() {
    return Row(
      children: [
        RichText(
          text: TextSpan(
            children: [
              WidgetSpan(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(8.0, 0, 0, 0),
                  child: Icon(Icons.attachment, size: 24),
                ),
              ),
              // WidgetSpan(child : Text('File: ')),
              WidgetSpan(
                  child: Padding(
                padding: const EdgeInsets.fromLTRB(22.0, 12, 0, 8),
                child: Text('Record Value:  ${_uploadFile.name}',
                    style: TextStyle(fontSize: 14)),
              )),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(10, 0, 0, 0),
          child: IconButton(
              icon: Icon(Icons.remove_circle, color: Colors.red),
              onPressed: onRemoveUpload),
        ),
      ],
    );
  }

  void onRemoveUpload() {
    setState(() {
      resetValueWidget();
    });
  }

  void resetValueWidget() {
    _uploadFile = null;
    _uploadFileName = null;
    _valueWidget = defaultValueWidget();
  }
}
