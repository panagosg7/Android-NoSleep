package edu.ucsd.energy.analysis;

import java.io.File;
import java.io.IOException;

import org.json.JSONException;

import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;

import edu.ucsd.energy.ApkException;
import edu.ucsd.energy.managers.GlobalManager;
import edu.ucsd.energy.results.CompoundReport;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.util.SystemUtil;

public class Wala {
	private File mPath;
	
	
	public Wala(File path, File androidJarPath, File cachePath) {
		mPath = path;
	}

	public IReport wakelockAnalyze() throws IOException, WalaException, CancelException, ApkException, JSONException {
		String appJar = mPath.getAbsolutePath();
		String exclusionFile = "/home/pvekris/dev/workspace/WALA_shared/" +
				"com.ibm.wala.core.tests/bin/Java60RegressionExclusions.txt";						
		SystemUtil.setResultDirectory(mPath.getAbsolutePath());
		if(!Opts.RUN_IN_PARALLEL) {
			edu.ucsd.energy.util.Util.printLabel(mPath.getAbsolutePath());	
		}
		GlobalManager gm = new GlobalManager(appJar, exclusionFile);
		gm.createClassHierarchy();
		gm.createAppCallGraph();
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

	
	public IReport analyzeFull() throws IOException, WalaException, CancelException, ApkException {
		String appJar = mPath.getAbsolutePath();
		//TODO: Put this somewhere else
		String exclusionFile = "/home/pvekris/dev/workspace/WALA_shared/" +
				"com.ibm.wala.core.tests/bin/Java60RegressionExclusions.txt";						
		SystemUtil.setResultDirectory(mPath.getAbsolutePath());
		if(!Opts.RUN_IN_PARALLEL) {
			edu.ucsd.energy.util.Util.printLabel(mPath.getAbsolutePath());	
		}
		GlobalManager gm = new GlobalManager(appJar, exclusionFile);
		gm.createClassHierarchy();
		gm.createAppCallGraph();
		gm.createComponentManager();
		
		gm.createWakeLockManager();
		gm.createSpecialConditions();
		
		//Component Manager stuff
		
		gm.createIntentManager();
		gm.createRunnableManager();

		gm.solveComponents();
		
		CompoundReport report = new CompoundReport();
		report.register(gm.getIntentReport());
		report.register(gm.getRunnableReport());
		report.register(gm.getWakeLockReport());
		IReport[] analysisReport = gm.getAnalysisReport();	//meh
		for(int i = 0; i < analysisReport.length; i++) {
			report.register(analysisReport[i]);	
		}
		return report;
	}
	
	public IReport analyzeUsage() throws IOException, WalaException, CancelException, ApkException {
		String appJar = mPath.getAbsolutePath();
		//TODO: Put this somewhere else
		String exclusionFile = "/home/pvekris/dev/workspace/WALA_shared/" +
				"com.ibm.wala.core.tests/bin/Java60RegressionExclusions.txt";						
		SystemUtil.setResultDirectory(mPath.getAbsolutePath());
		if(!Opts.RUN_IN_PARALLEL) {
			edu.ucsd.energy.util.Util.printLabel(mPath.getAbsolutePath());	
		}
		GlobalManager gm = new GlobalManager(appJar, exclusionFile);
		gm.createClassHierarchy();
		gm.createAppCallGraph();
		gm.createComponentManager();
		
		gm.createWakeLockManager();
		gm.createSpecialConditions();
		
		//Component Manager stuff

		gm.solveComponents();
		
		CompoundReport report = new CompoundReport();
		//report.register(gm.getWakeLockReport());
		
		/*
		//Disabling cause I am not solving anything
		IReport[] analysisReport = gm.getAnalysisReport();	//meh
		for(int i = 0; i < analysisReport.length; i++) {
			report.register(analysisReport[i]);	
		}
		*/
		return report;
	}
	
}
