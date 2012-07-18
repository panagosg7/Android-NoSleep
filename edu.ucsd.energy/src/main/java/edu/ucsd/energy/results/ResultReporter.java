package edu.ucsd.energy.results;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.io.IOUtils;

import com.ibm.wala.util.debug.Assertions;

import edu.ucsd.energy.util.Log;

/**
 * Custom tailored class to process and find aggregate numbers on
 * analysis result files.
 * @author pvekris
 *
 */
public class ResultReporter {

	private static Logger logger = Logger.getLogger("Result Logger");

	//Look for this kind of files in the result directory
	private static Pattern filePattern = Pattern.compile("0.*results.*");

	File result_directoy;

	private Map<String, JSONObject> apps = new HashMap<String, JSONObject>();

	Map<String, Integer> violation_histogram = new HashMap<String, Integer>();
	Map<String, Integer> warning_histogram = new HashMap<String, Integer>();

	private static int ANALYSIS_FAILURES	= 0;
	private static int VERIFIED 					= 0;
	private static int FAILED		 					= 0;

	private static int RESOLVED_INTENTS			= 0;
	private static int TOTAL_INTENTS				= 0;
	private static int RESOLVED_RUNNABLES		= 0;
	private static int TOTAL_RUNNABLES			= 0;

  Pattern elapsedPattern = Pattern.compile("(\\d+)m([^s]*)s");


	public ResultReporter(String dir) {
		result_directoy = new File(dir);
	}


	private void readJSONFiles() {
		if (result_directoy.isDirectory()) {
			File[] listFiles = result_directoy.listFiles();
			for (int i = 0; i < listFiles.length; i++) {
				File file = listFiles[i];
				String name = file.getName();
				Matcher matcher = filePattern.matcher(name);
				if (matcher.find()) {
					System.out.println("Reading file: " + name);
					InputStream is;
					try {
						is = new FileInputStream(file);
						String jsonTxt = IOUtils.toString( is );
						JSONObject json = (JSONObject) JSONSerializer.toJSON( jsonTxt );
						for (String key : (Set<String>) json.keySet()) {
							JSONObject appObj = (JSONObject) json.get(key);
							
							if (appObj.containsKey("Violation Report")) {		//this means that the analysis did not fail
								apps.put(key, appObj);	
							}
							
						}
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}//for files
		}
		else {
			Assertions.UNREACHABLE(result_directoy + " is not a directoy.");
		}
		System.out.println();
	}


	private void updateViolationTags(Set<String> tags) {
		for (String e : tags) {
			Integer integer = violation_histogram.get(e);
			if (integer == null) {
				violation_histogram.put(e, new Integer(0));
			}
			violation_histogram.put(e, violation_histogram.get(e) + 1);
		}
	}

	private void updateWarningTags(String e) {
		Integer integer = warning_histogram.get(e);
		if (integer == null) {
			warning_histogram.put(e, new Integer(0));
		}
		warning_histogram.put(e, warning_histogram.get(e) + 1);
	}


	public void listApps() {
		readJSONFiles();
		Set<String> tags = new HashSet<String>();
		for (Entry<String, JSONObject> e : apps.entrySet()) {
			tags.add(e.getKey());
		}
		for (String t : tags) {
			System.out.println(t);
		}
		System.out.println("Total: " + tags.size());
	}

	public void fullResults() {
		
		readJSONFiles();
		
		float[] elapsedTimes  = new float[apps.size()];
		int ind = 0;

		for (Entry<String, JSONObject> e : apps.entrySet()) {	//for every app
			
			StringBuffer sb = new StringBuffer();

			String name = e.getKey();

			JSONObject appObj = e.getValue();

			String 			version 	= (String) appObj.get("version");
			JSONObject 	violation = (JSONObject) appObj.get("Violation Report");
			JSONArray 	warning		= (JSONArray) appObj.get("Warning Report");
			JSONObject 	intent 		= (JSONObject) appObj.get("Intent");
			JSONObject 	runnable	= (JSONObject) appObj.get("Runnable");

			String 			elapsed		= (String) appObj.get("elapsed");

		//Violations
			if (violation != null) {
				if (violation.size() == 0) {					
					VERIFIED++;
					Log.green();
				}
				else {
					Log.yellow();
					Set<String> violation_tags = new HashSet<String>();
					//Forall components
					for(String comp : (Set<String>) violation.keySet()) {
						//sb.append(new Formatter().format("   %s", comp).toString() +  "\n");
						//Gather results
						JSONArray arr = (JSONArray) violation.get(comp);
						for (Iterator it = arr.iterator(); it.hasNext(); ) {
							JSONObject obj = (JSONObject) it.next();
							for (String k : (Set<String>) obj.keySet()) {
								violation_tags.add(k);
							}
						}
					}//forall components
					for (String t : violation_tags) {
						sb.append(new Formatter().format("  %s", t).toString() +  "\n");
					}
					FAILED++;
					updateViolationTags(violation_tags);
				}
			}
			else {
				Assertions.UNREACHABLE();
				ANALYSIS_FAILURES++;
			}
			
		//Warnings	
			if (warning != null) {
				for (Iterator it = warning.iterator(); it.hasNext(); ) {
					String s = (String) it.next();
					updateWarningTags(s);
				}
			}
			else {

			}
			
		//Intents
			if (intent != null) {
				RESOLVED_INTENTS += Integer.parseInt((String) intent.get("successfully_resolved_calls"));
				TOTAL_INTENTS += Integer.parseInt((String) intent.get("total_calls"));
				
			}
			else {

			}
			
			
		//Runnables
			if (runnable != null) {
				RESOLVED_RUNNABLES += Integer.parseInt((String) runnable.get("successfully_resolved_calls"));
				TOTAL_RUNNABLES += Integer.parseInt((String) runnable.get("total_calls"));
				
			}
			else {

			}
			
		//Elapsed
			if (elapsed != null) {
				
	      Matcher m = elapsedPattern.matcher(elapsed);
	      if (m.find( )) {
	         float secs = Integer.parseInt(m.group(1)) * 60 + Float.parseFloat(m.group(2));
	         elapsedTimes[ind] = secs;
	      } else {
	         System.out.println("NO MATCH");
	      }

			}


			System.out.println(new Formatter().format("%-50s (%s)", name, 
					(version.length()>10)?version.subSequence(0, 10):version).toString());

			System.out.println(sb.toString());
			
			ind ++;
			Log.resetColor();

		}

		int total = VERIFIED + FAILED + ANALYSIS_FAILURES;
		System.out.println();
		System.out.println(String.format("===================================================="));
		System.out.println(String.format("FINAL RESULTS"));
		System.out.println(String.format("----------------------------------------------------"));
		System.out.println(String.format("%-35s: %4d (%7.2f%%)", "Verified", 				VERIFIED, 100 * (double)VERIFIED/(double)total));
		System.out.println(String.format("%-35s: %4d (%7.2f%%)", "Not verified",		FAILED, 100 * (double)FAILED/(double)total));
//		System.out.println(String.format("%-35s: %4d (%7.2f%%)", "Analysis failed", ANALYSIS_FAILURES, 100 * (double)ANALYSIS_FAILURES/(double)total));
		System.out.println(String.format("----------------------------------------------------"));
		System.out.println(String.format("%-35s: %4d (%7.2f%%)", "Total", 					total, 100 * (double)total/(double)total));
		System.out.println(String.format("===================================================="));
		System.out.println(String.format("VIOLATED POLICIES"));
		System.out.println(String.format("----------------------------------------------------"));		
		ArrayList<String> list = new ArrayList<String>();
		list.addAll(violation_histogram.keySet());
		Collections.sort(list);
		for(String k : list) {
			Integer integer = violation_histogram.get(k);
			System.out.println(String.format("%-35s: %4d (%7.2f%%)", k, integer, 100 * (double)integer/(double)total));
		}
		System.out.println(String.format("===================================================="));
		System.out.println(String.format("WARNINGS"));
		System.out.println(String.format("----------------------------------------------------"));		
		list = new ArrayList<String>();
		list.addAll(warning_histogram.keySet());
		Collections.sort(list);
		for(String k : list) {
			Integer integer = warning_histogram.get(k);
			System.out.println(String.format("%-35s: %4d (%7.2f%%)", k, integer, 100 * (double)integer/(double)total));
		}
		System.out.println(String.format("=============================================================="));
		System.out.println(String.format("ASYNC CALL RESOLUTION"));
		System.out.println(String.format("--------------------------------------------------------------"));		
		System.out.println(String.format("%-35s: %6d /%6d (%7.2f%%)", "Intents", RESOLVED_INTENTS, TOTAL_INTENTS, 100 * (double)RESOLVED_INTENTS/(double)TOTAL_INTENTS));
		System.out.println(String.format("%-35s: %6d /%6d (%7.2f%%)", "Runnables", RESOLVED_RUNNABLES, TOTAL_RUNNABLES, 100 * (double)RESOLVED_RUNNABLES/(double)TOTAL_RUNNABLES));
		
		System.out.println(String.format("=============================================================="));
		System.out.println();
	}


}