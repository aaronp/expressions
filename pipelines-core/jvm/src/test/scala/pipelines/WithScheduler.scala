package pipelines

import monix.execution.Scheduler

object WithScheduler {
  def apply[A](f: Scheduler => A): A = {
    val sched = Scheduler.computation()
    try {
      f(sched)
    } finally {
      sched.shutdown()
    }
  }
}
