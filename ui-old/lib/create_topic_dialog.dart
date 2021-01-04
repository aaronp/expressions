import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';

import 'model.dart';
import 'rest_client.dart';


typedef GetRequest = CreateTopic Function();
typedef GetRequestCallback = void Function(GetRequest callback);
typedef OnCreate = void Function();

class CreateTopicDialog {
  static Widget createButton(BuildContext context,
      GlobalKey<ScaffoldState> scaffoldKey, OnCreate onCreate) {
    return IconButton(
        icon: const Icon(Icons.add_circle),
        tooltip: 'Create Topic',
        onPressed: () async {
          await CreateTopicDialog.show(context, scaffoldKey);
          onCreate();
        });
  }

  static Future show(BuildContext ctxt, GlobalKey<ScaffoldState> scaffoldKey) {
    return showDialog(
      context: ctxt,
      builder: (parentCtxt) {
        return StatefulBuilder(builder: (context, setState) {
          GetRequest getRequestCallback = () => null;

          Widget okButton = RaisedButton.icon(
            icon: Icon(Icons.golf_course),
            color: Colors.red,
            label: Text("Ok"),
            onPressed: () async {
              //NewTopicForm.of([parentC  txt, ctxt]).request
              final CreateTopic request = getRequestCallback?.call();
              if (request != null) {
                onCreateTopic(context, scaffoldKey, request);
                Navigator.of(parentCtxt).pop();
              }
            },
          );

          Widget cancelButton = RaisedButton(
            child: Text("Cancel"),
            onPressed: () {
              Navigator.of(parentCtxt).pop();
            },
          );
          return AlertDialog(
            title: Text("New Topic"),
            content: Flexible(child: NewTopicForm((getRequest) {
              getRequestCallback = getRequest;
            })),
            actions: <Widget>[
              cancelButton,
              Expanded(child: Container()),
              okButton
            ],
          );
        });
      },
    );
  }

  static Future<CreateTopic> onCreateTopic(BuildContext context,
      GlobalKey<ScaffoldState> scaffoldKey, CreateTopic request) async {
    final response = await RestClient.createTopic(request);
    scaffoldKey.currentState.showSnackBar(SnackBar(
        content: Text('Created topic ${request.name}: ${response.asJson}')));
    return response;
  }
}

class NewTopicForm extends StatefulWidget {
  NewTopicForm(this.setCallback);

  GetRequestCallback setCallback;

  @override
  NewTopicFormState createState() => NewTopicFormState();

  static NewTopicFormState of(List<BuildContext> contexts) {
    final found = contexts.where((element) {
      final NewTopicFormState navigator =
      element.findAncestorStateOfType<NewTopicFormState>();
      print('got $navigator');
      return navigator != null;
    });
    final NewTopicFormState navigator =
    found.single.findAncestorStateOfType<NewTopicFormState>();

    assert(() {
      if (navigator == null) {
        throw new FlutterError(
            'NewTopicFormState operation requested with a context that does not include a NewTopicFormState.');
      }
      return true;
    }());

    return navigator;
  }
}

class NewTopicFormState extends State<NewTopicForm> {
  final _formKey = GlobalKey<FormState>();
  final _partitionController = TextEditingController(text: '3');
  final _replicasController = TextEditingController(text: '2');
  final _topicController = TextEditingController(text: '');

  CreateTopic get request {
    if (_formKey.currentState.validate()) {
      return CreateTopic(
          _topicController.text,
          int.tryParse(_partitionController.text),
          int.tryParse(_replicasController.text));
    } else {
      return null;
    }
  }

  @override
  void initState() {
    super.initState();
    widget.setCallback(() => request);
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 300,
      width: 400,
      child: Form(
          key: _formKey,
          child: Padding(
            padding: const EdgeInsets.all(8.0),
            child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  topicField(),
                  partitionField(),
                  replicasField()
                ]),
          )),
    );
  }

  Widget partitionField() {
    return Padding(
        padding: const EdgeInsets.all(8.0),
        child: TextFormField(
            controller: _partitionController,
            keyboardType: TextInputType.number,
            inputFormatters: <TextInputFormatter>[
              FilteringTextInputFormatter.digitsOnly
            ],
            decoration: InputDecoration(
              labelText: 'Partitions',
              icon: new Icon(FontAwesomeIcons.chartPie),
              fillColor: Colors.white,
              border: new OutlineInputBorder(
                borderRadius: new BorderRadius.circular(10.0),
                borderSide: new BorderSide(),
              ),
            ),
            validator: (value) {
              var i = int.tryParse(value);
              if (i == null) {
                return 'Number of partitions is required';
              } else {
                if (i > 100) {
                  return "That's a lot of partitions";
                } else if (i < 1) {
                  return 'You must have at least one partition';
                } else {
                  return null;
                }
              }
            }));
  }

  Widget replicasField() {
    return Padding(
        padding: const EdgeInsets.all(8.0),
        child: TextFormField(
            controller: _replicasController,
            keyboardType: TextInputType.number,
            inputFormatters: <TextInputFormatter>[
              FilteringTextInputFormatter.digitsOnly
            ],
            decoration: InputDecoration(
              labelText: 'Replicas',
              hintText: 'The number of replicas required for this topic',
              icon: new Icon(FontAwesomeIcons.database),
              fillColor: Colors.white,
              border: new OutlineInputBorder(
                borderRadius: new BorderRadius.circular(10.0),
                borderSide: new BorderSide(),
              ),
            ),
            validator: (value) {
              var i = int.tryParse(value);
              if (i == null) {
                return 'Number of replicas is required';
              } else {
                if (i > 100) {
                  return "That's a lot of replicas";
                } else if (i < 1) {
                  return 'You must have at least one replica';
                } else {
                  return null;
                }
              }
            }));
  }

  Widget topicField() {
    return Padding(
        padding: const EdgeInsets.all(8.0),
        child: TextFormField(
            autofocus: true,
            controller: _topicController,
            keyboardType: TextInputType.text,
            inputFormatters: <TextInputFormatter>[
              FilteringTextInputFormatter.singleLineFormatter
            ],
            decoration: InputDecoration(
              labelText: 'Topic',
              hintText: 'The name of the topic',
              icon: new Icon(Icons.topic),
              fillColor: Colors.white,
              border: new OutlineInputBorder(
                borderRadius: new BorderRadius.circular(10.0),
                borderSide: new BorderSide(),
              ),
            ),
            validator: (value) {
              if (value == null || value.isEmpty) {
                return "The topic name is required";
              } else {
                return null;
              }
            }));
  }
}
