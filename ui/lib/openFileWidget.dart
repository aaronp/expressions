import 'package:confirm_dialog/confirm_dialog.dart';
import 'package:flutter/material.dart';

import 'client/diskClient.dart';

/** Debug entry point */
void main() {
  runApp(MaterialApp(
      title: 'Franz',
      theme: ThemeData(brightness: Brightness.light),
      darkTheme: ThemeData(brightness: Brightness.dark),
      themeMode: ThemeMode.dark,
      debugShowCheckedModeBanner: true,
      home: OpenFileWidget()));
}

class OpenFileWidget extends StatefulWidget {
  @override
  _OpenFileWidgetState createState() => _OpenFileWidgetState();
}

class _OpenFileWidgetState extends State<OpenFileWidget> {
  @override
  Widget build(BuildContext context) {
    return FutureBuilder(
        future: DiskClient.listFiles("config"),
        builder: (ctxt, snapshot) {
          if (snapshot.hasData) {
            return listFiles(context, snapshot.data);
          } else if (snapshot.hasError) {
            return Center(child: Text("Computer says nope: ${snapshot.error}"));
          } else {
            return Center(child: CircularProgressIndicator());
          }
        });
  }

  Widget listFiles(BuildContext context, List<String> children) {
    children.sort();
    return AlertDialog(
      title: Text("${children.length} Files:"),
      content: listFilesContent(context, children),
      actions: <Widget>[
        OutlinedButton(
          child: Text("Cancel"),
          onPressed: () {
            Navigator.pop(context, "");
          },
        )
      ],
    );
  }

  Widget listFilesContent(BuildContext context, List<String> children) {
    return SizedBox(
        width: 400,
        height: 600,
        child: ListView.separated(
            itemBuilder: (_, index) => fileWidget(context, index, children[index]),
            separatorBuilder: (_, index) => Divider(),
            itemCount: children.length));
  }

  Widget fileWidget(BuildContext context, int index, String fileName) {
    return InkWell(
      child: Row(
        children: [
          IconButton(
              onPressed: () async => _onDelete(context, fileName),
              icon: Icon(Icons.delete_forever)),
          Text(fileName),
        ],
      ),
      onTap: () {
        Navigator.pop(context, fileName);
      },
    );
  }

  void _onDelete(BuildContext ctxt, String fileName) async {
    // TODO - confirm
    final future = confirm(ctxt,
      title: Text('Delete $fileName'),
      content: Text('Are you sure you want delete configuration "$fileName"?'));
    if (await future) {
      await DiskClient.remove("config/$fileName");
      // refresh
      setState(() {});
    }
  }
}
