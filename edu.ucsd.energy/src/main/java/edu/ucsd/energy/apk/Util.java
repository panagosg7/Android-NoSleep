package edu.ucsd.energy.apk;
//Author: John C. McCullough
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Util {
	public static  Set<String> getClassCollectionFromJarOrDirectory(File target) throws IOException {
		Set<String> classCollection = new HashSet<String>();
		
		if (target.isFile()) {
			JarFile jf = new JarFile(target);
			for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
				JarEntry je = e.nextElement();
				String canonicalName = je.toString().replaceFirst("[$.].*class$", "");
				classCollection.add(canonicalName);
			}
		} else if (target.isDirectory()) {
			/* Depth first search for locality */
			Stack<File> pending = new Stack<File>();
			
			/* Top level starts with something like Facebook_for_Android_1_3_0 */
			pending.add(target);
			
			final int targetLength = target.getAbsolutePath().length() + 1;
			
			while (!pending.empty()) {
				File entry = pending.pop();
				
				if (entry.isFile()) {
					String path = entry.getAbsolutePath().substring(targetLength);
					String canonicalName = path.replaceFirst("[$.].*class$", "");
					classCollection.add(canonicalName);
				} else {
					for (File f: entry.listFiles()) pending.add(f);
				}
			}
		}
		
		return classCollection;
	}
	
	public static File getAndCheckConfigPath(Properties prop, String propertyName) throws ConfigurationException {
		if (!prop.containsKey(propertyName)) {
			throw new ConfigurationException(propertyName + " not defined in property file");
		}
		File path = new File(getPropFromConfig(prop, propertyName));		
		if (!path.exists()) {
			throw new ConfigurationException(propertyName + " " + path.getAbsolutePath() + " does not exist");
		}
		return path;
	}
	
	public static String getPropFromConfig(Properties prop, String propertyName) throws ConfigurationException {
		if (!prop.containsKey(propertyName)) {
			throw new ConfigurationException(propertyName + " not defined in property file");
		}
		return prop.getProperty(propertyName);		
	}
}
