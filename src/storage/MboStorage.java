package storage;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import psdi.mbo.Mbo;
import psdi.mbo.MboSet;
import psdi.util.logging.MXLogger;
import psdi.util.logging.MXLoggerFactory;

public class MboStorage {

	private Map mboRegister;
	private Map mboData;
	private long myCounter;
	private static MboStorage singleton;

	final private String APPLOGGER = "maximo.system.fastmbo";
	private MXLogger log;
	private Object rd;
	private Method rdm;
	long lastAccessed = 0;

	class MapKey {
		String className;
		long mboNum;

		MapKey(String className, long mboNum) {
			this.className = className;
			this.mboNum = mboNum;
		}

		public String toString() {
			return className + ".." + mboNum;
		}

		public boolean equals(Object obj) {
			return ((MapKey) obj).className.equals(className)
					&& (((MapKey) obj).mboNum == mboNum);
		}

	}

	private MboStorage() {
		mboRegister = new HashMap();
		mboData = new WeakHashMap();
		log = MXLoggerFactory.getLogger(APPLOGGER);
		lastAccessed = System.currentTimeMillis();
	}

	public static MboStorage getStorage() {
		if (singleton == null) {
			singleton = new MboStorage();
		}
		return singleton;
	}

	public void registerMbo(Mbo m) throws InstantiationException,
			IllegalAccessException, SecurityException, NoSuchMethodException,
			IllegalArgumentException, InvocationTargetException {
		String className = m.getClass().getName();
		if (mboRegister.get(className) == null) {
			ClassLoader classLoader = m.getClass().getClassLoader();
			Constructor constructor = m.getClass().getConstructor(
					new Class[] { MboSet.class });
			//System.out.println("registering the class:"+className);
//			mboRegister
//					.put(className, constructor.newInstance(new Object[] { m
//							.getThisMboSet() }));
		}

	}

	public long getMboCounter() {
		return myCounter++;
	}

	// public void putMboData(MboData m, long mboCount) throws RemoteException {
	// String className = m.getMbo().getName();
	// mboData.put(new MapKey(className, mboCount), m);
	// }

	public void putMboRecordData(Mbo m, Object recordData, long mboCount)
			throws SecurityException, NoSuchMethodException,
			IllegalArgumentException, IllegalAccessException,
			InvocationTargetException {

		// System.out.println("*********************************");
		// System.out.println(recordData.getClass().getName());
		Method declaredMethod = getDeclaredMethod(recordData);
		List data = (List) declaredMethod.invoke(recordData, null);
		// MboRecordDataI record=MboRecordDataF.getMboRecordData(recordData);
		// System.out.println(getDisplayString(data));
		// log.debug(getDisplayString(record));
		String className = m.getClass().getName();

		// mboData.put(new MapKey(className, mboCount), new
		// SoftReference(data));
		mboData.put(new MapKey(className, mboCount), data);// putting to
															// weakhashmap
															// directly
		if ((System.currentTimeMillis() - lastAccessed) > 1000) {
			lastAccessed = System.currentTimeMillis();
			System.out.println("Size of a data Map:" + mboData.size());
			System.out.println("Size of a classRegister:" + mboRegister.size());
			//System.out.println("Size of a MboDataStorage :"+MboDataStorage.getSize());

			// printLiveObjects();
		}
	}

	public Object getMboRecordData(String className, long mboCount) {
		return mboData.get(new MapKey(className, mboCount));
	}

	private Method getDeclaredMethod(Object recordData)
			throws NoSuchMethodException {
		if (this.rdm != null) {
			return rdm;
		}
		Class classD = recordData.getClass();
		System.out.println("_________________________________________>>>>"
				+ classD.getName());
		Method declaredMethod = classD.getDeclaredMethod("getAttributeData",
				null);
		declaredMethod.setAccessible(true);
		rdm = declaredMethod;
		return declaredMethod;
	}

	private String getDisplayString(MboRecordDataI rec) {
		List data = rec.getAttributeData();
		Iterator iter = data.iterator();
		String rez = "";
		while (iter.hasNext()) {
			rez += iter.next() + ", ";
		}
		rez = rez.substring(0, rez.length() - 2);
		return "{ " + rez + " }";
	}

	private String getDisplayString(List rec) {

		Iterator iter = rec.iterator();
		String rez = "";
		while (iter.hasNext()) {
			rez += iter.next() + ", ";
		}
		rez = rez.substring(0, rez.length() - 2);
		return "{ " + rez + " }";
	}
}
