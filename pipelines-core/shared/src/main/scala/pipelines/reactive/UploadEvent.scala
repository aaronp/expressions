package pipelines.reactive

import java.nio.file.Path

import pipelines.users.Claims

/**
  * Represents a user-upload. This isn't serializable as-is: it's meant/intended to be mapped by various transforms into another type
  *
  * @param user
  * @param uploadPath
  * @param fileName
  */
final case class UploadEvent(user: Claims, uploadPath: Path, fileName: String)
