package com.tutorialacademy.ds.titan;

import java.util.Iterator;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;

import com.thinkaurelius.titan.core.PropertyKey;
import com.thinkaurelius.titan.core.TitanFactory;
import com.thinkaurelius.titan.core.TitanGraph;
import com.thinkaurelius.titan.core.schema.SchemaAction;
import com.thinkaurelius.titan.core.schema.TitanManagement;
import com.thinkaurelius.titan.core.util.TitanCleanup;
import com.thinkaurelius.titan.example.GraphOfTheGodsFactory;
import com.thinkaurelius.titan.graphdb.database.management.ManagementSystem;
import com.tinkerpop.gremlin.java.GremlinPipeline;

public class TitanStart {

	private static String getRelativeResourcePath( String resource ) {
		return TitanStart.class.getClassLoader().getResource(resource).getPath();
	}
	
	public static void main(String[] args) throws Exception {

		Configuration conf = new PropertiesConfiguration( getRelativeResourcePath( "titan-cassandra-es.properties" ) );
		// start elastic search on startup
		Node node = new NodeBuilder().node();
		
		TitanGraph graph = TitanFactory.open(conf);
		/* Comment if you do not want to reload the graph every time */
		graph.close();
		TitanCleanup.clear(graph);
		graph = TitanFactory.open(conf);
		GraphOfTheGodsFactory.load(graph);
		/* graph loaded  */
		
		// create own indexes
		TitanManagement mgmt = graph.openManagement();
		PropertyKey name = mgmt.getPropertyKey("name");
		PropertyKey age = mgmt.getPropertyKey("age");
		mgmt.buildIndex( "byNameComposite", Vertex.class ).addKey(name).buildCompositeIndex();
		// index consisting of multiple properties
		mgmt.buildIndex( "byNameAndAgeComposite", Vertex.class ).addKey(name).addKey(age).buildCompositeIndex();
		mgmt.commit();
		
		// wait for the index to become available
		ManagementSystem.awaitGraphIndexStatus(graph, "byNameComposite").call();
		ManagementSystem.awaitGraphIndexStatus(graph, "byNameAndAgeComposite").call();
		
		// create new vertex
		Vertex me = graph.addVertex("theOneAndOnly");
		me.property( "name", "me" );
		me.property( "age", 1 );
		graph.tx().commit();
		System.out.println("Created the one and only!");
		
		// re index the existing data (not required, just for demo purposes)
		mgmt = graph.openManagement();
		mgmt.updateIndex( mgmt.getGraphIndex("byNameComposite"), SchemaAction.REINDEX ).get();
		mgmt.updateIndex( mgmt.getGraphIndex("byNameAndAgeComposite"), SchemaAction.REINDEX ).get();
		mgmt.commit();
		
		GraphTraversalSource g = graph.traversal();
		GremlinPipeline<GraphTraversal<?, ?>, ?> pipe = new GremlinPipeline();
		
		// read our new vertex
		pipe.start( g.V().has( "name", "me" ) );	
		Vertex v = (Vertex)pipe.next();
		System.out.println();
		System.out.println( "Label: " + v.label() );
		System.out.println( "Name: " + v.property("name").value() );
		System.out.println( "Age: " + v.property("age").value() );
		System.out.println();
		
		// read different vertex
		pipe.start( g.V().has( "name", "hercules" ) );	
		Vertex herclues = (Vertex)pipe.next();
		System.out.println( "Label: " + herclues.label() );
		System.out.println( "Name: " + herclues.property("name").value() );
		System.out.println( "Age: " + herclues.property("age").value() );
		
		// print some edges
		Iterator<Edge> it = herclues.edges( Direction.OUT );
		while( it.hasNext() ) {
			Edge e = it.next();
			System.out.println( "Out: " + e.label()  + " --> " + e.inVertex().property("name").value() );
		}
		System.out.println();
		
		// close graph
		graph.close();
		// close elastic search on shutdown
		node.close();
		
		System.exit(0);
	}
}
