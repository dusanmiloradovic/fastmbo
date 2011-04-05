package storage;

import java.util.List;

import psdi.mbo.RowStampData;

interface MboRecordDataI {

	public abstract List getAttributeData();

	public abstract RowStampData getRowStampData();

	public abstract void setAttributeData(List list);

	public abstract void setRowStampData(RowStampData data);

	public abstract void setBaseLangaugeData(String attrName,
			Object baseLangString);

	public abstract Object getBaseLanguageData(String attrName);

}