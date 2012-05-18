package edu.ucsd.energy.components;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.graph.BooleanSolver;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.IR;
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

import edu.ucsd.energy.analysis.AppCallGraph;
import edu.ucsd.energy.analysis.Opts;
import edu.ucsd.energy.analysis.SpecialConditions.SpecialCondition;
import edu.ucsd.energy.analysis.WakeLockInstance;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.CtxInsensLocking;
import edu.ucsd.energy.interproc.CtxSensLocking;
import edu.ucsd.energy.interproc.LockingTabulationSolver.LockingResult;
import edu.ucsd.energy.interproc.SensibleCallGraph;
import edu.ucsd.energy.interproc.SensibleExplodedInterproceduralCFG;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.interproc.SingleLockState.LockStateColor;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SystemUtil;
import edu.ucsd.energy.util.Util;
import edu.ucsd.energy.viz.ColorNodeDecorator;
import edu.ucsd.energy.viz.GraphDotUtil;

public abstract class Component extends NodeWithNumber {
  protected IClass klass;
  
  
  /**
   * CallBack representation 
   */

  public class CallBack {
	  
	  private CGNode node;
	  
	  public String getSignature() {
		  return node.getMethod().getSignature();
	  }
	  
	  public CGNode getNode () {
		  return node;
	  }	  
	  
	  CallBack(CGNode node) {
		  this.node = node;
	  }

      public String getName() {
		return node.getMethod().getName().toString();
      }
      
      public boolean equals(Object o) {
    	  if (o instanceof CallBack) {
    		  return this.node.equals(((CallBack) o).getNode());
    	  }
    	  return false;
      }
      
      public int hashCode() {
    	  return node.hashCode();
      }
      
      public String toString() {
    	  return ("CallBack: " + node.getMethod().getSignature().toString());
      }
	  
  }
  
  
  
  
  /*******************************************************************************
   * 
   * 					Component CallGraph
   * 
   *******************************************************************************/

  
  
  /** These are the actual callbacks that get resolved */
  private HashSet<CallBack> 			callbacks;
  private HashMap<String, CallBack> 	callbackMap;
  
  /** These are ALL the possible callbacks - initialized before 
   * the analysis runs*/
  protected HashSet<String> callbackNames;
  
  /** This is going to be needed for the construction of the 
   * sensible graph */
  protected HashSet<Pair<String, String>> callbackEdges;
  
  protected CallGraph componentCallgraph;

  protected AppCallGraph originalCallgraph;

  /**
   * Contains a call graph regarding the callbacks for this component that makes
   * sense logically based on the component's lifecycle. Only implemented for
   * Activity.
   */
  private SensibleCallGraph sensibleAuxCG = null;

  
  /** Does it call any locking functions ? */
  private boolean interesting = false;

  
  /* Deprecating this - not very useful*/
  //protected HashSet<Pair<String, List<String>>> callbackExpectedState;

  public void registerCallback(String name, CGNode node) {    
	E.log(2, "Registering callback: " + name + " to " + this.toString());    
    if (callbacks == null) {
    	callbacks = new HashSet<Component.CallBack>();
    }
    if (callbackMap == null) {
    	callbackMap = new HashMap<String, Component.CallBack>();
    }
    
    CallBack callBack = new CallBack(node);    
    callbacks.add(callBack);
    callbackMap.put(node.getMethod().getName().toString(), callBack);    
  }

  public boolean isCallBack(CGNode n) {
	  return callbacks.contains(new CallBack(n));	  
  }
  
  Component(AppCallGraph cg, IClass declaringClass, CGNode root) {
	  
    setKlass(declaringClass);
    originalCallgraph = cg;
    registerCallback(root.getMethod().getName().toString(), root);
    callbackNames         = new HashSet<String>();
    callbackEdges         = new HashSet<Pair<String, String>>();
    
    String name = this.getClass().getName().toString();
	componentType = name.substring(name.lastIndexOf('.') + 1);
    
    
  }

  void createComponentCG() {	  
    HashSet<CGNode> rootSet = new HashSet<CGNode>();    
    E.log(2, "Creating callgraph: " + klass.getName().toString());    
    HashSet<CGNode> set = new HashSet<CGNode>();    
    for (CallBack cb : getCallbacks()) {    	
      set.addAll(getDescendants(originalCallgraph, cb.getNode()));      
      rootSet.add(cb.getNode());      
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
    String name = this.getClass().getName().toString();
	b.append(name.substring(name.lastIndexOf('.') + 1) + ": ");
    b.append(getKlass().getName().toString());
    return b.toString();
  }

  public IClass getKlass() {
    return klass;
  }

  public void setKlass(IClass klass) {
    this.klass = klass;
  }

  public HashSet<CallBack> getCallbacks() {
    return callbacks;
  }

  public CallBack getCallBackByName(String name) {
    return callbackMap.get(name);
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


/*******************************************************************************
 * 
 * 					Print CFGs (colored and non-colored)
 * 
 *******************************************************************************/

  
  @SuppressWarnings("unused")
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

    public LockStateColor getFillColor(Object o) {
      if (o instanceof BasicBlockInContext) {
        @SuppressWarnings("unchecked")
        BasicBlockInContext<IExplodedBasicBlock> bb = (BasicBlockInContext<IExplodedBasicBlock>) o;        
        Pair<IMethod, Integer> pair = Pair.make(bb.getMethod(), bb.getNumber());        
        return getColorFromPP(pair);
      }
      
      if (o instanceof IExplodedBasicBlock) {
        IExplodedBasicBlock bb = (IExplodedBasicBlock) o;
        Pair<IMethod, Integer> pair = Pair.make(bb.getMethod(), bb.getNumber());        
        return getColorFromPP(pair);                
      }      
      return LockStateColor.UNDEFINED;
    }

    
    
    /**
     * Each program point can have multiple "color" states, because of: 
     * (a) multiple locks,
     * (b) multiple states due to a single lock (context sensitive inter-
     * procedural analysis)
     * @param pp
     * @return
     */
    private Set<SingleLockState> getColorsFromPP(Pair<IMethod, Integer> pp) {
        Map<WakeLockInstance, Set<SingleLockState>> st = stateHash.get(pp);
        Set<SingleLockState> result = new HashSet<SingleLockState>();
        if (st == null) {
          Assertions.UNREACHABLE();
        }
        else {          	
        	for (Entry<WakeLockInstance, Set<SingleLockState>> e : st.entrySet()) {
        		//TODO: for the moments just append all possible states 
        		// - unsound as it refers to all fields         		
        		result.addAll(e.getValue());
            	//E.log(1, pp.toString() + " :: " + color );        		
        	}        	
        }
		return result;
	}
    
    
    
    
    /**
     * If there are multiple states, just merge them
     */
    private LockStateColor getColorFromPP(Pair<IMethod, Integer> pp) {
        E.log(2, pp.toString());        
        Map<WakeLockInstance, Set<SingleLockState>> st = stateHash.get(pp);
        if (st == null) {
          Assertions.UNREACHABLE();
        }
        else {
        	if (st.size() > 0) {
        		//TODO: just take one field only
        		Set<SingleLockState> sls = st.values().iterator().next();        		
        		for (SingleLockState s : sls ) {
        			s.getLockStateColor();
        		}       		        	
        	}
        	else {
        		return LockStateColor.NOLOCKS;
        	}        	
        }
		return LockStateColor.UNDEFINED;
	}

	public Set<SingleLockState> getFillColors(Object o) {
		if (o instanceof BasicBlockInContext) {	        
	        @SuppressWarnings("unchecked")
			BasicBlockInContext<IExplodedBasicBlock> bb = (BasicBlockInContext<IExplodedBasicBlock>) o;        
	        Pair<IMethod, Integer> pair = Pair.make(bb.getMethod(), bb.getNumber());	        
	        LockStateColor colorFromPP = getColorFromPP(pair);      
	        return null;	//TODO : fix this
	      }
	      
	      if (o instanceof IExplodedBasicBlock) {
	        IExplodedBasicBlock bb = (IExplodedBasicBlock) o;
	        Pair<IMethod, Integer> pair = Pair.make(bb.getMethod(), bb.getNumber());
	        Set<SingleLockState> cols = getColorsFromPP(pair);
	        //E.log(1, pair.toString() + " :: " + cols);
	        return cols;	                	
	      }	      
	      return new HashSet<SingleLockState>();
	}

	public String getFontColor(Object n) {
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
      String folder = SystemUtil.getResultDirectory() + File.separatorChar + prefix;
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
    String cfgs = SystemUtil.getResultDirectory() + File.separatorChar + "color_cfg";
    new File(cfgs).mkdirs();
    Iterator<CGNode> it = componentCallgraph.iterator();
    while (it.hasNext()) {
      CGNode n = it.next();      
      ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = icfg.getCFG(n);
      
      if (cfg == null) {
    	  //JNI methods are empty
    	  continue;
      }       
      
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
    String cfgs = SystemUtil.getResultDirectory() + File.separatorChar + "cfg";
    new File(cfgs).mkdirs();
    Iterator<CGNode> it = componentCallgraph.iterator();
    while (it.hasNext()) {
      final CGNode n = it.next();      
      IR ir = n.getIR();
      
      if (ir == null) {
    	  //JNI methods are empty
    	  continue;
      }      
    		  
      SSACFG cfg = ir.getControlFlowGraph();
      
      String bareFileName = n.getMethod().getDeclaringClass().getName().toString().replace('/', '.') + "_"
          + n.getMethod().getName().toString();
      String cfgFileName = cfgs + File.separatorChar + bareFileName + ".dot";
      String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
      String pdfFile = null;
      try {      
    	NodeDecorator nd = new NodeDecorator() {			
			public String getLabel(Object o) throws WalaException {
				StringBuffer sb = new  StringBuffer();
				if (o instanceof ISSABasicBlock) {
					
					ISSABasicBlock bb = (ISSABasicBlock) o;
					
					SensibleExplodedInterproceduralCFG icfgLoc = getICFG();
					ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = icfgLoc.getCFG(n);
					
					IExplodedBasicBlock ebb = cfg.getNode(bb.getNumber());
					//sb.append(ebb.toString() + "\\n");
					//sb.append(bb.toString() + "\\n");
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

  protected LockingResult csSolver;
  private 	TabulationDomain<Pair<WakeLockInstance,SingleLockState>, BasicBlockInContext<IExplodedBasicBlock>> csDomain;

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
   */
  public SensibleExplodedInterproceduralCFG createSensibleCG() {
	  
    E.log(2, "Creating sensible CFG for " + this.toString());
    
    /* First create the auxiliary call graph (SensibleAuxCallGraph) This is just
     * the graph that represents the logical relation of callbacks. */
    SensibleCallGraph sensibleCG = new SensibleCallGraph(this);
    
    /* Test it */
    //sensibleCG.outputToDot();
    
    /* Pack inter-procedural sensible edges */
    HashSet<Pair<CGNode, CGNode>> packedEdges = sensibleCG.packEdges();
    
    return new SensibleExplodedInterproceduralCFG(getCallgraph(), originalCallgraph, packedEdges);
    
  }

  /**
   * Solve the _context-insensitive_ problem on the exploded inter-procedural
   * CFG based on the component's sensible callgraph
   */
  public void solveCICFG() {
	icfg = getICFG();
	
	CtxInsensLocking lockingProblem = new CtxInsensLocking(icfg);
    acquireMaySolver = lockingProblem.analyze(true, true);
    releaseMaySolver = lockingProblem.analyze(true, false);
    releaseMustSolver = lockingProblem.analyze(false, false);
  }

  /**
   * Solve the _context-sensitive_ problem on the exploded inter-procedural CFG
   * based on the component's sensible callgraph
   */
  public void solveCSCFG() {
	if (icfg == null) {
	  icfg = createSensibleCG();
	  icfg.printBBToThreadMap();
	}
	
    CtxSensLocking lockingProblem = new CtxSensLocking(icfg);
    csSolver = lockingProblem.analyze();    
    csDomain = lockingProblem.getDomain();
    isSolved = true;    
  }

  
  /** Set this true if we analyze it as part of a larger graph */
  public boolean	isSolved		= false;
  

  
  /**
   *  Super-ugly way to keep the states for every method in the graph 
   */  
  private HashMap<Pair<IMethod, Integer>, Map<WakeLockInstance,Set<SingleLockState>>> stateHash;

  public void cacheStates() {
	  stateHash = new HashMap<Pair<IMethod, Integer>, Map<WakeLockInstance,Set<SingleLockState>>>();
	  Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterator = icfg.iterator();
	  while (iterator.hasNext()) {	      
		  BasicBlockInContext<IExplodedBasicBlock> bb = iterator.next();	      
		  Pair<IMethod, Integer> pair = Pair.make(bb.getMethod(), bb.getNumber());
		  	      
	      if (Opts.DO_CS_ANALYSIS) {
	    	/* Context sensitive analysis */
	    	//q = csSolver.getMergedState(bb);
	    	IntSet set = csSolver.getResult(bb);	    	
	    	HashMap<WakeLockInstance, Set<SingleLockState>> q = 
	    			new HashMap<WakeLockInstance, Set<SingleLockState>>();
	    	for (IntIterator it = set.intIterator(); it.hasNext(); ) {
	    		int i = it.next();
	    		Pair<WakeLockInstance, SingleLockState> obj = csDomain.getMappedObject(i);	    		
	    		Set<SingleLockState> sts = q.get(obj.fst);	    		
	    		if (sts == null) {	//first time
	    			sts = new HashSet<SingleLockState>();
	    			sts.add(obj.snd);	    			
	    		}
	    		else {
	    			sts.add(obj.snd);	    			
	    		}
	    		q.put(obj.fst, sts);
	    		
	    		if(Opts.PRINT_HIGH_STATE) {
		    		/**
			    	 * Keep some info about the states that are of interest to us
			    	 */
			    	if (obj.snd.isMustbeAcquired()) {
			    	//This program point is kept to high power state by this specific field 
			    		SSAInstruction instruction = bb.getDelegate().getInstruction();
			    		if (instruction instanceof SSAInvokeInstruction) {
			    			SSAInvokeInstruction inv = (SSAInvokeInstruction) instruction;
			    			E.log(1, "HIGH POWER: " + inv.toString());
			    		}
			    	}
	    		}
	    	}
	    	stateHash.put(pair, q);
	      }

	      else {
	    	/* Context insensitive (TODO: needs fixing)*/
	    	  SingleLockState singleLockState = new SingleLockState(Quartet.make(
	          		acquireMaySolver.getOut(bb).getValue(),
	          		releaseMaySolver.getOut(bb).getValue(),
	          		releaseMustSolver.getOut(bb).getValue(),
	          		false /* no info */));	    	  	    	 
	    	  Assertions.UNREACHABLE("Need to fix context-insensitive analysis.");	       
	      }	      
	    }
  }
  
  
  public void outputSolvedICFG() {
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
    String dotFile = SystemUtil.getResultDirectory() + File.separatorChar + DOT_FILE;

    try {
      /* Do the colored graph */
      GraphDotUtil.dotify(icfg, colorNodeDecorator, dotFile, pdfFile, dotExe);
    } catch (WalaException e) {
      e.printStackTrace();
    }
  }
  
  
  
  /*******************************************************************************
   * 
   * 			Auxiliary
   * 
   *******************************************************************************/

  
  /**
   * Get the state at the exit of a cg node
   * @param cgNode
   * @return May return null (eg. JNI)
   */
  public CompoundLockState getExitState(CGNode cgNode) {
	  //This will create problems (JNI)
	  if (icfg.getCFG(cgNode) == null) {
		  return null;
	  }
	  BasicBlockInContext<IExplodedBasicBlock> exit = icfg.getExit(cgNode);
      Pair<IMethod, Integer> p = Pair.make(
          cgNode.getMethod(), exit.getNumber());
      Map<WakeLockInstance, Set<SingleLockState>> map = stateHash.get(p);
      CompoundLockState compoundLockState = new CompoundLockState(map);
      return compoundLockState;
  }
  
  public CompoundLockState getState(CGNode cgNode, int num) {
	  //This will create problems (JNI)
	  if (icfg.getCFG(cgNode) == null) {
		  return null;
	  }
      Pair<IMethod, Integer> p = Pair.make(cgNode.getMethod(), num);
      Map<WakeLockInstance, Set<SingleLockState>> map = stateHash.get(p);
      CompoundLockState compoundLockState = new CompoundLockState(map);
      return compoundLockState;
  }
  
  
  /* 	
   * Apply the policies to the corresponding callbacks. This one checks that at
   * the end of the method, we have an expected state.
   */
  public Map<String, CompoundLockState> getCallBackExitStates() {
	
	Map<String, CompoundLockState> result = 
			new HashMap<String, CompoundLockState>();
	
	/* get the defined callbacks */
	HashSet<CallBack> callbacks = getCallbacks();
	
	for (CallBack cb : callbacks) {						    
  		//Register the exit state of every callback
  		result.put(cb.getName(), getExitState(cb.getNode()));
	}
	        
    return result;
    
  }

  public Map<String, CompoundLockState> getAllExitStates() {
		Map<String, CompoundLockState> result = new HashMap<String, CompoundLockState>();			
		for (Iterator<CGNode> it = icfg.getCallGraph().iterator(); it.hasNext(); ) {
	  		CGNode node = it.next();
			result.put(node.getMethod().getName().toString(), getExitState(node));
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

  public void setSpecialConditions(HashMap<BasicBlockInContext<IExplodedBasicBlock>, SpecialCondition> compCond) {
	  icfg.setSpecConditions(compCond);	
  }
  

  public boolean isThread() {	
	return (this instanceof RunnableThread);
  }
  
  public boolean isActivity() {	
		return (this instanceof Activity);
  }

  public String getComponentType() {
	return componentType;
  }

 protected String componentType = null; 
  
}
