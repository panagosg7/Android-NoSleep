/**
 * 
 */
package edu.ucsd.energy.retarget;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import edu.ucsd.energy.RetargetException;
import edu.ucsd.energy.apk.ApkInstance;
import edu.ucsd.energy.apk.ApkPaths;

public class D2jConverter implements DexConverter {
	private ApkPaths mPaths;
	
	public D2jConverter(ApkPaths paths) {
		mPaths = paths;
	}
	
	public void convert() throws IOException, RetargetException {
		File target = this.getJarTarget();
		File source = mPaths.apk;
	
		ApkInstance.LOGGER.info("Attempting to d2j retarget to " + target);
		
		target.getParentFile().mkdirs();
		
		try {
			com.googlecode.dex2jar.v3.Main.doFile(source, target);
		} catch (Exception e) {
			PrintWriter w = new PrintWriter(mPaths.d2jErrLogTarget);
			e.printStackTrace(w);
			w.close();
			
			throw new RetargetException(e.toString());
		}
		
		PrintWriter w = new PrintWriter(mPaths.d2jSuccess);
		w.print(0);
		w.close();
		
		ApkInstance.LOGGER.info("Conversion Successful!");
	}

	public File getTarget() {
		return null;
	}

	public boolean conversionSuccess() {
		return mPaths.d2jSuccess.exists();
	}

	public File getJarTarget() {
		return mPaths.d2jRetargetedJar;
	}

	public File getErrTarget() {
		return mPaths.d2jErrLogTarget;
	}

	public File getLogTarget() {
		return mPaths.d2jLog;
	}
}