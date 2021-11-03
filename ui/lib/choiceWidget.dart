import 'package:dropdown_formfield/dropdown_formfield.dart';
import 'package:flutter/material.dart';

void main() => runApp(MyApp());

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: MyHomePage(),
    );
  }
}


@immutable
class User {
  const User({this.email, this.name});

  final String email;
  final String name;

  @override
  String toString() {
    return '$name, $email';
  }

  @override
  bool operator ==(Object other) {
    if (other.runtimeType != runtimeType) {
      return false;
    }
    return other is User && other.name == name && other.email == email;
  }

  @override
  int get hashCode => hashValues(email, name);
}


class AutocompleteBasicUserExample extends StatelessWidget {
  const AutocompleteBasicUserExample({Key key = null}) : super(key: key);

  static const List<User> _userOptions = <User>[
    User(name: 'Alice', email: 'alice@example.com'),
    User(name: 'Bob', email: 'bob@example.com'),
    User(name: 'Charlie', email: 'charlie123@gmail.com'),
  ];

  static String _displayStringForOption(User option) => option.name;

  @override
  Widget build(BuildContext context) {
    return Autocomplete<User>(
      displayStringForOption: _displayStringForOption,
      optionsBuilder: (TextEditingValue textEditingValue) {
        if (textEditingValue.text == '') {
          return const Iterable<User>.empty();
        }
        return _userOptions.where((User option) {
          return option
              .toString()
              .contains(textEditingValue.text.toLowerCase());
        });
      },
      onSelected: (User selection) {
        print('You just selected ${_displayStringForOption(selection)}');
      },
    );
  }
}

class MyHomePage extends StatefulWidget {
  @override
  _MyHomePageState createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  String _myActivity = "";
  String _myActivityResult = "";
  final formKey = new GlobalKey<FormState>();

  @override
  void initState() {
    super.initState();
    _myActivity = '';
    _myActivityResult = '';
  }

  _saveForm() {
    var form = formKey.currentState;
    if (form != null) {
      if (form.validate()) {
        form.save();
        setState(() {
          _myActivityResult = _myActivity;
        });
      }
    }
  }

  Widget example() {
    return                   Container(
      padding: EdgeInsets.all(16),
      child: DropDownFormField(
        titleText: 'My workout',
        hintText: 'Please choose one',
        value: _myActivity,
        onSaved: (value) {
          setState(() {
            _myActivity = value;
          });
        },
        onChanged: (value) {
          setState(() {
            _myActivity = value;
          });
        },
        dataSource: [
          {
            "display": "Running",
            "value": "Running",
          },
          {
            "display": "Climbing",
            "value": "Climbing",
          },
          {
            "display": "Walking",
            "value": "Walking",
          },
          {
            "display": "Swimming",
            "value": "Swimming",
          },
          {
            "display": "Soccer Practice",
            "value": "Soccer Practice",
          },
          {
            "display": "Baseball Practice",
            "value": "Baseball Practice",
          },
          {
            "display": "Football Practice",
            "value": "Football Practice",
          },
        ],
        textField: 'display',
        valueField: 'value',
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Dropdown Formfield Example'),
      ),
      body: Center(
        child: Form(
          key: formKey,
          child: Column(
            mainAxisAlignment: MainAxisAlignment.start,
            children: <Widget>[
              AutocompleteBasicUserExample(),
              Container(
                padding: EdgeInsets.all(8),
                child: RaisedButton(
                  child: Text('Save'),
                  onPressed: _saveForm,
                ),
              ),
              Container(
                padding: EdgeInsets.all(16),
                child: Text(_myActivityResult),
              )
            ],
          ),
        ),
      ),
    );
  }
}

//
// /**
//  * Debug entry point
//  */
// void main() {
//   runApp(MaterialApp(
//       title: 'Franz',
//       theme: ThemeData(brightness: Brightness.light),
//       darkTheme: ThemeData(brightness: Brightness.dark),
//       themeMode: ThemeMode.dark,
//       debugShowCheckedModeBanner: false,
//       home: ChoiceWidget()));
// }
//
//
// class ChoiceWidget extends StatefulWidget {
//   const ChoiceWidget({Key? key}) : super(key: key);
//
//   @override
//   _ChoiceWidgetState createState() => _ChoiceWidgetState();
// }
//
// class _ChoiceWidgetState extends State<ChoiceWidget> {
//   @override
//   Widget build(BuildContext context) {
//     return Center(
//       child : DropDownFormField(
//           titleText : "Example"
//       )
//     );
//   }
// }
