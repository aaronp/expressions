import 'dart:convert';
import 'dart:html' as darthtml;

// import 'dart:html';
import 'dart:math';
import 'dart:typed_data';

import 'package:drop_zone/drop_zone.dart';
import 'package:flutter/material.dart';
import 'package:flutter_spinbox/material.dart';

import 'Consts.dart';
import 'client/configSummary.dart';
import 'client/dataGenClient.dart';
import 'client/postRecord.dart';
import 'client/topicData.dart';
import 'client/topics.dart';
import 'fieldWidget.dart';

void main() {
  runApp(MaterialApp(
      title: 'Franz',
      theme: ThemeData(brightness: Brightness.light),
      darkTheme: ThemeData(brightness: Brightness.dark),
      themeMode: ThemeMode.dark,
      debugShowCheckedModeBanner: false,
      home: PublishWidget(
          "foo",
          ConfigSummary("bar", {}, [], "string", "string", "string", "string"),
          "")));
}

typedef OnTabChange = void Function(int value);
typedef OnUpdateValue = void Function(dynamic jason);
typedef OnUpload = void Function(Uint8List data);

class PublishWidget extends StatefulWidget {
  PublishWidget(this.title, this.summary, this.configuration, {Key key})
      : super(key: key);
  final String title;
  final ConfigSummary summary;
  final String configuration;

  static const TabNames = Consts.SupportedTypes;

  @override
  _PublishWidgetState createState() => _PublishWidgetState();
}

class _PublishWidgetState extends State<PublishWidget> {
  final _formKey = GlobalKey<FormState>();

  int _selectedTabIndex = 0;
  int _repeat = 1;
  ConfigSummary _summary;
  String _partitionOverride = "";

  int _keyTabIndex = 0;
  dynamic _latestKeyValue = "";
  dynamic _latestValueValue = "";
  int _valueTabIndex = 0;

  Topics _topics = Topics([], [], []);
  TopicData _topic = TopicData.empty();

  String testDataForType(String t) {
    final safe = t.toLowerCase().trim();
    if (safe == "string") {
      return "text-${(DateTime.now().millisecondsSinceEpoch % 337).toString()}";
    } else if (safe == "long") {
      return (DateTime.now().millisecondsSinceEpoch % 337).toString();
    } else if (safe.startsWith("avro")) {
      //TopicData.get(t)
      return "some avro...";
    } else {
      return "dunno for $t";
    }
  }

  // don't judge me
  int tabIndexForType(String type) {
    final clean = type.trim().toLowerCase();
    final index = PublishWidget.TabNames.indexWhere(
        (tabName) => clean.startsWith(tabName.toLowerCase()));
    if (index < 0) {
      return 0;
    } else {
      return index;
    }
  }

  @override
  void initState() {
    super.initState();

    _keyTabIndex = tabIndexForType(this.widget.summary.keyType);
    _valueTabIndex = tabIndexForType(this.widget.summary.valueType);

    _summary = widget.summary;
    // _keyTextController.text = _topic.hasKeySchema() ? _topic.keyData().testData.toString() : testDataForType(_summary.keyType);
    // _valueTextController.text = _topic.hasValueSchema() ? _topic.valueData().testData.toString() : testDataForType(_summary.valueType);

    _reload(false);
  }

  Widget keyWidget() {
    return TabbedWidget(_topics, _keyTabIndex, _latestKeyValue, true, (idx) {
      _keyTabIndex = idx;
    }, (newValue) {
      _latestKeyValue = newValue;
    }, (uploadData) async {
      DataGenClient.dataAsJson(uploadData).then((jason) async {
        _latestKeyValue = jason.toString();
        final Future<Null> ok =
            Future.delayed(const Duration(milliseconds: 100), () {
          setState(() {
            _latestKeyValue = jason;
          });
        });
        await ok;
      });
    }, key: Key("key$_keyTabIndex${_latestKeyValue.hashCode}"));
  }

  Widget valueWidget() {
    return TabbedWidget(_topics, _valueTabIndex, _latestValueValue, false,
        (idx) {
      _valueTabIndex = idx;
    }, (newValue) {
      print("onValueChange >>>> Value is $newValue");
      _latestValueValue = newValue;
    }, (uploadData) async {
      final jason = await DataGenClient.dataAsJson(uploadData);
      setState(() {
        _latestValueValue = jason;
      });
    }, key: Key("value$_valueTabIndex${_latestValueValue.hashCode}"));
  }

  void reloadTopic() {
    TopicData.get(_summary.topic).then((value) {
      setState(() {
        _topic = value;
      });
    });
  }

  void _reload(bool force) {
    reloadTopic();
    Topics.get().then((found) {
      setState(() {
        _topics = found;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
        onWillPop: () => onBack(context), child: buildInner(context));
  }

  String _typeForIndex(int idx) => PublishWidget.TabNames[idx];

  // if the user changed the publish type, we should reflex that in the config page
  Future<bool> onBack(BuildContext context) async {
    final String newKeyType = _typeForIndex(this._keyTabIndex);
    final String newValueType = _typeForIndex(this._valueTabIndex);
    final newSummary = this.widget.summary.withTypes(newKeyType, newValueType);
    Navigator.pop(context, newSummary);
    return false;
  }


  Widget buildInner(BuildContext context) {
    return SafeArea(
        child: Scaffold(
      appBar: AppBar(
          title: Align(
              alignment: Alignment.topLeft,
              child: Text("Publish to '${this.widget.summary.topic}'")),
          backgroundColor: Colors.grey[800],
          actions: []),
      floatingActionButton: FloatingActionButton.extended(
        label: Text('Publish'),
        icon: Icon(Icons.publish),
        backgroundColor: Colors.orange,
        onPressed: onPublish,
      ),
      body: publishLayout(context), //publishFormWidget()
    ));
  }

  PostRecord asPostRecord(dynamic key, dynamic value) {
    return PostRecord(
        value,
        widget.configuration,
        key,
        _repeat,
        isNumber(_partitionOverride) ? int.parse(_partitionOverride) : null,
        _summary.topic,
        Map());
  }

  void onPublish() async {
    final dontShootMe = ScaffoldMessenger.of(context);
    final post = asPostRecord(_latestKeyValue, _latestValueValue);

    try {
      final numPublished = await post.publish();
      dontShootMe.showSnackBar(SnackBar(
          content: Text("Published $numPublished to ${post.topicOverride}")));
    } catch (e) {
      dontShootMe
          .showSnackBar(SnackBar(content: Text("Failed to publish $post: $e")));
    }
  }

  Widget publishLayout(BuildContext c) {
    return Row(
      children: <Widget>[
        NavigationRail(
          selectedIndex: _selectedTabIndex,
          onDestinationSelected: (int index) {
            setState(() {
              _selectedTabIndex = index;
            });
          },
          labelType: NavigationRailLabelType.all,
          destinations: const <NavigationRailDestination>[
            NavigationRailDestination(
              icon: Icon(Icons.polymer),
              selectedIcon: Icon(Icons.polymer),
              label: Text('Key'),
            ),
            NavigationRailDestination(
              icon: Icon(Icons.message),
              selectedIcon: Icon(Icons.message),
              label: Text('Value'),
            )
          ],
        ),
        const VerticalDivider(thickness: 1, width: 1),
        // This is the main content.
        Expanded(
          child: _selectedTabIndex == 0 ? keyWidget() : valueWidget(),
        )
      ],
    );
  }

  Widget publishFormWidget() {
    return LayoutBuilder(builder: (ctxt, BoxConstraints constraints) {
      final heightAvailableForCode = constraints.maxHeight - 400;
      final suggestedRows = 4 + (heightAvailableForCode ~/ 20);
      final numRows = max(4, min(40, suggestedRows));
      final valueHeight = max(100, constraints.maxHeight - 315);

      final knownAvroKey = _topics.keys.contains(_summary.topic);
      final knownAvroValue = _topics.values.contains(_summary.topic);

      final showNamespace = !knownAvroValue && _summary.keyIsAvro();

      double screenHeight = MediaQuery.of(context).size.height;

      return Container(
        // constraints: BoxConstraints(maxHeight: 200),
        margin: EdgeInsets.all(16),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Flexible(
                  child: FieldWidget(
                      "Repeat:",
                      "The number of records to submit",
                      "1",
                      (value) => _repeat = int.parse(value),
                      validateNumber)),
              Flexible(
                  // see https://stackoverflow.com/questions/49577781/how-to-create-number-input-field-in-flutter
                  //keyboardType: TextInputType.number
                  child: FieldWidget(
                      "Partition:",
                      "A specific partition to publish to, if specified",
                      "",
                      (value) => isNumber(value)
                          ? _partitionOverride = value
                          : _partitionOverride = "",
                      textOk)),
            ],
          ),
        ),
      );
    });
  }

  Widget typeWidget(
      String label, String currentValue, bool isAvro, OnUpdate onUpdate) {
    final List<String> values = [...Consts.SupportedTypes];
    if (!values.contains(currentValue)) {
      values.insert(values.length, currentValue);
    }

    final child = isAvro
        ? Text("Avro")
        : DropdownButton<String>(
            items: values.map((String value) {
              return DropdownMenuItem<String>(
                value: value,
                child: Text(value),
              );
            }).toList(),
            value: currentValue,
            onChanged: onUpdate,
          );

    return Padding(
        padding: const EdgeInsets.all(8.0),
        child: Container(
          width: 600,
          child: Row(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(0, 0, 8.0, 0),
                child: Text("$label :"),
              ),
              child,
            ],
          ),
        ));
  }
}

class TabbedWidget extends StatefulWidget {
  TabbedWidget(this._topics, this.tabIndex, this.initialValue, this.isKey,
      this.onTabChange, this.onValueChange, this.onUpload,
      {Key key})
      : super(key: key);
  Topics _topics;
  OnTabChange onTabChange;
  OnUpdateValue onValueChange;
  OnUpload onUpload;
  bool isKey;
  int tabIndex;
  dynamic initialValue;

  List<String> topics() {
    if (isKey) {
      return _topics.keys;
    } else {
      return _topics.values;
    }
  }

  @override
  _TabbedWidgetState createState() => _TabbedWidgetState();
}

class _TabbedWidgetState extends State<TabbedWidget>
    with TickerProviderStateMixin {
  final _stringController = TextEditingController();
  final _jasonController = TextEditingController();
  final _avroController = TextEditingController();
  TabController _tabController;
  String _selectedTopic;

  @override
  void initState() {
    super.initState();
    _tabController = new TabController(
        initialIndex: this.widget.tabIndex, length: 4, vsync: this);
    _tabController.addListener(() {
      this.widget.onTabChange(_tabController.index);
    });

    print(
        "TabbedWidget ${this.widget.isKey} index:${this.widget.tabIndex} w/ ${this.widget.initialValue}");

    final prettyJson =
        DataGenClient.pretty(this.widget.initialValue.toString());

    // switch (this.widget.tabIndex) {
    //   case 0:
    //     _avroController.text = prettyJson;
    //     break;
    //   case 1:
    //     _jasonController.text = prettyJson;
    //     break;
    //   case 2:
    //     _stringController.text = this.widget.initialValue.toString();
    //     break;
    //   case 3:
    // }
    _avroController.text = prettyJson;
    _jasonController.text = prettyJson;
    _stringController.text = this.widget.initialValue.toString();
  }

  @override
  void dispose() {
    super.dispose();
    _stringController.dispose();
    _jasonController.dispose();
    _avroController.dispose();
    _tabController.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return contentWidget(context);
  }

  Widget contentWidget(BuildContext c) {
    double screenHeight = MediaQuery.of(c).size.height;

    return Column(
      mainAxisAlignment: MainAxisAlignment.spaceAround,
      children: <Widget>[
        TabBar(
          controller: _tabController,
          indicatorColor: Colors.orange,
          labelColor: Colors.orange,
          unselectedLabelColor: Theme.of(c).primaryTextTheme.caption.color,
          isScrollable: true,
          tabs: PublishWidget.TabNames.map((name) => Tab(text: name)).toList(),
        ),
        Container(
          height: screenHeight * 0.80,
          margin: EdgeInsets.only(left: 16.0, right: 16.0),
          child: TabBarView(
            controller: _tabController,
            children: <Widget>[
              inputWidget(c, PublishWidget.TabNames[0], _avroController),
              inputWidget(c, PublishWidget.TabNames[1], _jasonController),
              stringInputWidget(c),
              numericWidget(c),
            ],
          ),
        )
      ],
    );
  }

  Container stringInputWidget(BuildContext c) {
    String hint = this.widget.isKey ? 'String key' : 'String value';
    return Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(8.0),
        ),
        child: TextField(
          controller: _stringController,
          onChanged: (newValue) {
            // this.widget.onValueChange(jsonDecode("\"${newValue}\""));
            this.widget.onValueChange(newValue);
          },
          decoration:
              InputDecoration(border: OutlineInputBorder(), hintText: hint),
        ));
  }

  void _onLoadFromTopic(String newTopic) async {
    TopicData.get(newTopic).then((topicData) {
      final testJson = topicData.data().testData;
      final jason = testJson.toString();
      final jsonBytes = Uint8List.fromList(jason.codeUnits);
      this.widget.onUpload(jsonBytes);
    });
  }

  void parseFiles(BuildContext c, List<darthtml.File> files) {
    if (files.length == 1) {
      final file = files[0];
      darthtml.FileReader reader = darthtml.FileReader();

      reader.onLoadEnd.listen((e) {
        this.widget.onUpload(reader.result);
      });

      reader.onError.listen((fileEvent) {
        setState(() {
          ScaffoldMessenger.of(c).showSnackBar(
              SnackBar(content: Text("Error parsing file '${file.name}': $e")));
        });
      });

      reader.readAsArrayBuffer(file);
    }
  }

  void _onRefreshContent(TextEditingController textController) {
    DataGenClient.contentAsJson(textController.text).then((value) {
      final pretty = DataGenClient.prettyJson(value);
      this.widget.onValueChange(pretty);
      setState(() {
        textController.text = pretty;
      });
    });
  }

  Widget inputWidget(
      BuildContext c, String label, TextEditingController textController) {
    return LayoutBuilder(builder: (ctxt, BoxConstraints constraints) {
      final height = constraints.maxHeight - 200;
      final rows = max(4, height ~/ 30);

      return DropZone(
          onDragEnter: () {},
          onDragExit: () {},
          onDrop: (List<darthtml.File> files) {
            parseFiles(c, files);
          },
          child: Container(
              height: 200, //constraints.maxHeight * 0.1,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8.0),
                // color: Colors.blueGrey[300],
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  ButtonBar(
                    alignment: MainAxisAlignment.start,
                    children: [
                      Padding(
                          padding: const EdgeInsets.all(8.0),
                          child: Container(
                            width: 600,
                            height: 180,
                            child: Row(
                              // crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Padding(
                                  padding:
                                      const EdgeInsets.fromLTRB(0, 0, 8.0, 0),
                                  child: Text("Load from topic:"),
                                ),
                                DropdownButton<String>(
                                  items:
                                      this.widget.topics().map((String value) {
                                    return DropdownMenuItem<String>(
                                      value: value,
                                      child: Text(value),
                                    );
                                  }).toList(),
                                  value: _selectedTopic,
                                  onChanged: (newTopic) {
                                    _onLoadFromTopic(newTopic);
                                    setState(() {
                                      _selectedTopic = newTopic;
                                    });
                                  },
                                ),
                              ],
                            ),
                          )),
                      OutlinedButton.icon(
                          onPressed: () => _onRefreshContent(textController),
                          icon: Icon(Icons.refresh),
                          label: Text("refresh"))
                    ],
                  ),
                  Card(
                      color: Colors.grey[700],
                      child: Padding(
                          padding: EdgeInsets.all(8.0),
                          child: TextField(
                            controller: textController,
                            maxLines: rows,
                            onChanged: (v) {
                              this.widget.onValueChange(v);
                            },
                            decoration: InputDecoration.collapsed(
                                hintText: "Enter (or drag & drop) $label here"),
                          ))),
                ],
              )));
    });
  }

  double initialIntValue() {
    try {
      return double.parse(this.widget.initialValue.toString());
    } catch (e) {
      return 0;
    }
  }

  Container numericWidget(BuildContext c) {
    return Container(
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(8.0),
          // color: Colors.blueGrey[300],
        ),
        child: SpinBox(
          min: 0,
          max: double.maxFinite,
          value: initialIntValue(),
          spacing: 24,
          direction: Axis.vertical,
          onChanged: (newValue) {
            try {
              var jason = jsonDecode(newValue.toString());
              this.widget.onValueChange(jason);
            } catch (e) {
              ScaffoldMessenger.of(c).showSnackBar(
                  SnackBar(content: Text("Invalid numeric jason: $e")));
              return;
            }
          },
          textStyle: TextStyle(fontSize: 24),
          incrementIcon: Icon(Icons.keyboard_arrow_up, size: 32),
          decrementIcon: Icon(Icons.keyboard_arrow_down, size: 32),
          decoration: InputDecoration(
            border: OutlineInputBorder(),
            contentPadding: const EdgeInsets.all(24),
          ),
        ));
  }
}
