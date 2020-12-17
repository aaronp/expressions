package expressions.rest.server

class MappingConfigTest extends BaseRouteTest {
  "MappingConfig.pathForTopic" should {
    "resolve paths" in {
      val cfg = MappingConfig("app.mapping.foo = file.sc", """ app.mapping.bar = "path/to/file.sc" """, """ app.mapping."ba.*" = "ba-default.sc" """)
      val makeDisk = for {
        d <- Disk.Service()
        _ <- d.write(List("file.sc"), "file.sc")
        _ <- d.write(List("file.sc"), "file.sc")
        _ <- d.write(List("path", "to", "file.sc"), "path/to/file.sc")
        _ <- d.write(List("ba-default.sc"), "ba-default.sc")
      } yield d

      cfg.lookup("foo") shouldBe Some("file.sc" :: Nil)
      cfg.lookup("bar") shouldBe Some("path/to/file.sc".split("/").toList)
      cfg.lookup("bazz") shouldBe Some("ba-default.sc" :: Nil)
      cfg.lookup("buzz") shouldBe None
    }
  }
}
