package edu.ucsd.energy.util;
//Author: John C. McCullough
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;

import edu.ucsd.energy.smali.LameMethod;
import edu.ucsd.energy.smali.LameSmali;
import edu.ucsd.energy.smali.LameWorld;
import edu.ucsd.energy.util.DiffParser.DiffLine.LineType;

public class DiffParser implements Iterable<DiffParser.DiffFile>{
	public static class DiffLine {
		static public enum LineType {
			CONTEXT, SOURCE, DEST
		};
		
		private String mLine;
		private int mLineNumberLeft;
		private int mLineNumberRight;
		private LineType mType;
		
		public DiffLine(String line, int lineNumberLeft, int lineNumberRight, LineType type) {
			mLine = line.substring(1);
			mLineNumberLeft = lineNumberLeft;
			mLineNumberRight = lineNumberRight;
			mType = type;
		}
		
		public LineType getType() { return mType; }
		public int getLineNumberLeft() { return mLineNumberLeft; }
		public int getLineNumberRight() { return mLineNumberRight; }
		public String getLine() { return mLine; }
	}
	
	public static class DiffHunk implements Iterable<DiffLine> {

		
		int mSrcLine;
		int mSrcChange;
		int mDstLine;
		int mDstChange;
		
		List<DiffLine> mLines;
		
		public DiffHunk(String descriptor, BufferedReader r) throws IOException {
			parseDescriptor(descriptor);
			readLines(r);
		}
		
		private void parseDescriptor(String descriptor) {
			String[] chunks = descriptor.split("[ ,]");
			mSrcLine = Integer.parseInt(chunks[1].substring(1));
			mSrcChange = Integer.parseInt(chunks[2]);
			mDstLine = Integer.parseInt(chunks[3].substring(1));
			mDstChange = Integer.parseInt(chunks[4]);
		}
		
		private void readLines(BufferedReader r) throws IOException {
			// XXX AAARG
			int sourceCount = 0;
			int destCount = 0;
			
			mLines = new ArrayList<DiffLine>();
			
			while (sourceCount < mSrcChange && destCount < mDstChange) {
				String line = r.readLine();
				
				LineType type = LineType.CONTEXT;
				
				if (line.charAt(0) == '-') {
					type = LineType.SOURCE;
				} else if (line.charAt(0) == '+') {
					type = LineType.DEST;
				} else if (line.startsWith("@@")) {
					throw new IOException("hunk misparse");
				}
				
				mLines.add(new DiffLine(line, mSrcLine + sourceCount, mDstLine + destCount, type));
				if (type != LineType.DEST) ++sourceCount;
				if (type != LineType.SOURCE) ++destCount;
			}
		}

		public Iterator<DiffLine> iterator() {
			return mLines.iterator();
		}
	}
	
	public static class DiffFile implements Iterable<DiffHunk> {
		public interface DiffHunkVisitor {
			public boolean visit(DiffHunk hunk);
		}
		
		public class FilteredDiffFile implements Iterable<DiffHunk> {
			List<DiffHunk> mHunks;
			public FilteredDiffFile(DiffHunkVisitor visitor) {
				mHunks = new LinkedList<DiffHunk>();
				
				for (DiffHunk dh: DiffFile.this.mHunks){
					if (visitor.visit(dh)) mHunks.add(dh);
				}
			}
			
			public Iterator<DiffHunk> iterator() {
				return mHunks.iterator();
			}
			
		}
		
		String mOrig;
		String mNew;
		List<DiffHunk> mHunks;
		
		public DiffFile(String originalFileLine, String newFileLine) {
			mOrig = originalFileLine.split("[\t]")[0].substring(4);
			mNew = newFileLine.split("[\t]")[0].substring(4);
			
			mHunks = new LinkedList<DiffHunk>();
		}
		
		public void readHunk(String descriptor, BufferedReader r) throws IOException {
			mHunks.add(new DiffHunk(descriptor, r));
		}

		public Iterator<DiffHunk> iterator() {
			return mHunks.iterator();
		}
		
		public FilteredDiffFile filter(DiffHunkVisitor visitor) {
			return new FilteredDiffFile(visitor);
		}
		
		public final String getOrig() {
			return mOrig;
		}
		
		public final String getNew() {
			return mNew;
		}
	}
	
	private List<DiffFile> mFiles;
	
	public DiffParser(InputStream in) {
		mFiles = new LinkedList<DiffFile>();
		BufferedReader r = new BufferedReader(new InputStreamReader(in));
		
		try {
			DiffFile currentFile = null;
			
			while (true) {
				String line = r.readLine();
				if (line == null) break;
				
				if (line.startsWith("---")) {
					currentFile = new DiffFile(line, r.readLine());
					mFiles.add(currentFile);
				}
				else if (line.startsWith("@@")) {
					currentFile.readHunk(line, r);
				}
			}
                        in.close();
		} catch (IOException e) {
			System.err.println(e.toString());
                }
	}

	public Iterator<DiffFile> iterator() {
		return mFiles.iterator();
	}
	
	private static class InterestingHunkVisitor implements DiffFile.DiffHunkVisitor {
		// Interesting if hunk has change that isn't boring
		private static Pattern sboring_re = Pattern.compile("^\\s*(?:[.]line.*|const.*|0x.*|[.]local.*|[.]end method.*|[.]field.*|)$");
		
		
		public boolean visit(DiffHunk hunk) {
			boolean interesting = false;
			for (DiffParser.DiffLine dl: hunk) {
				interesting = interesting || checkLine(dl);
			}
			return interesting;
		}
		
		public boolean checkLine(DiffLine dl) {
			if (dl.getType() != LineType.CONTEXT) {
				if (!sboring_re.matcher(dl.getLine()).matches()) return true;
			}
			return false;
		}
		
	}
	
	public Set<String> modifiedMethods(String smaliPath) throws IOException {
		LameSmali ls = new LameSmali(smaliPath);
		
		InterestingHunkVisitor interesting = new InterestingHunkVisitor();
		
		Set<String> modifiedMethods = new HashSet<String>();
		
		for (DiffParser.DiffFile df: this) {
			for (DiffParser.DiffHunk dh: df.filter(interesting)) {
				for (DiffParser.DiffLine dl: dh) {
					if (interesting.checkLine(dl) && dl.getType() != LineType.CONTEXT) {
						String method = ls.lookupMethod(df.getOrig(), dl.getLineNumberLeft());
						modifiedMethods.add(method);
						break;
					}
				}
			}
		}

		return modifiedMethods;
	}
	
	protected static void writeCodeLinesHtml(PrintStream out, int startLine, List<Integer> modified, List<String> lines) {
		final Pattern linePattern = Pattern.compile("^(\\s*)(.*)(\\s*)$");
		
		// start index is off by one relative to diff line numbering
		int currentLine = startLine + 1;
		
		for (String line: lines) {
			//for (;modifiedIndex < modified.size() && currentLine > modified.get(modifiedIndex); ++modifiedIndex) {}
			boolean wasModified = modified.contains(currentLine); // (modifiedIndex < modified.size() && (currentLine == modified.get(modifiedIndex)));
			
			Matcher m = linePattern.matcher(line);
			if (m.matches() && m.group(2).length() > 0) {
				for (int i = 0; i < m.group(1).length(); ++i) { out.print("&nbsp;"); }
				if (wasModified) out.print("<b>");
				out.print(StringEscapeUtils.escapeHtml(m.group(2).toString()));
				if (wasModified) out.print("</b>");
				out.print("<br>\n");
			}
			++currentLine;
		}
	}
	
	public void writeModifiedMethods(String smaliPath) throws IOException {
		DiffParser.DiffFile firstFile = this.iterator().next();
		
		System.out.println("!! " + firstFile.getOrig() + " -- " + firstFile.getNew());
		
		String origFile = firstFile.getOrig();
		String newFile = firstFile.getNew();
		
		String origPrefix = origFile.substring(0, origFile.indexOf("smali") + 6);
		String newPrefix = newFile.substring(0, newFile.indexOf("smali") + 6);
		
		LameSmali lsa = new LameSmali(smaliPath + "/" + origPrefix);
		LameSmali lsb = new LameSmali(smaliPath + "/" + newPrefix);
		
		LameWorld lwa = lsa.traverse();
		LameWorld lwb = lsb.traverse();
		
		InterestingHunkVisitor interesting = new InterestingHunkVisitor();
		
		PrintStream out = new PrintStream(new File("dump.html"));
		
		out.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">");
		out.println("<html><body>");
		out.println("<style type=\"text/css\"> td { width: 6in; word-break: break-word; vertical-align: text-top; border: 1px solid; }</style>");
		
//		out.println("<tr><th style='width: 50%'></th><th style='width: 50%'></th></tr>");
		
		for (DiffParser.DiffFile df: this) {
			final String leftFile = df.getOrig().substring(origPrefix.length());
			final String rightFile = df.getNew().substring(newPrefix.length());
			
			out.println("<table>");
			out.println("<tr><th>" + leftFile + "</th><th>" + rightFile + "</th></tr>");
			
			List<LameMethod> methodsLeft = new ArrayList<LameMethod>();
			List<LameMethod> methodsRight = new ArrayList<LameMethod>();
			
			// There's probably a more efficient way, but oh well
			List<Integer> modifiedLeft = new ArrayList<Integer>();
			List<Integer> modifiedRight = new ArrayList<Integer>();
			
			for (DiffParser.DiffHunk dh: df.filter(interesting)) {
				LameMethod methodA = null;
				LameMethod methodB = null;
				
				for (DiffParser.DiffLine dl: dh) {
					if (dl.getType() == LineType.SOURCE) {
						modifiedLeft.add(dl.getLineNumberLeft());
					} else if (dl.getType() == LineType.DEST) {
						modifiedRight.add(dl.getLineNumberRight());
					}
					
					if (dl.getType() != LineType.CONTEXT) {
						if (methodA == null) {
							String methodName = lsa.lookupMethod(leftFile, dl.getLineNumberLeft());
							if (methodName != null) methodA = lwa.findMethod(methodName);
						}
						if (methodB == null) {
							String methodName = lsb.lookupMethod(rightFile, dl.getLineNumberRight());
							if (methodName != null) methodB = lwb.findMethod(methodName);
						}
					}
				}
					
				if ((methodsLeft.size() == 0 || methodsLeft.get(methodsLeft.size()-1) != methodA) || (methodsRight.size() == 0 || methodsRight.get(methodsRight.size()-1) != methodB)) {
					methodsLeft.add(methodA);
					methodsRight.add(methodB);
				}
			}
				
			for (int i = 0; i < methodsLeft.size(); ++i) {
				LameMethod methodA = methodsLeft.get(i);
				LameMethod methodB = methodsRight.get(i);
				
				out.println("<tr><td>");
				if (methodA != null) {
					writeCodeLinesHtml(out,
									   methodA.mStartingLineNumber,
									   modifiedLeft,
									   lsa.getLines(df.getOrig().substring(origPrefix.length()), 
													methodA.mStartingLineNumber, 
													methodA.mEndingLineNumber));
				} else {
					out.println("MISSING");
				}
				
				out.println("</td><td>");
				if (methodB != null) {
					writeCodeLinesHtml(out,
									   methodB.mStartingLineNumber,
									   modifiedRight,
									   lsb.getLines(df.getNew().substring(newPrefix.length()), 
													methodB.mStartingLineNumber, 
													methodB.mEndingLineNumber));
					
				} else {
					out.println("MISSING");
				}
				out.println("</td></tr>");
			}
			out.println("</table>");
		}
		
		out.println("</body></html>");
		out.close();
	}
}
