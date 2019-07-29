package pipelines.reactive

import pipelines.Pipeline
import pipelines.layout.AsciiTable
import pipelines.reactive.trigger.RepoState

object RepoStateRender {

  def apply(svc: PipelineService, repoState: RepoState): String = {
    val sourceData: Seq[HasMetadata] = repoState.sources.map { src: DataSource =>
      val foundSize = src.id.fold("no-id") { id =>
        svc.pipelinesForSource(id).size.toString
      }
      val d8a: HasMetadata = src.ensuringContentType().addMetadata("pipelines", foundSize)
      d8a
    }
    val sinkData: Seq[HasMetadata] = repoState.sinks.map { sink: DataSink =>
      val foundSize = sink.id.fold("no-id") { id =>
        svc.pipelinesForSink(id).size.toString
      }
      val d8a: HasMetadata = sink.ensuringContentType().addMetadata("pipelines", foundSize)
      d8a
    }

    val pipelinesRows: Seq[Map[String, String]] = svc.pipelines.map {
      case (id, p) =>
        val steps = p.steps.collect {
          case Pipeline.Step(transform, name, _) =>
            s"$name ($transform)"
        }
        Map(
          "source" -> p.sourceId,
          "via"    -> steps.mkString(s" ${steps.size} steps: ", " --> ", ""),
          "sink"   -> p.sinkId
        )
    }.toSeq

    val block = s"""
       !==================================== ${sourceData.size} Sources ===================================
       !${AsciiTable(sourceData).render()}
       !
       !====================================== ${sinkData.size} Sinks ===================================
       !${AsciiTable(sinkData).render()}
       !
       !==================================== ${pipelinesRows.size} Pipelines =================================
       !${new AsciiTable(pipelinesRows).render(Seq("source", "via", "sink"))}
       !
       !==================================== ${repoState.triggers.size} Triggers =================================
       !${repoState.triggers.mkString("\n")}
       !
       !==================================== ${repoState.transformsByName.size} Transforms =================================
       !${repoState.transformsByName.keySet.toList.sorted.mkString(", ")}
    """.stripMargin('!')

    block.lines.map("\t\t" + _).mkString("\n")
  }
}
