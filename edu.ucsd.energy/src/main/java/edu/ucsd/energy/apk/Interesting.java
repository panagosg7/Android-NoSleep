package edu.ucsd.energy.apk;
//Author: John C. McCullough
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.StringStuff;

public class Interesting {
	
	public static Set<MethodReference> sInterestingMethods = new HashSet<MethodReference>();
	//MethodReference and interesting argument index
	//WARNING: need to use the selector here 
	public static Map<Selector, Integer> mIntentMethods = new HashMap<Selector, Integer>();
	public static Map<MethodReference, Integer> mWakelockMethods = new HashMap<MethodReference, Integer>();
	
	public static MethodReference wakelockAcquire;
	public static MethodReference wakelockRelease;
	public static MethodReference wakelockTimedAcquire;
	
	
	public static Set<Selector> activityCallbackMethods = new HashSet<Selector>();
	public static Set<Selector> activityEntryMethods = new HashSet<Selector>();
	public static Set<Selector> activityExitMethods = new HashSet<Selector>();
	
	public static Set<Selector> serviceCallbackMethods = new HashSet<Selector>();
	public static Set<Selector> serviceEntryMethods = new HashSet<Selector>();
	public static Set<Selector> serviceExitMethods = new HashSet<Selector>();
	
	public static Set<Selector> runnableCallbackMethods = new HashSet<Selector>();
	
	public static Set<Selector> runnableEntryMethods = new HashSet<Selector>();
	public static Set<Selector> runnableExitMethods = new HashSet<Selector>();
	
	
	public static Set<Selector> ignoreSelectors = new HashSet<Selector>();
	
	public static Set<Selector> broadcastReceiverCallbackMethods = new HashSet<Selector>();
	public static Set<Selector> broadcastReceiverEntryMethods = new HashSet<Selector>();
	
	public static Map<Selector, Integer> mRunnableMethods = new HashMap<Selector, Integer>();

	public final static TypeName WakeLockType = TypeName.string2TypeName("Landroid/os/PowerManager$WakeLock");
	
	public  final static TypeReference WakeLockTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, WakeLockType);
	
	public final static TypeName PowerManagerName = TypeName.string2TypeName("Landroid/os/PowerManager");
	
	public  final static TypeName IntentType = TypeName.string2TypeName("Landroid/content/Intent");
	
	public  final static TypeReference IntentTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, IntentType);
	
	public  final static TypeName RunnableType = TypeName.string2TypeName("Ljava/lang/Runnable");
	
	public  final static TypeReference RunnableTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, RunnableType);

	
	//Method Selectors
	public final static Selector ThreadRun = Selector.make("run()V");
	
	public static final Selector ActivityConstructor = Selector.make("<init>()V");
	public static final Selector ActivityOnCreate = Selector.make("onCreate(Landroid/os/Bundle;)V");
	public static final Selector ActivityOnDestroy = Selector.make("onDestroy()V");
	public static final Selector ActivityOnPause = Selector.make("onPause()V");
	public static final Selector ActivityOnResume = Selector.make("onResume()V");
	public static final Selector ActivityOnStart = Selector.make("onStart()V");
	public static final Selector ActivityOnStop = Selector.make("onStop()V");
	public static final Selector ActivityOnRestart = Selector.make("onRestart()V");

	public final static Selector ThreadCall = Selector.make("call()V");
	
	public static final Selector ServiceOnCreate = Selector.make("onCreate()V");
	public static final Selector ServiceOnStartCommand = Selector.make("onStartCommand(Landroid/content/Intent;II)I");
	public static final Selector ServiceOnStart = Selector.make("onStart(Landroid/content/Intent;I)V");
	public static final Selector ServiceOnDestroy = Selector.make("onDestroy()V");
	
	public static final Selector BroadcastReceiverOnReceive = 
			Selector.make("onReceive(Landroid/content/Context;Landroid/content/Intent;)V");
	
	static {

	//WakeLocks
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
		
		wakelockAcquire = StringStuff.makeMethodReference("android.os.PowerManager$WakeLock.acquire()V");
		wakelockTimedAcquire = StringStuff.makeMethodReference("android.os.PowerManager$WakeLock.acquire(J)V");
		wakelockRelease = StringStuff.makeMethodReference("android.os.PowerManager$WakeLock.release()V");
		
		mWakelockMethods.put(wakelockAcquire, new Integer(0));
		mWakelockMethods.put(wakelockRelease, new Integer(0));
		mWakelockMethods.put(wakelockTimedAcquire, new Integer(0));
		
		/*
		 * Needed a selector for the Intents because the class appearing in the signature of
		 * the method is not always in the android namespace  
		 */
		//mIntentMethods.put(Selector.make(), 0);
		mIntentMethods.put(Selector.make("startActivity(Landroid/content/Intent;)V"), new Integer(1));
		mIntentMethods.put(Selector.make("startService(Landroid/content/Intent;)Landroid/content/ComponentName;"), new Integer(1));
		mIntentMethods.put(Selector.make("startActivityForResult(Landroid/content/Intent;I)V"), new Integer(1));
		mIntentMethods.put(Selector.make("sendBroadcast(Landroid/content/Intent;)V"), new Integer(1));
		
		//mIntentMethods.put(Selector.make("getBroadcast(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;"), new Integer(1));
		//mIntentMethods.put(Selector.make("getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;"), new Integer(1));
		
	//bindService

		mRunnableMethods.put(Selector.make("start(Ljava/lang/Runnable;)V"), new Integer(1));
		mRunnableMethods.put(Selector.make("start(Ljava/lang/Thread;)V"), new Integer(1));
		mRunnableMethods.put(Selector.make("post(Ljava/lang/Runnable;)Z"), new Integer(1));
		mRunnableMethods.put(Selector.make("runOnUiThread(Ljava/lang/Runnable;)V"), new Integer(1));
		mRunnableMethods.put(Selector.make("start(Ljava/lang/Thread;)V"), new Integer(1));
		mRunnableMethods.put(Selector.make("schedule(Ljava/util/TimerTask;JJ)V"), new Integer(1));
		mRunnableMethods.put(Selector.make("start()V"), new Integer(0));
		mRunnableMethods.put(Selector.make("postDelayed(Ljava/lang/Runnable;J)Z"), new Integer(1));
		//TODO: may have to extend this list with more calls
		
		sInterestingMethods.addAll(mWakelockMethods.keySet());
		
	//Activity
		activityCallbackMethods.add(Selector.make("<init>()V"));	//Should this be an entry point?
		activityCallbackMethods.add(ActivityOnPause);
		activityCallbackMethods.add(ActivityOnResume);
		activityCallbackMethods.add(ActivityOnCreate);
		activityCallbackMethods.add(ActivityOnDestroy);
		activityCallbackMethods.add(ActivityOnStart);
		activityCallbackMethods.add(ActivityOnStop);
		
		activityEntryMethods.add(ActivityConstructor);
		activityEntryMethods.add(ActivityOnCreate);
		activityEntryMethods.add(ActivityOnStart);
		activityEntryMethods.add(ActivityOnResume);
		//activityEntryMethods.add(ActivityOnRestart);
		
		activityExitMethods.add(ActivityOnDestroy);
		activityExitMethods.add(ActivityOnStop);

	//Service
		serviceCallbackMethods.add(Selector.make("<init>()V"));
		serviceCallbackMethods.add(Selector.make("onBind(Landroid/content/Intent;)Landroid.os.IBinder;"));
		serviceCallbackMethods.add(Selector.make("onDestroy()V"));
		serviceCallbackMethods.add(Selector.make("onCreate()V"));
		serviceCallbackMethods.add(Selector.make("onLowMemory()V"));
		serviceCallbackMethods.add(Selector.make("onRebind(Landroid/content/Intent;)V"));
		serviceCallbackMethods.add(Selector.make("onStart(Landroid/content/Intent;I)V"));
		serviceCallbackMethods.add(Selector.make("onStartCommand(Landroid/content/Intent;II)V"));
		serviceCallbackMethods.add(Selector.make("onTaskRemoved(Landroid/content/Intent;)V"));
		serviceCallbackMethods.add(Selector.make("onTrimMemory(I)V"));
		serviceCallbackMethods.add(Selector.make("onUnbind(Landroid/content/Intent;)B"));
		serviceCallbackMethods.add(Selector.make("startForeground(ILandroid/app/Notification;)V"));
		serviceCallbackMethods.add(Selector.make("stopForeground(B;)V"));
		serviceCallbackMethods.add(Selector.make("stopSelf()V"));
		serviceCallbackMethods.add(Selector.make("stopSelf(I)V"));
		serviceCallbackMethods.add(Selector.make("stopSelfResult(I)B"));
		
		
	//Ignore these selectors when invoking a def-use manager
		ignoreSelectors.add(Selector.make("putExtra(Ljava/lang/String;I)Landroid/content/Intent;"));
		ignoreSelectors.add(Selector.make("setFlags(I)Landroid/content/Intent;"));
		ignoreSelectors.add(Selector.make("putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;"));
		ignoreSelectors.add(Selector.make("setData(Landroid/net/Uri;)Landroid/content/Intent;"));
		ignoreSelectors.add(Selector.make("putExtra(Ljava/lang/String;Landroid/os/Parcelable;)Landroid/content/Intent;"));
		ignoreSelectors.add(Selector.make("setType(Ljava/lang/String;)Landroid/content/Intent;"));
		ignoreSelectors.add(Selector.make("createChooser(Landroid/content/Intent;Ljava/lang/CharSequence;)Landroid/content/Intent;"));
		ignoreSelectors.add(Selector.make("addFlags(I)Landroid/content/Intent;"));
		ignoreSelectors.add(Selector.make("setResult(ILandroid/content/Intent;)V"));
		ignoreSelectors.add(Selector.make("queryIntentActivities(Landroid/content/Intent;I)Ljava/util/List;"));
		ignoreSelectors.add(Selector.make("submitIntent(Landroid/content/Intent;)V"));
		
		
		
		serviceEntryMethods.add(Selector.make("onCreate()V"));
		serviceEntryMethods.add(Selector.make("onStart(Landroid/content/Intent;I)V"));
		serviceEntryMethods.add(Selector.make("onStartCommand(Landroid/content/Intent;II)V"));
		serviceEntryMethods.add(Selector.make("onHandleIntent(Landroid/content/Intent;)V"));
		serviceEntryMethods.add(Selector.make("bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z"));
		
		serviceExitMethods.add(Selector.make("onDestroy()V"));
		serviceExitMethods.add(Selector.make("onHandleIntent(Landroid/content/Intent;)V"));
		
	//Runnables
		runnableCallbackMethods.add(ThreadRun);
		runnableEntryMethods.add(ThreadRun);
		runnableExitMethods.add(ThreadRun);
		
	//BroadcastReceivers
		broadcastReceiverEntryMethods.add(BroadcastReceiverOnReceive);
		broadcastReceiverCallbackMethods.add(BroadcastReceiverOnReceive);
	}
}

