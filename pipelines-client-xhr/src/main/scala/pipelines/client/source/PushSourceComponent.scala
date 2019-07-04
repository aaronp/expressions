package pipelines.client.source

import scalatags.Text.all.{div, h2, p, _}

object PushSourceComponent {
  def render(st8: PushSourceState) = {

    import io.circe.syntax._

    div(`class` := "pipeline-component")(
      h1("Push Source"),
      h2(st8.createdBy),
      h3(st8.id),
      p(st8.asJson.spaces2)
    ).render
  }

}
