package com.larskroll.whloot

import spray.util._
import spray.http._
import akka.actor.{ ActorLogging, Actor }
import akka.pattern.{ ask, pipe }
import scala.concurrent._
import scala.concurrent.duration._
import akka.util.Timeout
import spray.routing.{ HttpService, Route }
import spray.routing.directives._
import spray.httpx.encoding._
import spray.http._
import spray.http.MediaTypes._
import spray.http.HttpCharsets._
import spray.http.StatusCodes._
import spray.json._
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import scalikejdbc.ConnectionPool
import spray.routing.ExceptionHandler
import akka.actor.Props

class ServiceRouterActor extends Actor with ServiceRouter with ActorLogging {

	def actorRefFactory = context

	///////////////// DB Stuff //////////////////

	// MySQL
	connectDB('mysql, "whloot.db.mysql", conf);

	def receive = runRoute(primaryRoute);

	private def connectDB(name: Symbol, prefix: String, conf: com.typesafe.config.Config): Unit = {
		val driver = conf.getString(prefix + ".driver");
		val url = conf.getString(prefix + ".url");
		val user = conf.getString(prefix + ".user");
		val pw = conf.getString(prefix + ".pw");
		Class.forName(driver);
		ConnectionPool.add(name, url, user, pw);
	}
}

trait ServiceRouter extends HttpService {
	import BasicDirectives._
	
	val apiService = actorRefFactory.actorOf(Props[APIService]);
	val opService = actorRefFactory.actorOf(Props[OpService]);
	
	implicit val timeout = Timeout(5 seconds);

	val conf = Main.system.settings.config;

	import ExecutionDirectives._
	def debugHandler(implicit log: LoggingContext) = ExceptionHandler {
		case e => ctx =>
			log.warning("Request {} could not be handled normally", ctx.request);
			ctx.complete(InternalServerError, "An unknown error occurred. We apologize for this inconvenience.");
	}

	val detachAndRespond = respondWithMediaType(`application/json`) & handleExceptions(debugHandler) & detach();

	val primaryRoute: Route = {
		get {
			path("calc"/IntNumber) {opId =>
				detachAndRespond { ctx =>
						ctx.complete {
							calcPayout(opId);
						}
					}
			} ~ pathPrefix("pull") {
				path("transactions") {
					detachAndRespond { ctx =>
						ctx.complete {
							pullAPI(GetTransactions);
						}
					}
				} ~ path("assets") {
					detachAndRespond { ctx =>
						ctx.complete {
							pullAPI(GetAssets);
						}
					}
				}				
			}
		}
	}
	
	private def calcPayout(sinceOp: Int): String = {
		val f = opService ? CalcPayout(sinceOp);
		try {
			val res = Await.result(f, 30 seconds).asInstanceOf[Payout];
			val perMemString = res.perMember.map {
				case (member, money) => f"			${member.name}: ${money}%-,10.2f ISK \n"
			}.foldLeft("Distribution: \n ")(_ + _);
			f"""Payout: \n
				TotalIncome: ${res.totalIncome}%-,10.2f ISK \n
				Fuel Costs: ${res.fuelPart}%-,10.2f ISK for ${res.fuelDays} days \n
				Master Wallet: ${res.masterPart}%-,10.2f ISK / SRP Wallet: ${res.srpPart}%-,10.2f ISK \n
				Income after expenses: ${res.distributableIncome}%-,10.2f ISK \n
				${perMemString}%s""";
		} catch {
			case e: TimeoutException => "Fail: Operation Timeout";
		}
	}
	
	private def pullAPI(r: ApiRequest): String = {
		val f = apiService ? r;
		try {
			val res = Await.result(f, 30 seconds).asInstanceOf[ApiResponse];
			res match {
				case Pulled(txs, sells) => "Success: API pulled (TX: " + txs + ", Sells: " + sells + ")";
				case StillCached(until) => "API still cached until " + until;
				case PullFailed(e) => "Fail: " + e.getError();
			}
		} catch {
			case e: TimeoutException => "Fail: Operation Timeout";
		}
	}
}