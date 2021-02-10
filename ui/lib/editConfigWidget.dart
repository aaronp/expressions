import 'package:flutter/material.dart';
import 'package:ui/client/configClient.dart';

import 'client/mappingEntry.dart';

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
    // final MappingEntry args = ModalRoute.of(context).settings.arguments;
    return SafeArea(child: Scaffold(
        appBar: AppBar(
            title: Text("Edit Config"),
            backgroundColor: Colors.grey[800],
            actions: [
            ]),
        body: Card(
            color: Colors.grey,
            child: Padding(
              padding: EdgeInsets.all(8.0),
              child: TextField(
                controller: _configTextController,
                maxLines: 80,
                decoration: InputDecoration.collapsed(hintText: "Configuration"),
              ),
            )
        ) // This trailing comma makes auto-formatting nicer for build methods.
    ));
  }
}
