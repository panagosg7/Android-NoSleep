package edu.ucsd.energy.util;

import java.io.File;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.FilterIterator;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.INodeWithNumber;
import com.ibm.wala.util.graph.impl.InvertedGraph;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.SCCIterator;
import com.ibm.wala.util.graph.traverse.Topological;

import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.viz.GraphDotUtil;

public class GraphUtils {
	/**
	 * Returns a list of T elements in a bottom up order
	 * @param g the graph to traverse
	 * @param f filter some of the root nodes
	 * @return
	 */
	public static <T> Iterator<T> bottomUpIterator(Graph<T> g, Filter<T> f) {
		InvertedGraph<T> invertedGraph = new InvertedGraph<T>(g);
		//A topological sort preserves the edge constraints
	    Iterator<T> topoIter = Topological.makeTopologicalIter(invertedGraph);
	    FilterIterator<T> filterIter = new FilterIterator<T>(topoIter, f);	    
	    return filterIter;
	}

	public static <T> Iterator<T> bottomUpIterator(Graph<T> g) {
		InvertedGraph<T> invertedGraph = new InvertedGraph<T>(g);
		//A topological sort preserves the edge constraints
	    return Topological.makeTopologicalIter(invertedGraph);
	}

	public static <T> Iterator<T> topDownIterator(Graph<T> g) {
	    return Topological.makeTopologicalIter(g);
	}	
	
	public static <V extends INodeWithNumber> SparseNumberedGraph<V> merge(SparseNumberedGraph<V> a, SparseNumberedGraph<V> b) {
		  SparseNumberedGraph<V> graph = new SparseNumberedGraph<V>();
		  for(Iterator<V> it = a.iterator(); it.hasNext();) {
			  graph.addNode(it.next());
		  }
		  for(Iterator<V> it = b.iterator(); it.hasNext();) {
			  graph.addNode(it.next());
		  }
		  for(Iterator<V> it1 = graph.iterator(); it1.hasNext();) {
			  V x = it1.next();
			  for(Iterator<V> it2 = graph.iterator(); it2.hasNext();) {
				 V y = it2.next();
				 if(a.hasEdge(x, y) || b.hasEdge(x, y)) {
					 graph.addEdge(x, y);
				 }
			  }
		  }
		  return graph;
	}
	
	public static <T> void dumpConstraintGraph(Graph<T> constraintGraph, String tag) {
		try {
			//E.log(1, "Dumping constraint graph for " + tag + "... ");
			Properties p = null;
			p = WalaExamplesProperties.loadProperties();
			p.putAll(WalaProperties.loadProperties());
			String dotFile = SystemUtil.getResultDirectory() + 
					File.separatorChar + tag + "_dependencies.dot";
			String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
			GraphDotUtil.dotify(constraintGraph, null, dotFile, null, dotExe);
			return;
		} catch (WalaException e) {
			e.printStackTrace();
			return;
		}
	  }	
	
	
	public static <T> Iterator<Set<T>> connectedComponentIterator(Graph<T> g) {
		DoubleLinkedGraph<T> doubleLinkedGraph = new DoubleLinkedGraph<T>(g);
		return new SCCIterator<T>(doubleLinkedGraph);		
	}

	public static void dumpConnectedComponents(SparseNumberedGraph<Context> g) {
		//E.log(1, "Dumping connecting components... ");
		Iterator<Set<Context>> ccIt = GraphUtils.connectedComponentIterator(g);
	    while(ccIt.hasNext()) {
	    	Set<Context> sComp = ccIt.next();    	
	    	for(Context c :sComp) {
	    		System.out.println(c.toString());	    		
	    	}
	    	System.out.println("----------------------------------");
	    }		
	}
	
}
