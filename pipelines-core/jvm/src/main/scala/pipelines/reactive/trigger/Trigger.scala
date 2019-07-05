package pipelines.reactive.trigger

import pipelines.reactive.{DataSink, DataSource, MetadataCriteria}

/**
  * A 'trigger' is named after a database trigger.
  *
  * Is is a way to match sources with transforms and sinks
  *
  * @param sourceCriteria
  * @param sinkCriteria
  * @param transforms
  */
case class Trigger(sourceCriteria: MetadataCriteria = MetadataCriteria(), sinkCriteria: MetadataCriteria = MetadataCriteria(), transforms: Seq[String] = Nil) {
  def matchesSource(dataSource: DataSource): Boolean = {
    sourceCriteria.matches(dataSource.metadata)
  }
  def matchesSink(dataSink: DataSink): Boolean = sinkCriteria.matches(dataSink.metadata)
}
