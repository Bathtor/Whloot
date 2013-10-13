package com.larskroll

import spray.util._
import spray.http._
import akka.actor.{ ActorLogging, Props, Actor }
import spray.routing.{ HttpService, RequestContext, Route }
import spray.routing.directives._
import spray.httpx.encoding._
import spray.httpx.SprayJsonSupport._
import spray.http._
import MediaTypes._
import HttpCharsets._
import spray.routing.ExceptionHandler
import spray.http.StatusCodes._

class ServiceRouterActor extends Actor with ServiceRouter with ActorLogging {
	
	def actorRefFactory = context

	def receive = runRoute(primaryRoute);
}

trait ServiceRouter extends HttpService {
	import BasicDirectives._
	import ExecutionDirectives._
	def debugHandler(implicit log: LoggingContext) = ExceptionHandler {
		case e => ctx =>
			log.warning("Request {} could not be handled normally", ctx.request);
			ctx.complete(InternalServerError, "An unknown error occurred. We apologize for this inconvenience.");
	}
	
	val detachAndRespond = respondWithMediaType(`application/json`) & handleExceptions(debugHandler) & detach();
	
	val primaryRoute: Route = {
		get {
			detachAndRespond { ctx =>
				ctx.complete("Hello")				
			}
		}
	}
}