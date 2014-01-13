package edu.ucsd.energy;

import java.io.File;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FilenameUtils;

import edu.ucsd.energy.analysis.Wala;
import edu.ucsd.energy.results.FailReport;
import edu.ucsd.energy.results.IReport;
import edu.ucsd.energy.results.ProcessResults.ResultType;
import edu.ucsd.energy.util.SystemUtil;

public class Main {

	public static IReport verify(File apk) throws Exception {
		IReport res;
		try {
			res = (new Wala(apk)).analyzeFull();
		} catch (IOException e) {
			e.printStackTrace();
			res = new FailReport(ResultType.IOEXCEPTION_FAILURE);		
		} catch(Exception e) {
			e.printStackTrace();
			res = new FailReport(ResultType.ANALYSIS_FAILURE);
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
			res = new FailReport(ResultType.IOEXCEPTION_FAILURE);		
		} catch(Exception e) {
			e.printStackTrace();
			res = new FailReport(ResultType.ANALYSIS_FAILURE);
		}		
		return res;
		
	}

	public static IReport wakeLockCreation(File apk) {
		IReport res;
		try {
			res = (new Wala(apk)).wakelockAnalyze();
		} catch (IOException e) {
			e.printStackTrace();
			res = new FailReport(ResultType.IOEXCEPTION_FAILURE);		
		} catch(Exception e) {
			e.printStackTrace();
			res = new FailReport(ResultType.ANALYSIS_FAILURE);
		}		
		return res;
	}

	private static void setOutputFile(String optionValue) {
		String extension = FilenameUtils.getExtension(optionValue);
		String pureName = FilenameUtils.removeExtension(optionValue);
		SystemUtil.setOutputFileName(pureName + "_" + SystemUtil.getDateTime() + "." + extension);
	}

	private static void setInputJSONFile(String optionValue) {
		System.out.println("Using input: " + optionValue);
		new File(optionValue);
	}


	public static void main(String[] args) {

		Options options = new Options();
		CommandLineParser parser = new PosixParser();

		options.addOption(new Option("v", "verify", false, "verify"));
		options.addOption(new Option("u", "usage", false, "print the components that leave a callback locked or unlocked"));
		options.addOption(new Option("w", "wakelock-info", false, "gather info about wakelock creation"));
		options.addOption(new Option("o", "output", true, "specify an output filename (date will be included)"));
		
		try {
			
			CommandLine line = parser.parse(options,  args);
			
			//TODO: ask for file !!!
			File apk = null;
			
			if (line.hasOption("output")) {
				setOutputFile(line.getOptionValue("output"));
			}
			if (line.hasOption("input")) {
				setInputJSONFile(line.getOptionValue("input"));
			}			
			
			if (line.hasOption("wakelock-info")) {				
				wakeLockCreation(apk);
			}
			else if (line.hasOption("verify")) {
				verify(apk);
			}
			else if (line.hasOption("usage")) {
				analyzeUsage(apk);				
			}
			else {
				for (Object opt: options.getOptions()) {
					System.err.println(opt);
				}
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
