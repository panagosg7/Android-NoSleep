package edu.ucsd.salud.mcmutton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FileUtils;

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
		return name.replaceAll("[()'~\"?!|\\xae\u2122]", "").replaceAll("[- /]", "_").replaceAll("[&]", "+");
	}
	
	public static String cleanVersion(final String version) {
		return version.replaceAll("[()'~]", "").replaceAll("[/ ]", "_");
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
	
	private Map<String, List<File>> mContentMap = null;
	private Set<File> mContentMapFiles = new HashSet<File>();
	
	public Map<String, List<File>> buildApkContentMap() {
		File cacheFile = new File (mCollectionRoot + File.separator + "hash.cache");
		Map<String, List<File>> result = new HashMap<String, List<File>>();
		System.out.println("building hash cache " + cacheFile.exists());
		if (cacheFile.exists()) {
			try {
				ObjectInputStream is = new ObjectInputStream(new FileInputStream(cacheFile));
				result = (Map<String, List<File>>)is.readObject();
				is.close();
				
				for (Map.Entry<String, List<File>> elem: result.entrySet()) {
					mContentMapFiles.addAll(elem.getValue());
				}
			} catch (IOException e) {
				System.err.println("hache cache read error: " + e);
			} catch (ClassNotFoundException e) {
				System.err.println("Class not found decoding hash cache");
			}
		} else {
			// Self loop to test pathway...
			try {
				ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(cacheFile));
				os.writeObject(result);
				os.close();
				return buildApkContentMap();
			} catch (IOException e) {
				System.err.println("hashe cache write error: " + e);
			}
		}
		
		for (ApkApplication app: this.listApplications()) {
			for (ApkVersion ver: app.listVersions()) {
				try {
					for (ApkSource src: ver.listSources()) {
						try {
							ApkInstance apk = src.getPreferred();
	
							if (mContentMapFiles.contains(apk.getApkFile())) {
	//							System.out.println(apk.getName() + " " + apk.getVersion());
								continue;
							}
							
							String hash = apk.getApkHash();
							System.out.println(apk.getApkFile() + " " + hash);
							
							if (!result.containsKey(hash)) {
								result.put(hash,  new LinkedList<File>());
							}
							result.get(hash).add(apk.getApkFile());
							mContentMapFiles.add(apk.getApkFile());
						} catch (IOException e) {
							System.err.println("Error loading " + e);
						}
					}
				} catch (IOException e) {
					System.err.println("Error loading " + e);
				}
			}
		}
		try {
			System.err.println("Writing hash cache");
			ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(cacheFile));
			os.writeObject(result);
		} catch (IOException e) {
			System.err.println("hashe cache write error: " + e);
		}
		return result;
	}
	
	
	public Map<String, List<File>> getApkContentMap() {
		if (mContentMap == null) mContentMap = buildApkContentMap();
		return mContentMap;
	}
	
	public void integrateApks(File basePath, String sourceName) {
		getApkContentMap();
		
		for (File sub: basePath.listFiles()) {
			if (sub.isDirectory()) {
				integrateApks(sub, sourceName);
			} else if (sub.getName().endsWith(".apk")) {
				integrateApk(sub, sourceName);
			}
		}
	}
	
	public void integrateApk(File apkPath, String sourceName) {
		try {
			ApkInstance instance = new ApkInstance(apkPath, new File("integrate" + File.separator + apkPath.getPath()));
			String hash = instance.getApkHash();
			String status = "New";
			if (this.getApkContentMap().containsKey(hash)) {
				status = "Exists";
			} else {
				this.getApkContentMap().put(hash, new LinkedList<File>());
				
				String name = instance.getNameFromManifest();
				String version = instance.getVersionFromManifest();
				
				if (name == null || version == null) {
					System.err.println("Failed detecting name/version for " + instance.getApkFile());
					return;
				}
				name = cleanApkName(name);
				version = cleanVersion(version);
				
				if (!name.matches("^[:a-zA-Z0-9.+!_-]+$")) {
					System.err.println("Bad name: " + name);
					return;
				}
				System.err.println(name + " " + version + " -- " + status);
				
				File expectedPath = new File(mCollectionRoot + File.separator + name + File.separator + version + File.separator + sourceName);
//				System.err.println(expectedPath);
				
				expectedPath.mkdirs();
				FileUtils.copyFileToDirectory(instance.getApkFile(), expectedPath);
			}
			this.getApkContentMap().get(hash).add(instance.getApkFile());
		} catch (IOException e) {
			System.err.println("Error processing apk: " + e);
			e.printStackTrace();
		} catch (FailedManifestException e) {
			System.err.println("Error processing apk: " + e);
		}
	}
	
	List<ApkApplication> listApplications() {
		LinkedList<ApkApplication> list = new LinkedList<ApkApplication>();
		
		for (File sub: mCollectionRoot.listFiles()) {
			if (sub.isDirectory()) list.add(new ApkApplication(sub));
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
			if (vers.size() == 0) throw new FileNotFoundException("No versions for " + mPath);
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
		
		List<ApkSource> listSources() throws IOException {
			ArrayList<ApkSource> list = new ArrayList<ApkSource>();
			
			for (File sub: mPath.listFiles()) {
				if (sub.isDirectory()) list.add(new ApkSource(sub));
			}
			
			if (list.size() == 0) throw new FileNotFoundException("No sources for " + mPath);
			
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
