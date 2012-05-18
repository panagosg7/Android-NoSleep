package edu.ucsd.energy.apk;
//Author: John C. McCullough
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.util.strings.StringStuff;

public class Interesting {
	public static Set<MethodReference> sInterestingMethods = new HashSet<MethodReference>();
	//MethodReference and interesting argument index
	//WARNING: need to use the selecotr here 
	public static Map<Selector, Integer> mIntentMethods = new HashMap<Selector, Integer>();
	public static Map<MethodReference, Integer> mWakelockMethods = new HashMap<MethodReference, Integer>();
	public static Set<String> activityEntryMethods = new HashSet<String>();
	public static Set<String> serviceEntryMethods = new HashSet<String>();
	public static Set<String> broadcastReceiverEntryMethods = new HashSet<String>();
	
	static {
		sInterestingMethods.add(StringStuff.makeMethodReference("android.app.AlarmManager.set(IJLandroid/app/PendingIntent;)V"));
		sInterestingMethods.add(StringStuff.makeMethodReference("android.app.AlarmManager.cancel(Landroid/app/PendingIntent;)V"));
		
		sInterestingMethods.add(StringStuff.makeMethodReference("android.app.AlarmManager.setRepeating(IJJLandroid/app/PendingIntent;)V"));
		sInterestingMethods.add(StringStuff.makeMethodReference("android.app.AlarmManager.setInexactRepeating(IJJLandroid/app/PendingIntent;)V"));
		
		sInterestingMethods.add(StringStuff.makeMethodReference("android.net.wifi.WifiManager$WifiLock.acquire()V"));
		sInterestingMethods.add(StringStuff.makeMethodReference("android.net.wifi.WifiManager$WifiLock.release()V"));
		sInterestingMethods.add(StringStuff.makeMethodReference("android.net.wifi.WifiManager.setWifiEnabled(Z)Z"));

		sInterestingMethods.add(StringStuff.makeMethodReference("android.media.MediaPlayer.prepare()V"));
		sInterestingMethods.add(StringStuff.makeMethodReference("android.media.MediaPlayer.prepareAsync()V"));
		sInterestingMethods.add(StringStuff.makeMethodReference("android.media.MediaPlayer.release()V"));
		sInterestingMethods.add(StringStuff.makeMethodReference("android.media.MediaPlayer.start()V"));
		
		mWakelockMethods.put(StringStuff.makeMethodReference("android.os.PowerManager$WakeLock.acquire()V"), new Integer(0));
		mWakelockMethods.put(StringStuff.makeMethodReference("android.os.PowerManager$WakeLock.release()V"), new Integer(0));
		mWakelockMethods.put(StringStuff.makeMethodReference("android.os.PowerManager$WakeLock.acquire(J)V"), new Integer(0));
		
		/*
		 * Needed a selector for the Intents because the class appearing in the signature of
		 * the method is not always in the android namespace  
		 */
		//mIntentMethods.put(Selector.make(), 0);
		mIntentMethods.put(Selector.make("startActivity(Landroid/content/Intent;)V"), new Integer(1));
		mIntentMethods.put(Selector.make("startService(Landroid/content/Intent;)Landroid/content/ComponentName;"), new Integer(1));
		mIntentMethods.put(Selector.make("startActivityForResult(Landroid/content/Intent;I)V"), new Integer(1));
		mIntentMethods.put(Selector.make("sendBroadcast(Landroid/content/Intent;)V"), new Integer(1));
		//TODO: may have to extend this list with more calls
		
		
		sInterestingMethods.addAll(mWakelockMethods.keySet());		
		
		activityEntryMethods.add("<init>()V");
		activityEntryMethods.add("onPause()V");
		activityEntryMethods.add("onResume()V");
		activityEntryMethods.add("onCreate(Landroid/os/Bundle;)V");
		activityEntryMethods.add("onDestroy()V");
		activityEntryMethods.add("onStart()V");
		activityEntryMethods.add("onStop()V");
		
		
		serviceEntryMethods.add("<init>()V");
		serviceEntryMethods.add("onBind(Landroid/content/Intent;)Landroid.os.IBinder;");
		serviceEntryMethods.add("onDestroy()V");
		serviceEntryMethods.add("onCreate()V");
		serviceEntryMethods.add("onLowMemory()V");
		serviceEntryMethods.add("onRebind(Landroid/content/Intent;)V");
		serviceEntryMethods.add("onStart(Landroid/content/Intent;I)V");
		serviceEntryMethods.add("onStartCommand(Landroid/content/Intent;II)V");
		serviceEntryMethods.add("onTaskRemoved(Landroid/content/Intent;)V");
		serviceEntryMethods.add("onTrimMemory(I)V");
		serviceEntryMethods.add("onUnbind(Landroid/content/Intent;)B");
		serviceEntryMethods.add("startForeground(ILandroid/app/Notification;)V");
		serviceEntryMethods.add("stopForeground(B;)V");
		serviceEntryMethods.add("stopSelf()V");
		serviceEntryMethods.add("stopSelf(I)V");
		serviceEntryMethods.add("stopSelfResult(I)B");
		
		broadcastReceiverEntryMethods.add("onReceive(Landroid/content/Context;Landroid/content/Intent;)V");
	}
}
