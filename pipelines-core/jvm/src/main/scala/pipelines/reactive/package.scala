package pipelines

import monix.execution.Scheduler

package object reactive {

  def Ignore = TriggerCallback.Ignore
  type Metadata = Map[String, String]

  type Sources = Repo[SourceEvent, DataSource]
  def Sources(implicit sched: Scheduler): Sources = Repo.sources(sched)

  type Sinks = Repo[SinkEvent, DataSink]
  def Sinks(implicit sched: Scheduler): Sinks = Repo.sinks(sched)

  /** samples 'max' elements from the given sequence
    *
    * @param data the data to sample
    * @param max the number of elements to keep
    * @tparam A
    * @return 'max' as a sample of the data
    */
  def select[A](data: Seq[A], max: Int): Seq[A] = {
    if (max <= 0) Nil
    else {
      val size = data.size
      val skip = size / max
      val result = if (skip == 0) {
        data
      } else {
        val iter = data.iterator.zipWithIndex.collect {
          case (value, i) if i % skip == 0 => value
        }
        iter.toSeq.take(max)
      }

      result

    }
  }
}
