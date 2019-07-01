package pipelines.audit

trait HasVersionDetails {

  def version: VersionDetails

  def revision: Int = version.revision
  def createdAt: Long = version.createdAt
  def userId: String = version.userId
}
