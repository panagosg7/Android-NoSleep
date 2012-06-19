package edu.ucsd.energy.interproc;

import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;

import edu.ucsd.energy.component.AbstractComponent;
import edu.ucsd.energy.contexts.Context;

public class SingleContextCFG extends AbstractContextCFG {

	  /**
	   * Constructor that the component and the pairs of methods (Signatures) that need to 
	   * be connected.
	   */
	  public SingleContextCFG(AbstractComponent component, Set<Pair<CGNode, CGNode>> packedEdges) {
		  super(component.getCallGraph());
		  this.component = component;
		  this.callgraph = component.getCallGraph();
		  /* Will only work like this - loses laziness. */
		  constructFullGraph();
		  addReturnToEntryEdge(packedEdges);  
		  cacheCallbacks(packedEdges);
	  }

	  //This should be like this for single-context CFGs
	  @Override
		public Context getCalleeContext(BasicBlockInContext<IExplodedBasicBlock> bb) {
	  	Assertions.UNREACHABLE();
			return null;
		}

	  @Override
		public Set<BasicBlockInContext<IExplodedBasicBlock>> getContextExit(Context c) {
	  	Assertions.UNREACHABLE();
			return null;
		}

	  @Override
		public boolean isReturnFromContextEdge(
				BasicBlockInContext<IExplodedBasicBlock> bb1,
				BasicBlockInContext<IExplodedBasicBlock> bb2) {
	  	Assertions.UNREACHABLE();
			return false;
		}

		@Override
		public boolean isCallToContextEdge(
				BasicBlockInContext<IExplodedBasicBlock> src,
				BasicBlockInContext<IExplodedBasicBlock> dest) {
			Assertions.UNREACHABLE();
			return false;
		}

		@Override
		public boolean isCallToContext(BasicBlockInContext<IExplodedBasicBlock> src) {
			Assertions.UNREACHABLE();
			return false;
		}

		@Override
		public Context returnFromContext(BasicBlockInContext<IExplodedBasicBlock> src) {
			Assertions.UNREACHABLE();
			return null;
		}

}
