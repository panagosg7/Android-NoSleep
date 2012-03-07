package edu.ucsd.salud.mcmutton;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

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
			
			while (true) {
				try {
					String work = (String)mWorkSet.getWork();
					ApkInstance apk = collection.getPreferred(work);
					System.out.println(apk.getPath());
					apk.writeInfo();
					if (apk.hasWakelockCalls()) {
						apk.requiresOptimized();
					}
                } catch (ApkException e) {
                    System.err.println("apk err: " + e.toString());
                    continue;
                } catch (RetargetException e) {
                	System.err.println("retarget err: " + e.toString());
                } catch (OutOfMemoryError e) {
                	System.err.println("ran out of memory: " + e.toString());
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
