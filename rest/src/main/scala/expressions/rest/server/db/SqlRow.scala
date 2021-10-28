package expressions.rest.server.db

import java.sql.{ResultSet, ResultSetMetaData}

case class SqlRow(result: ResultSet) {

  val metadata = result.getMetaData
  val cols = (1 to metadata.getColumnCount).map { col =>
    SqlRow.Col(col, metadata)
  }

  // these have to be eager 'cause the resultSet is a mutable buffer
  val values: Seq[AnyRef] = (1 to metadata.getColumnCount).map { col =>
    result.getObject(col)
  }
  val rowNr = result.getRow

  def colNames = cols.map(_.name)

  def asMap: Map[String, AnyRef] = colNames.zip(values).toMap.updated(SqlRow.RowNrCol, Integer.valueOf(rowNr))

  def asMapString: Map[String, String] = asMap.view.mapValues(_.toString).toMap

  override def toString = AsciiTable.forRows(List(asMapString)).toString
}

object SqlRow {
  val RowNrCol = "$row"

  def show(rows: Seq[SqlRow]) = AsciiTable.forRows(rows.map(_.asMapString))

  case class Col(col: Int, metadata: ResultSetMetaData) {
    val name = metadata.getColumnName(col).toLowerCase

    def label = metadata.getColumnLabel(col)

    def typeName = metadata.getColumnTypeName(col)

    override def toString = s"$name ($label): $typeName"
  }
}
