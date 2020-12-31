import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class ConfigSummaryWidget extends StatefulWidget {

  @override
  _ConfigSummaryWidgetState createState() => _ConfigSummaryWidgetState();
}

class _ConfigSummaryWidgetState extends State<ConfigSummaryWidget> {
  final _formKey = GlobalKey<FormState>();

  final _nameFocusNode = FocusNode();
  final _nameController = TextEditingController();

  @override
  Widget build(BuildContext context) {
    return Container();
  }
}
