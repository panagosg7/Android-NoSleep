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
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetAction;
import com.ibm.wala.viz.NodeDecorator;

import edu.ucsd.energy.component.CallBack;
import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.LifecycleGraph.SensibleCGNode;
import edu.ucsd.energy.util.SystemUtil;
import edu.ucsd.energy.viz.GraphDotUtil;

public class LifecycleGraph extends SparseNumberedGraph<SensibleCGNode> {

	//Set to 1 to dump auxiliary life-cycle graphs
	
	//Set to more to get more detailed debug messages
	private static final int DEBUG = 1;

	private HashMap<Selector, SensibleCGNode> dictionary;

	private Context component;


	public class SensibleCGNode extends NodeWithNumber {

		private Selector name = null;

		//The callback corresponding to a sensible node
		//This will be null if the callback is not overridden  
		private CallBack realCallBack = null;

		SensibleCGNode(CallBack cb) {
			realCallBack = cb;
			this.name = cb.getNode().getMethod().getSelector();
		}        

		public SensibleCGNode(Selector s) {
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

		public CallBack getCallBack() {
			return realCallBack;
		}

	}

	/**
	 * The life-cycle graph before removing the missing nodes 
	 */
	private SparseNumberedGraph<SensibleCGNode> fullLifeCycleGraph;
	
	public SparseNumberedGraph<SensibleCGNode> getFullLifeCycleGraph() {
		return fullLifeCycleGraph;
	}
	
	public LifecycleGraph(Context context) {
		this.component = context;
		dictionary = new HashMap<Selector, SensibleCGNode>();
		
		if (DEBUG > 1) {
			System.out.println();
			System.out.println("Creating lifecycle graph: " + context.toString());
			System.out.println("Size typ callbacks: " + context.getTypicalCallbacks());
		}
		
		for (Selector cb : context.getTypicalCallbacks()) {
			addSensibleNode(cb);
		}
		for (Pair<Selector, Selector> edge : context.getCallbackEdges()) {
			addSensibleEdge(edge.fst, edge.snd);
		}
		//The full life-cycle graph will be the snapshot of the final graph  
		//without removing the empty edges from it.
		fullLifeCycleGraph = getSnapShot();
		
		removeEmptyNodes();

		if (DEBUG > 0) {
			outputToDot();
		}
	}


	private SparseNumberedGraph<SensibleCGNode> getSnapShot() {
		SparseNumberedGraph<SensibleCGNode> g = new SparseNumberedGraph<SensibleCGNode>();
		for (Iterator<SensibleCGNode> it = iterator(); it.hasNext(); ) {
			g.addNode(it.next());
		}
		for (Iterator<SensibleCGNode> it = iterator(); it.hasNext(); ) {
			SensibleCGNode n = it.next();
			for (Iterator<SensibleCGNode> iit = getSuccNodes(n); iit.hasNext(); ) {
				g.addEdge(n, iit.next());
			}
		}
		return g;
	}

	public SensibleCGNode find(Selector s) {
		if (s != null) {
			return dictionary.get(s);
		}
		return null;
	}


	/**
	 * Remove nodes that are not found in the actual graph by
	 * short-circuiting the adjacent edges.
	 */
	private void removeEmptyNodes() {
		for (Iterator<SensibleCGNode> iter = iterator(); iter.hasNext();) {
			SensibleCGNode node = iter.next();
			if (node.isEmpty()) {
				if (DEBUG > 1) {
					System.out.println("Removing: " + node.toString());
				}
				IntSet predSet = getPredNodeNumbers(node);
				final IntSet succSet = getSuccNodeNumbers(node);        
				if (predSet != null && succSet != null) {
					if (DEBUG > 1) {
						System.out.println("#PredNodes: " + predSet);
						System.out.println("#SuccNodes: " + succSet);
					}
					predSet.foreach(new IntSetAction() {
						public void act(final int p) {
							succSet.foreach(new IntSetAction() {
								public void act(int s) {
									SensibleCGNode pn = getNode(p);
									SensibleCGNode sn = getNode(s);
									if (DEBUG > 1) {
										System.out.println("Connecting: " + pn + " -> " + sn);
									}
									addEdge(pn, sn);
								}
							});
						};
					});
				};
				removeNodeAndEdges(node);
			}
		}
	}


	private void addSensibleNode(Selector sel) {
		CallBack callback = component.getCallBack(sel);
		SensibleCGNode snode = (callback!=null)?(new SensibleCGNode(callback)):(new SensibleCGNode(sel));
		dictionary.put(sel, snode);	
		addNode(snode);
		if (DEBUG > 1) {
			System.out.println("Adding callback: " + getNumber(snode) + " : " + 
					sel.toString() + ((callback!=null)?(""):"<null>"));
		}
	}


	private void addSensibleEdge (Selector src, Selector dst) {
		SensibleCGNode snode = dictionary.get(src);
		SensibleCGNode dnode = dictionary.get(dst);        
		if ((snode!=null) && (dnode!=null)) {     
			addEdge(snode, dnode);
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
			String fullFileName = folder + File.separatorChar + "FULL_" + bareFileName + ".dot";
			NodeDecorator nd = new NodeDecorator() {
				public String getLabel(Object o) throws WalaException {
					return ((SensibleCGNode) o).getSelector().toString();
				}
			};  
			if (DEBUG > 1) {
				System.out.println("Dumping auxilary sensible call graph for: " + component.toString());
			}
			GraphDotUtil.dotify(this , nd, fileName, null, null);
			GraphDotUtil.dotify(fullLifeCycleGraph , nd, fullFileName, null, null);
		} catch (WalaException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Return a set with pairs of all the edges in the life-cycle graph
	 * after removing the non-overridden nodes.
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
					if (DEBUG > 1) {
						System.out.println("Packing edge: " + src + ", " + dst );
					}
				}        
			}    
		}
		return result;
	}



}
