package edu.ucsd.salud.mcmutton.apk;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

public class ApkPaths {
	public final File basePath;
	public final File workPath;
	public final File androidPath;
	public final File dedPath;
	
	public final File apk;
	
	public final File extractedPath;
	public final File dexPath;
	public final File manifestPath;
	/** See if values-en exists in case the default isn't english */
	public final File manifestStringsEnPath;
	public final File manifestStringsPath;
	public final File info;
	public final File smali;
	
	public final File ded;
	public final File dedSuccess;
	public final File d2jSuccess;
	public final File dedLog;
	public final File dedErrLogTarget;
	
	public final File d2j;
	public final File d2jLog;
	public final File d2jErrLogTarget;
	
	public final File dedOptimizedLogTarget;
	public final File dedOptimizedErrLogTarget;
	
	public final File d2jOptimizedLogTarget;
	public final File d2jOptimizedErrLogTarget;
	
	public final File dedOptimizedSuccess;
	public final File d2jOptimizedSuccess;
	
	public final File dedRetargetedBase;
	public final File dedRetargeted;
	public final File dedRetargetedJar;
	public final File dedOptimizedBase;
	public final File dedOptimized;
	public final File dedOptimizedJar;
	public final File d2jOptimized;
	public final File d2jRetargetedJar;
	public final File d2jOptimizedJar;
	
	public final File walaCache;
	public final File hasWakelockCallsCache;
	
	
	
	public ApkPaths(File baseApkPath, File workPathBase, File dedPathBase, File androidSdkBase) throws IOException {
		basePath = baseApkPath;
		workPath = workPathBase;
		dedPath = dedPathBase;
		androidPath = androidSdkBase;
		
		
		/* See if it is an actual apk, else grab the first thing that looks like an apk */
		if (baseApkPath.getName().endsWith("apk")) {
			apk = baseApkPath;
		} else {
			File[] apks = basePath.listFiles(new FilenameFilter() {
				public boolean accept(File arg0, String arg1) {
					if (arg1.endsWith(".apk")) return true;
					else return false;
				}
			});
			
			if (apks == null || apks.length == 0) {
				throw new IOException("apk not found in " + basePath);
			}
			
			apk = apks[0];
		}
		
		
		extractedPath = new File(workPath + File.separator + "extracted");
		dexPath = new File(extractedPath + File.separator + "classes.dex");
		manifestPath = new File(extractedPath + File.separator + "AndroidManifest.xml");
		manifestStringsEnPath = new File(extractedPath + File.separator + "res" + File.separator + "values-en" + File.separator + "strings.xml");
		manifestStringsPath = new File(extractedPath + File.separator + "res" + File.separator + "values" + File.separator + "strings.xml");
		
		info = new File(workPath + File.separator + "info.json");
		smali = new File(extractedPath + File.separator + "smali");
		
		ded = new File(workPath + File.separator + "ded");
		dedSuccess = new File(ded + File.separator + "ded_success");
		dedLog = new File(ded + File.separator + "ded.log");
		dedErrLogTarget = new File(ded + File.separator + "ded_err.log");
		
		dedOptimizedLogTarget = new File(ded + File.separator + "opt.log");
		dedOptimizedErrLogTarget = new File(ded + File.separator + "opt_err.log");
		

		dedOptimizedSuccess = new File(ded + File.separator + "ded_optimized_success");

		String apkName = apk.getName().replaceAll("[.]apk$", "");
		
		dedRetargetedBase = new File(ded + File.separator + "retargeted");
		dedOptimizedBase = new File(ded + File.separator + "retargeted");
		
		dedRetargeted = new File(ded + File.separator + "retargeted" + File.separator + apkName);
		dedOptimized = new File(ded + File.separator + "optimized" + File.separator + apkName);
		
		
		dedRetargetedJar = new File(ded + File.separator + "retargeted.jar");
		dedOptimizedJar = new File(ded + File.separator + "optimized.jar");
		
		d2j = new File(workPath + File.separator + "d2j");
		d2jRetargetedJar = new File(d2j + File.separator + "retargeted.jar");
		d2jSuccess = new File(d2j + File.separator + "d2j_success");
		d2jLog = new File(d2j + File.separator + "d2j.log");
		d2jErrLogTarget = new File(d2j + File.separator + "d2j-err.log");
		d2jOptimizedJar = new File(d2j + File.separator + "optimized-d2j.jar");
		d2jOptimized = new File(d2j + File.separator + apkName);

		d2jOptimizedSuccess = new File(d2j + File.separator + "d2j_optimized_success");
		d2jOptimizedLogTarget = new File(d2j + File.separator + "d2j-opt.log");
		d2jOptimizedErrLogTarget = new File(d2j + File.separator + "d2j-opt_err.log");
		
		walaCache = new File(workPath + File.separator + "wala.cache");
		walaCache.mkdirs();
		
		hasWakelockCallsCache = new File(workPath + File.separator + "hasWakelockCalls");
	}
	
	public File getAndroidJar(int androidVersion) {
		if (androidVersion < 0) {
			return new File(dedPath + "/android-libs/android.jar");
		} else {
			return new File(androidPath + "/platforms/android-" + androidVersion + "/android.jar");
		}
	}
	public File getGoogleMapsJar(int androidVersion) {
		int useVersion = androidVersion;
		if (androidVersion < 0) {
			useVersion = 11;
		}
		return new File(androidPath + "/add-ons/addon-google_apis_google_inc_" + useVersion + "/libs/maps.jar");		
	}
}
