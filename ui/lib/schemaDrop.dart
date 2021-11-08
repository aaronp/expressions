import 'package:drop_zone/drop_zone.dart';
import 'dart:html' as html;

import 'package:flutter/material.dart';
void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Flutter Demo',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: MyHomePage(title: 'Flutter Demo Home Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  MyHomePage({Key key, this.title}) : super(key: key);

  final String title;

  @override
  _MyHomePageState createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  String dropzoneState = '';
  String dropzoneState2 = '';

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            DropZone(
              onDragEnter: () {
                print('drag enter');
                setState(() {
                  dropzoneState = 'drag enter';
                });
              },
              onDragExit: () {
                print('drag exit');
                setState(() {
                  dropzoneState = 'drag exit';
                });
              },
              onDrop: (List<html.File> files) {
                print('files dropped');
                print(files);
                setState(() {
                  dropzoneState = 'files dropped $files';
                });
              },
              child: Container(
                decoration: BoxDecoration(border: Border.all()),
                width: 300,
                height: 300,
              ),
            ),
            Text(dropzoneState),
            Container(
                width: 300,
                height: 300,
                child: Locationstat()),
          ],
        ),
      ),
    );
  }
}


class Locationstat extends StatefulWidget {
  @override
  _LocationstatState createState() => _LocationstatState();
}

class _LocationstatState extends State<Locationstat>
    with SingleTickerProviderStateMixin {
  TabController _tabController;
  @override
  void initState() {
    super.initState();
    _tabController = new TabController(length: 2, vsync: this);
  }
  @override
  void dispose() {
    super.dispose();
    _tabController.dispose();
  }
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Colors.white,
        elevation: 0,
        title: Text('Statistics'),

        bottom: TabBar(
            controller: _tabController,
            indicatorColor: Colors.orange,
            labelColor: Colors.orange,
            unselectedLabelColor: Colors.black54,
            tabs: <Widget>[
              Tab(
                text:('Pokhara Lekhnath'),
              ),
              Tab(
                text:('Outside Pokhara-Lekhnath'),
              ),
            ]),
      ),
      body: TabBarView(
        children: <Widget>[
          NestedTabBar(),
          NestedTabBar(),

        ],
        controller: _tabController,
      ),
    );
  }
}




class NestedTabBar extends StatefulWidget {
  @override
  _NestedTabBarState createState() => _NestedTabBarState();
}
class _NestedTabBarState extends State<NestedTabBar>
    with TickerProviderStateMixin {
  TabController _nestedTabController;
  @override
  void initState() {
    super.initState();
    _nestedTabController = new TabController(length: 2, vsync: this);
  }
  @override
  void dispose() {
    super.dispose();
    _nestedTabController.dispose();
  }
  @override
  Widget build(BuildContext context) {
    double screenHeight = MediaQuery.of(context).size.height;
    return Column(
      mainAxisAlignment: MainAxisAlignment.spaceAround,
      children: <Widget>[
        TabBar(
          controller: _nestedTabController,
          indicatorColor: Colors.orange,
          labelColor: Colors.orange,
          unselectedLabelColor: Colors.black54,
          isScrollable: true,
          tabs: <Widget>[
            Tab(
              text: "Inside Pokhara",
            ),
            Tab(
              text: "Outside Pokhara",
            ),

          ],
        ),
        Container(
          height: screenHeight * 0.70,
          margin: EdgeInsets.only(left: 16.0, right: 16.0),
          child: TabBarView(
            controller: _nestedTabController,
            children: <Widget>[
              Container(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8.0),
                  color: Colors.blueGrey[300],
                ),
              ),
              Container(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(8.0),
                  color: Colors.blueGrey[300],
                ),
              ),

            ],
          ),
        )
      ],
    );
  }
}