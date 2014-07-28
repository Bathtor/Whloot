package com.larskroll.whloot

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.ActorContext
import com.larskroll.neo4j._
import spray.json._
import com.larskroll.whloot.data._
import spray.http.StatusCodes._

trait PathMessage
trait PathRequest extends PathMessage
trait PathResponse extends PathMessage
case class PathAB(start: Either[String, Int], end: Either[String, Int]) extends PathRequest
case class SinglePath(route: Route) extends PathResponse
case class MultiPath(route: MultiRoute) extends PathResponse
case class PathError(msg: String) extends PathResponse

class PathService extends Actor with ActorLogging {
    import context._
    import WhlootJsonProtocol._
    import Graph._

    def receive = {
        case PathAB(start, end) => {
            Neo4J tx { implicit api =>
                //
                // Get Node IDs         
                //
                val aStmt = start match {
                    case Left(sysName) => Cypher(f"MATCH (s:System) WHERE s.name = '$sysName' RETURN id(s)")
                    case Right(sysId)  => Cypher(f"MATCH (s:System) WHERE s.id = $sysId RETURN id(s)")
                }
                val bStmt = end match {
                    case Left(sysName) => Cypher(f"MATCH (s:System) WHERE s.name = '$sysName' RETURN id(s)")
                    case Right(sysId)  => Cypher(f"MATCH (s:System) WHERE s.id = $sysId RETURN id(s)")
                }
                val res = Cypher.commit(aStmt, bStmt);
                val res2 = res match {
                    case Some(cRes) => {
                        if (cRes.code != OK) {
                            sender ! PathError("Could not get node ids");
                            None
                        } else {
                            val txRes = cRes.parse();
                            if (txRes.hasErrors) {
                                println("Result has errors: " + txRes);
                                sender ! PathError(txRes.errors.foldLeft("Errors encountered in query: ")(_ + _ + "\n"));
                                None
                            } else {
                                val ids = txRes.results.flatMap(_.extractField[Int]("id(s)"));
                                if (ids.size != 2) {
                                    println("Result has errors: " + txRes);
                                    sender ! PathError(txRes.errors.foldLeft("Errors encountered in query: ")(_ + _ + "\n"));
                                    None
                                } else {
                                    val aId = ids(0);
                                    val bId = ids(1);
                                    //
                                    // Get Path
                                    //
                                    val aNode = Graph.node(aId);
                                    val bNode = Graph.node(bId);
                                    Graph.path(aNode, bNode, "GATE", `out`, `shortestPath`(100));
                                }
                            }
                        }
                    }
                    case None => sender ! PathError("Lookup failed"); None
                }
                val path = res2 match {
                    case Some(tRes) => {
                        if (tRes.code != OK) {
                            println(f"Error getting Path, result was: ${tRes.code} -> ${tRes.body}");
                            sender ! PathError("Could not get Path");
                            None
                        } else {
                            val transRes = tRes.parse();
                            //
                            // Get System Info
                            //
                            Some(transRes.nodes);
                        }
                    }
                    case None => sender ! PathError("Route failed"); None
                }
                if (path.isDefined) {
                    val stmts = path.get.map(n => Cypher(f"START n=node(${n.id}) RETURN id(n),n"));
                    val res3 = Cypher.commit(stmts);
                    val solarSystemInfos = res3 match {
                        case Some(cRes2) => {
                            if (cRes2.code != OK) {
                                sender ! PathError("Could not get system information");
                                None
                            } else {
                                val txRes2 = cRes2.parse();
                                if (txRes2.hasErrors) {
                                    println("Result has errors: " + txRes2);
                                    sender ! PathError(txRes2.errors.foldLeft("Errors encountered in query: ")(_ + _ + "\n"));
                                    None
                                } else {
                                    val sysInfos = txRes2.results.flatMap(_.mapFields { fields =>
                                        {
                                            fields("id(n)").convertTo[Int] -> fields("n").convertTo[FullSolarSystem]
                                        }
                                    }).toMap;
                                    Some(sysInfos)
                                }
                            }
                        }
                        case None => sender ! PathError("Could not get System Info"); None
                    }
                    if (solarSystemInfos.isDefined) {
                        val route = Route(path.get.length, path.get.map(n => {
                            val fullSys = solarSystemInfos.get(n.id);
                            SolarSystem(fullSys.id, fullSys.name, fullSys.sec)
                        }));
                        sender ! SinglePath(route)
                    }
                }
            }
        }
    }
}