/**
 * 
 */
package edu.ucsd.salud.mcmutton.retarget;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import edu.ucsd.salud.mcmutton.ApkInstance;
import edu.ucsd.salud.mcmutton.RetargetException;
import edu.ucsd.salud.mcmutton.SystemUtil;
import edu.ucsd.salud.mcmutton.apk.ApkPaths;

public class SootOptimize implements JavaOptimize {
	protected ApkPaths mPaths;
	protected Callable<Integer> mFetchAndroidVersion;
	
	public SootOptimize(ApkPaths paths, Callable<Integer> fetchAndroidVersion) {
		mPaths = paths;
		mFetchAndroidVersion = fetchAndroidVersion;
	}
	public void optimize(File retargetedTarget) throws IOException, RetargetException {
			File optimizedTarget = getTarget();
			int androidVersion = 10;
			
			try {
				androidVersion = mFetchAndroidVersion.call();
			} catch (Exception e) {
				throw new RetargetException("Error extracting android version " + e);
			}
			 
			optimizedTarget.mkdirs();
			
			ApkInstance.LOGGER.info("Attempting to optimize java file " + retargetedTarget + " -> " + optimizedTarget);
			
			String java_classpath[] = {
					mPaths.dedPath + "/soot/soot-2.3.0/classes",
					mPaths.dedPath + "/soot/polyglot-1.3.5/classes",
					mPaths.dedPath + "/soot/polyglot-1.3.5/cup-classes",
					mPaths.dedPath + "/soot/jasmin-2.3.0/classes",
					mPaths.getAndroidJar(androidVersion).getAbsolutePath()
			};
			
			String classpath[] = {
				retargetedTarget.getAbsolutePath(),

				"/usr/lib/jvm/java-6-sun-1.6.0.26/jre/lib/rt.jar",
				mPaths.getAndroidJar(androidVersion).getAbsolutePath(),
				"/usr/share/java/servlet-api-2.4.jar",
				mPaths.getGoogleMapsJar(androidVersion).getAbsolutePath(),
				mPaths.dedPath + "/android-libs/jzlib-1.1.1.jar",
				mPaths.dedPath + "/android-libs/admob-sdk-android.jar"
			};
			

			String cmd[] = {
					"/usr/bin/java",
					"-cp",
					StringUtils.join(java_classpath, ":"),
				    "soot.Main",
				    "-cp",
				    StringUtils.join(classpath, ":"),
				    "-O",
				    "-process-dir", retargetedTarget.getAbsolutePath(),
				    "-d", optimizedTarget.getAbsolutePath(),
					"-pp",
					"-allow-phantom-refs",
					"-p", "db", "source-is-javac:true",
					"-p", "bb.lso", "sll:false",
					"-p", "db.transformations", "off",
					"-p", "db.force-recompile", "off"
			};
			
			ApkInstance.LOGGER.info("optimize cmd: " + StringUtils.join(cmd, " "));
			
			getTarget().mkdirs();
			SystemUtil.runCommand(cmd, this.getLogTarget(), this.getErrTarget(), this.getSuccessTarget(), new File("/home/pvekris/dev/ded"));
	}
	
	public void clean() {
		if (getTarget().exists()) {
			try {
				FileUtils.deleteDirectory(getTarget());
			} catch (IOException e) {
				System.err.println("Error deleteing directory " + getTarget() + ": " + e);
			}
		}
		
		if (getJarTarget().exists()) {
			getJarTarget().delete();
		}
		
		if (getSuccessTarget().exists()) {
			getSuccessTarget().delete();
		}
		
		if (getLogTarget().exists()) {
			getLogTarget().delete();
		}
		
		if (getErrTarget().exists()) {
			getErrTarget().delete();
		}
	}
	
	public File getTarget() {
		return mPaths.dedOptimized;
	}
	
	public File getJarTarget() {
		return mPaths.dedOptimizedJar;
	}

	public boolean attempted() {
		return this.getLogTarget().exists();
	}

	public boolean success() {
		return this.getSuccessTarget().exists();
	}

	public File getErrTarget() {
		return mPaths.dedOptimizedErrLogTarget;
	}

	public File getLogTarget() {
		return mPaths.dedOptimizedErrLogTarget;
	}
	
	public File getSuccessTarget() {
		return mPaths.dedOptimizedSuccess;
	}
}