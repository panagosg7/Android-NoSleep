package edu.ucsd.energy.interproc;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.viz.NodeDecorator;

import edu.ucsd.energy.components.Component;
import edu.ucsd.energy.components.Component.CallBack;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SystemUtil;
import edu.ucsd.energy.viz.GraphDotUtil;

public class SensibleCallGraph extends SparseNumberedGraph<SensibleCGNode> {
      
  private HashMap<String,SensibleCGNode> sensibleMap;
  
  private Component component;

  public SensibleCallGraph(Component component) {
    this.component = component;        
    sensibleMap = new HashMap<String, SensibleCGNode>();
    
    /* Initialization part:
     * Add all nodes and edges to the sensible graph 
     * irrelevant of whether they are found in the 
     * actual call graph - they will be pruned later.
     */
    for (String str : component.getCallbackNames()) {
      addSensibleNode(str);
    }
    for (Pair<String, String> edge : component.getCallbackEdges()) {
      String src = edge.fst;
      String dst = edge.snd;      
      addSensibleEdge(src, dst);
    }
    
    /* Prune empty nodes */
    removeEmptyNodes();       
  }
  
  /**
   * Remove nodes that are not found in the actual graph 
   * short-circuiting the adjacent edges.
   */
  private void removeEmptyNodes() {
	  
    for (Iterator<SensibleCGNode> iter = iterator(); iter.hasNext();) {
    	
      SensibleCGNode node = iter.next();
      if (node.isEmpty()) {
        E.log(2,"Removing: " + node.toString());
        IntSet predSet = getPredNodeNumbers(node);
        IntSet succSet = getSuccNodeNumbers(node);        
        if (predSet != null && succSet != null) {
          E.log(2,"#PredNodes: " + predSet);
          E.log(2,"#SuccNodes: " + succSet);
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

  
  
  public void addSensibleNode(String name) {    
	CallBack callback = component.getCallBackByName(name);
	if (callback != null) {
		CGNode cbNode = callback.getNode();
		SensibleCGNode snode = new SensibleCGNode(name, cbNode);
	    sensibleMap.put(name, snode);
	    addNode(snode);
	}
	else {
		SensibleCGNode snode = new SensibleCGNode(name, null);
	    sensibleMap.put(name, snode);
	    addNode(snode);
	}
	if (callback!=null) {
      E.log(2,"Initializing: " + name);
    }
    else {
      /* add dummy node */
      E.log(3,"<null>");      
    }
	return;
  }
  
  
  public void addSensibleEdge (String src, String dst) {
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
      String dotFile = SystemUtil.getResultDirectory() + 
          File.separatorChar + "aux_scg_" + 
          bareFileName + ".dot";            
      NodeDecorator nd = new NodeDecorator() {
        public String getLabel(Object o) throws WalaException {
          return ((SensibleCGNode) o).getName();
        }
      };  
      E.log(2, "Dumping: " + dotFile);
      GraphDotUtil.dotify(this , nd, dotFile, null, null);
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
          E.log(2,src + "," + dst );
        }        
      }    
    }
    return result;
  }

  
  
}
