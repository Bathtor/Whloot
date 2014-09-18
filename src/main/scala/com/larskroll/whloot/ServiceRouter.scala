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
import scala.util.{ Try, Success, Failure }
import com.larskroll.whloot.data._

class ServiceRouterActor extends Actor with ServiceRouter with ActorLogging {

    def actorRefFactory = context
    def corsExp = context.system.settings.config.getString("whloot.routing.cors");

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

trait ServiceRouter extends HttpService with CORSDirectives {
    import BasicDirectives._
    import WhlootJsonProtocol._

    def corsExp: String;

    val apiService = actorRefFactory.actorOf(Props[APIService]);
    val opService = actorRefFactory.actorOf(Props[OpService]);
    val pathService = actorRefFactory.actorOf(Props[PathService]);

    implicit val timeout = Timeout(60 seconds);

    val conf = Main.system.settings.config;

    import ExecutionDirectives._
    def debugHandler(implicit log: LoggingContext) = ExceptionHandler {
        case e => ctx =>
            log.warning("Request {} could not be handled normally", ctx.request);
            e.printStackTrace();
            ctx.complete(InternalServerError, "An unknown error occurred. We apologize for this inconvenience.");
    }

    val detachAndRespond = respondWithMediaType(`application/json`) & handleExceptions(debugHandler) & detach();
    //val securityChecks = corsFilter("lars-kroll.com") | corsFilter("127.0.0.1");

    val primaryRoute: Route = {
        corsFilter(corsExp) {
            get {
                pathPrefix("eve") {
                    path("marketTypes") {
                        detachAndRespond {ctx => 
                        	ctx.complete(EVEStatic.topItems.toList);
                        }
                    } ~ path("marketTypes" / Segment) { pattern =>
                        detachAndRespond {ctx => 
                        	ctx.complete {
                        	    searchMarketTypes(pattern);
                        	}
                        }
                    }
                } ~ path("calc" / IntNumber / IntNumber / LongNumber) { (start, end, tidBound) =>
                    detachAndRespond { ctx =>
                        ctx.complete {
                            calcPayout(start, end, tidBound);
                        }
                    }
                } ~ path("calc" / IntNumber / IntNumber) { (start, end) =>
                    detachAndRespond { ctx =>
                        ctx.complete {
                            calcPayout(start, end, 0);
                        }
                    }
                    //			} ~ path("calc" / IntNumber) { opId =>
                    //				detachAndRespond { ctx =>
                    //					ctx.complete {
                    //						calcPayout(opId, Int.MaxValue);
                    //					}
                    //				}
                } ~ path("calc" / IntNumber) { opId =>
                    detachAndRespond { ctx =>
                        ctx.complete {
                            calcPayout(opId, Int.MaxValue, 0);
                        }
                    }
                } ~ pathPrefix("transactions") {
                    path("pull") {
                        detachAndRespond { ctx =>
                            ctx.complete {
                                pullAPI(GetTransactions);
                            }
                        }
                    }
                } ~ path("ops") {
                    detachAndRespond { ctx =>
                        ctx.complete {
                            listOps();
                        }
                    }
                } ~ path("assets") {
                    detachAndRespond { ctx =>
                        ctx.complete {
                            listAssets();
                        }
                    }
                } ~ path("assets" / IntNumber) { opId =>
                    detachAndRespond { ctx =>
                        ctx.complete {
                            listAssets(opId);
                        }
                    }
                } ~ pathPrefix("assets") {
                    path("pull") {
                        detachAndRespond { ctx =>
                            ctx.complete {
                                pullAPI(GetAssets);
                            }
                        }
                    } ~ path("clear") {
                        detachAndRespond { ctx =>
                            ctx.complete {
                                pullAPI(ClearAssets);
                            }
                        }
                    }
                } ~ pathPrefix("path") {
                    pathPrefix("from" / IntNumber) { start =>
                        path("to" / IntNumber) { end =>
                            detachAndRespond { ctx =>
                                ctx.complete {
                                    path(Right(start), Right(end));
                                }
                            }
                        } ~ path("to" / Segment) { end =>
                            detachAndRespond { ctx =>
                                ctx.complete {
                                    path(Right(start), Left(end));
                                }
                            }
                        } ~ path("toStation" / IntNumber) { end =>
                            detachAndRespond { ctx =>
                                ctx.complete {
                                    pathStation(Right(start), end);
                                }
                            }
                        }
                    } ~ pathPrefix("from" / Segment) { start =>
                        path("to" / IntNumber) { end =>
                            detachAndRespond { ctx =>
                                ctx.complete {
                                    path(Left(start), Right(end));
                                }
                            }
                        } ~ path("to" / Segment) { end =>
                            detachAndRespond { ctx =>
                                ctx.complete {
                                    path(Left(start), Left(end));
                                }
                            }
                        } ~ path("toStation" / IntNumber) { end =>
                            detachAndRespond { ctx =>
                                ctx.complete {
                                    pathStation(Left(start), end);
                                }
                            }
                        }
                    }
                } ~ path(Rest) { x =>
                    _.complete(x);
                }
            } ~ post {
                pathPrefix("eve") {
                    path("marketTypes" / LongNumber) { typeID => 
                    	detachAndRespond { ctx =>
                    		ctx.complete {
                    		    EVEStatic.rateMarketItem(typeID);
                    		    "OK"
                    		}
                    	}
                    }
                }
            }
        }
    }
    
    private def searchMarketTypes(pattern: String): List[ItemType] = {
        println("Searching for " + pattern);
        EVEStatic.searchMarketItem(pattern);
    }

    private def calcPayout(sinceOp: Int, upToOp: Int, tidBound: Long): Either[Payout, Failure[Payout]] = {
        val f = opService ? CalcPayoutRange(sinceOp, upToOp, tidBound);
        try {
            val res = Await.result(f, 30 seconds).asInstanceOf[Payout];

            val perMemString = res.perMember.map {
                case (member, money) => f"			${member.name}: ${money}%-,10.2f ISK \n"
            }.foldLeft("Distribution: \n ")(_ + _);
            println(f"""Payout: \n
				TotalIncome: ${res.totalIncome}%-,10.2f ISK \n
				Fuel Costs: ${res.fuelPart}%-,10.2f ISK for ${res.fuelDays} days \n
				Master Wallet: ${res.masterPart}%-,10.2f ISK / SRP Wallet: ${res.srpPart}%-,10.2f ISK \n
				Income after expenses: ${res.distributableIncome}%-,10.2f ISK \n
				${perMemString}%s""");
            return Left(res);
        } catch {
            case e: TimeoutException => Right(Failure(e));
        }
    }

    private def pullAPI(r: ApiRequest): String = {
        val f = apiService ? r;
        try {
            val res = Await.result(f, 30 seconds).asInstanceOf[ApiResponse];
            res match {
                case Pulled(txs, sells) => "Success: API pulled (TX: " + txs + ", Sells: " + sells + ")";
                case StillCached(until) => "API still cached until " + until;
                case PullFailed(e)      => "Fail: " + e.getError();
                case DiffFailed(msg)    => "Fail: " + msg;
                case Diff(added, removed) =>
                    added.map(asset =>
                        s"		Item(OpId: ${asset.opId}, ItemId: ${asset.itemId}, Name: ${asset.name}, Quantity: ${asset.quantity}) \n").foldLeft("Added: {\n")(_ + _) +
                        "} \n " + removed.map(asset =>
                            s"		Item(OpId: ${asset.opId}, ItemId: ${asset.itemId}, Name: ${asset.name}, Quantity: ${asset.quantity})").foldLeft("Removed: {\n")(_ + _) +
                        "} \n";
                case Cleared => "Success: Cleared";
            }
        } catch {
            case e: TimeoutException => "Fail: Operation Timeout";
        }
    }

    private def listAssets(opId: Int = Ops.ZERO): Either[List[Loot], Failure[List[Loot]]] = {
        val f = opService ? ListAssets(opId);
        try {
            val res = Await.result(f, 30 seconds).asInstanceOf[AssetList];
            Left(res.loots);
        } catch {
            case e: TimeoutException => Right(Failure(e));
        }
    }

    private def listOps(): Either[List[OpHeader], Failure[List[OpHeader]]] = {
        val f = opService ? ListOps;
        try {
            val res = Await.result(f, 30 seconds).asInstanceOf[OpList];
            Left(res.ops);
        } catch {
            case e: TimeoutException => Right(Failure(e));
        }
    }

    private def path(start: Either[String, Int], end: Either[String, Int]): Either[data.Route, Failure[data.Route]] = {
        val f = pathService ? PathAB(start, end);
        try {
            val res = Await.result(f, 30 seconds).asInstanceOf[PathResponse];
            res match {
                case SinglePath(route) => Left(route)
                case PathError(msg)    => println("Error: \"\n		" + msg + "\n\""); Right(Failure(ErrorMessage(msg)))
            }
        } catch {
            case e: Throwable => Right(Failure(e))
        }
    }

    private def pathStation(start: Either[String, Int], end: Int): Either[data.Route, Failure[data.Route]] = {
        val sysId = EVEStatic.station2systemId(end);
        return path(start, sysId.map(id => Right(id)).getOrElse(start)); // 0 route on failure
    }
}

case class ErrorMessage(msg: String) extends Throwable(msg) {

}