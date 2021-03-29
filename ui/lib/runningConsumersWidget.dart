import 'package:flutter/material.dart';
import 'package:ui/client/startedConsumer.dart';

import 'client/batchClient.dart';
import 'client/kafkaClient.dart';

class RunningConsumersWidget extends StatefulWidget {
  @override
  _RunningConsumersWidgetState createState() => _RunningConsumersWidgetState();
}

class _RunningConsumersWidgetState extends State<RunningConsumersWidget> {
  final _formKey = GlobalKey<FormState>();
  final _fileNameController = TextEditingController();

  @override
  void dispose() {
    super.dispose();
    _fileNameController.dispose();
  }

  @override
  void initState() {
    super.initState();
  }

  Future<List<StartedConsumer>> running() async {
    final List<StartedConsumer> bList = await BatchClient.running();
    final List<StartedConsumer> kList = await KafkaClient.running();
    bList.addAll(kList);
    return bList;
  }

  @override
  Widget build(BuildContext context) {
    // final MappingEntry args = ModalRoute.of(context).settings.arguments;
    return SafeArea(
        child: Scaffold(
            appBar: AppBar(
                title: Text("Running Consumers"),
                backgroundColor: Colors.grey[800],
                actions: []),
            body: Center(
              // Center is a layout widget. It takes a single child and positions it
              // in the middle of the parent.
              child: FutureBuilder(
                  future: running(),
                  builder: (ctxt, snapshot) {
                    if (snapshot.hasData) {
                      return listWidget(snapshot.data);
                    } else if (snapshot.hasError) {
                      return Center(
                          child: Text("Computer says nope: ${snapshot.error}"));
                    } else {
                      return Center(child: CircularProgressIndicator());
                    }
                  }),
            ) // This trailing comma makes auto-formatting nicer for build methods.
            ));
  }

  Widget listWidget(List<StartedConsumer> data) {
    return Center(child: Text("${data.length} data: $data"));
  }
}
