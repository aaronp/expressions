import 'package:flutter/material.dart';
import 'package:ui/client/configClient.dart';
import 'package:ui/client/consumerStats.dart';
import 'package:ui/client/startedConsumer.dart';

import 'client/batchClient.dart';
import 'client/kafkaClient.dart';
import 'configSummaryWidget.dart';

class RunningConsumersWidget extends StatefulWidget {
  @override
  _RunningConsumersWidgetState createState() => _RunningConsumersWidgetState();
}

class _RunningConsumersWidgetState extends State<RunningConsumersWidget> {
  int _batchCount = 0;
  int _simpleCount = 0;
  PersistentBottomSheetController bottomSheet;

  static final BatchTitle = "Batch";

  @override
  Widget build(BuildContext context) {
    // final MappingEntry args = ModalRoute.of(context).settings.arguments;
    return SafeArea(
        child: Scaffold(
      appBar: AppBar(
          title: Align(
              alignment: Alignment.topLeft, child: Text("Running Consumers")),
          backgroundColor: Colors.grey[800],
          actions: []),
      body: listedWidget(_batchCount, BatchTitle, BatchClient.running()),
    ));
  }

  Widget listedWidget(
      int count, String title, Future<List<StartedConsumer>> running) {
    print(
        "listed Widget $title with $count, _batchCount=$_batchCount, simple=$_simpleCount");
    return FutureBuilder(
        future: running,
        builder: (ctxt, snapshot) {
          if (snapshot.hasData) {
            return listWidget(title, snapshot.data);
          } else if (snapshot.hasError) {
            return Center(child: Text("Computer says nope: ${snapshot.error}"));
          } else {
            return Center(child: CircularProgressIndicator());
          }
        });
  }

  Widget listWidget(String title, List<StartedConsumer> data) {
    if (data.length > 0) {
      return nonZeroListWidget(title, data);
    } else {
      return Center(child: Text("No $title Running"));
    }
  }

  Widget nonZeroListWidget(String title, List<StartedConsumer> data) {
    final isBatch = title == BatchTitle;
    return LayoutBuilder(builder: (ctxt, constraints) {
      return SizedBox(
          width: constraints.maxWidth,
          height: constraints.maxHeight / 2,
          child: ListView.separated(
              itemBuilder: (_, index) => index == 0
                  ? Padding(
                      padding: const EdgeInsets.all(8.0),
                      child: Text(title),
                    )
                  : processWidget(ctxt, index, isBatch, data[index - 1]),
              separatorBuilder: (_, index) => Divider(),
              itemCount: data.length));
    });
  }

  Widget processWidget(
      BuildContext ctxt, int index, bool isBatch, StartedConsumer data) {
    final started = DateTime.fromMillisecondsSinceEpoch(data.startedAtEpoch);
    final diff = DateTime.now().difference(started);

    final link = InkWell(
        child: Text(
            "Job '${data.id}' started ${diff.inMinutes} minutes ago at $started",
            style: const TextStyle(color: Colors.blue)),
        onTap: () => onShowEntry(ctxt, data, isBatch));
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Row(
        children: [
          link,
          Padding(
            padding: const EdgeInsets.fromLTRB(8.0, 0, 0, 0),
            child: OutlinedButton.icon(
                onPressed: () => onCancelJob(data, isBatch),
                icon: Icon(Icons.cancel_outlined),
                label: Text("Stop")),
          )
        ],
      ),
    );
  }

  void onCancelJob(StartedConsumer data, bool isBatch) async {
    bool stopped = false;
    if (isBatch) {
      stopped = await BatchClient.stop(data.id);
    } else {
      stopped = await KafkaClient.stop(data.id);
    }

    final msg =
        stopped ? "Stopped job ${data.id}" : "Couldn't stop job ${data.id}";
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    setState(() {
      if (isBatch) {
        _simpleCount = _simpleCount + 1;
      } else {
        _batchCount = _batchCount + 1;
      }
    });
  }

  void onShowEntry(
      BuildContext ctxt, StartedConsumer data, bool isBatch) async {
    ConsumerStats stats;
    if (isBatch) {
      stats = await BatchClient.stats(data.id);
    } else {
      stats = await KafkaClient.stats(data.id);
    }
    final cfgSummary = await ConfigClient.configSummary(data.config);

    final PersistentBottomSheetController controller = showBottomSheet(
        context: ctxt,
        elevation: 12,
        backgroundColor: Theme.of(ctxt).backgroundColor,
        builder: (context) => LayoutBuilder(builder: (c, constraints) {
              final records = stats.recentRecords
                  .map((e) => recordSummaryWidget(e))
                  .toList();
              final errors =
                  stats.errors.map((e) => recordSummaryWidget(e)).toList();

              // final h = constraints.maxHeight / 0.4;
              return Container(
                  height: 600,
                  constraints: BoxConstraints(maxHeight: 600),
                  child: Scrollbar(
                      child: SingleChildScrollView(
                          child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Align(
                          alignment: Alignment.topRight,
                          child: Padding(
                            padding: const EdgeInsets.all(8.0),
                            child: ElevatedButton.icon(
                                onPressed: () => onCloseBottomSheet(),
                                icon: Icon(Icons.close),
                                label: Text("Close")),
                          )),
                      ...records,
                      ...errors,
                      Flexible(
                          child: Text("Total consumed: ${stats.totalRecords}")),
                      Flexible(child: ConfigSummaryWidget(cfgSummary)),
                      // Flexible(
                      //     child: Text(
                      //       "Config:",
                      //       style: const TextStyle(fontWeight: FontWeight.bold),
                      //     )),
                      // Expanded(child: Text(data.config)),
                    ],
                  ))));
            }));
    bottomSheet = controller;
    print(stats);
  }

  void onCloseBottomSheet() {
    if (bottomSheet != null) {
      bottomSheet.close();
      setState(() {
        bottomSheet = null;
      });
    }
  }

  Widget recordSummaryWidget(RecordSummary summary) {
    return Flexible(
        child: Text(
            "${summary.record.partition}:${summary.record.offset} : ${summary.record.key}"));
  }
}
