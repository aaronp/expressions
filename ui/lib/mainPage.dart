import 'package:flutter/material.dart';

import 'common.dart';


class MainPage extends StatefulWidget {
  MainPage({Key key, this.title}) : super(key: key);
  final String title;

  @override
  _MainPageState createState() => _MainPageState();
}

class _MainPageState extends State<MainPage> {
  int _counter = 0;

  void _incrementCounter() {
    setState(() {
      _counter++;
    });
  }

  @override
  Widget build(BuildContext context) {
    final configButton = IconButton(
        iconSize: 32,
        tooltip: 'Config',
        icon: Icon(Icons.settings),
        color: Colors.red,
        onPressed: () async {
          await Navigator.pushNamed(context, RouteNames.config);
        });
    final publishButton = IconButton(
        iconSize: 32,
        tooltip: 'Publish',
        icon: Icon(Icons.send),
        color: Colors.red,
        onPressed: () async {
          await Navigator.pushNamed(context, RouteNames.publish);
        });

    final runningButton = IconButton(
        iconSize: 32,
        tooltip: 'Running',
        icon: Icon(Icons.run_circle),
        color: Colors.red,
        onPressed: () async {
          await Navigator.pushNamed(context, RouteNames.running);
        });

    return Scaffold(
      appBar: AppBar(title: Text('Franz'), actions: [
        publishButton,
        runningButton,
        configButton,
      ]),
      drawer: Drawer(
        // Add a ListView to the drawer. This ensures the user can scroll
        // through the options in the drawer if there isn't enough vertical
        // space to fit everything.
        child: ListView(
          // Important: Remove any padding from the ListView.
          padding: EdgeInsets.zero,
          children: <Widget>[
            DrawerHeader(
              child: Text('Drawer Header'),
              decoration: BoxDecoration(
                color: Colors.blue,
              ),
            ),
            ListTile(
              title: Text('Item 1'),
              onTap: () {
                // Update the state of the app.
                // ...
              },
            ),
            ListTile(
              title: Text('Item 2'),
              onTap: () {
                // Update the state of the app.
                // ...
              },
            ),
          ],
        ),
      ),
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
