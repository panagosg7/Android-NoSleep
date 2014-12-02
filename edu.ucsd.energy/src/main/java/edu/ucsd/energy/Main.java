package edu.ucsd.energy;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FilenameUtils;

import edu.ucsd.energy.analysis.Wala;
import edu.ucsd.energy.results.FailReport;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.Warning.WarningType;
import edu.ucsd.energy.util.SystemUtil;

public class Main {
	public static final Object logLock = new Object();


	public static IReport verify(File apk) throws Exception {
		IReport res;
		try {
			res = (new Wala(apk)).analyzeFull();
		} catch (IOException e) {
			e.printStackTrace();
			res = new FailReport(WarningType.IOEXCEPTION_FAILURE);		
		} catch(Exception e) {
			e.printStackTrace();
			res = new FailReport(WarningType.ANALYSIS_FAILURE);
		}		
		//Dump the output file in each intermediate step
		SystemUtil.writeToFile();
		return res;
	}
	
	public static IReport analyzeUsage(File apk) {
		IReport res;
		try {
			res = (new Wala(apk)).analyzeUsage();
		} catch (IOException e) {
			e.printStackTrace();
			res = new FailReport(WarningType.IOEXCEPTION_FAILURE);		
		} catch(Exception e) {
			e.printStackTrace();
			res = new FailReport(WarningType.ANALYSIS_FAILURE);
		}		
		return res;
		
	}

	public static IReport wakeLockCreation(File apk) {
		IReport res;
		try {
			res = (new Wala(apk)).wakelockAnalyze();
		} catch (IOException e) {
			e.printStackTrace();
			res = new FailReport(WarningType.IOEXCEPTION_FAILURE);		
		} catch(Exception e) {
			e.printStackTrace();
			res = new FailReport(WarningType.ANALYSIS_FAILURE);
		}		
		return res;
	}

	private static void setOutputFile() {
		String timeStamp = new SimpleDateFormat("yyyyMMddhhmm").format(new Date());
		String outputFileName = "output_" + timeStamp + ".txt" ;
		SystemUtil.setOutputFileName(outputFileName);
		//System.out.println("Output file: " + outputFileName);
	}

	public static void main(String[] args) {

		Options options = new Options();
		CommandLineParser parser = new PosixParser();

		options.addOption(new Option("r", "verify", false, "verify"));
		options.addOption(new Option("u", "usage", false, "print components that leave a callback (un)locked"));
		options.addOption(new Option("w", "wakelock-info", false, "gather info about wakelock creation"));
		options.addOption(new Option("i", "input", true, "input JAR file"));
		options.addOption(new Option("h", "help", false, "prints help message"));
		
		try {
			
			CommandLine line = parser.parse(options,  args);
			
			if (line.hasOption("help")) {
				printHelpMessageAndExit(options);
			}
			
			File jarFile  = null;
			
			// Read input file
			if (line.hasOption("input")) {
				String jarPath = line.getOptionValue("input");
				jarFile = new File(jarPath);
			}
			else {
				throw new Exception("No input file given");				
			}			
			
			setOutputFile();
					
			if (line.hasOption("wakelock-info")) {				
				wakeLockCreation(jarFile );
			}
			else if (line.hasOption("verify")) {
				verify(jarFile );
			}
			else if (line.hasOption("usage")) {
				analyzeUsage(jarFile );				
			}
			else {
				printHelpMessageAndExit(options);
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void printHelpMessageAndExit(Options options) {
		// TODO Auto-generated method stub
		String header = "No-sleep energy bug finder for android applications\n\n";
		String footer = "\nPlease report issues to Panagiotis Vekris (pvekris@cs.ucsd.edu)";				 
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("./nosleep", header, options, footer, true);
		System.exit(0);		
	}

}
