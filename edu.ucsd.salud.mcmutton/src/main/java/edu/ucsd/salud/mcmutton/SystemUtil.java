package edu.ucsd.salud.mcmutton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;

import org.apache.commons.lang.StringUtils;

public class SystemUtil {
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
}
