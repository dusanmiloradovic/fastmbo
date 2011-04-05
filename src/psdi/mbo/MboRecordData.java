package psdi.mbo;

import java.util.HashMap;
import java.util.List;

class MboRecordData
{
  List attributeData;
  RowStampData rowStampData;
  HashMap baseLanguageData;

  MboRecordData()
  {
    this.attributeData = null;

    this.rowStampData = null;

    this.baseLanguageData = null;
  }

  public List getAttributeData()
  {
    return this.attributeData;
  }

  public RowStampData getRowStampData()
  {
    return this.rowStampData;
  }

  public void setAttributeData(List list)
  {
    this.attributeData = list;
  }

  public void setRowStampData(RowStampData data)
  {
    this.rowStampData = data;
  }

  public void setBaseLangaugeData(String attrName, Object baseLangString)
  {
    if (this.baseLanguageData == null)
      this.baseLanguageData = new HashMap(4);
    this.baseLanguageData.put(attrName.toUpperCase(), baseLangString);
  }

  public Object getBaseLanguageData(String attrName)
  {
    return this.baseLanguageData.get(attrName.toUpperCase());
  }
}