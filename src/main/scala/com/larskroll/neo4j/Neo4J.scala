package com.larskroll.neo4j

import akka.actor._
import akka.routing._
import akka.pattern.{ ask, pipe }
import spray.http._
import spray.client.pipelining._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext
import HttpMethods._
import akka.util.Timeout

trait Neo4JQuery
case class Query(method: HttpMethod, url: String, body: HttpEntity) extends Neo4JQuery

object Neo4J {

    implicit val timeout = Timeout(25 seconds);

    var workers: ActorRef = null;

    def init(implicit as: ActorSystem, numWorkers: Int = 1) {
        this.synchronized {
            workers = as.actorOf(Props[Neo4JActor].withRouter(RoundRobinRouter(nrOfInstances = numWorkers)));
        }
    }

    def ?(msg: Neo4JQuery) = (workers ? msg).mapTo[HttpResponse];

    def tx[A](execution: RESTEndpoint => A)(implicit ec: ExecutionContext): A = {
        val api = new RESTEndpoint();
        execution(api);
    }
}

class Neo4JActor extends Actor with ActorLogging {
    implicit val system = context.system;
    import system.dispatcher;

    val pipeline: HttpRequest => Future[HttpResponse] = (
        addHeader("accept", "application/json")
        ~> addHeader("X-Stream", "true")
        ~> addHeader("User-Agent", "Whloot/0.1")
        ~> sendReceive);

    def receive = {
        case q @ Query(method, url, body) => {
            //println(q);
            pipeline(HttpRequest(method = method, uri = url, entity = body)) pipeTo sender;
        }
    }
}