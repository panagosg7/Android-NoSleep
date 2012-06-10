package edu.ucsd.energy.component;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
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
import com.ibm.wala.util.functions.Function;
import com.ibm.wala.viz.NodeDecorator;

import edu.ucsd.energy.contexts.Context;
import edu.ucsd.energy.interproc.AbstractContextCFG;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.interproc.SingleLockState.LockStateDescription;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.SystemUtil;
import edu.ucsd.energy.viz.GraphDotUtil;
import edu.ucsd.energy.viz.IColorNodeDecorator;

public class ComponentPrinter<T extends AbstractComponent> {

	CallGraph componentCallgraph;

	T component;

	AbstractContextCFG icfg;
	
	ColorNodeDecorator colorNodeDecorator = new ColorNodeDecorator();
	
	SimpleNodeDecorator nodeDecorator = new SimpleNodeDecorator();

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


	public void outputColoredSupergraph() {
		Properties p = WalaExamplesProperties.loadProperties();
		try {
			p.putAll(WalaProperties.loadProperties());
		} catch (WalaException e) {
			e.printStackTrace();
		}
		String cfgs = SystemUtil.getResultDirectory() + File.separatorChar + "color_super_cfg";
		new File(cfgs).mkdirs();
		
		String bareFileName = component.toFileName();
		String dotFile = cfgs + File.separatorChar + bareFileName + ".dot";
		String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
		String pdfFile = null;
		try {
			/* Do the colored graph */
			ColorNodeDecorator colorNodeDecorator = new ColorNodeDecorator();
			GraphDotUtil.dotify(icfg, colorNodeDecorator, dotFile, pdfFile, dotExe);
		} catch (WalaException e) {
			e.printStackTrace();
		}
	}

	
	/**
	 * Output the whole component CFG
	 */
	public void outputSupergraph() {
		Properties p = WalaExamplesProperties.loadProperties();
		try {
			p.putAll(WalaProperties.loadProperties());
		} catch (WalaException e) {
			e.printStackTrace();
		}
		String cfgs = SystemUtil.getResultDirectory() + File.separatorChar + "super_cfg";
		new File(cfgs).mkdirs();
		
		String bareFileName = component.toFileName();
		String dotFile = cfgs + File.separatorChar + bareFileName + ".dot";
		String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
		String pdfFile = null;
		try {
			/* Do the colored graph */
			GraphDotUtil.dotify(icfg, nodeDecorator, dotFile, pdfFile, dotExe);
		} catch (WalaException e) {
			e.printStackTrace();
		}
	}


	/**
	 * Node Decorators
	 */
	
	public class ColorNodeDecorator extends SimpleNodeDecorator implements IColorNodeDecorator {

		private Collection<SingleLockState> getStates(IExplodedBasicBlock ebb) {
			CompoundLockState st = component.getState(ebb);
			return st.getStates();
		}
		
		public Set<LockStateDescription> getFillColors(Object o) {
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
				Collection<SingleLockState> states = getStates(ebb);
				Function<SingleLockState, LockStateDescription> f = new Function<SingleLockState, LockStateDescription>() {
					public LockStateDescription apply(SingleLockState sls) {
						return sls.getLockStateDescription();
					}
				};
				return Util.mapToSet(states, f);
			}
			HashSet<LockStateDescription> set = new HashSet<LockStateDescription>();
			set.add(LockStateDescription.UNDEFINED);
			return set;
		}

		
		public String edgeLabel (Object o1, Object o2) {
			if ((o1 instanceof BasicBlockInContext) && (o2 instanceof BasicBlockInContext)) {
				@SuppressWarnings("unchecked")
				BasicBlockInContext<IExplodedBasicBlock> bb1 = (BasicBlockInContext<IExplodedBasicBlock>) o1;
				@SuppressWarnings("unchecked")
				BasicBlockInContext<IExplodedBasicBlock> bb2 = (BasicBlockInContext<IExplodedBasicBlock>) o2;
				if (icfg.isExceptionalEdge(bb1, bb2)) {
					return " [style = dashed]";
				}
			}
			return "";
		}
		
		public String getFontColor(Object n) {
			return "black";
		}

	}
	
	
	public class SimpleNodeDecorator implements NodeDecorator {

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

		
	};

}
