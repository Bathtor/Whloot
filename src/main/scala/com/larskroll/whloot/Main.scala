package com.larskroll.whloot

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import com.larskroll.neo4j.Neo4J

object Main extends App with MySslConfiguration {

	// we need an ActorSystem to host our application in
	implicit val system = ActorSystem("whloot");
	
	// get host settings from config
	val hostname = system.settings.config.getString("whloot.host.hostname");
	val port = system.settings.config.getInt("whloot.host.port");
	
	// the handler actor replies to incoming HttpRequests
	val handler = system.actorOf(Props[ServiceRouterActor], "service-router");
	
	Neo4J.init(system);

	
	// create a new HttpServer using our handler tell it where to bind to
	IO(Http) ! Http.Bind(handler, interface = hostname, port = port);
}