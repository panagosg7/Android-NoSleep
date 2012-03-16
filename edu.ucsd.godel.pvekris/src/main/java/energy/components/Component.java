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
import java.util.Set;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.dataflow.graph.BooleanSolver;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.IndiscriminateFilter;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.collections.Quartet;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.GraphReachability;
import com.ibm.wala.util.graph.impl.NodeWithNumber;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.viz.NodeDecorator;

import energy.analysis.AnalysisDriver;
import energy.analysis.ApplicationCallGraph;
import energy.analysis.Opts;
import energy.interproc.ContextInsensitiveLocking;
import energy.interproc.ContextSensitiveLocking;
import energy.interproc.LockState;
import energy.interproc.SensibleCallGraph;
import energy.interproc.SensibleExplodedInterproceduralCFG;
import energy.util.E;
import energy.util.Util;
import energy.viz.ColorNodeDecorator;
import energy.viz.GraphDotUtil;

public abstract class Component extends NodeWithNumber {
  protected IClass klass;
  /**
   * Gather all possible callbacks
   */
  private HashMap<String, CGNode> callbacks;
  protected CallGraph 				componentCallgraph;
  protected ApplicationCallGraph	originalCallgraph;

  /**
   * Contains a call graph regarding the callbacks for this component that makes
   * sense logically based on the component's lifecycle. Only implemented for
   * Activity.
   */
  private SensibleCallGraph sensibleAuxCG = null;

  
  /** Does it call any locking functions ? */
  private boolean interesting = false;

  protected HashSet<String> callbackNames;
  protected HashSet<Pair<String, String>> callbackEdges;
  protected HashSet<Pair<String, List<String>>> callbackExpectedState;

  public void registerCallback(String name, CGNode node) {
    E.log(2, "Registering callback: " + name + " to " + this.toString());
    callbacks.put(name, node);
  }

  
  Component(ApplicationCallGraph cg, IClass declaringClass, CGNode root) {
    setKlass(declaringClass);
    originalCallgraph = cg;
    callbacks = new HashMap<String, CGNode>();
    registerCallback(root.getMethod().getName().toString(), root);

    callbackNames         = new HashSet<String>();
    callbackEdges         = new HashSet<Pair<String, String>>();
    callbackExpectedState = new HashSet<Pair<String,List<String>>>();
  }

  void createComponentCG() {
    HashSet<CGNode> rootSet = new HashSet<CGNode>();
    
    E.log(2, "Creating callgraph: " + klass.getName().toString());
    
    HashSet<CGNode> set = new HashSet<CGNode>();
    for (CGNode node : getCallbacks()) {
      set.addAll(getDescendants(originalCallgraph, node));
      rootSet.add(node);
    }
    
    componentCallgraph = PartialCallGraph.make(originalCallgraph, rootSet, set);
    
    E.log(2, "Partial CG #nodes: " + componentCallgraph.getNumberOfNodes());
    
  }

  /**
   * Get all the callgraph nodes that are reachable from @param node in the @param
   * cg
   * 
   * @return the set of these nodes
   */
  private Set<CGNode> getDescendants(CallGraph cg, CGNode node) {
    Filter<CGNode> filter = IndiscriminateFilter.<CGNode> singleton();
    GraphReachability<CGNode> graphReachability = new GraphReachability<CGNode>(cg, filter);
    try {
      graphReachability.solve(null);
    } catch (CancelException e) {
      e.printStackTrace();
    }
    OrdinalSet<CGNode> reachableSet = graphReachability.getReachableSet(node);
    return Util.iteratorToSet(reachableSet.iterator());
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
	if (componentCallgraph == null) {
	  this.createComponentCG();
	}
    return componentCallgraph;
  }

  public void setCallgraph(CallGraph callgraph) {
    this.componentCallgraph = callgraph;
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
        ISSABasicBlock ebb = (ISSABasicBlock) o;
    	StringBuffer sb = new  StringBuffer();
    	for (Iterator<SSAInstruction> it = ebb.iterator(); it.hasNext(); ) {
			if (!sb.toString().equals("")) {
				sb.append("\\n");
			}
			sb.append(it.next().toString());
		}
        String prefix = "(" + Integer.toString(ebb.getNumber()) + ")";

        return (prefix + "[" + sb.toString() + "]");
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
        LockState st = stateHash.get(Pair.make(bb.getMethod(), bb.getNumber()));
        if (st == null)
          Assertions.UNREACHABLE();
        else
          return st.getColor();
      }
      if (o instanceof IExplodedBasicBlock) {
        IExplodedBasicBlock bb = (IExplodedBasicBlock) o;
        LockState st = stateHash.get(Pair.make(bb.getMethod(), bb.getNumber()));
        return st.getColor();
        		
      }
      return "black";
    }

    @Override
    public String getFontColor(Object o) {
      /*
       if (o instanceof BasicBlockInContext) {
       BasicBlockInContext<IExplodedBasicBlock> ebb =
       (BasicBlockInContext<IExplodedBasicBlock>) o; String c =
       getTargetColor(ebb); if (c == null) return "black"; else return c; } if
       (o instanceof IExplodedBasicBlock) { IExplodedBasicBlock ebb =
       (IExplodedBasicBlock) o; String c = getTargetColor(ebb); if (c == null)
       return "black"; else return c; }
       */
      return "black";
    }

  };

  public void outputNormalCallGraph() {
	if (componentCallgraph == null) {
	  /* Create and dump the component's callgraph */
	  createComponentCG();		
	}
    outputCallGraph(componentCallgraph, "cg");
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
  
  public SensibleExplodedInterproceduralCFG getICFG() {
	  if (icfg == null) {
		icfg = createSensibleCG();
	  }
	  return icfg;
  }
  
  protected BooleanSolver<BasicBlockInContext<IExplodedBasicBlock>> acquireMaySolver = null;
  protected BooleanSolver<BasicBlockInContext<IExplodedBasicBlock>> releaseMaySolver = null;
  protected BooleanSolver<BasicBlockInContext<IExplodedBasicBlock>> releaseMustSolver = null;

  protected TabulationResult<BasicBlockInContext<IExplodedBasicBlock>,
    CGNode, LockState> csSolver;
  private TabulationDomain<LockState, 
    BasicBlockInContext<IExplodedBasicBlock>> csDomain;

  public boolean isInteresting() {
    return interesting;
  }

  public void setInteresting(boolean interesting) {
    this.interesting = interesting;
  }

  /**
   * Create a sensible exploded inter-procedural CFG analysis on it.
   * 
   * @param component
 * @return 
   * @return
   */
  public SensibleExplodedInterproceduralCFG createSensibleCG() {
    E.log(2, "Creating sensible callgraph for " + this.toString());
    
    /* First create the auxiliary call graph (SensibleAuxCallGraph) This is just
     * the graph that represents the logical relation of callbacks. */
    SensibleCallGraph sensibleCG = new SensibleCallGraph(this);
    
    /* Test it */
    //sensibleCG.outputToDot();
    
    /* Pack inter-procedural sensible edges */
    HashSet<Pair<CGNode, CGNode>> packedEdges = sensibleCG.packEdges();
    
    return new SensibleExplodedInterproceduralCFG(getCallgraph(), packedEdges);
    
  }

  /**
   * Solve the _context-insensitive_ problem on the exploded inter-procedural
   * CFG based on the component's sensible callgraph
   */
  protected void solveCICFG() {
	icfg = getICFG();
	
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
	if (icfg == null) {
	  icfg = createSensibleCG();
	  icfg.printBBToThreadMap();
	}
    ContextSensitiveLocking lockingProblem = new ContextSensitiveLocking(icfg);
    csSolver = lockingProblem.analyze();    
    csDomain = lockingProblem.getDomain();
    isSolved = true;
  }

  
  /** Set this true if we analyze it as part of a larger graph */
  public boolean	isSolved		= false;
  
  
  
  /**
   * Output the colored CFG for each node in the callgraph (Basically done
   * because dot can't render the complete interproc CFG.)
   */
  public void outputColoredCFGs() {
    /*
     * Need to do this here - WALA was giving me a hard time to crop a small
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
    Iterator<CGNode> it = componentCallgraph.iterator();
    while (it.hasNext()) {
      CGNode n = it.next();      
      ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = icfg.getCFG(n);
      // ExceptionPrunedCFG.make(icfg.getCFG(n));
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
    }
  }

  
  /**
   * Output the CFG for each node in the callgraph 
   */
  public void outputCFGs() {
    Properties p = WalaExamplesProperties.loadProperties();
    try {
      p.putAll(WalaProperties.loadProperties());
    } catch (WalaException e) {
      e.printStackTrace();
    }
    String cfgs = energy.util.Util.getResultDirectory() + File.separatorChar + "cfg";
    new File(cfgs).mkdirs();
    Iterator<CGNode> it = componentCallgraph.iterator();
    while (it.hasNext()) {
      final CGNode n = it.next();      
      SSACFG cfg = n.getIR().getControlFlowGraph();      
      String bareFileName = n.getMethod().getDeclaringClass().getName().toString().replace('/', '.') + "_"
          + n.getMethod().getName().toString();
      String cfgFileName = cfgs + File.separatorChar + bareFileName + ".dot";
      String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
      String pdfFile = null;
      try {      
    	NodeDecorator nd = new NodeDecorator() {			
			@Override
			public String getLabel(Object o) throws WalaException {
				StringBuffer sb = new  StringBuffer();
				if (o instanceof ISSABasicBlock) {
					
					ISSABasicBlock bb = (ISSABasicBlock) o;
					
					SensibleExplodedInterproceduralCFG icfgLoc = getICFG();
					ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = icfgLoc.getCFG(n);
					
					IExplodedBasicBlock ebb = cfg.getNode(bb.getNumber());
					sb.append(ebb.toString() + "\\n");
					sb.append(bb.toString() + "\\n");
					for (Iterator<SSAInstruction> it = bb.iterator(); it.hasNext(); ) {
						if (!sb.toString().equals("")) {
							sb.append("\\n");
						}
						sb.append(it.next().toString());
					}
				}					
				return sb.toString();
			}
		};
        GraphDotUtil.dotify(cfg, nd, cfgFileName, pdfFile, dotExe);
      } catch (WalaException e) {
        e.printStackTrace();
      }
      
    }
  }
  
  
  /* Super-ugly way to keep the colors for every method in the graph */
  private HashMap<Pair<IMethod, Integer>, LockState> stateHash;

  public void cacheStates() {
	
	/*  
    for (Iterator<Quartet<Boolean, Boolean, Boolean, Boolean>> it = 
        csDomain.iterator(); it.hasNext(); ) {
      Quartet<Boolean, Boolean, Boolean, Boolean> q = it.next();
      int i = csDomain.getMappedIndex(q);
      E.log(2, i + ": " + q.toString());
    }
    */
	  
    stateHash = new HashMap<Pair<IMethod, Integer>, LockState>();
    Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterator = icfg.iterator();
    while (iterator.hasNext()) {
      BasicBlockInContext<IExplodedBasicBlock> bb = iterator.next();
      
      //col = getTargetColor(bb);
      
      LockState q;
      
      if (Opts.DO_CS_ANALYSIS) {
      /* Context sensitive analysis */
        IntSet result = csSolver.getResult(bb);    
        assert result.size() > 0;
        q = mergeResult(result);          
    
      }
      else {
        /* Context insensitive analysis */        
        q = new LockState(Quartet.make(
        		acquireMaySolver.getOut(bb).getValue(),
        		releaseMaySolver.getOut(bb).getValue(),
        		releaseMustSolver.getOut(bb).getValue(),
        		false /* no info */));        
      }
      
      Pair<IMethod, Integer> pair = Pair.make(bb.getMethod(), bb.getNumber());      
      stateHash.put(pair, q);
    }
  }

  
  
  
  
  private LockState mergeResult(IntSet x) {
    IntIterator it = x.intIterator();
    LockState n = new LockState(
        Quartet.make(Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, Boolean.TRUE)); 
    while (it.hasNext()) {
      int i = it.next();
      LockState q = csDomain.getMappedObject(i);
      n = n.merge(q);
    }
    return n;
  }



  protected void outputSolvedICFG() {
    E.log(2, "#nodes: " + componentCallgraph.getNumberOfNodes());
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

  
  
  public boolean  	checkedPolicy 	= false;
  public String  	policyResult	= null;
  
  
  /** Thread invocation info will live in icfg !!! */
  	
  
  
  /**
   * Get the state at the exit of a cg node
   * @param cgNode
   * @return
   */
  protected LockState getExitState(CGNode cgNode) {
	  BasicBlockInContext<IExplodedBasicBlock> exit = icfg.getExit(cgNode);
      Pair<IMethod, Integer> p = Pair.make(
          cgNode.getMethod(), exit.getNumber());
      return stateHash.get(p);
  }
  
  
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
    	LockState st = getExitState(cgNode);
        
        result.put(methName, st.getColor());
        
        List<String> expStatus = elem.snd;
        String status = "BAD";
        /* Is this an expected exit state ? */
        if (expStatus.contains(st.getColor())) {
          status = "OK";
        }            
        else {
          AnalysisDriver.retCode = 1;          
        }
        //Register the result
        checkedPolicy = true;
        policyResult = status;
        
        E.slog(0, "locking.txt", this.toString() + " | " + methName + " : " + 
            st.toString() + "\t[" + status + "]");        
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



  public HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component> getThreadInvocations() {
	return icfg.getThreadInvocations();
  }

  public Collection<Component> getThreadDependencies() {
	return icfg.getThreadInvocations().values();
  }
  

  public void setThreadInvocations(HashMap<BasicBlockInContext<IExplodedBasicBlock>, Component> compInv) {
	  icfg.setThreadInvocations(compInv);
	
  }

}
