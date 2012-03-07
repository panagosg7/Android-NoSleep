package edu.ucsd.salud.mcmutton;

import edu.ucsd.salud.mcmutton.apk.ConfigurationException;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
    	ApkCollection collection;
    	
    	try {
    		collection = new ApkCollection();
    	} catch (ConfigurationException e) {
    		e.printStackTrace();
    		System.err.println("Error initializing ApkCollection: " + e);
    		return;
    	}
    	
//    	List<ApkInstance> batteryStatsApps = collection.instancesByPermission().get("android.permission.BATTERY_STATS");
//    	List<ApkInstance> wakeLockApps = collection.instancesByPermission().get("android.permission.WAKE_LOCK");
    	
//    	for (String perm: collection.instancesByPermission().keySet()) { System.out.println(perm); }
    	
//    	System.out.print("BATTERY_STATS");
//    	for (ApkInstance inst: batteryStatsApps) { System.out.print(" " + inst.getName()); }
//    	
//    	System.out.println();
//    	System.out.print("WAKE_LOCK");
//    	for(ApkInstance inst: wakeLockApps) { System.out.print(" " + inst.getName()); }
    	
//    	ApkInstance fb = collection.getApplication("Facebook").getVersion("1.3.0").getPreferred();
    	
//    	System.out.println(fb.getName());
    	//fb.analyze();
    	
//    	fb.interestingFunctionSet();
    	
    	for (ApkCollection.ApkApplication app: collection.listApplications()) {
    		try {
	    		System.out.println(app.getName());
	    		ApkInstance inst = app.getPreferred();
	    		inst.writeInfo();
    		} catch (Exception e) {
    			System.err.println(app.getName() + " E " + e.toString());
    		} finally {
    			
    		}
    	}
//        System.out.println( "Hello World!" );
    }
}
