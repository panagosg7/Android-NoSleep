package edu.ucsd.energy.util;
//Author: John C. McCullough
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.regex.Pattern;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;

import edu.ucsd.energy.RetargetException;
import edu.ucsd.energy.analysis.Options;
import edu.ucsd.energy.apk.ConfigurationException;
import edu.ucsd.energy.apk.Util;

public class SystemUtil {

	public static File output;
	public static File outputFile;
	
	public static File walaROOT;
	
	private static JSONObject jsonObject;
	//private static String resultDirectory;
	private static ThreadLocal<File> resultDirectory = new ThreadLocal<File>();

	
	static {
		Properties prop = new Properties();
		try {
			File workingDir = new File(System.getProperty("user.dir"));
			walaROOT = workingDir.getParentFile();
			
			//prop.load(new FileInputStream("properties.default"));			
			//File mScratchRoot = Util.getAndCheckConfigPath(prop, "scratch_path");			
			// output = new File (mScratchRoot + File.separator + "output");
			// outputFile = new File (output.getPath() + File.separator + "output.out");	//default name
			// jsonObject = new JSONObject();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
		
	
	public static class LogDumper implements Runnable {
		InputStream mSource = null;
		FileOutputStream mDest = null;
		
		public LogDumper(InputStream source, File dest) throws IOException {
			mSource = source;
			mDest = new FileOutputStream(dest);
		}
		
		public void run() {
			byte buf[] = new byte[4096];
			try {
				while (true) {
					int read_len = mSource.read(buf);
					if (read_len <= 0) break;
					mDest.write(buf, 0, read_len);
				}
			} catch (IOException e) {
				// Nada
			} finally {
				try {
				mDest.close();
				} catch (IOException e) {
					// Nada
				}
			}
		}
	}
	
	public static void buildJar(File jarPath, File basePath) throws IOException, RetargetException {
		try {
			String cmd[] = {
					"/usr/bin/jar",
					"cvf", jarPath.getAbsolutePath(),
					"-C", basePath.getAbsolutePath(),
					"."
			};
			
			Process p = Runtime.getRuntime().exec(cmd);
			File dev_null = new File("/dev/null");
			
			Thread out_thread = new Thread(new LogDumper(p.getInputStream(), dev_null));
			Thread err_thread = new Thread(new LogDumper(p.getErrorStream(), dev_null));
			out_thread.start();
			err_thread.start();
			
			int result = p.waitFor();
			
			out_thread.join();
			err_thread.join();
			if (result != 0) {
				throw new RetargetException("jar trouble: " + StringUtils.join(cmd, " ") + " status=" + result);
			}
		} catch (InterruptedException e) {
			throw new RetargetException("jar trouble: " + e);
		}
	}

	public static void runCommand(String cmd[], File logTarget, File errTarget, File successTarget) throws IOException, RetargetException {
		runCommand(cmd, logTarget, errTarget, successTarget, null);
	}
	
	public static void runCommand(String cmd[], File logTarget, File errTarget, File successTarget, File cwd) throws IOException, RetargetException {
		if (successTarget.exists()) successTarget.delete();
		
		try {
			Process p = Runtime.getRuntime().exec(cmd, null, cwd);
			
			Thread out_thread = new Thread(new LogDumper(p.getInputStream(), logTarget));
			Thread err_thread = new Thread(new LogDumper(p.getErrorStream(), errTarget));
			
			out_thread.start();
			err_thread.start();
			
			int result = p.waitFor();
			
			out_thread.join();
			err_thread.join();
			
			if (result != 0) {
				throw new RetargetException("Execution failed " + result);
			} else {
				PrintStream out = new PrintStream(successTarget);
				out.print(result);
				out.close();
			}
		} catch (InterruptedException e) {
			throw new RetargetException("Interrupted");
		} catch (IOException e) {
			throw new RetargetException("IO Error: " + e);
		}
	}
	
	static public Boolean readFileBoolean(final File f) {
		if (!f.exists()) return null;
		
		try {
			ObjectInputStream s = new ObjectInputStream(new FileInputStream(f));
			Boolean result = s.readBoolean();
			s.close();
			return result;
		} catch (IOException e) {
			return null;
		}
	}
	
	static public void writeFileBoolean(final File f, Boolean b) {
		try {
			ObjectOutputStream s = new ObjectOutputStream(new FileOutputStream(f));
			s.writeBoolean(b);
			s.close();
		} catch (IOException e) {
			// meh
		}
	}


	public final static String getDateTime() {  
	    DateFormat df = new SimpleDateFormat("yyyy-MM-dd_hh:mm:ss");  
	    df.setTimeZone(TimeZone.getTimeZone("PST"));  
	    return df.format(new Date());  
	}  
	
	/**
	 * This will output the current instance of jsonObject to the designated
	 * output file, overwriting the old version of the file.
	 */
	public static void writeToFile() {
		synchronized (outputFile) {
			try {
				FileWriter fileWriter = new FileWriter(outputFile, false);
				BufferedWriter bw = new BufferedWriter(fileWriter);
	            bw.write(jsonObject.toString()); 
	            bw.close();
			} catch (Exception e) {
				System.out.println("Could not create filewriter: " + outputFile.toString());
	        }
		}
	}

	public static void writeToFile(String text) {
		synchronized (outputFile) {
			try {
				FileWriter fileWriter = new FileWriter(outputFile, true);	//this will append to existing
				BufferedWriter bw = new BufferedWriter(fileWriter);
	            bw.write(text); 
	            bw.close();
			} catch (Exception e) {
				System.out.println("Could not create filewriter: " + outputFile.toString());
	        }
		}
	}
	
	public static void setOutputFileName(String outputFileName) {
		// outputFile = new File(output.getPath() + File.separator + string);
		outputFile = new File(outputFileName);
		try {
			outputFile.createNewFile();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void commitReport(String id, JSONObject json) throws JSONException {
		synchronized (jsonObject) {
			jsonObject.put(id, json);
		}
			
	}

	public static File getResultDirectory() {
		return resultDirectory.get();
	}

	public static void setResultDirectory(String appJar) {
	    File file = new File(appJar);
	    if (!Pattern.matches(".+\\.jar", appJar)) {
	      throw new IllegalArgumentException("Input file must be a jar file.");
	    };
	    
	    File resultDir = new File(Options.OUTPUT_FOLDER, file.toString().split(File.separatorChar+"")[5]);
	    
		resultDirectory.set(resultDir);
	    
	    if (!removeDirectory(resultDir)) {
	      System.err.println("Wrong result directory.");
	    };
	    resultDir.mkdir();
	}

	public static boolean removeDirectory(File directory) {
	    if (directory == null) return false;
	    if (!directory.exists()) return true;
	    if (!directory.isDirectory()) return false;
	    String[] list = directory.list();
	    if (list != null) {
	      for (int i = 0; i < list.length; i++) {
	        File entry = new File(directory, list[i]);
	        if (entry.isDirectory()) {
	          if (!removeDirectory(entry))
	            return false;
	        }
	        else {
	          if (!entry.delete())
	            return false;
	        }
	      }
	    }
	    return directory.delete();
	}
}
