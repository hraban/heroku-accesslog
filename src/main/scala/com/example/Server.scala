package com.example

import com.twitter.finagle
import com.twitter.finagle.httpx.filter
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.{Await, Future, Time}
import com.twitter.logging.Logger
import java.net
import util.Properties
import java.sql.Connection
import java.sql.DriverManager
import scala.collection.JavaConversions._

object Server {
  private def logFilter = {
    val log = Logger.get("accesslog")
    val formatter = new filter.CommonLogFormatter
    new filter.LoggingFilter(log, formatter)
  }

  def main(args: Array[String]) {
    val port = Properties.envOrElse("PORT", "8080").toInt
    println("Starting on port: "+port)

    val errfilter = new filter.ExceptionFilter[Request]
    val service = logFilter andThen errfilter andThen (new HelloFactory)
    val server = finagle.Httpx.serve(":" + port, service)
    Await.ready(server)
  }
}

class HelloFactory extends finagle.ServiceFactory[Request, Response] {
  def apply(conn: finagle.ClientConnection): Future[finagle.Service[Request, Response]] =
    Future(new Hello(conn))

  def close(deadline: Time): Future[Unit] =
    // what Im not getting this
    Future(Unit)
}

class Hello(client: finagle.ClientConnection) extends finagle.Service[Request, Response] {

  private def serializeHeaders(entries: Iterable[(String, String)]): String =
    entries map { x => x._1 + ": " + x._2 } mkString "\n"

  private def route(path: String) = path match {
    case "/log" => showDatabase _
    case "/debug" => showDebug _
    case _ => showHome _
  }

  def apply(request: Request): Future[Response] = {
    route(request.path)(request)
  }

  private def addr2str(addr: net.SocketAddress): String = addr match {
    case i: net.InetSocketAddress => i.getAddress.getHostAddress.toString
    case _ => addr.toString()
  }

  private def clientip: String = addr2str(client.remoteAddress)

  def showHome(request: Request): Future[Response] = {
    println("test");
    val response = Response()
    response.setStatusCode(200)
    response.setContentString("Hello " + clientip + "\n\n" + serializeHeaders(request headerMap))
    Future(response)
  }

  def showDebug(request: Request): Future[Response] = {
    println("test2");
    println(System.getenv("DATABASE_URL"));
    val response = Response()
    response.setStatusCode(200)
    response.setContentString("DB: " + System.getenv("DATABASE_URL"))
    return Future(response)
  }

  def showDatabase(request: Request): Future[Response] = {
    val connection = getConnection
    val stmt = connection.createStatement
    stmt.executeUpdate("DROP TABLE IF EXISTS ticks")
    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS accesslog (time timestamp, method text, client text, path text)")
    val query = connection.prepareStatement("INSERT INTO accesslog VALUES (now(), ?, ?, ?)")
    query.setString(1, request.xForwardedFor map (_.toString) getOrElse "")
    query.setString(2, clientip)
    query.setString(3, request.path)
    query.executeUpdate()

    val rs = stmt.executeQuery("SELECT time, method, client, path FROM accesslog ORDER BY time DESC LIMIT 100")

    // Is string builder hoisted outside of a while loop by optimizer?
    val sb = new StringBuilder()
    sb.append("Access log:\n\n")
    while (rs.next) {
      println("Got result")
      List(
        rs.getTimestamp("time"),
        " ",
        rs.getString("client"),
        " ",
        rs.getString("method"),
        " ",
        rs.getString("path"),
        "\n"
      ).foreach(sb.append)
    }

    val response = Response()
    response.setStatusCode(200)
    response.setContentString(sb.toString)
    Future(response)
  }

  def getConnection(): Connection = {
    val dbUri = new net.URI(System.getenv("DATABASE_URL"))
    val userinfo = Option(dbUri.getUserInfo)
    val username = userinfo map (_.split(":")) filter (_.length > 0) map (_(0)) getOrElse null
    val password = userinfo map (_.split(":")) filter (_.length > 1) map (_(1)) getOrElse null
    val host = Option(dbUri.getHost) getOrElse "localhost"
    val dbUrl = "jdbc:postgresql://" + host + dbUri.getPath
    println("Connecting to: " + dbUrl)
    DriverManager.getConnection(dbUrl, username, password)
  }
}
