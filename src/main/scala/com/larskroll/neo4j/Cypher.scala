package com.larskroll.neo4j

import spray.http._

case class CypherStatement(cypher: String, params: Map[String, Any] = Map()) {
    def on(args: (String, Any)*) = this.copy(params = params ++ args);
    def commit(implicit api: RESTEndpoint) = api.commit(this);
    def query(implicit api: RESTEndpoint) = api.query(this);

    def replaceParams(): String = {
        if (params.isEmpty) {
            return cypher;
        }
        var str = cypher;
        params.foreach {
            case (k, p) => {
                str = str.replaceAll("{\\s" + k + "\\s}", p.toString);
            }
        };
        return str;
    }
}

abstract class CypherResult[T](val code: StatusCode, val body: HttpEntity) {
    def parse(): T;
}

object Cypher {
    def apply(query: String) = CypherStatement(query);

    def commit(statements: CypherStatement*)(implicit api: RESTEndpoint) = api.commit(statements);
    
    def commit(statements: Traversable[CypherStatement])(implicit api: RESTEndpoint) = api.commit(statements.toSeq);
    
    
    def query(statement: CypherStatement)(implicit api: RESTEndpoint) = api.query(statement);

}

object Graph {

    trait GraphAlgorithm
    case class `shortestPath`(maxDepth: Int) extends GraphAlgorithm
    case class `dijkstra`(costProperty: String) extends GraphAlgorithm
    
    trait EdgeDirection
    case object `in` extends EdgeDirection
    case object `out` extends EdgeDirection

    def node(id: Int)(implicit api: RESTEndpoint) = Node(id, api.nodeEndpoint(id));

    def path(from: Node, to: Node, edgeType: String, edgeDirection: EdgeDirection, algo: GraphAlgorithm)(implicit api: RESTEndpoint) = api.path(from, Traversal(to, edgeType, edgeDirection, algo));
}


