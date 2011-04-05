package storage;

import java.util.Hashtable;

import psdi.mbo.Mbo;
import psdi.util.MXException;

public class DummyValueHash extends Hashtable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Mbo m;

	public DummyValueHash(Mbo m) {
		this.m=m;
	}

	public Object put (Object key, Object value){return value;}

	public synchronized Object get(Mbo m,String attrName) throws MXException {
		return m.getMboValue(attrName);
	}

	public synchronized Object get(Object key) {
		
		try {
			return get(m,(String) key);
		} catch (MXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	
		
}
