import 'package:flutter/material.dart';

void main() => runApp(
  new MaterialApp(
    builder: (context, child) => new SafeArea(child: new Material(color: Colors.white, child: child)),
    home: new MyStatefulWidget(),
  ),
);

class MyStatefulWidget extends StatefulWidget {
  @override
  State<StatefulWidget> createState() => new MyStatefulWidgetState();

  static MyStatefulWidgetState of(BuildContext context) {
    final MyStatefulWidgetState navigator =
    context.findAncestorStateOfType<MyStatefulWidgetState>();

    assert(() {
      if (navigator == null) {
        throw new FlutterError(
            'MyStatefulWidgetState operation requested with a context that does '
                'not include a MyStatefulWidget.');
      }
      return true;
    }());

    return navigator;
  }
}

class MyStatefulWidgetState extends State<MyStatefulWidget> {
  String _string = "Not set yet";

  set string(String value) => setState(() => _string = value);

  @override
  Widget build(BuildContext context) {
    return new Column(
      children: <Widget>[
        new Text(_string),
        new MyChildClass(callback: (val) => setState(() => _string = val))
      ],
    );
  }
}

typedef void StringCallback(String val);

class MyChildClass extends StatelessWidget {
  final StringCallback callback;

  MyChildClass({this.callback});

  @override
  Widget build(BuildContext context) {
    return new Column(
      children: <Widget>[
        new FlatButton(
          onPressed: () {
            callback("String from method 1");
          },
          child: new Text("Method 1"),
        ),
        new FlatButton(
          onPressed: () {
            MyStatefulWidget.of(context).string = "String from method 2";
          },
          child: new Text("Method 2"),
        )
      ],
    );
  }
}
