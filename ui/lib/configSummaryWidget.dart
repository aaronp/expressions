import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'client/configSummary.dart';
import 'client/mappingEntry.dart';
import 'editZIOMapping.dart';

// TODO - share this  w/ the ConfigPage
class ConfigSummaryWidget extends StatelessWidget {
  ConfigSummaryWidget(this.summary);

  ConfigSummary summary;

  @override
  Widget build(BuildContext context) {
    return Container(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.max,
        children: [
          configEntry("Brokers", summary.brokersAsString()),
          configEntry("Topic", summary.topic),
          configEntry("Key Type", summary.keyType),
          configEntry("Value Type", summary.valueType),
          Container(
            height: 400.0,
            alignment: Alignment.topLeft,
            child: mappingsWidget(context),
          )
        ],
      ),
    );
  }

  static const labelStyle = TextStyle(
    fontSize: 14.0,
    fontWeight: FontWeight.bold,
    color: Colors.white,
  );
  static const mappingStyle = TextStyle(
    fontSize: 18.0,
    fontWeight: FontWeight.bold,
    color: Colors.white,
  );

  Widget configEntry(String label, String value) {
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Container(
          width: 800,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.start,
            children: [
              Container(
                  width: 100,
                  child: Text("$label :", style: labelStyle),
                  alignment: AlignmentDirectional.topEnd),
              SelectableText(value),
            ],
          )),
    );
  }

  Widget mappingsWidget(BuildContext ctxt) {
    return Column(
        mainAxisAlignment: MainAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            // width: 200,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(8.0, 28.0, 8.0, 8.0),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(8.0, 8.0, 8.0, 8.0),
                      child: Text("Topic Mappings:", style: mappingStyle),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(8.0, 8.0, 18.0, 8.0),
                      child: OutlinedButton.icon(
                          onPressed: () => {
                            // onAddMapping(ctxt)
                          },
                          icon: Icon(Icons.add, color: Colors.white),
                          style: ButtonStyle(
                              backgroundColor: MaterialStateProperty.all<Color>(
                                  Colors.grey[700])),
                          label: Text('Add')),
                    )
                  ],
                ),
              ),
              alignment: AlignmentDirectional.topStart),
          // mappingsList(ctxt),
        ]);
  }

// Future<Object> _push(BuildContext ctxt, Widget page) async {
//   return await Navigator.push(
//       ctxt, MaterialPageRoute(builder: (context) => page));
// }
//
// void onEditMapping(BuildContext ctxt, MappingEntry entry) =>
//     _push(ctxt, EditZIOMappingWidget(entry, _currentConfig.loadedContent));
//
// void onEditHttpMapping(BuildContext ctxt, MappingEntry entry) =>
//     _push(ctxt, EditTopicMappingWidget(entry));
//
// void onAddMapping(BuildContext ctxt) =>
//     _push(ctxt, EditZIOMappingWidget(MappingEntry("new topic name", ""), _currentConfig.loadedContent));
}