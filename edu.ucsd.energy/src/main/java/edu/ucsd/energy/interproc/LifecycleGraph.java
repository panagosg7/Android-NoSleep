package edu.ucsd.energy.interproc;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.impl.NodeWithNumber;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.viz.NodeDecorator;

import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.LifecycleGraph.SensibleCGNode;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SystemUtil;
import edu.ucsd.energy.viz.GraphDotUtil;

public class LifecycleGraph extends SparseNumberedGraph<SensibleCGNode> {

	private static final int DEBUG = 2;


	public static class SensibleCGNode extends NodeWithNumber {
		private Selector name = null;
		private CallBack realCallBack = null;

		public static SensibleCGNode makeNonEmpty(CallBack callback) {
			return new SensibleCGNode(callback);
		}

		public static SensibleCGNode makeEmpty(Selector s) {
			return new SensibleCGNode(s);
		}

		private SensibleCGNode(CallBack cb) {
			this.setCallBack(cb);
			this.name = cb.getNode().getMethod().getSelector();
		}        

		private SensibleCGNode(Selector s) {
			this.name = s;
		}

		public Selector getSelector() {
			return this.name;
		} 

		@Override
		public String toString() {
			return name.toString();    
		}

		public boolean isEmpty() {
			return (getRealNode() == null);
		}

		public CGNode getRealNode() {
			if (realCallBack != null) {
				return realCallBack.getNode();
			}
			return null;
		}

		public boolean equals(Object o) {
			if (o instanceof SensibleCGNode) {
				SensibleCGNode n = (SensibleCGNode) o;
				return getSelector().equals(n.getSelector());
			}
			return false;
		}
		
		public void setCallBack(CallBack cb) {
			this.realCallBack = cb;
		}

		public CallBack getCallBack() {
			return realCallBack;
		}
	}




	private HashMap<Selector,SensibleCGNode> sensibleMap;

	private Context component;

	public LifecycleGraph(Context component) {
		this.component = component;        
		sensibleMap = new HashMap<Selector, SensibleCGNode>();

		/* Initialization part:
		 * Add all nodes and edges to the sensible graph 
		 * irrelevant of whether they are found in the 
		 * actual call graph - they will be pruned later.
		 */
		for (Selector cb : component.getTypicalCallbacks()) {
			addSensibleNode(cb);
		}
		for (Pair<Selector, Selector> edge : component.getCallbackEdges()) {
			addSensibleEdge(edge.fst, edge.snd);
		}

		/* Prune empty nodes */
		removeEmptyNodes();

		if (DEBUG < 2) {
			outputToDot();
		}
	}

	/**
	 * Remove nodes that are not found in the actual graph by
	 * short-circuiting the adjacent edges.
	 */
	private void removeEmptyNodes() {
		for (Iterator<SensibleCGNode> iter = iterator(); iter.hasNext();) {
			SensibleCGNode node = iter.next();
			if (node.isEmpty()) {
				E.log(DEBUG, "Removing: " + node.toString());
				IntSet predSet = getPredNodeNumbers(node);
				IntSet succSet = getSuccNodeNumbers(node);        
				if (predSet != null && succSet != null) {
					E.log(DEBUG, "#PredNodes: " + predSet);
					E.log(DEBUG, "#SuccNodes: " + succSet);
					IntIterator predIterator = predSet.intIterator();
					IntIterator succIterator = succSet.intIterator();
					while(predIterator.hasNext()) {            
						SensibleCGNode src = getNode(predIterator.next());
						while(succIterator.hasNext()) {
							SensibleCGNode dst = getNode(succIterator.next());
							addEdge(src, dst);              
						}
					}
				};
				removeNodeAndEdges(node);
			}
		}
	}

	private void addSensibleNode(Selector sel) {
		CallBack callback = component.getCallBack(sel);
		if (callback != null) {
			SensibleCGNode snode = SensibleCGNode.makeNonEmpty(callback);
			sensibleMap.put(sel, snode);
			addNode(snode);
		}
		else {
			SensibleCGNode snode = SensibleCGNode.makeEmpty(sel);
			sensibleMap.put(sel, snode);
			addNode(snode);
		}
		E.log(DEBUG,  (callback!=null)?(sel.toString()):"<null>");
		return;
	}

	private void addSensibleEdge (Selector src, Selector dst) {
		SensibleCGNode snode = sensibleMap.get(src);
		SensibleCGNode dnode = sensibleMap.get(dst);        
		if (snode!=null && dnode!=null) {     
			addEdge(snode,dnode);
		}
		return;
	}

	public void outputToDot () {
		Properties p = WalaExamplesProperties.loadProperties();
		try {
			p.putAll(WalaProperties.loadProperties());
			String className = component.getKlass().getName().toString();        
			String bareFileName = className.replace('/', '.');

			String folder = SystemUtil.getResultDirectory() + File.separatorChar + "aux";
			new File(folder).mkdirs();
			String fileName = folder + File.separatorChar + bareFileName + ".dot";
			NodeDecorator nd = new NodeDecorator() {
				public String getLabel(Object o) throws WalaException {
					return ((SensibleCGNode) o).getSelector().toString();
				}
			};  
			E.log(DEBUG,  "Dumping auxilary sensible call graph for this component...");
			GraphDotUtil.dotify(this , nd, fileName, null, null);
		} catch (WalaException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Return a set with pairs of all the edges in the custom 
	 * sensible graph
	 * @return 
	 */
	public HashSet<Pair<CGNode, CGNode>> packEdges() {
		HashSet<Pair<CGNode, CGNode>> result = new HashSet<Pair<CGNode, CGNode>>();
		for (Iterator<SensibleCGNode> iter = iterator(); iter.hasNext();) {
			SensibleCGNode node = iter.next();
			if (node.isEmpty()) {
				/* Empty nodes should be gone */
				Assertions.UNREACHABLE();
			}
			else {
				CGNode src = node.getRealNode();        
				for (Iterator<SensibleCGNode> iterSucc = getSuccNodes(node);
						iterSucc.hasNext();) {
					SensibleCGNode destNode = iterSucc.next();
					//String dst = destNode.getRealNode().getMethod().getSignature().toString();
					CGNode dst = destNode.getRealNode();
					result.add(Pair.make(src,dst));
					E.log(DEBUG, src + "," + dst );
				}        
			}    
		}
		return result;
	}



}
