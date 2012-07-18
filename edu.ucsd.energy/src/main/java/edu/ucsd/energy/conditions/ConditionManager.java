package edu.ucsd.energy.conditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import com.ibm.wala.cfg.cdg.ControlDependenceGraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.shrikeBT.IConditionalBranchInstruction.Operator;
import com.ibm.wala.ssa.ConstantValue;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.util.collections.Iterator2List;
import com.ibm.wala.util.graph.Acyclic;
import com.ibm.wala.util.graph.InferGraphRoots;
import com.ibm.wala.util.graph.Path;

import edu.ucsd.energy.analysis.Options;
import edu.ucsd.energy.intraproc.IntraProcAnalysis.ConditionEdge;
import edu.ucsd.energy.intraproc.VariableManager;
import edu.ucsd.energy.intraproc.VariableManager.ConditionVariable;
import edu.ucsd.energy.intraproc.VariableManager.FunctionCallResult;
import edu.ucsd.energy.util.InvokeInfo;

/**
 * Condition Manager: gathers mapping: edge -> condition
 * @author pvekris
 */
public class ConditionManager {

  private static int DEBUG_LEVEL = 0;
  
  private CompoundCondition hash;
  private static IR currIR;
  private static SSACFG currCFG;

  private static HashMap<InvokeInfo, HashSet<CompoundCondition>> invokeConditionMap;
  private static HashMap<InvokeInfo, HashSet<CompoundCondition>> filteredInvokeConditionMap;

  private static HashMap<ConditionEdge, GeneralCondition> edgeToBranchInfo;

  private static VariableManager variableManager;



  /**
   * The constructor initializes fields and collects branching and variable
   * information
   * 
   * @param node
   * @param vMan
   */
  public ConditionManager(CGNode node, VariableManager vMan) {

    currIR = node.getIR();
    currCFG = currIR.getControlFlowGraph();

    variableManager = vMan;

    invokeConditionMap = new HashMap<InvokeInfo, HashSet<CompoundCondition>>();
    filteredInvokeConditionMap = new HashMap<InvokeInfo, HashSet<CompoundCondition>>();

    this.hash = new CompoundCondition();
    computeBranchingInformation();
    variableManager.collectVariableInformation();
    if (DEBUG_LEVEL > 1) {
      variableManager.outputVariableInformation();
    }

  }

  /**
   * Compute conditional branching information for every edge: if it is an edge
   * coming out of a conditional statement it should have info about whether the
   * branch was taken or not
   */
  private static void computeBranchingInformation() {
    // Clear the map from previous bindings
    edgeToBranchInfo = new HashMap<ConditionEdge, GeneralCondition>();

    Iterator<ISSABasicBlock> itr = currCFG.iterator();

    while (itr.hasNext()) {
      ISSABasicBlock bb = itr.next();

      try {
        SSAInstruction instr = bb.getLastInstruction();
        if (instr instanceof SSAConditionalBranchInstruction) {
          SSAConditionalBranchInstruction condInstr = (SSAConditionalBranchInstruction) instr;
          int var1 = condInstr.getUse(0);
          int var2 = condInstr.getUse(1);

          Iterator<ISSABasicBlock> succNodesItr = currCFG.getSuccNodes(bb);
          Iterator2List<ISSABasicBlock> succNodesArray = 
					new Iterator2List<ISSABasicBlock>(succNodesItr, new ArrayList<ISSABasicBlock>(2));
          switch (succNodesArray.size()) {
          case 0: break;
          case 1: break;
          case 2: {
            /*
             * TODO: Is branching info propagated differently?
             */
            ConditionEdge e0 = new ConditionEdge(bb.getNumber(), succNodesArray.get(0).getNumber());
            Operator op0 = (Operator) condInstr.getOperator();
            ConditionEdge e1 = new ConditionEdge(bb.getNumber(), succNodesArray.get(1).getNumber());
            Operator op1 = ((Operator) condInstr.getOperator()).getInverseOperator();

            SimpleCondition simpleCondition0 = new SimpleCondition(bb, op0, var1, var2);
            SimpleCondition simpleCondition1 = new SimpleCondition(bb, op1, var1, var2);

            variableManager.register(var1);
            variableManager.register(var2);

            edgeToBranchInfo.put(e0, simpleCondition0);
            edgeToBranchInfo.put(e1, simpleCondition1);

          }
          default: break;
          }
        } 
        /*
         * Switch instruction
         */
        else if (instr instanceof SSASwitchInstruction) {
          SSASwitchInstruction switchInstr = (SSASwitchInstruction) instr;
          int var1 = switchInstr.getUse(1);
          variableManager.register(var1);
          int var2;
          Operator op = Operator.EQ;
          ConditionEdge e;
          int[] casesAndLabels = switchInstr.getCasesAndLabels();
          /*
           * Create an edge for each case 
           */
          for (int i = 0; i < casesAndLabels.length - 1; i += 2) {
            ISSABasicBlock targetBB = 
                currIR.getBasicBlockForInstructionIndex(casesAndLabels[i + 1]);
            e = new ConditionEdge(bb.getNumber(), targetBB.getNumber());
            
            var2 = casesAndLabels[i];
                        

            /*
             * The switch expression should be a constant. So look for it and 
             * create it if it
             */
            SymbolTable symbolTable = currIR.getSymbolTable();
            int value = symbolTable.getConstant(i);
          
            ConstantValue cv = new ConstantValue(value);
            
            /*
             * The rest of the variable manager mapping is updated 
             * by VariableManager.collectvariableInformation()
             */
            variableManager.register(var2, cv);
            
            SimpleCondition simpleCondition = new SimpleCondition(bb, op, var1, var2);
            if (DEBUG_LEVEL > 1) {
              System.out.println("[Inserting switch info] Edge: " + e.toString() +
                  " Condition: " + simpleCondition.toString());
            }
            edgeToBranchInfo.put(e, simpleCondition);
          }
          /*
           * TODO: Default case
           */
          /*
          int defaultInstr = switchInstr.getDefault();
          ISSABasicBlock targetBB = currIR.getBasicBlockForInstructionIndex(defaultInstr);
          e = new ConditionEdge(bb.getNumber(), targetBB.getNumber());
          
          
          WPSimple simpleCondition = new WPSimple(bb, op, var1, var2);
          if (DEBUG_LEVEL > 1) {
            System.out.println("[Inserting switch info] Default edge: " + e.toString() +
                " Condition: " + simpleCondition.toString());
          }
          */
        }
        
        /*
         * TODO: exception variables
         */

      } catch (ArrayIndexOutOfBoundsException e) {
        // Just ignore the out of bound for now
      }
    }

    if (DEBUG_LEVEL > 1) {
      System.out.println("Inserted conditions:");
      for (Entry<ConditionEdge, GeneralCondition> entry : edgeToBranchInfo.entrySet()) {
        System.out.println(((ConditionEdge) entry.getKey()).fst + " -> " + 
            ((ConditionEdge) entry.getKey()).snd + " : "
            + entry.getValue().toString());
      }
      System.out.println();
    }

  }

  public GeneralCondition getEdgeCondition(ConditionEdge e) {
    return edgeToBranchInfo.get(e);
  }

  @Override
  public String toString() {
    StringBuffer buffer = new StringBuffer();
    Iterator<SimpleCondition> iterator = hash.iterator();
    buffer.append("[");
    while (iterator.hasNext()) {
      buffer.append(iterator.next().toString());
      buffer.append(", ");
    }
    // remove the extra ", "
    buffer.subSequence(0, buffer.length() - 1);
    buffer.append("]");
    return buffer.toString();
  }

  public void add(SimpleCondition edgeCI) {
    hash.add(edgeCI);
  }

  /**
   * Remove conditions that do not offer valuable information. These might be: -
   * we do not care about conditions that are not predecessors of an invocation
   * at the control dependence graph
   * 
   * Eg. if(wakeLock.isHeld()) {...}
   * 
   * TODO: figure out which these are, and also a more sophisticated way to
   * handle them
   * 
   * @param callSiteConditions
   * @param conditionDependencies
   * @return
   * 
   */
  public HashSet<CompoundCondition> filterConditionMap(
      HashSet<CompoundCondition> callSiteConditions,
      HashSet<Integer> conditionDependencies) {

    if (DEBUG_LEVEL > 0) {
      System.out.println("[filterConditionMap] CallSite Conditions: " + 
          callSiteConditions.toString());
      System.out.println("[filterConditionMap] Target depends on these blocks: " +
          conditionDependencies.toString());
    }

    HashSet<CompoundCondition> setOfCompoundConditions = new HashSet<CompoundCondition>();
    for (CompoundCondition condSet : callSiteConditions) {
      
      CompoundCondition cs = new CompoundCondition();
      
      for (SimpleCondition cond : condSet.getConditionSet()) {
        /*
         * Only keep blocks upon which the invoke method is dependent
         * check condition dependence graph
         */
        if (conditionDependencies.contains(cond.getBlockIndex())) {
          /*
           * Filter out calls to designated functions
           */
          int firstArgument = cond.getFirstArgument();
          ConditionVariable firstVariable =
              variableManager.getInfoOfVariable(firstArgument);

          int secondArgument = cond.getSecondArgument();
          ConditionVariable secondVariable = 
              variableManager.getInfoOfVariable(secondArgument);


          if (firstVariable instanceof FunctionCallResult) {
            String firstSignature = ((FunctionCallResult) firstVariable).
                invInstr.getDeclaredTarget().getSignature();
            if (!Options.filterOutMethods.contains(firstSignature)) {
              cs.add(cond);
            }
          } 
          else if (secondVariable instanceof FunctionCallResult) {
            String secondSignature = ((FunctionCallResult) secondVariable).
                invInstr.getDeclaredTarget().getSignature();
            if (!Options.filterOutMethods.contains(secondSignature)) {
              cs.add(cond);
            }
          }
          else {
            cs.add(cond);
          }
        }
      } //end of for
      
      /*
       * Add the compound condition to the set
       */
      boolean find = false;
      for (CompoundCondition elem : setOfCompoundConditions) {
        find = find || (elem.getConditionSet().containsAll(cs.getConditionSet()));            
      };
      if (!find) {
        setOfCompoundConditions.add(cs);
      }
      
    }
    return setOfCompoundConditions;
  }

  
  
  public void addOriginalConditions(InvokeInfo ii, HashSet<CompoundCondition> cond) {
    getOriginalConditions().put(ii, cond);
  }

  
  public void addFilteredConditions(InvokeInfo ii, HashSet<CompoundCondition> cond) {
    getFilteredConditions().put(ii, cond);
  }

  public void outputInvokeConditionInfo() {
    if (DEBUG_LEVEL > 0) {
      System.out.println("Original Conditions");
      outputIvokeConditionMap(getOriginalConditions());
      System.out.println();
    }
   
    HashMap<InvokeInfo, HashSet<CompoundCondition>> filteredConditions = getFilteredConditions();
    outputVariableInfoForConditions(filteredConditions);
    
    System.out.println();
    System.out.println("Filtered Conditions:");
    outputIvokeConditionMap(filteredConditions);

    System.out.println();
  }

  private void outputVariableInfoForConditions(
      HashMap<InvokeInfo, HashSet<CompoundCondition>> filteredConditions) {
    HashSet<Integer> varSet = new HashSet<Integer>();
    for (Entry<InvokeInfo, HashSet<CompoundCondition>> entry : filteredConditions.entrySet()) {
      for (CompoundCondition cs : entry.getValue()) {
        for (SimpleCondition c : cs.getConditionSet()) {
          varSet.add(c.getFirstArgument());
          varSet.add(c.getSecondArgument());
        }
      }
    }

    if (varSet.size() > 0) {
      System.out.println();
      System.out.println("Variable information:");
    }
    for (int i : varSet) {
      ConditionVariable varInfo = variableManager.getInfoOfVariable(i);
      if (varInfo != null) {
        System.out.println(i + " : " + varInfo.toString());
      } else {
        System.out.println("No info for variable: " + i);
      }
    }
  }

  public void outputIvokeConditionMap(HashMap<InvokeInfo, HashSet<CompoundCondition>> map) {
    for (Entry<InvokeInfo, HashSet<CompoundCondition>> entry : map.entrySet()) {

      System.out.println("----------------------------------------" + "--------------------------------------------");
      System.out.println(entry.getKey().getInvokeInstruction().toString());
      System.out.println("----------------------------------------" + "--------------------------------------------");
      for (CompoundCondition cs : entry.getValue()) {
        System.out.println(cs.toString());
      }
    }
  }

  public HashMap<InvokeInfo, HashSet<CompoundCondition>> getOriginalConditions() {
    return invokeConditionMap;
  }

  public void setOriginalConditions(HashMap<InvokeInfo, HashSet<CompoundCondition>> hash) {
    invokeConditionMap = hash;
  }

  public void updateOriginalConditions(InvokeInfo ii, HashSet<CompoundCondition> hash) {
    invokeConditionMap.put(ii, hash);
  }

  public static HashMap<InvokeInfo, HashSet<CompoundCondition>> getFilteredConditions() {
    return filteredInvokeConditionMap;
  }

  public static void setFilteredLnvokeConditionMap(HashMap<InvokeInfo, HashSet<CompoundCondition>> map) {
    filteredInvokeConditionMap = map;
  }

  public HashSet<Integer> getConditionDependencies(ControlDependenceGraph<SSAInstruction, ISSABasicBlock> currCDG,
      InvokeInfo invokeInfo) {

    Collection<ISSABasicBlock> roots = InferGraphRoots.inferRoots(currCDG);
    HashSet<Integer> interestingConditions = new HashSet<Integer>();

    if (DEBUG_LEVEL > 0) {
      System.out.println("[getConditionDependencies] Target:" + invokeInfo.toString());
    }

    for (ISSABasicBlock root : roots) {

      Collection<Path> currPaths;

      try {
        currPaths = Acyclic.computeAcyclicPaths(currCDG, root, root, invokeInfo.getInvokeBasicBlock(), 100);
      } catch (Exception e) {
        /*
         * TODO : see what's wrong here
         */
        currPaths = new HashSet<Path>();
      }

      /*
       * Now traverse each path and look for conditions
       */
      for (Path path : currPaths) {
        int bbIndex;
        if (DEBUG_LEVEL > 0) {
          System.out.println("[getConditionDependencies] Path: ");
          System.out.println("[getConditionDependencies]" + path.toString());
        }

        for (int i = 0; i < path.size(); i++) {
          bbIndex = path.get(i);
          ISSABasicBlock bb = currCFG.getBasicBlock(bbIndex);
          Iterator<SSAInstruction> itr = bb.iterator();
          while (itr.hasNext()) {
            SSAInstruction instr = itr.next();
            /*
             * we got condition instruction, so keep the number of the block we
             * will check this against the block in the WPSimple entry to see if
             * we need the entry or not
             */
            if (instr instanceof SSAConditionalBranchInstruction || instr instanceof SSASwitchInstruction) {
              interestingConditions.add(bbIndex);
            }
          }
        }
      }
    }

    if (DEBUG_LEVEL > 0) {
      System.out.println("[getConditionDependencies] Interesting conditions: " + interestingConditions);
    }

    return interestingConditions;

  }

}
