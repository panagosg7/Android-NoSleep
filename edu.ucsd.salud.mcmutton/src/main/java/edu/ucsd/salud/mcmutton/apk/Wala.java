package edu.ucsd.salud.mcmutton.apk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jgrapht.DirectedGraph;
import org.jgrapht.alg.BellmanFordShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayLengthInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSABinaryOpInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAComparisonInstruction;
import com.ibm.wala.ssa.SSAConditionalBranchInstruction;
import com.ibm.wala.ssa.SSAConversionInstruction;
import com.ibm.wala.ssa.SSAGetCaughtExceptionInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAGotoInstruction;
import com.ibm.wala.ssa.SSAInstanceofInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstruction.IVisitor;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadMetadataInstruction;
import com.ibm.wala.ssa.SSAMonitorInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAPiInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SSAThrowInstruction;
import com.ibm.wala.ssa.SSAUnaryOpInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.config.AnalysisScopeReader;

import edu.ucsd.salud.mcmutton.ApkException;
import edu.ucsd.salud.mcmutton.ApkInstance;
import edu.ucsd.salud.mcmutton.RetargetException;
import energy.analysis.AnalysisResults;
import energy.analysis.ApplicationCallGraph;
import energy.analysis.ApplicationClassHierarchy;
import energy.analysis.ComponentManager;
import energy.analysis.LockInvestigation;
import energy.analysis.Opts;

public class Wala {
	private File mPath;
	private File mAndroidJarPath;
	private File mCachePath;
	
	private final Logger LOGGER = Logger.getLogger(ApkInstance.class.getName());
	
	public Wala(File path, File androidJarPath, File cachePath) {
		mPath = path;
		mAndroidJarPath = androidJarPath;
		mCachePath = cachePath;
	}
	
	public AnalysisScope getWalaScope() throws IOException, RetargetException {
		File exclusions_file = new File("test_exclusions.txt");

		String classPathArray[] = {
				mPath.getAbsolutePath()
//				,mAndroidJarPath.getAbsolutePath()
		};
		
		final String classPath = StringUtils.join(classPathArray, File.pathSeparator);
		AnalysisScope apk_scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(classPath, exclusions_file);
		apk_scope.addToScope(apk_scope.getPrimordialLoader(), new JarFile(mAndroidJarPath.getAbsolutePath()));
		
		return apk_scope;
	}
	
	public Map<MethodReference, Set<MethodReference>> interestingCallSites(final Set<MethodReference> interestingMethods, IClassHierarchy cha) throws ApkException {
		final Map<MethodReference, Set<MethodReference>> results = new HashMap<MethodReference, Set<MethodReference>>();
		
		try {
			class CallVisitor implements IVisitor {
				private IMethod mCurrentMethod = null;
				
				public void setCurrentMethod(IMethod meth) { mCurrentMethod = meth; }
				
				public void visitInvoke(SSAInvokeInstruction instruction) {
					MethodReference target = instruction.getDeclaredTarget();
					
					if (interestingMethods.contains(instruction.getDeclaredTarget())) {
						if (!results.containsKey(instruction.getDeclaredTarget())) {
							results.put(target, new HashSet<MethodReference>());
						}
						results.get(target).add(mCurrentMethod.getReference());
					}
				}

				public void visitArrayLength(SSAArrayLengthInstruction instruction) {}
				public void visitArrayLoad(SSAArrayLoadInstruction instruction) {}
				public void visitArrayStore(SSAArrayStoreInstruction instruction) {}
				public void visitBinaryOp(SSABinaryOpInstruction instruction) {}
				public void visitCheckCast(SSACheckCastInstruction instruction) {}
				public void visitComparison(SSAComparisonInstruction instruction) {}
				public void visitConditionalBranch(SSAConditionalBranchInstruction instruction) {}
				public void visitConversion(SSAConversionInstruction instruction) {}
				public void visitGet(SSAGetInstruction instruction) {}
				public void visitGetCaughtException(SSAGetCaughtExceptionInstruction instruction) {}
				public void visitGoto(SSAGotoInstruction instruction) {}
				public void visitInstanceof(SSAInstanceofInstruction instruction) {}
				public void visitLoadMetadata(SSALoadMetadataInstruction instruction) {}
				public void visitMonitor(SSAMonitorInstruction instruction) {}
				public void visitNew(SSANewInstruction instruction) {}
				public void visitPhi(SSAPhiInstruction instruction) {}
				public void visitPi(SSAPiInstruction instruction) {}
				public void visitPut(SSAPutInstruction instruction) {}
				public void visitReturn(SSAReturnInstruction instruction) {}
				public void visitSwitch(SSASwitchInstruction instruction) {}
				public void visitThrow(SSAThrowInstruction instruction) {}
				public void visitUnaryOp(SSAUnaryOpInstruction instruction) {}
			}
			
			CallVisitor myVisitor = new CallVisitor();
			
			AnalysisCache cache = new AnalysisCache();

			Set<String> sourceClasses = edu.ucsd.salud.mcmutton.apk.Util.getClassCollectionFromJarOrDirectory(mPath);
			
			for (IClass cls: cha) {
				// Drop cached crap?
				String canonicalName = cls.getName().toString().substring(1).replaceFirst("[$].*$", "");


				if (!sourceClasses.contains(canonicalName)) {
					//System.out.println("cn: " + canonicalName);
					continue;
				}

				for (IMethod meth: cls.getAllMethods()) {
					IR methIR = cache.getIR(meth);
					
					if (methIR == null) {
						//System.err.println("no IR for " + canonicalName + "::" + meth.toString());
						continue;
					}
					
					myVisitor.setCurrentMethod(meth);

					for (ISSABasicBlock bb: methIR.getControlFlowGraph()) {
						for (SSAInstruction inst: bb) {
							inst.visit(myVisitor);
						}
					}
					
				}
			}
		} catch (Exception e) {
//			e.printStackTrace();
			LOGGER.info("FAIL " + e.toString());
			return null;
		}
		
		return results;
	}
	
	protected List<Entrypoint> getEntrypoints(IClassHierarchy cha) {
		IClass activityClass = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Application, "Landroid/app/Activity"));
		IClass serviceClass = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Application, "Landroid/app/Service"));
		IClass broadcastReceiverClass = cha.lookupClass(TypeReference.findOrCreate(ClassLoaderReference.Application, "Landroid/content/BroadcastReceiver"));

		Set<Entrypoint> entryPoints = new HashSet<Entrypoint>();

		for (IClass cls: cha) {
//			if (!cls.getClassLoader().getReference().equals(ClassLoaderReference.Application)) continue;
			
			if (cls.getClassHierarchy().isSubclassOf(cls, activityClass)) {
				
				
				for (String methodName: Interesting.activityEntryMethods) {
					IMethod method = cls.getMethod(Selector.make(methodName));
					if (method == null) continue;
					entryPoints.add(new DefaultEntrypoint(method, cha));
				}
			}
			
			if (cls.getClassHierarchy().isSubclassOf(cls, serviceClass)) {
				for (String methodName: Interesting.serviceEntryMethods) {
					IMethod method = cls.getMethod(Selector.make(methodName));
					if (method == null) continue;
					entryPoints.add(new DefaultEntrypoint(method, cha));
				}
			}
			
			if (cls.getClassHierarchy().isSubclassOf(cls, broadcastReceiverClass)) {
				for (String methodName: Interesting.broadcastReceiverEntryMethods) {
					IMethod method = cls.getMethod(Selector.make(methodName));
					
					if (method == null) continue;
					entryPoints.add(new DefaultEntrypoint(method, cha));
				}
			}
		}
		
		List<Entrypoint> points = new ArrayList<Entrypoint>();
		points.addAll(entryPoints);
		return points;
	}
	
	public Map<MethodReference, Map<MethodReference, Double>> callGraphReachability(final Set<MethodReference> interestingMethods,
																					IClassHierarchy cha, 
																					CallGraph cg) {
		final Map<MethodReference, Map<MethodReference, Double>> results = new HashMap<MethodReference, Map<MethodReference, Double>>();

		
		DirectedGraph<IMethod, DefaultEdge> fullGraph = new DefaultDirectedGraph<IMethod, DefaultEdge>(DefaultEdge.class);
		
		/* Make vertices */
		for (CGNode node: cg) {
			fullGraph.addVertex(node.getMethod());
			
			Iterator<CallSiteReference> i = node.iterateCallSites();
			
			while (i.hasNext()) {
				CallSiteReference ref = i.next();
				IMethod method = cha.resolveMethod(ref.getDeclaredTarget());
				if (method == null) continue;
				
				fullGraph.addVertex(method);
			}
		}
		
		/* Make edges */
		for (CGNode node: cg) {
			
			Iterator<CGNode> si = cg.getSuccNodes(node);
			while (si.hasNext()) {
				fullGraph.addEdge(node.getMethod(), si.next().getMethod());
			}
			
			Iterator<CallSiteReference> i = node.iterateCallSites();
			
			while (i.hasNext()) {
				CallSiteReference ref = i.next();
				IMethod method = cha.resolveMethod(ref.getDeclaredTarget());
				if (method == null) continue;
				
				fullGraph.addEdge(node.getMethod(), method);
			}
		}
		
		for (CGNode entryNode: cg.getEntrypointNodes()) {
			IMethod source = entryNode.getMethod();
			
			BellmanFordShortestPath<IMethod, DefaultEdge> sp = new BellmanFordShortestPath<IMethod, DefaultEdge>(fullGraph, source);
			
			for (MethodReference ref: interestingMethods) {
				IMethod meth = cha.resolveMethod(ref);
				
				if (fullGraph.containsVertex(meth)) {
					double cost = sp.getCost(meth);
					if (!Double.isInfinite(cost)) {
						if (!results.containsKey(ref)) {
							results.put(ref, new HashMap<MethodReference, Double>());
						}
						results.get(ref).put(entryNode.getMethod().getReference(), cost);
					}
				}
			}
		}
	
//		try {
//			DotUtil.dotify(cg, null, "test.dot", "test.pdf", "dot");
//		} catch (WalaException e) {
//			e.printStackTrace();
//		}
		return results;

	}
	
	public Map<MethodReference, Map<MethodReference, Double>> entryReachability(final Set<MethodReference> interestingMethods,
																		final List<Entrypoint> entryPoints,
																		AnalysisScope apk_scope,
																		IClassHierarchy cha) throws IOException, RetargetException, CancelException {
		

			AnalysisOptions opts = new AnalysisOptions(apk_scope, entryPoints);
			CallGraphBuilder builder = Util.makeZeroCFABuilder(opts, new AnalysisCache(), cha, apk_scope);
			
			LOGGER.finer("call graph builder initialized");
			CallGraph cg = builder.makeCallGraph(opts, null);
			LOGGER.fine("call graph created: " + CallGraphStats.getStats(cg));
			
			
		
		return callGraphReachability(interestingMethods, cha, cg);
	}
	
	class CacheOMite {
		@SuppressWarnings("unchecked")
		public <V> V get(String key, int verHash) throws IOException {
			File f = new File(mCachePath + File.separator + key);
			
			if (!f.exists()) return null;
			
			ObjectInputStream s = new ObjectInputStream(new FileInputStream(f));
			
			int fileVer = s.readInt();
			
			if (fileVer != verHash) return null;
			
			try {
				Object val = s.readObject();
				return (V)val;
			} catch (ClassNotFoundException e) {
				return null;
			} finally {
				s.close();
			}
			
		}
		public <V> void put(String key, V value, int verHash) throws IOException {
			File f = new File(mCachePath + File.separator + key);
			
			ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(f));
			
			s.writeInt(verHash);
			s.writeObject(value);
			
			s.close();
		}
	}
	
	static class InterestingReachabilityStringResult implements Serializable {
		private static final long serialVersionUID = 6480634977100474782L;
		
		public HashMap<String, HashSet<String>> mInterestingSites;
		public HashMap<String, HashMap<String, Double>> mEntryReachability;
		
		public InterestingReachabilityStringResult() {
			mInterestingSites = new HashMap<String, HashSet<String>>();
			mEntryReachability = new HashMap<String, HashMap<String, Double>>();
		}
		
		public InterestingReachabilityStringResult(InterestingReachabilityResult result) {
			this();
			
			for (Map.Entry<MethodReference, Set<MethodReference>> e: result.mInterestingSites.entrySet()) {
				HashSet<String> theSet = new HashSet<String>();
				
				for (MethodReference meth: e.getValue()) {
					theSet.add(meth.getSignature());
				}
				
				mInterestingSites.put(e.getKey().getSignature(), theSet);
			}
			
			for (Map.Entry<MethodReference, Map<MethodReference, Double>> e: result.mEntryReachability.entrySet()) {
				HashMap<String, Double> theMap = new HashMap<String, Double>();
				
				for (Map.Entry<MethodReference, Double> m: e.getValue().entrySet()) {
					theMap.put(m.getKey().getSignature(), m.getValue());
				}
				
				mEntryReachability.put(e.getKey().getSignature(), theMap);
			}
		}
		
		public void dump() {
			for (Map.Entry<String, HashMap<String, Double>> e: mEntryReachability.entrySet()) {
				System.out.println(e.getKey() + " :: " + e.getValue().keySet().toString());
			}
		}
	}

	class InterestingReachabilityResult {
		public Map<MethodReference, Set<MethodReference>> mInterestingSites;
		public Map<MethodReference, Map<MethodReference, Double>> mEntryReachability;
		
		public InterestingReachabilityResult(Map<MethodReference, Set<MethodReference>> interestingSites, Map<MethodReference, Map<MethodReference, Double>> entryReachability) {
			mInterestingSites = interestingSites;
			mEntryReachability = entryReachability;
		}
	}
	
	public InterestingReachabilityResult interestingReachability() throws IOException, RetargetException {
		try {
			AnalysisScope apk_scope = this.getWalaScope();
			IClassHierarchy cha = ClassHierarchy.make(apk_scope);
			
			Map<MethodReference, Set<MethodReference>> interestingSites = this.interestingCallSites(Interesting.sInterestingMethods, cha);
			Map<MethodReference, Map<MethodReference, Double>> entryReachability = this.entryReachability(Interesting.sInterestingMethods, 
																								  getEntrypoints(cha),
																								  apk_scope, cha);
			
			return new InterestingReachabilityResult(interestingSites, entryReachability);
		} catch (Exception e ) {
			System.err.println(e);
			return null;
		}
	}
	
	static class ReachabilityPredicates {
		static private Set<String> sAcquireRelease = new HashSet<String>();
		static private Set<String> sTimedAcquire = new HashSet<String>();
		
		static final String sAcquireSig = "android.os.PowerManager$WakeLock.acquire()V";
		static final String sReleaseSig = "android.os.PowerManager$WakeLock.release()V";
		
		static final String sTimedAcquireSig = "android.os.PowerManager$WakeLock.acquire(J)V";
		
		static {
			sAcquireRelease.add(sAcquireSig);
			sAcquireRelease.add(sReleaseSig);
			
			sTimedAcquire.add(sTimedAcquireSig);
		}
		
		InterestingReachabilityStringResult mInteresting;
		
		public ReachabilityPredicates(InterestingReachabilityStringResult interesting) {
			mInteresting = interesting;
		}
		
		public boolean onlyAcquireRelease() {
			return sAcquireRelease.equals(mInteresting.mEntryReachability.keySet());
		}
		
		public boolean onlyTimedAcquire() {
			return sTimedAcquire.equals(mInteresting.mEntryReachability.keySet());
		}
		
		public boolean onlyAcquire() {
			return mInteresting.mEntryReachability.size() == 1 && mInteresting.mEntryReachability.keySet().contains(sAcquireSig);
		}
		
		public boolean onlyMultimedia() {
			if (mInteresting.mEntryReachability.size() == 0) return false;
			
			for (String s: mInteresting.mEntryReachability.keySet()) {
				if (! s.startsWith("android.media.MediaPlayer")) return false;
			}
			return true;
		}
		
		public boolean onlyCreateDestroy() {
			Set<String> acquireSet = mInteresting.mEntryReachability.get(sAcquireSig).keySet();
			Set<String> releaseSet = mInteresting.mEntryReachability.get(sReleaseSig).keySet();

			return acquireSet.size() == 1 && 
					(acquireSet.iterator().next().endsWith("onCreate(Landroid/os/Bundle;)V") || 
					 acquireSet.iterator().next().endsWith("onCreate()V") ) &&
				   releaseSet.size() == 1 && releaseSet.iterator().next().endsWith("onDestroy()V"); 
		}
		
		public boolean onlyCreateStop() {
			Set<String> acquireSet = mInteresting.mEntryReachability.get(sAcquireSig).keySet();
			Set<String> releaseSet = mInteresting.mEntryReachability.get(sReleaseSig).keySet();

			return acquireSet.size() == 1 && 
					(acquireSet.iterator().next().endsWith("onCreate(Landroid/os/Bundle;)V") || 
					 acquireSet.iterator().next().endsWith("onCreate()V") ) &&
				   releaseSet.size() == 1 && releaseSet.iterator().next().endsWith("onStop()V"); 
		}
		
		public boolean onlyStartDestroy() {
			Set<String> acquireSet = mInteresting.mEntryReachability.get(sAcquireSig).keySet();
			Set<String> releaseSet = mInteresting.mEntryReachability.get(sReleaseSig).keySet();

			return acquireSet.size() == 1 && 
					(acquireSet.iterator().next().endsWith("onStart(Landroid/content/Intent;I)V")) &&
				   releaseSet.size() == 1 && releaseSet.iterator().next().endsWith("onDestroy()V"); 
		}
		
		public boolean onlyPauseResume() {
			Set<String> acquireSet = mInteresting.mEntryReachability.get(sAcquireSig).keySet();
			Set<String> releaseSet = mInteresting.mEntryReachability.get(sReleaseSig).keySet();
			
			return acquireSet.size() == 1 && acquireSet.iterator().next().endsWith("onResume()V") &&
				   releaseSet.size() == 1 && releaseSet.iterator().next().endsWith("onPause()V"); 
		}
	}
	
	public enum BugLikely {
		DEFINITELY, PROBABLY, MAYBE, UNDETERMINED, UNLIKELY
	}
	
	public enum UsageType {
		CREATE_DESTROY(BugLikely.UNLIKELY), 
		PAUSE_RESUME(BugLikely.UNLIKELY), 
		START_DESTROY(BugLikely.UNLIKELY), 
		CREATE_STOP(BugLikely.PROBABLY),
		ONLY_TIMED_ACQUIRE(BugLikely.UNLIKELY), ONLY_MULTIMEDIA(BugLikely.MAYBE), 
		ONLY_ACQUIRE(BugLikely.DEFINITELY),
		UNKNOWN(BugLikely.UNDETERMINED), 
		CONVERSION_FAILURE(BugLikely.UNDETERMINED), FAILURE(BugLikely.UNDETERMINED);
		
		public BugLikely bugLikely;
		
		private UsageType(BugLikely likely) { bugLikely = likely; }

	}
	
	public UsageType analyzeReachability(InterestingReachabilityStringResult interestingStr) {
		/* 
		 * XXX Need to fix call graph creation.  SimpleTime is missing the acquire call
		 */
		ReachabilityPredicates p = new ReachabilityPredicates(interestingStr);
		
		if (p.onlyAcquireRelease()) {
			if (p.onlyCreateDestroy()) {
				return UsageType.CREATE_DESTROY;
			} else if (p.onlyPauseResume()) {
				return UsageType.PAUSE_RESUME;
			} else if (p.onlyStartDestroy()) {
				return UsageType.START_DESTROY;
			} else if (p.onlyCreateStop()) {
				return UsageType.CREATE_STOP;
			}
			interestingStr.dump();
		} else if (p.onlyMultimedia()) {
			return UsageType.ONLY_MULTIMEDIA;
		} else if (p.onlyTimedAcquire()) {
			return UsageType.ONLY_TIMED_ACQUIRE;
		} else if (p.onlyAcquire()) {
			return UsageType.ONLY_ACQUIRE;
		} else {
			//System.out.println(interestingStr.mInterestingSites.keySet());
			interestingStr.dump();
		}
		return UsageType.UNKNOWN;
	}
	
	public UsageType analyze() throws IOException, RetargetException {
		try {
			CacheOMite cache = new CacheOMite();
			UsageType result = UsageType.FAILURE;
			
			final int ver = Interesting.activityEntryMethods.hashCode() +
							Interesting.serviceEntryMethods.hashCode() +
							Interesting.broadcastReceiverEntryMethods.hashCode() + 3;
			
			InterestingReachabilityStringResult interestingStr = cache.get("interesting", ver);
			
			if (interestingStr == null) {
				InterestingReachabilityResult interesting = interestingReachability();
				if (interesting == null) throw new RetargetException("Interesting reachability null");
				
				interestingStr = new InterestingReachabilityStringResult(interesting);
				result = analyzeReachability(interestingStr);
				cache.put("interesting", interestingStr, ver);
			} else {
				result = analyzeReachability(interestingStr);
			}
			
			return result;
			
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("analyze exception: " + e);
			return UsageType.FAILURE;
		} catch (Error e) {
			e.printStackTrace();
			System.err.println("analyze error: " + e);
			return UsageType.FAILURE;
		}
	}

	public Set<String> panosAnalyze() throws IOException, WalaException, CancelException, ApkException {
		
		String appJar = mPath.getAbsolutePath();

		//TODO: Put this somewhere else
		String exclusionFile = "/home/pvekris/dev/workspace/WALA_shared/" +
				"com.ibm.wala.core.tests/bin/Java60RegressionExclusions.txt";						
		
		energy.util.Util.setResultDirectory(mPath.getAbsolutePath());
		energy.util.Util.printLabel(mPath.getAbsolutePath());		
		
		ApplicationClassHierarchy	ch = new ApplicationClassHierarchy(appJar, exclusionFile);		

		
		
		
		ApplicationCallGraph 		cg = new ApplicationCallGraph(ch);		
		
		Set<String> result = new HashSet<String>();

		if (Opts.PROCESS_ANDROID_COMPONENTS) {
			
			ComponentManager componentManager = new ComponentManager(cg);
			
			componentManager.prepareReachability();
			
			componentManager.resolveComponents();
			
			AnalysisResults results = componentManager.processComponents();
			
			results.processResults();
			
			results.outputFinalResults();
			
			
			
		}
		
		return result;
	}
}
