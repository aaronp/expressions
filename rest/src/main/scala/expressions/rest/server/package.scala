package expressions.rest

import expressions.RichDynamicJson
import expressions.template.Message
import zio.Has

package object server {

  type Disk = Has[Disk.Service]

  type JsonMsg = Message[RichDynamicJson, RichDynamicJson]
}
