import 'package:flutter/material.dart';
import 'package:ui/configPage.dart';
import 'package:ui/publishPage.dart';
import 'package:ui/runningPage.dart';

import 'common.dart';
import 'mainPage.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Franz',
      theme: ThemeData(brightness: Brightness.light),
      darkTheme: ThemeData(brightness: Brightness.dark),
      themeMode: ThemeMode.dark,
      debugShowCheckedModeBanner: false,
      // home: MainPage(title: 'Franz'),
    initialRoute: RouteNames.home,
    routes: {
      RouteNames.home: (context) => MainPage(),
      RouteNames.publish: (context) => PublishPage(),
      RouteNames.config: (context) => ConfigPage(),
      RouteNames.running: (context) => RunningPage(),
    }
    );
  }
}
