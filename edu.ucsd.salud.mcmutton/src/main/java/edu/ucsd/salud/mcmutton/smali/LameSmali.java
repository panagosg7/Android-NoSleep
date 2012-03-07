package edu.ucsd.salud.mcmutton.smali;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LameSmali {
	
	private interface InstGenerator {
		public LameInstruction useMatch(Matcher m);
	}
	private static class InstParser {
		private Pattern mP;
		private InstGenerator mG;
		
		public InstParser(Pattern p, InstGenerator g) {
			mP = p;
			mG = g;
		}
		
		public LameInstruction tryMatch(String line) {
			Matcher m = mP.matcher(line);
			if (!m.matches()) return null;
			else return mG.useMatch(m);
		}
		
	}
	
	static Pattern sMethodRe = Pattern.compile("^\\s*[.]method.* (\\S*)$");
	static Pattern sEndMethodRe = Pattern.compile("^\\s*[.]end method$");
	static Pattern sClassRe = Pattern.compile("^.class.* L(.*);");
	
	
	static Pattern sInvokeDirectRe = Pattern.compile("^\\s*invoke-direct(?:[/]range)?\\s+(.*)\\s+(\\S+)$");
	
	static Pattern sMoveResultObjectRe = Pattern.compile("^\\s*move-result-object\\s+(.*)$");
	
	static List<InstParser> sInstructionPatterns = new ArrayList<InstParser>();
	
	static {
		sInstructionPatterns.add(
				new InstParser(
						Pattern.compile("^\\s*invoke-virtual(?:[/]range)?\\s+(.*)\\s+(\\S+)$"),
						new InstGenerator() { 
							public LameInstruction useMatch(Matcher m) {return new LameInvokeInstruction(m.group(2));} 
							}
						)
				);
		sInstructionPatterns.add(
				new InstParser(
						Pattern.compile("^\\s*invoke-direct(?:[/]range)?\\s+(.*)\\s+(\\S+)$"),
						new InstGenerator() { 
							public LameInstruction useMatch(Matcher m) {return new LameInvokeInstruction(m.group(2));} 
							}
						)
				);
		sInstructionPatterns.add(
				new InstParser(
						Pattern.compile("^\\s*move-result-object\\s+(.*)$"),
						new InstGenerator() { 
							public LameInstruction useMatch(Matcher m) {return new LameMoveResultObjectInstruction(m.group(1));} 
							}
						)
				);
		sInstructionPatterns.add(
				new InstParser(
						Pattern.compile("^\\s*const(/\\S*)\\s+(v\\d+),\\s+(.*)$"),
						new InstGenerator() { 
							public LameInstruction useMatch(Matcher m) {return new LameConstInstruction(m.group(1), m.group(2), m.group(3));} 
							}
						)
				);	
		sInstructionPatterns.add(
				new InstParser(
						Pattern.compile("^\\s*const-string\\s+(v\\d+),\\s+(.*)$"),
						new InstGenerator() { 
							public LameInstruction useMatch(Matcher m) {return new LameConstInstruction(m.group(1), m.group(2));} 
							}
						)
				);	
		sInstructionPatterns.add(
				new InstParser(
						Pattern.compile("^\\s*[.](\\S*)\\s*(.*)$"),
						new InstGenerator() { 
							public LameInstruction useMatch(Matcher m) {return new LameAnnotation(m.group(1), m.group(2));} 
							}
						)
				);
		sInstructionPatterns.add(
				new InstParser(
						Pattern.compile("^\\s*[:](\\S*)$"),
						new InstGenerator() { 
							public LameInstruction useMatch(Matcher m) {return new LameJumpLabel(m.group(1));} 
							}
						)
				);
		sInstructionPatterns.add(
				new InstParser(
						Pattern.compile("^\\s*return-void$"),
						new InstGenerator() { 
							public LameInstruction useMatch(Matcher m) {return new LameReturnInstruction();} 
							}
						)
				);
		sInstructionPatterns.add(
				new InstParser(
						Pattern.compile("^\\s*return (v\\d+)$"),
						new InstGenerator() { 
							public LameInstruction useMatch(Matcher m) {return new LameReturnInstruction(m.group(1));} 
							}
						)
				);
	}
	
	File mBase;
	private Map<String, ArrayList<String>> mCachedLines;
	
	public LameSmali(String basePath) {
		this(new File(basePath));
	}
	
	public LameSmali(File basePath) {
		mBase = basePath;
		mCachedLines = new HashMap<String, ArrayList<String>>();
	}
	
	public ArrayList<String> getLines(String file) throws IOException {
		synchronized (mCachedLines) {
			if (mCachedLines.containsKey(file)) {
				return mCachedLines.get(file);
			}
		}
		
		BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(mBase + "/" + file)));
		
		ArrayList<String> lines = new ArrayList<String>();
		String line = null;
		
		while(true) {
			line = r.readLine();
			if (line == null) break;
			lines.add(line);
		}

        r.close();
		
        synchronized (mCachedLines) {
			mCachedLines.put(file, lines);
			return mCachedLines.get(file);
        }
		
	}
	
	public String lookupMethod(String file, int lineNumber) throws IOException {
		ArrayList<String> lines = getLines(file);
		
		// Find .method
		for (int l = lineNumber - 1; l >= 0; --l) {
			//System.out.println(lines.get(l) + " -- " + sMethodRe.matcher(lines.get(l)));
			Matcher m = sMethodRe.matcher(lines.get(l));
			
			if (m.matches()) {
				return lookupClass(file) + "." + m.group(1);
			}
		}
		return null;
	}
	
	public String lookupClass(String file) throws IOException {
		ArrayList<String> lines = getLines(file);
		
		for (int l = 0; l < lines.size(); ++l) {
			Matcher m = sClassRe.matcher(lines.get(l));
			if (m.matches()) {
				return m.group(1);
			}
		}
		
		return null;
	}
	
	public List<String> getLines(String file, int start, int end) throws IOException {
		ArrayList<String> lines = getLines(file);
		return lines.subList(start, end);
	}
	
	public LameWorld traverse() throws IOException {
		ExecutorService execService = Executors.newFixedThreadPool(8);
		
		LameWorld world = new LameWorld();
		traverseHelper(mBase, world, execService);

		
		execService.shutdown();
		try {
			execService.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			// meh 
		}
		
		return world;
	}
	
	protected void traverseHelper(final File path, final LameWorld world, ExecutorService execService) throws IOException {
		if (path.isDirectory()) {
			for (File f: path.listFiles()) {
				traverseHelper(f, world, execService);
			}
		} else if (path.getName().endsWith(".smali")) {
			execService.execute(new Runnable() {
				public void run() {
					synchronized(world) {
						try {
						world.addClass(pseudoParse(path));
						} catch (IOException e) {
							System.err.println("Failed on " + path + ": " + e.toString());
						}
					}
				}
			});
		}
	}
	
	protected LameClass pseudoParse(File f) throws IOException {
		ArrayList<String> lines = getLines(f.getPath().substring(mBase.getPath().length()));
		
		LameClass cls = null;
		LameMethod method = null;
		
		for (int l = 0; l < lines.size(); ++l) {
			String line = lines.get(l);
			
			if (line.length() == 0) continue;
			
			if (cls == null) {
				Matcher classMatch = sClassRe.matcher(line);
				if (classMatch.matches()) {
					cls = new LameClass(classMatch.group(1));
				}
			} else if (method != null) {
				Matcher endMethodMatch = sEndMethodRe.matcher(line);
				
				if (endMethodMatch.matches()) {
					method.setEndingLineNumber(l);
					method = null;
					continue;
				}
				
				LameInstruction i = null;
				for (InstParser p: sInstructionPatterns) {
					i = p.tryMatch(line);
					if (i != null) break;
				}
				
				if (i != null) {
					method.addInstruction(i);
					continue;
				}

				//System.out.println("unmatched: (" + line.length() + ")" + line);

			} else {
				Matcher methodMatch = sMethodRe.matcher(line);
				if (methodMatch.matches()) {
					method = new LameMethod(methodMatch.group(1), l);
					cls.addMethod(method);
				}
			}
		}
		
		return cls;
	}
}
