import 'package:flutter/material.dart';
import 'package:kafkaui/config_widget.dart';

import 'home_widget.dart';
import 'list_topics_widget.dart';
import 'topic_widget.dart';
import 'topics_widget.dart';

void main() {
  runApp(KafkaUIApp());
}

class KafkaUIApp extends StatelessWidget {

  static final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

  static final lightTheme = ThemeData(
    primarySwatch: Colors.orange,
    accentColor: Colors.blue,
    // primaryColor: Colors.grey[700],
    // primaryColorBrightness: Brightness.dark,
    visualDensity: VisualDensity.adaptivePlatformDensity,
  );
  static final darkTheme = ThemeData(
    brightness: Brightness.dark
  );

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
        title: 'Kafka',
        initialRoute: HomeWidget.path,
        theme: lightTheme,
        darkTheme:  darkTheme,
        themeMode: ThemeMode.dark,
        navigatorKey: navigatorKey,
        routes: {
          HomeWidget.path: (context) => HomeWidget(),
          ConfigWidget.path: (context) => ConfigWidget(title: 'Config'),
          ListTopicsWidget.path: (context) => ListTopicsWidget(),
          TopicWidget.path: (context) => TopicWidget(() {}),
          TopicsWidget.path: (context) => TopicsWidget()
        });
  }
}
