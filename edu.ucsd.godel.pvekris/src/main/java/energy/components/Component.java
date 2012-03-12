package energy.components;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.dataflow.graph.BooleanSolver;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.collections.Quartet;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;

import energy.analysis.AnalysisDriver;
import energy.analysis.Opts;
import energy.interproc.ContextInsensitiveLocking;
import energy.interproc.ContextSensitiveLocking;
import energy.interproc.SensibleCallGraph;
import energy.interproc.SensibleExplodedInterproceduralCFG;
import energy.util.E;
import energy.viz.ColorNodeDecorator;
import energy.viz.GraphDotUtil;

public abstract class Component {
  protected IClass klass;
  /**
   * Gather all possible callbacks
   */
  private HashMap<String, CGNode> callbacks;
  protected CallGraph callgraph;

  /**
   * Contains a call graph regarding the callbacks for this component that makes
   * sense logically based on the component's lifecycle. Only implemented for
   * Activity.
   */
  private SensibleCallGraph sensibleAuxCG = null;

  private boolean interesting = false;

  protected HashSet<String> callbackNames;
  protected HashSet<Pair<String, String>> callbackEdges;
  protected HashSet<Pair<String, List<String>>> callbackExpectedState;

  public void registerCallback(String name, CGNode node) {
    E.log(2, "Registering callback: " + name + " to " + this.toString());
    callbacks.put(name, node);
  }

  Component(IClass declaringClass, CGNode root) {
    setKlass(declaringClass);
    callbacks = new HashMap<String, CGNode>();
    registerCallback(root.getMethod().getName().toString(), root);

    callbackNames         = new HashSet<String>();
    callbackEdges         = new HashSet<Pair<String, String>>();
    callbackExpectedState = new HashSet<Pair<String,List<String>>>();
  }

  public String toString() {
    StringBuffer b = new StringBuffer();
    b.append(this.getClass().getName() + ": ");
    b.append(getKlass().getName().toString());
    return b.toString();
  }

  public IClass getKlass() {
    return klass;
  }

  public void setKlass(IClass klass) {
    this.klass = klass;
  }

  public Collection<CGNode> getCallbacks() {
    return callbacks.values();
  }

  public CGNode getCallBackByName(String name) {
    return callbacks.get(name);
  }

  public CallGraph getCallgraph() {
    return callgraph;
  }

  public void setCallgraph(CallGraph callgraph) {
    this.callgraph = callgraph;
  }

  public HashSet<Pair<String, String>> getCallbackEdges() {
    return callbackEdges;
  }

  public HashSet<String> getCallbackNames() {
    return callbackNames;
  }

  public SensibleCallGraph getSensibleCG() {
    return sensibleAuxCG;
  }

  private String getTargetColor(ISSABasicBlock ebb) {
    Iterator<SSAInstruction> iterator = ebb.iterator();
    while (iterator.hasNext()) {
      SSAInstruction instr = iterator.next();
      if (instr instanceof SSAInvokeInstruction) {
        SSAInvokeInstruction inv = (SSAInvokeInstruction) instr;
        if (inv.getDeclaredTarget().getSignature().toString().contains("WakeLock.acquire")) {
          return "red";
        }
        if (inv.getDeclaredTarget().getSignature().toString().contains("WakeLock.release")) {
          return "green";
        }
      }
    }
    return null;
  }

  /**
   * This is a colored node decorator for a cfg (interprocedural or not)
   */
  ColorNodeDecorator colorNodeDecorator = new ColorNodeDecorator() {
    @Override
    public String getLabel(Object o) throws WalaException {
      /* This is the case for the complete Interprocedural CFG */
      if (o instanceof BasicBlockInContext) {
        @SuppressWarnings("unchecked")
        BasicBlockInContext<IExplodedBasicBlock> ebb = (BasicBlockInContext<IExplodedBasicBlock>) o;
        String prefix = "(" + Integer.toString(ebb.getNode().getGraphNodeId()) + "," + Integer.toString(ebb.getNumber()) + ")";
        String name = ebb.getMethod().getName().toString() + " : ";

        Iterator<SSAInstruction> iterator = ebb.iterator();
        while (iterator.hasNext()) {
          SSAInstruction instr = iterator.next();
          name += instr.toString();
        }
        return (prefix + "[" + name + "]");
      }
      /* This is the case of the CFG for a single method. */
      if (o instanceof IExplodedBasicBlock) {
        IExplodedBasicBlock ebb = (IExplodedBasicBlock) o;

        String name = "";
        Iterator<SSAInstruction> iterator = ebb.iterator();
        while (iterator.hasNext()) {
          SSAInstruction instr = iterator.next();
          name += instr.toString();
        }

        String prefix = "(" + Integer.toString(ebb.getNumber()) + ")";

        return (prefix + "[" + name + "]");
      }
      return "[error]";
    }

    @Override
    public String getFillColor(Object o) {
      if (o instanceof BasicBlockInContext) {
        @SuppressWarnings("unchecked")
        BasicBlockInContext<IExplodedBasicBlock> bb = (BasicBlockInContext<IExplodedBasicBlock>) o;
        Pair<IMethod, Integer> pair = Pair.make(bb.getMethod(), bb.getNumber());
        E.log(2, "LOOKING FOR: " + pair.toString());
        String col = colorHash.get(Pair.make(bb.getMethod(), bb.getNumber()));
        if (col == null)
          Assertions.UNREACHABLE();
        else
          return col;
      }
      if (o instanceof IExplodedBasicBlock) {
        IExplodedBasicBlock bb = (IExplodedBasicBlock) o;
        return colorHash.get(Pair.make(bb.getMethod(), bb.getNumber()));
      }
      return "black";
    }

    @Override
    public String getFontColor(Object o) {
      /*
       * if (o instanceof BasicBlockInContext) {
       * BasicBlockInContext<IExplodedBasicBlock> ebb =
       * (BasicBlockInContext<IExplodedBasicBlock>) o; String c =
       * getTargetColor(ebb); if (c == null) return "black"; else return c; } if
       * (o instanceof IExplodedBasicBlock) { IExplodedBasicBlock ebb =
       * (IExplodedBasicBlock) o; String c = getTargetColor(ebb); if (c == null)
       * return "black"; else return c; }
       */
      return "black";
    }

  };

  public void outputNormalCallGraph() {
    outputCallGraph(callgraph, "cg");
  }

  public void outputCallGraph(CallGraph cg, String prefix) {
    try {
      Properties p = WalaExamplesProperties.loadProperties();
      p.putAll(WalaProperties.loadProperties());
      String className = klass.getName().toString();
      String bareFileName = className.replace('/', '.');
      String folder = energy.util.Util.getResultDirectory() + File.separatorChar + prefix;
      new File(folder).mkdirs();
      String fileName = folder + File.separatorChar + bareFileName + ".dot";
      String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
      String pdfFile = null;
      E.log(2, "Dumping: " + fileName);
      GraphDotUtil.dotify(cg, null, fileName, pdfFile, dotExe);
      return;
    } catch (WalaException e) {
      e.printStackTrace();
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    }
  }

  /*******************************************************************************
   * 
   * Solvers for the flow-sensitive context-insensitive and context-sensitive
   * 
   *******************************************************************************/

  protected SensibleExplodedInterproceduralCFG icfg = null;

  protected BooleanSolver<BasicBlockInContext<IExplodedBasicBlock>> acquireMaySolver = null;
  protected BooleanSolver<BasicBlockInContext<IExplodedBasicBlock>> releaseMaySolver = null;
  protected BooleanSolver<BasicBlockInContext<IExplodedBasicBlock>> releaseMustSolver = null;

  protected TabulationResult<BasicBlockInContext<IExplodedBasicBlock>,
    CGNode, Quartet<Boolean, Boolean, Boolean, Boolean>> csSolver;
  private TabulationDomain<Quartet<Boolean, Boolean, Boolean, Boolean>, 
    BasicBlockInContext<IExplodedBasicBlock>> csDomain;

  public boolean isInteresting() {
    return interesting;
  }

  public void setInteresting(boolean interesting) {
    this.interesting = interesting;
  }

  /**
   * Create a sensible exploded interprocedural CFG analysis on it.
   * 
   * @param component
   * @return
   */
  public void createSensibleCG() {
    E.log(2, "Creating sensible callgraph for " + this.toString() );
    /*
     * First create the auxiliary call graph (SensibleAuxCallGraph) This is just
     * the graph that represents the logical relation of callbacks.
     */
    SensibleCallGraph sensibleCG = new SensibleCallGraph(this);
    /* Test it */
    sensibleCG.outputToDot();
    /* Pack edges */
    HashSet<Pair<CGNode, CGNode>> packedEdges = sensibleCG.packEdges();

    icfg = new SensibleExplodedInterproceduralCFG(getCallgraph(), packedEdges);

  }

  /**
   * Solve the _context-insensitive_ problem on the exploded inter-procedural
   * CFG based on the component's sensible callgraph
   */
  protected void solveCICFG() {
    ContextInsensitiveLocking lockingProblem = new ContextInsensitiveLocking(icfg);
    acquireMaySolver = lockingProblem.analyze(true, true);
    releaseMaySolver = lockingProblem.analyze(true, false);
    releaseMustSolver = lockingProblem.analyze(false, false);
  }

  /**
   * Solve the _context-sensitive_ problem on the exploded inter-procedural CFG
   * based on the component's sensible callgraph
   */
  protected void solveCSCFG() {
    ContextSensitiveLocking lockingProblem = new ContextSensitiveLocking(icfg);
    csSolver = lockingProblem.analyze();
    
    csDomain = lockingProblem.getDomain();
  }

  /**
   * Output the colored CFG for each node in the callgraph (Basically done
   * because dot can't render the complete interproc CFG.)
   */
  public void outputColoredCFGs() {
    /*
     * Need to do this here - WALA was giving me a hard time to crop a smart
     * part of the graph
     */
    Properties p = WalaExamplesProperties.loadProperties();
    try {
      p.putAll(WalaProperties.loadProperties());
    } catch (WalaException e) {
      e.printStackTrace();
    }
    String cfgs = energy.util.Util.getResultDirectory() + File.separatorChar + "color_cfg";
    new File(cfgs).mkdirs();
    Iterator<CGNode> it = callgraph.iterator();
    while (it.hasNext()) {
      CGNode n = it.next();
      /* only color the callbacks */
      // if (callbacks.containsKey(n.getMethod().getName().toString())) {
      ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg =
      // ExceptionPrunedCFG.make(icfg.getCFG(n));
      icfg.getCFG(n);

      String bareFileName = n.getMethod().getDeclaringClass().getName().toString().replace('/', '.') + "_"
          + n.getMethod().getName().toString();

      String cfgFileName = cfgs + File.separatorChar + bareFileName + ".dot";

      String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
      String pdfFile = null;
      try {
        /* Do the colored graph - this will get the colors from the color hash */
        GraphDotUtil.dotify(cfg, colorNodeDecorator, cfgFileName, pdfFile, dotExe);
      } catch (WalaException e) {
        e.printStackTrace();
      }
      // }
    }
  }

  /* Super-ugly way to keep the colors for every method in the graph */
  private HashMap<Pair<IMethod, Integer>, String> colorHash;

  public void cacheColors() {
    for (Iterator<Quartet<Boolean, Boolean, Boolean, Boolean>> it = 
        csDomain.iterator(); it.hasNext(); ) {
      Quartet<Boolean, Boolean, Boolean, Boolean> q = it.next();
      int i = csDomain.getMappedIndex(q);
      E.log(2, i + ": " + q.toString());
    }
    
    colorHash = new HashMap<Pair<IMethod, Integer>, String>();
    Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterator = icfg.iterator();
    while (iterator.hasNext()) {
      BasicBlockInContext<IExplodedBasicBlock> bb = iterator.next();
      String col = null;
      col = getTargetColor(bb);
      if (Opts.DO_CS_ANALYSIS) {
        /* Context sensitive analysis */
        IntSet result = csSolver.getResult(bb);        
        if (col == null) {
          if (result.size() == 0) {
            /* This node was just initialized */
            col = "lightyellow";
          } 
          else {
            Quartet<Boolean, Boolean, Boolean, Boolean> q = 
                mergeResult(result);        
            /* Single color here */
            boolean maybeAcquired = q.fst;
            boolean mustbeAcquired = q.snd;
            boolean maybeReleased = q.thr;
            boolean mustbeReleased = q.frt;
            E.log(2, "RESULT: " + bb.toString()+ " " + q);
            if (mustbeReleased) {
              col = "green";
            } else if (maybeReleased) {
              col = "lightgreen";
            } else if (mustbeAcquired) {
              col = "red";
            } else if (maybeAcquired) {
              col = "lightpink";
            } else {
              col = "lightgrey";
            }
          }
          /*
          else {          
            StringBuffer pred = new StringBuffer();
            for (Iterator<BasicBlockInContext<IExplodedBasicBlock>> itr = icfg.getPredNodes(bb);
                itr.hasNext(); ) {
              BasicBlockInContext<IExplodedBasicBlock> p = itr.next();
              pred.append("(" + p.getMethod().getName().toString() + ", " + p.getNumber() + 
                  ", "  + csSolver.getResult(p) + ") ");
            }            
            ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = icfg.getCFG(bb);
            IExplodedBasicBlock d = bb.getDelegate();
            StringBuffer normPred = new StringBuffer();
            try {
              for (IExplodedBasicBlock i : cfg.getNormalPredecessors(d)) {
                normPred.append("(" + i.getMethod().getName().toString() + ", " + i.getNumber() + ") ");
              }
            } catch (Exception e) {
              normPred.append("ERROR");
            }
            E.log(1, "MULTI-RESULTS" +
                bb.toString() + result
                + " Pred: " + pred.toString()
                + "NormPred: " + normPred.toString());
            if (mayContainAcquired(result)) {
              col = "orange";
            } else {
              col = "lightblue";
            }
          }
          */
        }
      }
      else {
        /* Context insensitive analysis */
        boolean maybeAcquired = acquireMaySolver.getOut(bb).getValue();
        boolean maybeReleased = releaseMaySolver.getOut(bb).getValue();
        boolean mustbeReleased = releaseMustSolver.getOut(bb).getValue();
        if (col == null) {
          if (maybeAcquired) {
            col = "lightpink";
          } else {
            col = "lightgrey";
          }
        }
      }
      Pair<IMethod, Integer> pair = Pair.make(bb.getMethod(), bb.getNumber());
      colorHash.put(pair, col);
    }
  }

  
  
  
  
  private Quartet<Boolean, Boolean, Boolean, Boolean> mergeResult(IntSet x) {
    IntIterator it = x.intIterator();
    Quartet<Boolean, Boolean, Boolean, Boolean> n = 
        Quartet.make(Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE); 
    while (it.hasNext()) {
      int i = it.next();
      Quartet<Boolean, Boolean, Boolean, Boolean> q = csDomain.getMappedObject(i);
      n = Quartet.make(
          new Boolean(n.fst.booleanValue() || q.fst.booleanValue()), // may
          new Boolean(n.snd.booleanValue() && q.snd.booleanValue()), // must
          new Boolean(n.thr.booleanValue() || q.thr.booleanValue()), // may
          new Boolean(n.frt.booleanValue() && q.frt.booleanValue()) // must
      );
    }
    return n;
  }


  @SuppressWarnings("unused")
  private boolean mayContainAcquired(Collection<Integer> set) {
    for (Integer i: set) {
      Quartet<Boolean, Boolean, Boolean, Boolean> mappedObject = 
          csDomain.getMappedObject(i.intValue());
      if (mappedObject.fst.booleanValue()) {
        return true;
      }
    }
    return false;
  }

  protected void outputSolvedICFG() {
    E.log(2, "#nodes: " + callgraph.getNumberOfNodes());
    Properties p = WalaExamplesProperties.loadProperties();
    try {
      p.putAll(WalaProperties.loadProperties());
    } catch (WalaException e) {
      e.printStackTrace();
    }
    String path = klass.getName().toString().replace('/', '.');
    String fileName = new File(path).getName();
    String DOT_FILE = "ExpInterCFG_" + fileName + ".dot";
    String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
    String pdfFile = null;
    String dotFile = energy.util.Util.getResultDirectory() + File.separatorChar + DOT_FILE;

    try {
      /* Do the colored graph */
      GraphDotUtil.dotify(icfg, colorNodeDecorator, dotFile, pdfFile, dotExe);
    } catch (WalaException e) {
      e.printStackTrace();
    }
  }

  
  
  public boolean  checkedPolicy  = false;
  public String   policyResult    = null;
  
  /**
   * Apply the policies to the corresponding callbacks. This one checks that at
   * the end of the method, we have an expected state.
   */
  protected Map<String, String> checkLockingPolicy() {
	Map<String, String> result = new HashMap<String, String>();
	
    for (Pair<String, List<String>> elem : callbackExpectedState) {
      String methName = elem.fst;      
      CGNode cgNode = callbacks.get(methName);
      
      if (cgNode != null) {
        BasicBlockInContext<IExplodedBasicBlock> exit = icfg.getExit(cgNode);
        Pair<IMethod, Integer> p = Pair.make(
            cgNode.getMethod(), exit.getNumber());
        String color = colorHash.get(p);
        
        result.put(methName, color);
        
        List<String> expStatus = elem.snd;
        String status = "BAD";
        /* Is this an expected exit state ? */
        if (expStatus.contains(color)) {
          status = "OK";
        }            
        else {
          AnalysisDriver.retCode = 1;          
        }
        //Register the result
        checkedPolicy = true;
        policyResult = status;
        
        E.slog(0, "locking.txt", this.toString() + " | " + methName + " : " + 
            color + "\t[" + status + "]");        
      } else {
        E.log(1, "Callback " + methName + " was not found.");
        for (Entry<String, CGNode> e : callbacks.entrySet()) {
          E.log(2, " - " + e.getValue().getMethod().getSignature().toString());
        }
      }
    }
    return result;
  }

  public void lookForExceptionalEdges() {
    Iterator<BasicBlockInContext<IExplodedBasicBlock>> it = icfg.iterator();
    while (it.hasNext()) {
      BasicBlockInContext<IExplodedBasicBlock> bb = it.next();
      ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = icfg.getCFG(bb);
      IExplodedBasicBlock d = bb.getDelegate();
      Collection<IExplodedBasicBlock> normalSuccessors = cfg.getNormalSuccessors(d);
      int nsn = normalSuccessors.size();
      int snc = cfg.getSuccNodeCount(d);
      if (nsn != snc) {

        Iterator<SSAInstruction> si = d.iterator();
        while (si.hasNext()) {
          E.log(1, cfg.getMethod().getName().toString() + " Exceptional edges: (" + nsn + ", " + snc + ") [" + d.getNumber() + "]"
              + si.next().toString());
        }
        Iterator<IExplodedBasicBlock> succNodesIter = cfg.getSuccNodes(d);
        for (IExplodedBasicBlock ns : normalSuccessors) {
          E.log(1, " -N-> [" + ns.getNumber() + "]");
        }
        while (succNodesIter.hasNext()) {
          IExplodedBasicBlock succNode = succNodesIter.next();

          if (!normalSuccessors.contains(succNode)) {
            E.log(1, " -E-> [" + succNode.getNumber() + "]");
          }
        }
      }
    }
  }

}
