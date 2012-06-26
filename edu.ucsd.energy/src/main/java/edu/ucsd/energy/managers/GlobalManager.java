package edu.ucsd.energy.managers;

import com.ibm.wala.ipa.cha.ClassHierarchy;

import edu.ucsd.energy.apk.AppCallGraph;
import edu.ucsd.energy.apk.ClassHierarchyUtils;
import edu.ucsd.energy.conditions.SpecialConditions;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.ViolationReport;

public class GlobalManager {

	private String appJar;
	private String excludionFile;
	private ClassHierarchy ch;
	private AppCallGraph cg;
	private ComponentManager cm;
	private WakeLockManager wakeLockManager;
	private IntentManager intentManager;
	private RunnableManager runnableManager;
	private SpecialConditions specialConditions; 
	
	public GlobalManager(String appJar, String exclusionFile) {
		this.appJar = appJar;
		this.excludionFile = exclusionFile;		
	}

	public ClassHierarchy getClassHierarchy() {
		if (ch == null) {
			try {
				ch = ClassHierarchyUtils.make(appJar, excludionFile);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return ch;
	}
	
	public void createComponentManager() {
		cm = new ComponentManager(this);
		cm.prepareReachability();
		cm.resolveComponents();
	}

	/**
	 * This will demand information gathered by the runnable and wakelock managers
	 */
	public void solveComponents() {
		cm.solveComponents();
	}

	
	public void createWakeLockManager() {
		wakeLockManager = new WakeLockManager(this);
		wakeLockManager.prepare();
	}

	public void createIntentManager() {
		intentManager = new IntentManager(this);
		intentManager.prepare();
	}

	public void createRunnableManager() {
		runnableManager = new RunnableManager(this);
		runnableManager.prepare();
		//runnableManager.computeConstraintGraph();
		//runnableManager.dumpConstraintGraph();
	}
	
	public void createSpecialConditions() {
		specialConditions = new SpecialConditions(this);
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

	public ViolationReport getAnalysisReport() {
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
	
}

