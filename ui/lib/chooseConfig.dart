import 'package:flutter/material.dart';

import 'client/client.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Choose Config',
      theme: ThemeData(brightness: Brightness.light),
      darkTheme: ThemeData(brightness: Brightness.dark),
      themeMode: ThemeMode.dark,
      debugShowCheckedModeBanner: false,
      home: Scaffold(body: SafeArea(child : Center(child : ChooseConfig()))),
    );
  }
}

class ChooseConfig extends StatefulWidget {
  @override
  _ChooseConfigState createState() => _ChooseConfigState();
}

class _ChooseConfigState extends State<ChooseConfig> {
  var chosenConfig = "One";

  @override
  Widget build(BuildContext context) {
    return FutureBuilder(
        future: Client.listFiles("config"),
        builder: (ctxt, snapshot) {
          if (snapshot.hasData) {
            return chooseFile(ctxt, snapshot.data);
            // return Text("wtf");
            // return wtf();
          } else if (snapshot.hasError) {
            return Center(child: Text("Error: ${snapshot.error}"));
          } else {
            return Center(child: CircularProgressIndicator());
          }
        });
  }

  Widget wtf() {
    return DropdownButton<String>(
      value: chosenConfig,
      icon: Icon(Icons.arrow_downward),
      iconSize: 24,
      elevation: 16,
      style: TextStyle(color: Colors.deepPurple),
      underline: Container(
        height: 2,
        color: Colors.deepPurpleAccent,
      ),
      onChanged: (String newValue) {
        setState(() {
          chosenConfig = newValue;
        });
      },
      items: <String>['One', 'Foo', 'Free', 'Four']
          .map<DropdownMenuItem<String>>((String value) {
        return DropdownMenuItem<String>(
          value: value,
          child: Text(value),
        );
      }).toList(),
    );
  }

  Widget chooseFile(BuildContext context, List<dynamic> files) {
    return Container(
        child: Row(
          children: [

            DropdownButton<String>(
                value: chosenConfig,
                icon: Icon(Icons.arrow_downward),
                // isExpanded: true,
                onChanged: (String newValue) {
                  setState(() {
                    chosenConfig = newValue;
                  });
                },
                items: <String>['One', 'Foo', 'Free', 'Four']
                    .map<DropdownMenuItem<String>>((String value) {
                  return DropdownMenuItem<String>(
                    value: value,
                    child: Text(value),
                  );
                }).toList()),
          ],
        ));
  }

  Widget chooseFile2(BuildContext context, List<dynamic> files) {
    print("got $files");

    var i = 0;
    List<DropdownMenuItem<String>> defaultList = [
      dropdownMenuItem("default$i", "default config")
    ];
    // final fileItems = files.map<DropdownMenuItem<String>>((dynamic value) {
    //   i++;
    //   return dropdownMenuItem(i.toString(), value.toString());
    // }).toList();
    final choices = defaultList; //+ fileItems;

    print("${choices.length} choices is $choices");
    choices.forEach((element) {
      print("  CHOICE: ${element.value} w/ ''");
    });
    return Container(
        child: DropdownButton<String>(
            value: chosenConfig,
            icon: Icon(Icons.arrow_downward),
            // isExpanded: true,
            onChanged: (String newValue) {
              setState(() {
                chosenConfig = newValue;
              });
            },
            items: choices));
  }

  DropdownMenuItem<String> dropdownMenuItem(String value, String label) {
    return DropdownMenuItem<String>(
      value: value,
      child: Text(label),
    );
  }
}
