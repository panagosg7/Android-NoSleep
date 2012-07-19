package edu.ucsd.energy.component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.impl.NodeWithNumber;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;

import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.interproc.AbstractComponentCFG;
import edu.ucsd.energy.interproc.CompoundLockState;
import edu.ucsd.energy.interproc.CtxSensLocking;
import edu.ucsd.energy.interproc.CtxSensLocking.SensibleICFGSupergraph;
import edu.ucsd.energy.interproc.LockingTabulationSolver.LockingResult;
import edu.ucsd.energy.interproc.SingleLockState;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.managers.WakeLockInstance;
import edu.ucsd.energy.util.Log;

public abstract class AbstractComponent extends NodeWithNumber implements IComponent {

	protected static GlobalManager global;   

	protected CallGraph componentCallgraph;

	//The whole application call graph
	protected AppCallGraph originalCallgraph;

	protected AbstractComponentCFG icfg = null;

	protected ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> supergraph = null;


	public AbstractComponent() {
		global = GlobalManager.get();
		originalCallgraph = global.getAppCallGraph();
	}

	/**
	 * The callgraph of the specific abstract context - not the whole application 
	 */
	abstract public CallGraph getContextCallGraph();
	
	private Set<MethodReference> sMethRef;
	
	/**
	 * Get the method references of this context
	 * @return
	 */
	public Set<MethodReference> getMethodReferences() {
		if(sMethRef == null) {
			sMethRef = new HashSet<MethodReference>();
			for (Iterator<CGNode> it = getContextCallGraph().iterator(); it.hasNext(); ) {
				sMethRef.add(it.next().getMethod().getReference());			
			}
		}
		return sMethRef;
	}
		

	public AbstractComponentCFG getICFG() {
		if (icfg == null) {
			icfg = makeCFG();
		}
		return icfg;
	}

	public ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> getSupergraph() {
		if (supergraph == null) {
			AnalysisCache cache = new AnalysisCache();
			supergraph = new SensibleICFGSupergraph(getICFG(), cache);
		}
		return supergraph;
	}

	protected LockingResult csSolver;

	private TabulationDomain<Pair<WakeLockInstance,SingleLockState>, BasicBlockInContext<IExplodedBasicBlock>> csDomain;

	/**
	 * Solve the _context-sensitive_ problem on the exploded inter-procedural CFG
	 * based on the component's sensible callgraph
	 */
	public void solve() {
		CtxSensLocking lockingProblem = new CtxSensLocking(this);
		csSolver = lockingProblem.analyze();    
		csDomain = lockingProblem.getDomain();
		cacheStates();
		solved  = true;
	}

	//No so elegant way to keep the states for every method in the graph
	private HashMap<SSAInstruction, CompoundLockState> mInstrState;
	private HashMap<IExplodedBasicBlock, CompoundLockState> mEBBState;
	private HashMap<BasicBlockInContext<IExplodedBasicBlock>, CompoundLockState> mBBICState;

	private boolean solved = false;
	
	public boolean solved() {
		return solved;
	}
	

	private void cacheStates() {
		mInstrState = new HashMap<SSAInstruction, CompoundLockState>();
		mEBBState = new HashMap<IExplodedBasicBlock, CompoundLockState>();
		mBBICState = new HashMap<BasicBlockInContext<IExplodedBasicBlock>, CompoundLockState>();
		Iterator<BasicBlockInContext<IExplodedBasicBlock>> iterator = getSupergraph().iterator();
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
			mBBICState.put(bb, q);
		}

	}

	abstract public CompoundLockState getReturnState(CGNode cgNode);

	
	public CompoundLockState getExitState(CGNode n) {
		needsSolved();
		return mBBICState.get(getICFG().getExit(n));
	}

	public CompoundLockState getState(BasicBlockInContext<IExplodedBasicBlock> i) {
		needsSolved();
		return mBBICState.get(i);
	}

	public CompoundLockState getState(IExplodedBasicBlock i) {
		needsSolved();
		return mEBBState.get(i);
	}

	public CompoundLockState getState(SSAInstruction i) {
		needsSolved();
		return mInstrState.get(i);
	}
	
	protected void needsSolved() {
		if (solved) {
			return;
		}
		Assertions.UNREACHABLE("Need to solve the component before querying state.");
	}

	
	private Set<Pair<MethodReference, SSAInvokeInstruction>> unresolvedHighState;
	
	/**
	 * We need to ensure that we are not missing a high energy state propagation due 
	 * to failure to resolve an Intent. 
	 * 
	 * @return
	 */
	public Set<Pair<MethodReference, SSAInvokeInstruction>> getHightStateUnresolvedIntents(
			Collection<Pair<MethodReference, SSAInvokeInstruction>> unresolvedInstrucions) {
		if (!callsInteresting()) {
			return new HashSet<Pair<MethodReference,SSAInvokeInstruction>>();
		}
		needsSolved();
		if (unresolvedHighState == null) {
			unresolvedHighState = new HashSet<Pair<MethodReference,SSAInvokeInstruction>>();
			for (Pair<MethodReference, SSAInvokeInstruction> p : unresolvedInstrucions) {
				MethodReference mr = p.fst;
				if(getMethodReferences().contains(mr)) {
					//This method belongs to this context, so check instruction
					CompoundLockState state = getState(p.snd);
					if (!state.isEmpty()) {
						if (state.simplify().acquired()) {
							unresolvedHighState.add(p);
							Log.flog("UNRESOLVED ASYNC CALL: " + this.toString() + " calls " + p.snd.toString());
						}
						else {
							Log.log(2, this.toString());
							Log.log(2, "NOT ADDING:" + p.toString());
						}
					}
					else {
						Log.log(2, this.toString());
						Log.log(2, "NOT ADDING:" + p.toString());
					}
				}
			}
		}
		return unresolvedHighState;
		
	}

	abstract public Set<Component> getContainingContexts(CGNode node);
	
}
