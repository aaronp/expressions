package pipelines.client

object Constants {
  object pages {
    val IndexPage = "/index.html"
    val MainPage  = "/app.html"
    val LoginPage = "/users/login.html"
  }

  /**
    * These mirror what's set up in app.js
    *
    * there is a TODO for migrating that hand-cranked javascript into ScalaJS
    *
    */
  object components {
    val pushSource  = "pushSource"
    val sourceTable = "sourceTable"
  }
}
