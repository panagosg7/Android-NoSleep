package edu.ucsd.salud.mcmutton;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;

public class DistributedWorkSet {
	private ZooKeeper mZookeeper;
	private final String mDir;
	private final String mPrefix = "s-";
	private List<ACL> mAcl = ZooDefs.Ids.OPEN_ACL_UNSAFE;
	
	
	public DistributedWorkSet (ZooKeeper zookeeper, String dir) {
		mZookeeper = zookeeper;
		mDir = dir;
	}
	
	public void clear() throws InterruptedException, KeeperException {
		List<String> childNames = null;
		try {
			childNames = mZookeeper.getChildren(mDir, null);
		} catch (KeeperException.NoNodeException e) {
			System.out.println("NoNodeException listing children");
			throw new NoSuchElementException();
		}
		
		for (String elem: childNames) {
			String path = mDir + "/" + elem;
			mZookeeper.delete(path, -1);
		}
		
	}
	
	public Object getWork() throws NoSuchElementException, KeeperException, InterruptedException, IOException, ClassNotFoundException {
		List<String> childNames = null;
		
		try {
			childNames = mZookeeper.getChildren(mDir, null);
		} catch (KeeperException.NoNodeException e) {
			System.out.println("NoNodeException listing children");
			throw new NoSuchElementException();
		}
		
		if (childNames.size() == 0) {
			System.out.println("childNames Empty");
			throw new NoSuchElementException();
		}
		
		for (String elem: childNames) {
			String path = mDir + "/" + elem;
			try {
				byte[] data = mZookeeper.getData(path, false, null);
				mZookeeper.delete(path, -1);
				
				ByteArrayInputStream bytes = new ByteArrayInputStream(data);
				ObjectInputStream in = new ObjectInputStream(bytes);
				return in.readObject();
			} catch (KeeperException.NoNodeException e) {
				// someone else got it
			}
		}
		
		System.out.println("Couldn't find non-deleted element");
		
		throw new NoSuchElementException();
	}
	
	public boolean putWork(Object work) throws InterruptedException, KeeperException, IOException {
		while (true) {
			try {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				ObjectOutputStream out = new ObjectOutputStream(bytes);
				out.writeObject(work);
				out.flush();
				
				mZookeeper.create(mDir + "/" + mPrefix, bytes.toByteArray(), mAcl, CreateMode.PERSISTENT_SEQUENTIAL);
				return true;
			} catch (KeeperException.NoNodeException e) {
				mZookeeper.create(mDir, new byte[0], mAcl, CreateMode.PERSISTENT);
			}
		}
			
	}
	
	public void waitForEmpty() throws InterruptedException, KeeperException {
		while (true) {
			Watcher childWatcher = new Watcher() {
				public void process(WatchedEvent arg0) {
					synchronized (this) {
						this.notifyAll();
					}
				}
				
			};
			
			try {
				synchronized (childWatcher) {
					List<String> children = mZookeeper.getChildren(mDir, childWatcher);
					System.err.println("tick " + children.size());
					
					if (children.size() > 0) {
						childWatcher.wait();
					} else {
						return;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
