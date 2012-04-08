package energy.interproc;

import java.util.Collection;
import java.util.HashMap;

import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationProblem;
import com.ibm.wala.dataflow.IFDS.PartiallyBalancedTabulationSolver;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationProblem;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;

public class LockingTabulationSolver  extends PartiallyBalancedTabulationSolver<
	BasicBlockInContext<IExplodedBasicBlock>, 
	CGNode, Pair<FieldReference, SingleLockState>> {

	protected LockingTabulationSolver(
			PartiallyBalancedTabulationProblem<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<FieldReference, SingleLockState>> p,
			IProgressMonitor monitor) {
		super(p, monitor);
	} 
	
	
	public LockingResult solve() throws CancelException {
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<FieldReference, SingleLockState>> result = super.solve();		
		return new LockingResult(result);
	}
	
	public class LockingResult implements TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, 
	CGNode, Pair<FieldReference, SingleLockState>> {
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<FieldReference, SingleLockState>> result;
		
		LockingResult(TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<FieldReference, SingleLockState>> r) {
			this.result = r;
		} 
		
		/**
		 * Get the a mapping with all the states with a single state for every field 
		 * @param n
		 * @return
		 */
		public HashMap<FieldReference, SingleLockState> getMergedState(BasicBlockInContext<IExplodedBasicBlock> n) {
			HashMap<FieldReference, SingleLockState> result = new  HashMap<FieldReference, SingleLockState>();						
			IntSet orig = getResult(n);			
			TabulationDomain<Pair<FieldReference, SingleLockState>, BasicBlockInContext<IExplodedBasicBlock>> dom =
					this.getProblem().getDomain();	
			for (IntIterator it = orig.intIterator(); it.hasNext(); ) {
				int i = it.next();
				Pair<FieldReference, SingleLockState> iObj = dom.getMappedObject(i);
				SingleLockState already = result.get(iObj.fst);
				if (already != null) {
					result.put(iObj.fst, already.merge(iObj.snd));
				}
				else{
					result.put(iObj.fst, iObj.snd);			
				}			
			}
			return result;			
		}

		@Override
		public IntSet getResult(BasicBlockInContext<IExplodedBasicBlock> node) {			
			return result.getResult(node);
		}

		@Override
		public TabulationProblem<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<FieldReference, SingleLockState>> getProblem() {
			return result.getProblem();
		}

		@Override
		public Collection<BasicBlockInContext<IExplodedBasicBlock>> getSupergraphNodesReached() {
			return result.getSupergraphNodesReached();
		}

		@Override
		public IntSet getSummaryTargets(
				BasicBlockInContext<IExplodedBasicBlock> n1, int d1,
				BasicBlockInContext<IExplodedBasicBlock> n2) {			
			return result.getSummaryTargets(n1, d1, n2);
		}

		@Override
		public Collection<PathEdge<BasicBlockInContext<IExplodedBasicBlock>>> getSeeds() {
			return result.getSeeds();
		}
	
	}
	

}
