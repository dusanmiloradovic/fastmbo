package aspects;


import java.util.Hashtable;

import org.apache.jcs.JCS;
import org.apache.jcs.access.exception.CacheException;

//import COM.rsa.Intel.e1;

import psdi.mbo.Mbo;
import psdi.mbo.MboSet;
import psdi.mbo.MboValue;
import storage.DummyValueHash;
import storage.MboStorage;
import storage.MboValueKey;

public aspect MboCut {

	private long Mbo.myMboCounter;
	
	private long localCnt;
	private Hashtable Mbo.changedMboValues;
	
	declare parents : psdi.mbo.MboValue implements java.io.Serializable;
	declare parents : psdi.mbo.* implements java.io.Serializable;

	
	pointcut getMboValueValueHash(Mbo m) : this(m) && call (* Mbo.getValueHash(..)) && (withincode (* *.getMboValue(..)) );
	
		Object around(Mbo m):getMboValueValueHash(m){
			//System.out.println("Da nije ovde?");
			return new Hashtable();
		}
	
	public MboValue Mbo.myConstructMboValue(String attrName) {
		return null;
	}

	pointcut constructMbo(Mbo m): this(m) && initialization( Mbo+.new(..));

	before(Mbo m):constructMbo(m){
		long counter = MboStorage.getStorage().getMboCounter();
		// MboDataStorage.getDataStorage().initMboStorage(counter);
		localCnt = counter;
	}

	after(Mbo m):constructMbo(m){
		m.myMboCounter = localCnt;
	}

	private void addChangedMboValue(Mbo m, String attrName, MboValue mboValue) {
		if (m.changedMboValues == null) {
			m.changedMboValues = new Hashtable();
		}
		m.changedMboValues.put(attrName, mboValue);
	}

	private MboValue getChangedMboValue(Mbo m, String attrName) {
		if (m.changedMboValues == null) {
			return null;
		}

		return (MboValue) m.changedMboValues.get(attrName.toLowerCase());
	}

	// public void Mbo.finalize(){
	// //System.out.println
	// (getClass().getName()+" is finalized for the counter"+myMboCounter);
	// //
	// System.out.println("And the value in the cache is "+MboStorage.getStorage().getMboRecordDat(getClass().getName(),
	// myMboCounter));
	//
	// // MboDataStorage.getDataStorage().removeMboStorage(myMboCounter);
	// }

	// pointcut setValue(Mbo m,String attr, String value,long flag):this(m)&&
	// args(attr,value,flag) && execution(* *.setValue(..));
	// before(Mbo m,String attr, String value,long
	// flag):setValue(m,attr,value,flag){
	// try {
	// //System.out.println("Cutting at set value of "+m.getName()+" for attribute "+
	// attr+"for value "+value);
	// } catch (Exception e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// }
	//
	// pointcut save(Mbo mr): this (mr) && execution (* *.save(..));
	//
	// after(Mbo mr):save(mr){
	// mr.changedMboValues = null;
	// }

	pointcut setMboSetInfo(MboSet mr):this (mr) && execution(* *.setMboSetInfo(..));

	// before (MboSet mr):setMboSetInfo(mr){
	// new Throwable().printStackTrace();
	// }

	pointcut createMbo():execution(Mbo.new(..));

	// before (Mbo mr): createMbo(mr){
	// String className=mr.getClass().getName();
	// // System.out.println("--->Creating new Mbo instance for "+className);
	// }

	// Mbo around () : createMbo(){
	// Mbo m=proceed();
	// m.myMboCounter=MboStorage.getStorage().getMboCounter();
	// return m;
	// }
	//
	// before():createMbo(){
	//
	// }

	// pointcut generateMboInstance(MboSet ms, Object rd): this (ms) && args(rd,
	// int ) && execution (* *.generateMboInstance(..));
	// after (MboSet ms, Object rd) returning (Mbo
	// m):generateMboInstance(ms,rd){
	// // m.myMboCounter=MboStorage.getStorage().getMboCounter();
	// //
	// // try {
	// //
	// // MboStorage.getStorage().putMboRecordData(m, rd, m.myMboCounter);
	// // MboStorage.getStorage().registerMbo(m);
	// // } catch (SecurityException e) {
	// // // TODO Auto-generated catch block
	// // e.printStackTrace();
	// // } catch (IllegalArgumentException e) {
	// // // TODO Auto-generated catch block
	// // e.printStackTrace();
	// // } catch (NoSuchMethodException e) {
	// // // TODO Auto-generated catch block
	// // e.printStackTrace();
	// // } catch (IllegalAccessException e) {
	// // // TODO Auto-generated catch block
	// // e.printStackTrace();
	// // } catch (InvocationTargetException e) {
	// // // TODO Auto-generated catch block
	// // e.printStackTrace();
	// // } catch (InstantiationException e) {
	// // // TODO Auto-generated catch block
	// // //e.printStackTrace();
	// // }
	// //
	// }

	pointcut getMboInstance():execution (* MboSet+.getMboInstance(..));

	after() returning (Mbo m): getMboInstance(){
		m.myMboCounter = MboStorage.getStorage().getMboCounter();
		// MboDataStorage.getDataStorage().initMboStorage(m.myMboCounter);
	}

	before(): getMboInstance(){

	}

	// pointcut setAttr(Mbo m):this(m) && set (* Mbo+.*);
	//
	// after(Mbo m):setAttr(m){
	// String className=thisJoinPoint.getThis().getClass().getName();
	// String sign=thisJoinPointStaticPart.getSignature().toString();
	// //System.out.println("********************Setting "+className+","+sign);
	//
	// String targetClass=thisJoinPoint.getTarget().getClass().toString();
	// String target=thisJoinPoint.getTarget().toString();
	//
	// Object argsO=thisJoinPoint.getArgs()[0];
	// String argsClass=(argsO==null)?"":argsO.getClass().toString();
	// String argsS=(argsO==null)?"":argsO.toString();
	//
	//
	//
	// //System.out.println("Target "+targetClass+"="+target+",args "+argsClass+"="+argsS);
	// // long counter=(m.myMboCounter==null)?localCnt:m.myMboCounter;
	// // MboDataStorage.getDataStorage().putAttribute(m.myMboCounter, sign,
	// thisJoinPoint.getArgs()[0]);
	// }

	// after (Mbo mr): createMbo(mr){
	// String className=mr.getClass().getName();
	// try {
	// System.out.println("<---Created new Mbo instance for "+className+" with uniqueId="+mr.getUniqueIDValue());
	// } catch (RemoteException e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// } catch (MXException e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// }

	// pointcut addMbo (): execution (* MboSet.add*(..));
	// Object around () :addMbo() {
	// System.out.println("---->1");
	// // new Throwable().printStackTrace();
	// MboRemote mr = (MboRemote)proceed();
	// if (mr==null){
	// return null;
	// }
	// System.out.println("---->2");
	// String className=mr.getClass().getName();
	//
	// System.out.println("---->3");
	// try {
	//
	// System.out.println("-->Adding new Mbo instance for "+className+" and uniqueid="+mr.getUniqueIDValue());
	// } catch (RemoteException e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// } catch (MXException e) {
	// // TODO Auto-generated catch block
	// e.printStackTrace();
	// }
	// return mr;
	// }
	//
	// before ():addMbo(){
	// System.out.println("___>Adding the mbo");
	// }

	pointcut getMboValueInstance(Mbo m, String attrName) : this (m) && args(attrName) && execution(* *.getMboValue(..));

	Object around(Mbo m, String attrName):getMboValueInstance(m,attrName){
		//System.out.println("Entering around");
		MboValue changedMboValue = getChangedMboValue(m, attrName);
		if (changedMboValue != null) {
			if (attrName.equalsIgnoreCase("attemptresult7")) {

				System.out.println("Found changed" + m.getName() + ", "
						+ attrName);
			}
			return changedMboValue;
		}
		if (attrName.equalsIgnoreCase("attemptresult7")) {
			new Throwable().printStackTrace();
			System.out.println("Not found " + m.getName() + ", " + attrName
					+ ", getting it from cache");
		}
		MboValueKey key = new MboValueKey(m.myMboCounter, attrName);
		JCS cache;
		Object el = null;
		try {
			cache = JCS.getInstance("mboValues");
			el = cache.get(key);
			if (el != null) {
				return el;
			}
			el = proceed(m, attrName);
			if (el != null) {
				cache.put(key, el);
			}
		} catch (CacheException e) {
			Throwable cause = e.getCause();
			System.out.println("***" + cause.getMessage());
			System.out.println("****************************************");
			// cause.printStackTrace();
			System.out.println("****************************************");
			// TODO Auto-generated catch block
			// e.printStackTrace();
		}
		//System.out.println("Exiting around"+el.getClass().getClassLoader().toString());

		if (m.ojsa){
			System.out.println("Exiting around::::"+el.getClass().getClassLoader().toString());
			System.out.println("Name==="+el.getClass().getName());
			if (el==null){
				
				
				System.out.println("El je null!");
			}
		}
		return el;
	}

	// pointcut setHashValue(): within (Mbo) && call ( * *.put(..));

	pointcut getMboValueHash(Mbo m) : this (m) && execution (* Mbo.getValueHash(..));

	Hashtable around(Mbo m):getMboValueHash(m){
		//System.out.println("CUT:"+getClass().getClassLoader());
		return new DummyValueHash(m);
	}

	pointcut putMboValue(): call (* *.put(..)) && within (Mbo) && withincode (* *.getMboValue(..));

	Object around():putMboValue(){
		return null;
	}

	pointcut setMboValue(MboValue mboVal): this(mboVal) && execution(* MboValue.setValue*(..));

	before(MboValue mboVal):setMboValue(mboVal){
		Mbo mbo = mboVal.getMbo();
		String attrName = mboVal.getAttributeName().toLowerCase();
//		System.out.println("Adding to changed " + mbo.getName() + ", "
//				+ attrName);
		if (attrName.equalsIgnoreCase("attemptresult7")) {

//			System.out.println("Adding to changed " + mbo.getName() + ", "
//					+ attrName);
		}
		addChangedMboValue(mbo, attrName, mboVal);
	}

	// void around (): setHashValue(){
	// return;
	// }

	pointcut validateAttributes(Mbo m) : this(m) && call (* Mbo.getValueHash(..)) && (withincode (* *.validateAttributes(..)) || withincode (* *.getModifiedPersistentAttributeValues(..))) ;

	Object around(Mbo m):validateAttributes(m){
		return m.changedMboValues;
	}

//	
}
