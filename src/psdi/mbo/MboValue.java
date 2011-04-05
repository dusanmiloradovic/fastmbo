package psdi.mbo;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import java.util.Vector;
import psdi.app.system.NUMValue;
import psdi.security.ConnectionKey;
import psdi.security.UserInfo;
import psdi.server.AppService;
import psdi.server.MXServer;
import psdi.server.ServiceRemote;
import psdi.util.BitFlag;
import psdi.util.MXAccessException;
import psdi.util.MXApplicationException;
import psdi.util.MXCipher;
import psdi.util.MXCipherX;
import psdi.util.MXException;
import psdi.util.MXFormat;
import psdi.util.MXSystemException;
import psdi.util.MaxType;

public abstract class MboValue
  implements MboConstants
{
  boolean toBeValidated;
  boolean init;
  protected MaxType currentValue;
  MaxType previousValue;
  MaxType initialValue;
  protected MboValueInfo mbovalueinfo;
  protected Mbo mbo;
  String defaultValue;
  boolean modified;
  boolean longDescription;
  MXException mxException;
  MaxType checkPointValue;
  MaxType checkPointPreviousValue;
  static Random random = null;
  boolean flagInitialized;
  boolean hasInitValue;
  long currentAccess;
  boolean guaranteedUnique;
  Vector mboValueListeners;
  BitFlag userFlags;
  private static long sequenceValue = 0L;

  public MboValue()
  {
    this.toBeValidated = false;

    this.init = false;

    this.currentValue = null;

    this.previousValue = null;

    this.initialValue = null;

    this.modified = false;

    this.longDescription = false;

    this.mxException = null;

    this.checkPointValue = null;
    this.checkPointPreviousValue = null;

    this.flagInitialized = false;

    this.hasInitValue = false;

    this.guaranteedUnique = false;

    this.mboValueListeners = new Vector();

    this.userFlags = new BitFlag();
  }

  public void construct(Mbo mbo, MboValueInfo mvInfo)
    throws MXException
  {
    this.mbo = mbo;
    this.mbovalueinfo = mvInfo;
    this.defaultValue = this.mbovalueinfo.getDefaultValue();

    Locale l = getMbo().getClientLocale();
    TimeZone tz = getMbo().getClientTimeZone();

    this.currentValue = new MaxType(l, tz, getType());
    this.currentValue.setMaxLength(getLength());
    this.currentValue.setScale(getScale());

    if ((mbo instanceof NUMValue) && (mvInfo.getName().equalsIgnoreCase("VALUE")))
    {
      try
      {
        MboRemote domainMbo = mbo.getMboSet("MAXDOMAIN").getMbo(0);

        if (domainMbo != null)
        {
          String type = domainMbo.getString("maxtype");
          if (!(type.equals("")))
          {
            if (type.equals("DECIMAL"))
              this.currentValue = new MaxType(l, tz, 9);
            else if (type.equals("INTEGER"))
              this.currentValue = new MaxType(l, tz, 6);
            else if (type.equals("FLOAT"))
              this.currentValue = new MaxType(l, tz, 8);
            this.currentValue.setMaxLength(domainMbo.getInt("length"));
            this.currentValue.setScale(domainMbo.getInt("SCALE"));
          }

        }

      }
      catch (RemoteException e)
      {
      }

    }

    this.longDescription = mvInfo.hasLongDescription();

    if (this.mbovalueinfo.isFetchAttribute())
    {
      setDatabaseValue(this.currentValue);
    }
    else if (this.currentValue.getType() == 12)
    {
      if ((this.defaultValue == null) || (this.defaultValue.equals(""))) {
        this.currentValue.setValue(false);
      }
      else {
        this.currentValue.setValue(MXFormat.stringToBoolean(this.defaultValue, l));
      }

    }

    this.previousValue = ((MaxType)this.currentValue.clone());

    if (!(mbo.getCheckpoint()))
      return;
    this.checkPointValue = ((MaxType)this.currentValue.clone());
    this.checkPointPreviousValue = this.checkPointValue;
  }

  private void setDatabaseValue(MaxType val)
    throws MXException
  {
    String attributeName = this.mbovalueinfo.getAttributeName();

    Object value = getMbo().getFetchValue(attributeName);
    if (value == null)
    {
      val.setValueNull();
      return;
    }

    try
    {
      switch (getType())
      {
      case 3:
      case 4:
      case 5:
        java.util.Date dt;
        if (value instanceof java.sql.Date)
          dt = new java.util.Date(((java.sql.Date)value).getTime());
        else
          dt = (java.util.Date)value;
        val.setValue(dt);
        break;
      case 8:
      case 9:
      case 10:
      case 11:
        val.setValue(((Number)value).doubleValue());
        break;
      case 6:
      case 7:
        val.setValue(((Number)value).longValue());
        break;
      case 12:
        if (((Number)value).intValue() == 1)
          val.setValue(true);
        else {
          val.setValue(false);
        }
        break;
      case 16:
      case 18:
        byte[] buf = (byte[])value;
        val.setValue(new MaxType(getType(), buf).asBytes());
        break;
      case 13:
      case 14:
      case 15:
      case 17:
      default:
        val.setValue(value.toString());
      }

    }
    catch (MXException e)
    {
      Object[] params = { attributeName, getMbo().getName(), value.toString() };
      throw new MXApplicationException("system", "fetchDBValueError", params, e);
    }
  }

  public void init()
    throws MXException
  {
    if (this.init)
    {
      return;
    }

    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      try
      {
        l.init();
      }
      catch (RemoteException re)
      {
        new MXSystemException("system", "remoteexception", re);
      }
    }

    this.init = true;
  }

  public void initValue()
    throws MXException
  {
    if (this.hasInitValue)
    {
      return;
    }

    this.hasInitValue = true;

    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      try
      {
        l.initValue();
      }
      catch (RemoteException re)
      {
        new MXSystemException("system", "remoteexception", re);
      }

    }

    this.initialValue = ((MaxType)this.currentValue.clone());
    this.previousValue = ((MaxType)this.currentValue.clone());
    this.modified = false;
  }

  public MboValueData getMboValueData()
  {
    return new MboValueData(this);
  }

  public MboValueData getMboValueData(boolean ignoreFieldFlags)
  {
    return new MboValueData(this, ignoreFieldFlags);
  }

  public Mbo getMbo()
  {
    return this.mbo;
  }

  protected MboServerInterface getMboServer()
  {
    return getMbo().getMboServer();
  }

  public String getDefault()
  {
    return this.defaultValue;
  }

  public boolean hasLongDescription()
  {
    return this.longDescription;
  }

  public void setDefault(String val)
  {
    this.defaultValue = val;
  }

  public void setValueNull(long accessModifier)
    throws MXException
  {
    setCurrentFieldAccess(accessModifier);
    checkFieldAccess(accessModifier);

    initValue();
    _setValueNull();
  }

  public void setValueNull() throws MXException
  {
    setValueNull(0L);
  }

  public void _setValueNull()
    throws MXException
  {
    MaxType t = (MaxType)this.currentValue.clone();
    t.setValueNull();
    setValue(t);
  }

  public boolean isNull()
    throws MXException
  {
    initValue();
    return ((this.currentValue == null) || (this.currentValue.isNull()));
  }

  public MaxType getCurrentValue()
    throws MXException
  {
    initValue();
    return this.currentValue;
  }

  public MaxType getInitialValue()
    throws MXException
  {
    initValue();
    return this.initialValue;
  }

  public MaxType getPreviousValue()
    throws MXException
  {
    initValue();
    return this.previousValue;
  }

  public final String getString()
    throws MXException
  {
    initValue();

    if (isNull()) {
      return "";
    }
    return _getLocaleString();
  }

  String _getString()
    throws MXException
  {
    return this.currentValue.asString();
  }

  String _getLocaleString()
    throws MXException
  {
    return this.currentValue.asLocaleString();
  }

  public final boolean getBoolean()
    throws MXException
  {
    initValue();

    return _getBoolean();
  }

  boolean _getBoolean()
    throws MXException
  {
    return this.currentValue.asBoolean();
  }

  public final byte getByte()
    throws MXException
  {
    initValue();

    return _getByte();
  }

  byte _getByte()
    throws MXException
  {
    throw new MXApplicationException("system", "invalidtype");
  }

  public final int getInt()
    throws MXException
  {
    initValue();

    return _getInt();
  }

  int _getInt()
    throws MXException
  {
    return this.currentValue.asInt();
  }

  public final long getLong()
    throws MXException
  {
    initValue();

    return _getLong();
  }

  long _getLong()
    throws MXException
  {
    return this.currentValue.asLong();
  }

  public final float getFloat()
    throws MXException
  {
    initValue();

    return _getFloat();
  }

  float _getFloat()
    throws MXException
  {
    return this.currentValue.asFloat();
  }

  public final double getDouble()
    throws MXException
  {
    initValue();

    return _getDouble();
  }

  double _getDouble()
    throws MXException
  {
    return this.currentValue.asDouble();
  }

  public final java.util.Date getDate()
    throws MXException
  {
    initValue();

    return _getDate();
  }

  java.util.Date _getDate()
    throws MXException
  {
    return this.currentValue.asDate();
  }

  public final byte[] getBytes()
    throws MXException
  {
    initValue();

    return _getBytes();
  }

  byte[] _getBytes()
    throws MXException
  {
    return this.currentValue.asBytes();
  }

  private void setValue(MaxType val)
    throws MXException
  {
    if (isFlagSet(32L)) {
      return;
    }

    if ((getCurrentFieldAccess() & 0x4) == 4L)
    {
      setFlag(32L, true);
    }

    if ((!(isFlagSet(16L))) && (val.equals(this.currentValue)))
    {
      this.modified = true;
      return;
    }

    if ((getCurrentFieldAccess() & 0x4) == 4L)
    {
      this.toBeValidated = true;
    }

    if (this.mbovalueinfo.isPositive())
    {
      switch (val.getType())
      {
      case 6:
      case 7:
      case 8:
      case 9:
      case 10:
      case 11:
        if (val.asDouble() < 0.0D)
        {
          String title = this.mbovalueinfo.getTitle();
          if ((title != null) && 
            (title.indexOf(",") >= 0))
          {
            StringBuffer strBuff = new StringBuffer(title);
            strBuff = strBuff.deleteCharAt(title.indexOf(","));
            title = strBuff.toString();
          }

          Object[] params = { title };
          throw new MXApplicationException("system", "mustbepositive", params);
        }
      }
    }

    MaxType savedValue = (MaxType)this.currentValue.clone();

    MaxType savedPrevValue = (MaxType)this.previousValue.clone();
    int keyCountOfOwner;
    Enumeration e;
    try {
      this.previousValue = ((MaxType)this.currentValue.clone());

      this.modified = true;

      if (isPersistent())
      {
        this.mbo.setModified(true);

        if ((this.mbovalueinfo.isESigEnabled()) && ((
          ((getCurrentFieldAccess() & 0x10) == 16L) || (this.mbo.isAutoKeyed(this.mbovalueinfo.getAttributeName())))))
        {
          this.mbo.setESigFieldModified(true);
        }

        if (this.mbovalueinfo.isEAuditEnabled())
        {
          this.mbo.setEAuditFieldModified(true);
        }

      }

      this.currentValue.setValue(val);
      validate();

      if ((getPropagateKeyFlag()) && (this.mbovalueinfo.isKey()))
      {
        for (Enumeration keyEnum = this.mbo.getRelatedSets().keys(); keyEnum.hasMoreElements(); )
        {
          String keyRelation = (String)keyEnum.nextElement();
          MboSetRemote tmpMsr = (MboSetRemote)this.mbo.getRelatedSets().get(keyRelation);

          if (tmpMsr.getExcludeMeFromPropagation()) {
            continue;
          }

          if (this.mbo.excludeObjectForPropagate(tmpMsr.getName())) {
            continue;
          }
          RelationInfo ri = this.mbo.getThisMboSet().getMboSetInfo().getRelationInfo(keyRelation);
          if (ri != null)
          {
            String relationship = ri.getSqlExpr();

            SqlFormat sf = new SqlFormat(this.mbo, relationship);
            tmpMsr.setRelationship(sf.format());
            if (!(sf.hasNullBoundValue())) {
              tmpMsr.setNoNeedtoFetchFromDB(false);
            }

            if ((!(tmpMsr instanceof NonPersistentMboSetRemote)) && 
              (!(tmpMsr.toBeSaved())))
            {
              tmpMsr.reset();
            }

          }

        }

        keyCountOfOwner = this.mbo.getThisMboSet().getMboSetInfo().getKeySize();

        for (e = this.mbo.getRelatedSets().elements(); e.hasMoreElements(); )
        {
          boolean keyExistInRelatedSets = false;

          MboSetRemote msr = (MboSetRemote)e.nextElement();

          if (msr.getExcludeMeFromPropagation()) {
            continue;
          }
          int keyCountOfRelatedSet = msr.getMboSetInfo().getKeySize();

          String[] cascadeMboKeys = msr.getMboSetInfo().getKeyAttributes();

          for (int j = 0; j < cascadeMboKeys.length; ++j)
          {
            if (this.mbovalueinfo.getAttributeName().compareToIgnoreCase(cascadeMboKeys[j]) != 0)
              continue;
            keyExistInRelatedSets = true;
            break;
          }

          for (int i = 0; msr.getMbo(i) != null; ++i)
          {
            Mbo cascadeMbo = (Mbo)msr.getMbo(i);

            if ((keyExistInRelatedSets) && (keyCountOfRelatedSet >= keyCountOfOwner))
            {
              if ((!(cascadeMbo.hasHierarchyLink())) && (this.previousValue.asString().equalsIgnoreCase(cascadeMbo.getString(this.mbovalueinfo.getAttributeName().toLowerCase()))))
              {
                cascadeMbo.setValue(this.mbovalueinfo.getAttributeName().toLowerCase(), getString(), 11L); continue;
              }

              cascadeMbo.propagateKeyValue(this.mbovalueinfo.getAttributeName().toLowerCase(), val.toString());

              if (cascadeMbo.propagateKeyValueImplemented) continue;
              break;
            }

            cascadeMbo.propagateKeyValue(this.mbovalueinfo.getAttributeName().toLowerCase(), val.toString());

            if (!(cascadeMbo.propagateKeyValueImplemented))
            {
              break;
            }

          }

        }

      }

    }
    catch (RemoteException re)
    {
      throw new MXSystemException("system", "remoteexception", re);
    }
    catch (MXException me)
    {
    	me.printStackTrace();
      this.modified = false;

      this.currentValue = savedValue;
      this.previousValue = savedPrevValue;
      throw me;
    }

    this.previousValue = savedValue;
  }

  public final void setValue(int value)
    throws MXException
  {
    setValue(value, 0L);
  }

  public final void setValue(int val, long accessModifier)
    throws MXException
  {
    initValue();
    try
    {
      setCurrentFieldAccess(accessModifier);
      checkFieldAccess(accessModifier);

      _setValue(val);
    }
    finally
    {
      resetCurrentFieldAccess();
    }
  }

  void _setValue(int value)
    throws MXException
  {
    MaxType t = (MaxType)this.currentValue.clone();
    t.setValue(value);
    setValue(t);
  }

  public final void setValue(String value)
    throws MXException
  {
    setValue(value, 0L);
  }

  public final void setValue(String val, long accessModifier)
    throws MXException
  {
    initValue();
    try
    {
      setCurrentFieldAccess(accessModifier);
      checkFieldAccess(accessModifier);

      if ((val == null) || (val.trim().length() == 0))
      {
        if (this instanceof MboLONGALNValue)
          ((MboLONGALNValue)this)._setValueNull(accessModifier);
        else {
          _setValueNull();
        }

      }
      else if (this instanceof MboLONGALNValue)
        ((MboLONGALNValue)this)._setValue(val.trim(), accessModifier);
      else {
        _setValue(val.trim());
      }
    }
    finally
    {
      resetCurrentFieldAccess();
    }
  }

  void _setValue(String val)
    throws MXException
  {
    if (getType() == 12)
    {
      if (val.equalsIgnoreCase("true"))
      {
        _setValue(true);
        return;
      }
      if (val.equalsIgnoreCase("false"))
      {
        _setValue(false);
        return;
      }
    }

    try
    {
      MaxType t = (MaxType)this.currentValue.clone();

      boolean synonymValueIsSet = false;

      StringBuffer buf = new StringBuffer(val);
      int stringLength = buf.length();
      if ((getType() != 15) && (getType() != 16) && (this.mbovalueinfo != null) && (stringLength > 2) && (buf.charAt(0) == '!') && (buf.charAt(stringLength - 1) == '!'))
      {
        DomainInfo domainInfo = this.mbovalueinfo.getDomainInfo();
        String domainName = null;
        Enumeration e;
        if (domainInfo != null)
        {
          MboValueListener l = domainInfo.getDomainObject(this);
          if (l instanceof SynonymDomain)
          {
            domainName = this.mbovalueinfo.getDomainName();
          }

        }
        else
        {
          for (e = this.mboValueListeners.elements(); e.hasMoreElements(); )
          {
            MboValueListener l = (MboValueListener)e.nextElement();
            if (l instanceof SynonymDomain)
            {
              domainName = ((SynonymDomain)l).getDomainId();
            }
          }
        }
        if ((domainName != null) && (domainName.trim().length() > 0))
        {
          Translate tr = getTranslator();
          String synonymValue = val.substring(1, stringLength - 1);
          String externalValue = tr.toExternalDefaultValue(domainName, synonymValue, getMbo());
          if ((externalValue != null) && (!(externalValue.equals(""))))
          {
            t.setValue(externalValue);
            synonymValueIsSet = true;
          }
        }
      }

      if (!(synonymValueIsSet)) {
        t.setValue(val);
      }
      switch (getType())
      {
      case 15:
        byte[] encVal = ((AppService)((MboSet)getMbo().getThisMboSet()).getMboServer()).getMXServer().getMXCipher().encData(val);
        t.setValue(encVal);
        break;
      case 16:
        byte[] encValX = ((AppService)((MboSet)getMbo().getThisMboSet()).getMboServer()).getMXServer().getMXCipherX().encData(val);
        t.setValue(encValX);
      }

      setValue(t);
    }
    catch (MXException me)
    {
      if ((me.getErrorGroup().equalsIgnoreCase("system")) && (me.getErrorKey().equalsIgnoreCase("maxlength")))
      {
        throw new MXApplicationException("system", "maxlength", new Object[] { (val.length() > 80) ? val.substring(0, 80) + "..." : val, getColumnTitle(), getMbo().getMboSetInfo(getMbo().getName()).getName() });
      }

      throw me;
    }
  }

  public final void setValue(boolean value)
    throws MXException
  {
    setValue(value, 0L);
  }

  protected Translate getTranslator()
  {
    return getMbo().getTranslator();
  }

  protected boolean getPropagateKeyFlag()
    throws MXException, RemoteException
  {
    return this.mbo.getPropagateKeyFlag();
  }

  public final void setValue(boolean val, long accessModifier)
    throws MXException
  {
    initValue();
    try
    {
      setCurrentFieldAccess(accessModifier);
      checkFieldAccess(accessModifier);

      _setValue(val);
    }
    finally
    {
      resetCurrentFieldAccess();
    }
  }

  void _setValue(boolean val)
    throws MXException
  {
    MaxType t = (MaxType)this.currentValue.clone();
    t.setValue(val);

    setValue(t);

    if ((t.isNull()) || (this.initialValue == null) || (!(this.initialValue.isNull())))
      return;
    this.initialValue.setValue(val);
    if ((this.previousValue != null) && (this.previousValue.isNull()))
      this.previousValue.setValue(val);
  }

  public final void setValue(byte value)
    throws MXException
  {
    setValue(value, 0L);
  }

  public final void setValue(byte val, long accessModifier)
    throws MXException
  {
    initValue();
    try
    {
      setCurrentFieldAccess(accessModifier);
      checkFieldAccess(accessModifier);

      _setValue(val);
    }
    finally
    {
      resetCurrentFieldAccess();
    }
  }

  void _setValue(byte val)
    throws MXException
  {
    throw new MXApplicationException("system", "invalidtype");
  }

  public final void setValue(long value)
    throws MXException
  {
    setValue(value, 0L);
  }

  public final void setValue(long val, long accessModifier)
    throws MXException
  {
    initValue();
    try
    {
      setCurrentFieldAccess(accessModifier);
      checkFieldAccess(accessModifier);

      _setValue(val);
    }
    finally
    {
      resetCurrentFieldAccess();
    }
  }

  void _setValue(long val)
    throws MXException
  {
    MaxType t = (MaxType)this.currentValue.clone();
    t.setValue(val);
    setValue(t);
  }

  public final void setValue(float value)
    throws MXException
  {
    setValue(value, 0L);
  }

  public final void setValue(float val, long accessModifier)
    throws MXException
  {
    initValue();
    try
    {
      setCurrentFieldAccess(accessModifier);
      checkFieldAccess(accessModifier);

      _setValue(val);
    }
    finally
    {
      resetCurrentFieldAccess();
    }
  }

  void _setValue(float val)
    throws MXException
  {
    MaxType t = (MaxType)this.currentValue.clone();
    t.setValue(val);
    setValue(t);
  }

  public final void setValue(double value)
    throws MXException
  {
    setValue(value, 0L);
  }

  public final void setValue(double val, long accessModifier)
    throws MXException
  {
    initValue();
    try
    {
      setCurrentFieldAccess(accessModifier);
      checkFieldAccess(accessModifier);

      _setValue(val);
    }
    finally
    {
      resetCurrentFieldAccess();
    }
  }

  void _setValue(double val)
    throws MXException
  {
    MaxType t = (MaxType)this.currentValue.clone();
    t.setValue(val);
    setValue(t);
  }

  public final void setValue(byte[] value)
    throws MXException
  {
    setValue(value, 0L);
  }

  public final void setValue(byte[] val, long accessModifier)
    throws MXException
  {
    initValue();
    try
    {
      setCurrentFieldAccess(accessModifier);
      checkFieldAccess(accessModifier);

      _setValue(val);
    }
    finally
    {
      resetCurrentFieldAccess();
    }
  }

  void _setValue(byte[] val)
    throws MXException
  {
    MaxType t = (MaxType)this.currentValue.clone();
    t.setValue(val);
    setValue(t);
  }

  public final void setValue(java.util.Date value)
    throws MXException
  {
    setValue(value, 0L);
  }

  public final void setValue(java.util.Date val, long accessModifier)
    throws MXException
  {
    initValue();
    try
    {
      setCurrentFieldAccess(accessModifier);
      checkFieldAccess(accessModifier);

      if (val == null)
        _setValueNull();
      else
        _setValue(val);
    }
    finally
    {
      resetCurrentFieldAccess();
    }
  }

  void _setValue(java.util.Date val)
    throws MXException
  {
    MaxType t = (MaxType)this.currentValue.clone();
    t.setValue(val);
    setValue(t);
  }

  public void setCurrentFieldAccess(long access)
  {
    this.currentAccess = access;
  }

  public void resetCurrentFieldAccess()
  {
    setCurrentFieldAccess(0L);
  }

  public long getCurrentFieldAccess()
  {
    return this.currentAccess;
  }

  public final void validate(long access) throws MXException {
    long holdAccess = getCurrentFieldAccess();
    setCurrentFieldAccess(access);
    try {
      validate();
    } finally {
      setCurrentFieldAccess(holdAccess);
    }
  }

  public final void validate()
    throws MXException
  {
    init();
    initValue();

    if ((!(isFlagSet(16L))) && (this.previousValue.equals(this.currentValue))) {
      return;
    }

    if ((getCurrentFieldAccess() & 0x9) == 9L) {
      return;
    }

    if ((getCurrentFieldAccess() & 0x4) == 4L)
    {
      this.toBeValidated = true;
      return;
    }
    Enumeration e;
    MboValueListener l;
    try
    {
      if (this.mbovalueinfo.isPersistent()) {
        getMbo().modify();
      }

      if ((getCurrentFieldAccess() & 1L) != 1L)
      {
        for (e = this.mboValueListeners.elements(); e.hasMoreElements(); )
        {
          l = (MboValueListener)e.nextElement();
          l.validate();
        }

      }

      if ((getCurrentFieldAccess() & 0x8) != 8L)
      {
        for (e = this.mboValueListeners.elements(); e.hasMoreElements(); )
        {
          l = (MboValueListener)e.nextElement();
          l.action();
        }
      }

    }
    catch (RemoteException re)
    {
      throw new MXSystemException("system", "remoteexception", re);
    }
  }

  public boolean isModified()
  {
    return this.modified;
  }

  public boolean isReadOnly()
  {
    try
    {
      checkFieldAccess(0L);
    } catch (MXException e) {
      return true;
    }
    return false;
  }

  public void setReadOnly(boolean ro)
  {
    setFlag(7L, ro);
  }

  public String getName()
  {
    return this.mbovalueinfo.getName();
  }

  public String getAttributeName()
  {
    return this.mbovalueinfo.getAttributeName();
  }

  public MboValueInfo getMboValueInfo()
  {
    return this.mbovalueinfo;
  }

  public int getLength()
  {
    return this.mbovalueinfo.getLength();
  }

  public int getScale()
  {
    return this.mbovalueinfo.getScale();
  }

  public String getColumnTitle()
  {
    try
    {
      return getMbo().getMboValueInfoStatic(getName()).getTitle();
    }
    catch (Exception e)
    {
    }

    return "";
  }

  public abstract int getType();

  public boolean hasList()
  {
    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      if (l.hasList()) {
        return true;
      }
    }
    return false;
  }

  public MboSetRemote getList()
    throws MXException, RemoteException
  {
    MboSetRemote tmpMsr = null;
    MboValueListener foundOne = null;
    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      if (l.hasList())
      {
        String whereClause = null;
        if (l instanceof MAXTableDomain)
        {
          whereClause = ((MAXTableDomain)l).getListCriteria();

          if ((whereClause == null) || (whereClause.trim().length() == 0))
          {
            if (foundOne != null)
              continue;
            foundOne = l;
          }

          foundOne = l;
          break;
        }

        foundOne = l;
      }
    }
    if (foundOne != null) {
      tmpMsr = foundOne.getList();
    }
    return tmpMsr;
  }

  public MboSetRemote smartFill(String value, boolean exact)
    throws MXException, RemoteException
  {
    MboSetRemote tmpMsr = null;
    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      if (l.hasList())
      {
        tmpMsr = l.smartFill(value, exact);
      }
    }

    return tmpMsr;
  }

  public MboSetRemote smartFind(String value, boolean exact)
    throws MXException, RemoteException
  {
    MboSetRemote tmpMsr = null;
    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      if (l.hasList())
      {
        tmpMsr = l.smartFind(value, exact);
      }
    }

    return tmpMsr;
  }

  public MboSetRemote smartFind(String object, String value, boolean exact)
    throws MXException, RemoteException
  {
    MboSetRemote tmpMsr = null;
    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      if (l.hasList())
      {
        tmpMsr = l.smartFind(object, value, exact);
      }
    }

    return tmpMsr;
  }

  public String getMatchingAttr()
    throws MXException, RemoteException
  {
    String retAttr = null;
    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      if (l.hasList())
      {
        retAttr = l.getMatchingAttr();
      }
    }

    return retAttr;
  }

  public String getMatchingAttr(String sourceObjectName)
    throws MXException, RemoteException
  {
    String retAttr = null;
    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      if (l.hasList())
      {
        retAttr = l.getMatchingAttr(sourceObjectName);
      }
    }

    return retAttr;
  }

  public boolean isRequired()
  {
    return ((this.mbovalueinfo.isRequired()) || (isFlagSet(8L)));
  }

  public void setRequired(boolean state)
  {
    setFlag(8L, state);
  }

  public boolean isPersistent()
  {
    return this.mbovalueinfo.isPersistent();
  }

  public boolean isGuaranteedUnique()
  {
    return this.guaranteedUnique;
  }

  public void setGuaranteedUnique(boolean flag)
  {
    this.guaranteedUnique = flag;
  }

  public void autoKey()
    throws MXException
  {
    ConnectionKey conKey = null;
    try
    {
      UserInfo userinfo = this.mbo.getUserInfo();
      MboSetInfo mbosetinfo = this.mbo.getThisMboSet().getMboSetInfo();

      conKey = new ConnectionKey(userinfo);
      Connection con = ((MboSet)this.mbo.getThisMboSet()).getMboServer().getDBConnection(conKey);
      AutoKey ak = new AutoKey(con, this.mbovalueinfo, userinfo, this.mbo, mbosetinfo);
      this.guaranteedUnique = true;
      setValue(ak.nextValue(), 2L);
    }
    catch (RemoteException ex)
    {
    }
    finally
    {
      this.guaranteedUnique = false;
      ((MboSet)this.mbo.getThisMboSet()).getMboServer().freeDBConnection(conKey);
    }
  }

  public void autoKeyByMboSiteOrg()
    throws MXException
  {
    ConnectionKey conKey = null;
    try
    {
      UserInfo userinfo = this.mbo.getUserInfo();
      MboSetInfo mbosetinfo = this.mbo.getThisMboSet().getMboSetInfo();

      conKey = new ConnectionKey(userinfo);
      Connection con = ((MboSet)this.mbo.getThisMboSet()).getMboServer().getDBConnection(conKey);
      AutoKey ak = new AutoKey(con, this.mbovalueinfo, userinfo, this.mbo, mbosetinfo);
      ak.setToUseMboSiteOrg(getMbo());
      this.guaranteedUnique = true;
      setValue(ak.nextValue(), 2L);
    }
    catch (RemoteException ex)
    {
    }
    finally
    {
      this.guaranteedUnique = false;
      ((MboSet)this.mbo.getThisMboSet()).getMboServer().freeDBConnection(conKey);
    }
  }

  public void addMboValueListener(MboValueListener l)
  {
    this.mboValueListeners.addElement(l);
  }

  public void removeMboValueListener(MboValueListener l)
  {
    this.mboValueListeners.removeElement(l);
  }

  public void checkFieldAccess(long accessModifier)
    throws MXException
  {
    if (!(getMbo().checkAccessForDelayedValidation())) {
      return;
    }

    if (new BitFlag(accessModifier).isFlagSet(2L)) {
      return;
    }

    getMbo().checkFieldAccess(accessModifier);
    try
    {
      if ((this.mbovalueinfo.isKey()) && (!(getMbo().toBeAdded())) && (!(getMbo().hasHierarchyLink())))
      {
        Object[]p = new Object[] { getColumnTitle() };
        if (this.mxException == null) {
          throw new MXAccessException("access", "field", p);
        }
        throw this.mxException;
      }
    }
    catch (RemoteException ex)
    {
    	ex.printStackTrace();
    }

    if (!(this.init)) {
      init();
    }

    if (!(isFlagSet(7L)))
      return;
    Object[] p = { getColumnTitle() };

    if (this.mxException == null) {
      throw new MXAccessException("access", "field", p);
    }
    throw this.mxException;
  }

  public void setFlags(long flags)
  {
    this.userFlags.setFlags(flags);
  }

  public void setFlags(long flags, MXException mxe)
  {
    this.mxException = mxe;
    setFlags(flags);
  }

  public long getFlags()
  {
    return this.userFlags.getFlags();
  }

  public void setFlag(long flag, boolean state)
  {
    getFieldFlagFromMbo();
    this.userFlags.setFlag(flag, state);
  }

  public void setFlag(long flag, boolean state, MXException mxe)
  {
    this.mxException = mxe;
    setFlag(flag, state);
  }

  public boolean isFlagSet(long flag)
  {
    getFieldFlagFromMbo();

    return this.userFlags.isFlagSet(flag);
  }

  public void getFieldFlagFromMbo()
  {
    try
    {
      if ((getMbo().needCallInitFieldFlag(getName())) || ((!(this.flagInitialized)) && (!(getMbo().hasFieldFlagsOnMbo(getName())))))
      {
        getMbo().initFieldFlagsOnMbo(getName());
      }
      if (getMbo().hasFieldFlagsOnMbo(getName()))
      {
        getMbo().moveFieldFlagsToMboValue(this);
      }
    }
    catch (MXException e) {
      this.mxException = e;
    }

    this.flagInitialized = true;
  }

  public MXException getMXException()
  {
    return this.mxException;
  }

  public synchronized void generateUniqueID()
    throws MXException
  {
    ConnectionKey conKey = null;
    try
    {
      conKey = getMbo().getUserInfo().getConnectionKey();
      Connection con = getMbo().getMboServer().getDBConnection(conKey);

      if (!(isPersistent()))
      {
        throw new MXSystemException("system", "uniqueidnp");
      }

      String columnName = getMboValueInfo().getEntityColumnName();
      String tbName = getMbo().getMboSetInfo().getEntity().getTableName(columnName);

      this.guaranteedUnique = true;

      setValue(new Long(MaxSequence.generateKey(con, tbName, columnName)).toString(), 2L);
    }
    catch (RemoteException ex)
    {
    }
    finally
    {
      getMbo().getMboServer().freeDBConnection(conKey);
      this.guaranteedUnique = false;
    }
  }

  public boolean isToBeValidated()
  {
    return this.toBeValidated; }

  public void setToBeValidated(boolean value) {
    this.toBeValidated = value;
  }

  public ServiceRemote getIntegrationService()
    throws RemoteException, MXException
  {
    return MXServer.getMXServer().lookupLocal("INTEGRATION");
  }

  public void rollbackToCheckpoint()
  {
    if (this.checkPointValue == null)
      return;
    this.currentValue = ((MaxType)this.checkPointValue.clone());
    this.previousValue = ((MaxType)this.checkPointPreviousValue.clone());

    this.checkPointValue = null;
    this.checkPointPreviousValue = null;
  }

  public void takeCheckpoint()
  {
    this.checkPointValue = ((MaxType)this.currentValue.clone());
    this.checkPointPreviousValue = ((MaxType)this.previousValue.clone());
  }

  public void setValueFromLookup(MboRemote sourceMbo)
    throws MXException, RemoteException
  {
    MboSetRemote tmpMsr = null;
    MboValueListener foundOne = null;
    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      if (l.hasList())
      {
        String whereClause = null;
        if (l instanceof MAXTableDomain)
        {
          whereClause = ((MAXTableDomain)l).getListCriteria();

          if ((whereClause == null) || (whereClause.trim().length() == 0))
          {
            if (foundOne != null)
              continue;
            foundOne = l;
          }

          foundOne = l;
          break;
        }
      }

    }

    if (foundOne != null)
      foundOne.setValueFromLookup(sourceMbo);
  }

  public String[] getAppLink()
    throws MXException, RemoteException
  {
    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      String[] appLink = l.getAppLink();
      if (appLink != null)
        return appLink;
    }
    return null;
  }

  public String getLookupName()
    throws MXException, RemoteException
  {
    for (Enumeration e = this.mboValueListeners.elements(); e.hasMoreElements(); )
    {
      MboValueListener l = (MboValueListener)e.nextElement();
      String name = l.getLookupName();
      if (name != null)
        return name;
    }
    return null;
  }
}