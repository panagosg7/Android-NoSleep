package edu.ucsd.energy.interproc;

import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.collections.Pair;

import edu.ucsd.energy.component.AbstractComponent;
import edu.ucsd.energy.component.Component;

public class SingleContextCFG extends AbstractComponentCFG {

	  /**
	   * Constructor that the component and the pairs of methods (Signatures) that need to 
	   * be connected.
	   */
	  public SingleContextCFG(AbstractComponent component, Set<Pair<CGNode, CGNode>> packedEdges) {
		  super(component.getContextCallGraph());
		  this.absCtx = component;
		  this.callgraph = component.getContextCallGraph();
		  /* Will only work like this - loses laziness. */
		  constructFullGraph();
		  addReturnToEntryEdge(packedEdges);  
		  cacheCallbacks(packedEdges);
	  }

	  //This should be like this for single-context CFGs
	  @Override
		public Component getCalleeComponent(BasicBlockInContext<IExplodedBasicBlock> bb) {
			return null;
		}

	  @Override
		public Set<BasicBlockInContext<IExplodedBasicBlock>> getContextExit(Component c) {
			return null;
		}

	  @Override
		public boolean isReturnFromComponentEdge(
				BasicBlockInContext<IExplodedBasicBlock> bb1,
				BasicBlockInContext<IExplodedBasicBlock> bb2) {
			return false;
		}

		@Override
		public boolean isCallToComponentEdge(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			return false;
		}

		@Override
		public boolean isCallToComponent(BasicBlockInContext<IExplodedBasicBlock> src) {
			return false;
		}

		@Override
		public Component returnFromComponent(BasicBlockInContext<IExplodedBasicBlock> src) {
			return null;
		}


		@Override
		public boolean isContextExit(BasicBlockInContext<IExplodedBasicBlock> a) {
			return false;
		}

}
