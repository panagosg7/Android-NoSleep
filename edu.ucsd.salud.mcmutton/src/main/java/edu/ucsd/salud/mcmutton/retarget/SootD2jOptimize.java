/**
 * 
 */
package edu.ucsd.salud.mcmutton.retarget;

import java.io.File;

import edu.ucsd.salud.mcmutton.apk.ApkPaths;

public class SootD2jOptimize extends SootOptimize {
	
	public SootD2jOptimize(ApkPaths paths, int androidVersion) {
		super(paths, androidVersion);
	}

	@Override
	public File getTarget() {
		return mPaths.d2jOptimized;
	}
	
	@Override
	public File getJarTarget() {
		return mPaths.d2jOptimizedJar;
	}
	
	@Override
	public File getErrTarget() {
		return mPaths.d2jOptimizedErrLogTarget;
	}

	@Override
	public File getLogTarget() {
		return mPaths.d2jOptimizedLogTarget;
	}
	
	@Override
	public File getSuccessTarget() {
		return mPaths.d2jOptimizedSuccess;
	}
}