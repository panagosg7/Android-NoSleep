package edu.ucsd.energy.component;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.examples.properties.WalaExamplesProperties;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.viz.NodeDecorator;

import edu.ucsd.energy.interproc.AbstractContextCFG;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.interproc.SingleLockState.LockStateDescription;
import edu.ucsd.energy.util.Log;
import edu.ucsd.energy.util.SystemUtil;
import edu.ucsd.energy.viz.GraphDotUtil;
import edu.ucsd.energy.viz.IColorNodeDecorator;

public class ComponentPrinter<T extends AbstractContext> {

	Properties p = WalaExamplesProperties.properties;

	CallGraph componentCallgraph;

	T component;

	AbstractContextCFG icfg;

	ColorNodeDecorator colorNodeDecorator = new ColorNodeDecorator();

	SimpleNodeDecorator nodeDecorator = new SimpleNodeDecorator();

	public ComponentPrinter(T component) {
		this.component = component;
		this.componentCallgraph = component.getContextCallGraph();
		this.icfg = component.getICFG();
	}

	public void outputNormalCallGraph() {
		String prefix = "unknown";
		if (component instanceof Component) {
			prefix = "context_callgraphs";
		}
		else if (component instanceof SuperComponent) {
			prefix = "supercomponent_callgraphs";
		}
		outputCallGraph(component.getContextCallGraph(), prefix);
	}

	private void outputCallGraph(CallGraph cg, String prefix) {
		try {
			String folder = SystemUtil.getResultDirectory() + File.separatorChar + prefix;
			new File(folder).mkdirs();
			String fileName = folder + File.separatorChar + component.toFileName() + ".dot";
			String dotExe = p.getProperty(WalaExamplesProperties.DOT_EXE);
			String pdfFile = null;
			Log.log(2, "Dumping: " + fileName);
			GraphDotUtil.dotify(cg, null, fileName, pdfFile, dotExe);
			return;
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (WalaException e) {
			e.printStackTrace();
		}	
	}


	/**
	 * Output the colored CFG for each node in the callgraph.
	 * "Dot" can't render the complete inter-procedural CFG.
	 * @param icfg 
	 */	
	public void outputColoredCFGs() {
		//Need to do this here - WALA was giving me a hard time to crop a small
		//part of the graph
		String cfgs = SystemUtil.getResultDirectory() + File.separatorChar + "color_cfg" +
				File.separatorChar +  component.toFileName() ;	//every supercomponent should have its own folder 
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
	 * Output the whole component CFG without colors
	 */
	public void outputSupergraph() {
		
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

		private Collection<SingleLockState> getStates(BasicBlockInContext<IExplodedBasicBlock> ebb) {
			CompoundLockState st = component.getState(ebb);
			return st.getStates();
		}



		public List<LockStateDescription> statesToColor(Collection<SingleLockState> states) {
			ArrayList<LockStateDescription> list = new ArrayList<LockStateDescription>();
			if (states != null) {
				for(SingleLockState st : states) {
					LockStateDescription lsd = st.getLockStateDescription();
					list.add(lsd);
				}
			}
			else {
				list.add(LockStateDescription.UNDEFINED);
			}
			return list;
		}


		public List<LockStateDescription> getFillColors(Object o) {
			IExplodedBasicBlock ebb = null;
			Collection<SingleLockState> states = null;
			//Supergraph - need to have the BasicBlockInContext
			if (o instanceof BasicBlockInContext) {
				@SuppressWarnings("unchecked")
				BasicBlockInContext<IExplodedBasicBlock> bb = (BasicBlockInContext<IExplodedBasicBlock>) o;
				states = getStates(bb);
			}
			//Per method cfg
			else if (o instanceof IExplodedBasicBlock) {
				ebb = (IExplodedBasicBlock) o;
				states = getStates(ebb);
				//if (ebb.isExitBlock()) {
				//	System.out.println(ebb.toString() + " :: " + states.toString());
				//}
			}
			return statesToColor(states);

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
				if (icfg.isReturnFromContextEdge(bb1, bb2) || icfg.isCallToContextEdge(bb1, bb2)) {
					return " [style = bold arrowhead = diamond color = blue]";
				}
				if (icfg.isLifecycleExit(bb1)) {
					return " [style = bold color = blue]";
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
