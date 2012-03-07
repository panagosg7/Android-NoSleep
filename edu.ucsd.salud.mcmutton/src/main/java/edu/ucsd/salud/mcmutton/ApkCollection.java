package edu.ucsd.salud.mcmutton;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.ucsd.salud.mcmutton.apk.ConfigurationException;
import edu.ucsd.salud.mcmutton.apk.Util;

public class ApkCollection {
	private File mCollectionRoot = new File("/home/jcm/working/apk-crawl.hg/collection");
	
	/* No caching by default */
	Map<File, ApkInstance> mCacheInstances = null;
	Map<String, List<ApkInstance>> mApkByPermission;
	
	public ApkCollection() throws ConfigurationException {
		loadPaths();
	}
	
	private void loadPaths() throws ConfigurationException {
		try {
			Properties prop = new Properties();
	
			prop.load(new FileInputStream("mcmutton.properties"));
	
			loadPaths(prop);
		} catch (IOException e) {
			throw new ConfigurationException("Error loading configuration file: " + e.toString());
		}
	}
	
	private void loadPaths(Properties prop) throws ConfigurationException {
		mCollectionRoot = Util.getAndCheckConfigPath(prop, "collection_path");
		ApkInstance.loadPaths(prop);
	}
	
	public static String cleanApkName(final String name) {
		/* should match apk-crawl.hg/fix-names.py */
		return name.replaceAll("[()'~]", "").replaceAll("[ -]", "_");
	}
	
	/* Turning caching on can lead to memory leaks */
	public void setCaching(boolean caching) {
		if (caching) {
			if (mCacheInstances == null) {
				mCacheInstances = new HashMap<File, ApkInstance>();
			}
		} else {
			mCacheInstances = null;
		}
	}
	
	List<ApkApplication> listApplications() {
		LinkedList<ApkApplication> list = new LinkedList<ApkApplication>();
		
		for (File sub: mCollectionRoot.listFiles()) {
			list.add(new ApkApplication(sub));
		}
		
		return list;
	}
	
	public ApkApplication getApplication(String name) {
		File f = new File(mCollectionRoot + "/" + name);
		
		if (f.exists()) {
			return new ApkApplication(f);
		} else {
			return null;
		}
	}
	
	ApkInstance getPreferred(String name) throws IOException {
		ApkApplication app = getApplication(name);
		if (app == null) return null;
		else return app.getPreferred();
	}
	
	Map<String, List<ApkInstance>> instancesByPermission() throws IOException {
		if (mApkByPermission == null) {
			 mApkByPermission = new HashMap<String, List<ApkInstance>>();
		
			for (ApkApplication app: listApplications()) {
				try {
					ApkInstance inst = app.getPreferred();
					Set<String> perms = inst.getPermissions();
					
					for (String perm: perms) {
						if (! mApkByPermission.containsKey(perm)) {
							mApkByPermission.put(perm, new LinkedList<ApkInstance>());
						}
						mApkByPermission.get(perm).add(inst);
					}
				} catch (FailedManifestException e) {
					// hmm
				}
			}
		}
		return mApkByPermission;
	}
	
	public class ApkApplication {
		private File mPath;
		List<ApkVersion> mVersions;
		
		public ApkApplication(File path) {
			mPath = path;
		}
		
		List<ApkVersion> listVersions() {
			if (mVersions == null) {
				ArrayList<ApkVersion> list = new ArrayList<ApkVersion>();
				
				for (File sub: mPath.listFiles()) {
					list.add(new ApkVersion(sub));
				}
				
				Collections.sort(list);
				
				mVersions = list;
			}
			return mVersions;
		}
		
		public final String getName() {
			return mPath.getName();
		}
		
		public ApkInstance getPreferred() throws IOException {
			List<ApkVersion> vers = listVersions();
			return vers.get(vers.size()-1).getPreferred();
		}
		
		public ApkVersion getVersion(String version) {
			File f = new File(mPath + "/" + version);
			
			if (f.exists()) {
				return new ApkVersion(f);
			} else {
				return null;
			}
		}
		
	}
	
	public class ApkVersion implements Comparable<ApkVersion> {
		private File mPath;
		
		public ApkVersion(File path) {
			mPath = path;
		}
		
		List<ApkSource> listSources() {
			ArrayList<ApkSource> list = new ArrayList<ApkSource>();
			
			for (File sub: mPath.listFiles()) {
				list.add(new ApkSource(sub));
			}
			
			return list;
		}
		
		public final String getVersion() {
			return mPath.getName();
		}
		
		public ApkInstance getPreferred() throws IOException {
			return listSources().get(0).getPreferred();
		}

		public int compareTo(ApkVersion o) {
			return mPath.getName().compareTo(o.mPath.getName());
		}
		
		public String toString() {
			return mPath.getName();
		}
	}
	
	public class ApkSource {
		private File mPath;
		
		public ApkSource(File path) {
			mPath = path;
		}
		
		public final String getSource() {
			return mPath.getName();
		}
		
		public final File getCollectionRelativePath() {
			return new File(mPath.getAbsolutePath().substring(ApkCollection.this.mCollectionRoot.getAbsolutePath().length() + 1));
		}
		
		public ApkInstance getPreferred() throws IOException {
			if (ApkCollection.this.mCacheInstances != null) {
				if (!ApkCollection.this.mCacheInstances.containsKey(mPath)) {
					ApkCollection.this.mCacheInstances.put(mPath, new ApkInstance(mPath, this.getCollectionRelativePath()));
				}
				return ApkCollection.this.mCacheInstances.get(mPath);
			} else {
				return new ApkInstance(mPath, this.getCollectionRelativePath());
			}
		}
	}
}
