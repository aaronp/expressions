package pipelines.client.source
import org.scalajs.dom.html.{Div, Input, LI, Span}
import org.scalajs.dom.raw.{HTMLElement, HTMLInputElement, KeyboardEvent}
import pipelines.client.HtmlUtils
import pipelines.reactive.repo.RepoSchemas
import scalatags.JsDom.all.{`class`, div, _}

import scala.collection.immutable

/**
  * Contains the scalatags/scripts to render a 'create new push source' modal dialog box.
  *
  * The user must specify a name and can add additional metadata.
  *
  * TODO - render &times' (or any of https://dev.w3.org/html5/html-author/charref) using scalatags
  * autocomplete (e.g. https://www.w3schools.com/howto/howto_js_autocomplete.asp)
  */
object NewPushSourceModal extends RepoSchemas {

  final case class FormData(name: String, persist: Boolean, metadata: Map[String, String])

  //
  def render(modalsContainer: HTMLElement)(onCreate: FormData => Unit): Div = {
    val expander = new MetadataExpander(modalsContainer, onCreate)
    modalsContainer.appendChild(expander.modalDiv)
    expander.modalDiv
  }

  /**
    * TODO -
    *
    * @param modalsContainer
    */
  private final class MetadataExpander(modalsContainer: HTMLElement, onCreateNewSource: FormData => Unit) {
    private val newSourceModalContainer: Div = div().render

    private val closeElement: Span  = span(`class` := "close-modal")(img(src := "/img/icons8-close-window-24.png", `class` := "modal-close-button")).render
    private val createElement: Span = span(`class` := "modal-ok-button")("Create").render

    private val nameInput              = input(`type` := "text", name := "pushSrc", placeholder := "Name").render
    private val persistInput           = input(`type` := "checkbox", name := "persist", placeholder := "Persist").render
    private var metadataKeyValueFields = List[(HTMLInputElement, HTMLInputElement)]()

    lazy val modalDiv = {
      div(`class` := "modal", style := "display:block;") {
        div(`class` := "modal-content").apply(
          div(`class` := "modal-header").apply(
            closeElement,
            h2("New Source")
          ),
          div(`class` := "modal-body").apply(
            div(`class` := "form-style-1").apply(
              form().apply(
                nameInput,
                br,
                label(`for` := "persist")(
                  span(style := "padding-right:10px", "Persist :"),
                  persistInput
                ),
                label("Metadata:"),
                newSourceModalContainer
              )
            )
          ),
          div(`class` := "modal-footer")(
            div(createElement)
          )
        )
      }.render
    }
    createElement.onclick = _ => {
      createNewSource()
    }
    closeElement.onclick = _ => {
      modalsContainer.removeChild(modalDiv)
    }

    // The first Metaadata:
    appendMetadataDiv()

    // and one below it:
    appendMetadataDiv()

    private def keyValues(): immutable.Seq[(String, String)] = {
      metadataKeyValueFields.map {
        case (k, v) => (k.value, v.value)
      }
    }

    def createNewSource() = {
      val metadata = keyValues().toMap.filterNot {
        case (k, v) => k.isEmpty && v.isEmpty
      }
      onCreateNewSource(FormData(nameInput.value, persistInput.checked, metadata))
      modalsContainer.removeChild(modalDiv)
    }

    private def appendMetadataDiv(): Unit = {
      val (_, _, first) = keyValueDiv()
      newSourceModalContainer.appendChild(first)
    }

    /**
      * https://www.sanwebe.com/2014/08/css-html-forms-designs
      */
    private def keyValueDiv(): (Input, Input, LI) = {
      val keyInput   = input(`type` := "text", name := "key", `class` := "field-divided", placeholder := "Key").render
      val valueInput = input(`type` := "text", name := "value", `class` := "field-divided", placeholder := "Value").render
      metadataKeyValueFields = metadataKeyValueFields :+ (keyInput -> valueInput)

      keyInput.onkeyup = (event: KeyboardEvent) => {
        if (event.keyCode == 13) {
          valueInput.focus()
        }
      }

      valueInput.onkeyup = (event: KeyboardEvent) => {
        if (event.keyCode == 13) {
          HtmlUtils.log(s"key is '${keyInput.value}', value  '${valueInput.value}'")
          if (keyInput.value == "" && valueInput.value == "") {
            // treat an empty key/value pair as a submit
            createNewSource()
          } else {
            val (newKey, _, elm) = keyValueDiv()
            newSourceModalContainer.appendChild(elm)
            newKey.focus()
          }
        }
      }

      val element = li(`class` := "metadata-li", keyInput, valueInput).render
      (keyInput, valueInput, element)
    }
  }

}
