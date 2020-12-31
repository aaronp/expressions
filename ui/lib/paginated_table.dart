import 'package:flutter/material.dart';

typedef OnRowChange = void Function(int index);
typedef OnPageChange = void Function(int index);

/// Credit to https://github.com/AseemWangoo/experiments_with_web
/// found from
///
/// https://flatteredwithflutter.com/using-paginateddatatable-in-flutter-web/
///
class CustomPaginatedTable extends StatelessWidget {
  const CustomPaginatedTable({
    Key key,
    this.rowsPerPage = 3, //PaginatedDataTable.defaultRowsPerPage,
    DataTableSource source,
    List<DataColumn> dataColumns,
    Widget header,
    bool showActions = false,
    List<Widget> actions,
    this.sortColumnIndex,
    this.sortColumnAsc = true,
    this.onRowChanged,
    this.onNewPage,
  })  : _source = source,
        _dataColumns = dataColumns,
        _header = header,
        _showActions = showActions,
        _actions = actions,
        assert(
            sortColumnIndex == null ||
                (sortColumnIndex >= 0 && sortColumnIndex < dataColumns.length),
            'Check the sortColumnIndex value'),
        assert(sortColumnAsc != null),
        super(key: key);

  /// This is the source / model which will be binded
  ///
  /// to each item in the Row...
  final DataTableSource _source;

  /// This is the list of columns which will be shown
  ///
  /// at the top of the DataTable.
  final List<DataColumn> _dataColumns;

  final Widget _header;
  final bool _showActions;
  final List<Widget> _actions;
  final int rowsPerPage;
  final int sortColumnIndex;
  final bool sortColumnAsc;

  final OnRowChange onRowChanged;
  final OnPageChange onNewPage;

  DataTableSource get _fetchDataTableSource {
    if (_source != null) {
      return _source;
    }
    // return _DefaultSource();
    return _source;
  }

  List<DataColumn> get _fetchDataColumns {
    if (_dataColumns != null) {
      return _dataColumns;
    }
    // return _defColumns;
    return _dataColumns;
  }

  Widget get _fetchHeader {
    if (_header != null) {
      return _header;
    }

    return const Text('Data with 7 rows per page');
  }

  List<Widget> get _fetchActions {
    if (_showActions && _actions != null) {
      return _actions;
    } else if (!_showActions) {
      return null;
    }

    return [
      IconButton(
        icon: const Icon(Icons.info_outline),
        onPressed: () {},
      )
    ];
  }

  @override
  Widget build(BuildContext ctxt) {
    //
    return LayoutBuilder(
        builder: (BuildContext context, BoxConstraints viewportConstraints) {
      return SingleChildScrollView(
          child: ConstrainedBox(
        constraints: BoxConstraints(
          // minHeight: viewportConstraints.maxHeight,
          maxHeight: 1000,
        ),
        child: PaginatedDataTable(
          actions: _fetchActions,
          columns: _fetchDataColumns,
          header: _fetchHeader,
          onRowsPerPageChanged: onRowChanged,
          onPageChanged: onNewPage,
          rowsPerPage: rowsPerPage,
          availableRowsPerPage: <int>[
            rowsPerPage,
            rowsPerPage * 2,
            rowsPerPage * 5,
            rowsPerPage * 10
          ],
          source: _fetchDataTableSource,
          sortColumnIndex: sortColumnIndex,
          sortAscending: sortColumnAsc,
        ),
      ));
    });
  }
}
