package energy.interproc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExceptionPrunedCFG;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.collections.Pair;

import energy.util.E;
import energy.util.Util;

public class SensibleExplodedInterproceduralCFG extends ExplodedInterproceduralCFG {

  
  /**
   * Constructor that takes as arguments the initial call graph and the
   * pairs of methods (Signatures) that need to be connected.
   * @param cg
   * @param packedEdges
   */
  public SensibleExplodedInterproceduralCFG(CallGraph cg, HashSet<Pair<CGNode, CGNode>> packedEdges) {
    super(cg);
    /* Will only work like this - loses laziness. */
    constructFullGraph();    
    addInterCallbackEdges(cg,packedEdges);
    cacheCallbacks(cg,packedEdges);
  } 
  
  private HashMap<String,CGNode> callbacks;
  
  
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
  
 private void addInterCallbackEdges(CallGraph cg, HashSet<Pair<CGNode, CGNode>> packedEdges) {
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

public HashMap<String,CGNode> getCallbacks() {
  return callbacks;
}

}

