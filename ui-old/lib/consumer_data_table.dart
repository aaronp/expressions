import 'dart:math';

import 'package:flutter/material.dart';
import 'package:kafkaui/paginated_table.dart';
import 'package:provider/provider.dart';

import 'model.dart';
import 'rest_client.dart';

void main() => runApp(
      MaterialApp(
        builder: (context, child) =>
            SafeArea(child: new Material(color: Colors.white, child: child)),
        home: Scaffold(
          // body: ConsumerDataWidget('topiceae5c8f8a3054d19a132cb3033031e49'),
          body:
              PeekDataWidget.forTopic('topiceae5c8f8a3054d19a132cb3033031e49'),
        ),
      ),
    );

typedef OnRowSelect = void Function(int index);

class RecordDataTableSource extends DataTableSource {
  RecordDataTableSource(
      {@required List<Record> data,
      @required int fromOffset,
      @required this.onRowSelect})
      : _records = data,
        _fromOffset = fromOffset,
        assert(data != null);

  final List<Record> _records;
  final int _fromOffset;
  final OnRowSelect onRowSelect;

  @override
  DataRow getRow(int offset) {
    final index = max(0, offset - _fromOffset);
    print('getRow($offset) of ${_records.length} is index $index');

    if (index >= _records.length) {
      return emptyRow(index);
    }

    final record = _records[index];
    return DataRow.byIndex(index: index, cells: <DataCell>[
      DataCell(IconButton(
        hoverColor: Colors.transparent,
        splashColor: Colors.transparent,
        icon: const Icon(Icons.info_outline_rounded),
        tooltip: record.topic,
        onPressed: () => onRowSelect(index),
      )),
      DataCell(Text('${record.partition}')),
      DataCell(Text('${record.offset}')),
      DataCell(
          Text('${DateTime.fromMillisecondsSinceEpoch(record.timestamp)}')),
      DataCell(Text('${record.timestampType}')),
      DataCell(Text('${record.value}'), onTap: () {
        print('tap $index');
      }),
      DataCell(Text('${record.serializedKeySize}')),
      DataCell(Text('${record.serializedValueSize}')),
    ]);
  }

  DataRow emptyRow(int index) {
    return DataRow.byIndex(index: index, cells: <DataCell>[
      DataCell(Text('')),
      DataCell(Text('')),
      DataCell(Text('')),
      DataCell(Text('')),
      DataCell(Text('')),
      DataCell(Text('')),
      DataCell(Text('')),
      DataCell(Text('')),
    ]);
  }

  void sort<T>(Comparable<T> Function(Record d) getField, bool ascending) {
    _records.sort((a, b) {
      final aValue = getField(a);
      final bValue = getField(b);
      return ascending
          ? Comparable.compare(aValue, bValue)
          : Comparable.compare(bValue, aValue);
    });

    notifyListeners();
  }

  @override
  bool get isRowCountApproximate => true;

  @override
  int get rowCount => _records.length * 10;

  @override
  int get selectedRowCount => 0;
}

class PeekDataWidget extends StatelessWidget {
  static Widget forTopic(String topic) {
    return Column(
      children: [
        Text('Data for $topic:'),
        ChangeNotifierProvider<RecordDataNotifier>(
          create: (_) => RecordDataNotifier(topic, {}),
          child: PeekDataWidget(),
        ),
      ],
    );
  }

  DataColumn _textCol(String label) {
    return DataColumn(
      label: Text(label),
      tooltip: label,
    );
  }

  DataColumn _numCol(String label, DataColumnSortCallback sortBy) {
    return DataColumn(
        label: Text(label), numeric: true, tooltip: label, onSort: sortBy);
  }

  void _showSnackBar(BuildContext c, String textToShow) {
    Scaffold.of(c).showSnackBar(
      SnackBar(
        content: Text(textToShow),
        duration: const Duration(milliseconds: 1000),
      ),
    );
  }

  void _sort<T>(
    Comparable<T> Function(Record r) getField,
    int colIndex,
    bool asc,
    RecordDataTableSource _src,
    RecordDataNotifier _provider,
  ) {
    _src.sort<T>(getField, asc);
    _provider.sortAscending = asc;
    _provider.sortColumnIndex = colIndex;
  }

  @override
  Widget build(BuildContext context) {

    final RecordDataNotifier _provider = context.watch<RecordDataNotifier>();

    return Column(
      children: [
        Text('${_provider.recordList.length} records'),
        paginatedTable(_provider, context),
      ],
    );
  }

  void _showDetails(BuildContext c, Record data) async =>
      await showDialog<bool>(
        context: c,
        builder: (_) => CustomDialog(
          showPadding: false,
          child: Text('Record is ${data.base64}'),
        ),
      );

  Widget paginatedTable(RecordDataNotifier _provider, BuildContext context) {
    final _model = _provider.recordList;

    if (_model.isEmpty) {
      return const Text('No Data');
    }

    final _src = RecordDataTableSource(
        data: _model,
        fromOffset: _provider._fromOffset,
        onRowSelect: (index) => _showDetails(context, _model[index]));

    return CustomPaginatedTable(
        source: _src,
        dataColumns: [
          _textCol('Topic'),
          _numCol('Partition', (colIndex, asc) {
            _sort<num>((r) => r.partition, colIndex, asc, _src, _provider);
          }),
          _numCol('Offset', (colIndex, asc) {
            _sort<num>((r) => r.offset, colIndex, asc, _src, _provider);
          }),
          _numCol('Timestamp', (colIndex, asc) {
            _sort<num>((r) => r.timestamp, colIndex, asc, _src, _provider);
          }),
          _textCol('TS Type'),
          _textCol('Value'),
          _textCol('Key Size'),
          _textCol('Value Size')
        ],
        header: Text(_provider.topic),
        onRowChanged: (index) => _provider.rowsPerPage = index,
        onNewPage: (from) => _provider.fromOffset = from,
        rowsPerPage: _provider.rowsPerPage,
        showActions: true,
        actions: <IconButton>[
          IconButton(
            splashColor: Colors.transparent,
            icon: const Icon(Icons.refresh),
            onPressed: () {
              _provider.fetchData();
              _showSnackBar(context, 'Refresh');
            },
          ),
        ]);
  }
}

class CustomDialog extends StatelessWidget {
  const CustomDialog({
    Key key,
    @required this.child,
    this.showPadding = true,
  }) : super(key: key);

  final Widget child;
  final bool showPadding;

  @override
  Widget build(BuildContext context) {
    var _child = child;

    if (showPadding) {
      _child = Padding(
        padding: const EdgeInsets.symmetric(vertical: 32.0, horizontal: 16.0),
        child: child,
      );
    } else {
      _child = Padding(
        padding: const EdgeInsets.symmetric(vertical: 22.0, horizontal: 16.0),
        child: child,
      );
    }

    return Dialog(
      elevation: 8.0,
      insetAnimationCurve: Curves.easeInOut,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.zero),
      child: _child,
    );
  }
}

class RecordDataNotifier with ChangeNotifier {
  RecordDataNotifier(this.topic, this.partitions) {
    fetchData();
  }

  List<Record> get recordList => _recordList;

  // SORT COLUMN INDEX...

  int get sortColumnIndex => _sortColumnIndex;

  set sortColumnIndex(int sortColumnIndex) {
    _sortColumnIndex = sortColumnIndex;
    notifyListeners();
  }

  // SORT ASCENDING....

  bool get sortAscending => _sortAscending;

  set sortAscending(bool sortAscending) {
    _sortAscending = sortAscending;
    notifyListeners();
  }

  int get rowsPerPage => _rowsPerPage;

  set rowsPerPage(int rowsPerPage) {
    _rowsPerPage = rowsPerPage;
    fetchData();
  }

  set fromOffset(int offset) {
    _fromOffset = offset;
    fetchData();
  }

  // -------------------------------------- INTERNALS --------------------------------------------

  var _recordList = <Record>[];

  int _sortColumnIndex;
  bool _sortAscending = true;
  int _rowsPerPage = 5;

  ///PaginatedDataTable.defaultRowsPerPage;
  int _fromOffset = 0;

  final String topic;
  final Set<int> partitions;

  Future<void> fetchData() async {
    _recordList = await RestClient.peek(
        PeekRequest({topic}, _fromOffset, _rowsPerPage, partitions));

    notifyListeners();
  }
}
