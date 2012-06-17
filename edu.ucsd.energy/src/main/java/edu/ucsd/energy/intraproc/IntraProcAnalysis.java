package edu.ucsd.energy.intraproc;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.ibm.wala.cfg.cdg.ControlDependenceGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.ExceptionPrunedCFG;
import com.ibm.wala.ipa.cfg.PrunedCFG;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.graph.Acyclic;
import com.ibm.wala.util.graph.InferGraphRoots;
import com.ibm.wala.util.graph.Path;
import com.ibm.wala.util.graph.dominators.Dominators;
import com.ibm.wala.util.graph.impl.InvertedGraph;

import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.conditions.CompoundCondition;
import edu.ucsd.energy.conditions.ConditionManager;
import edu.ucsd.energy.conditions.GeneralCondition;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.InvokeInfo;
 
public class IntraProcAnalysis {
  
  private static int DEBUG_LEVEL = 0;

  private static SSACFG                 currCFG;
  private static PrunedCFG<SSAInstruction, ISSABasicBlock> 
                                        exceptionPrunnedCFG;
  private static IR                     currIR;
  private static ControlDependenceGraph<SSAInstruction, ISSABasicBlock> currCDG;
  private static HashSet<InvokeInfo>    invokeSiteSet;

  
  /**
   * A CFG edge used for condition calculation
   */
  public static class ConditionEdge {
    public Integer fst;
    public Integer snd;

    
    public ConditionEdge(int i, int j) {
      this.fst = i;
      this.snd = j;      
    }

    // TODO : improve these functions
    public int hashCode() {
      return new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
          append(fst.toString()).append("-").append(snd.toString()).toHashCode();
    }

    public boolean equals(Object o) {
      if (o instanceof ConditionEdge) {
        ConditionEdge e = (ConditionEdge) o;
        return (this.fst.equals(e.fst) && this.snd.equals(e.snd));
      } else {
        return false;
      }
    }
    
    public String toString() {
      StringBuffer result = new StringBuffer();
      result.append(fst + " -> " + snd);
      return result.toString();
    }

  }

  
  /**
   * Basic info about whether the conditional in the branch was true or false
   * TODO: switch-case -> integer
   */
  public enum BranchingInfo {
    TRUE_BRANCH, FALSE_BRANCH, IRRELEVANT;

    @Override
    public String toString() {
      return super.toString().toLowerCase();
    }
  }

  private static VariableManager  variableManager;
  private static ConditionManager conditionManager;
  

  /**
   * Constructor method for the intra-procedural analysis class. This method
   * initializes structures and runs the analysis on a graph node.
   * 
   * @param hashSet
   * @param n
   * @return 
   * @throws WalaException 
   */
  public void run(AppCallGraph cg, CGNode node) throws WalaException {
    
    System.out.println("===============================================" +
    		               "===============================================");
    System.out.println("IN METHOD: " + node.getMethod().getSignature());
    System.out.println();
    
    invokeSiteSet       = getInvokeSet(cg, node);       
    currIR              = node.getIR();    
    currCFG             = currIR.getControlFlowGraph();    
    
     
    /* Prune the CFG of all the exception edges that lead to the exit of this method,
       TODO: need to treat case of keeping the exceptional edges */ 
    if (Opts.PRUNE_EXCEPTION_EDGES_IN_GFG) {
      exceptionPrunnedCFG = ExceptionPrunedCFG.make(currCFG);
      /*
       * In case the CFG has no nodes left because the only control dependencies
       *  were exceptional, simply return because at this point there are no nodes.
       *  Otherwise, later this may raise an Exception.
       */
      if (exceptionPrunnedCFG.getNumberOfNodes() == 0) {
        System.out.println("Skipping analysis of this method because " +
            "the exception prunned CFG has no nodes.");      
        System.out.println();
        return;
      }
      currCDG = new ControlDependenceGraph<SSAInstruction, ISSABasicBlock>(exceptionPrunnedCFG, true);  
    }
    else {
      currCDG = new ControlDependenceGraph<SSAInstruction, ISSABasicBlock>(currCFG, true);
    }       
    
    /*
     * Initialize the variable manager which will hold information about the variables 
     * taking place in conditions 
     */
    variableManager   = new VariableManager(node);
    
    /*
     * Compute information for every edge regarding the outcome of a condition query
     * if it is an edge coming out of a conditional statement it should have info about
     * whether the branch was taken or not.
     * Also updates the variable manager, so keep this order
     */
    conditionManager  = new ConditionManager(node, variableManager);    
    
    /*
     * Check whether acquire calls are post-dominated by release calls. 
     */    
    checkCallDomination(invokeSiteSet);    
    
    /*
     * For every invoke instruction, it gathers the conditions that have to hold 
     * in order to reach it.
     */
    gatherInvokeConditions();    
    conditionManager.outputInvokeConditionInfo();   

  }
  

  /**
   * Collect invocations to methods in the pruned called graph
   * @param cg
   * @param node
   * @return
   */
  private static HashSet<InvokeInfo> getInvokeSet(AppCallGraph cg, CGNode node) {
    IR currIR = node.getIR();
    // Collect potential call sites here
    HashSet<InvokeInfo> keepInvInfo = new HashSet<InvokeInfo>();
    Iterator<SSAInstruction> instrItr = currIR.iterateAllInstructions();
    while (instrItr.hasNext()) {
      SSAInstruction nextInstr = instrItr.next();
      if (nextInstr instanceof SSAInvokeInstruction) {
        SSAInvokeInstruction invInstr = (SSAInvokeInstruction) nextInstr;
        CallSiteReference cs = invInstr.getCallSite();
        MethodReference declTarg = cs.getDeclaredTarget();
        Set<CGNode> probeNodes = cg.getNodes(declTarg);
        if (!probeNodes.isEmpty()) {
          ISSABasicBlock bb = currIR.getBasicBlockForInstruction(invInstr);
          InvokeInfo invInfo = new InvokeInfo(invInstr, bb);
          keepInvInfo.add(invInfo);
        }
      }
    }    
    return keepInvInfo;
  }


  private static void gatherInvokeConditions() {
    Iterator<InvokeInfo> itr = invokeSiteSet.iterator();
    while (itr.hasNext()) {
      InvokeInfo invokeInfo = itr.next();
      E.log(1, "Target:" + invokeInfo.toString());

      /* Compute the original set of conditions that hold for this call site */
      Collection<Path> pathsToTarget = 
        checkAllPathsToTarget(invokeInfo);
      HashSet<CompoundCondition> originalConditions = 
        pathToConditionCollection(pathsToTarget);
      conditionManager.addOriginalConditions(invokeInfo, originalConditions);
            
      E.log(2, "Loaded original conditions");
            
      /* This is the set of blocks on which this invocation depends on */
      HashSet<Integer> conditionDependencies = 
          conditionManager.getConditionDependencies(currCDG, invokeInfo);              
      E.log(2, "Got condition dependencies");            
            
      /* Filter the conditions based on dependency information */
      HashSet<CompoundCondition> filteredConditions = 
          conditionManager.filterConditionMap(originalConditions, conditionDependencies);
      conditionManager.addFilteredConditions(invokeInfo, filteredConditions);            
      E.log(2, "Done filtering conditions");      
    }    
  }

  private static void checkCallDomination(HashSet<InvokeInfo> invokeSiteSet) {
    
    HashSet<InvokeInfo> wifiLockAcqInvSet = new HashSet<InvokeInfo>();
    HashSet<InvokeInfo> wifiLockRelInvSet = new HashSet<InvokeInfo>();
    HashSet<InvokeInfo> wakeLockAcqInvSet = new HashSet<InvokeInfo>();
    HashSet<InvokeInfo> wakeLockRelInvSet = new HashSet<InvokeInfo>();
    
    /*
     * Fill in information
     */
    Iterator<InvokeInfo> iterator = invokeSiteSet.iterator();
    while (iterator.hasNext()) {
      InvokeInfo invInfo = iterator.next();
      
      if (invInfo.isWakeLockInstruction()) {
        if (invInfo.isAcquireInstruction()) {
          wakeLockAcqInvSet.add(invInfo);
        }
        if (invInfo.isReleaseInstruction()) {
          wakeLockRelInvSet.add(invInfo);
        }       
      }
      
      if (invInfo.isWifiLockInstruction()) {
        if (invInfo.isAcquireInstruction()) {
          wifiLockAcqInvSet.add(invInfo);
        }
        if (invInfo.isReleaseInstruction()) {
          wifiLockRelInvSet.add(invInfo);
        }
      }
    }
    
    /*
     * WifiLock check
     */
    
    for (InvokeInfo wakeLockAcqInv: wakeLockAcqInvSet) {
      System.out.println("Checking release for invocation: " + 
          wakeLockAcqInv.toString());
      boolean foundRelease = false;
      
      for (InvokeInfo wakeLockRelInv: wakeLockRelInvSet) {
    
        boolean checkPostDomination = checkPostDomination(
            wakeLockAcqInv.getInvokeBasicBlock(),
            wakeLockRelInv.getInvokeBasicBlock());
        foundRelease = foundRelease || checkPostDomination; 

        if (checkPostDomination) {
          System.out.println("  -> released by: " + 
              wakeLockRelInv.toString());          
        }        
      }
      
      if (!foundRelease) {
        System.out.println("  -> This wakelock is not released within this function for all paths.");        
      }
      System.out.println();      
    }
    
    /*
     * WakeLock check
     */
    for (InvokeInfo wifiLockAcqInv: wifiLockAcqInvSet) {
      System.out.println("Checking release for invocation: " + 
          wifiLockAcqInv.toString());
      boolean foundRelease = false;
      
      for (InvokeInfo wifiLockRelInv: wifiLockRelInvSet) {
    
        boolean checkPostDomination = checkPostDomination(
            wifiLockAcqInv.getInvokeBasicBlock(),
            wifiLockRelInv.getInvokeBasicBlock());
        foundRelease = foundRelease || checkPostDomination; 

        if (checkPostDomination) {
          System.out.println("  -> released by: " + 
              wifiLockRelInv.toString());          
        }        
      }
      
      if (!foundRelease) {
        System.out.println("  -> This wifilock is not released within this function for all paths.");        
      }
      System.out.println();

    }
    
  }

  /**
   * Check weather a block (d) post-dominates another block (s), 
   * i.e. every path starting at the source block, passes through drain.
   * @param s(ource)
   * @param d(rain)
   * @return 
   */
  private static boolean checkPostDomination(ISSABasicBlock s, ISSABasicBlock d) {
    
    InvertedGraph<ISSABasicBlock> invCFG = new InvertedGraph<ISSABasicBlock>(exceptionPrunnedCFG);
    Collection<ISSABasicBlock> roots = InferGraphRoots.inferRoots(invCFG);
    boolean result = true;
    for (ISSABasicBlock root: roots) {
      Dominators<ISSABasicBlock> dom = Dominators.make(invCFG, root);
      
      boolean dominatedBy = dom.isDominatedBy(s, d);
      result = dominatedBy && result;
    }
    return result;
  }
  
  
  
  

  /**
   * Return all paths that start from the methods entry point and stop at the invoke instruction
   * we pass as a parameter. 
   * @param invokeInfo
   * @return
   */
  private static Collection<Path> checkAllPathsToTarget(InvokeInfo invokeInfo) {
    Collection<ISSABasicBlock> roots = InferGraphRoots.inferRoots(currCFG);
    Collection<Path> paths = HashSetFactory.make();
    for (ISSABasicBlock root : roots) {
      Collection<Path> currPaths = Acyclic.computeAcyclicPaths(currCFG, root, root, invokeInfo.getInvokeBasicBlock() , 100);
      paths.addAll(currPaths);
      if ((DEBUG_LEVEL > 0) && currPaths.size() > 0) {            
        System.out.println("[checkAllPathsToTarget] " + 
            paths.size() + " paths from " + root.getNumber() + 
            " to " +  invokeInfo.getInvokeBasicBlock().getNumber());
        for(Path path : paths) {
          System.out.println("[checkAllPathsToTarget] " + path.toString());
        }
      }
      paths.addAll(currPaths);
    }
    return paths;
  }

  /**
   * Create a set of condition sets from a collection of paths  
   * @param paths
   * @return
   */
  private static HashSet<CompoundCondition> pathToConditionCollection(Collection<Path> paths) {
    HashSet<CompoundCondition> result = HashSetFactory.make();

    for (Path path : paths) {
      CompoundCondition condSet = new CompoundCondition();
      int size = path.size();
      for(int i = 0; i < size - 1 ; i++) {        
        ConditionEdge edge = new ConditionEdge(path.get(i), path.get(i+1));
        GeneralCondition edgeCondition = conditionManager.getEdgeCondition(edge);
        if (edgeCondition != null) {
          condSet.add(edgeCondition);
        }
      }
      result.add(condSet);
    }
    return result;    
    
  }
  
  
  
}
  
  
