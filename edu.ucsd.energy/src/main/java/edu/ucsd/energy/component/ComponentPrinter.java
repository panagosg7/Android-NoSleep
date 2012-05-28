package edu.ucsd.energy.component;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Util;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.functions.Function;

import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.interproc.SingleLockState.LockStateColor;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SystemUtil;
import edu.ucsd.energy.viz.IColorNodeDecorator;
import edu.ucsd.energy.viz.GraphDotUtil;

public class ComponentPrinter<T extends AbstractComponent> {

	CallGraph componentCallgraph;

	T component;

	ExplodedInterproceduralCFG icfg;

	public ComponentPrinter(T component) {
		this.component = component;
		this.componentCallgraph = component.getCallGraph();
		this.icfg = component.getICFG();
	}

	public void outputNormalCallGraph() {
		String prefix = "unknown";
		if (component instanceof Context) {
			prefix = "compcg";
		}
		else if (component instanceof SuperComponent) {
			prefix = "super";
		}
		outputCallGraph(component.getCallGraph(), prefix);
	}

	private void outputCallGraph(CallGraph cg, String prefix) {
		try {
			Properties p = WalaExamplesProperties.loadProperties();
			p.putAll(WalaProperties.loadProperties());
			String folder = SystemUtil.getResultDirectory() + File.separatorChar + prefix;
			new File(folder).mkdirs();
			String fileName = folder + File.separatorChar + component.toFileName() + ".dot";
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
	 * because dot can't render the complete inter-procedural CFG.)
	 * @param icfg 
	 */	
	public void outputColoredCFGs() {
		/* Need to do this here - WALA was giving me a hard time to crop a small
		 * part of the graph */
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
			TypeName className = n.getMethod().getDeclaringClass().getName();
			String bareFileName = className.toString().replace('/', '.') + "_"
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


	public void outputSolvedICFG() {
		Properties p = WalaExamplesProperties.loadProperties();
		try {
			p.putAll(WalaProperties.loadProperties());
		} catch (WalaException e) {
			e.printStackTrace();
		}
		String path = component.toFileName();
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


	/**
	 * This is a colored node decorator for a cfg (inter-procedural or not)
	 */
	IColorNodeDecorator colorNodeDecorator = new IColorNodeDecorator() {

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

		private Set<SingleLockState> getStates(IExplodedBasicBlock ebb) {
			CompoundLockState st = component.getState(ebb);
			Set<SingleLockState> result = new HashSet<SingleLockState>();
			if (st != null) {
				for (Entry<WakeLockInstance, Set<SingleLockState>> e : st.getLockStateMap().entrySet()) {
					//Merges the state for every field
					result.add(CompoundLockState.simplify(e.getValue()));
				}        	
			}
			return result;
		}

		public Set<LockStateColor> getFillColors(Object o) {
			IExplodedBasicBlock ebb = null;
			if (o instanceof BasicBlockInContext) {
				@SuppressWarnings("unchecked")
				BasicBlockInContext<IExplodedBasicBlock> bb = (BasicBlockInContext<IExplodedBasicBlock>) o;        
				ebb = bb.getDelegate();
			}
			//Per method cfg
			else if (o instanceof IExplodedBasicBlock) {
				ebb = (IExplodedBasicBlock) o;
			}
			if (ebb != null) {
				Set<SingleLockState> states = getStates(ebb);
				Function<SingleLockState, LockStateColor> f = new Function<SingleLockState, LockStateColor>() {
					public LockStateColor apply(SingleLockState sls) {
						return sls.getLockStateColor();
					}
				};
				return Util.mapToSet(states, f);
			}
			HashSet<LockStateColor> set = new HashSet<LockStateColor>();
			set.add(LockStateColor.UNDEFINED);
			return set;
		}

		public String getFontColor(Object n) {
			return "black";
		}

	};

}
