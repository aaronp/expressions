import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/rest_client.dart';

import 'topic_widget.dart';

class ListTopicsWidget extends StatefulWidget {
  static const path = '/topics';

  ListTopicsWidget({Key key}) : super(key: key);

  @override
  _ListTopicsWidgetState createState() => _ListTopicsWidgetState();
}

class _ListTopicsWidgetState extends State<ListTopicsWidget> {
  var dontPromptForDelete = false;

  List<String> topics = [];

  @override
  void initState() {
    super.initState();
    RestClient.listTopics(true).then((value) => setState(() {
          topics = value;
        }));
  }

  @override
  Widget build(BuildContext context) {
    return LimitedBox(
        maxHeight: 100,
        maxWidth: 1000,
        child: CustomScrollView(
          slivers: <Widget>[
            SliverList(
              // Use a delegate to build items as they're scrolled on screen.
              delegate: SliverChildBuilderDelegate(
                // The builder function returns a ListTile with a title that
                // displays the index of the current item.
                (context, index) => ListTile(
                    leading: IconButton(
                        onPressed: () => _deleteTopic(context, topics[index]),
                        color: Colors.red[900],
                        icon: Icon(Icons.remove_circle)),
                    title: InkWell(child: Text(topics[index])),
                    onTap: () => _onTopic(context, topics[index])),
                // Builds 1000 ListTiles
                childCount: topics.length,
              ),
            ),
          ],
        ));
  }

  Future<void> _doDelete(String topic, bool dontPrompt) {
    return RestClient.deleteTopic(topic).then((value) => setState(() {
          if (value.contains(topic)) {
            topics.remove(topic);
          }
          dontPromptForDelete = dontPrompt;
        }));
  }

  _deleteTopic(BuildContext ctxt, String topic) {
    if (dontPromptForDelete) {
      _doDelete(topic, dontPromptForDelete);
    } else {
      _promptForDelete(ctxt, topic);
    }
  }

  _promptForDelete(BuildContext ctxt, String topic) {
    // show the dialog
    showDialog(
      context: ctxt,
      builder: (parentCtxt) {
        var dontPrompt = false;
        return StatefulBuilder(
          builder: (context, setState) {
            Widget okButton = RaisedButton.icon(
              icon: Icon(Icons.remove_circle),
              color: Colors.red,
              label: Text("Delete"),
              onPressed: () {
                _doDelete(topic, dontPrompt).then(
                    (value) => Navigator.pop(ctxt),
                    onError: (err) => {
                          Scaffold.of(ctxt).showSnackBar(
                            SnackBar(
                              content: Text("Error deleting '$topic': ${err}"),
                              duration: const Duration(milliseconds: 3000),
                            ),
                          )
                        });
              },
            );

            Widget cancelButton = RaisedButton(
              // color: Colors.red[400],
              child: Text("Cancel"),
              onPressed: () {
                Navigator.of(context).pop();
              },
            );

            return AlertDialog(
              title: Text("Confirm Delete"),
              content: Container(
                  width: 500,
                  height: 140,
                  child: Flexible(
                      fit: FlexFit.tight,
                      flex: 1,
                      child: Column(children: [
                        Padding(
                          padding: const EdgeInsets.all(8.0),
                          child: Text(
                              "Do you really want to delete topic '$topic'?"),
                        ),
                        Padding(
                          padding: const EdgeInsets.all(8.0),
                          child: CheckboxListTile(
                              title: Text("Don't ask me again"),
                              value: dontPrompt,
                              onChanged: (newValue) {
                                setState(() {
                                  dontPrompt = !dontPrompt;
                                });
                              },
                              controlAffinity: ListTileControlAffinity.leading),
                        )
                      ]))),
              actions: <Widget>[cancelButton, okButton],
            );
          },
        );
      },
    );
  }

  _onTopic(BuildContext context, String topic) {
    Navigator.pushNamed(context, TopicWidget.path, arguments: topic);
  }
}
