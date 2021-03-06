package edu.ucsd.energy.managers;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.graph.AbstractNumberedGraph;
import com.ibm.wala.util.graph.NumberedEdgeManager;
import com.ibm.wala.util.graph.NumberedNodeManager;
import com.ibm.wala.util.graph.traverse.SCCIterator;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.viz.DotUtil;

import edu.ucsd.energy.analysis.Options;
import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.util.SystemUtil;
import edu.ucsd.energy.util.Util;

public class SCCManager {
  
  private  AppCallGraph cg = null;
  private  SSCGraph sccGraph;  

  public  class SCC {
    private Set<CGNode> nodeSet;
    private int id;
    
    
    private HashSet<SCC> outgoingSet;
    private HashSet<SCC> incomingSet;
    
    /*
     * SCC constructor
     */
    SCC(int id, Set<CGNode> ns) {
      nodeSet     = ns;
      this.id     = id;
      outgoingSet = new HashSet<SCC>();
      incomingSet = new HashSet<SCC>();   
    }            
    
    
    public boolean equals(Object scc) {
      if (scc instanceof SCC) {
        return (this.id == ((SCC) scc).getId());        
      }
      else 
        return false;
    }
    
    public int size() {
      return nodeSet.size();
    }

    public void addPredSCC(SCC scc) {
      incomingSet.add(scc);
    }

    public void remPredSCC(SCC scc) {
      incomingSet.remove(scc);
    }

    public void addSuccSCC(SCC scc) {
      outgoingSet.add(scc);      
    }

    public void remSuccSCC(SCC scc) {
      outgoingSet.remove(scc);      
    }

    
    public Set<CGNode> getNodeSet() {
      return nodeSet;
    }

    public int getId() {
      return id;
    }

    public HashSet<SCC> getOutgoingSet() {
      return outgoingSet;
    }

    public HashSet<SCC> getIncomingSet() {
      return incomingSet;
    }
     
    public String toString() {
      if (getNodeSet().size() > 1) {
        return "BLOB: " + getNodeSet().iterator().next().getMethod().getSignature().toString();
      }
      if (getNodeSet().size() > 0) {        
        return getNodeSet().iterator().next().getMethod().getSignature().toString();
      }
      return super.toString();
    }
    
    
  }

  public  class SCCNodeManager implements NumberedNodeManager<SCC> {
    private int id_counter;
    
    private HashMap<CGNode, SCC>  nodeToSCC         = null;
    private HashSet<SCC>          sccSet            = null;    
    private HashMap<Integer, SCC> sccMap            = null;
    
    /*
     * Manager construction and initialization
     */
    SCCNodeManager(AppCallGraph cg) {
      
      nodeToSCC   = new HashMap<CGNode, SCC>();
      sccSet      = new HashSet<SCC>();
      sccMap      = new HashMap<Integer, SCCManager.SCC>();
      id_counter  = 0;
      
      /*
       * Create the SCCs and register each of their nodes
       */
      SCCIterator<CGNode> sccIterator = new SCCIterator<CGNode>(cg);
      while(sccIterator.hasNext()) {
        Set<CGNode> sccNodes  = sccIterator.next();
        
        /* The new SCC */
        int id = getID();
        SCC scc = new SCC(id, sccNodes);
        sccSet.add(scc);
        sccMap.put(id, scc);
        
        for (CGNode node : sccNodes) {
          registerNode(node,scc);
        }
      }
      
      /*
       * Compute incoming and outgoing edges
       */      
      for(SCC scc : sccSet) {
        addNode(scc);
      }
      
    }
    
    /* Register: node -> SCC */
    public void registerNode (CGNode node, SCC scc) {
      nodeToSCC.put(node, scc);      
    }
  
    public int getID () {
      return id_counter ++ ;
    }

    public SCC getSCCFromNode(CGNode node) {
      return nodeToSCC.get(node);
      
    }

    public Iterator<SCC> iterator() {     
      return sccSet.iterator();
    }

    public int getNumberOfNodes() {
      return sccSet.size();
    }

    public void addNode(SCC scc) {
      for (CGNode node: scc.getNodeSet()) {                  
        Iterator<CGNode> succNodes = cg.getSuccNodes(node);        
        /* Iterate over successor cg nodes of this node */
        while(succNodes.hasNext()) {
          CGNode nextNode = succNodes.next();
          SCC nextSCC = getSCCFromNode(nextNode);
          if (!scc.equals(nextSCC)) {
            scc.addSuccSCC(nextSCC);
          }            
        }      
        Iterator<CGNode> predNodes = cg.getPredNodes(node);
        /* Iterate over successor cg nodes of this node */
        while(predNodes.hasNext()) {
          CGNode predNode = predNodes.next();
          SCC predSCC = getSCCFromNode(predNode);
          if (!scc.equals(predSCC)) {
            scc.addPredSCC(predSCC);
          }
        }          
      }
    }

    public void removeNode(SCC n) throws UnsupportedOperationException {
      
    }

    
    public boolean containsNode(SCC n) {
      return sccSet.contains(n);
    }

    
    public int getNumber(SCC N) {
      return N.getId();
    }

    
    public SCC getNode(int number) {
      return sccMap.get(number);
    }

    
    public int getMaxNumber() {
      return id_counter;
    }

    
    public Iterator<SCC> iterateNodes(IntSet s) {
      HashSet<SCC> set = new HashSet<SCC>();
      IntIterator iter = s.intIterator();
      while (iter.hasNext()) {
        SCC scc = sccMap.get(iter.next());        
        if (scc != null) {
          set.add(scc);   
        }
      }
      return set.iterator();
    }
    
  }
  
  public  class SCCEdgeManager implements NumberedEdgeManager<SCC> {

    
    public Iterator<SCC> getPredNodes(SCC n) {
      return n.getIncomingSet().iterator();
    }

    
    public int getPredNodeCount(SCC n) {
      return n.getIncomingSet().size();
    }

    
    public Iterator<SCC> getSuccNodes(SCC n) {
      return n.getOutgoingSet().iterator();
    }

    
    public int getSuccNodeCount(SCC n) {
      return n.getOutgoingSet().size();
    }

    
    public void addEdge(SCC src, SCC dst) {
      src.addSuccSCC(dst);
      dst.addPredSCC(src);      
    }

    
    public void removeEdge(SCC src, SCC dst) throws UnsupportedOperationException {
      src.remSuccSCC(dst);
      dst.remPredSCC(src);      
    }

    
    public void removeAllIncidentEdges(SCC node) throws UnsupportedOperationException {
      node.incomingSet.clear();
      node.outgoingSet.clear();
    }

    
    public void removeIncomingEdges(SCC node) throws UnsupportedOperationException {
      node.incomingSet.clear();      
    }

    
    public void removeOutgoingEdges(SCC node) throws UnsupportedOperationException {
      node.outgoingSet.clear();
    }

    
    public boolean hasEdge(SCC src, SCC dst) {     
      return src.outgoingSet.contains(dst);
    }

    
    public IntSet getSuccNodeNumbers(SCC node) {
      IntSet set = IntSetUtil.make(); 
      for (SCC sn : node.outgoingSet) {
        IntSetUtil.add(set, sn.getId());
      };
      return set;
    }

    
    public IntSet getPredNodeNumbers(SCC node) {
      IntSet set = IntSetUtil.make(); 
      for (SCC sn : node.incomingSet) {
        IntSetUtil.add(set, sn.getId());
      };
      return set;
    }
    
  }
    
  public  class SSCGraph extends AbstractNumberedGraph<SCC> {

    private SCCNodeManager sccNodeManager;
    private SCCEdgeManager sccEdgeManager;

    
    protected NumberedNodeManager<SCC> getNodeManager() {
      return sccNodeManager;
    }

    
    protected NumberedEdgeManager<SCC> getEdgeManager() {      
      return sccEdgeManager;
    }
    
    public SSCGraph(AppCallGraph cg) {
      sccNodeManager = new SCCNodeManager(cg);
      sccEdgeManager = new SCCEdgeManager();
    }

    public void outputCallgraphToDot() throws WalaException {
      File dotFile = /*AndroidAnalysisOptions.OUTPUT_FOLDER*/
          new File(SystemUtil.getResultDirectory(), "scc_cg.dot");
      DotUtil.writeDotFile(this, null, "SCC", dotFile);      
      
    }
    
  }
  
  private void build() throws WalaException {
    sccGraph = new SSCGraph(cg);
    if (Options.OUTPUT_SCC_DOT_FILE) { 
      sccGraph.outputCallgraphToDot();
    }
//    return sccGraph;
  }
  

  public void analyze() {
    
    Iterator<SCC> sccItr = sccGraph.iterator();
    
    int i = 0;
    while (sccItr.hasNext()) {
      SCC scc = sccItr.next();
      System.out.println(i + ". SCC: #nodes = " + scc.size());
      
      int j     = 0;
      int appl  = 0;
      int prim  = 0;
      int java  = 0;
      
      for (CGNode node : scc.getNodeSet()) {
      
        IClass klass = node.getMethod().getDeclaringClass();
        j++;
        if (j < 10) {
          System.out.println("  " + node.getMethod().getSignature());
          try {
            System.out.println("  Class: " + klass.getName());
            System.out.println("  Implements: " + klass.getDirectInterfaces());
          } catch (Exception e) {
          }
        }
        if (klass.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
          appl++;
        } else if (klass.getClassLoader().getReference().equals(ClassLoaderReference.Primordial)) {
          prim++;
        } else if (klass.getClassLoader().getReference().equals(ClassLoaderReference.Java)) {
          java++;
        }
      }
      i++;
      System.out.println("App: " + (100 * appl / j) + " %, " + "Primordial: " + 
          (100 * prim / j) + " %, " + "Java: " + (100 * java / j) + " %");
      System.out.println();

    }

  }

  
  
  
  public SCCManager(AppCallGraph callGraph) throws WalaException {
    cg = callGraph;
    build();
  }

}
