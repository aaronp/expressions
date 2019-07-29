package pipelines

import com.typesafe.scalalogging.StrictLogging
import monix.execution.schedulers.SchedulerService
import monix.execution.{ExecutionModel, Scheduler, UncaughtExceptionReporter}

object Schedulers {

  def using[A](f: Scheduler => A): A = {
    val sched = compute()
    try {
      f(sched)
    } finally {
      sched.shutdown()
    }
  }

  object LoggingReporter extends UncaughtExceptionReporter with StrictLogging {
    override def reportFailure(ex: Throwable): Unit = {
      logger.error(s"Failure: $ex", ex)
    }
  }

  def io(name: String = "pipelines-io", daemonic: Boolean = true): SchedulerService = {
    Scheduler.io(name, daemonic = daemonic, reporter = LoggingReporter, executionModel = ExecutionModel.Default)
  }

  def compute(name: String = "pipelines-compute", daemonic: Boolean = true): SchedulerService = {
    Scheduler.computation(name = name, daemonic = daemonic, reporter = LoggingReporter, executionModel = ExecutionModel.Default)
  }

}