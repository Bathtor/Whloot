package com.larskroll.neo4j

import spray.json._

object Neo4JsonProtocol extends DefaultJsonProtocol {
    implicit object StatementFormat extends JsonFormat[StatementBlock] {
        def write(m: StatementBlock): JsValue = {
            val paramCypher = m.statement.replaceParams();
            return JsObject("statement" -> JsString(paramCypher));
        }
        def read(value: JsValue): StatementBlock = {
            throw new Exception("No necessity to deserialize StatementBlock");
        }
    }
    implicit object BlockFormat extends RootJsonFormat[StatementsBlock] {
        def write(m: StatementsBlock) =
            JsObject("statements" -> JsArray(m.statements.map(_.toJson).toList));
        def read(value: JsValue): StatementsBlock = {
            throw new Exception("No necessity to deserialize StatementsBlock");
        }
    }

    implicit object StatementResultFormat extends JsonFormat[StatementResult] {
        def write(m: StatementResult): JsValue = {
            throw new Exception("No necessity to serialize StatementResult");
        }
        def read(value: JsValue): StatementResult = {
            value.asJsObject.getFields("columns", "data") match {
                case Seq(JsArray(columns), JsArray(data)) => {
                	val cols = columns.map { 
                	    case JsString(s) => s
                	    case _ => throw new DeserializationException("Unkown result format!")
                	}
                	StatementResult(cols, data);
                }
                case _ => throw new DeserializationException("Unkown result format!")
            }
        }
    }

    implicit object QueryErrorFormat extends JsonFormat[QueryError] {
        def write(m: QueryError): JsValue = {
            throw new Exception("No necessity to serialize StatementResult");
        }
        def read(value: JsValue): QueryError = {
            value.asJsObject.getFields("code", "message") match {
                case Seq(JsString(code), JsString(message)) => QueryError(code, message)
                case _                                      => throw new DeserializationException("Unkown result format!")
            }
        }
    }

    implicit object TxResultFormat extends RootJsonFormat[TxResult] {
        def write(m: TxResult): JsValue = {
            throw new Exception("No necessity to serialize TxResult");
        }
        def read(value: JsValue): TxResult = {
            value.asJsObject.getFields("results", "errors") match {
                case Seq(JsArray(results), JsArray(errors)) => {
                    val res = results.map(StatementResultFormat.read(_));
                    val errs = errors.map(QueryErrorFormat.read(_));
                    TxResult(res, errs);
                }
                case _ => throw new DeserializationException("Unkown result format!")
            }
        }
    }
    
    implicit object NodeFormat extends JsonFormat[Node] {
        def write(n: Node): JsValue = {
            JsString(n.endpoint);
        }
        def read(value: JsValue): Node = {
            value match {
                case JsString(endpoint) => {
                    val parts = endpoint.split("/");
                    val id = parts(parts.length-1).toInt;
                    Node(id, endpoint)
                }
                case _ => throw new DeserializationException("Unkown result format!")
            }
        }
    }
    implicit object RelationshipFormat extends JsonFormat[Relationship] {
        def write(n: Relationship): JsValue = {
            JsString(n.endpoint);
        }
        def read(value: JsValue): Relationship = {
            value match {
                case JsString(endpoint) => {
                    val parts = endpoint.split("/");
                    val id = parts(parts.length-1).toInt;
                    Relationship(id, endpoint)
                }
                case _ => throw new DeserializationException("Unkown result format!")
            }
        }
    }
    
    implicit object TraversalFormat extends RootJsonFormat[Traversal] {
        import Graph._
        def write(m: Traversal): JsValue = {
            val algoProp = m.algo match {
                case `shortestPath`(maxDepth) => ("max_depth" -> JsNumber(maxDepth))
                case `dijkstra`(costProp) => ("cost_property" -> JsString(costProp))
            }
            val rel = "relationships" -> JsObject("type" -> JsString(m.edgeType), "direction" -> JsString(m.edgeDirection.getClass().getSimpleName().stripSuffix("$")));
            JsObject("to" ->NodeFormat.write(m.to), algoProp, rel, "algorithm" -> JsString(m.algo.getClass().getSimpleName()))
        }
        def read(value: JsValue): Traversal = {
            throw new Exception("No necessity to deserialize Traversal");
        }
    }
    
    implicit object TraversalResultFormat extends RootJsonFormat[TraversalResult] {
        import Graph._
        def write(m: TraversalResult): JsValue = {
            throw new Exception("No necessity to serialize TraversalResult");
        }
        def read(value: JsValue): TraversalResult = {
            value.asJsObject.getFields("weight", "start", "nodes", "length", "relationships", "end") match {
                case Seq(JsNumber(weight), start: JsString, JsArray(nodes), JsNumber(length), JsArray(relationships), end: JsString) => {
                    val nds = nodes.map(NodeFormat.read);
                    val rels = relationships.map(RelationshipFormat.read);
                    TraversalResult(Some(weight.toFloat), NodeFormat.read(start), nds, length.toInt, rels, NodeFormat.read(end))
                }
                case Seq(start: JsString, JsArray(nodes), JsNumber(length), JsArray(relationships), end: JsString) => {
                    val nds = nodes.map(NodeFormat.read);
                    val rels = relationships.map(RelationshipFormat.read);
                    TraversalResult(None, NodeFormat.read(start), nds, length.toInt, rels, NodeFormat.read(end))
                
                }
            }
        }
    }
}