import 'package:flutter/material.dart';
import 'package:ui/client/configClient.dart';

class EditConfigWidget extends StatefulWidget {
  EditConfigWidget(this.configuration);

  String configuration;

  @override
  _EditConfigWidgetState createState() => _EditConfigWidgetState();
}

class _EditConfigWidgetState extends State<EditConfigWidget> {
  final _formKey = GlobalKey<FormState>();
  final _configTextController = TextEditingController();
  var _formattedLines = <String>[];

  @override
  void dispose() {
    super.dispose();
    _configTextController.dispose();
  }

  @override
  void initState() {
    super.initState();
    ConfigClient.formatConfig(this.widget.configuration).then((formattedLines) {
      setState(() {
        _formattedLines = formattedLines;
        _configTextController.text = _formattedLines.join("\n");
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final saveButton = IconButton(
        onPressed: () => onSave(context),
        icon: Icon(Icons.save));
    final cancelButton = IconButton(
        onPressed: () => onCancel(context),
        icon: Icon(Icons.cancel_outlined));
    return SafeArea(
        child: Scaffold(
            appBar: AppBar(
                title: Text("Edit Config"),
                backgroundColor: Colors.grey[800],
                actions: [saveButton, cancelButton]),
            body: LayoutBuilder(builder:
                (BuildContext context, BoxConstraints viewportConstraints) {
              return SingleChildScrollView(
                  child: ConstrainedBox(
                      constraints: BoxConstraints(
                          minHeight: viewportConstraints.maxHeight),
                      child: IntrinsicHeight(
                          child: Padding(
                        padding: EdgeInsets.all(8.0),
                        child: TextField(
                          controller: _configTextController,
                          maxLines: 180,
                          decoration: InputDecoration.collapsed(
                              hintText: "Configuration"),
                        ),
                      ))));
            })));
  }

  void onSave(BuildContext ctxt) {
    print("Popping");
    print(_configTextController.text);
    Navigator.pop(ctxt, _configTextController.text);
  }

  void onCancel(BuildContext ctxt) {
    print("Popping");
    print(_configTextController.text);
    Navigator.pop(ctxt, this.widget.configuration);
  }
}
