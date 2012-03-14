package energy.interproc;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.collections.Pair;

import energy.components.Component;
import energy.util.E;
import energy.util.SSAProgramPoint;
import energy.util.Util;

public class SensibleExplodedInterproceduralCFG extends ExplodedInterproceduralCFG {
  
  /**
   * Constructor that takes as arguments the initial call graph and the
   * pairs of methods (Signatures) that need to be connected.
   * @param cg
   * @param packedEdges
   * @param threadInvocations 
   */
  public SensibleExplodedInterproceduralCFG(CallGraph cg, 
		  HashSet<Pair<CGNode, CGNode>> packedEdges, 
		  HashMap<SSAProgramPoint, Component> threadInvocations) {
    super(cg);
    /* Will only work like this - loses laziness. */
    constructFullGraph();
      
    /* Do not add these edges at the moment - just use the information taken
     * from the analysis of the component (w\o context sensitivity). */
    
    this.threadInvocations = threadInvocations; 
    
    addReturnToEntryEdge(cg,packedEdges);  
    
    cacheCallbacks(cg,packedEdges);
  } 
  
  private Map<String,CGNode> callbacks;  
  private HashMap<SSAProgramPoint, Component> threadInvocations;  
  
  /**
   * The adjacent nodes to the packed edges are the interesting callbacks.
   * So we will cache them and apply our policies later.
   * @param packedEdges 
   * @param cg 
   */
 private void cacheCallbacks(CallGraph cg, HashSet<Pair<CGNode, CGNode>> packedEdges) {
   callbacks = new HashMap<String, CGNode>();
   for (Pair<CGNode, CGNode> e : packedEdges) {
     getCallbacks().put(e.fst.getMethod().getName().toString(), e.fst);
     getCallbacks().put(e.snd.getMethod().getName().toString(), e.snd);
   }
 }
  
 private void addReturnToEntryEdge(CallGraph cg, HashSet<Pair<CGNode, CGNode>> packedEdges) {
   Set<CGNode> nodeset = Util.iteratorToSet(cg.iterator());
   HashMap<String, CGNode> cgNodeSet = new HashMap<String, CGNode>();
   
   for (CGNode node  : nodeset) {
     String signature = node.getMethod().getSignature().toString();
     cgNodeSet.put(signature, node);
   }
   
   for(Pair<CGNode, CGNode> edge : packedEdges) {
     CGNode startcgNode = edge.fst;      
     ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> startCFG = getCFG(startcgNode);      
     CGNode stopcgNode = edge.snd;
     ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> stopCFG = getCFG(stopcgNode);
     
     IExplodedBasicBlock startBB = startCFG.exit();
     IExplodedBasicBlock stopBB = stopCFG.entry();
     
     BasicBlockInContext<IExplodedBasicBlock> p = 
         new BasicBlockInContext<IExplodedBasicBlock>(startcgNode, startBB);
     BasicBlockInContext<IExplodedBasicBlock> b =
         new BasicBlockInContext<IExplodedBasicBlock>(stopcgNode, stopBB);     
     this.addEdge(p,b);
     E.log(2,"Added edge:" + p.toString() +" -> "+ b.toString());     
   }
  }

 /**
  * Add call edges to thread invocations, using the mapping that we have 
  * gathered earlier.
 * @param threadInvocations 
  * @param m
  */
 private void addThreadEdges(HashMap<SSAProgramPoint, Component> threadInvocations) {	 		
	 for (Entry<SSAProgramPoint,Component> e : threadInvocations.entrySet()) {
		 SSAProgramPoint callPP = e.getKey();
		 CGNode caller = callPP.getCGNode();
		 IExplodedBasicBlock callBlock = ppToIEBB(callPP);
		 Component c = e.getValue();
		 CGNode target = c.getCallBackByName("run");
		 ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> tcfg = getCFG(target);
		 if (target != null) { //this must be the case
			 E.log(1, "Adding call to entry edge.");
			 addEdgesFromCallToEntry(caller, callBlock, target, tcfg);
			 //private void addEdgesFromExitToReturn(CGNode caller, T returnBlock, CGNode target,
			 //ControlFlowGraph<SSAInstruction, ? extends T> targetCFG) {
			 ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = getCFG(target);
			 for (Iterator<IExplodedBasicBlock> returnBlocks = cfg.getSuccNodes(callBlock); returnBlocks.hasNext();) {
				 IExplodedBasicBlock retBlock = returnBlocks.next();
				 E.log(1, "Adding exit to return edge: " + target.getMethod().getName().toString() +
		        		 " -> " + caller + retBlock.toString() );
				 addEdgesFromExitToReturn(caller, retBlock, target, tcfg);
		         
			 }
		 }
	 }
	 
 }
 

 protected boolean isReturn(BasicBlockInContext<IExplodedBasicBlock> B, 
		 ControlFlowGraph<SSAInstruction,IExplodedBasicBlock> cfg) {
	 
	    SSAInstruction[] statements = cfg.getInstructions();
	
	    int lastIndex = B.getLastInstructionIndex();
	    if (lastIndex >= 0) {	
	      if (statements.length <= lastIndex) {
	        System.err.println(statements.length);
	        System.err.println(cfg);
	        assert lastIndex < statements.length : "bad BB " + B + " and CFG for " + getCGNode(B);
	      }
	      SSAInstruction last = statements[lastIndex];
	      return (last instanceof SSAReturnInstruction);
	    } else {
	      return false;
	    }
	
 	}

 	public Map<String,CGNode> getCallbacks() {
 		return callbacks;
 	}

 	
 	
 	private IExplodedBasicBlock ppToIEBB(SSAProgramPoint pp) {
 		ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = getCFG(pp.getCGNode()); 		
		int number = pp.getCGNode().getIR().getControlFlowGraph().getNumber(pp.getBasicBlock());
 		IExplodedBasicBlock iebb = cfg.getNode(number);
 		//BasicBlockInContext<IExplodedBasicBlock> bbic = new BasicBlockInContext<IExplodedBasicBlock>(pp.getCGNode(), bb);
		return iebb;
 	}	


}

