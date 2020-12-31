import 'package:flutter/material.dart';


class RunningPage extends StatefulWidget {

  RunningPage({Key key, this.title}) : super(key: key);
  final String title;

  @override
  _RunningPageState createState() => _RunningPageState();
}

class _RunningPageState extends State<RunningPage> {
  int _counter = 0;

  void _incrementCounter() {
    setState(() {
      _counter++;
    });
  }

  @override
  Widget build(BuildContext context) {
    final configButton = RaisedButton(onPressed: () {}, child: Text('Config'));
    final config2Button = RaisedButton(onPressed: () {}, child: Text('Config Two'));
    final publishButton = IconButton(
        iconSize: 32,
        tooltip: 'Publish',
        icon: Icon(Icons.send),
        color: Colors.red,
        onPressed: () async {
          //await Navigator.pushNamed(context, ChooseRace.path);
        });

    final runningButton = IconButton(
        iconSize: 32,
        tooltip: 'Running',
        icon: Icon(Icons.run_circle),
        color: Colors.red,
        onPressed: () {});

    return Scaffold(
      appBar: AppBar(title: Text('Running'), actions: [
        publishButton,
        runningButton,
        config2Button,
        configButton,
      ]),
      body: Center(
        // Center is a layout widget. It takes a single child and positions it
        // in the middle of the parent.
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Text(
              'You have pushed the button this many times:',
            ),
            Text(
              '$_counter',
              style: Theme.of(context).textTheme.headline4,
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _incrementCounter,
        tooltip: 'Increment',
        child: Icon(Icons.add),
      ), // This trailing comma makes auto-formatting nicer for build methods.
    );
  }
}
