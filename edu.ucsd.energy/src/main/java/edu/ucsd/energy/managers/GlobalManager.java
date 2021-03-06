package edu.ucsd.energy.managers;

import java.io.File;

import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;

import edu.ucsd.energy.analysis.Wala;
import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.apk.ClassHierarchyUtils;
import edu.ucsd.energy.component.Component;
import edu.ucsd.energy.conditions.SpecialConditions;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.util.GraphUtils;
import edu.ucsd.energy.util.Log;
import edu.ucsd.energy.util.SystemUtil;

public class GlobalManager {

	private String appJar;
	private ClassHierarchy ch;
	private AppCallGraph cg;
	private ComponentManager cm;
	private WakeLockManager wakeLockManager;
	private IntentManager intentManager;
	private RunnableManager runnableManager;
	private SpecialConditions specialConditions;
	private SparseNumberedGraph<Component> constraintGraph; 
	
	
	private static ThreadLocal<GlobalManager> threadGM = new ThreadLocal<GlobalManager>();
	
	public static GlobalManager get() {
		GlobalManager global = threadGM.get();
		if (global == null) {
			File mPath = Wala.mPath.get();
			String absolutePath = mPath.getAbsolutePath();
			SystemUtil.setResultDirectory(absolutePath);
			threadGM.set(new GlobalManager(absolutePath));
		}
		return threadGM.get();
	}
	
	
	private GlobalManager(String appJar) {
		this.appJar = appJar;
	}

	public ClassHierarchy getClassHierarchy() {
		if (ch == null) {
			try {
				ch = ClassHierarchyUtils.make(appJar);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return ch;
	}
	
	public void createComponentManager() {
		cm = new ComponentManager();
		if (ComponentManager.RESOLVE_DEBUG > 0) {
			Log.timeln("Resolving Components ... ");
		}
		cm.resolveComponents();
		if (ComponentManager.RESOLVE_DEBUG > 0) {
			Log.timeln("Resolved Components ... ");
		}
		
	}

	/**
	 * This will demand information gathered by the runnable and wakelock managers
	 */
	public void solveComponents() {
		cm.solveComponents();
	}

	
	public void createWakeLockManager() {
		wakeLockManager = new WakeLockManager();
		wakeLockManager.prepare();
	}

	public void createIntentManager() {
		intentManager = new IntentManager();
		intentManager.prepare();
	}

	public void createRunnableManager() {
		runnableManager = new RunnableManager();
		runnableManager.prepare();
		//runnableManager.computeConstraintGraph();
		//runnableManager.dumpConstraintGraph();
	}
	
	public void createSpecialConditions() {
		specialConditions = new SpecialConditions();
		specialConditions.prepare();
	}
	

	public IReport getWakeLockReport() {
		return wakeLockManager.getReport();
	}

	public IReport getIntentReport() {
		if (intentManager == null) {
			createIntentManager();
		}
		return intentManager.getReport();
	}

	public IReport getAnalysisReport() {
		return cm.getAnalysisResults();
	}

	public AppCallGraph getAppCallGraph() {
		if (cg == null) {
			try {
				cg = new AppCallGraph(getClassHierarchy());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return cg;
	}

	public WakeLockManager getWakeLockManager() {
		if (wakeLockManager == null) {
			createWakeLockManager();
		}
		return wakeLockManager;
	}

	public SpecialConditions getSpecialConditions() {
		if (specialConditions == null) {
			createSpecialConditions();
		}
		return specialConditions;
	}

	public ComponentManager getComponentManager() {
		if (cm == null) {
			createComponentManager();
		}
		return cm;
	}

	public IReport getRunnableReport() {
		if (runnableManager == null) {
			createRunnableManager();
		}
		return runnableManager.getReport();
	}

	public RunnableManager getRunnableManager() {
		if (runnableManager == null) {
			createRunnableManager();
		}
		return runnableManager;
	}

	public IntentManager getIntentManager() {
		if (intentManager == null) {
			createIntentManager();
		}
		return intentManager;
		
	}

	/**
	 * BE CEREFUL - THIS NEEDS TO BE DONE IN PARALLEL MODE
	 * Reset the global manager as it might be used by another thread
	 * in the thread pool later
	 */
	public void reset() {
		threadGM.set(null);		
	}


	public SparseNumberedGraph<Component> getConstraintGraph() {
		if (constraintGraph == null) {
			constraintGraph = GraphUtils.merge(getRunnableManager().getConstraintGraph(),getIntentManager().getConstraintGraph());
			GraphUtils.dumpConstraintGraph(constraintGraph , "all_constraints");
		}
		return constraintGraph;
	}
	
	
}

