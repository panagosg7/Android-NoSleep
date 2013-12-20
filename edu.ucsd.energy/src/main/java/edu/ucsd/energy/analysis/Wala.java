package edu.ucsd.energy.analysis;

import java.io.File;
import java.io.IOException;

import org.json.JSONException;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.results.CompoundReport;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.util.SystemUtil;

public class Wala {
	private File mPath;
	
	/**
	 * Create an instance 
	 * @param path: the path of the jar file of the app 
	 */
	public Wala(File path) {
		mPath = path;
	}

	public IReport wakelockAnalyze() throws IOException, WalaException, CancelException, JSONException {
		String appJar = mPath.getAbsolutePath();
		//TODO: fix this
		String exclusionFile = "/home/pvekris/dev/workspace/WALA_shared/com.ibm.wala.core.tests/bin/Java60RegressionExclusions.txt";						
		SystemUtil.setResultDirectory(mPath.getAbsolutePath());
		
		edu.ucsd.energy.util.Util.printLabel(mPath.getAbsolutePath());	
		
		GlobalManager gm = new GlobalManager(appJar, exclusionFile);
		gm.createComponentManager();
		
		gm.createWakeLockManager();
		gm.createIntentManager();
		gm.createRunnableManager();
		
		CompoundReport report = new CompoundReport();
		report.register(gm.getIntentReport());
		report.register(gm.getRunnableReport());
		report.register(gm.getWakeLockReport());
		return report;
	}

	
	public IReport analyzeFull() throws IOException, WalaException, CancelException {
		String appJar = mPath.getAbsolutePath();
		//TODO: fix this
		String exclusionFile = "/home/pvekris/dev/workspace/WALA_shared/com.ibm.wala.core.tests/bin/Java60RegressionExclusions.txt";						
		SystemUtil.setResultDirectory(mPath.getAbsolutePath());
		if(!Opts.RUN_IN_PARALLEL) {
			edu.ucsd.energy.util.Util.printLabel(mPath.getAbsolutePath());	
		}
		GlobalManager gm = new GlobalManager(appJar, exclusionFile);
		gm.createComponentManager();
		
		gm.createWakeLockManager();
		gm.createSpecialConditions();
		
		//Component Manager stuff		
		gm.createIntentManager();
		gm.createRunnableManager();

		//Perform the data flow
		gm.solveComponents();
		
		CompoundReport report = new CompoundReport();
//		report.register(gm.getIntentReport());
//		report.register(gm.getRunnableReport());
//		report.register(gm.getWakeLockReport());
		report.register(gm.getAnalysisReport());

		return report;
	}
	
	public IReport analyzeUsage() throws IOException, WalaException, CancelException {
		String appJar = mPath.getAbsolutePath();
		//TODO: fix this
		String exclusionFile = "/home/pvekris/dev/workspace/WALA_shared/com.ibm.wala.core.tests/bin/Java60RegressionExclusions.txt";						
		SystemUtil.setResultDirectory(mPath.getAbsolutePath());
		if(!Opts.RUN_IN_PARALLEL) {
			edu.ucsd.energy.util.Util.printLabel(mPath.getAbsolutePath());	
		}
		GlobalManager gm = new GlobalManager(appJar, exclusionFile);
		gm.createComponentManager();
		
		gm.createWakeLockManager();
		gm.createSpecialConditions();
		
		//Component Manager stuff

		gm.solveComponents();
		
		CompoundReport report = new CompoundReport();

		return report;
	}
	
}
