package edu.ucsd.energy.apk;
//Author: John C. McCullough
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.json.JSONException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import brut.androlib.Androlib;
import brut.androlib.AndrolibException;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.util.ExtFile;
import brut.directory.DirectoryException;

import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.strings.StringStuff;

import edu.ucsd.energy.entry.ApkException;
import edu.ucsd.energy.entry.FailedManifestException;
import edu.ucsd.energy.entry.RetargetException;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.retarget.D2jConverter;
import edu.ucsd.energy.retarget.DedConverter;
import edu.ucsd.energy.retarget.SootD2jOptimize;
import edu.ucsd.energy.retarget.SootOptimize;
import edu.ucsd.energy.retarget.Translation;
import edu.ucsd.energy.smali.LameSmali;
import edu.ucsd.energy.smali.LameWorld;
import edu.ucsd.energy.util.SystemUtil;

public class ApkInstance {
	private ApkPaths mPaths;
	public static File sScratchPath = null;
	public static File sDedPath = null;
	public static File sAndroidSdkPath = null;
	
	private Set<String> mPermissions;

	private org.w3c.dom.Document mManifest;
	private org.w3c.dom.Document mManifestStrings;
	private LameSmali mSmali;
	
	private Wala mWala = null;
	private boolean mRunWalaOnOptimized = true;
	
	public final static Logger LOGGER = Logger.getLogger(ApkInstance.class.getName());
	

	public static void loadPaths() throws ConfigurationException {
		try {
			Properties prop = new Properties();
			prop.load(new FileInputStream("mcmutton.properties"));
			loadPaths(prop);
		} catch (IOException e) {
			throw new ConfigurationException("Error loading configuration file: " + e.toString());
		}
	}
	
	public static void loadPaths(Properties prop) throws ConfigurationException {
		sScratchPath = Util.getAndCheckConfigPath(prop, "scratch_path");
		sDedPath = Util.getAndCheckConfigPath(prop, "ded_path");
		sAndroidSdkPath = Util.getAndCheckConfigPath(prop, "android_sdk_base");
	}
	
	
	public void setRunWalaOnOptimized(boolean val) { mRunWalaOnOptimized = val; }

	public ApkInstance(File path) throws IOException {
		this(path, path);
	}
	
	public ApkInstance(File path, File scratchRelativePath) throws IOException {
		mPaths = new ApkPaths(path, new File(sScratchPath + File.separator + scratchRelativePath), sDedPath, sAndroidSdkPath);
		mSmali = null;
		
		Callable<Integer> fetchAndroidVersion = new Callable<Integer>() {
			public Integer call() {
				return ApkInstance.this.getAndroidVersion();
			}
		};
		
		mDedTranslation = new Translation(new DedConverter(mPaths), new SootOptimize(mPaths, fetchAndroidVersion));
		mDex2JarTranslation = new Translation(new D2jConverter(mPaths), new SootD2jOptimize(mPaths, fetchAndroidVersion));

		/*
		 * Try ded translation first and if it fails it then does d2j
		 */
		if (mDedTranslation.mOptimize.attempted() && !mDedTranslation.mOptimize.success()) {
			mPreferredTranslation = mDex2JarTranslation;
		} else {
			mPreferredTranslation = mDedTranslation;
		}
	}
	
	public String getApkHash() throws IOException {
		try {
			String result = null;
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			
			digest.reset();
			
			InputStream is = new FileInputStream(this.getApkFile());
			byte buffer[] = new byte[4096];
			
			int count = 0;
			do {
				count = is.read(buffer);
				if (count > 0) digest.update(buffer, 0, count);
			} while (count > 0);
			is.close();
			
			StringBuilder sb = new StringBuilder();
			for (byte b: digest.digest()) {
				String hstr = Integer.toHexString(b & 0xFF);
				if (hstr.length() == 1) {
					sb.append('0');
				}
				sb.append(hstr);
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new Error("Couldn't find SHA-256");
		}
	}
	
	public final Set<String> getPermissions() throws FailedManifestException {
		if (mPermissions == null) loadPermissions();
		return mPermissions;
	}
	
	public final String getNameFromManifest() throws FailedManifestException {
		this.requiresManifest();
		
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		
		try {
			XPathExpression expr = xpath.compile("//application");
			NodeList nodes = (NodeList)expr.evaluate(mManifest, XPathConstants.NODESET);
			if (nodes.getLength() == 0) {
				throw new FailedManifestException("Unable to find application");
			}
			if (nodes.item(0).getAttributes().getNamedItem("android:label") != null) {
				return lookupStringsValue(nodes.item(0).getAttributes().getNamedItem("android:label").getNodeValue());
			} else if (nodes.item(0).getAttributes().getNamedItem("android:name") != null) {
				return lookupStringsValue(nodes.item(0).getAttributes().getNamedItem("android:name").getNodeValue());
			} else {
				throw new FailedManifestException("Couldn't determine name");
			}
		} catch (XPathExpressionException e) {
			throw new FailedManifestException(e.toString());
		}
	}
	
	public final String getVersionFromManifest() throws FailedManifestException {
		this.requiresManifest();
		
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		
		try {
			XPathExpression expr = xpath.compile("//manifest");
			NodeList nodes = (NodeList)expr.evaluate(mManifest, XPathConstants.NODESET);
			if (nodes.getLength() == 0) {
				throw new FailedManifestException("Unable to find manifest");
			}
			return lookupStringsValue(nodes.item(0).getAttributes().getNamedItem("android:versionName").getNodeValue());
		} catch (XPathExpressionException e) {
			throw new FailedManifestException(e.toString());
		}
	}
	
	public final String lookupStringsValue(String val) throws FailedManifestException {
		this.requiresManifest();
		
		final int name_offset = 8;
		
		if (!val.startsWith("@string")) {
			return val;
		} else {
			XPathFactory factory = XPathFactory.newInstance();
			XPath xpath = factory.newXPath();
			
			try {
				XPathExpression expr = xpath.compile("/resources/string[@name=\"" + val.substring(name_offset) + "\"]");
				Node node = (Node)expr.evaluate(mManifestStrings, XPathConstants.NODE);
				if (node == null) {
					throw new FailedManifestException("Unable to find string value " + val.substring(name_offset));
				}
				return node.getFirstChild().getNodeValue();
			} catch (XPathExpressionException e) {
				throw new FailedManifestException(e.toString());
			}
		}
	}
	
	public final String getId() { return (getName() + "(" + getVersion() + ")"); };
	public final String getName() { return mPaths.basePath.getParentFile().getParentFile().getName();  }
	public final String getVersion() { return new File(mPaths.basePath.getParent()).getName();  }
	public final String getPath() { return mPaths.basePath.getPath(); }
	
	public File getDedTarget() { return mPaths.ded; }
	protected File getInfoTarget() { return  mPaths.info; }
	
	public File getExtractedPath() { return mPaths.extractedPath; }
	public File getDexPath() { return mPaths.extractedPath; }
	public File getManifestPath() { return mPaths.manifestPath; }
	
	public File getWorkPath() { return mPaths.workPath; }
	
	private Translation mPreferredTranslation;
	private Translation mDex2JarTranslation;
	private Translation mDedTranslation;
	
	
	public File getDedLogTarget() {
		return mPreferredTranslation.mConverter.getLogTarget();
	}
	
	public File getDedErrLogTarget() {
		return mPreferredTranslation.mConverter.getErrTarget();
	}
	
	public File getDedOptimizedLogTarget() {
		return mPreferredTranslation.mOptimize.getLogTarget();
	}
	
	public File getDedOptimizedErrLogTarget() {
		return mPreferredTranslation.mOptimize.getErrTarget();
	}
	
	public static final String canonicalize(String smaliCall) {
		int parenIndex = smaliCall.indexOf('(');
		int startIndex = 0;
		if (smaliCall.charAt(0) == 'L') startIndex = 1;
		String firstPart = smaliCall.substring(startIndex, parenIndex);
		String secondPart = smaliCall.substring(parenIndex);
		
		return firstPart.replaceAll("(?:[/]|;->|->)", ".") + secondPart;
	}
	
	public Map<MethodReference, Set<MethodReference>> interestingReachability() throws ApkException {
		try {
			Map<MethodReference, Set<MethodReference>> results = new HashMap<MethodReference, Set<MethodReference>>();
			
			LameWorld lr = this.inspectSmali();
			List<String> strReverse = lr.getMethodList();
			List<MethodReference> reverse = new ArrayList<MethodReference>();
			
			for (String smr: strReverse) {
				reverse.add(StringStuff.makeMethodReference(canonicalize(smr)));
			}
			
			List<List<Integer>> reachability = lr.computeReachability();

			for (int i = 0; i < reachability.size(); ++i) {
				MethodReference srcMr = reverse.get(i);
				
				for (Integer dst: reachability.get(i)) {
					MethodReference dstMr = reverse.get(dst);
					
					if (Interesting.sInterestingMethods.contains(dstMr)) {
						if (!results.containsKey(dstMr)) {
							results.put(dstMr, new HashSet<MethodReference>());
						}
						results.get(dstMr).add(srcMr);
					}
				}
			}
				
			return results;
		} catch (IOException e) {
			throw new ApkException("IOE: " + e.toString());
		}
	}
	
	public Map<MethodReference, Set<MethodReference>> interestingCallSites() throws ApkException{
		try {
			LameWorld lr = this.inspectSmali();
			Map<String, Set<String>> callSiteList = lr.invertAdjacency();
			
			Map<MethodReference, Set<MethodReference>> results = new HashMap<MethodReference, Set<MethodReference>>();
			
			for (String target: callSiteList.keySet()) {
				MethodReference mr = StringStuff.makeMethodReference(canonicalize(target));
				if (Interesting.sInterestingMethods.contains(mr)) {
					Set<MethodReference> sites = new HashSet<MethodReference>();
					for (String callSite: callSiteList.get(target)) {
						sites.add(StringStuff.makeMethodReference(canonicalize(callSite)));
					}
					results.put(mr, sites); 
				}
			}
			
			return results;
		} catch (IOException e) {
			throw new ApkException(e.toString());
		}
	}
	
	protected Wala getWala() throws IOException, RetargetException {
		if (mWala == null) {
			if (mRunWalaOnOptimized) {
				requiresOptimizedJar();
				mWala = new Wala(mPreferredTranslation.mOptimize.getJarTarget(), mPaths.getAndroidJar(getAndroidVersion()), mPaths.walaCache);
			} else {
				requiresRetargetedJar();
				mWala = new Wala(mPreferredTranslation.mConverter.getJarTarget(), mPaths.getAndroidJar(getAndroidVersion()), mPaths.walaCache);
			}
		}
		return mWala;	
	}

	
	public Set<MethodReference> interestingFunctionSet() throws ApkException {
		return interestingCallSites().keySet();
	}
	
	public IReport analyzeFull() throws IOException, CancelException, RetargetException, WalaException, ApkException {		
		return this.getWala().analyzeFull();		
	}
 
	public IReport wakelockAnalyze() throws IOException, CancelException, RetargetException, WalaException, ApkException, JSONException {		
		return this.getWala().wakelockAnalyze();		
	}
	
	public Wala.UsageType analyze() throws IOException, RetargetException {
		return this.getWala().analyze();
	}
	
	public void requiresRetargeted() throws IOException, RetargetException {
	    mPreferredTranslation.requiresRetargeted();
	}
	
	public void requiresOptimized() throws IOException, RetargetException {
	    mPreferredTranslation.requiresOptimized();
	}
	
	public void buildOptimizedJava() throws IOException, RetargetException {
	    mPreferredTranslation.buildOptimizedJava();
	}
	
	public boolean successfullyRetargeted() throws IOException, RetargetException {
		return mPreferredTranslation.retargetSuccess();
	}
	
	/**
	 * Does not attempt to optimize - just checks
	 * @return
	 * @throws IOException
	 * @throws RetargetException
	 */
	public boolean isSuccessfullyOptimized() throws IOException, RetargetException {
		return mPreferredTranslation.optimizationSuccess();
	}
	
	/**
	 * Will optimize if needed
	 * @return
	 * @throws IOException
	 * @throws RetargetException
	 */
	public boolean successfullyOptimized() throws IOException {
		try {
			return mPreferredTranslation.successfullyOptimized();
		}
		catch (RetargetException e) {
			return false;
		}
	}
	
	public void requiresRetargetedJar() throws IOException, RetargetException {
	    if (!mPreferredTranslation.hasRetargetedJar()) mPreferredTranslation.buildRetargetedJar();
	}
	
	public void requiresOptimizedJar() throws IOException, RetargetException {
		if (!mPreferredTranslation.hasOptimizedJar()) mPreferredTranslation.buildOptimizedJar();
	}
	
	public Set<String> getOptPhantoms() {
		Set<String> phantoms = new HashSet<String>();
		
		try {
			FileInputStream is = new FileInputStream(this.getDedOptimizedLogTarget());
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				while (true) {
					String line = br.readLine();
					if (line == null) break;
					if (line.contains("is a phantom class")) {
						phantoms.add(line.split(" ")[1]);
					}
				}
			} finally {
				is.close();
			}
		} catch (IOException e) {
			// meh
		}
		
		return phantoms;
	}
	
	public void cleanOptimizations() {
		mDedTranslation.mOptimize.clean();
		mDex2JarTranslation.mOptimize.clean();
		
	}
	
	private String getException(File log) {
		String exception = null;
		
		try {
			FileInputStream is = new FileInputStream(log);
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				String line0 = br.readLine();
				if (line0 == null) return null;
				if (line0.contains("Exception")) {
					exception = br.readLine();
				}
			} finally {
				is.close();
			}
		} catch (IOException e) {
			// meh
		}
		
		return exception;
	}

	public String getRetargetException() {
		return this.getException(this.getDedErrLogTarget());
	}
	
	public String getOptException() {
		return this.getException(this.getDedOptimizedErrLogTarget());
	}

	public void requiresExtraction() throws IOException { 
		if (!getExtractedPath().exists() || !getDexPath().exists()) {
			decodeApk();
		}
	}
	
	public File getApkFile() throws IOException {
		return mPaths.apk;
	}
	
	public void decodeApk() throws IOException {
//		Thread.dumpStack();
		LOGGER.info("Decoding APK file");
		// ApkDecoder decoder = new ApkDecoder(); // Pulled out internal bits
		File targetDir = getExtractedPath();
		File sourceApk = getApkFile();
		
		try {
			Androlib androlib = new Androlib();
			ExtFile apkFile = new ExtFile(sourceApk);
			boolean debug = false;
			
			targetDir.mkdirs();
			
			androlib.decodeSourcesRaw(apkFile, targetDir, debug);
			androlib.decodeSourcesSmali(apkFile, targetDir, debug);
			
			boolean resourcesExtracted = new File(targetDir + "/res").exists();
			// XXX Deal with resources?
			try {
				if (!resourcesExtracted && apkFile.getDirectory().containsFile("classes.dex")) {
					ResTable resTable = androlib.getResTable(apkFile);
					System.err.println("!!!" + sourceApk + " " + targetDir);
					androlib.decodeResourcesFull(apkFile, targetDir, resTable);
				}
			} catch (DirectoryException e) {
				throw new AndrolibException("Error looking up classes.dex");
			}
		} catch (StringIndexOutOfBoundsException e) {
			// Manifest parsing error, passing through
			LOGGER.info("Manifest conversion failed on " + getName());
		} catch (AndrolibException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassCastException e) {
			LOGGER.info("decode failed, carrying on anyway");
		}
	}
	
	private void loadPermissions() throws FailedManifestException {
		requiresManifest();
		
		mPermissions = new HashSet<String>();
		
		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		
		try {
			XPathExpression expr = xpath.compile("//uses-permission");
			NodeList nodes = (NodeList)expr.evaluate(mManifest, XPathConstants.NODESET);
			for (int i = 0; i < nodes.getLength(); ++i) {
				mPermissions.add(nodes.item(i).getAttributes().getNamedItem("android:name").getNodeValue());
			}
		} catch (XPathExpressionException e) {
			throw new FailedManifestException(e.toString());
		}
	}
	
	private int getAndroidVersion() {
		final int defaultVersion = 10;
		int version = -1;
		
		try {
			requiresManifest();
	
			XPathFactory factory = XPathFactory.newInstance();
			XPath xpath = factory.newXPath();
			
			try {
				XPathExpression expr = xpath.compile("//uses-sdk");
				NodeList nodes = (NodeList)expr.evaluate(mManifest, XPathConstants.NODESET);
				for (int i = 0; i < nodes.getLength(); ++i) {
					try {
						return Integer.parseInt(nodes.item(i).getAttributes().getNamedItem("android:targetSdkVersion").getNodeValue());
					} catch (NullPointerException e) {
						version = defaultVersion;
					}
				}
			} catch (XPathExpressionException e) {
				throw new FailedManifestException(e.toString());
			}
		} catch (FailedManifestException e) {
			return -1;
		}
		
		return version;
	}
	
	private void requiresManifest() throws FailedManifestException {
		try {
			requiresExtraction();
		} catch (IOException e) {
			throw new FailedManifestException("Failed decoding Apk to get Manifest: " + getManifestPath() + " " + e);
		}
		if (mManifest == null) loadManifest();
	}
	
	private void loadManifest() throws FailedManifestException {
		try {
			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			domFactory.setNamespaceAware(true);
			DocumentBuilder builder = domFactory.newDocumentBuilder();
			mManifest = builder.parse(getManifestPath());
			
			if (mPaths.manifestStringsEnPath.exists()) {
				mManifestStrings = builder.parse(mPaths.manifestStringsEnPath);
			} else if (mPaths.manifestStringsPath.exists() ) {
				mManifestStrings = builder.parse(mPaths.manifestStringsPath);
			} else {
				/* Let's go hunting */
				for (File dir: mPaths.manifestStringsPath.getParentFile().getParentFile().listFiles()) {
					if (dir.getName().startsWith("values-")) {
						File strPath = new File(dir + "/strings.xml");
						if (strPath.exists()) {
							mManifestStrings = builder.parse(new File(dir + "/strings.xml"));
							break;
						}
					}
				}
			}
		} catch (IOException e) {
			throw new FailedManifestException(e.toString());
		} catch (ParserConfigurationException e) {
			throw new FailedManifestException(e.toString());
		} catch (SAXException e) {
			throw new FailedManifestException(e.toString());
		}
	}
	
	public boolean hasWakelockCalls() throws ApkException {
		Boolean result = SystemUtil.readFileBoolean(mPaths.hasWakelockCallsCache);
		
		if (result == null) {
			result = hasWakelockCallsHelper();
			SystemUtil.writeFileBoolean(mPaths.hasWakelockCallsCache, result);
		}
		
		return result;
	}
	
	private boolean hasWakelockCallsHelper() throws ApkException {
		
		Set<MethodReference> interestingMethods = interestingFunctionSet();
		
		for (MethodReference ref: Interesting.sWakelockMethods) {
			if (interestingMethods.contains(ref)) {
				return true;
			}
		}
		return false;
	}
	
	public void writeInfo() throws ApkException, IOException {
		if (getInfoTarget().exists()) return;
		
		this.requiresExtraction();
		
		Set<String> permissions = null;
		Set<MethodReference> interestingMethods = null;

		permissions = this.getPermissions();
		interestingMethods = this.interestingFunctionSet();

		if (permissions == null || interestingMethods == null) {
			System.out.println("error reading something in " + getName());
			return;
		}
		
		JSONObject obj = new JSONObject();
		
		JSONArray perm_arr = new JSONArray();
		if (permissions != null) {
			for (String perm: permissions) perm_arr.add(perm);
		}
		obj.put("permissions", perm_arr);
		
		JSONArray meth_arr = new JSONArray();
		if (interestingMethods != null) {
			for (MethodReference mr: interestingMethods) meth_arr.add(mr.toString());
		}
		obj.put("interestingMethods", meth_arr);
		obj.put("name", this.getName());
		obj.put("version", this.getVersion());
		
		FileWriter writer = new FileWriter(getInfoTarget());
		obj.write(writer);
		writer.close();
	}
	
	public LameWorld inspectSmali() throws IOException {
		requiresExtraction();
		if (mSmali == null) mSmali = new LameSmali(mPaths.smali);
		return mSmali.traverse();
	}
}
