package expressions.rest

import zio.Has

package object server {

  type Disk = Has[Disk.Service]
}
