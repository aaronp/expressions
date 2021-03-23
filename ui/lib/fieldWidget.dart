
import 'package:flutter/material.dart';

typedef OnUpdate = void Function(String value);


bool isNumber(String x) {
  try {
    int.parse(x);
    return true;
  } catch (e) {
    return false;
  }
}

String validateNumber(String x) {
  if (isNumber(x)) {
    return null;
  } else {
    return "'$x' is not a number";
  }
}

String textOk(String text) => null;

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