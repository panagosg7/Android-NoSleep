package edu.ucsd.energy.component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.content.IContentTypeManager.ISelectionPolicy;

import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.IndiscriminateFilter;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.graph.GraphReachability;
import com.ibm.wala.util.graph.impl.NodeWithNumber;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;

import edu.ucsd.energy.analysis.AppCallGraph;
import edu.ucsd.energy.contexts.Component;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.CtxSensLocking;
import edu.ucsd.energy.interproc.InterproceduralCFG;
import edu.ucsd.energy.interproc.LockingTabulationSolver.LockingResult;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.util.E;
import edu.ucsd.energy.util.Util;

public abstract class AbstractComponent extends NodeWithNumber implements IComponent {

	private GlobalManager global;  

	protected CallGraph componentCallgraph;

	public AppCallGraph originalCallgraph;

	public AbstractComponent(GlobalManager gm) {
		global = gm;
		originalCallgraph = gm.getAppCallGraph();
	}

	abstract protected void makeCallGraph();

	public CallGraph getCallGraph() {
		if (componentCallgraph == null) {
			makeCallGraph();
		}
		return componentCallgraph;	
	}

	protected Set<CGNode> getDescendants(CallGraph cg, CGNode node) {
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


	protected InterproceduralCFG icfg = null;

	public InterproceduralCFG getICFG() {
		if (icfg == null) {
			icfg = makeInterproceduralCFG();
		}
		return icfg;
	}

	protected LockingResult csSolver;

	private TabulationDomain<Pair<WakeLockInstance,SingleLockState>, BasicBlockInContext<IExplodedBasicBlock>> csDomain;

	/**
	 * Solve the _context-sensitive_ problem on the exploded inter-procedural CFG
	 * based on the component's sensible callgraph
	 */
	public void solve() {
		CtxSensLocking lockingProblem = new CtxSensLocking(getICFG());
		csSolver = lockingProblem.analyze();    
		csDomain = lockingProblem.getDomain();
		isSolved = true;    
		cacheStates();
	}

	//Set this true if we analyze it as part of a larger graph
	public boolean isSolved = false;

	//No so elegant way to keep the states for every method in the graph
	private HashMap<SSAInstruction, CompoundLockState> mInstrState;
	private HashMap<IExplodedBasicBlock, CompoundLockState> mEBBState;

	private void cacheStates() {
		E.log(2, "Caching states");
		mInstrState = new HashMap<SSAInstruction, CompoundLockState>();
		mEBBState = new HashMap<IExplodedBasicBlock, CompoundLockState>();

		Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterator = icfg.iterator();
		while (iterator.hasNext()) {
			BasicBlockInContext<IExplodedBasicBlock> bb = iterator.next();
			IntSet set = csSolver.getResult(bb);
			CompoundLockState q = new CompoundLockState();
			for (IntIterator it = set.intIterator(); it.hasNext(); ) {
				int i = it.next();
				Pair<WakeLockInstance, SingleLockState> obj = csDomain.getMappedObject(i);
				q.register(obj.fst, obj.snd);
			}  
			SSAInstruction instruction = bb.getDelegate().getInstruction();
			if (instruction != null) {
				mInstrState.put(instruction, q);
			}
			mEBBState.put(bb.getDelegate(), q);

		}
	}


	public GlobalManager getGlobalManager() {
		return global;
	}

	/**
	 * Get the state at the exit of a cg node
	 * @param cgNode
	 * @return May return null (eg. JNI)
	 */
	public CompoundLockState getReturnState(CGNode cgNode) {
		if (cgNode.getMethod().isNative()) return null;

		//XXX: Be very careful with this!!!
		//The exit basic block does not always contain the state we need, so 
		//we get it from the non-exceptional predecessor
		BasicBlockInContext<IExplodedBasicBlock> exitBB = icfg.getExit(cgNode);
		Iterator<BasicBlockInContext<IExplodedBasicBlock>> predNodes = icfg.getPredNodes(exitBB);
		HashSet<CompoundLockState> set = new HashSet<CompoundLockState>();
		while(predNodes.hasNext()) {
			BasicBlockInContext<IExplodedBasicBlock> bb = predNodes.next();
			if (!icfg.isExceptionalEdge(bb, exitBB)) {
				IExplodedBasicBlock ebb = bb.getDelegate();
				set.add(mEBBState.get(ebb));
			}
		}
		CompoundLockState merged = CompoundLockState.merge(set);
		//IExplodedBasicBlock exit = exitBB.getDelegate();
		
		//Do not search by instruction cause that might be null
		return merged;
	}

	public CompoundLockState getState(IExplodedBasicBlock i) {
		return mEBBState.get(i);
	}

	public CompoundLockState getState(SSAInstruction i) {
		return mInstrState.get(i);
	}

}
