package energy.interproc;


import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.ProgramCounter;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.analysis.ExplodedControlFlowGraph;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

import energy.analysis.AppCallGraph;
import energy.analysis.SpecialConditions.SpecialCondition;
import energy.components.Component;
import energy.components.RunnableThread;
import energy.util.E;
import energy.util.Util;

public class SensibleExplodedInterproceduralCFG extends ExplodedInterproceduralCFG {
  
  /**
   * Constructor that takes as arguments the initial call graph and the
   * pairs of methods (Signatures) that need to be connected.
   * @param cg
 * @param originalCallgraph 
   * @param packedEdges
   */
  public SensibleExplodedInterproceduralCFG(
		  CallGraph cg,									//The components CG 
		  AppCallGraph originalCallgraph,		//The whole application's CG 
		  HashSet<Pair<CGNode, CGNode>> packedEdges) {
	  
    super(cg);
    
    this.applicationCG = originalCallgraph;    
    
    /* Will only work like this - loses laziness. */
    constructFullGraph();
      
    /* Do not add these edges at the moment - just use the information taken
     * from the analysis of the component (w\o context sensitivity). */
    
    addReturnToEntryEdge(cg,packedEdges);  
    
    cacheCallbacks(cg,packedEdges);
  } 
  
  private AppCallGraph applicationCG;
    
  private Map<String,CGNode> callbacks;
  
  
  
  /**
   * Keep a map to all thread calls 
   */
  private HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component> ebbToThreadComponent = null;
  
  /**
   * Also, keep a map to any special conditions: null checks, isHeld()
   */
  private HashMap<BasicBlockInContext<IExplodedBasicBlock>, SpecialCondition> ebbToSpecConditions = null;
  
  
  
  
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

 	public RunnableThread getThreadInvocations(BasicBlockInContext<IExplodedBasicBlock> bbic) {
 		if (ebbToThreadComponent == null) {
 			E.log(1, "Not initialized");
 		}
 		else { 		
	 		Component component = ebbToThreadComponent.get(bbic);
	 		if (component != null) {
	 			Assertions.productionAssertion(component instanceof RunnableThread);
	 			return (RunnableThread) component;
	 		}
 		}
 		return null;
 	}
 	
 	public SpecialCondition getSpecialConditions(BasicBlockInContext<IExplodedBasicBlock> bbic) {
 		if (ebbToSpecConditions == null) {
 			E.log(1, "Special conditions not resolved");
 		}
 		else { 		
	 		return ebbToSpecConditions.get(bbic);	 		
 		}
 		return null;
 	}
 	
 	
 	
 	/**
	 * Simple method that detects an exceptional edge
	 * Src and Dst must belong to the same method.
	 * Src node is the one throwing the exception, so 
	 * we can search in its instructions and find the 
	 * ones that throw a certain exception. 
	 * @param src
	 * @param dest
	 * @return
	 */
	public boolean isExceptionalEdge(
			BasicBlockInContext<IExplodedBasicBlock> src,
			BasicBlockInContext<IExplodedBasicBlock> dest) {
				
		for(Iterator<SSAInstruction> it = src.iterator(); it.hasNext(); ) {
			SSAInstruction i = it.next();
			Collection<TypeReference> exceptionTypes = i.getExceptionTypes();
			for(TypeReference et : exceptionTypes) {
				//TODO: Create a set somewhere with all the exceptions to be ignored
				
				if(!et.equals(TypeReference.JavaLangNullPointerException)) {
					//E.log(1, et.toString());	
				}
			}
		}
		
		if (getCGNode(src).equals(getCGNode(dest))) {
			ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = getCFG(src);
			IExplodedBasicBlock d = src.getDelegate();
			Collection<IExplodedBasicBlock> normalSuccessors = cfg.getNormalSuccessors(d);
			int nsn = normalSuccessors.size();
			int snc = cfg.getSuccNodeCount(d);
			if (nsn != snc) {
				IExplodedBasicBlock dst = dest.getDelegate();
				return (!normalSuccessors.contains(dst));
			}
		}
		return false;
	} 	
 	
 	
 	
 	/**
 	 * WARNING: SET THIS RIGHT AFTER CONSTRAINTS HAVE BEEN RESOLVED 
 	 * @param m
 	 */
 	public void setThreadInvocations(HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component> m) { 		
 		this.ebbToThreadComponent = m;
 	}
 	
 	public void setSpecConditions(HashMap<BasicBlockInContext<IExplodedBasicBlock>, SpecialCondition> m) {    
    this.ebbToSpecConditions = m;
 	}
  
 	
 	
 	public HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component> getThreadInvocations() {
 		return ebbToThreadComponent;
 	}
 	
 	
 	public void printBBToThreadMap() {
 		E.log(1, "");
 		for ( Entry<BasicBlockInContext<IExplodedBasicBlock>, Component> e : ebbToThreadComponent.entrySet()) {
 			System.out.println(e.getKey().toString() + " -> " + e.getValue());
 		} 		
 	}

	public AppCallGraph getApplicationCG() {
		return applicationCG;
	}
 	
	public BasicBlockInContext<IExplodedBasicBlock> getExplodedBasicBlock(CGNode n, ProgramCounter pc) {
		ExplodedControlFlowGraph cfg = (ExplodedControlFlowGraph) this.getCFG(n);
		BasicBlockInContext<IExplodedBasicBlock> explodedBasicBlock = cfg.getExplodedBasicBlock(n, pc);
		return explodedBasicBlock;
	}
	
	
 	
}

