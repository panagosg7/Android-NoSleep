package edu.ucsd.salud.mcmutton;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import edu.ucsd.salud.mcmutton.apk.ConfigurationException;

public class WorkMonger implements Watcher {
	ZooKeeper mZookeeper;
	DistributedWorkSet mWorkSet;
	
	public WorkMonger(String zooHost, int zooPort) throws IOException {
		mZookeeper = new ZooKeeper(zooHost, zooPort, this);
		mWorkSet = new DistributedWorkSet(mZookeeper, "/apk_monger");
	}
	
	public void flushAll() throws InterruptedException, KeeperException {
		try {
			mWorkSet.clear();
		} catch (NoSuchElementException e) {
			// Slurp
		}
	}
	
	public void enqueueAll() throws ConfigurationException, IOException, InterruptedException, KeeperException {
		ApkCollection collection = new ApkCollection();
		
		for (ApkCollection.ApkApplication app: collection.listApplications()) {
			mWorkSet.putWork(app.getName());
		}
	}
	
	public void waitForFinish() throws KeeperException, InterruptedException{
		mWorkSet.waitForEmpty();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		try {
			Properties prop = new Properties();
			prop.load(new FileInputStream("mcmutton.properties"));
			WorkMonger monger = new WorkMonger(prop.getProperty("zoo_host", "sysnet122.ucsd.edu"), 
											   Integer.parseInt(prop.getProperty("zoo_port", "2181")));
			monger.flushAll();
			monger.enqueueAll();
			monger.waitForFinish();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("done");

	}

	public void process(WatchedEvent arg0) {
		// TODO Auto-generated method stub
		
	}

}
