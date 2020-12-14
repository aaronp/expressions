package expressions.client

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}

import java.net.InetSocketAddress

object SimpleHttpServer {

  def start(port: Int, handlers: Map[String, HttpHandler]): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress(port), 0)
    handlers.foreach {
      case (path, handler) => server.createContext(path, handler)
    }
    server.setExecutor(null)
    server.start()
    server
  }

  /**
    * create an HttpHandler for the given thunk
    */
  def handler(onMsg: HttpExchange => String): HttpHandler = new HttpHandler {
    override def handle(exchange: HttpExchange): Unit = {
      val response: String = onMsg(exchange)
      exchange.sendResponseHeaders(200, response.length.toLong)
      val os = exchange.getResponseBody
      os.write(response.getBytes)
      os.close()
    }
  }
}
