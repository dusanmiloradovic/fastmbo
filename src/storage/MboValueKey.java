package storage;

import java.io.Serializable;

public class MboValueKey implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public long mboId;
	public String attrName;
	public boolean equals(Object arg0) {
		return ((MboValueKey)arg0).mboId==mboId && ((MboValueKey)arg0).attrName.equals(attrName);
	}
	public String toString() {
		return mboId+".."+attrName;
	}
	public MboValueKey(long mboId, String attrName) {
		super();
		this.mboId = mboId;
		this.attrName = attrName;
	}
}
