package com.larskroll.neo4j

import spray.json._
import spray.http._
import spray.httpx.unmarshalling._
import spray.httpx.marshalling._
import HttpCharsets._
import MediaTypes._
import HttpMethods._
import scala.util.Success
import scala.util.Failure
import scala.concurrent._
import scala.concurrent.duration._
import spray.httpx.SprayJsonSupport
import akka.util.Timeout
import Graph._

case class StatementBlock(statement: CypherStatement)
case class StatementsBlock(statements: List[StatementBlock])
case class StatementResult(columns: List[String], data: List[JsValue]) {
    
    def extractField[T: JsonReader](pos: Int): List[T] = {
        data.map { value =>
            value.asJsObject.getFields("row") match {
                case Seq(JsArray(contents)) => contents(pos).convertTo[T];
                case _                      => throw new DeserializationException("Unkown result format!")
            }
        }
    }
    def extractField[T: JsonReader](column: String): List[T] = {
        val pos = columns.indexWhere(_.equalsIgnoreCase(column));
        if (pos >= 0) {
            extractField(pos)
        } else {
            throw new DeserializationException(f"No column named $column in $columns!");
        }
    }
    def mapFields[T](f: FieldAccessor => T): List[T] = {
        data.map { value =>
            value.asJsObject.getFields("row") match {
                case Seq(JsArray(contents)) => f(new FieldAccessor(contents))
                case _                      => throw new DeserializationException("Unkown result format!")
            }
        }
    }
    class FieldAccessor(contents: List[JsValue]) {
        def apply(pos: Int): JsValue = contents(pos);
        def apply(name: String): JsValue = {
            val pos = columns.indexWhere(_.equalsIgnoreCase(name));
            contents(pos)
        }
    }
}
case class QueryError(code: String, message: String)
case class TxResult(results: List[StatementResult], errors: List[QueryError]) {
    def hasErrors = !errors.isEmpty
}
case class Node(id: Int, endpoint: String)
case class Relationship(id: Int, endpoint: String)
case class Traversal(to: Node, edgeType: String, edgeDirection: EdgeDirection, algo: GraphAlgorithm)
case class TraversalResult(weight: Option[Float], start: Node, nodes: List[Node], length: Int, relationships: List[Relationship], end:Node)

class RESTEndpoint(implicit val ec: ExecutionContext, val baseURL: String = "http://localhost:7474/db/data/", val user: String = "", val pass: String = "") extends SprayJsonSupport {
    import Neo4JsonProtocol._

    implicit val timeout = Timeout(25 seconds);

    val txEndpoint = baseURL + "transaction/commit";
    val cypherEndpoint = baseURL + "cypher";
    val nodesEndpoint = baseURL + "node";
    def nodeEndpoint(nodeId: Int) = nodesEndpoint + "/" + nodeId;
    def pathEndpoint(nodeId: Int) = nodeEndpoint(nodeId) + "/path";
    def pathEndpoint(node: Node) = node.endpoint + "/path";
    def pathsEndpoint(nodeId: Int) = nodeEndpoint(nodeId) + "/paths";
    def pathsEndpoint(node: Node) = node.endpoint + "/paths";

    def commit(statements: Seq[CypherStatement]): Option[CypherResult[TxResult]] = {
        val block = StatementsBlock(statements.map(stmt => StatementBlock(stmt)).toList);
        commit(block)
    }
    def commit(stmt: CypherStatement): Option[CypherResult[TxResult]] = {
        val block = StatementsBlock(Seq(StatementBlock(stmt)).toList);
        commit(block)
    }

    def query(statement: CypherStatement): Option[CypherResult[_]] = {
        None
    }

    def path(from: Node, trav: Traversal): Option[CypherResult[TraversalResult]] = {
        marshal(trav) match {
            case Right(entity) => {
                val f = Neo4J ? Query(POST, pathEndpoint(from), entity);
                try {
                    val res = Await.result(f, 25 seconds);
                    return Some(new TraversalCypherResult(res.status, res.entity));
                } catch {
                    case e: TimeoutException => {
                        println(f"""Path Algorithm failed. Got 
                        $e
                        for
                        $from -> $trav
                """);
                        return None
                    }
                }
            }
            case Left(error) => {
                println(f"""Path Algorithm failed. Got 
                        $error
                        for
                        $from -> $trav
                """);
                return None
            }
        }
    }

    private def commit(block: StatementsBlock): Option[CypherResult[TxResult]] = {
        marshal(block) match {
            case Right(entity) => {
                val f = Neo4J ? Query(POST, txEndpoint, entity);
                try {
                    val res = Await.result(f, 25 seconds);
                    return Some(new TxCypherResult(res.status, res.entity));
                } catch {
                    case e: TimeoutException => {
                        println(f"""Transaction failed. Got 
                        $e
                        for
                        $block""");
                        return None
                    }
                }
            }
            case Left(error) => {
                println(f"""Transaction failed. Got 
                        $error
                        for
                        $block
                """);
                return None
            }
        }
        return None
    }

    class TxCypherResult(code: StatusCode, body: HttpEntity) extends CypherResult[TxResult](code, body) {
        override def parse(): TxResult = body.as[TxResult] match {
            case Right(x) => x;
            case Left(e)  => println(e); null;
        }
    }
    
    class TraversalCypherResult(code: StatusCode, body: HttpEntity) extends CypherResult[TraversalResult](code, body) {
        override def parse(): TraversalResult = body.as[TraversalResult] match {
            case Right(x) => x;
            case Left(e)  => println(e); null;
        }
    }
}




