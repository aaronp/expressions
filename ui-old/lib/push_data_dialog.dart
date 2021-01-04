import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/rest_client.dart';

import 'model.dart';
import 'push_data.dart';

class PushDataDialog {
  static Future<ScaffoldFeatureController<SnackBar, SnackBarClosedReason>>
      pushData(BuildContext context, GlobalKey<ScaffoldState> scaffoldKey,
          PublishOne request) async {
    final response = await RestClient.push(request);
    final result = scaffoldKey.currentState.showSnackBar(SnackBar(
        duration: Duration(milliseconds: 100),
        content: Text(
            'Pushed ${response.topicPartition.partition}:${response.offset}')));
    return result;
  }

  static void show(
      BuildContext ctxt, GlobalKey<ScaffoldState> scaffoldKey, String topic) {
    showDialog(
      context: ctxt,
      builder: (parentCtxt) {
        GetPublishOneRequest getRequestCallback = () => null;

        return StatefulBuilder(
          builder: (context, setState) {
            ScaffoldFeatureController<SnackBar, SnackBarClosedReason>
                controller;
            Widget okButton = RaisedButton.icon(
              icon: Icon(Icons.add_circle_outlined),
              color: Colors.green,
              label: Text("Ok"),
              onPressed: () async {
                final request = getRequestCallback?.call();
                if (request != null) {
                  try {
                    controller?.close();
                    controller = await pushData(context, scaffoldKey, request);
                  } catch (e) {
                    print('error closing: $e');
                  }
                  //Navigator.of(parentCtxt).pop();
                }
              },
            );

            Widget cancelButton = RaisedButton(
              child: Text("Close"),
              onPressed: () {
                controller?.close();
                Navigator.of(context).pop();
              },
            );

            return AlertDialog(
              title: Text("Push Data"),
              content: PushDataWidget(topic, (getRequest) {
                getRequestCallback = getRequest;
              }),
              actions: <Widget>[
                cancelButton,
                Expanded(child: Container()),
                okButton
              ],
            );
          },
        );
      },
    );
  }
}
