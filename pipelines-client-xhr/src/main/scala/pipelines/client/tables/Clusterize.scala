package pipelines.client.tables

import scala.scalajs.js

@js.native
trait Clusterize extends js.Object {
  def append(markup: Seq[String]): Unit = js.native
}

object Clusterize {
  def apply(config: ClusterizeConfig): Clusterize = {

    /**
      * see app.js for 'newClusterize';
      */
    js.Dynamic.global.newClusterize(config.asJsonDynamic).asInstanceOf[Clusterize]
  }
}
