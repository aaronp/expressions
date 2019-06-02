package pipelines

import monix.execution.Scheduler

package object reactive {

  /** The metadata key to identify a unique source id
    */
  val UniqueSourceId = "sourceId"

  /** The metadata key to identify a unique sink id
    */
  val UniqueSinkId = "sinkId"

  type NewSource = Scheduler => DataSource
  def NewSource(data: DataSource): NewSource                = (_: Scheduler) => data
  def NewSource(create: Scheduler => DataSource): NewSource = create

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
