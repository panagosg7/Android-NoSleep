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

import edu.ucsd.energy.util.E;

/**
 * Custom tailored class to process and find aggregate numbers on
 * analysis result files.
 * @author pvekris
 *
 */
public class ResultReporter {

	private static Logger logger = Logger.getLogger("Result Logger");

	//Look for this kind of files in the result directory
	private static Pattern filePattern = Pattern.compile(".*results.*");

	File result_directoy;

	private Map<String, JSONObject> apps = new HashMap<String, JSONObject>();

	Map<String, Integer> histogram = new HashMap<String, Integer>();

	private static int ANALYSIS_FAILURES	= 0;
	private static int VERIFIED 					= 0;
	private static int FAILED		 					= 0;

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
					System.out.println(name);
					InputStream is;
					try {
						is = new FileInputStream(file);
						String jsonTxt = IOUtils.toString( is );
						JSONObject json = (JSONObject) JSONSerializer.toJSON( jsonTxt );
						for (String key : (Set<String>) json.keySet()) {
							JSONObject appObj = (JSONObject) json.get(key);
							apps.put(key, appObj);
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
	}


	private void updateTags(Set<String> tags) {
		for (String e : tags) {
			Integer integer = histogram.get(e);
			if (integer == null) {
				histogram.put(e, new Integer(0));
			}
			histogram.put(e, histogram.get(e) + 1);
		}
	}


	public void fullResults() {
		readJSONFiles();
		for (Entry<String, JSONObject> e : apps.entrySet()) {
			StringBuffer sb = new StringBuffer();
			String name = e.getKey();
			JSONObject appObj = e.getValue();
			String version = (String) appObj.get("version");
			JSONObject violation = (JSONObject) appObj.get("Violation ");
			if (violation != null) {
				if (violation.size() == 0) {					
					VERIFIED++;
					E.green();
				}
				else {
					E.yellow();
					Set<String> tags = new HashSet<String>();
					//Forall components
					for(String comp : (Set<String>) violation.keySet()) {
						//sb.append(new Formatter().format("   %s", comp).toString() +  "\n");
						//Gather results
						JSONArray arr = (JSONArray) violation.get(comp);
						for (Iterator it = arr.iterator(); it.hasNext(); ) {
							JSONObject obj = (JSONObject) it.next();
							for (String k : (Set<String>) obj.keySet()) {
								tags.add(k);
							}
						}
					}//forall components
					for (String t : tags) {
						sb.append(new Formatter().format("  %s", t).toString() +  "\n");
					}
					FAILED++;
					updateTags(tags);
				}
			}
			else {
				E.red();
				sb.append(appObj);
				ANALYSIS_FAILURES++;
			}

			System.out.println(new Formatter().format("%-50s (%s)", name, version).toString());
			System.out.println(sb.toString());
			E.resetColor();

		}

		int total = VERIFIED + FAILED + ANALYSIS_FAILURES;
		System.out.println();
		System.out.println(String.format("===================================================="));
		System.out.println(String.format("FINAL RESULTS"));
		System.out.println(String.format("----------------------------------------------------"));
		System.out.println(String.format("%-35s: %4d (%7.2f%%)", "Verified", 				VERIFIED, 100 * (double)VERIFIED/(double)total));
		System.out.println(String.format("%-35s: %4d (%7.2f%%)", "Not verified",		FAILED, 100 * (double)FAILED/(double)total));
		System.out.println(String.format("%-35s: %4d (%7.2f%%)", "Analysis failed", ANALYSIS_FAILURES, 100 * (double)ANALYSIS_FAILURES/(double)total));
		System.out.println(String.format("----------------------------------------------------"));
		System.out.println(String.format("%-35s: %4d (%7.2f%%)", "Total", 					total, 100 * (double)total/(double)total));
		System.out.println(String.format("===================================================="));
		System.out.println(String.format("VIOLATED POLICIES"));
		System.out.println(String.format("----------------------------------------------------"));		
		ArrayList<String> list = new ArrayList<String>();
		list.addAll(histogram.keySet());
		Collections.sort(list);
		for(String k : list) {
			Integer integer = histogram.get(k);
			System.out.println(String.format("%-35s: %4d (%7.2f%%)", k, integer, 100 * (double)integer/(double)total));
		}
		System.out.println(String.format("===================================================="));
		System.out.println();
	}


}