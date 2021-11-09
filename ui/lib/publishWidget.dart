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

  @override
  _PublishWidgetState createState() => _PublishWidgetState();
}

class _PublishWidgetState extends State<PublishWidget> {
  final _formKey = GlobalKey<FormState>();

  int _selectedIndex = 0;
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

  @override
  void initState() {
    super.initState();

    _summary = widget.summary;
    // _keyTextController.text = _topic.hasKeySchema() ? _topic.keyData().testData.toString() : testDataForType(_summary.keyType);
    // _valueTextController.text = _topic.hasValueSchema() ? _topic.valueData().testData.toString() : testDataForType(_summary.valueType);

    _reload(false);
  }

  Widget keyWidget() {
    print("Creating keyWidget w/ >$_latestKeyValue<");
    return TabbedWidget(_keyTabIndex, _latestKeyValue, true, (idx) {
      _keyTabIndex = idx;
    }, (newValue) {
      print(" >>>> Key is $newValue");
      _latestKeyValue = newValue;
    }, (uploadData) async {
      DataGenClient.dataAsJson(uploadData).then((jason) async {
        print("GOT: $jason");
        _latestKeyValue = jason.toString();
        final Future<Null> ok =
            Future.delayed(const Duration(milliseconds: 100), () {
          print("setting (1) _latestKeyValue to $jason");
          setState(() {
            _latestKeyValue = jason;
            print("setting (2) _latestKeyValue to $_latestKeyValue");
          });
        });
        await ok;
      });
    }, key: Key("key$_keyTabIndex${_latestKeyValue.hashCode}"));
  }

  Widget valueWidget() {
    return TabbedWidget(_valueTabIndex, _latestValueValue, false, (idx) {
      _valueTabIndex = idx;
    }, (newValue) {
      print(" >>>> Value is $newValue");
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
    if (_formKey.currentState.validate()) {
      _formKey.currentState.save();
      return PostRecord(
          value,
          widget.configuration,
          key,
          _repeat,
          isNumber(_partitionOverride) ? int.parse(_partitionOverride) : null,
          _summary.topic,
          Map());
    } else {
      return null;
    }
  }

  void onPublish() async {
    final dontShootMe = ScaffoldMessenger.of(context);
    dynamic value;
    try {
      // value = jsonDecode(_valueTextController.text);
    } catch (e) {
      dontShootMe
          .showSnackBar(SnackBar(content: Text("Invalid value jason: $e")));
      return;
    }
    dynamic key;
    // try {
    //   key = jsonDecode(_keyTextController.text);
    // } catch (e1) {
    //   try {
    //     key = jsonDecode("\"${_keyTextController.text}\"");
    //   } catch (e) {
    //     dontShootMe
    //         .showSnackBar(SnackBar(content: Text("Invalid key jason: $e")));
    //     return;
    //   }
    // }

    final post = asPostRecord(key, value);
    if (post != null) {
      try {
        final numPublished = await post.publish();
        dontShootMe.showSnackBar(SnackBar(
            content:
                Text("Published ${numPublished} to ${post.topicOverride}")));
      } catch (e) {
        dontShootMe.showSnackBar(
            SnackBar(content: Text("Failed to publish $post: $e")));
      }
    }
  }

  Widget publishLayout(BuildContext c) {
    return Row(
      children: <Widget>[
        NavigationRail(
          selectedIndex: _selectedIndex,
          onDestinationSelected: (int index) {
            setState(() {
              _selectedIndex = index;
            });
          },
          labelType: NavigationRailLabelType.all,
          destinations: const <NavigationRailDestination>[
            NavigationRailDestination(
              icon: Icon(Icons.polymer),
              selectedIcon: Icon(Icons.polymer),
              label: Text('Keys'),
            ),
            NavigationRailDestination(
              icon: Icon(Icons.message),
              selectedIcon: Icon(Icons.message),
              label: Text('Values'),
            )
          ],
        ),
        const VerticalDivider(thickness: 1, width: 1),
        // This is the main content.
        Expanded(
          child: _selectedIndex == 0 ? keyWidget() : valueWidget(),
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

  void _onReloadKey() {}
}

class TabbedWidget extends StatefulWidget {
  TabbedWidget(this.tabIndex, this.initialValue, this.isKey, this.onTabChange,
      this.onValueChange, this.onUpload,
      {Key key})
      : super(key: key);

  OnTabChange onTabChange;
  OnUpdateValue onValueChange;
  OnUpload onUpload;
  bool isKey;
  int tabIndex;
  dynamic initialValue;

  @override
  _TabbedWidgetState createState() => _TabbedWidgetState();
}

class _TabbedWidgetState extends State<TabbedWidget>
    with TickerProviderStateMixin {
  final _stringController = TextEditingController();
  final _jasonController = TextEditingController();
  final _avroController = TextEditingController();
  TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = new TabController(
        initialIndex: this.widget.tabIndex, length: 4, vsync: this);
    _tabController.addListener(() {
      this.widget.onTabChange(_tabController.index);
    });

    String prettyprint = this.widget.initialValue.toString();
    try {
      JsonEncoder encoder = new JsonEncoder.withIndent('  ');
      prettyprint = encoder.convert(this.widget.initialValue);
    } catch (e) {
      print("pretty print threw $e for >${this.widget.initialValue.toString()}<");
    }


    print("this.widget.tabIndex is ${this.widget.tabIndex}");
    switch (this.widget.tabIndex) {
      case 0:
        {
          _stringController.text = prettyprint;
          break;
        }
      case 1:
        {
          break;
        }
      case 2:
        {
          _jasonController.text = prettyprint;
          break;
        }
      case 3:
        {
          _avroController.text = prettyprint;
          break;
        }
    }

    print("- - - - - - - - - - - - - - - - - - - - ");
    print(_jasonController.text);
    print("- - - - - - - - - - - - - - - - - - - - ");
    //""; //_topic.hasKeySchema() ? _topic.keyData().testData.toString() : testDataForType(_summary.keyType);
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
          tabs: <Widget>[
            Tab(text: "String"),
            Tab(text: "Long"),
            Tab(text: "Json"),
            Tab(text: "Avro"),
          ],
        ),
        Container(
          height: screenHeight * 0.80,
          margin: EdgeInsets.only(left: 16.0, right: 16.0),
          child: TabBarView(
            controller: _tabController,
            children: <Widget>[
              stringInputWidget(c),
              numericWidget(c),
              inputWidget(c, "Json", _jasonController),
              inputWidget(c, "Avro", _avroController),
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
          onSubmitted: (newValue) => this.widget.onValueChange(newValue),
          decoration:
              InputDecoration(border: OutlineInputBorder(), hintText: hint),
        ));
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

  Widget inputWidget(
      BuildContext c, String label, TextEditingController textController) {
    // double screenHeight = MediaQuery.of(c).size.height;
    return LayoutBuilder(builder: (ctxt, BoxConstraints constraints) {
      final rows = max(10, constraints.maxHeight ~/ 30);
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
                      OutlinedButton.icon(
                          onPressed: () {
                            setState(() {});
                          },
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
