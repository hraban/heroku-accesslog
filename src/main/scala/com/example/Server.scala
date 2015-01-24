package com.example

import com.twitter.finagle.{Http, Service}
import com.twitter.util.{Await, Future}
import com.twitter.finagle.http.Response
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http._
import util.Properties
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager

object Server {
  def main(args: Array[String]) {
    val port = Properties.envOrElse("PORT", "8080").toInt
    println("Starting on port: "+port)

    val server = Http.serve(":" + port, new Hello)
    Await.ready(server)
  }
}

class Hello extends Service[HttpRequest, HttpResponse] {
  def apply(request: HttpRequest): Future[HttpResponse] = {
    if (request.getUri.endsWith("/log")) {
      log(request);
    } else {
      showHome(request);
    }
  }

  def showHome(request: HttpRequest): Future[HttpResponse] = {
    val response = Response()
    response.setStatusCode(200)
    response.setContentString(request.getHeaders())
    Future(response)
  }

  def showDatabase(request: HttpRequest): Future[HttpResponse] = {
    val connection = getConnection
    val stmt = connection.createStatement
    stmt.executeUpdate("DROP TABLE ticks")
    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS accesslog (time timestamp, method string, client string, path text)")
    val query = connection.prepareStatement("INSERT INTO accesslog VALUES (now(), ?, ?)")
    query.setString(1, request.getHeader("X-Forwarded-For"))
    query.setString(2, request.getUri())

    val rs = stmt.executeQuery("SELECT time, method, client, path FROM accesslog LIMIT 100")

    var sb = new StringBuilder()
    while (rs.next) {
      sb.append(rs.getTimestamp("time"))
      sb.append(" ")
      sb.append(rs.getString("client"))
      sb.append(" ")
      sb.append(rs.getString("method"))
      sb.append(" ")
      sb.append(rs.getString("path"))
      sb.append("\n")
    }

    val response = Response()
    response.setStatusCode(200)
    response.setContentString(sb.toString())
    Future(response)
  }

  def getConnection(): Connection = {
    val dbUri = new URI(System.getenv("DATABASE_URL"))
    val username = dbUri.getUserInfo.split(":")(0)
    val password = dbUri.getUserInfo.split(":")(1)
    val dbUrl = "jdbc:postgresql://" + dbUri.getHost + dbUri.getPath
    DriverManager.getConnection(dbUrl, username, password)
  }
}
