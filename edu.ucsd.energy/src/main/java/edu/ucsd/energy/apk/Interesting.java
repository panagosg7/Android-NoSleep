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
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.strings.StringStuff;

public class Interesting {
	
	public static Set<MethodReference> sInterestingMethods = new HashSet<MethodReference>();
	//MethodReference and interesting argument index
	//WARNING: need to use the selector here 
	
	//Map intent call methods to the possible entry methods in the called component
	public static Map<Selector, Pair<Integer, Set<Selector>>> mIntentMethods = new HashMap<Selector, Pair<Integer, Set<Selector>>>();
	
	//WakeLock invocation methods
	public static Map<MethodReference, Pair<Integer, Set<Selector>>> mWakelockMethods = new HashMap<MethodReference, Pair<Integer, Set<Selector>>>();
	
	
	//WakeLock Definitions
	public static MethodReference wakelockAcquire = StringStuff.makeMethodReference("android.os.PowerManager$WakeLock.acquire()V");
	public static MethodReference wakelockTimedAcquire = StringStuff.makeMethodReference("android.os.PowerManager$WakeLock.acquire(J)V");
	public static MethodReference wakelockRelease = StringStuff.makeMethodReference("android.os.PowerManager$WakeLock.release()V");
	
	
	//Set Definitions
	public static Set<Selector> wakelockInterestingMethods = new HashSet<Selector>();
	
	public static Set<Selector> activityCallbackMethods = new HashSet<Selector>();
	public static Set<Selector> activityEntryMethods = new HashSet<Selector>();
	public static Set<Selector> activityExitMethods = new HashSet<Selector>();
	
	public static Set<Selector> serviceCallbackMethods = new HashSet<Selector>();
	public static Set<Selector> startedServiceEntryMethods = new HashSet<Selector>();
	public static Set<Selector> startedServiceExitMethods = new HashSet<Selector>();
	public static Set<Selector> boundServiceEntryMethods = new HashSet<Selector>();
	public static Set<Selector> boundServiceExitMethods = new HashSet<Selector>();
	public static Set<Selector> intentServiceEntryMethods = new HashSet<Selector>();
	public static Set<Selector> intentServiceExitMethods = new HashSet<Selector>();
	
	
	
	public static Set<Selector> runnableCallbackMethods = new HashSet<Selector>();
	
	public static Set<Selector> runnableEntryMethods = new HashSet<Selector>();
	public static Set<Selector> runnableExitMethods = new HashSet<Selector>();
	
	
	public static Set<Selector> ignoreIntentSelectors = new HashSet<Selector>();
	
	public static Set<Selector> broadcastReceiverCallbackMethods = new HashSet<Selector>();
	public static Set<Selector> broadcastReceiverEntryMethods = new HashSet<Selector>();
	public static Set<Selector> broadcastReceiverExitMethods = new HashSet<Selector>();
	
	public static Set<Selector> applicationEntryMethods = new HashSet<Selector>();
	public static Set<Selector> applicationExitMethods = new HashSet<Selector>();
	
	public static Map<Selector, Pair<Integer, Set<Selector>>> mRunnableMethods = new HashMap<Selector, Pair<Integer, Set<Selector>>>();

	
	public final static TypeName WakeLockType = TypeName.string2TypeName("Landroid/os/PowerManager$WakeLock");
	public  final static TypeReference WakeLockTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, WakeLockType);
	
	public final static TypeName PowerManagerName = TypeName.string2TypeName("Landroid/os/PowerManager");

	public  final static TypeName IntentType = TypeName.string2TypeName("Landroid/content/Intent");
	public  final static TypeReference IntentTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, IntentType);
	
	public  final static TypeName RunnableType = TypeName.string2TypeName("Ljava/lang/Runnable");
	public  final static TypeReference RunnableTypeRef = TypeReference.findOrCreate(ClassLoaderReference.Application, RunnableType);

	public static final Selector GenericInitializer = Selector.make("<init>()V");
	
	
	
	//Method Selectors
	public final static Selector ThreadRun = Selector.make("run()V");
	
	public static final Selector ActivityConstructor = Selector.make("<nit>()V");
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
	public static final Selector ServiceOnBind = Selector.make("onBind(Landroid/content/Intent;)Landroid/os/IBinder");
	public static final Selector ServiceOnUnbind = Selector.make("onUnbind(Landroid/content/Intent;)Z");
	public static final Selector ServiceOnRebind = Selector.make("onRebind(Landroid/content/Intent;)V");
	public static final Selector ServiceOnDestroy = Selector.make("onDestroy()V");
	
	public static final Selector ServiceOnHandleIntent = Selector.make("onHandleIntent(Landroid/content/Intent;)V");
	
	public static final Selector BroadcastReceiverOnReceive = 
			Selector.make("onReceive(Landroid/content/Context;Landroid/content/Intent;)V");
	
	//Application Selectors
	public static final Selector ApplicationOnCreate = Selector.make("onCreate()V");
	public static final Selector ApplicationOnTerminate = Selector.make("onTerminate()V");
	
	
	//Intent Calls
	public static final Selector StartActivity = Selector.make("startActivity(Landroid/content/Intent;)V");
	public static final Selector StartActivityForResult = Selector.make("startActivityForResult(Landroid/content/Intent;I)V");
	
	public static final Selector StartService = Selector.make("startService(Landroid/content/Intent;)Landroid/content/ComponentName;");
	public static final Selector BindService = Selector.make("bindService(Landroid/content/Intent;Landroid/content/ServiceConnection;I)Z");
	
	public static final Selector SendBroadcast = Selector.make("sendBroadcast(Landroid/content/Intent;)V");
	
	
	

	//Fill in the sets and maps
	static {

		//WakeLocks
		
		mWakelockMethods.put(wakelockAcquire, Pair.make(new Integer(0),wakelockInterestingMethods));
		mWakelockMethods.put(wakelockRelease, Pair.make(new Integer(0),wakelockInterestingMethods));
		mWakelockMethods.put(wakelockTimedAcquire, Pair.make(new Integer(0),wakelockInterestingMethods));
		
		sInterestingMethods.addAll(mWakelockMethods.keySet());
		
		/*
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
		*/
		
		//mIntentMethods.put(Selector.make("getBroadcast(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;"), new Integer(1));
		//mIntentMethods.put(Selector.make("getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;"), new Integer(1));
		
		
	//Activity
		activityCallbackMethods.add(GenericInitializer);
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
		serviceCallbackMethods.add(GenericInitializer);
		serviceCallbackMethods.add(ServiceOnBind);
		serviceCallbackMethods.add(ServiceOnDestroy);
		serviceCallbackMethods.add(ServiceOnCreate);
		serviceCallbackMethods.add(Selector.make("onLowMemory()V"));
		serviceCallbackMethods.add(ServiceOnRebind);
		serviceCallbackMethods.add(ServiceOnStart);
		serviceCallbackMethods.add(ServiceOnStartCommand);
		serviceCallbackMethods.add(Selector.make("onTaskRemoved(Landroid/content/Intent;)V"));
		serviceCallbackMethods.add(Selector.make("onTrimMemory(I)V"));
		serviceCallbackMethods.add(ServiceOnUnbind);

		serviceCallbackMethods.add(Selector.make("startForeground(ILandroid/app/Notification;)V"));
		serviceCallbackMethods.add(Selector.make("stopForeground(B;)V"));
		serviceCallbackMethods.add(Selector.make("stopSelf()V"));
		serviceCallbackMethods.add(Selector.make("stopSelf(I)V"));
		serviceCallbackMethods.add(Selector.make("stopSelfResult(I)B"));
		
		
	//Ignore these selectors when invoking a def-use manager
		ignoreIntentSelectors.add(Selector.make("putExtra(Ljava/lang/String;I)Landroid/content/Intent;"));
		ignoreIntentSelectors.add(Selector.make("setFlags(I)Landroid/content/Intent;"));
		ignoreIntentSelectors.add(Selector.make("putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;"));
		ignoreIntentSelectors.add(Selector.make("setData(Landroid/net/Uri;)Landroid/content/Intent;"));
		ignoreIntentSelectors.add(Selector.make("putExtra(Ljava/lang/String;Landroid/os/Parcelable;)Landroid/content/Intent;"));
		ignoreIntentSelectors.add(Selector.make("setType(Ljava/lang/String;)Landroid/content/Intent;"));
		ignoreIntentSelectors.add(Selector.make("createChooser(Landroid/content/Intent;Ljava/lang/CharSequence;)Landroid/content/Intent;"));
		ignoreIntentSelectors.add(Selector.make("addFlags(I)Landroid/content/Intent;"));
		ignoreIntentSelectors.add(Selector.make("setResult(ILandroid/content/Intent;)V"));
		ignoreIntentSelectors.add(Selector.make("queryIntentActivities(Landroid/content/Intent;I)Ljava/util/List;"));
		ignoreIntentSelectors.add(Selector.make("submitIntent(Landroid/content/Intent;)V"));
		
		
	//Started service
		startedServiceEntryMethods.add(ServiceOnStart);
		startedServiceEntryMethods.add(ServiceOnStartCommand);
		startedServiceExitMethods.add(ServiceOnStart);
		startedServiceExitMethods.add(ServiceOnStartCommand);
		//startedServiceExitMethods.add(ServiceOnDestroy);

	//Bound service
		boundServiceEntryMethods.add(ServiceOnBind);
		boundServiceEntryMethods.add(ServiceOnRebind);
		boundServiceExitMethods.add(ServiceOnUnbind);
		//boundServiceExitMethods.add(ServiceOnDestroy);
		
	//Intent service
		intentServiceEntryMethods.add(ServiceOnHandleIntent);
		intentServiceExitMethods.add(ServiceOnHandleIntent);
		intentServiceExitMethods.add(ServiceOnDestroy);

		
	//Runnable
		runnableCallbackMethods.add(ThreadRun);
		runnableEntryMethods.add(ThreadRun);
		runnableExitMethods.add(ThreadRun);
		
	//BroadcastReceivers
		broadcastReceiverEntryMethods.add(BroadcastReceiverOnReceive);
		broadcastReceiverExitMethods.add(BroadcastReceiverOnReceive);
		broadcastReceiverCallbackMethods.add(BroadcastReceiverOnReceive);
		
	//Application
		applicationEntryMethods.add(ApplicationOnCreate);
		applicationExitMethods.add(ApplicationOnTerminate);
		
		/*
		 * Needed a selector for the Intents because the class appearing in the signature of
		 * the method is not always in the android namespace  
		 */
		mIntentMethods.put(StartActivity, Pair.make(new Integer(1), activityEntryMethods));
		mIntentMethods.put(StartActivityForResult, Pair.make(new Integer(1), activityEntryMethods));
		
		mIntentMethods.put(StartService, Pair.make(new Integer(1), startedServiceEntryMethods));
		mIntentMethods.put(BindService, Pair.make(new Integer(1), boundServiceEntryMethods));
		mIntentMethods.put(StartService, Pair.make(new Integer(1), intentServiceEntryMethods));
		
		mIntentMethods.put(SendBroadcast, Pair.make(new Integer(1), broadcastReceiverEntryMethods));

		
		mRunnableMethods.put(Selector.make("start()V"), Pair.make(new Integer(0), runnableEntryMethods));
		mRunnableMethods.put(Selector.make("start(Ljava/lang/Runnable;)V"), Pair.make(new Integer(1), runnableEntryMethods));
		mRunnableMethods.put(Selector.make("start(Ljava/lang/Thread;)V"), Pair.make(new Integer(1), runnableEntryMethods));
		mRunnableMethods.put(Selector.make("post(Ljava/lang/Runnable;)Z"), Pair.make(new Integer(1), runnableEntryMethods));
		mRunnableMethods.put(Selector.make("runOnUiThread(Ljava/lang/Runnable;)V"), Pair.make(new Integer(1), runnableEntryMethods));
		mRunnableMethods.put(Selector.make("start(Ljava/lang/Thread;)V"), Pair.make(new Integer(1), runnableEntryMethods));
		mRunnableMethods.put(Selector.make("schedule(Ljava/util/TimerTask;JJ)V"), Pair.make(new Integer(1), runnableEntryMethods));
		mRunnableMethods.put(Selector.make("start()V"), Pair.make(new Integer(0), runnableEntryMethods));
		mRunnableMethods.put(Selector.make("postDelayed(Ljava/lang/Runnable;J)Z"), Pair.make(new Integer(1), runnableEntryMethods));
		//TODO: may have to extend this list with more calls
		
		
		
		
	}
}


