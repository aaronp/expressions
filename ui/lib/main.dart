import 'package:flutter/material.dart';
import 'package:ui/configPage.dart';
import 'package:ui/runningPage.dart';

import 'routeNames.dart';

void main() {
  runApp(FranzApp());
}

class FranzApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Franz',
      theme: ThemeData(brightness: Brightness.light),
      darkTheme: ThemeData(brightness: Brightness.dark),
      themeMode: ThemeMode.dark,
      debugShowCheckedModeBanner: false,
    initialRoute: RouteNames.config,
    routes: {
      RouteNames.config: (context) => ConfigPage(),
      // RouteNames.publish: (context) => PublishPage("",""),
      RouteNames.running: (context) => RunningPage(),
    }
    );
  }
}
