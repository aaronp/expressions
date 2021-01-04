import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:kafkaui/rest_client.dart';

import 'model.dart';

class RepartitionDialog {
  static void onRepartition(BuildContext ctxt, String topic, int currentPartitions) {
    showDialog(
      context: ctxt,
      builder: (parentCtxt) {
        var validateOnly = false;
        var newPartitions = currentPartitions;

        final _partitionController =
        TextEditingController(text: currentPartitions.toString());

        final partitionField = TextFormField(
            controller: _partitionController,
            keyboardType: TextInputType.number,
            inputFormatters: <TextInputFormatter>[
              FilteringTextInputFormatter.digitsOnly
            ],
            decoration: InputDecoration(
              labelText: 'Partition',
              fillColor: Colors.white,
              border: new OutlineInputBorder(
                borderRadius: new BorderRadius.circular(10.0),
                borderSide: new BorderSide(),
              ),
            ),
            validator: (value) {
              var i = int.tryParse(value);
              if (i == null) {
                return null;
              } else {
                if (i > 100) {
                  return "That's a lot of partitions";
                } else {
                  return null;
                }
              }
            });

        return StatefulBuilder(
          builder: (context, setState) {
            Widget okButton = RaisedButton.icon(
              icon: Icon(Icons.golf_course),
              color: Colors.red,
              label: Text("Repartition"),
              onPressed: () async {
                final update = UpdatedPartition(newPartitions, []);
                final request = CreatePartitionRequest(
                    {topic: update}, validateOnly);
                final result = await RestClient.repartition(request);
                Scaffold.of(ctxt).showSnackBar(
                  SnackBar(
                    content: Text(
                        'Updated to $newPartitions partitions for $result'),
                    duration: const Duration(milliseconds: 1000),
                  ),
                );
              },
            );

            Widget cancelButton = RaisedButton(
              child: Text("Cancel"),
              onPressed: () {
                Navigator.of(context).pop();
              },
            );

            return AlertDialog(
              title: Text("Repartition"),
              content: Flexible(
                  child: Column(children: [
                    partitionField,
                    Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: CheckboxListTile(
                        title: Text("Validate Only"),
                        value: validateOnly,
                        onChanged: (newValue) {
                          setState(() {
                            validateOnly = !validateOnly;
                          });
                        },
                        controlAffinity: ListTileControlAffinity
                            .leading, //  <-- leading Checkbox
                      ),
                    )
                  ])),
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