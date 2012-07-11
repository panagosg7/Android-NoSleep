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

public class Wala {

	public static ThreadLocal<File> mPath = new ThreadLocal<File>();
	
	public Wala(File path, File androidJarPath, File cachePath) {
		mPath.set(path);
	}

	public IReport wakelockAnalyze() throws IOException, WalaException, CancelException, ApkException, JSONException {
		GlobalManager gm = GlobalManager.get();
		gm.createComponentManager();
		gm.createWakeLockManager();
		gm.createIntentManager();
		gm.createRunnableManager();
		CompoundReport report = new CompoundReport();
		report.register(gm.getIntentReport());
		report.register(gm.getRunnableReport());
		report.register(gm.getWakeLockReport());
		//finalize
		gm.reset();		
		return report;
	}

	
	public IReport analyzeFull() throws IOException, WalaException, CancelException, ApkException {
		GlobalManager gm = GlobalManager.get();						
		gm.createComponentManager();
		gm.createWakeLockManager();
		gm.createSpecialConditions();
		//Component Manager stuff
		gm.createIntentManager();
		gm.createRunnableManager();
		//Perform the data flow
		gm.solveComponents();
		CompoundReport report = new CompoundReport();
		report.register(gm.getIntentReport());
		report.register(gm.getRunnableReport());
//		report.register(gm.getWakeLockReport());
		report.register(gm.getAnalysisReport());
		gm.reset();
		return report;
	}
	
	public IReport analyzeUsage() throws IOException, WalaException, CancelException, ApkException {
		GlobalManager gm = GlobalManager.get();
		gm.createComponentManager();
		gm.createWakeLockManager();
		gm.createSpecialConditions();
		//Component Manager stuff
		gm.solveComponents();
		CompoundReport report = new CompoundReport();
		//finalize
		gm.reset();
		return report;
	}
	
}
