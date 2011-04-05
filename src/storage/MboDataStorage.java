package storage;

import java.util.HashMap;
import java.util.Map;

public class MboDataStorage {

	private Map storage;
	private  static MboDataStorage singleton;
	
	private MboDataStorage(){
		this.storage=new HashMap();
	}
	
	public void initMboStorage(long mboId){
		storage.put(new Long(mboId), new HashMap());
	}
	
	public void removeMboStorage(long mboId){
		storage.remove(new Long(mboId));
	}
	
	public void putAttribute(long mboId, String attributeName,Object attrVal){
		Map mapStorage = (Map) storage.get(new Long(mboId));
		if (mapStorage==null){
			mapStorage = new HashMap();
			storage.put(new Long(mboId), mapStorage);
		}
		mapStorage.put(attributeName, attrVal);
	}
	
	public Object getAttribute(long mboId, String attributeName){
		return ((Map)((Map) storage.get(new Long(mboId)))).get(attributeName);
	}
	
	public static MboDataStorage getDataStorage(){
		if (singleton==null){
			singleton=new MboDataStorage();
		}
		return singleton;
	}
	
	public  static long getSize(){
		return singleton.storage.size();
	}
	
}
