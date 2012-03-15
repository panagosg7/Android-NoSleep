package edu.ucsd.salud.mcmutton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import edu.ucsd.salud.mcmutton.apk.Wala;

public class WorkConsumer implements Watcher {
	ZooKeeper mZookeeper;
	DistributedWorkSet mWorkSet;
	
	public WorkConsumer(String zooHost, int zooPort) throws IOException {
		System.out.println("zk: " + zooHost + ":" + zooPort);
		mZookeeper = new ZooKeeper(zooHost, zooPort, this);
		mWorkSet = new DistributedWorkSet(mZookeeper, "/apk_monger");
	}
		
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		try {
			Properties prop = new Properties();
			prop.load(new FileInputStream("mcmutton.properties"));
			WorkConsumer consumer = new WorkConsumer(prop.getProperty("zoo_host", "sysnet122.ucsd.edu"), 
											         Integer.parseInt(prop.getProperty("zoo_port", "2181")));			
			consumer.runAll();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("done");
	}
	
	public void runAll() {
		try {
			ApkCollection collection = new ApkCollection();
			collection.setCaching(false);
			
			File resultsDir = new File(ApkInstance.sScratchPath + File.separator + "results");
			resultsDir.mkdirs();
			
			while (true) {
				try {
					String work = (String)mWorkSet.getWork();
					System.err.println("pre-" + work);
					ApkInstance apk = collection.getPreferred(work);
					if (apk == null) {
						throw new ApkException("Apk missing for " + work);
					}
					
					System.out.println(apk.getPath());
					
					apk.writeInfo();
					if (apk.hasWakelockCalls()) {
						apk.requiresOptimized();
					}
					
					File result_target = new File(resultsDir + File.separator + work + ".json");
					
					JSONObject obj = new JSONObject();
					obj.put("hasWakelockCalls", apk.hasWakelockCalls());
					if (apk.hasWakelockCalls()) {
						obj.put("successfullyOptimized", apk.successfullyOptimized());
						obj.put("optimizationException", apk.getOptException());
						Set<String> phantoms = apk.getOptPhantoms();
						JSONArray phantom_array = new JSONArray();
						phantom_array.addAll(phantoms);
						obj.put("optimizationPhantoms", phantom_array);
						
						if (apk.successfullyOptimized()) {
							JSONObject panos_result = new JSONObject();
							try {
								Set<String> colors = apk.panosAnalyze();
								JSONArray colors_array = new JSONArray();
								colors_array.addAll(colors);
								panos_result.put("colors", colors);
							} catch (Exception e) {
								panos_result.put("_exception", e.toString());
								ByteArrayOutputStream baos = new ByteArrayOutputStream();
								e.printStackTrace(new PrintStream(baos));
								panos_result.put("_stackTrace", baos.toString());
								baos.close();
							} catch (Error e) {
								panos_result.put("_error", e.toString());
								ByteArrayOutputStream baos = new ByteArrayOutputStream();
								e.printStackTrace(new PrintStream(baos));
								panos_result.put("_stackTrace", baos.toString());
								baos.close();
							}
							obj.put("panosResult", panos_result);
							
							
							JSONObject pattern_result = new JSONObject();
							try {
								Wala.UsageType usageType = Wala.UsageType.UNKNOWN;
								usageType = apk.analyze();
								pattern_result.put("usageType", usageType);
							} catch (Exception e) {
								pattern_result.put("_exception", e.toString());
								ByteArrayOutputStream baos = new ByteArrayOutputStream();
								e.printStackTrace(new PrintStream(baos));
								pattern_result.put("_stackTrace", baos.toString());
								baos.close();
							} catch (Error e) {
								pattern_result.put("_error", e.toString());
								ByteArrayOutputStream baos = new ByteArrayOutputStream();
								e.printStackTrace(new PrintStream(baos));
								pattern_result.put("_stackTrace", baos.toString());
								baos.close();
							}
							obj.put("patternResult", pattern_result);
						}

					}
					
					System.out.println("writing " + result_target);
					FileWriter writer = new FileWriter(result_target);
					obj.write(writer);
					writer.close();
                } catch (ApkException e) {
                    System.err.println("apk err: " + e.toString());
                    continue;
                } catch (RetargetException e) {
                	System.err.println("retarget err: " + e.toString());
                } catch (OutOfMemoryError e) {
                	System.err.println("ran out of memory: " + e.toString());
                } catch (FileNotFoundException e) {
                	System.err.println("apk not " + e.toString());
				} catch (NoSuchElementException e) {
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void process(WatchedEvent arg0) {
		// TODO Auto-generated method stub
		System.out.println(arg0);
		
		
	}

}
