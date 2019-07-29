package pipelines.layout

import pipelines.reactive.HasMetadata

final class AsciiTable(rows: Seq[AsciiTable.Row] = Nil) {
  def headers: Seq[String] = rows.flatMap(_.keySet).distinct.sorted

  def addRow(row: (String, String)*): AsciiTable = addRow(row.toMap)

  def addRow(row: Map[String, String]): AsciiTable = new AsciiTable(row +: rows)

  def render(keys: Seq[String] = headers, indent: String = "    "): String = renderLines(keys, indent).mkString("\n")

  override lazy val toString: String = render(indent = "")

  def renderLines(keys: Seq[String], indent: String): Seq[String] = {
    val maxSizes: Seq[Int] = keys.map { key =>
      val lens   = rows.map(_.get(key).fold(0)(_.length))
      val maxLen = (key.length +: lens).max
      maxLen
    }
    def pad(name: String, i: Int) = {
      val width = maxSizes(i)
      s" ${name.padTo(width, ' ')} "
    }
    val renderCell = (pad _).tupled
    def renderRow(row: Map[String, String]): Seq[String] = {
      val values: Seq[String] = keys.map(row.getOrElse(_, ""))
      values.zipWithIndex.map(renderCell)
    }
    val headerRow: String = keys.zipWithIndex.map(renderCell).mkString(s"$indent|", "|", "|")
    val separator: String = {
      val line = "+" + ("-" * (headerRow.length - indent.length - 2)) + "+"
      s"$indent$line"
    }
    val rowCells: Seq[String] = rows.map(renderRow).map(_.mkString(s"$indent|", "|", "|"))

    separator +: headerRow +: separator +: rowCells.flatMap(r => List(r, separator))
  }
}
object AsciiTable {
  type Row = Map[String, String]
  def apply(firstValue: (String, String), row: (String, String)*): AsciiTable = new AsciiTable(Seq((firstValue +: row).toMap))
  def apply(data: Seq[HasMetadata]): AsciiTable = {
    data.foldLeft(new AsciiTable()) {
      case (tbl, src) => tbl.addRow(src.metadata)
    }
  }
}
