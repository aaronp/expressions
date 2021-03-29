package expressions.rest.server

import com.typesafe.config.Config
import zio.{RIO, ZEnv}

package object kafka {
  type RunningSinkId = String
  type SinkInput     = (RunningSinkId, Config)

  type BatchResult = RIO[ZEnv, Unit]

  /**
    * A task which, when run, produces a 'sink' -- a function which persists the given record (and returns back that record)
    */
  type SinkIO = RIO[ZEnv, Batch => BatchResult]

  type OnBatch = BatchInput => BatchResult
}
