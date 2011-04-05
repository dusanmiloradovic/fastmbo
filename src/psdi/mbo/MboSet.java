package psdi.mbo;

import java.io.StringReader;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Vector;
import oracle.jdbc.OraclePreparedStatement;
import oracle.sql.BLOB;
import oracle.sql.CLOB;
import psdi.app.common.virtual.DrillDown;
import psdi.app.sets.SetsServiceRemote;
import psdi.app.signature.SignatureCache;
import psdi.app.signature.SignatureServiceRemote;
import psdi.security.ProfileRemote;
import psdi.security.SecurityServiceRemote;
import psdi.security.UserInfo;
import psdi.server.ConRef;
import psdi.server.DBManager;
import psdi.server.MXServer;
import psdi.server.MaxVarServiceRemote;
import psdi.server.PerformanceStats;
import psdi.txn.MXTransaction;
import psdi.util.BitFlag;
import psdi.util.CombineWhereClauses;
import psdi.util.MXAccessException;
import psdi.util.MXApplicationException;
import psdi.util.MXCipher;
import psdi.util.MXException;
import psdi.util.MXFormat;
import psdi.util.MXInactiveUserException;
import psdi.util.MXMath;
import psdi.util.MXObjectNotFoundException;
import psdi.util.MXRowUpdateException;
import psdi.util.MXSystemException;
import psdi.util.MaxType;
import psdi.util.Message;
import psdi.util.logging.FixedLoggers;
import psdi.util.logging.MXLogger;

public abstract class MboSet extends UnicastRemoteObject
  implements MboSetRemote, MboConstants, FixedLoggers
{
  HashMap defaultValue = null;

  HashMap jspDefaultValue = null;
  static final char SEPCHAR = 47;
  MboServerInterface mboServer = null;

  MboSetInfo mbosetinfo = null;

  private static HashMap dbColumnNameCache = null;

  private String app = null;

  String whereClause = "";

  private String userWhereClause = "";

  private String selectionWhereClause = "";

  private String setOrderByForUI = "";

  String sqlOptions = "";

  String relationshipWhere = "";

  private String relationName = null;

  String orderByClause = "";

  protected Vector mboVec = null;

  MboQbe qbe = null;

  MboRemote currMbo = null;

  int currIndex = -1;

  boolean allFetched = false;

  private BitFlag userFlags = null;

  UserInfo user = null;

  MboRemote ownerMbo = null;
  static final int FETCH_RANGE = 1;
  Hashtable dbCache = null;

  Vector newlyCreated = null;

  MXException mxException = null;

  private Boolean processML = null;

  protected boolean toBeSaved = false;

  private boolean logLargFetchResultDisabled = false;

  private int fetchIndex = 0;

  private boolean fetchRelatedMbosOfOwnersChildren = false;

  private int fetchRelatedMboIndex = -1;

  private String relationshipUsedForThis = null;

  private int fetchNextIndex = -1;

  private boolean eSigFieldModified = false;

  private boolean eAuditFieldModified = false;

  private static ThreadLocal eAuditTransId = new ThreadLocal();
  private static ThreadLocal eSigTransId = new ThreadLocal();

  private String lastESigTransId = null;

  private boolean tableDomainLookup = false;

  private boolean setAutoKey = true;

  private String insertSiteForSet = null;

  private String insertOrgForSet = null;

  private String insertItemsetForSet = null;

  private String insertCompanySetForSet = null;

  private boolean excludeMeFromPropagation = false;

  private boolean noNeedtoFetchFromDB = false;

  boolean readOptionFastUse = false;
  boolean canUseFast = false;
  int optionNum = 0;

  Vector sqlTableScanExcludeList = null;

  String selectQuery = "";

  private MboRemote discardableMbo = null;

  private long transactionStatus = -1L;

  private static String mboSetDBProductName = null;

  private MboSetConnectionDetails connectionDetail = new MboSetConnectionDetails();

  private MboSetCounter mboSetCounter = null;

  private MboRemote zombieMbo = null;

  boolean preserveOrderByCase = false;

  int discardableCurrentIndex = -1;

  private int createdCount = 0;

  private int deletedCount = 0;

  private Hashtable methodAccessList = null;

  Hashtable sharedSets = null;

  MXTransaction activeTransaction = null;

  MXTransaction potentialTransaction = null;
  long saveFlags;
  private static long sqlTimeLimit = 0L;
  private static boolean readSQLTimeLimit = false;
  private static long fetchResultLogLimit = 0L;
  private static boolean readFetchResultLogLimit = false;

  private String applicationClause = null;

  private HashMap integrationKeyMbos = null;
  static final String ESCAPECHAR = new String(new char[] { '\27' });

  private static int sqlServerPrefetchRows = 0;
  private static boolean readSQLServerPrefetchRows = false;
  private static final int DEFAULT_SQLSERVER_PREFETCHROWS = 198;
  public static ThreadLocal perfStats = new ThreadLocal();

  public void setNoNeedtoFetchFromDB(boolean flag)
    throws RemoteException
  {
    this.noNeedtoFetchFromDB = flag;
  }

  public MboSet(MboServerInterface ms)
    throws RemoteException
  {
    this.mboServer = ms;

    this.connectionDetail.setMboServer(ms);
    this.mboSetCounter = new MboSetCounter(getMboServer(), this.connectionDetail);
    if (mboSetDBProductName != null)
      return;
    mboSetDBProductName = MXServer.getMXServer().getDBManager().getDatabaseProductName();
  }

  public final void init(UserInfo user)
    throws MXException, RemoteException
  {
    this.mboSetCounter.setName(getName());

    this.currIndex = -1;
    this.currMbo = null;
    this.user = user;

    this.mboVec = new Vector();

    if (this.qbe == null) {
      this.qbe = new MboQbe(getMboSetInfo(), getClientLocale(), getClientTimeZone(), processML(), getUserInfo());
    }
    this.connectionDetail.init();
    this.allFetched = false;

    if ((this.ownerMbo == null) || (!(this.ownerMbo.isZombie())))
      init();
  }

  public void init()
    throws MXException, RemoteException
  {
  }

  public void initDataDictionary()
  {
  }

  public void setApp(String appName)
    throws RemoteException
  {
    this.app = appName;
    try
    {
      ProfileRemote profile = getProfile();

      String siteorg = null;

      boolean isSetLevelaApp = false;
      int level = profile.getAppLevel(getApp());
      if ((level == 3) || (level == 4)) {
        isSetLevelaApp = true;
      }

      if ((profile.orgLevelApp(appName)) || (isSetLevelaApp))
      {
        if ((getInsertOrg() != null) && (getInsertOrg().trim().length() > 0))
          siteorg = getInsertOrg();
        else
          siteorg = profile.getDefaultOrg();
      }
      else if (profile.siteLevelApp(appName))
      {
        if ((getInsertSite() != null) && (getInsertSite().trim().length() > 0))
          siteorg = getInsertSite();
        else {
          siteorg = profile.getDefaultSite();
        }
      }

      if ((profile.orgLevelApp(appName)) || (profile.siteLevelApp(appName)) || (isSetLevelaApp))
      {
        if ((siteorg == null) || (siteorg.trim().length() == 0))
        {
          setFlag(1L, true);
        }
        else if (!(profile.getAppOptionAuth(appName, "SAVE", siteorg)))
        {
          setFlag(1L, true);
        }
      }
    }
    catch (Exception e)
    {
    }
  }

  public String getApp()
    throws RemoteException
  {
    return this.app;
  }

  public MboValueData getMboSetValueData(String attribute)
    throws MXException, RemoteException
  {
    MboValueInfo mvi = getMboSetInfo().getMboValueInfo(attribute.toUpperCase());

    if (mvi == null) {
      return null;
    }
    return getZombie().getMboValueData(attribute);
  }

  public MboRemote getZombie()
    throws RemoteException
  {
    synchronized (this)
    {
      if (this.zombieMbo == null)
      {
        try
        {
          this.zombieMbo = getMboInstance(this);
          this.zombieMbo.setFlag(7L, true);
          ((Mbo)this.zombieMbo).setZombie(true);
        }
        catch (Throwable t)
        {
          if (getMboLogger().isErrorEnabled())
          {
            getMboLogger().error("MboSet getZombie :", t);
          }
        }
      }
    }

    return this.zombieMbo;
  }

  public MboRemote getOwner()
  {
    return this.ownerMbo;
  }

  public void setOwner(MboRemote mbo)
    throws MXException, RemoteException
  {
    this.ownerMbo = mbo;
  }

  public MboSetData getMboSetData(String[] attributes)
    throws RemoteException
  {
    MboSetData msd = new MboSetData(this);
    msd.setMboData(new MboData[] { getMboData(attributes) });
    return msd;
  }

  public MboSetData getMboSetData(int row, int count, String[] attributes)
    throws MXException, RemoteException
  {
    MboSetData msd = new MboSetData(this);
    msd.setMboData(getMboData(row, count, attributes));
    return msd;
  }

  public MboValueData[] getMboSetValueData(String[] attribute)
    throws MXException, RemoteException
  {
    MboValueData[] result = new MboValueData[attribute.length];

    for (int i = 0; i < attribute.length; ++i)
    {
      try
      {
        result[i] = getMboSetValueData(attribute[i]);
      }
      catch (MXException e)
      {
        result[i] = null;
      }
    }

    return result;
  }

  private MboData getMboData(String[] attributes)
    throws RemoteException
  {
    Mbo mbo = null;

    if (getCurrentPosition() == -1)
      mbo = (Mbo)getZombie();
    else {
      mbo = (Mbo)getMbo();
    }
    return mbo.getMboData(attributes);
  }

  private MboData[] getMboData(int row, int count, String[] attributes)
    throws MXException, RemoteException
  {
    if (isFlagSet(39L))
    {
      MboData[] finalResult = null;

      if (row <= this.currIndex)
      {
        throw new MXApplicationException("system", "invalidfetch");
      }

      MboData[] result = new MboData[count];

      if (count <= 0)
      {
        return result;
      }

      int i = 0;
      int k = row;
      while (true)
      {
        MboRemote mbo = getMbo(k);
        if (mbo == null)
        {
          break;
        }

        result[i] = mbo.getMboData(attributes);
        ++i;
        ++k;

        if (i >= count) {
          break;
        }
      }
      if (i == count)
      {
        finalResult = result;
      }

      if (i < count)
      {
        finalResult = new MboData[i];
        for (int j = 0; j < i; ++j)
        {
          finalResult[j] = result[j];
        }
      }

      return finalResult;
    }

    getMbo(row + count - 1);

    int lengthToFetch = getSize() - row;
    lengthToFetch = (lengthToFetch > count) ? count : (lengthToFetch < 0) ? 0 : lengthToFetch;

    MboData[] result = new MboData[lengthToFetch];

    for (int i = 0; i < lengthToFetch; ++i) {
      result[i] = getMbo(i + row).getMboData(attributes);
    }
    return result;
  }

  public MboValueData getMboValueData(String attribute)
    throws MXException, RemoteException
  {
    if (getCurrentPosition() == -1) {
      return getMboSetValueData(attribute);
    }
    return getMbo().getMboValueData(attribute);
  }

  public MboValueData[] getMboValueData(String[] attribute)
    throws MXException, RemoteException
  {
    if (getCurrentPosition() == -1) {
      return getMboSetValueData(attribute);
    }
    return getMbo().getMboValueData(attribute);
  }

  public MboValueInfoStatic getMboValueInfoStatic(String attribute)
    throws MXException, RemoteException
  {
    if (getCurrentPosition() != -1) {
      return getMbo().getMboValueInfoStatic(attribute);
    }
    return getZombie().getMboValueInfoStatic(attribute);
  }

  public MboValueInfoStatic[] getMboValueInfoStatic(String[] attribute)
    throws MXException, RemoteException
  {
    if (getCurrentPosition() != -1) {
      return getMbo().getMboValueInfoStatic(attribute);
    }
    return getZombie().getMboValueInfoStatic(attribute);
  }

  public MboValueData[][] getMboValueData(int rowStart, int rowCount, String[] attribute)
    throws MXException, RemoteException
  {
    if (isFlagSet(39L))
    {
      MboValueData[][] finalResult = (MboValueData[][])null;

      if (rowStart <= this.currIndex)
      {
        throw new MXApplicationException("system", "invalidfetch");
      }

      MboValueData[][] result = new MboValueData[rowCount][];

      if (rowCount <= 0)
      {
        return result;
      }

      int i = 0;
      int k = rowStart;
      while (true)
      {
        MboRemote mbo = getMbo(k);
        if (mbo == null)
        {
          break;
        }

        result[i] = mbo.getMboValueData(attribute);
        ++i;
        ++k;

        if (i >= rowCount) {
          break;
        }
      }
      if (i == rowCount)
      {
        finalResult = result;
      }

      if (i < rowCount)
      {
        finalResult = new MboValueData[i][];
        for (int j = 0; j < i; ++j)
        {
          finalResult[j] = result[j];
        }
      }

      return finalResult;
    }

    getMbo(rowStart + rowCount - 1);
    int lengthToFetch = getSize() - rowStart;
    lengthToFetch = (lengthToFetch > rowCount) ? rowCount : (lengthToFetch < 0) ? 0 : lengthToFetch;

    MboValueData[][] result = new MboValueData[lengthToFetch][];

    for (int i = 0; i < lengthToFetch; ++i) {
      result[i] = getMbo(i + rowStart).getMboValueData(attribute);
    }
    return result;
  }

  public Locale getClientLocale()
  {
    if (this.user == null)
    {
      return Locale.getDefault();
    }

    return this.user.getLocale();
  }

  public TimeZone getClientTimeZone()
  {
    if (this.user == null)
    {
      return TimeZone.getDefault();
    }

    return this.user.getTimeZone();
  }

  public void setWhere(String whereClause)
  {
    int whereIndex = 0;
    String stmt = whereClause.trim();
    String lowercaseStmt = stmt.toLowerCase();

    if ((lowercaseStmt.startsWith("where ")) || (lowercaseStmt.startsWith("where(")))
    {
      whereIndex = 5;
    }
    this.whereClause = stmt.substring(whereIndex);
  }

  public String getWhere()
  {
    return this.whereClause;
  }

  public void setUserWhere(String userWhere)
    throws MXException, RemoteException
  {
    String trimmedWhere = userWhere.trim();

    if (trimmedWhere.toLowerCase().startsWith("where "))
    {
      this.userWhereClause = trimmedWhere.substring(5);
    }
    else
    {
      this.userWhereClause = userWhere;
    }
  }

  public void setUserWhereAfterParse(String where)
    throws MXException, RemoteException
  {
    String storedQuery = where;

    String[] sep = seperateOrderBy(storedQuery);
    if (sep == null) {
      setUserWhere(where);
    }
    else {
      setUserWhere(sep[0]);
      setOrderBy(sep[1]);

      if ((sep[1] != null) && (sep[1].trim().length() > 0))
        this.setOrderByForUI = " order by " + sep[1];
    }
  }

  public String getUserWhere()
    throws MXException, RemoteException
  {
    return getUserWhere("");
  }

  public String getUserWhere(String alias)
    throws MXException, RemoteException
  {
    String userWhereSQL = this.userWhereClause;

    SqlFormat formatter = new SqlFormat(getUserInfo(), this.userWhereClause);
    userWhereSQL = formatter.format();
    try
    {
      Connection conn = getMboServer().getDBConnection(getUserInfo().getConnectionKey());
      userWhereSQL = conn.nativeSQL(userWhereSQL);
    }
    catch (SQLException ex)
    {
      ex.printStackTrace();
    }
    finally
    {
      getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
    }

    return userWhereSQL;
  }

  public String getUserAndQbeWhere()
    throws MXException, RemoteException
  {
    String userAndQbeWhere = "";

    CombineWhereClauses cwc = new CombineWhereClauses(new String(this.userWhereClause));

    cwc.addWhere(getQbeWhere());

    cwc.addWhere(getUserPrefWhere());

    userAndQbeWhere = cwc.getWhereClause();

    SqlFormat formatter = new SqlFormat(getUserInfo(), userAndQbeWhere);
    String userAndQbeWhereSQL = formatter.format();

    if ((this.setOrderByForUI != null) && (this.setOrderByForUI.trim().length() > 0))
      userAndQbeWhereSQL = userAndQbeWhereSQL + this.setOrderByForUI;
    try
    {
      Connection conn = getMboServer().getDBConnection(getUserInfo().getConnectionKey());
      userAndQbeWhereSQL = conn.nativeSQL(userAndQbeWhereSQL);
    }
    catch (SQLException ex)
    {
      ex.printStackTrace();
    }
    finally
    {
      getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
    }

    return userAndQbeWhereSQL;
  }

  public void useStoredQuery(String queryName)
    throws MXException, RemoteException
  {
    if (this.app == null) {
      throw new MXApplicationException("system", "invalidAppName", new Object[] { "null" });
    }

    SignatureServiceRemote sr = (SignatureServiceRemote)MXServer.getMXServer().lookup("SIGNATURE");
    if (sr == null)
      return;
    setUserWhereAfterParse(sr.getQuerySql(queryName, this.app, getUserInfo()));
  }

  private String[] seperateOrderBy(String query)
  {
    String lowerQuery = query.toLowerCase();
    int indexOrder = lowerQuery.indexOf(" order ");
    if (indexOrder == -1)
      return null;
    int indexBy;
    if ((indexBy = lowerQuery.substring(indexOrder + 7).trim().indexOf("by ")) != -1)
    {
      char[] chars = query.substring(indexOrder + 7).trim().substring(3).toCharArray();
      int pCount = 0;
      for (int i = 0; i < chars.length; ++i)
      {
        if (chars[i] == '"')
          return null;
        if (chars[i] == '(')
          ++pCount;
        else if (chars[i] == ')')
          --pCount;
        else if (chars[i] == '\'')
          return null;
      }
      if (pCount != 0) {
        return null;
      }
      return new String[] { query.substring(0, indexOrder), query.substring(indexOrder + 7).trim().substring(3) };
    }

    return null;
  }

  public void setSQLOptions(String sqlOptions)
    throws RemoteException
  {
    this.sqlOptions = sqlOptions;
  }

  public String getSQLOptions()
    throws RemoteException
  {
    return this.sqlOptions;
  }

  public MboRemote findKey(Object keyObject)
    throws MXException, RemoteException
  {
    String[] keys;
    if (keyObject instanceof String[])
      keys = (String[])keyObject;
    else if (keyObject instanceof String)
      keys = new String[] { (String)keyObject };
    else {
      throw new MXSystemException("system", "keyformat");
    }

    String[] keyInfo = getMboSetInfo().getKeyAttributes();
    String findKeyClause = "";
    for (int i = 0; i < keys.length; ++i)
    {
      if (i > 0) {
        findKeyClause = findKeyClause + " AND ";
      }
      findKeyClause = findKeyClause + keyInfo[i] + " = :" + (i + 1);
    }

    SqlFormat fmt = new SqlFormat(getUserInfo(), findKeyClause);
    for (int i = 0; i < keys.length; ++i) {
      fmt.setObject(i + 1, getName(), keyInfo[i], keys[i]);
    }
    findKeyClause = fmt.format();

    setWhere(fmt.format());
    reset();

    return moveNext();
  }

  public void setRelationship(String relationClause)
  {
    this.relationshipWhere = relationClause;
  }

  public String getRelationship()
  {
    return this.relationshipWhere;
  }

  public void setOrderBy(String orderByClause)
    throws MXException
  {
    this.orderByClause = orderByClause;
  }

  public String getOrderBy()
  {
    return this.orderByClause;
  }

  public void setDefaultOrderBy()
    throws MXException
  {
    String[] attributes = getMboSetInfo().getKeyAttributes();
    if (attributes.length == 0)
      return;
    StringBuffer str = new StringBuffer("");
    for (int i = 0; i < attributes.length; ++i)
    {
      if (attributes[i] == null)
        continue;
      if (i > 0)
        str.append(", ");
      str.append(attributes[i].toLowerCase());
    }

    setOrderBy(str.toString());
  }

  public void setPreserveOrderByCase(boolean value)
    throws RemoteException
  {
    this.preserveOrderByCase = value;
  }

  public MboRemote moveToKey(KeyValue keyVal)
    throws MXException, RemoteException
  {
    if (keyVal == null) {
      return null;
    }

    for (int i = 0; getMbo(i) != null; ++i)
    {
      MboRemote mbo = getMbo(i);
      if (mbo.getKeyValue().equals(keyVal)) {
        return moveTo(i);
      }
    }
    return null;
  }

  public MboRemote moveNext()
    throws MXException, RemoteException
  {
    MboRemote mbo = getMbo(this.currIndex + 1);

    if (mbo != null)
    {
      this.currIndex += 1;
      this.currMbo = mbo;
    }

    return mbo;
  }

  public MboRemote movePrev()
    throws MXException, RemoteException
  {
    if (this.currIndex <= 0) {
      return null;
    }
    this.currIndex -= 1;
    this.currMbo = getMbo(this.currIndex);
    return this.currMbo;
  }

  public MboRemote moveFirst()
    throws MXException, RemoteException
  {
    MboRemote mbo = getMbo(0);

    if (mbo != null)
    {
      this.currIndex = 0;
      this.currMbo = mbo;
    }

    return mbo;
  }

  public MboRemote moveLast()
    throws MXException, RemoteException
  {
    if (isFlagSet(39L))
    {
      MboRemote prevMboRemote = null;
      while (true)
      {
        int index = this.currIndex + 1;
        MboRemote mr = getDiscardableMbo(index);
        if (mr == null)
        {
          return prevMboRemote;
        }

        prevMboRemote = mr;
      }

    }

    fetchMbos();
    if (getSize() > 0)
    {
      this.currIndex = (getSize() - 1);
      this.currMbo = getMbo(this.currIndex);
    }
    else {
      return null;
    }
    return this.currMbo;
  }

  public MboRemote moveTo(int pos)
    throws MXException, RemoteException
  {
    MboRemote mbo = getMbo(pos);

    if (mbo != null)
    {
      this.currMbo = mbo;
      this.currIndex = pos;
    }

    return mbo;
  }

  public int getCurrentPosition()
  {
    return this.currIndex;
  }

  public MboRemote getMbo()
  {
    return this.currMbo;
  }

  public MboRemote getMbo(int index)
    throws MXException, RemoteException
  {
    if (isFlagSet(39L))
    {
      return getDiscardableMbo(index);
    }

    if (index < 0) {
      return null;
    }

    String dbProductName = mboSetDBProductName;
    if ((isSQLServerPrefetchRowsNeeded()) && (dbProductName.equalsIgnoreCase("Microsoft SQL Server")) && ((((getApp() != null) && (this.ownerMbo == null)) || (isTableDomainLookup()))))
    {
      if (index == getSize() - 1)
      {
        Mbo mbo = (Mbo)this.mboVec.elementAt(index);
        return mbo;
      }

      fetchMbos(getSQLServerPrefetchRows());
      close();
    }
    else
    {
      fetchMbos(index);
    }

    if (index >= getSize()) {
      return null;
    }
    Mbo mbo = (Mbo)this.mboVec.elementAt(index);

    return mbo;
  }

  private MboRemote getDiscardableMbo(int index)
    throws MXException, RemoteException
  {
    MboRecordData nextRecord = null;
    if (index < 0)
    {
      return null;
    }

    if (index < this.discardableCurrentIndex)
      throw new MXApplicationException("system", "invalidfetch");
    if (index == this.discardableCurrentIndex) {
      return this.discardableMbo;
    }

    int mboVecSize = (this.mboVec == null) ? 0 : this.mboVec.size();
    int i;
    if ((mboVecSize > 0) && (index - this.discardableCurrentIndex <= mboVecSize))
    {
      this.discardableMbo = ((Mbo)this.mboVec.get(index - this.discardableCurrentIndex - 1));
      for (i = 0; i < index - this.discardableCurrentIndex; ++i)
        this.mboVec.remove(i);
      this.discardableCurrentIndex = index;

      if (this.currIndex <= index)
        this.currIndex = index;
      return this.discardableMbo;
    }
    if ((mboVecSize > 0) && (index - this.discardableCurrentIndex > mboVecSize))
    {
      for (i = 0; i < index - this.discardableCurrentIndex; ++i)
        this.mboVec.remove(i);
      this.discardableCurrentIndex += mboVecSize;
    }

    if (isClosed())
    {
      if (nextRecord != null)
      {
        this.discardableCurrentIndex = (this.currIndex = index);
        nextRecord = null;
        this.discardableMbo = generateMboInstance(nextRecord, index);
        return this.discardableMbo;
      }

      return null;
    }

    try
    {
      Mbo mbo = null;
      boolean done = false;
      for (int ii = this.discardableCurrentIndex; ii < index + 1; ++ii)
      {
        if ((nextRecord == null) || (ii > this.discardableCurrentIndex))
          nextRecord = getNextRecordData();
        if (nextRecord == null)
        {
          close();

          break;
        }
        if (!(done))
          this.discardableCurrentIndex += 1;
        if (this.discardableCurrentIndex != index) {
          continue;
        }
        if (!(done))
        {
          mbo = generateMboInstance(nextRecord, this.currIndex);
        }
        done = true;
      }

      this.discardableMbo = mbo;
      this.currIndex = index;
      return mbo;
    }
    catch (SQLException e)
    {
      Object[] params = { new Integer(e.getErrorCode()).toString() };

      if (getSqlLogger().isErrorEnabled())
      {
        getSqlLogger().error("MboSet getDiscardableMbo() failed with SQL error code : " + ((String)params[0]), e);
      }

      throw new MXSystemException("system", "sql", params, e);
    }
  }

  private final Mbo generateMboInstance(MboRecordData mboRecordData, int genFetchIndex)
    throws MXException, RemoteException
  {
    Mbo mbo = getMboInstance(this);
    mbo.setFetchIndex(genFetchIndex);
    mbo.setMboRecordData(mboRecordData);
    mbo.initialize();
    mbo.setModified(false);

    return mbo;
  }

  protected abstract Mbo getMboInstance(MboSet paramMboSet)
    throws MXException, RemoteException;

  public final String getName()
  {
    return this.mbosetinfo.getName();
  }

  protected void addMbo(Mbo mbo, int i)
  {
    if (i > this.mboVec.size())
    {
      try
      {
        fetchMbos(i - 1);
      }
      catch (Exception e) {
      }
    }
    this.mboVec.insertElementAt(mbo, i);
  }

  protected void addMbo(Mbo mbo)
  {
    this.mboVec.addElement(mbo);
  }

  public boolean processML()
    throws MXException, RemoteException
  {
    if ((this.processML == null) && 
      (getMboServer().getMaxVar().getBoolean("MLSUPPORTED", (String)null)) && 
      (!(getMboServer().getMaxVar().getString("BASELANGUAGE", (String)null).equals(getUserInfo().getLangCode()))) && 
      (this.processML == null)) {
      this.processML = new Boolean(getMboSetInfo().isMLInUse());
    }

    if (this.processML == null)
      this.processML = new Boolean(false);
    return this.processML.booleanValue();
  }

  String buildSelectStatement()
    throws MXException, RemoteException
  {
    String entityName = getMboSetInfo().getEntityName().toLowerCase();

    MboSetInfo mboSetInfo = getMboSetInfo();

    boolean ml = false;

    ml = processML();

    StringBuffer selectClause = null;
    StringBuffer joinClause = null;
    StringBuffer selectStmt;
    if (ml)
    {
      Iterator it = mboSetInfo.getAttributes();
      HashMap langTableNames = getMboSetInfo().getLangTableNames();
      if (langTableNames.isEmpty())
        throw new MXApplicationException("system", "LangTBMissing", new String[] { entityName });
      while (it.hasNext())
      {
        MboValueInfo mi = (MboValueInfo)it.next();
        if (!(mi.isPersistent()))
          continue;
        String attrUpper = mi.getAttributeName();
        String attr = attrUpper.toLowerCase();

        if (selectClause == null)
          selectClause = new StringBuffer("");
        else {
          selectClause.append(",");
        }
        if (mi.isMLInUse())
        {
          String tbName = mboSetInfo.getEntity().getTableName(attrUpper);
          String langTableName = ((String)langTableNames.get(tbName)).toLowerCase();

          String langColumnName = mboSetInfo.getEntity().getColumnName(attr, tbName);
          String nvlfunc = SqlFormat.getNullValueFunction(langTableName + "." + attr, entityName + "." + attr);

          selectClause.append(nvlfunc);
          selectClause.append(" " + attr);

          selectClause.append("," + entityName + "." + attr);
          selectClause.append(" bl_" + attr);
        }
        else
        {
          selectClause.append(entityName + "." + attr);
        }

      }

      Entity entity = getMboSetInfo().getEntity();
      if (entity.hasRowStamp())
      {
        RowStampInfo rowStampInfo = entity.getRowStampInfo();
        Iterator rowStampIterator = rowStampInfo.getFetchColumnNames();

        while (rowStampIterator.hasNext())
        {
          String rowStampColumnName = ((String)rowStampIterator.next()).toLowerCase();
          selectClause.append(", " + entityName + "." + rowStampColumnName);
        }
      }

      selectStmt = new StringBuffer("select ");
      selectStmt.append(selectClause);
      selectStmt.append(" from ");

      StringBuffer fromClause = getMLFromClause(langTableNames);
      selectStmt.append(fromClause);
    }
    else
    {
      selectStmt = new StringBuffer("select * from ");
      selectStmt.append(entityName);
    }

    String selectSQLOptions = getSQLOptions();
    if (selectSQLOptions != null)
    {
      selectStmt.append(" ");
      selectStmt.append(selectSQLOptions);
      selectStmt.append(" ");
    }

    selectStmt.append(" ");

    String where = buildWhere();
    if (where.trim().length() > 0)
    {
      selectStmt.append(" where ");

      SqlFormat sf = new SqlFormat(getUserInfo(), where);
      String sfWhere = sf.format();
      if (sf.hasNullBoundValue()) {
        return null;
      }
      selectStmt.append(sfWhere);
    }

    if ((this.orderByClause != null) && (this.orderByClause.length() > 0))
    {
      selectStmt.append(" order by ");

      if (this.preserveOrderByCase)
        selectStmt.append(this.orderByClause);
      else {
        selectStmt.append(this.orderByClause.toLowerCase());
      }
    }
    if (!(this.readOptionFastUse))
    {
      optionFastUse();
    }

    if (this.canUseFast)
    {
      selectStmt.append(" OPTION (FAST " + this.optionNum + ")  ");
    }
    return selectStmt.toString();
  }

  private StringBuffer getMLFromClause()
    throws MXException, RemoteException
  {
    return getMLFromClause(getMboSetInfo().getLangTableNames(), false);
  }

  public StringBuffer getMLFromClause(boolean useSchemaOwner)
    throws MXException, RemoteException
  {
    return getMLFromClause(getMboSetInfo().getLangTableNames(), useSchemaOwner);
  }

  private StringBuffer getMLFromClause(HashMap langTableNames) throws MXException, RemoteException {
    return getMLFromClause(langTableNames, false);
  }

  private StringBuffer getMLFromClause(HashMap langTableNames, boolean useSchemaOwner) throws MXException, RemoteException
  {
    boolean db2 = mboSetDBProductName.toUpperCase().indexOf("DB2") != -1;

    Iterator langNameIt = langTableNames.keySet().iterator();
    String entityName = getMboSetInfo().getEntityName().toLowerCase();
    StringBuffer fromClause;
    if (!(db2))
      fromClause = new StringBuffer("{oj ");
    else {
      fromClause = new StringBuffer("");
    }
    fromClause.append(getSubJoinClause(langTableNames, entityName, langNameIt, useSchemaOwner));
    if (!(db2)) {
      fromClause.append("}");
    }
    return fromClause;
  }

  private StringBuffer getSubJoinClause(HashMap langTableNames, String entityName, Iterator moreTables, boolean useSchemaOwner) throws MXException, RemoteException {
    StringBuffer sb = new StringBuffer();
    String thisTable = (String)moreTables.next();

    String langTable = ((String)langTableNames.get(thisTable)).toLowerCase();

    String basicTableId = getMboServer().getMaximoDD().getUniqueIdColumn(thisTable);

    String keyInEntity = getMboSetInfo().getEntity().getColumnName(basicTableId, thisTable);

    if (useSchemaOwner)
      sb.append(getMboServer().getSchemaOwner() + "." + langTable);
    else
      sb.append(langTable);
    sb.append(" right outer join ");
    if (moreTables.hasNext())
    {
      sb.append("(");
      sb.append(getSubJoinClause(langTableNames, entityName, moreTables, useSchemaOwner));
      sb.append(")");
    }
    else if (useSchemaOwner) {
      sb.append(getMboServer().getSchemaOwner() + "." + entityName);
    } else {
      sb.append(entityName);
    }
    sb.append(" on (");
    sb.append(langTable);
    sb.append(".ownerid=");
    sb.append(entityName.toLowerCase());
    sb.append(".");
    sb.append(keyInEntity.toLowerCase());
    UserInfo userInfo = getUserInfo();
    String langCodeColumn = MXServer.getMXServer().getMaximoDD().getLangCodeColumn(langTable.toUpperCase()).toLowerCase();
    SqlFormat sqf = new SqlFormat(userInfo, langTable + "." + langCodeColumn + "='" + userInfo.getLangCode() + "')");
    sb.append(" and ");
    sb.append(sqf.format());

    return sb;
  }

  private StringBuffer getMLJoinClause() throws MXException, RemoteException
  {
    return getMLJoinClause(getMboSetInfo().getLangTableNames());
  }

  private StringBuffer getMLJoinClause(HashMap langTableNames) throws MXException, RemoteException {
    StringBuffer joinClause = null;
    MboSetInfo mboSetInfo = getMboSetInfo();
    String entityName = mboSetInfo.getEntityName();
    Iterator basicTables = langTableNames.keySet().iterator();
    while (basicTables.hasNext())
    {
      String basicTable = (String)basicTables.next();

      String langTable = (String)langTableNames.get(basicTable);

      String basicTableId = getMboServer().getMaximoDD().getUniqueIdColumn(basicTable);

      String keyInEntity = mboSetInfo.getEntity().getColumnName(basicTableId, basicTable);

      if (joinClause == null)
        joinClause = new StringBuffer(entityName + "." + keyInEntity + "=" + langTable + ".ownerid (+)");
      else
        joinClause.append("and " + entityName + "." + keyInEntity + "=" + langTable + ".ownerid (+)");
      String langCode = getUserInfo().getLangCode();
      String langCodeColumn = MXServer.getMXServer().getMaximoDD().getLangCodeColumn(langTable.toUpperCase()).toLowerCase();

      joinClause.append(" and " + langTable + "." + langCodeColumn + "='" + langCode + "'");
    }
    return joinClause;
  }

  public MboRemote fetchNext()
    throws MXException, RemoteException
  {
    if (isClosed()) {
      return null;
    }
    int index = this.fetchNextIndex + 1;

    fetchMbos(index);

    if (index >= getSize()) {
      return null;
    }
    Mbo mbo = (Mbo)this.mboVec.elementAt(index);
    this.fetchNextIndex += 1;
    return mbo;
  }

  private void fetchMbos(int index)
    throws MXException, RemoteException
  {
    if (isClosed()) {
      return;
    }
    if (index < 0) {
      return;
    }
    if (index < getSize()) {
      return;
    }

    try
    {
      int tempIndex = this.fetchIndex;
      for (int i = this.fetchIndex; i < ((index > tempIndex) ? index + 2 : tempIndex + 2); ++i)
      {
        MboRecordData mboRecordData = getNextRecordData();
        if (mboRecordData == null)
        {
          if ((includeRelatedMbosOfOwnersChildren()) && (!(isFlagSet(16L))))
          {
            closeFetchConnection();
            this.fetchRelatedMbosOfOwnersChildren = true;
            this.fetchRelatedMboIndex = 0;
          }
          else
          {
            close();
          }
          this.selectQuery = "";
          return;
        }

        Mbo mbo = generateMboInstance(mboRecordData, getSize());
        addMbo(mbo);
        this.fetchIndex += 1;

        if (!(needToLogLargeFetchResult()))
          continue;
        MXLogger maximoLogger = FixedLoggers.MAXIMOLOGGER;

        if ((getSize() <= 0) || (getSize() % getFetchResultLogLimit() != 0L) || 
          (!(maximoLogger.isInfoEnabled()))) {
          continue;
        }
        maximoLogger.info("-------FetchResultLogLimit logging starts------");
        if (getUserInfo() != null)
          maximoLogger.info("User Name : " + getUserInfo().getLoginUserName());
        if (getApp() != null)
          maximoLogger.info("App Name : " + getApp());
        if ((this.selectQuery != null) && (this.selectQuery.trim().length() > 0))
          maximoLogger.info("Query :  " + this.selectQuery);
        maximoLogger.info("MboSet Name : " + getName() + " Reference = " + this);
        maximoLogger.info("MboSet Size : " + getSize());
        maximoLogger.info("Fetch count so far : " + this.fetchIndex);
        maximoLogger.info("Printing StackTrace: ", new Exception());
        maximoLogger.info("-------FetchResultLogLimit logging ends------");
      }

    }
    catch (SQLException e)
    {
      Object[] params = { new Integer(e.getErrorCode()).toString() };
      if (getSqlLogger().isErrorEnabled())
      {
        getSqlLogger().error("MboSet fetchMbos() failed with error code : " + ((String)params[0]), e);
      }

      throw new MXSystemException("system", "sql", params, e);
    }
  }

  private MboRecordData getNextRecordData()
    throws MXException, SQLException, RemoteException
  {
    if (!(this.connectionDetail.isfetchPerformed()))
    {
      if (this.noNeedtoFetchFromDB)
      {
        this.connectionDetail.setFetchPerformed(true);
        this.allFetched = true;
        close();
        return null;
      }

      String query = buildSelectStatement();

      if (query == null)
      {
        this.connectionDetail.setFetchPerformed(true);
        this.allFetched = true;
        close();
        return null;
      }
      this.selectQuery = query;

      this.connectionDetail.setResultConnectionKey(getUserInfo().getConnectionKey());

      Connection connection = getMboServer().getDBConnection(this.connectionDetail.getResultConnectionKey());
      if (connection == null) {
        return null;
      }

      String spid = getSPID(connection);

      Statement mboSetSqlStatement = null;
      mboSetSqlStatement = connection.createStatement();
      this.connectionDetail.setStatement(mboSetSqlStatement);
      try
      {
        long startTime = System.currentTimeMillis();

        ResultSet rs = mboSetSqlStatement.executeQuery(query);
        this.connectionDetail.setResultSet(rs);

        long execTime = System.currentTimeMillis() - startTime;

        logSQLStatementTimeForSelect(spid, MXServer.getMXServer().getDBManager().getSystemConnection(), query, execTime, getApp(), getName());
      }
      catch (SQLException sqlex)
      {
        if (getMboLogger().isErrorEnabled())
        {
          getMboLogger().error("Failed SQL query in MboSet.getNextRecordData(): " + connection.nativeSQL(query), sqlex);
        }
        throw sqlex;
      }

    }

    MboSetInfo thisMboSetInfo = getMboSetInfo();

    MboRecordData recordData = null;
    ResultSet mboSetResultSet = this.connectionDetail.getResultSet();
    if (mboSetResultSet.next())
    {
      this.connectionDetail.setFetchPerformed(true);

      recordData = new MboRecordData();

      int attributeCount = thisMboSetInfo.getFetchAttributeCount();
      ArrayList attributeData = new ArrayList(attributeCount);

      Iterator iterator = thisMboSetInfo.getFetchAttributes();
      while (iterator.hasNext())
      {
        MboValueInfo attributeInfo = (MboValueInfo)iterator.next();
        int attributeNo = attributeInfo.getFetchAttributeNumber();

        String fetchColumn = attributeInfo.getEntityColumnName();

        if ((processML()) && (attributeInfo.isMLInUse()))
        {
          String baseLangColumn = "bl_" + fetchColumn;
          Object o = mboSetResultSet.getObject(baseLangColumn);
          recordData.setBaseLangaugeData(fetchColumn, o);
        }

        Object o = null;
        String dbVendor = mboSetDBProductName;
        if ((dbVendor != null) && (dbVendor.toUpperCase().indexOf("ORACLE") >= 0))
        {
          if ((attributeInfo.getTypeAsInt() == 3) || (attributeInfo.getTypeAsInt() == 4) || (attributeInfo.getTypeAsInt() == 5))
          {
            o = mboSetResultSet.getTimestamp(fetchColumn);
          }
          else o = mboSetResultSet.getObject(fetchColumn);
        }
        else
          o = mboSetResultSet.getObject(fetchColumn);
        if (attributeInfo.getTypeAsInt() == 17)
        {
          if ((o != null) && (getMboServer().getMaximoDD().storeClobAsClob()))
          {
            attributeData.add(MXFormat.clobToString((Clob)o));
          }
          else
          {
            attributeData.add(o);
          }
        }
        else if (attributeInfo.getTypeAsInt() == 18)
        {
          if ((o != null) && (getMboServer().getMaximoDD().storeBlobAsBlob()))
          {
            attributeData.add(MXFormat.blobToBytes((Blob)o));
          }
          else
          {
            attributeData.add(o);
          }
        }
        else if (attributeInfo.getTypeAsInt() == 14)
        {
          if ((o != null) && (getMboServer().getMaximoDD().storeLongalnAsClob()))
            attributeData.add(MXFormat.clobToString((Clob)o));
          else
            attributeData.add(o);
        }
        else if (attributeInfo.getTypeAsInt() == 15)
        {
          if (o != null)
          {
            byte[] valEncrypted = (byte[])o;
            String valDecrypted = MXServer.getMXServer().getMXCipher().decData(valEncrypted);
            attributeData.add(valDecrypted);
          }
          else
          {
            attributeData.add("");
          }

        }
        else {
          attributeData.add(o);
        }

      }

      RowStampData rowStampData = null;
      Entity entity = thisMboSetInfo.getEntity();
      if (entity.hasRowStamp())
      {
        RowStampInfo rowStampInfo = entity.getRowStampInfo();
        rowStampData = new RowStampData(rowStampInfo);

        Iterator rowStampIterator = rowStampInfo.getFetchColumnNames();
        while (rowStampIterator.hasNext())
        {
          String columnName = (String)rowStampIterator.next();
          Object rowStampValue = mboSetResultSet.getObject(columnName);

          String table = rowStampInfo.getRowStampTableName(columnName);
          rowStampData.addRowStamp(table, rowStampValue);
        }

      }

      recordData.setAttributeData(attributeData);
      recordData.setRowStampData(rowStampData);
    }
    else {
      this.allFetched = true; }
    return recordData;
  }

  private String getSPID(Connection connection)
    throws MXException, SQLException, RemoteException
  {
    String spidStr = "Unable to get spid";
    if (connection instanceof ConRef) {
      return ((ConRef)connection).getSPID();
    }
    return spidStr;
  }

  private String getRelationShipUsedForThis()
    throws MXException, RemoteException
  {
    if (this.relationshipUsedForThis == null)
    {
      MaximoDD maximoDD = getMboServer().getMaximoDD();
      Iterator iterator = maximoDD.getMboSetInfo(this.ownerMbo.getName()).getRelationsInfo();
      while (iterator.hasNext())
      {
        RelationInfo ri = (RelationInfo)iterator.next();
        if (ri.getDest().equalsIgnoreCase(getName()))
        {
          this.relationshipUsedForThis = ri.getName();
          break;
        }
      }
    }

    return this.relationshipUsedForThis;
  }

  private void fetchMbos()
    throws MXException, RemoteException
  {
    if (isClosed()) {
      return;
    }

    int index = 0;
    while (true)
    {
      fetchMbos(index);
      if (isClosed())
        return;
      ++index;
    }
  }

  public String appendToWhere()
    throws MXException, RemoteException
  {
    return "";
  }

  public String getUserPrefWhere()
  {
    return "";
  }

  public int getSize()
  {
    return this.mboVec.size();
  }

  public int count()
    throws MXException, RemoteException
  {
    return count(8);
  }

  public int count(int countConstant)
    throws MXException, RemoteException
  {
    switch (countConstant)
    {
    case 1:
      if (this.allFetched) {
        return this.fetchIndex;
      }
      return (int)getDBCalc("count", "*");
    case 2:
      return this.createdCount;
    case 4:
      return this.deletedCount;
    case 8:
      return (count(1) + this.createdCount);
    case 16:
      return (count(1) + this.createdCount - this.deletedCount);
    }

    Object[] params = { new Long(countConstant) };
    throw new MXSystemException("system", "invalidCountConst", params);
  }

  public void incrementDeletedCount(boolean inc)
  {
    if (inc)
    {
      this.deletedCount += 1;
    }
    else
    {
      this.deletedCount -= 1;
    }
  }

  public double sum(String attributeName)
    throws MXException, RemoteException
  {
    MboValueInfo mvi = getMboSetInfo().getMboValueInfo(attributeName);

    if (mvi == null)
    {
      char[] chars = attributeName.toCharArray();
      StringBuffer columnString = new StringBuffer();
      boolean operator = false;
      int lasti = 0;
      for (int i = 0; ; ++i)
      {
        boolean hasAttr = false;
        if ((i != chars.length) && (chars[i] != '+') && (chars[i] != '-') && (chars[i] != '*') && (chars[i] != '/'))
          continue;
        String attr = new String(chars, lasti, i - lasti).trim();
        if (attr.equals(""))
        {
          Object[] params = { attributeName };
          throw new MXSystemException("system", "noattribute", params);
        }
        columnString.append(getMboSetInfo().getMboValueInfo(attr).getEntityColumnName());

        if (i == chars.length) {
          break;
        }

        columnString.append(chars[i]);
        ++i;
        lasti = i;
        operator = true;
      }

      if (operator) {
        return getDBCalc("sum", columnString.toString());
      }

      Object[] params = { attributeName };
      throw new MXSystemException("system", "noattribute", params);
    }

    if (!(mvi.isNumeric())) {
      throw new MXApplicationException("system", "invalidtype");
    }

    double sum = getDBCalc("sum", mvi.getEntityColumnName());

    for (int i = 0; i < getSize(); ++i)
    {
      Mbo mbo = (Mbo)getMbo(i);

      if ((mbo.toBeDeleted()) && (mbo.isNew()))
      {
        continue;
      }

      MboValue mbv = mbo.getMboValue(attributeName);

      if (mbo.toBeDeleted())
      {
        if (mbv.isNull())
          continue;
        sum = MXMath.subtract(sum, mbv.getDouble());
      }
      else {
        if (!(mbo.isModified())) {
          continue;
        }

        if (!(mbv.getInitialValue().isNull()))
        {
          sum = MXMath.subtract(sum, mbv.getInitialValue().asDouble());
        }

        if (mbv.isNull())
          continue;
        sum = MXMath.add(sum, mbv.getDouble());
      }

    }

    return sum;
  }

  public double max(String attributeName)
    throws MXException, RemoteException
  {
    boolean initialzedMaxValue = false;
    double maxValue = 0.0D;

    double curMaxValue = maxThis(attributeName);
    if (!(initialzedMaxValue))
    {
      maxValue = curMaxValue;
      initialzedMaxValue = true;
    }

    if (curMaxValue > maxValue)
    {
      maxValue = curMaxValue;
    }

    return maxValue;
  }

  private double maxThis(String attributeName)
    throws MXException, RemoteException
  {
    MboValueInfo mvi = getMboSetInfo().getMboValueInfo(attributeName);

    if (mvi == null)
    {
      Object[] params = { attributeName };
      throw new MXSystemException("system", "noattribute", params);
    }

    if (!(mvi.isNumeric())) {
      throw new MXApplicationException("system", "invalidtype");
    }

    double max = getDBCalc("max", mvi.getEntityColumnName());

    for (int i = 0; i < getSize(); ++i)
    {
      Mbo mbo = (Mbo)getMbo(i);
      if (!(mbo.isModified()))
        continue;
      MboValue mbv = mbo.getMboValue(attributeName);
      if ((!(mbv.isNull())) && (mbv.getDouble() > max)) {
        max = mbv.getDouble();
      }
    }

    return max;
  }

  public double min(String attributeName)
    throws MXException, RemoteException
  {
    boolean initialzedMinValue = false;
    double minValue = 0.0D;

    double curMinValue = minThis(attributeName);
    if (!(initialzedMinValue))
    {
      minValue = curMinValue;
      initialzedMinValue = true;
    }

    if (curMinValue < minValue)
    {
      minValue = curMinValue;
    }

    return minValue;
  }

  private double minThis(String attributeName)
    throws MXException, RemoteException
  {
    MboValueInfo mvi = getMboSetInfo().getMboValueInfo(attributeName);

    if (mvi == null)
    {
      Object[] params = { attributeName };
      throw new MXSystemException("system", "noattribute", params);
    }

    if (!(mvi.isNumeric())) {
      throw new MXApplicationException("system", "invalidtype");
    }

    double min = getDBCalc("min", mvi.getEntityColumnName());

    for (int i = 0; i < getSize(); ++i)
    {
      Mbo mbo = (Mbo)getMbo(i);
      if (!(mbo.isModified()))
        continue;
      MboValue mbv = mbo.getMboValue(attributeName);
      if ((!(mbv.isNull())) && (mbv.getDouble() < min)) {
        min = mbv.getDouble();
      }
    }

    return min;
  }

  public java.util.Date latestDate(String attributeName)
    throws MXException, RemoteException
  {
    boolean initialzedLatestDateValue = false;
    java.util.Date latestDateValue = null;

    java.util.Date curLatestDateValue = latestDateThis(attributeName);
    if (curLatestDateValue == null)
    {
      return latestDateValue;
    }

    if (!(initialzedLatestDateValue))
    {
      latestDateValue = curLatestDateValue;
      initialzedLatestDateValue = true;
    }

    if (curLatestDateValue.after(latestDateValue))
    {
      latestDateValue = curLatestDateValue;
    }

    return latestDateValue;
  }

  private java.util.Date latestDateThis(String attributeName)
    throws MXException, RemoteException
  {
    MboValueInfo mvi = getMboSetInfo().getMboValueInfo(attributeName);

    if (mvi == null)
    {
      Object[] params = { attributeName };
      throw new MXSystemException("system", "noattribute", params);
    }

    java.util.Date latest = null;
    try
    {
      latest = getDBDate("max", mvi.getEntityColumnName());
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }

    for (int i = 0; i < getSize(); ++i)
    {
      Mbo mbo = (Mbo)getMbo(i);
      if (!(mbo.isModified()))
        continue;
      MboValue mbv = mbo.getMboValue(attributeName);
      if (latest == null)
      {
        latest = mbv.getDate();
      }
      else if ((!(mbv.isNull())) && (mbv.getDate().after(latest))) {
        latest = mbv.getDate();
      }
    }

    return latest;
  }

  public java.util.Date earliestDate(String attributeName)
    throws MXException, RemoteException
  {
    boolean initialzedEarliestDateValue = false;
    java.util.Date earliestDateValue = null;

    java.util.Date curEarliestDateValue = earliestDateThis(attributeName);
    if (curEarliestDateValue == null)
    {
      return earliestDateValue;
    }

    if (!(initialzedEarliestDateValue))
    {
      earliestDateValue = curEarliestDateValue;
      initialzedEarliestDateValue = true;
    }

    if (curEarliestDateValue.before(earliestDateValue))
    {
      earliestDateValue = curEarliestDateValue;
    }

    return earliestDateValue;
  }

  private java.util.Date earliestDateThis(String attributeName)
    throws MXException, RemoteException
  {
    MboValueInfo mvi = getMboSetInfo().getMboValueInfo(attributeName);

    if (mvi == null)
    {
      Object[] params = { attributeName };
      throw new MXSystemException("system", "noattribute", params);
    }

    java.util.Date earliest = null;
    try
    {
      earliest = getDBDate("min", mvi.getEntityColumnName());
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }

    for (int i = 0; i < getSize(); ++i)
    {
      Mbo mbo = (Mbo)getMbo(i);
      if (!(mbo.isModified()))
        continue;
      MboValue mbv = mbo.getMboValue(attributeName);
      if (earliest == null)
      {
        earliest = mbv.getDate();
      }
      else if ((!(mbv.isNull())) && (mbv.getDate().before(earliest))) {
        earliest = mbv.getDate();
      }
    }

    return earliest;
  }

  private Object getDBValue(String dbFunction, String dbColumn) throws MXException, RemoteException
  {
    return getDBValue(dbFunction, dbColumn, 0);
  }

  private Object getDBValue(String dbFunction, String dbColumn, int maxType)
    throws MXException, RemoteException
  {
    Object retVal = null;

    Statement stmt = null;
    ResultSet resultset = null;
    try
    {
      String calcEntity = getMboSetInfo().getEntityName();

      Connection conn = getMboServer().getDBConnection(getUserInfo().getConnectionKey());
      String query = null;

      String getValueClause = buildWhere();
      String spid = getSPID(conn);
      if (processML())
      {
        query = "Select " + dbFunction + "(" + dbColumn + ") from " + getMLFromClause();

        if ((getValueClause != null) && (getValueClause.length() > 0))
          query = query + " where " + getValueClause;
      }
      else
      {
        query = "Select " + dbFunction + "(" + dbColumn + ") from " + calcEntity;
        if ((getValueClause != null) && (getValueClause.length() > 0))
          query = query + " where " + getValueClause;
      }
      stmt = conn.createStatement();
      String formattedQuery = new SqlFormat(getUserInfo(), query).format();

      long startTime = System.currentTimeMillis();

      resultset = stmt.executeQuery(formattedQuery);

      long execTime = System.currentTimeMillis() - startTime;
      logSQLStatementTime(spid, formattedQuery, execTime, getApp(), getName(), conn);

      resultset.next();

      if ((maxType == 3) || (maxType == 4) || (maxType == 5))
      {
        retVal = resultset.getTimestamp(1);
      }
      else retVal = resultset.getObject(1);
    }
    catch (SQLException e)
    {
      e.printStackTrace();
      Object[] params = { new Integer(e.getErrorCode()).toString() };
      throw new MXSystemException("system", "sql", params, e);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      try {
        if (resultset != null)
          resultset.close();
      } catch (Exception ex) {
      }
      try {
        if (stmt != null)
          stmt.close();
      } catch (Exception ex) {
      }
      getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
    }

    return retVal;
  }

  Hashtable getDBCache()
  {
    if (this.dbCache == null) {
      this.dbCache = new Hashtable();
    }
    return this.dbCache;
  }

  private double getDBCalc(String dbFunction, String dbColumn)
    throws MXException, RemoteException
  {
    Double val = (Double)getDBCache().get(dbFunction + "+" + dbColumn);
    if (val != null) {
      return val.doubleValue();
    }

    double result = 0.0D;

    Object obj = getDBValue(dbFunction, dbColumn);
    if (obj instanceof Number) {
      result = ((Number)obj).doubleValue();
    }

    getDBCache().put(dbFunction + "+" + dbColumn, new Double(result));

    return result;
  }

  private java.util.Date getDBDate(String dbFunction, String dbColumn)
    throws MXException, RemoteException
  {
    java.util.Date val = (java.util.Date)getDBCache().get(dbFunction + "+" + dbColumn);
    if (val != null) {
      return val;
    }

    java.util.Date result = null;

    Object obj = getDBValue(dbFunction, dbColumn, 4);
    if (obj instanceof java.util.Date)
    {
      result = (java.util.Date)obj;

      getDBCache().put(dbFunction + "+" + dbColumn, result);
    }

    return result;
  }

  public final boolean isEmpty()
    throws MXException, RemoteException
  {
    return (getMbo(0) == null);
  }

  public final MboRemote add()
    throws MXException, RemoteException
  {
    return add(0L);
  }

  public final MboRemote addAtEnd()
    throws MXException, RemoteException
  {
    return addAtEnd(0L);
  }

  public final MboRemote addAtIndex(int ind)
    throws MXException, RemoteException
  {
    return addAtIndex(0L, ind);
  }

  public void canAdd()
    throws MXException
  {
  }

  public void deleteAll()
    throws MXException, RemoteException
  {
    deleteAll(0L);
  }

  public void deleteAll(long accessModifier)
    throws MXException, RemoteException
  {
    int deleted = 0;
    MboRemote curMbo = null;
    try
    {
      curMbo = getMbo(deleted);

      while (curMbo != null)
      {
        curMbo.delete(accessModifier);
        ++deleted;
        curMbo = getMbo(deleted);
      }

    }
    catch (MXException e)
    {
      for (; deleted >= 0; --deleted)
      {
        curMbo = getMbo(deleted);
        curMbo.undelete();
      }

      throw e;
    }
  }

  public void undeleteAll()
    throws MXException, RemoteException
  {
    int undeleted = 0;
    MboRemote curMbo = null;

    curMbo = getMbo(undeleted);

    while (curMbo != null)
    {
      curMbo.undelete();
      ++undeleted;
      curMbo = getMbo(undeleted);
    }
  }

  public final MboRemote add(long accessModifier)
    throws MXException, RemoteException
  {
    return addAtIndex(accessModifier, 0);
  }

  public final MboRemote addAtEnd(long accessModifier)
    throws MXException, RemoteException
  {
    return addAtIndex(accessModifier, -1);
  }

  public MboRemote addAtIndex(long accessModifier, int ind)
    throws MXException, RemoteException
  {
	  new Throwable().printStackTrace();
    checkMethodAccess("add", accessModifier);

    Mbo mbo = getMboInstance(this);

    if (ind < 0)
      addMbo(mbo);
    else
      addMbo(mbo, ind);
    mbo.setNewMbo(true);
    mbo.setDefaultSiteOrgSetValues();
    mbo.setLangCodeDefault();
    mbo.setUniqueIDValue();
    mbo.initialize();
    mbo.add();
    mbo.setDatabaseDefaultValues(this.setAutoKey);
    mbo.setDefaultValues();
    mbo.setAppDefaultValue();
    mbo.setDefaultValue();

    if (ind < 0)
      moveTo(getSize() - 1);
    else {
      moveTo(ind);
    }

    if (this.newlyCreated == null)
    {
      this.newlyCreated = new Vector();
    }

    this.newlyCreated.addElement(mbo);

    this.createdCount += 1;

    return mbo;
  }

  public void clear()
    throws MXException, RemoteException
  {
    if (this.sharedSets != null)
    {
      for (Enumeration e = this.sharedSets.elements(); e.hasMoreElements(); )
      {
        MboSet ms = (MboSet)e.nextElement();
        ms.clear();
      }

    }

    for (Enumeration e = this.mboVec.elements(); e.hasMoreElements(); )
    {
      Mbo mbo = (Mbo)e.nextElement();
      if (mbo != null)
      {
        Hashtable rs = mbo.getRelatedSets();
        for (Enumeration ee = rs.elements(); ee.hasMoreElements(); )
        {
          MboSet msr = (MboSet)ee.nextElement();
          if (msr != null)
          {
            msr.clear();
          }
        }
      }
    }
    Enumeration ee;
    if (this.activeTransaction != null) {
      this.activeTransaction.remove(this);
    }
    this.mboVec.removeAllElements();
    this.currIndex = -1;
    this.createdCount = 0;
  }

  public void remove()
    throws MXException, RemoteException
  {
    remove(getCurrentPosition());
  }

  public void remove(int pos)
    throws MXException, RemoteException
  {
    if ((pos < 0) || (pos > getSize())) {
      return;
    }
    MboRemote targMbo = getMbo(pos);

    remove(targMbo);
  }

  public void remove(MboRemote mbo)
    throws MXException, RemoteException
  {
    if (mbo.toBeDeleted())
    {
      incrementDeletedCount(false);
    }

    if (mbo.isNew())
    {
      this.createdCount -= 1;
    }

    int pos = this.mboVec.indexOf(mbo);

    this.mboVec.removeElement(mbo);

    if (getSize() == 0)
      this.currIndex = -1;
    else if (pos == getSize()) {
      moveTo(getSize() - 1);
    }

    mbo.clear();
  }

  public void deleteAndRemove()
    throws MXException, RemoteException
  {
    deleteAndRemove(getCurrentPosition());
  }

  public void deleteAndRemove(int pos)
    throws MXException, RemoteException
  {
    deleteAndRemove(pos, 0L);
  }

  public void deleteAndRemove(int pos, long accessModifier)
    throws MXException, RemoteException
  {
    if ((pos < 0) || (pos > getSize())) {
      return;
    }
    MboRemote targMbo = getMbo(pos);
    deleteAndRemove(targMbo, accessModifier);
  }

  public void deleteAndRemove(MboRemote mbo)
    throws MXException, RemoteException
  {
    deleteAndRemove(mbo, 0L);
  }

  public void deleteAndRemove(MboRemote mbo, long accessModifier)
    throws MXException, RemoteException
  {
    if (mbo == null)
    {
      return;
    }

    if ((!(mbo.isNew())) && (!(mbo.hasHierarchyLink()))) {
      return;
    }

    if (!(mbo.toBeDeleted()))
    {
      mbo.delete(accessModifier);
    }

    remove(mbo);
  }

  public void deleteAndRemoveAll()
    throws MXException, RemoteException
  {
    deleteAndRemoveAll(0L);
  }

  public void deleteAndRemoveAll(long accessModifier)
    throws MXException, RemoteException
  {
    MboRemote mbo = null;
    for (int i = getSize() - 1; i >= 0; --i)
    {
      mbo = getMbo(i);
      deleteAndRemove(mbo, accessModifier);
    }
  }

  public boolean toBeSaved()
    throws RemoteException
  {
	  label0:
      {
          if(isFlagSet(8L))
              return false;
          if(toBeSaved)
              return true;
          Enumeration e;
          for(e = mboVec.elements(); e.hasMoreElements();)
          {
              Mbo mbo = (Mbo)e.nextElement();
              if(mbo != null && mbo.toBeSaved())
              {
                  toBeSaved = true;
                  return true;
              }
          }

          if(sharedSets == null)
              break label0;
          e = sharedSets.elements();
          MboSetRemote msr;
          do
          {
              if(!e.hasMoreElements())
                  break label0;
              msr = (MboSetRemote)e.nextElement();
          } while(msr == null || !msr.toBeSaved());
          return true;
      }
      return false;

  }

  public void close()
    throws MXException, RemoteException
  {
    this.connectionDetail.close();
  }

  private void closeFetchConnection()
  {
    this.connectionDetail.closeFetchConnection();
  }

  public boolean isClosed()
  {
    return this.connectionDetail.isClosed();
  }

  public void reset()
    throws MXException, RemoteException
  {
    for (Enumeration e = this.mboVec.elements(); e.hasMoreElements(); )
    {
      Mbo mbo = (Mbo)e.nextElement();
      if (mbo != null)
      {
        Hashtable rs = mbo.getRelatedSets();
        for (Enumeration ee = rs.elements(); ee.hasMoreElements(); )
        {
          MboSetRemote msr = (MboSetRemote)ee.nextElement();
          if (msr != null)
          {
            msr.clear();
          }
        }
      }
    }
    Enumeration ee;
    if (this.sharedSets != null)
    {
      for (Enumeration e = this.sharedSets.elements(); e.hasMoreElements(); )
      {
        MboSetRemote msr = (MboSetRemote)e.nextElement();
        if (msr != null)
        {
          msr.clear();
        }
      }
    }
    resetThis();
    this.toBeSaved = false;
  }

  private void resetThis()
    throws MXException, RemoteException
  {
    close();

    this.dbCache = null;

    this.createdCount = 0;
    this.deletedCount = 0;
    this.fetchIndex = 0;
    this.fetchNextIndex = -1;
    this.fetchRelatedMbosOfOwnersChildren = false;

    this.sharedSets = null;

    this.discardableCurrentIndex = -1;
    this.discardableMbo = null;

    this.connectionDetail.setFetchPerformed(false);

    this.newlyCreated = null;

    init(getUserInfo());

    this.selectionWhereClause = "";
    this.eSigFieldModified = false;
    this.eAuditFieldModified = false;

    this.integrationKeyMbos = null;
  }

  public void resetForRefreshOnSave()
    throws MXException, RemoteException
  {
    this.sharedSets = null;

    this.mboVec = new Vector(getSize());
    this.mboVec.setSize(getSize());
  }

  public void commit()
    throws MXException, RemoteException
  {
    if (!(toBeSaved()))
    {
      return;
    }

    Connection con = null;
    Enumeration e;
    try
    {
      con = getMboServer().getDBConnection(getUserInfo().getConnectionKey());
      if (con != null)
      {
        con.commit();

        for (e = this.mboVec.elements(); e.hasMoreElements(); ) {
          Mbo mbo = (Mbo)e.nextElement();
          if (mbo == null)
            continue;
          mbo.commit();
        }

      }

    }
    catch (SQLException ex)
    {
      Object[] params = { new Integer(ex.getErrorCode()).toString() };
      throw new MXSystemException("system", "sql", params, ex);
    }
    finally
    {
      this.activeTransaction = null;

      if (con != null)
      {
        getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
      }

      clearEAuditTransactionId();
      clearESigTransactionId();
    }
  }

  public void rollback()
    throws MXException, RemoteException
  {
    Connection con = null;

    for (Enumeration e = this.mboVec.elements(); e.hasMoreElements(); )
    {
      Mbo mbo = (Mbo)e.nextElement();
    }

    Mbo mbo;
    try
    {
      con = getMboServer().getDBConnection(getUserInfo().getConnectionKey());
      if (con != null)
        con.rollback();
    }
    catch (SQLException e)
    {
      Object[] params = { new Integer(e.getErrorCode()).toString() };
      throw new MXSystemException("system", "sql", params, e);
    }
    finally
    {
      if (con != null)
      {
        getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
      }

      clearEAuditTransactionId();
      clearESigTransactionId();
    }
  }

  public void validate()
    throws MXException, RemoteException
  {
    for (Enumeration e = this.mboVec.elements(); e.hasMoreElements(); )
    {
      Mbo mbo = (Mbo)e.nextElement();
      if (mbo == null)
        continue;
      if (mbo.toBeValidated());
      mbo.validate();
    }
  }

  public MboSetRemote getList(String attributeName)
    throws MXException, RemoteException
  {
    return getList(this.currIndex, attributeName);
  }

  public MboSetRemote getList(int row, String attributeName)
    throws MXException, RemoteException
  {
    if (row >= 0)
    {
      if (row == this.currIndex) {
        return this.currMbo.getList(attributeName);
      }
      return getMbo(row).getList(attributeName);
    }

    return getZombie().getList(attributeName);
  }

  public MboSetRemote smartFill(String attributeName, String value, boolean exact)
    throws MXException, RemoteException
  {
    return smartFill(this.currIndex, attributeName, value, exact);
  }

  public MboSetRemote smartFind(String attributeName, String value, boolean exact)
    throws MXException, RemoteException
  {
    return smartFind(this.currIndex, attributeName, value, exact);
  }

  public MboSetRemote smartFind(String objectName, String attributeName, String value, boolean exact)
    throws MXException, RemoteException
  {
    return smartFind(this.currIndex, objectName, attributeName, value, exact);
  }

  public MboSetRemote smartFill(int row, String attributeName, String value, boolean exact)
    throws MXException, RemoteException
  {
    if (row >= 0)
    {
      if (row == this.currIndex) {
        return this.currMbo.smartFill(attributeName, value, exact);
      }
      return getMbo(row).smartFill(attributeName, value, exact);
    }

    return getZombie().smartFill(attributeName, value, exact);
  }

  public MboSetRemote smartFind(int row, String objectName, String attributeName, String value, boolean exact)
    throws MXException, RemoteException
  {
    if (row >= 0)
    {
      if (row == this.currIndex) {
        return this.currMbo.smartFind(objectName, attributeName, value, exact);
      }
      return getMbo(row).smartFind(objectName, attributeName, value, exact);
    }

    return getZombie().smartFind(objectName, attributeName, value, exact);
  }

  public MboSetRemote smartFind(int row, String attributeName, String value, boolean exact)
    throws MXException, RemoteException
  {
    if (row >= 0)
    {
      if (row == this.currIndex) {
        return this.currMbo.smartFind(attributeName, value, exact);
      }
      return getMbo(row).smartFind(attributeName, value, exact);
    }

    return getZombie().smartFind(attributeName, value, exact);
  }

  public String getString(String attributeName)
    throws MXException, RemoteException
  {
    return this.currMbo.getString(attributeName);
  }

  public boolean getBoolean(String attributeName)
    throws MXException, RemoteException
  {
    return this.currMbo.getBoolean(attributeName);
  }

  public byte getByte(String attributeName)
    throws MXException, RemoteException
  {
    return this.currMbo.getByte(attributeName);
  }

  public int getInt(String attributeName)
    throws MXException, RemoteException
  {
    return this.currMbo.getInt(attributeName);
  }

  public long getLong(String attributeName)
    throws MXException, RemoteException
  {
    return this.currMbo.getLong(attributeName);
  }

  public float getFloat(String attributeName)
    throws MXException, RemoteException
  {
    return this.currMbo.getFloat(attributeName);
  }

  public double getDouble(String attributeName)
    throws MXException, RemoteException
  {
    return this.currMbo.getDouble(attributeName);
  }

  public java.util.Date getDate(String attributeName)
    throws MXException, RemoteException
  {
    return this.currMbo.getDate(attributeName);
  }

  public byte[] getBytes(String attributeName)
    throws MXException, RemoteException
  {
    return this.currMbo.getBytes(attributeName);
  }

  public boolean isNull(String attributeName)
    throws MXException, RemoteException
  {
    return this.currMbo.isNull(attributeName);
  }

  public void setValueNull(String attributeName)
    throws MXException, RemoteException
  {
    setValueNull(attributeName, 0L);
  }

  public void setValueNull(String attributeName, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValueNull(attributeName, accessModifier);
  }

  public void setValue(String attributeName, String val)
    throws MXException, RemoteException
  {
    setValue(attributeName, val, 0L);
  }

  public void setValue(String attributeName, String val, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, val, accessModifier);
  }

  public void setValue(String attributeName, boolean val)
    throws MXException, RemoteException
  {
    setValue(attributeName, val, 0L);
  }

  public void setValue(String attributeName, boolean val, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, val, accessModifier);
  }

  public void setValue(String attributeName, byte val)
    throws MXException, RemoteException
  {
    setValue(attributeName, val, 0L);
  }

  public void setValue(String attributeName, byte val, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, val, accessModifier);
  }

  public void setValue(String attributeName, short val)
    throws MXException, RemoteException
  {
    setValue(attributeName, val, 0L);
  }

  public void setValue(String attributeName, short val, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, val, accessModifier);
  }

  public void setValue(String attributeName, int val)
    throws MXException, RemoteException
  {
    setValue(attributeName, val, 0L);
  }

  public void setValue(String attributeName, int val, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, val, accessModifier);
  }

  public void setValue(String attributeName, long val)
    throws MXException, RemoteException
  {
    setValue(attributeName, val, 0L);
  }

  public void setValue(String attributeName, long val, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, val, accessModifier);
  }

  public void setValue(String attributeName, float val)
    throws MXException, RemoteException
  {
    setValue(attributeName, val, 0L);
  }

  public void setValue(String attributeName, float val, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, val, accessModifier);
  }

  public void setValue(String attributeName, double val)
    throws MXException, RemoteException
  {
    setValue(attributeName, val, 0L);
  }

  public void setValue(String attributeName, double val, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, val, accessModifier);
  }

  public void setValue(String attributeName, byte[] val)
    throws MXException, RemoteException
  {
    setValue(attributeName, val, 0L);
  }

  public void setValue(String attributeName, byte[] val, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, val, accessModifier);
  }

  public void setValue(String attributeName, java.util.Date val)
    throws MXException, RemoteException
  {
    setValue(attributeName, val, 0L);
  }

  public void setValue(String attributeName, java.util.Date val, long accessModifier)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, val, accessModifier);
  }

  public void setValue(String attributeName, MboSetRemote source)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, source);
  }

  public void setValue(String attributeName, MboRemote source)
    throws MXException, RemoteException
  {
    this.currMbo.setValue(attributeName, source);
  }

  public void setMboSetInfo(MboSetInfo ms)
    throws RemoteException
  {
    if (this.mbosetinfo != null) {
      throw new RuntimeException("Cannot change the MboSetInfo after MboSet is initialized.");
    }
    this.mbosetinfo = ms;
  }

  public MboSetInfo getMboSetInfo()
  {
    return this.mbosetinfo;
  }

  protected MboServerInterface getMboServer()
  {
    return this.mboServer;
  }

  protected Translate getTranslator()
  {
    return getMboServer().getMaximoDD().getTranslator();
  }

  public UserInfo getUserInfo()
    throws RemoteException
  {
    return this.user;
  }

  public void resetQbe()
  {
    this.qbe.resetQbe();
    this.zombieMbo = null;
  }

  public void setQbe(String[] attribute, String expression)
    throws MXException, RemoteException
  {
    this.qbe.setOperatorOr();

    Hashtable relList = new Hashtable();
    String relName;
    for (int i = 0; i < attribute.length; ++i)
    {
      relName = getRelName(attribute[i]);

      Vector curList = (Vector)relList.get(relName);

      if (curList == null)
      {
        curList = new Vector();
        relList.put(relName, curList);
      }

      curList.addElement(attribute[i]);
    }

    for (Enumeration e = relList.keys(); e.hasMoreElements(); )
    {
      relName = (String)e.nextElement();
      Vector v = (Vector)relList.get(relName);

      String[] attribs = new String[v.size()];
      v.copyInto(attribs);

      if (relName.equalsIgnoreCase("this"))
      {
        for (int i = 0; i < attribs.length; ++i)
        {
          try
          {
            setQbe(attribs[i], expression);
          }
          catch (Exception ce)
          {
            if (ce instanceof MXApplicationException)
            {
              MboValueInfo mvi = getMboSetInfo().getMboValueInfo(getRelAttrName(attribs[i]).toUpperCase());
              if ((mvi != null) && 
                (mvi.getTypeAsInt() != 3) && (mvi.getTypeAsInt() != 4))
              {
                throw ((MXApplicationException)ce);
              }

            }

          }

        }

      }
      else
      {
        MboSetRemote msr = getMboSetForAttribute(getRelAttrName(attribs[0]));

        for (int i = 0; i < attribs.length; ++i)
        {
          attribs[i] = getFullAttrName(attribs[i]);
        }

        msr.setQbeCaseSensitive(isQbeCaseSensitive());
        msr.setQbeExactMatch(isQbeExactMatch());
        msr.ignoreQbeExactMatchSet(isIgnoreQbeExactMatchSet());
        msr.setQbe(attribs, expression);
      }
    }
  }

  public void setQbe(String[] attribute, String[] expression)
    throws MXException, RemoteException
  {
    Hashtable relList = new Hashtable();
    String relName;
    for (int i = 0; i < attribute.length; ++i)
    {
      relName = getRelName(attribute[i]);

      Hashtable curList = (Hashtable)relList.get(relName);

      if (curList == null)
      {
        curList = new Hashtable();
        relList.put(relName, curList);
      }

      curList.put(attribute[i], expression[i]);
    }

    for (Enumeration e = relList.keys(); e.hasMoreElements(); )
    {
      relName = (String)e.nextElement();
      Hashtable v = (Hashtable)relList.get(relName);
      Enumeration e1;
      if (relName.equalsIgnoreCase("this"))
      {
        for (e1 = v.keys(); e1.hasMoreElements(); )
        {
          String attrName = (String)e1.nextElement();
          try
          {
            setQbe(attrName, (String)v.get(attrName));
          }
          catch (Exception ce)
          {
            if (ce instanceof MXApplicationException)
            {
              MboValueInfo mvi = getMboSetInfo().getMboValueInfo(getRelAttrName(attrName).toUpperCase());
              if ((mvi != null) && 
                (mvi.getTypeAsInt() != 3) && (mvi.getTypeAsInt() != 4))
              {
                throw ((MXApplicationException)ce);
              }

            }

          }

        }

      }
      else
      {
        String[] attribs = new String[v.size()];
        String[] exprs = new String[v.size()];
        MboSetRemote msr = null;
        int i = 0;
        for (Enumeration e11 = v.keys(); e11.hasMoreElements(); )
        {
          attribs[i] = ((String)e11.nextElement());
          exprs[i] = ((String)v.get(attribs[i]));
          i += 1;
        }
        msr = getMboSetForAttribute(getRelAttrName(attribs[0]));

        for (i = 0; i < attribs.length; ++i)
        {
          attribs[i] = getFullAttrName(attribs[i]);
        }

        msr.setQbeCaseSensitive(isQbeCaseSensitive());
        msr.setQbeExactMatch(isQbeExactMatch());
        msr.ignoreQbeExactMatchSet(isIgnoreQbeExactMatchSet());
        msr.setQbe(attribs, exprs);
      }
    }
  }

  private String getRelName(String attrstrg)
  {
    int dotindex = attrstrg.lastIndexOf(46);
    if (dotindex < 0)
      return "this";
    int lastSlashIndex = attrstrg.lastIndexOf(47);
    if (lastSlashIndex < 0)
      lastSlashIndex = 0;
    else
      ++lastSlashIndex;
    if (dotindex < lastSlashIndex)
      return "this";
    return attrstrg.substring(lastSlashIndex, dotindex).toUpperCase();
  }

  private String getAttrName(String attrstrg)
  {
    int startIndex = 0;
    int lastSlashIndex = attrstrg.lastIndexOf(47);
    if (lastSlashIndex >= 0)
      startIndex = lastSlashIndex + 1;
    int dotIndex = attrstrg.lastIndexOf(46);
    if (dotIndex >= 0)
      startIndex = dotIndex + 1;
    return attrstrg.substring(startIndex).toUpperCase();
  }

  private String getRelAttrName(String attrstrg)
  {
    int lastSlashIndex = attrstrg.lastIndexOf(47);
    if (lastSlashIndex < 0) {
      return attrstrg;
    }
    return attrstrg.substring(lastSlashIndex + 1).toUpperCase();
  }

  private String getFullAttrName(String attrstrg)
  {
    int startIndex = 0;
    int dotindex = attrstrg.lastIndexOf(46);
    if (dotindex < 0)
      return attrstrg.toUpperCase();
    int lastSlashIndex = attrstrg.lastIndexOf(47);
    if (lastSlashIndex < 0) {
      return attrstrg.substring(dotindex + 1).toUpperCase();
    }
    return attrstrg.substring(0, lastSlashIndex + 1).toUpperCase() + attrstrg.substring(dotindex + 1).toUpperCase();
  }

  public void setQbeExactMatch(String state)
    throws RemoteException
  {
    setQbeExactMatch(state.equalsIgnoreCase("true"));
  }

  public void setQbeExactMatch(boolean state)
    throws RemoteException
  {
    this.qbe.setExactMatch(state);

    Hashtable rs = ((Mbo)getZombie()).getRelatedSets();
    for (Enumeration ee = rs.elements(); ee.hasMoreElements(); )
    {
      MboSet msr = (MboSet)ee.nextElement();
      if (msr != null)
      {
        msr.setQbeExactMatch(state);
      }
    }
  }

  public boolean isQbeExactMatch()
  {
    return this.qbe.isExactMatch();
  }

  public void ignoreQbeExactMatchSet(boolean flag)
    throws MXException, RemoteException
  {
    this.qbe.ignoreQbeExactMatchSet(flag);

    Hashtable rs = ((Mbo)getZombie()).getRelatedSets();
    for (Enumeration ee = rs.elements(); ee.hasMoreElements(); )
    {
      MboSet msr = (MboSet)ee.nextElement();
      if (msr != null)
      {
        msr.ignoreQbeExactMatchSet(flag);
      }
    }
  }

  public boolean isIgnoreQbeExactMatchSet()
  {
    return this.qbe.isIgnoreQbeExactMatchSet();
  }

  public boolean isQbeCaseSensitive()
  {
    return this.qbe.isCaseSensitive();
  }

  public void setQbeCaseSensitive(boolean state)
    throws RemoteException
  {
    this.qbe.setCaseSensitive(state);

    Hashtable rs = ((Mbo)getZombie()).getRelatedSets();
    for (Enumeration ee = rs.elements(); ee.hasMoreElements(); )
    {
      MboSet msr = (MboSet)ee.nextElement();
      if (msr != null)
      {
        msr.setQbeCaseSensitive(state);
      }
    }
  }

  public void setQbeCaseSensitive(String state)
    throws RemoteException
  {
    setQbeCaseSensitive(state.equalsIgnoreCase("true"));
  }

  public String[] getQbe(String[] attributes)
    throws RemoteException
  {
    String[] result = new String[attributes.length];

    for (int i = 0; i < attributes.length; ++i)
      try
      {
        result[i] = getQbe(attributes[i]);
      }
      catch (MXException e) {
      }
    return result;
  }

  public String[][] getQbe()
    throws MXException, RemoteException
  {
    return this.qbe.getQbe();
  }

  public boolean hasQbe()
    throws MXException, RemoteException
  {
    return ((this.qbe.getWhere().length() > 0) || (((Mbo)getZombie()).hasRelatedQbe()));
  }

  BitFlag getUserFlags()
  {
    if (this.userFlags == null) {
      this.userFlags = new BitFlag();
    }
    return this.userFlags;
  }

  /** @deprecated */
  public void setFlags(long flags)
  {
    getUserFlags().setFlags(flags);
  }

  /** @deprecated */
  public long getFlags()
  {
    return getUserFlags().getFlags();
  }

  public void setFlag(long flag, boolean state)
  {
    if (flag == 39L)
      setDiscardableFlag(state);
    if (flag == 8L) {
      setNoSaveFlag(state);
    }
    getUserFlags().setFlag(flag, state);
  }

  protected void setNoSaveFlag(boolean state)
  {
    if ((state) && (isFlagSet(8L)))
      return;
    if (state)
    {
      if (this.activeTransaction == null)
        return;
      try
      {
        this.transactionStatus = this.activeTransaction.getTransactionStatus(this);
        this.activeTransaction.remove(this);
      }
      catch (Exception e)
      {
      }

      this.potentialTransaction = this.activeTransaction;
      this.activeTransaction = null;
    }
    else
    {
      try
      {
        this.activeTransaction = getMXTransaction();
        if (this.transactionStatus != -1L)
          this.activeTransaction.add(this, this.transactionStatus);
        else
          this.activeTransaction.add(this);
        this.potentialTransaction = null;
      }
      catch (RemoteException e)
      {
        getMboLogger().error(e);
      }
    }
  }

  protected void setDiscardableFlag(boolean state)
  {
    if ((state) && (isFlagSet(39L))) {
      return;
    }
    if (!(state))
    {
      getMboLogger().error(new MXApplicationException("system", "UnsetDiscardable"));
    }
    else
    {
      setFlag(8L, true);
    }
  }

  public void setFlag(long flag, boolean state, MXException mxe)
  {
    this.mxException = mxe;
    setFlag(flag, state);
  }

  public boolean isFlagSet(long flag)
  {
    return getUserFlags().isFlagSet(flag);
  }

  Hashtable getMethodAccessList()
  {
    if (this.methodAccessList == null) {
      this.methodAccessList = new Hashtable();
    }
    return this.methodAccessList;
  }

  public void enableMethod(String methodName, boolean state)
  {
    if (!(state)) {
      getMethodAccessList().put(methodName.toUpperCase(), new Boolean(state));
    }
    else
    {
      getMethodAccessList().remove(methodName.toUpperCase());
    }
  }

  public void checkMethodAccess(String methodName, long accessModifier)
    throws MXException, RemoteException
  {
    if (new BitFlag(accessModifier).isFlagSet(2L)) {
      return;
    }

    if (!(getProfile().getUserStatus().equals("ACTIVE")))
    {
      String[] params = { getUserInfo().getUserName() };
      throw new MXInactiveUserException("access", "inactiveuser", params);
    }

    if (getMethodAccessList().get(methodName.toUpperCase()) != null)
    {
      if (this.mxException == null) {
        throw new MXAccessException("access", "notavailable");
      }
      throw this.mxException;
    }

    if (!(methodName.equalsIgnoreCase("add"))) {
      return;
    }
    canAdd();

    if ((!(getMboSetInfo().isPersistent())) || 
      (!(isFlagSet(1L))))
    {
      return;
    }

    try
    {
      if ((getApp() != null) && (getApp().length() > 0))
      {
        String siteorg = null;

        if (getProfile().orgLevelApp(getApp()))
        {
          if ((getInsertOrg() != null) && (getInsertOrg().trim().length() > 0))
            siteorg = getInsertOrg();
          else
            siteorg = getProfile().getDefaultOrg();
        }
        else if (getProfile().siteLevelApp(getApp()))
        {
          if ((getInsertSite() != null) && (getInsertSite().trim().length() > 0))
            siteorg = getInsertSite();
          else {
            siteorg = getProfile().getDefaultSite();
          }
        }

        if ((siteorg == null) || (siteorg.trim().equals("")))
          throw new MXAccessException("access", "nodefaultsite");
      }
    }
    catch (RemoteException e)
    {
      Object[] args = { methodName.toUpperCase(), getName() };
      throw new MXAccessException("access", "method", args);
    }

    if (this.mxException == null)
    {
      Object[] args = { methodName.toUpperCase(), getName() };
      throw new MXAccessException("access", "method", args);
    }

    throw this.mxException;
  }

  public final void checkMethodAccess(String methodName)
    throws MXException, RemoteException
  {
    checkMethodAccess(methodName, 0L);
  }

  public String getUserName()
    throws MXException, RemoteException
  {
    return this.user.getUserName();
  }

  public ProfileRemote getProfile()
    throws MXException, RemoteException
  {
    return getMboServer().getProfile(getUserInfo());
  }

  public final void copy(MboSetRemote mboset)
    throws MXException, RemoteException
  {
    copy(mboset, 0L);
  }

  public final void copy(MboSetRemote mboset, long mboAddFlags)
    throws MXException, RemoteException
  {
    int xx = 0;

    MboRemote mbo;
	while ((mbo = getMbo(xx)) != null)
    {
   //   MboRemote mbo;
      if (!(mbo.toBeDeleted()))
      {
        if (mboAddFlags == 0L)
          mbo.copy(mboset);
        else
          ((Mbo)mbo).copy(mboset, mboAddFlags);
      }
      ++xx;
    }
  }

  public final void copy(MboSetRemote sourceSet, String[] srcAttributes, String[] destAttributes)
    throws MXException, RemoteException
  {
    int xx = 0;

    ArrayList a = new ArrayList();
    try
    {
      for (xx = 0; xx < sourceSet.getSize(); ++xx)
      {
        MboRemote sourceMbo = sourceSet.getMbo(xx);
        if ((sourceMbo.toBeDeleted()) || (!(sourceMbo.isSelected())))
          continue;
        MboRemote mbo = add();
        a.add(mbo);
        for (int i = 0; i < srcAttributes.length; ++i)
        {
          mbo.setValue(destAttributes[i], sourceMbo.getString(srcAttributes[i]));
        }

      }

    }
    catch (MXException e)
    {
      Iterator it = a.iterator();
      while (it.hasNext())
      {
        deleteAndRemove((MboRemote)it.next());
      }
      throw e;
    }
  }

  public MboSetRemote getSharedMboSet(String objectName, String relationship)
    throws MXException, RemoteException
  {
    objectName = objectName.toUpperCase();

    String key = objectName + relationship;

    if (this.sharedSets == null) {
      this.sharedSets = new Hashtable();
    }
    else
    {
      MboSetRemote returnSet = (MboSetRemote)this.sharedSets.get(key);
      if (returnSet != null) {
        return returnSet;
      }
    }

    MboSetRemote returnSet = getMboServer().getMboSet(objectName, getUserInfo());
    returnSet.setRelationship(relationship);
    returnSet.setMXTransaction(getMXTransaction());

    if (isFlagSet(7L))
    {
      returnSet.setFlag(7L, true);
    }

    this.sharedSets.put(key, returnSet);
    return returnSet;
  }

  public MXException[] getWarnings()
  {
    MXException[] ret = null;
    try
    {
      Object[] warnings = MXServer.getMXServer().getWarnings(getUserInfo().getConnectionKey()).toArray();
      ret = new MXException[warnings.length];

      for (int count = 0; count < warnings.length; ++count)
        ret[count] = ((MXException)warnings[count]);
    }
    catch (RemoteException e)
    {
      if (getMboLogger().isErrorEnabled())
      {
        getMboLogger().error(e);
      }

    }

    clearWarnings();

    return ret;
  }

  public void clearWarnings()
  {
    try
    {
      MXServer.getMXServer().clearWarnings(getUserInfo().getConnectionKey());
    }
    catch (RemoteException e)
    {
      if (!(getMboLogger().isErrorEnabled()))
        return;
      getMboLogger().error(e);
    }
  }

  public boolean hasWarnings()
  {
    try
    {
      return MXServer.getMXServer().hasWarnings(getUserInfo().getConnectionKey());
    }
    catch (RemoteException e)
    {
      if (getMboLogger().isErrorEnabled())
      {
        getMboLogger().error(e);
      }
    }
    return false;
  }

  public void addWarning(MXException e)
  {
    try
    {
      MXServer.getMXServer().addWarning(e, getUserInfo().getConnectionKey());
    }
    catch (RemoteException re)
    {
      if (!(getMboLogger().isErrorEnabled()))
        return;
      getMboLogger().error(re);
    }
  }

  public void addWarnings(MXException[] es)
  {
    for (int i = 0; i < es.length; ++i)
    {
      if (es[i] == null)
        continue;
      addWarning(es[i]);
    }
  }

  public void setMXTransaction(MXTransaction txn)
    throws RemoteException
  {
    if (isFlagSet(8L))
    {
      this.potentialTransaction = txn;
      return;
    }
    if (txn != null)
    {
      if (this.activeTransaction == null)
      {
        txn.add(this);
        this.activeTransaction = txn;
      } else {
        if (txn == this.activeTransaction)
          return;
        txn.add(this.activeTransaction);
      }
    }
    else this.activeTransaction = null;
  }

  public MXTransaction getMXTransaction()
    throws RemoteException
  {
    if (isFlagSet(8L))
    {
      if (this.potentialTransaction == null)
        this.potentialTransaction = MXServer.getMXServer().createMXTransaction();
      return this.potentialTransaction;
    }

    if (this.activeTransaction == null)
    {
      this.activeTransaction = MXServer.getMXServer().createMXTransaction();
      this.activeTransaction.add(this);
    }
    else
    {
      try
      {
        this.activeTransaction.indexOf(this);
      }
      catch (MXObjectNotFoundException ex)
      {
        this.activeTransaction.add(this);
      }
    }

    return this.activeTransaction;
  }

  public void save()
    throws MXException, RemoteException
  {
    save(0L);
  }

  public void save(long flags)
    throws MXException, RemoteException
  {
    this.saveFlags = flags;

    MXTransaction txn = getMXTransaction();
    Connection con = null;
    try
    {
      removeDeletedNewRecords();

      removeNewRelatedRecordsForDeletedMbo();

      getESigTransactionId();

      con = getMboServer().getDBConnection(getUserInfo().getConnectionKey());
      txn.save();
      txn.commit();
    }
    catch (MXException e)
    {
      txn.rollback();
      if ((getMboLogger().isErrorEnabled()) && (!(e instanceof MXApplicationException)))
        getMboLogger().error(e);
      throw e;
    }
    catch (Exception ex)
    {
      txn.rollback();
      if (getMboLogger().isErrorEnabled())
        getMboLogger().error(ex, ex);
      throw new MXSystemException("system", "unknownerror", ex);
    }
    catch (Throwable t)
    {
      t.printStackTrace();
      txn.rollback();
      if (getMboLogger().isErrorEnabled())
        getMboLogger().error(t, t);
      throw new MXSystemException("system", "unknownerror", t);
    }
    finally
    {
      if (con != null)
      {
        getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
      }
    }
  }

  public void save(MXTransaction txn)
    throws MXException, RemoteException
  {
    save(txn, 0L);
  }

  public void save(MXTransaction txn, long flags)
    throws MXException, RemoteException
  {
    this.saveFlags = flags;

    if (this.activeTransaction == null)
      txn.add(this);
    else
      txn.add(this.activeTransaction);
  }

  public void saveTransaction(MXTransaction txn)
    throws MXException, RemoteException
  {
    if ((this.activeTransaction != null) && (this.activeTransaction != txn)) {
      txn.add(this.activeTransaction);
    }

    KeyValue currKey = null;

    if ((getMbo() == null) || (getMbo().toBeDeleted()))
      currKey = null;
    else {
      currKey = this.currMbo.getKeyValue();
    }

    if (!(toBeSaved()))
    {
      return;
    }

    removeDeletedNewRecords();

    Mbo mbo = null;
    Connection con = null;

    Vector deleteMbos = new Vector();
    Vector insertMbos = new Vector();
    Vector updateMbos = new Vector();
    Vector otherModifiedMbos = new Vector();
    Enumeration e;
    try
    {
      con = getMboServer().getDBConnection(getUserInfo().getConnectionKey());

      for (e = this.mboVec.elements(); e.hasMoreElements(); )
      {
        mbo = (Mbo)e.nextElement();
        if (mbo == null)
          continue;
        if (mbo.toBeDeleted())
        {
          mbo.checkSiteOrgAccessForSave();
          deleteMbos.add(mbo);
        }
        if (mbo.toBeAdded())
        {
          mbo.checkSiteOrgAccessForSave();
          insertMbos.add(mbo);
        }
        if (mbo.thisToBeUpdated())
        {
          mbo.checkSiteOrgAccessForSave();
          updateMbos.add(mbo);
        }
        if (!(mbo.toBeSaved()))
          continue;
        otherModifiedMbos.add(mbo);
      }

      for (e = deleteMbos.elements(); e.hasMoreElements(); )
      {
        mbo = (Mbo)e.nextElement();
        if (mbo == null)
          continue;
        handleMLMbo(mbo, txn, "D");

        deleteMbo(con, mbo);
        eAuditMbo(con, mbo, "D");
      }

      for (e = insertMbos.elements(); e.hasMoreElements(); )
      {
        mbo = (Mbo)e.nextElement();
        if (mbo == null)
          continue;
        handleMLMbo(mbo, txn, "I");

        insertMbo(con, mbo);
        eAuditMbo(con, mbo, "I");
      }

      for (e = updateMbos.elements(); e.hasMoreElements(); )
      {
        mbo = (Mbo)e.nextElement();
        if (mbo == null)
          continue;
        handleMLMbo(mbo, txn, "U");

        updateMbo(con, mbo);
        eAuditMbo(con, mbo, "U");
      }

    }
    catch (SQLException ex)
    {
      if (getSqlLogger().isErrorEnabled())
      {
        getSqlLogger().error(ex);
      }
      if (mbo != null)
      {
        Object[] params = new Object[] { new Integer(ex.getErrorCode()).toString(), mbo.getRecordIdentifer() };
        throw new MXSystemException("system", "sqlWithIdentifier", params, ex);
      }

      Object[] params = { new Integer(ex.getErrorCode()).toString() };
      throw new MXSystemException("system", "sql", params, ex);
    }
    finally
    {
      if (deleteMbos != null)
      {
        deleteMbos.clear();
        deleteMbos = null;
      }
      if (insertMbos != null)
      {
        insertMbos.clear();
        insertMbos = null;
      }
      if (updateMbos != null)
      {
        updateMbos.clear();
        updateMbos = null;
      }
      if (otherModifiedMbos != null)
      {
        otherModifiedMbos.clear();
        otherModifiedMbos = null;
      }

      if (con != null)
      {
        getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
      }
    }
  }

  private void removeDeletedNewRecords()
    throws MXException, RemoteException
  {
    if ((this.newlyCreated == null) || (count(4) == 0))
    {
      return;
    }

    Enumeration newMbos = this.newlyCreated.elements();
    while (newMbos.hasMoreElements())
    {
      MboRemote newM = (MboRemote)newMbos.nextElement();
      if (newM.toBeDeleted())
      {
        remove(newM);
      }
    }
  }

  private void removeNewRelatedRecordsForDeletedMbo()
    throws MXException, RemoteException
  {
    for (Enumeration e = this.mboVec.elements(); e.hasMoreElements(); )
    {
      Mbo mbo = (Mbo)e.nextElement();
      if ((mbo != null) && (mbo.toBeDeleted()))
      {
        Hashtable rs = mbo.getRelatedSets();
        for (Enumeration ee = rs.elements(); ee.hasMoreElements(); )
        {
          MboSet msr = (MboSet)ee.nextElement();
          if (msr != null)
          {
            if (msr.newlyCreated == null)
              continue;
            Enumeration newMbos = msr.newlyCreated.elements();
            while (newMbos.hasMoreElements())
            {
              MboRemote newM = (MboRemote)newMbos.nextElement();
              msr.remove(newM);
            }
          }
        }
      }
    }
    Enumeration ee;
  }

  public boolean validateTransaction(MXTransaction txn)
    throws MXException, RemoteException
  {
    if (!(toBeSaved()))
    {
      return false;
    }

    validate();

    saveMbos();

    return true;
  }

  public void saveMbos()
    throws MXException, RemoteException
  {
    for (Enumeration e = this.mboVec.elements(); e.hasMoreElements(); )
    {
      Mbo mbo = (Mbo)e.nextElement();
      if ((mbo != null) && (mbo.toBeSaved()) && (((mbo.toBeAdded()) || (mbo.toBeDeleted()) || (mbo.thisToBeUpdated()))))
      {
        mbo.save();
      }
    }
  }

  public void fireEventsBeforeDB(MXTransaction txn)
    throws MXException, RemoteException
  {
    if (!(toBeSaved()))
    {
      return;
    }
    for (Enumeration e = this.mboVec.elements(); e.hasMoreElements(); )
    {
      Mbo mbo = (Mbo)e.nextElement();
      if ((mbo != null) && (mbo.toBeSaved()) && (((mbo.toBeAdded()) || (mbo.toBeDeleted()) || (mbo.thisToBeUpdated()))))
      {
        mbo.fireEvent("validate");
        mbo.fireEvent("preSaveEventAction");
        mbo.fireEvent("preSaveInternalEventAction");
      }
    }
  }

  public void fireEventsAfterDB(MXTransaction txn)
    throws MXException, RemoteException
  {
    if (!(toBeSaved()))
    {
      if (getMboSetInfo().getTransactionLogger().isDebugEnabled())
      {
        getMboSetInfo().getTransactionLogger().debug("In MboSet.fireEventsAfterDB(), MboSet" + this + "is not toBeSaved(). Event not fired");
      }
      return;
    }
    if (getMboSetInfo().getTransactionLogger().isDebugEnabled())
    {
      getMboSetInfo().getTransactionLogger().debug("In MboSet.fireEventsAfterDB(), prepare firing event for each mbo of " + this);
    }
    for (Enumeration e = this.mboVec.elements(); e.hasMoreElements(); )
    {
      Mbo mbo = (Mbo)e.nextElement();

      if ((mbo != null) && (mbo.toBeSaved()) && (((mbo.toBeAdded()) || (mbo.toBeDeleted()) || (mbo.thisToBeUpdated()))))
      {
        if (getMboSetInfo().getTransactionLogger().isDebugEnabled())
        {
          getMboSetInfo().getTransactionLogger().debug("In MboSet.fireEventsAfterDB(), event fired for mbo:" + mbo);
        }

        mbo.fireEvent("eventAction");
        mbo.fireEvent("postSaveInternalEventAction");
      }
      else if (getMboSetInfo().getTransactionLogger().isDebugEnabled())
      {
        getMboSetInfo().getTransactionLogger().debug("In MboSet.fireEventsAfterDB(), Condition false, event not fired for mbo:" + mbo + " due to mbo.toBeSaved():" + mbo.toBeSaved() + " mbo.toBeAdded():" + mbo.toBeAdded() + " mbo.toBeDeleted():" + mbo.toBeDeleted() + " mbo.thisToBeUpdated():" + mbo.thisToBeUpdated());
      }
    }
  }

  public void fireEventsAfterDBCommit(MXTransaction txn)
    throws MXException, RemoteException
  {
    if (!(toBeSaved()))
    {
      return;
    }
    for (Enumeration e = this.mboVec.elements(); e.hasMoreElements(); )
    {
      Mbo mbo = (Mbo)e.nextElement();
      if (mbo != null)
      {
        try
        {
          mbo.fireEvent("postCommitEventAction");
        }
        catch (Throwable t)
        {
          if (getMboLogger().isErrorEnabled())
          {
            getMboLogger().error("MboSet postCommitEventAction :", t);
          }
        }
      }
    }

    this.toBeSaved = false;

    if ((this.saveFlags & 1L) == 1L)
      resetForRefreshOnSave();
    else
      resetThis();
  }

  public void commitTransaction(MXTransaction txn)
    throws MXException, RemoteException
  {
    commit();
  }

  public void rollbackTransaction(MXTransaction txn)
    throws MXException, RemoteException
  {
    rollback();
  }

  public void undoTransaction(MXTransaction txn)
    throws MXException, RemoteException
  {
  }

  private void insertMbo(Connection con, Mbo mbo)
    throws MXException, RemoteException, SQLException
  {
    if (!(mbo.toBeAdded()))
    {
      return;
    }

    Map vh = mbo.getPersistentAttributeValues();
    if (vh == null)
    {
      return;
    }

    MboSetInfo mboSetInfo = mbo.getMboSetInfo();

    Entity entity = mboSetInfo.getEntity();

    HashMap attributes = new HashMap();
    String spid = getSPID(con);

    Iterator tableIterator = entity.getTablesInHierarchyOrder();
    while (tableIterator.hasNext())
    {
      String tableName = (String)tableIterator.next();

      attributes.clear();
      Iterator attributeNameIterator = vh.keySet().iterator();
      String columnName;
      while (attributeNameIterator.hasNext())
      {
        String attributeName = (String)attributeNameIterator.next();
        MboValue mboValue = (MboValue)vh.get(attributeName);
        MboValueInfo mboValueInfo = mboValue.getMboValueInfo();

        String entityColumnName = mboValueInfo.getEntityColumnName();
        columnName = entity.getColumnName(entityColumnName, tableName);

        if (columnName != null)
        {
          attributes.put(attributeName, columnName);
        }

      }

      StringBuffer insertStmt = new StringBuffer("insert into ");
      insertStmt.append(tableName.toLowerCase());
      insertStmt.append(" (");

      StringBuffer valuesStmt = null;

      HashMap attrMLs = new HashMap();

      attributeNameIterator = attributes.keySet().iterator();
      while (attributeNameIterator.hasNext())
      {
        String attributeName = (String)attributeNameIterator.next();
        columnName = (String)attributes.get(attributeName);

        if ((processML()) && 
          (mboSetInfo.getAttribute(attributeName).isMLInUse()))
        {
          if ((mbo.getMboValue(attributeName + "_BASELANGUAGE").isNull()) && (!(mbo.getMboValue(attributeName).isRequired())))
          {
            attrMLs.put(columnName, attributeName);
          }

          vh.put(attributeName, mbo.getMboValue(attributeName + "_BASELANGUAGE"));
        }

        if (valuesStmt == null)
        {
          insertStmt.append(columnName.toLowerCase());
          valuesStmt = new StringBuffer("?");
        }
        else
        {
          insertStmt.append(",");
          insertStmt.append(columnName.toLowerCase());
          valuesStmt.append(",?");
        }
      }

      insertStmt.append(") values (");
      insertStmt.append(valuesStmt);
      insertStmt.append(")");

      logSQLStatement(spid, insertStmt.toString(), getApp(), getName());

      PreparedStatement pstmt = null;
      try
      {
        pstmt = con.prepareStatement(insertStmt.toString());

        int bindIndex = 0;

        attributeNameIterator = attributes.keySet().iterator();
        while (attributeNameIterator.hasNext())
        {
          String attributeName = (String)attributeNameIterator.next();
          MboValue mboValue = (MboValue)vh.get(attributeName);

          String columnName2 = (String)attributes.get(attributeName);

          if (!(attrMLs.containsKey(columnName2)))
          {
            ++bindIndex;
            setBindValue(pstmt, bindIndex, mboValue, columnName2);
          }
        }

        long startTime = System.currentTimeMillis();

        pstmt.executeUpdate();

        long execTime = System.currentTimeMillis() - startTime;

        logSQLStatementTime(spid, insertStmt.toString(), execTime, getApp(), getName(), con);
      }
      finally
      {
        try
        {
          if (pstmt != null)
          {
            pstmt.close();
          }
        }
        catch (Exception ex)
        {
        }
      }
    }
  }

  private void deleteMbo(Connection con, Mbo mbo)
    throws MXException, RemoteException, SQLException
  {
    if (!(mbo.toBeDeleted()))
    {
      return;
    }

    if (mbo.isNew())
    {
      return;
    }

    MboSetInfo mboSetInfo = mbo.getMboSetInfo();

    Entity entity = mboSetInfo.getEntity();
    String spid = getSPID(con);

    Iterator tableIterator = entity.getTablesInReverseHierarchyOrder();
    while (tableIterator.hasNext())
    {
      String tableName = (String)tableIterator.next();

      StringBuffer deleteStmt = new StringBuffer("delete from ");
      deleteStmt.append(tableName.toLowerCase());
      deleteStmt.append(" where ");

      Iterator keyIterator = null;

      String uniqueColumnName = getMboServer().getMaximoDD().getUniqueIdColumn(tableName);
      String attributeName;
      MboValue mv;
      Object fetchValue;
      if ((uniqueColumnName != null) && (uniqueColumnName.trim().length() > 0))
      {
        deleteStmt.append(uniqueColumnName.toLowerCase());
        deleteStmt.append("=?");

        if (entity.hasRowStamp())
        {
          deleteStmt.append(" and ");
          deleteStmt.append("rowstamp=?");
        }

      }
      else
      {
        keyIterator = mboSetInfo.getKeyAttributeIterator();
        while (keyIterator.hasNext())
        {
          MboValueInfo attributeInfo = (MboValueInfo)keyIterator.next();
          String entityColumnName = attributeInfo.getEntityColumnName();
          String columnName = entity.getColumnName(entityColumnName, tableName);

          if (columnName == null)
          {
            throw new IllegalStateException("The key columns are incorrectly defined for business object " + getName());
          }

          deleteStmt.append(columnName.toLowerCase());

          attributeName = attributeInfo.getAttributeName();
          mv = mbo.getMboValue(attributeName);
          fetchValue = mbo.getFetchValue(attributeName);
          if (fetchValue == null)
          {
            deleteStmt.append(" is null");
          }
          else
          {
            deleteStmt.append("=?");
          }

          if (keyIterator.hasNext())
          {
            deleteStmt.append(" and ");
          }

        }

        if (entity.hasRowStamp())
        {
          if (mboSetInfo.getKeySize() > 0)
          {
            deleteStmt.append(" and ");
          }
          deleteStmt.append("rowstamp=?");
        }

      }

      logSQLStatement(spid, deleteStmt.toString(), getApp(), getName());

      PreparedStatement pstmt = null;
      try
      {
        pstmt = con.prepareStatement(deleteStmt.toString());

        int bindIndex = 0;

        if ((keyIterator == null) && (uniqueColumnName != null) && (uniqueColumnName.trim().length() > 0))
        {
          ++bindIndex;
          Object bindValue = mbo.getFetchValue(uniqueColumnName);
          pstmt.setObject(bindIndex, bindValue);

          logBindValue(uniqueColumnName, bindValue);
        }
        else
        {
          keyIterator = mboSetInfo.getKeyAttributeIterator();
          while (keyIterator.hasNext())
          {
            MboValueInfo keyInfo = (MboValueInfo)keyIterator.next();

            attributeName = keyInfo.getAttributeName();
            mv = mbo.getMboValue(attributeName);
            fetchValue = mbo.getFetchValue(attributeName);
            if (fetchValue == null)
            {
              continue;
            }

            ++bindIndex;
            Object bindValue = mbo.getFetchValue(attributeName);
            pstmt.setObject(bindIndex, bindValue);

            String entityColumnName = keyInfo.getEntityColumnName();
            String columnName = entity.getColumnName(entityColumnName, tableName);

            logBindValue(columnName, bindValue);
          }

        }

        if (entity.hasRowStamp())
        {
          MboRecordData mrd = mbo.getMboRecordData();
          RowStampData rsd = mrd.getRowStampData();
          Object rowStampValue = rsd.getRowStampValue(tableName);

          ++bindIndex;

          pstmt.setObject(bindIndex, rowStampValue);

          logBindValue("rowstamp", rowStampValue);
        }

        int deleteRowCount = 0;

        long startTime = System.currentTimeMillis();

        deleteRowCount = pstmt.executeUpdate();

        long execTime = System.currentTimeMillis() - startTime;
        logSQLStatementTime(spid, deleteStmt.toString(), execTime, getApp(), getName(), con);

        if (deleteRowCount == 0)
        {
          MXRowUpdateException mxr = new MXRowUpdateException("system", "rowupdateexception");

          logRowUpdatedException(mxr);
          throw mxr;
        }
      }
      finally
      {
        try
        {
          if (pstmt != null)
          {
            pstmt.close();
          }
        }
        catch (Exception ex)
        {
        }
      }
    }
  }

  private void updateMbo(Connection con, Mbo mbo)
    throws MXException, RemoteException, SQLException
  {
    if (!(mbo.toBeUpdated())) {
      return;
    }
    if (relationName==null ||"OPENWO".equals(relationName)){
	    new Throwable().printStackTrace();
    }
    System.out.println("^^^^^^^^^^^^^^^^^^^^Updating mbo:"+relationName+" for id="+mbo.getUniqueIDValue());
    Map vh = mbo.getModifiedPersistentAttributeValues();
    if (vh == null)
    {
      return;
    }

    MboSetInfo mboSetInfo = mbo.getMboSetInfo();

    Entity entity = mboSetInfo.getEntity();

    HashMap attributes = new HashMap();
    String spid = getSPID(con);

    Iterator tableIterator = entity.getTablesInHierarchyOrder();
    while (tableIterator.hasNext())
    {
      HashMap attrMLs = new HashMap();
      String tableName = (String)tableIterator.next();

      attributes.clear();
      Iterator attributeNameIterator = vh.keySet().iterator();
      while (attributeNameIterator.hasNext())
      {
        String attributeName = (String)attributeNameIterator.next();
        MboValue mboValue = (MboValue)vh.get(attributeName);
        MboValueInfo mboValueInfo = mboValue.getMboValueInfo();

        String entityColumnName = mboValueInfo.getEntityColumnName();
        String columnName = entity.getColumnName(entityColumnName, tableName);

        if (columnName != null)
        {
          attributes.put(attributeName, columnName);
        }

      }

      if (attributes.size() == 0)
      {
        continue;
      }

      StringBuffer updateStmt = new StringBuffer("update ");
      updateStmt.append(tableName.toLowerCase());
      updateStmt.append(" set ");

      boolean hasUpdateAttr = false;

      attributeNameIterator = attributes.keySet().iterator();

      while (attributeNameIterator.hasNext())
      {
        String attributeName = (String)attributeNameIterator.next();
        String columnName = (String)attributes.get(attributeName);

        if ((processML()) && 
          (mboSetInfo.getAttribute(attributeName).isMLInUse()))
        {
          if (!(mbo.getMboValue(attributeName + "_BASELANGUAGE").isModified()))
          {
            attrMLs.put(columnName, attributeName);
          }

          vh.put(attributeName, mbo.getMboValue(attributeName + "_BASELANGUAGE"));
        }

        if (hasUpdateAttr)
        {
          updateStmt.append(",");
        }
        else {
          hasUpdateAttr = true;
        }
        updateStmt.append(columnName.toLowerCase());
        updateStmt.append("=?");
      }

      if (!(hasUpdateAttr)) {
        continue;
      }
      updateStmt.append(" where ");

      Iterator keyIterator = null;

      String uniqueColumnName = getMboServer().getMaximoDD().getUniqueIdColumn(tableName);
      String attributeName;
      MboValue mv;
      Object fetchValue;
      if ((uniqueColumnName != null) && (uniqueColumnName.trim().length() > 0))
      {
        updateStmt.append(uniqueColumnName.toLowerCase());
        updateStmt.append("=?");

        if (entity.hasRowStamp())
        {
          updateStmt.append(" and ");
          updateStmt.append("rowstamp=?");
        }

      }
      else
      {
        keyIterator = mboSetInfo.getKeyAttributeIterator();
        while (keyIterator.hasNext())
        {
          MboValueInfo attributeInfo = (MboValueInfo)keyIterator.next();
          String entityColumnName = attributeInfo.getEntityColumnName();
          String columnName = entity.getColumnName(entityColumnName, tableName);

          if (columnName == null)
          {
            throw new IllegalStateException("The key columns are incorrectly defined for " + getName());
          }

          updateStmt.append(columnName.toLowerCase());

          attributeName = attributeInfo.getAttributeName();
          mv = mbo.getMboValue(attributeName);
          fetchValue = mbo.getFetchValue(attributeName);
          if (fetchValue == null)
          {
            updateStmt.append(" is null");
          }
          else
          {
            updateStmt.append("=?");
          }
          if (keyIterator.hasNext())
          {
            updateStmt.append(" and ");
          }

        }

        if (entity.hasRowStamp())
        {
          if (mboSetInfo.getKeySize() > 0)
          {
            updateStmt.append(" and ");
          }
          updateStmt.append("rowstamp=?");
        }

      }

      logSQLStatement(spid, updateStmt.toString(), getApp(), getName());

      PreparedStatement pstmt = null;
      try
      {
        pstmt = con.prepareStatement(updateStmt.toString());

        int bindIndex = 0;
        attributeNameIterator = attributes.keySet().iterator();
        while (attributeNameIterator.hasNext())
        {
          String attributeName2 = (String)attributeNameIterator.next();

          MboValue mboValue = (MboValue)vh.get(attributeName2);

          String columnName = (String)attributes.get(attributeName2);

          if (!(attrMLs.containsKey(columnName)))
          {
            ++bindIndex;
            setBindValue(pstmt, bindIndex, mboValue, columnName);
          }

        }

        if ((keyIterator == null) && (uniqueColumnName != null) && (uniqueColumnName.trim().length() > 0))
        {
          ++bindIndex;
          Object bindValue = mbo.getFetchValue(uniqueColumnName);
          pstmt.setObject(bindIndex, bindValue);

          logBindValue(uniqueColumnName, bindValue);
        }
        else
        {
          keyIterator = mboSetInfo.getKeyAttributeIterator();
          while (keyIterator.hasNext())
          {
            MboValueInfo keyInfo = (MboValueInfo)keyIterator.next();

            attributeName = keyInfo.getAttributeName();
            mv = mbo.getMboValue(attributeName);
            fetchValue = mbo.getFetchValue(attributeName);
            if (fetchValue == null)
            {
              continue;
            }

            ++bindIndex;
            Object bindValue = mbo.getFetchValue(attributeName);
            pstmt.setObject(bindIndex, bindValue);

            String entityColumnName = keyInfo.getEntityColumnName();
            String columnName = entity.getColumnName(entityColumnName, tableName);

            logBindValue(columnName, bindValue);
          }

        }

        if (entity.hasRowStamp())
        {
          MboRecordData mrd = mbo.getMboRecordData();
          RowStampData rsd = mrd.getRowStampData();
          Object rowStampValue = rsd.getRowStampValue(tableName);

          ++bindIndex;

          pstmt.setObject(bindIndex, rowStampValue);

          logBindValue("rowstamp", rowStampValue);
        }

        int updateRowCount = 0;

        long startTime = System.currentTimeMillis();

        updateRowCount = pstmt.executeUpdate();

        long execTime = System.currentTimeMillis() - startTime;
        logSQLStatementTime(spid, updateStmt.toString(), execTime, getApp(), getName(), con);

        if (updateRowCount == 0)
        {
          MXRowUpdateException mxr = new MXRowUpdateException("system", "rowupdateexception");

          logRowUpdatedException(mxr);
          throw mxr;
        }
      }
      finally
      {
        try {
          if (pstmt != null)
          {
            pstmt.close();
          }
        }
        catch (Exception ex)
        {
        }
      }
    }
  }

  private void setBindValue(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException, RemoteException
  {
    switch (mv.getType())
    {
    case 3:
      setBindValueAsDate(pstmt, bindIndex, mv, columnName);
      break;
    case 5:
      setBindValueAsTime(pstmt, bindIndex, mv, columnName);
      break;
    case 4:
      setBindValueAsTimeStamp(pstmt, bindIndex, mv, columnName);
      break;
    case 8:
    case 9:
    case 10:
    case 11:
      setBindValueAsDouble(pstmt, bindIndex, mv, columnName);
      break;
    case 6:
    case 7:
      setBindValueAsLong(pstmt, bindIndex, mv, columnName);
      break;
    case 13:
      setBindValueAsStringForGL(pstmt, bindIndex, mv, columnName);
      break;
    case 12:
      setBindValueAsIntForYORN(pstmt, bindIndex, mv, columnName);
      break;
    case 15:
    case 16:
      setBindValueAsBytes(pstmt, bindIndex, mv.getBytes(), columnName);
      break;
    case 17:
      if (getMboServer().getMaximoDD().storeClobAsClob()) {
        setBindValueAsClob(pstmt, bindIndex, mv, columnName); return;
      }
      setBindValueAsString(pstmt, bindIndex, mv, columnName);

      break;
    case 18:
      if (getMboServer().getMaximoDD().storeBlobAsBlob()) {
        setBindValueAsBlob(pstmt, bindIndex, mv, columnName); return;
      }
      setBindValueAsBytes(pstmt, bindIndex, mv.getBytes(), columnName);

      break;
    case 14:
    default:
      setBindValueAsString(pstmt, bindIndex, mv, columnName);
    }
  }

  private void setBindValueAsDate(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException
  {
    if (mv.isNull())
    {
      logBindValue(columnName, null);
      pstmt.setDate(bindIndex, null);
    }
    else
    {
      long time = mv.getDate().getTime();
      java.sql.Date sqlDateValue = new java.sql.Date(time);

      logBindValue(columnName, sqlDateValue);
      pstmt.setDate(bindIndex, sqlDateValue);
    }
  }

  private void setBindValueAsTimeStamp(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException
  {
    if (mv.isNull())
    {
      logBindValue(columnName, null);
      pstmt.setTimestamp(bindIndex, null);
    }
    else
    {
      long time = mv.getDate().getTime();
      Timestamp timeStampValue = new Timestamp(time);

      logBindValue(columnName, timeStampValue);
      pstmt.setTimestamp(bindIndex, timeStampValue);
    }
  }

  private void setBindValueAsTimeStamp(PreparedStatement pstmt, int bindIndex, Timestamp value, String columnName)
    throws SQLException, MXException
  {
    if (value == null)
    {
      logBindValue(columnName, null);
      pstmt.setTimestamp(bindIndex, null);
    }
    else
    {
      Timestamp timeStampValue = value;

      logBindValue(columnName, timeStampValue);
      pstmt.setTimestamp(bindIndex, timeStampValue);
    }
  }

  private void setBindValueAsTime(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException
  {
    if (mv.isNull())
    {
      logBindValue(columnName, null);
      pstmt.setTime(bindIndex, null);
    }
    else
    {
      long time = mv.getDate().getTime();
      Time sqlTimeValue = new Time(time);

      logBindValue(columnName, sqlTimeValue);
      pstmt.setTime(bindIndex, sqlTimeValue);
    }
  }

  private void setBindValueAsDouble(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException
  {
    if (mv.isNull())
    {
      logBindValue(columnName, null);
      pstmt.setNull(bindIndex, 8);
    }
    else
    {
      double doubleValue = mv.getDouble();

      logBindValue(columnName, new Double(doubleValue));
      pstmt.setDouble(bindIndex, doubleValue);
    }
  }

  private void setBindValueAsLong(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException
  {
    if (mv.isNull())
    {
      logBindValue(columnName, null);
      pstmt.setNull(bindIndex, 4);
    }
    else
    {
      long longValue = mv.getLong();

      logBindValue(columnName, new Long(longValue));
      pstmt.setLong(bindIndex, longValue);
    }
  }

  private void setBindValueAsStringForGL(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException
  {
    if (mv.isNull())
    {
      logBindValue(columnName, null);
      pstmt.setString(bindIndex, null);
    }
    else
    {
      try
      {
        String orgForGL = mv.getMbo().getOrgForGL(mv.getName());
        GLFormat glValue = new GLFormat(mv.getString(), orgForGL);
        String glStoredValue = glValue.toStorageString();

        logBindValue(columnName, glStoredValue);
        pstmt.setString(bindIndex, glStoredValue);
      }
      catch (RemoteException ex)
      {
        throw new MXApplicationException("system", "remoteexception");
      }
    }
  }

  protected void setBindValueAsBytes(PreparedStatement pstmt, int bindIndex, byte[] value, String columnName)
    throws SQLException, MXException
  {
    if ((value == null) || (value.length <= 0))
    {
      logBindValue(columnName, null);
      pstmt.setBytes(bindIndex, null);
    }
    else
    {
      logBindValue(columnName, value);
      pstmt.setBytes(bindIndex, value);
    }
  }

  private void setBindValueAsIntForYORN(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException
  {
    if (mv.isNull())
    {
      logBindValue(columnName, null);
      pstmt.setNull(bindIndex, 4);
    }
    else
    {
      String ynValue = mv.getString();

      String storedYNValue = MXFormat.convertToStoreYNValue(ynValue, getClientLocale());

      logBindValue(columnName, storedYNValue);
      pstmt.setInt(bindIndex, new Integer(storedYNValue).intValue());
    }
  }

  private void setBindValueAsString(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException
  {
    if ((mv.isNull()) && (!(mv.getName().endsWith("_BASELANGUAGE"))))
    {
      logBindValue(columnName, null);
      pstmt.setString(bindIndex, null);
    }
    else
    {
      String bindVal = mv.getString().trim();

      if ((bindVal.length() == 0) && (mv.getName().endsWith("_BASELANGUAGE")))
        bindVal = " ";
      logBindValue(columnName, bindVal);

      if (mboSetDBProductName.toUpperCase().indexOf("DB2") >= 0) {
        pstmt.setString(bindIndex, bindVal);
      }
      else
      {
        StringReader rd = new StringReader(bindVal);
        pstmt.setCharacterStream(bindIndex, rd, bindVal.length());
      }
    }
  }

  private void setBindValueAsString(PreparedStatement pstmt, int bindIndex, String value, String columnName)
    throws SQLException, MXException
  {
    if (value == null)
    {
      logBindValue(columnName, null);
      pstmt.setString(bindIndex, null);
    }
    else
    {
      String bindVal = value;

      logBindValue(columnName, bindVal);

      if (mboSetDBProductName.toUpperCase().indexOf("DB2") >= 0) {
        pstmt.setString(bindIndex, bindVal);
      }
      else
      {
        StringReader rd = new StringReader(bindVal);
        pstmt.setCharacterStream(bindIndex, rd, bindVal.length());
      }
    }
  }

  private void setBindValueAsClob(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException, RemoteException
  {
    Connection con = null;
    Statement s = null;
    ResultSet rs = null;
    Statement s2 = null;
    try
    {
      con = getMboServer().getDBConnection(getUserInfo().getConnectionKey());

      if (mv.isNull())
      {
        logBindValue(columnName, null);

        if (mboSetDBProductName.toUpperCase().indexOf("ORACLE") >= 0)
        {
          pstmt.setClob(bindIndex, CLOB.empty_lob());
        }
        else if (mboSetDBProductName.toUpperCase().indexOf("DB2") >= 0)
        {
          pstmt.setString(bindIndex, null);
        }
      }
      else
      {
        String bindVal = mv.getString().trim();
        logBindValue(columnName, bindVal);

        if (mboSetDBProductName.toUpperCase().indexOf("ORACLE") >= 0)
        {
          Clob rsClob;
          if (!(mv.getMbo().toBeAdded()))
          {
            s = con.createStatement();
            s.execute("select " + columnName + " from " + getMboSetInfo().getEntityName() + getWhereForClobUpdate(mv) + " for update");
            rs = s.getResultSet();
            rs.next();

            rsClob = rs.getClob(1);
            ((CLOB)rsClob).trim(0L);
            ((CLOB)rsClob).putString(1L, bindVal);
            pstmt.setClob(bindIndex, rsClob);
          }
          else if (getMboServer().getMaximoDD().getOraVersion() < 10)
          {
            s = con.createStatement();
            try
            {
              s.execute("select dummy_clob from dummy_table for update");
            }
            catch (SQLException se)
            {
              if (getSqlLogger().isInfoEnabled())
              {
                getSqlLogger().info("dummy_clob column is not defined in dummy_table. Please contact your system administrator.");
              }
              throw se;
            }
            rs = s.getResultSet();
            rs.next();

            rsClob = rs.getClob(1);
            ((CLOB)rsClob).putString(1L, bindVal);
            ((CLOB)rsClob).trim(bindVal.length());
            pstmt.setClob(bindIndex, rsClob);

            s2 = con.createStatement();
            s2.executeUpdate("update dummy_table set dummy_clob = empty_clob()");
          }
          else
          {
            ((OraclePreparedStatement)pstmt).setStringForClob(bindIndex, bindVal);
          }

        }
        else if (mboSetDBProductName.toUpperCase().indexOf("DB2") >= 0)
        {
          logBindValue(columnName, bindVal);

          StringReader rd = new StringReader(bindVal);
          pstmt.setCharacterStream(bindIndex, rd, bindVal.length());
        }
      }

    }
    catch (SQLException se)
    {
      throw se;
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw new MXApplicationException("system", "clobError");
    }
    finally
    {
      if (rs != null) {
        rs.close();
      }
      if (s != null) {
        s.close();
      }
      if (s2 != null) {
        s2.close();
      }
      getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
    }
  }

  private void setBindValueAsBlob(PreparedStatement pstmt, int bindIndex, MboValue mv, String columnName)
    throws SQLException, MXException, RemoteException
  {
    Connection con = null;
    Statement s = null;
    ResultSet rs = null;
    Statement s2 = null;
    try
    {
      con = getMboServer().getDBConnection(getUserInfo().getConnectionKey());

      if (mv.isNull())
      {
        logBindValue(columnName, null);

        if (mboSetDBProductName.toUpperCase().indexOf("ORACLE") >= 0)
        {
          pstmt.setBlob(bindIndex, BLOB.empty_lob());
        }
        else if (mboSetDBProductName.toUpperCase().indexOf("DB2") >= 0)
        {
          pstmt.setBytes(bindIndex, null);
        }
      }
      else
      {
        byte[] bindVal = mv.getBytes();
        logBindValue(columnName, bindVal);

        if (mboSetDBProductName.toUpperCase().indexOf("ORACLE") >= 0)
        {
          Blob rsBlob;
          if (!(mv.getMbo().toBeAdded()))
          {
            s = con.createStatement();
            s.execute("select " + columnName + " from " + getMboSetInfo().getEntityName() + getWhereForClobUpdate(mv) + " for update");
            rs = s.getResultSet();
            rs.next();

            rsBlob = rs.getBlob(1);
            ((BLOB)rsBlob).trim(0L);
            ((BLOB)rsBlob).putBytes(1L, bindVal);
            pstmt.setBlob(bindIndex, rsBlob);
          }
          else
          {
            s = con.createStatement();
            try
            {
              s.execute("select dummy_blob from dummy_table for update");
            }
            catch (SQLException se)
            {
              if (getSqlLogger().isInfoEnabled())
              {
                getSqlLogger().info("dummy_blob column is not defined in dummy_table. Please contact your system administrator.");
              }
              throw se;
            }
            rs = s.getResultSet();
            rs.next();

            rsBlob = rs.getBlob(1);
            ((BLOB)rsBlob).putBytes(1L, bindVal);
            pstmt.setBlob(bindIndex, rsBlob);

            s2 = con.createStatement();
            s2.executeUpdate("update dummy_table set dummy_blob = empty_blob()");
          }
        }
        else if (mboSetDBProductName.toUpperCase().indexOf("DB2") >= 0)
        {
          logBindValue(columnName, bindVal);

          pstmt.setBytes(bindIndex, bindVal);
        }
      }
    }
    catch (SQLException se)
    {
      throw se;
    }
    catch (Exception e)
    {
      e.printStackTrace();
      throw new MXApplicationException("system", "clobError");
    }
    finally
    {
      if (rs != null) {
        rs.close();
      }
      if (s != null) {
        s.close();
      }
      if (s2 != null) {
        s2.close();
      }
      getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
    }
  }

  private String getWhereForClobUpdate(MboValue mvClob)
    throws MXException, RemoteException
  {
    MboRemote mbo = mvClob.getMbo();
    MboSetInfo mboSetInfo = getMboSetInfo();
    Entity entity = mboSetInfo.getEntity();
    String tableName = getMboSetInfo().getEntityName();
    String where = " where ";
    int colIndex = 0;

    Iterator keyIterator = mboSetInfo.getKeyAttributeIterator();
    String attributeName;
    MboValue mv;
    Object fetchValue;
    while (keyIterator.hasNext())
    {
      ++colIndex;
      MboValueInfo attributeInfo = (MboValueInfo)keyIterator.next();
      String entityColumnName = attributeInfo.getEntityColumnName();
      String columnName = entity.getColumnName(entityColumnName, tableName);
      if (columnName == null)
      {
        throw new IllegalStateException("The key columns are incorrectly defined for " + getName());
      }

      where = where + columnName;

      attributeName = attributeInfo.getAttributeName();
      mv = ((Mbo)mbo).getMboValue(attributeName);
      fetchValue = ((Mbo)mbo).getFetchValue(attributeName);
      if (fetchValue == null)
      {
        where = where + " is null";
      }
      else
      {
        where = where + "=:" + colIndex;
      }
      if (keyIterator.hasNext())
      {
        where = where + " and ";
      }
    }

    SqlFormat sqf = new SqlFormat(where);
    int bindIndex = 0;

    keyIterator = mboSetInfo.getKeyAttributeIterator();
    while (keyIterator.hasNext())
    {
      MboValueInfo keyInfo = (MboValueInfo)keyIterator.next();
      attributeName = keyInfo.getAttributeName();
      mv = ((Mbo)mbo).getMboValue(attributeName);
      fetchValue = ((Mbo)mbo).getFetchValue(attributeName);
      if (fetchValue == null)
      {
        continue;
      }

      ++bindIndex;
      Object bindValue = ((Mbo)mbo).getFetchValue(attributeName);
      sqf.setObject(bindIndex, tableName, attributeName, bindValue.toString());
    }

    return sqf.format();
  }

  private void logSQLStatement(String spid, String sqlStatement, String appName, String objName)
  {
    if (!(getSqlLogger().isInfoEnabled()))
      return;
    StringBuffer lQuery = new StringBuffer("");
    lQuery.append("USER = (" + this.user.getLoginUserName() + ") SPID = (" + spid + ") ");
    lQuery.append(" app (" + appName + ") object (" + objName + ") :");
    lQuery.append(sqlStatement);
    getSqlLogger().info(lQuery.toString());
  }

  private void logSQLStatementTimeForSelect(String spid, Connection connection, String sqlStatement, long time, String appName, String objName)
    throws MXException, SQLException, RemoteException
  {
    StringBuffer lQuery = new StringBuffer("");
    if ((needToLogSQLOnTimeLimit()) && (time > getSQLTimeLimit()))
    {
      StringBuffer execBuffer = new StringBuffer();
      execBuffer.append("USER = (" + this.user.getLoginUserName() + ") SPID = (" + spid + ") ");
      execBuffer.append(" app (" + appName + ") object (" + objName + ") :");
      execBuffer.append(sqlStatement);
      execBuffer.append(" (execution took ");
      execBuffer.append(time);
      execBuffer.append(" milliseconds)");

      if ((needToLogSQLPlan()) && (sqlTableScanExclude().isEmpty()))
      {
        StringBuffer strBuf = logSQLPlan(connection, sqlStatement);
        if (strBuf != null)
          execBuffer.append(strBuf);
      }
      String execTimeMessage = execBuffer.toString();

      MAXIMOLOGGER.warn(execTimeMessage);
    }
    else if (getSqlLogger().isInfoEnabled())
    {
      lQuery.append("USER = (" + this.user.getLoginUserName() + ") SPID = (" + spid + ") ");
      lQuery.append(" app (" + appName + ") object (" + objName + ") :");
      lQuery.append(sqlStatement);
      getSqlLogger().info(lQuery.toString());
    }

    if ((needToLogSQLPlan()) && (!(sqlTableScanExclude().isEmpty())) && 
      (!(sqlTableScanExclude().contains(objName))))
    {
      StringBuffer strBuf = logSQLPlan(connection, sqlStatement);
      if (strBuf != null)
      {
        if (!(getSqlLogger().isInfoEnabled()))
        {
          lQuery.append(" app (" + appName + ") object (" + objName + ") :");
          lQuery.append(sqlStatement);
          lQuery.append(strBuf);
          MAXIMOLOGGER.info(lQuery);
        }
        else {
          MAXIMOLOGGER.info(strBuf);
        }

      }

    }

    incrementSQLStats(sqlStatement, time);
  }

  private void logBindValue(String bindColumnName, Object bindValue)
  {
    if (!(getSqlLogger().isInfoEnabled()))
      return;
    StringBuffer bindValueLogBuffer = new StringBuffer();

    bindValueLogBuffer.append("bind value for ");
    bindValueLogBuffer.append(bindColumnName);
    bindValueLogBuffer.append(" = ");
    if (bindValue == null)
    {
      bindValueLogBuffer.append("null");
    }
    else
    {
      bindValueLogBuffer.append(bindValue);
    }

    String bindValueMessage = bindValueLogBuffer.toString();
    getSqlLogger().info(bindValueMessage);
  }

  public void logRowUpdatedException(MXRowUpdateException mxr)
  {
    if (!(getSqlLogger().isErrorEnabled()))
      return;
    getSqlLogger().error(mxr.getMessage(), mxr);
  }

  String getQbeWhere()
    throws MXException, RemoteException
  {
    return getQbeWhere("");
  }

  String getQbeWhere(String alias)
    throws MXException, RemoteException
  {
    String where = this.qbe.getWhere(alias);
    String subSelect = ((Mbo)getZombie()).getRelatedWhere(alias);
    String op = this.qbe.getOperator();

    if ((subSelect.length() > 0) && (where.trim().length() > 0))
      where = where + " " + op + " (" + subSelect + ")";
    else {
      where = where + " " + subSelect;
    }

    if (this.selectionWhereClause.length() > 0)
    {
      if (where.trim().length() > 0)
      {
        where = where + " and " + this.selectionWhereClause;
      }
      else
      {
        where = this.selectionWhereClause;
      }
    }

    return where.trim();
  }

  String buildWhere() throws MXException, RemoteException
  {
    return buildWhere("");
  }

  String buildWhere(String alias)
    throws MXException, RemoteException
  {
    CombineWhereClauses cwc = new CombineWhereClauses(new String(getWhere()));

    cwc.addWhere(getUserWhere(alias));

    cwc.addWhere(getQbeWhere(alias));

    cwc.addWhere(getRelationship());

    cwc.addWhere(appendToWhere());

    cwc.addWhere(getUserPrefWhere());

    cwc.addWhere(getMultiSiteWhere());

    cwc.addWhere(getRoleRestrictionWhere());

    cwc.addWhere(getMaxAppsWhere());

    cwc.addWhere(getAppWhere());

    return cwc.getWhereClause();
  }

  public String getMaxAppsWhere()
    throws MXException, RemoteException
  {
    String whereClause = null;

    if ((getApp() == null) || (getApp().trim().equals(""))) {
      return null;
    }

    if (this.ownerMbo == null)
    {
      SignatureServiceRemote sr = (SignatureServiceRemote)MXServer.getMXServer().lookup("SIGNATURE");
      SignatureCache sc = sr.getSigCache();
      HashMap appMap = sc.getMaxAppsCache();
      MboRemote maxappsMbo = null;
      maxappsMbo = (MboRemote)appMap.get(getApp().toUpperCase());
      if (maxappsMbo != null) {
        whereClause = maxappsMbo.getString("restrictions");
      }
    }

    return whereClause;
  }

  public String getRoleRestrictionWhere()
    throws MXException, RemoteException
  {
    String whereClause = null;

    boolean isHierarchy = false;
    if ((this instanceof HierarchicalMboSet) && (this.ownerMbo instanceof DrillDown)) {
      isHierarchy = true;
    }
    String systemUserIdentity = MXServer.getMXServer().getConfig().getProperty("mxe.adminuserid").toUpperCase();
    if (getUserInfo().getUserName().equalsIgnoreCase(systemUserIdentity)) {
      return null;
    }

    if ((this.ownerMbo == null) || (isTableDomainLookup()) || (isHierarchy)) {
      whereClause = getProfile().getRoleRestrictions(getMboSetInfo().getEntityName());
    }
    return whereClause;
  }

  public void setTableDomainLookup(boolean flag)
  {
    this.tableDomainLookup = flag;
  }

  public boolean isTableDomainLookup()
  {
    return this.tableDomainLookup;
  }

  public String getCompleteWhere()
    throws MXException, RemoteException
  {
    return buildWhere();
  }

  public void setQbe(String attribute, String expression)
    throws MXException, RemoteException
  {
    if ((expression != null) && (expression.trim().length() > 0))
    {
      MboValueInfo mvi = getMboSetInfo().getMboValueInfo(getRelAttrName(attribute).toUpperCase());
      if (mvi != null)
      {
        String tmpExpr = new String(expression);
        if ((((mvi.getTypeAsInt() == 3) || (mvi.getTypeAsInt() == 4))) && 
          (expression.indexOf("TODAY") == -1) && (expression.indexOf("today") == -1) && (expression.indexOf("YTD") == -1) && (expression.indexOf("ytd") == -1) && (expression.indexOf("~NULL~") == -1) && (expression.indexOf("!=~NULL~") == -1))
        {
          String[] OPRLIST = { "<=", "<", ">=", ">", "!=", "=" };

          for (int i = 0; i < OPRLIST.length; ++i)
          {
            if (!(expression.startsWith(OPRLIST[i])))
              continue;
            int index = expression.indexOf(OPRLIST[i]);
            tmpExpr = expression.substring(index + OPRLIST[i].length());
            break;
          }

          if (mvi.getTypeAsInt() == 3)
            MXFormat.stringToDate(tmpExpr, getClientLocale());
          else if (mvi.getTypeAsInt() == 4) {
            MXFormat.stringToDateTime(tmpExpr, getClientLocale(), getClientTimeZone());
          }
        }
      }
    }
    MboSetRemote msr = getMboSetForAttribute(getRelAttrName(attribute));
    if (msr == this)
    {
      this.qbe.setQbe(getFullAttrName(attribute), expression);
    }
    else
    {
      msr.setQbeCaseSensitive(isQbeCaseSensitive());
      msr.setQbeExactMatch(isQbeExactMatch());
      msr.ignoreQbeExactMatchSet(isIgnoreQbeExactMatchSet());
      msr.setQbe(getFullAttrName(attribute), expression);
    }
  }

  public void setWhereQbe(String attribute, String value, String where)
    throws MXException, RemoteException
  {
    MboSetRemote msr = getMboSetForAttribute(getRelAttrName(attribute));
    if (msr == this)
    {
      this.qbe.setWhereQbe(getFullAttrName(attribute), value, where);
    }
    else
    {
      msr.setQbeCaseSensitive(isQbeCaseSensitive());
      msr.setQbeExactMatch(isQbeExactMatch());
      msr.ignoreQbeExactMatchSet(isIgnoreQbeExactMatchSet());
      msr.setWhereQbe(getFullAttrName(attribute), value, where);
    }
  }

  public String getQbe(String attribute)
    throws MXException, RemoteException
  {
    MboSetRemote msr = getMboSetForAttribute(getRelAttrName(attribute));
    if (msr == this) {
      return this.qbe.getQbe(getFullAttrName(attribute));
    }
    return msr.getQbe(getFullAttrName(attribute));
  }

  MboSetRemote getMboSetForAttribute(String attributeName)
    throws MXException, RemoteException
  {
    int index = attributeName.indexOf(46);
    MboSetRemote ms = this;
    MboSetRemote mboSet = this;
    while (index != -1)
    {
      ms = mboSet.getZombie().getMboSet(attributeName.substring(0, index).toUpperCase());
      attributeName = attributeName.substring(index + 1);
      index = attributeName.indexOf(46);
      mboSet = ms;
    }
    return ms;
  }

  String getAttributeName(String attributeName)
  {
    int index = attributeName.indexOf(46);
    if (index != -1) {
      return attributeName.substring(index + 1);
    }
    return attributeName;
  }

  public void cleanup()
    throws MXException, RemoteException
  {
    resetQbe();
    reset();
  }

  public void startCheckpoint()
    throws MXException, RemoteException
  {
    getMbo().startCheckpoint();
  }

  public void rollbackToCheckpoint()
    throws MXException, RemoteException
  {
    getMbo().rollbackToCheckpoint();
  }

  public void startCheckpoint(int i)
    throws MXException, RemoteException
  {
    getMbo(i).startCheckpoint();
  }

  public void rollbackToCheckpoint(int i)
    throws MXException, RemoteException
  {
    getMbo(i).rollbackToCheckpoint();
  }

  public void select(int index)
    throws MXException, RemoteException
  {
    MboRemote mbo = getMbo(index);
    if (mbo == null)
      return;
    mbo.select();
  }

  public void select(int startIndex, int count)
    throws MXException, RemoteException
  {
    if (startIndex < 0)
    {
      return;
    }

    if (count <= 0)
    {
      return;
    }

    for (int i = startIndex; i < startIndex + count; ++i)
    {
      MboRemote mbo = getMbo(i);
      if (mbo == null)
      {
        return;
      }

      mbo.select();
    }
  }

  public void select(Vector mboIndices)
    throws MXException, RemoteException
  {
    if (mboIndices == null)
    {
      return;
    }

    for (int i = 0; i < mboIndices.size(); ++i)
    {
      Object obj = mboIndices.elementAt(i);
      if (!(obj instanceof Integer)) {
        continue;
      }

      int mboIndex = ((Integer)obj).intValue();

      MboRemote mbo = getMbo(mboIndex);
      if (mbo == null)
        continue;
      mbo.select();
    }
  }

  public Vector getSelection()
    throws MXException, RemoteException
  {
    Vector selection = new Vector();

    for (int i = 0; i < this.mboVec.size(); ++i)
    {
      MboRemote mboRemote = (MboRemote)this.mboVec.elementAt(i);

      if (mboRemote.isSelected()) {
        selection.addElement(mboRemote);
      }
    }
    return selection;
  }

  public void unselect(int index)
    throws MXException, RemoteException
  {
    MboRemote mbo = getMbo(index);
    if (mbo == null)
      return;
    mbo.unselect();
  }

  public void unselect(int startIndex, int count)
    throws MXException, RemoteException
  {
    if (startIndex < 0)
    {
      return;
    }

    if (count <= 0)
    {
      return;
    }

    for (int i = startIndex; i < startIndex + count; ++i)
    {
      MboRemote mbo = getMbo(i);
      if (mbo == null)
      {
        return;
      }

      mbo.unselect();
    }
  }

  public void unselect(Vector mboIndices)
    throws MXException, RemoteException
  {
    if (mboIndices == null) {
      return;
    }
    for (int i = 0; i < mboIndices.size(); ++i)
    {
      Object obj = mboIndices.elementAt(i);
      if (!(obj instanceof Integer)) {
        continue;
      }

      int mboIndex = ((Integer)obj).intValue();

      MboRemote mbo = getMbo(mboIndex);
      if (mbo == null)
        continue;
      mbo.unselect();
    }
  }

  public void selectAll()
    throws MXException, RemoteException
  {
    MboRemote mbo = null;
    int i = 0;

    for (i = 0; i < getSize(); ++i)
    {
      mbo = getMbo(i);
      if (mbo != null)
        mbo.select();
    }
  }

  public void unselectAll()
    throws MXException, RemoteException
  {
    MboRemote mbo = null;
    int size = getSize();

    for (int i = 0; i < size; ++i)
    {
      mbo = getMbo(i);
      if (mbo != null)
        mbo.unselect();
    }
  }

  public void resetWithSelection()
    throws MXException, RemoteException
  {
    StringBuffer selectionWhereBuffer = new StringBuffer();

    Vector selectedMbos = new Vector();
    String keyClause = null;
    MboRemote mboRemote;
    for (int i = 0; i < this.mboVec.size(); ++i)
    {
      mboRemote = (MboRemote)this.mboVec.elementAt(i);

      if (!(mboRemote.isSelected()))
        continue;
      if (keyClause == null)
      {
        keyClause = getKeyClause();
      }

      if (selectedMbos.size() > 0)
      {
        selectionWhereBuffer.append(" or ");
      }

      selectionWhereBuffer.append(new SqlFormat(mboRemote, keyClause).format());
      selectedMbos.add(mboRemote);
    }

    reset();
    resetQbe();
    this.userWhereClause = "";

    this.selectionWhereClause = selectionWhereBuffer.toString();

    for (int i = 0; i < selectedMbos.size(); ++i)
    {
      mboRemote = (MboRemote)selectedMbos.elementAt(i);
      mboRemote.unselect();
    }

    this.mboVec = selectedMbos;
    close();
  }

  public String getSelectionWhere()
    throws MXException, RemoteException
  {
    StringBuffer selectionWhereBuffer = new StringBuffer();

    String keyClause = null;
    int selectedMboCount = 0;

    for (int i = 0; i < this.mboVec.size(); ++i)
    {
      MboRemote mboRemote = (MboRemote)this.mboVec.elementAt(i);

      if (!(mboRemote.isSelected()))
        continue;
      if (keyClause == null)
      {
        keyClause = getKeyClause();
      }

      if (selectedMboCount > 0)
      {
        selectionWhereBuffer.append(" or ");
      }

      selectionWhereBuffer.append(new SqlFormat(mboRemote, keyClause).format());
      ++selectedMboCount;
    }

    return selectionWhereBuffer.toString();
  }

  private String getKeyClause()
  {
    String[] keyAtrribs = getMboSetInfo().getKeyAttributes();

    StringBuffer keyClauseBuffer = new StringBuffer();
    for (int i = 0; i < keyAtrribs.length; ++i)
    {
      String key = keyAtrribs[i];

      if (i > 0)
      {
        keyClauseBuffer.append(" and ");
      }

      keyClauseBuffer.append(key + "=:" + key);
    }

    String keyClause = "";
    if (keyClauseBuffer.length() > 0)
    {
      keyClause = "(" + keyClauseBuffer.toString() + ")";
    }

    return keyClause;
  }

  long getSQLTimeLimit()
  {
    readSQLTimeLimit();
    return sqlTimeLimit;
  }

  boolean needToLogSQLOnTimeLimit()
  {
    readSQLTimeLimit();

    return (sqlTimeLimit > 0L);
  }

  void readSQLTimeLimit()
  {
    if (readSQLTimeLimit) {
      return;
    }
    try
    {
      String timeLimit = MXServer.getMXServer().getConfig().getProperty("mxe.db.logSQLTimeLimit", "1000");
      if ((timeLimit == null) || (timeLimit.trim().length() == 0))
      {
        sqlTimeLimit = 0L;
      }
      else
      {
        timeLimit = timeLimit.trim();
        sqlTimeLimit = new Long(timeLimit).longValue();
      }
    }
    catch (RemoteException ex)
    {
      ex.printStackTrace();
      sqlTimeLimit = 0L;
    }

    readSQLTimeLimit = true;
  }

  void optionFastUse()
  {
    if (this.readOptionFastUse) {
      return;
    }

    try
    {
      String dbProductName = mboSetDBProductName;
      if (!(dbProductName.equalsIgnoreCase("Microsoft SQL Server")))
      {
        this.canUseFast = false;
        return;
      }
      String optionUse = MXServer.getMXServer().getConfig().getProperty("mxe.db.optionuse", "Y");
      if (optionUse.equalsIgnoreCase("Y"))
      {
        String optionNumStr = MXServer.getMXServer().getConfig().getProperty("mxe.db.optionnum", "1000");
        this.optionNum = new Integer(optionNumStr).intValue();
        if (this.optionNum > 0)
        {
          this.canUseFast = true;
        }
      }
    }
    catch (Exception e)
    {
    }

    this.readOptionFastUse = true;
  }

  long getFetchResultLogLimit()
  {
    readSQLTimeLimit();
    return fetchResultLogLimit;
  }

  void readFetchResultLogLimit()
  {
    if (readFetchResultLogLimit) {
      return;
    }
    try
    {
      String logResult = MXServer.getMXServer().getConfig().getProperty("mxe.db.fetchResultLogLimit", "200");
      if ((logResult == null) || (logResult.trim().length() == 0))
      {
        fetchResultLogLimit = 0L;
      }
      else
      {
        logResult = logResult.trim();
        fetchResultLogLimit = new Long(logResult).longValue();
      }
    }
    catch (RemoteException ex)
    {
      ex.printStackTrace();
      fetchResultLogLimit = 0L;
    }

    readFetchResultLogLimit = true;
  }

  boolean needToLogLargeFetchResult()
  {
    if (this.logLargFetchResultDisabled)
      return false;
    readFetchResultLogLimit();

    return (fetchResultLogLimit > 0L);
  }

  public String getMultiSiteWhere()
    throws MXException, RemoteException
  {
    int siteOrgType = getMboSetInfo().getSiteOrgType();
    boolean forLookup = false;

    if (this.ownerMbo != null)
    {
      if (((this instanceof HierarchicalMboSet) && (this.ownerMbo instanceof DrillDown)) || (isTableDomainLookup()))
        forLookup = true;
      else {
        return null;
      }
    }
    if ((getApp() == null) || (getApp().trim().equals(""))) {
      forLookup = true;
    }
    switch (siteOrgType)
    {
    case 0:
      return null;
    case 1:
    case 2:
    case 5:
    case 9:
    case 10:
    case 11:
      return SiteOrgRestriction.getSiteOrgWhere(siteOrgType, getProfile(), forLookup, getApp(), getUserInfo(), getMboSetInfo().getEntityName().toLowerCase());
    case 3:
      return SiteOrgRestriction.getItemSetWhere(getProfile(), forLookup, getApp(), getUserInfo());
    case 4:
      return SiteOrgRestriction.getCompanySetWhere(getProfile(), forLookup, getApp(), getUserInfo());
    case 6:
    case 7:
    case 8:
      return null;
    }

    return null;
  }

  public void setAppWhere(String appWhere)
    throws MXException, RemoteException
  {
    this.applicationClause = appWhere;
  }

  public String getAppWhere()
    throws MXException, RemoteException
  {
    return this.applicationClause;
  }

  public void setDefaultValue(String attribute, String value)
    throws MXException, RemoteException
  {
    MboSetRemote msr = getMboSetForAttribute(attribute);
    if (msr == this)
    {
      if (value.equalsIgnoreCase("~NULL~"))
        value = null;
      getDefaultValueHash().put(attribute.toUpperCase(), value);
    }
    else
    {
      msr.setDefaultValue(attribute, value);
    }
  }

  public String getDefaultValue(String attribute)
    throws MXException, RemoteException
  {
    MboSetRemote msr = getMboSetForAttribute(attribute);
    if (msr == this)
    {
      String value = (String)getDefaultValueHash().get(attribute.toUpperCase());

      return value;
    }

    return msr.getDefaultValue(attribute);
  }

  public HashMap getDefaultValueHash()
  {
    if (this.defaultValue == null) {
      this.defaultValue = new HashMap();
    }
    return this.defaultValue;
  }

  public void setDefaultValues(String[] attributes, String[] values)
    throws MXException, RemoteException
  {
    for (int i = 0; i < attributes.length; ++i)
    {
      MboSetRemote msr = getMboSetForAttribute(attributes[i]);
      if (msr == this)
        setDefaultValues(attributes[i], values[i]);
      else
        ((MboSet)msr).setDefaultValues(attributes[i], values[i]);
    }
  }

  public void setDefaultValues(String attribute, String value)
  {
    if (value.equalsIgnoreCase("~NULL~"))
      value = null;
    getJspDefaultValueHash().put(attribute, value);
  }

  public HashMap getJspDefaultValueHash()
  {
    if (this.jspDefaultValue == null) {
      this.jspDefaultValue = new HashMap();
    }
    return this.jspDefaultValue;
  }

  protected boolean includeRelatedMbosOfOwnersChildren()
  {
    return false;
  }

  public boolean isESigNeeded(String optionName)
    throws MXException, RemoteException
  {
    String app = getApp();

    if ((app == null) || (app.trim().equals("")))
    {
      MboRemote owner = getOwner();
      while (owner != null)
      {
        app = owner.getThisMboSet().getApp();
        if ((app != null) && (!(app.trim().equals("")))) break;
        owner = owner.getOwner();
      }

    }

    if ((app == null) || (app.equals("")))
    {
      return false;
    }

    boolean eSigEnabled = false;
    boolean eSigNeeded = false;

    eSigNeeded = eSigEnabled = getMboServer().getMaximoDD().isESigEnabled(app, optionName);
    if ((eSigEnabled) && 
      (optionName.equalsIgnoreCase("save")))
    {
      eSigNeeded = isESigNeededForSave();
    }

    return eSigNeeded;
  }

  private boolean isESigNeededForSave()
    throws MXException, RemoteException
  {
    Enumeration enum = this.mboVec.elements();
    boolean eSigForInsert = false;
    boolean eSigForDuplicate = false;
    while (enum.hasMoreElements())
    {
      MboRemote mbo = (MboRemote)enum.nextElement();
      if (mbo.toBeAdded())
      {
        eSigForInsert = getMboServer().getMaximoDD().isESigEnabled(this.app, "insert");
        eSigForDuplicate = getMboServer().getMaximoDD().isESigEnabled(this.app, "duplicate");
        if ((eSigForInsert) || (eSigForDuplicate)) {
          return true;
        }
      }

    }

    if (!(isESigFieldModified()))
    {
      return false;
    }

    String eSigFilter = getMboSetInfo().getESigFilter();
    if ((eSigFilter == null) || (eSigFilter.equals("")))
    {
      return isESigNeeded();
    }

    return isESigNeededAfterFilter();
  }

  protected boolean isESigNeeded()
  {
    return true;
  }

  private boolean isESigNeededAfterFilter()
    throws MXException, RemoteException
  {
    boolean eSigNeeded = false;
    String eSigFilter = getMboSetInfo().getESigFilter();
    if ((eSigFilter == null) || (eSigFilter.trim().equals("")))
    {
      eSigNeeded = isESigNeeded();
    }
    else
    {
      String filterSQL = getFilterSQL(eSigFilter);

      Enumeration enum = this.mboVec.elements();
      while (enum.hasMoreElements())
      {
        MboRemote mbo = (MboRemote)enum.nextElement();
        if (!(((Mbo)mbo).isESigFieldModified()))
        {
          continue;
        }

        eSigNeeded = evaluateFilter("ESIG", filterSQL, (Mbo)mbo);
        if (eSigNeeded) {
          break;
        }
      }
    }
    return eSigNeeded;
  }

  public boolean isESigFieldModified()
  {
    return this.eSigFieldModified;
  }

  public void setESigFieldModified(boolean eSigFieldModified)
    throws RemoteException
  {
    if (this.eSigFieldModified == eSigFieldModified)
    {
      return;
    }

    this.eSigFieldModified = eSigFieldModified;

    if (this.ownerMbo == null)
      return;
    this.ownerMbo.setESigFieldModified(eSigFieldModified);
  }

  public boolean isEAuditFieldModified()
  {
    return this.eAuditFieldModified;
  }

  public void setEAuditFieldModified(boolean eAuditFieldModified)
    throws RemoteException
  {
    if (this.eAuditFieldModified == eAuditFieldModified)
    {
      return;
    }

    this.eAuditFieldModified = eAuditFieldModified;
  }

  private void eAuditMbo(Connection con, Mbo mbo, String auditType)
    throws MXException, RemoteException, SQLException
  {
    if (getName().equals("LONGDESCRIPTION"))
    {
      MboRemote owner = getOwner();
      if (owner == null)
        return;
      MboSetInfo ownermsi = owner.getThisMboSet().getMboSetInfo();
      if (!(ownermsi.isEAuditEnabled()))
        return;
      MboValueInfo info = ownermsi.getMboValueInfo(mbo.getString("ldownercol") + "_LONGDESCRIPTION");
      if ((info == null) || (!(info.isEAuditEnabled()))) {
        return;
      }
    }
    MboSetInfo msi = getMboSetInfo();
    if (!(msi.isEAuditEnabled()))
    {
      return;
    }

    if (!(isEAuditNeededAfterFilter(mbo)))
    {
      return;
    }

    if ((auditType.equalsIgnoreCase("U")) && 
      (!(mbo.isEAuditFieldModified())))
    {
      return;
    }

    MboSetInfo mboSetInfo = mbo.getMboSetInfo();
    Entity entity = mboSetInfo.getEntity();

    HashMap attributes = new HashMap();
    String spid = getSPID(con);

    Iterator tableIterator = entity.getTablesInHierarchyOrder();
    while (tableIterator.hasNext())
    {
      String tableName = (String)tableIterator.next();

      String langCodeColumn = MXServer.getMXServer().getMaximoDD().getLangCodeColumn(tableName.toUpperCase());
      boolean isLangCodeExist = false;
      if ((langCodeColumn != null) && (langCodeColumn.trim().length() > 0)) {
        isLangCodeExist = true;
      }

      attributes.clear();
      Iterator eAuditAttributeIterator = mboSetInfo.getEAuditAttributes();
      while (eAuditAttributeIterator.hasNext())
      {
        String attributeName = (String)eAuditAttributeIterator.next();
        MboValue mboValue = mbo.getMboValue(attributeName);
        MboValueInfo mboValueInfo = mboValue.getMboValueInfo();

        String entityColumnName = mboValueInfo.getEntityColumnName();
        String columnName = entity.getColumnName(entityColumnName, tableName);

        if (columnName != null)
        {
          if ((langCodeColumn != null) && (columnName.equalsIgnoreCase(langCodeColumn)))
            isLangCodeExist = false;
          attributes.put(attributeName, columnName);
        }

      }

      String tableUniqueId = getMboServer().getMaximoDD().getUniqueIdColumn(tableName);
      String entityUniqueIdName = entity.getEntityColumnName(tableUniqueId, tableName);
      attributes.put(entityUniqueIdName, tableUniqueId);

      String auditTable = entity.getAuditTable(tableName);

      StringBuffer insertStmt = new StringBuffer("insert into ");
      insertStmt.append(auditTable.toLowerCase());
      insertStmt.append(" ( ");

      insertStmt.append("eauditusername");
      insertStmt.append(",eaudittimestamp");
      insertStmt.append(",eaudittype");
      insertStmt.append(",eaudittransid");
      if (isLangCodeExist)
      {
        insertStmt.append(",esigtransid");
        insertStmt.append("," + langCodeColumn.toLowerCase() + ",");
      }
      else
      {
        insertStmt.append(",esigtransid,");
      }
      StringBuffer valuesStmt = new StringBuffer();

      if (isLangCodeExist)
        valuesStmt.append("?,?,?,?,?,?,");
      else {
        valuesStmt.append("?,?,?,?,?,");
      }

      Iterator attributeNameIterator = attributes.keySet().iterator();
      while (attributeNameIterator.hasNext())
      {
        String attributeName = (String)attributeNameIterator.next();
        String columnName = (String)attributes.get(attributeName);

        insertStmt.append(columnName.toLowerCase());
        valuesStmt.append("?");
        if (attributeNameIterator.hasNext())
        {
          insertStmt.append(",");
          valuesStmt.append(",");
        }
      }

      insertStmt.append(") values (");
      insertStmt.append(valuesStmt);
      insertStmt.append(")");

      logSQLStatement(spid, insertStmt.toString(), getApp(), getName());

      PreparedStatement pstmt = null;
      try
      {
        pstmt = con.prepareStatement(insertStmt.toString());

        int bindIndex = 0;

        ++bindIndex;
        String userName = getUserInfo().getUserName();
        setBindValueAsString(pstmt, bindIndex, userName, "EAUDITUSERNAME");

        ++bindIndex;
        long timeValue = MXServer.getMXServer().getDate().getTime();
        Timestamp auditTS = new Timestamp(timeValue);
        setBindValueAsTimeStamp(pstmt, bindIndex, auditTS, "EAUDITTIMESTAMP");

        ++bindIndex;
        setBindValueAsString(pstmt, bindIndex, auditType, "EAUDITTYPE");

        ++bindIndex;
        String eauditTransId = getEAuditTransactionId(con);
        setBindValueAsString(pstmt, bindIndex, eauditTransId, "EAUDITTRANSID");

        ++bindIndex;
        String esigTransId = getESigTransactionId();
        setBindValueAsString(pstmt, bindIndex, esigTransId, "ESIGTRANSID");

        if (isLangCodeExist)
        {
          ++bindIndex;
          boolean ml = false;

          ml = mbo.getThisMboSet().processML();
          String langCodeValue = null;
          if (ml)
            langCodeValue = mbo.getUserInfo().getLangCode();
          else {
            langCodeValue = mbo.getString(langCodeColumn.toLowerCase());
          }
          setBindValueAsString(pstmt, bindIndex, langCodeValue, langCodeColumn.toUpperCase());
        }

        attributeNameIterator = attributes.keySet().iterator();
        while (attributeNameIterator.hasNext())
        {
          String attributeName = (String)attributeNameIterator.next();
          MboValue mboValue = mbo.getMboValue(attributeName);
          ++bindIndex;

          String columnName = (String)attributes.get(attributeName);
          setBindValue(pstmt, bindIndex, mboValue, columnName);
        }

        long startTime = System.currentTimeMillis();

        pstmt.executeUpdate();

        long execTime = System.currentTimeMillis() - startTime;
        logSQLStatementTime(spid, insertStmt.toString(), execTime, getApp(), getName(), con);
      }
      finally
      {
        try
        {
          if (pstmt != null)
          {
            pstmt.close();
          }
        }
        catch (Exception ex) {
        }
      }
    }
  }

  public void handleMLMbo(Mbo mbo, MXTransaction txn, String action) throws MXException, RemoteException {
    if ((!(getMboServer().getMaxVar().getBoolean("MLSUPPORTED", (String)null))) || 
      (!(getMboSetInfo().isMLInUse())))
      return;
    HashMap langTableNames = mbo.getMboSetInfo().getLangTableNames();

    if (action.equals("D"))
    {
      Iterator names = langTableNames.keySet().iterator();
      while (names.hasNext())
      {
        String baseTableName = (String)names.next();
        String langTableName = (String)langTableNames.get(baseTableName);

        MboSetRemote toBeDeletedMLs = mbo.getMboSet(langTableName);
        toBeDeletedMLs.deleteAll();
      }
    } else {
      if ((!(action.equals("I"))) && (!(action.equals("U"))))
        return;
      String langCode = getUserInfo().getLangCode();

      if (langCode.equals(getMboServer().getMaxVar().getString("BASELANGUAGE", "")))
      {
        return;
      }

      Iterator names = langTableNames.keySet().iterator();
      while (names.hasNext())
      {
        String baseTbName = (String)names.next();
        String langTbName = (String)langTableNames.get(baseTbName);
        String langCodeColumn = MXServer.getMXServer().getMaximoDD().getLangCodeColumn(langTbName);
        MboSetRemote langMboSet = mbo.getMboSet(langTbName);

        MboRemote langMbo = null;
        for (int i = 0; (langMbo = langMboSet.getMbo(i)) != null; ++i)
        {
          if (langMbo.getString(langCodeColumn).equals(mbo.getUserInfo().getLangCode())) {
            break;
          }

        }

        if (langMbo == null)
        {
          langMbo = langMboSet.add();
          langMbo.setValue(langCodeColumn, mbo.getUserInfo().getLangCode());
        }

        ((LanguageMboRemote)langMbo).populate(mbo, baseTbName, langTbName);
      }
    }
  }

  private boolean isEAuditNeededAfterFilter(Mbo mbo)
    throws MXException, RemoteException
  {
    boolean eAuditNeeded = false;
    String eAuditFilter = getMboSetInfo().getEAuditFilter();
    if ((eAuditFilter == null) || (eAuditFilter.equals("")))
    {
      eAuditNeeded = isEAuditNeeded(mbo);
    }
    else
    {
      String filterSQL = getFilterSQL(eAuditFilter.trim());
      eAuditNeeded = evaluateFilter("EAUDIT", filterSQL, mbo);
    }

    return eAuditNeeded;
  }

  protected boolean isEAuditNeeded(Mbo mbo)
  {
    return true;
  }

  private String getFilterSQL(String filter)
  {
    String entityName = getMboSetInfo().getEntityName().toLowerCase();

    StringBuffer filterSQL = new StringBuffer();
    filterSQL.append("select * from ");
    filterSQL.append(entityName);
    filterSQL.append(" ");

    if ((!(filter.toLowerCase().startsWith("where "))) && (!(filter.toLowerCase().startsWith("where("))))
    {
      filterSQL.append("where ");
    }

    filterSQL.append(filter);

    return filterSQL.toString();
  }

  private boolean evaluateFilter(String purpose, String filterSQL, Mbo mbo)
    throws MXException, RemoteException
  {
    boolean evalResult = false;

    Connection con = getMboServer().getDBConnection(getUserInfo().getConnectionKey());
    Statement stmt = null;
    ResultSet resultset = null;
    try
    {
      String filterQuery = new SqlFormat(mbo, filterSQL).format();

      if (getSqlLogger().isInfoEnabled())
      {
        StringBuffer queryBuffer = new StringBuffer();
        queryBuffer.append(" app (" + getApp() + ") object (" + getName() + ") :");
        queryBuffer.append(purpose);
        queryBuffer.append(" filter: ");
        queryBuffer.append(filterQuery);

        getSqlLogger().info(queryBuffer.toString());
      }

      stmt = con.createStatement();
      resultset = stmt.executeQuery(filterQuery);

      if (resultset.next())
      {
        evalResult = true;
      }

      resultset.close();
      stmt.close();
    }
    catch (SQLException e)
    {
      Object[] params = { new Integer(e.getErrorCode()).toString() };

      if (getSqlLogger().isErrorEnabled())
      {
        getSqlLogger().error("MboSet evaluateFilter() failed with SQL error code : " + ((String)params[0]), e);
      }

      throw new MXSystemException("system", "sql", params, e);
    }
    finally
    {
      try {
        if (resultset != null)
          resultset.close();
      } catch (Exception ex1) {
      }
      try {
        if (stmt != null)
          stmt.close();
      } catch (Exception ex1) {
      }
      getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
    }

    return evalResult;
  }

  private String getEAuditTransactionId(Connection con)
    throws MXException
  {
    if (eAuditTransId.get() == null)
    {
      long transId = MaxSequence.generateKey(con, "EAUDIT", "TRANSID");
      eAuditTransId.set(transId + "");
    }

    return ((String)eAuditTransId.get());
  }

  private void clearEAuditTransactionId()
  {
    eAuditTransId.set(null);
  }

  public String getESigTransactionId()
    throws MXException, RemoteException
  {
    if ((eSigTransId.get() == null) && 
      (this.lastESigTransId != null))
    {
      eSigTransId.set(this.lastESigTransId);
    }

    return ((String)eSigTransId.get());
  }

  private void clearESigTransactionId()
  {
    eSigTransId.set(null);
    this.lastESigTransId = null;
  }

  private String generateESigTransactionId(Connection con)
    throws MXException
  {
    long transId = MaxSequence.generateKey(con, "LOGINTRACKING", "TRANSID");
    this.lastESigTransId = "" + transId;
    return this.lastESigTransId;
  }

  public void setLastESigTransId(String id)
    throws MXException, RemoteException
  {
    this.lastESigTransId = id;
  }

  public boolean verifyESig(String loginid, String password, String reason)
    throws MXException, RemoteException
  {
    boolean authenticatedSuccessfully = false;
    String userid = getUserInfo().getLoginUserName();

    SignatureServiceRemote sr = (SignatureServiceRemote)MXServer.getMXServer().lookup("SIGNATURE");

    if (sr.isBlocked(userid, getUserInfo()))
    {
      logESigVerification(userid, reason, authenticatedSuccessfully);

      throw new MXApplicationException("signature", "blockedUser");
    }

    SecurityServiceRemote sec = (SecurityServiceRemote)MXServer.getMXServer().lookup("SECURITY");
    authenticatedSuccessfully = sec.isUser(getUserInfo(), loginid, password);

    logESigVerification(userid, reason, authenticatedSuccessfully);

    return authenticatedSuccessfully;
  }

  public void logESigVerification(String username, String reason, boolean authenticatedSuccessfully)
    throws MXException, RemoteException
  {
    Connection con = getMboServer().getDBConnection(getUserInfo().getConnectionKey());
    try
    {
      String transId = generateESigTransactionId(con);

      String[] keyInfo = getMboSetInfo().getKeyAttributes();
      String[] keyValues = new String[keyInfo.length];
      String ownerTable = getName();
      String ownerId = null;

      if (this.currMbo != null)
      {
        for (int i = 0; i < keyInfo.length; ++i)
        {
          keyValues[i] = getString(keyInfo[i]);
        }
        ownerId = new Long(this.currMbo.getUniqueIDValue()).toString();
      }

      SignatureServiceRemote sr = (SignatureServiceRemote)MXServer.getMXServer().lookup("SIGNATURE");
      sr.addLoginTracking(username, authenticatedSuccessfully, getUserInfo(), getApp(), reason, transId, keyValues, ownerTable, ownerId);
    }
    finally
    {
      getMboServer().freeDBConnection(getUserInfo().getConnectionKey());
    }
  }

  public boolean isBasedOn(String objectName)
    throws RemoteException
  {
    boolean isBasedOn = getMboSetInfo().isBasedOn(objectName);
    return isBasedOn;
  }

  public String[] getKeyAttributes()
    throws RemoteException
  {
    return getMboSetInfo().getKeyAttributes();
  }

  public MXLogger getMboLogger()
  {
    return getMboSetInfo().getMboLogger();
  }

  public MXLogger getSqlLogger()
  {
    return getMboSetInfo().getSqlLogger();
  }

  public boolean setAutoKeyFlag(boolean flag)
    throws RemoteException
  {
    boolean ret = this.setAutoKey;
    this.setAutoKey = flag;
    return ret;
  }

  public MboRemote findByIntegrationKey(String[] integrationKeys, String[] integrationKeyValues)
    throws MXException, RemoteException
  {
    if ((integrationKeys.length == 0) || (integrationKeyValues == null) || (integrationKeys.length != integrationKeyValues.length))
    {
      return null;
    }

    if (this.integrationKeyMbos == null)
    {
      try
      {
        this.integrationKeyMbos = new HashMap();

        int i = 0;
        while (true)
        {
          Mbo mbo = (Mbo)getMbo(i);
          if (mbo == null)
          {
            break;
          }

          String key = "";
          boolean firstTime = true;
          for (int j = 0; j < integrationKeys.length; ++j)
          {
            Object obj = null;

            if (mbo.isNull(integrationKeys[j]))
            {
              continue;
            }

            if (mbo.toBeAdded())
            {
              obj = mbo.getMboValueData(integrationKeys[j]).getDataAsObject();
            }
            else
            {
              obj = mbo.getFetchValue(integrationKeys[j]);
              MboValueInfo mboValueInfo = getMboSetInfo().getMboValueInfo(integrationKeys[j]);
              int type = mboValueInfo.getTypeAsInt();

              switch (type)
              {
              case 8:
              case 9:
              case 10:
              case 11:
                if (obj instanceof BigDecimal)
                {
                  obj = "" + ((BigDecimal)obj).doubleValue();
                }

                break;
              case 3:
                obj = DateFormat.getDateInstance().format((java.util.Date)obj);
                break;
              case 4:
                obj = DateFormat.getDateTimeInstance().format((java.util.Date)obj);
                break;
              case 5:
                obj = DateFormat.getTimeInstance().format((java.util.Date)obj);
              case 6:
              case 7:
              }

            }

            if (obj == null)
            {
              continue;
            }

            if (firstTime)
            {
              key = obj + "";
              firstTime = false;
            }
            else
            {
              key = key + ESCAPECHAR + obj;
            }

          }

          this.integrationKeyMbos.put(key, mbo);
          ++i;
        }

      }
      catch (Throwable ex)
      {
        this.integrationKeyMbos = null;
        throw new MXApplicationException("system", "findByIntegrationKey", ex);
      }

    }

    if (this.integrationKeyMbos.size() == 0)
    {
      return null;
    }

    String key = "";
    boolean firstTime = true;
    for (int i = 0; i < integrationKeyValues.length; ++i)
    {
      if (integrationKeyValues[i] == null)
      {
        continue;
      }

      String keyValue = integrationKeyValues[i];
      MboValueInfo mboValueInfo = getMboSetInfo().getMboValueInfo(integrationKeys[i]);
      int type = mboValueInfo.getTypeAsInt();

      switch (type)
      {
      case 2:
        keyValue = keyValue.toLowerCase();
        break;
      case 1:
        keyValue = keyValue.toUpperCase();
        break;
      case 8:
      case 9:
      case 10:
      case 11:
        keyValue = new Double(keyValue).doubleValue() + "";
        break;
      case 6:
      case 7:
        keyValue = new Long(keyValue).longValue() + "";
      case 3:
      case 4:
      case 5:
      }

      if (firstTime)
      {
        key = keyValue;
        firstTime = false;
      }
      else
      {
        key = key + ESCAPECHAR + keyValue;
      }

    }

    MboRemote mboRemote = (MboRemote)this.integrationKeyMbos.get(key);

    return mboRemote;
  }

  public void setRelationName(String relationName)
    throws MXException, RemoteException
  {
    this.relationName = relationName;
  }

  public String getRelationName()
    throws MXException, RemoteException
  {
    return this.relationName;
  }

  public boolean hasMLQbe()
    throws MXException, RemoteException
  {
    if (!(processML()))
      return false;
    MboSetInfo msi = getMboSetInfo();
    Enumeration enum = this.qbe.getAllQbeKeys();
    while (enum.hasMoreElements())
    {
      String qbeKey = (String)enum.nextElement();
      if (msi.getAttribute(qbeKey).isMLInUse())
        return true;
    }
    return false;
  }

  public MboRemote getMboForUniqueId(long id)
    throws MXException, RemoteException
  {
    MboRemote mbo = null;

    for (int i = 0; i < this.mboVec.size(); ++i)
    {
      mbo = (MboRemote)this.mboVec.elementAt(i);
      if (mbo == null) {
        continue;
      }
      if (mbo.getUniqueIDValue() != id)
        continue;
      this.currIndex = i;
      this.currMbo = mbo;
      return mbo;
    }

    String uniqueIdName = getMboSetInfo().getUniqueIDName();
    setQbe(uniqueIdName, new Long(id).toString());
    reset();

    return moveNext();
  }

  public String getMessage(String errGrp, String errKey)
    throws RemoteException
  {
    try
    {
      Message msg = MXServer.getMXServer().getMaxMessageCache().getMessage(errGrp, errKey, getUserInfo());
      if (msg != null)
        return msg.getMessage();
    }
    catch (Exception e) {
    }
    return errGrp + "#" + errKey;
  }

  public String getMessage(String errGrp, String errKey, Object[] params)
    throws RemoteException
  {
    try
    {
      Message msg = MXServer.getMXServer().getMaxMessageCache().getMessage(errGrp, errKey, getUserInfo());
      if (msg != null)
        return msg.getMessage(params);
    }
    catch (Exception e) {
    }
    return errGrp + "#" + errKey;
  }

  public String getMessage(String errGrp, String errKey, Object param)
    throws RemoteException
  {
    try
    {
      Message msg = MXServer.getMXServer().getMaxMessageCache().getMessage(errGrp, errKey, getUserInfo());
      if (msg != null)
        return msg.getMessage(param);
    }
    catch (Exception e) {
    }
    return errGrp + "#" + errKey;
  }

  public String getMessage(MXException ex) throws RemoteException
  {
    String errorgroup = ex.getErrorGroup();
    String errorkey = ex.getErrorKey();
    String msgText;
    try
    {
      MaxMessageCache messageCache = MXServer.getMXServer().getMaxMessageCache();
      Message msg = messageCache.getMessage(errorgroup, errorkey, getUserInfo());

      if (ex.hasParameters())
      {
        Object[] params = ex.getParameters();
        for (int i = 0; i < params.length; ++i)
        {
          if (params[i] instanceof MXException)
            params[i] = getMessage(ex);
        }
        msgText = msg.getMessage(params);
      }
      else
      {
        msgText = msg.getMessage();
      }
      if (ex.hasDetail())
      {
        Throwable detail = ex.getDetail();
        if (detail instanceof MXException)
        {
          msgText = msgText + "\n\t" + getMessage((MXException)detail);
        }
        else
          msgText = msgText + "\n\t" + detail.getLocalizedMessage();
      }
    }
    catch (Throwable t)
    {
      msgText = errorgroup + "#" + errorkey;
    }
    return msgText;
  }

  public MaxMessage getMaxMessage(String errGrp, String errKey)
    throws MXException, RemoteException
  {
    try
    {
      return MXServer.getMXServer().getMaxMessageCache().getMaxMessage(errGrp, errKey, getUserInfo());
    }
    catch (Throwable t)
    {
      if (getMboLogger().isErrorEnabled())
      {
        getMboLogger().error(t);
      }
      throw new MXApplicationException("system", "unknownerror");
    }
  }

  public void setQueryBySiteQbe()
    throws MXException, RemoteException
  {
    boolean queryWithSite = getProfile().getQueryWithSite();
    if (!(queryWithSite))
      return;
    int siteOrgType = getMboSetInfo().getSiteOrgType();

    if ((siteOrgType == 1) || (siteOrgType == 8))
    {
      String insertSite = getProfile().getInsertSite();
      if ((insertSite != null) && (!(insertSite.equals(""))))
        setQbe("siteid", "=" + insertSite);
    }
    else if ((siteOrgType == 2) || (siteOrgType == 7) || (siteOrgType == 10))
    {
      String insertOrg = getProfile().getInsertOrg();
      if ((insertOrg != null) && (!(insertOrg.equals(""))))
        setQbe("orgid", "=" + insertOrg);
    }
    else if (siteOrgType == 3)
    {
      String insertItemSet = getProfile().getInsertItemSet();
      if ((insertItemSet != null) && (!(insertItemSet.equals(""))))
        setQbe("itemsetid", "=" + insertItemSet);
    } else {
      if (siteOrgType != 4)
        return;
      String insertCompanySet = getProfile().getInsertCompanySet();
      if ((insertCompanySet != null) && (!(insertCompanySet.equals(""))))
        setQbe("companysetid", "=" + insertCompanySet);
    }
  }

  public boolean setLogLargFetchResultDisabled(boolean disable)
    throws RemoteException
  {
    boolean ret = this.logLargFetchResultDisabled;
    this.logLargFetchResultDisabled = disable;
    return ret;
  }

  public void setQbe(String attribute, MboSetRemote lookup)
    throws MXException, RemoteException
  {
    MboSetRemote msr = getMboSetForAttribute(getRelAttrName(attribute));
    String attrName = attribute.substring(attribute.lastIndexOf(46) + 1);

    StringBuffer qbeString = null;

    String sourceKey = msr.getZombie().getMatchingAttr(lookup.getName(), getAttributeName(attrName).toUpperCase());
    if (sourceKey == null)
    {
      String[] params = { lookup.getName(), attrName };
      throw new MXApplicationException("system", "LookupNeedRegister", params);
    }

    Vector selected = lookup.getSelection();

    if (selected.size() == 0)
    {
      return;
    }
    if (selected.size() == 1)
    {
      setQbe(attribute, "=" + ((MboRemote)selected.get(0)).getString(sourceKey));
      return;
    }
    Iterator enum = selected.iterator();

    while (enum.hasNext())
    {
      MboRemote selectedMbo = (MboRemote)enum.next();
      if (qbeString == null) {
        qbeString = new StringBuffer("=" + selectedMbo.getString(sourceKey));
      }
      else
      {
        qbeString.append(",=");
        qbeString.append(selectedMbo.getString(sourceKey));
      }
    }
    setQbe(attribute, qbeString.toString());
  }

  public void addSubQbe(String name, String[] attrs, String operator)
    throws MXException, RemoteException
  {
    this.qbe.addSubQbe(name, attrs, operator);
  }

  public void addSubQbe(String name, String[] attrs, String operator, boolean exactMatch)
    throws MXException, RemoteException
  {
    this.qbe.addSubQbe(name, attrs, operator, exactMatch);
  }

  public void addSubQbe(String parentQbe, String name, String[] attrs, String operator, boolean exactMatch)
    throws MXException, RemoteException
  {
    MboQbe subQbe = this.qbe.getSubQbe(parentQbe);
    if (subQbe == null)
      throw new MXApplicationException("system", "SubQbeNotExist");
    subQbe.addSubQbe(name, attrs, operator, exactMatch);
  }

  public void addSubQbe(String parentQbe, String name, String[] attrs, String operator)
    throws MXException, RemoteException
  {
    MboQbe subQbe = this.qbe.getSubQbe(parentQbe);
    if (subQbe == null)
      throw new MXApplicationException("system", "SubQbeNotExist");
    subQbe.addSubQbe(name, attrs, operator);
  }

  int getSQLServerPrefetchRows()
  {
    readSQLServerPrefetchRowsSetting();
    return sqlServerPrefetchRows;
  }

  boolean isSQLServerPrefetchRowsNeeded()
  {
    readSQLServerPrefetchRowsSetting();

    return (sqlServerPrefetchRows > 0);
  }

  void readSQLServerPrefetchRowsSetting()
  {
    if (readSQLServerPrefetchRows) {
      return;
    }
    try
    {
      String rows = MXServer.getMXServer().getConfig().getProperty("mxe.db.sqlserverPrefetchRows");
      if ((rows == null) || (rows.trim().length() == 0))
      {
        sqlServerPrefetchRows = 0;
      }
      else
      {
        rows = rows.trim();
        sqlServerPrefetchRows = new Integer(rows).intValue() - 2;
        if (sqlServerPrefetchRows <= 0)
        {
          sqlServerPrefetchRows = 198;
        }
      }
    }
    catch (RemoteException ex)
    {
      ex.printStackTrace();
      sqlServerPrefetchRows = 198;
    }

    readSQLServerPrefetchRows = true;
  }

  public void setDefaultValue(String attribute, MboRemote mbo)
    throws MXException, RemoteException
  {
    MboSetRemote msr = getMboSetForAttribute(getRelAttrName(attribute));

    String sourceKey = msr.getZombie().getMatchingAttr(mbo.getName(), getAttributeName(attribute).toUpperCase());
    setDefaultValue(attribute, mbo.getString(sourceKey));
  }

  public void setInsertSite(String site)
    throws MXException, RemoteException
  {
    if (!(MXServer.getMXServer().isValidSite(site)))
    {
      throw new MXSystemException("system", "invalidsite", new Object[] { site });
    }
    this.insertSiteForSet = site;
  }

  public void setInsertOrg(String org)
    throws MXException, RemoteException
  {
    if (!(MXServer.getMXServer().isValidOrganization(org)))
    {
      throw new MXSystemException("system", "invalidorg", new Object[] { org });
    }
    this.insertOrgForSet = org;
  }

  public void setInsertCompanySet(String compSet)
    throws MXException, RemoteException
  {
    SetsServiceRemote setsServiceRemote = (SetsServiceRemote)MXServer.getMXServer().lookup("SETS");

    Vector org = setsServiceRemote.getOrgsForCompanySet(compSet, getUserInfo());
    if (org == null)
    {
      Object[] params = { compSet };
      throw new MXSystemException("system", "invalidcompanyset", params);
    }
    this.insertCompanySetForSet = compSet;
  }

  public void setInsertItemSet(String itemSet)
    throws MXException, RemoteException
  {
    SetsServiceRemote setsServiceRemote = (SetsServiceRemote)MXServer.getMXServer().lookup("SETS");

    Vector org = setsServiceRemote.getOrgsForItemSet(itemSet, getUserInfo());

    if (org == null)
    {
      Object[] params = { itemSet };
      throw new MXSystemException("system", "invaliditemset", params);
    }
    this.insertItemsetForSet = itemSet;
  }

  public void setExcludeMeFromPropagation(boolean flag)
    throws MXException, RemoteException
  {
    this.excludeMeFromPropagation = flag;
  }

  public boolean getExcludeMeFromPropagation()
    throws MXException, RemoteException
  {
    return this.excludeMeFromPropagation;
  }

  public String getInsertSite()
  {
    return this.insertSiteForSet;
  }

  public String getInsertOrg()
  {
    return this.insertOrgForSet;
  }

  public String getInsertCompanySet()
  {
    return this.insertCompanySetForSet;
  }

  public String getInsertItemSet()
  {
    return this.insertItemsetForSet;
  }

  private void logSQLStatementTime(String spid, String sqlStatement, long time, String appName, String objName, Connection conn)
  {
    StringBuffer lQuery = new StringBuffer("");
    if ((needToLogSQLOnTimeLimit()) && (time > getSQLTimeLimit()))
    {
      StringBuffer execBuffer = new StringBuffer();
      execBuffer.append("USER = (" + this.user.getLoginUserName() + ") SPID = (" + spid + ") ");
      execBuffer.append(" app (" + appName + ") object (" + objName + ") :");
      execBuffer.append(sqlStatement);
      execBuffer.append(" (execution took ");
      execBuffer.append(time);
      execBuffer.append(" milliseconds)");

      if ((needToLogSQLPlan()) && (sqlTableScanExclude().isEmpty()))
      {
        StringBuffer strBuf = logSQLPlan(conn, sqlStatement);
        if (strBuf != null) {
          execBuffer.append(strBuf);
        }
      }
      String execTimeMessage = execBuffer.toString();

      MAXIMOLOGGER.warn(execTimeMessage);
    }
    else if (getSqlLogger().isInfoEnabled())
    {
      lQuery.append("USER = (" + this.user.getLoginUserName() + ") SPID = (" + spid + ") ");
      lQuery.append(" app (" + appName + ") object (" + objName + ") :");
      lQuery.append(sqlStatement);
      getSqlLogger().info(lQuery.toString());
    }

    if ((needToLogSQLPlan()) && (!(sqlTableScanExclude().isEmpty())) && 
      (!(sqlTableScanExclude().contains(objName))))
    {
      StringBuffer strBuf = logSQLPlan(conn, sqlStatement);
      if (strBuf != null)
      {
        if (!(getSqlLogger().isInfoEnabled()))
        {
          lQuery.append("USER = (" + this.user.getLoginUserName() + ") SPID = (" + spid + ") ");
          lQuery.append(" app (" + appName + ") object (" + objName + ") :");
          lQuery.append(sqlStatement);
          lQuery.append(strBuf);
          MAXIMOLOGGER.info(lQuery);
        } else {
          MAXIMOLOGGER.info(strBuf);
        }
      }
    }

    incrementSQLStats(sqlStatement, time);
  }

  void incrementSQLStats(String stmt, long time)
  {
    PerformanceStats ps = (PerformanceStats)perfStats.get();

    if (ps == null)
      return;
    stmt = this.user.getUserName() + " -> " + stmt;
    ps.incrementSQLTime(time);
    ps.incrementSQLCount(stmt);
  }

  boolean needToLogSQLPlan()
  {
    try
    {
      String logSQLPlan = MXServer.getMXServer().getConfig().getProperty("mxe.db.logSQLPlan", "false");
      return Boolean.valueOf(logSQLPlan).booleanValue();
    }
    catch (RemoteException ex)
    {
      ex.printStackTrace(); }
    return false;
  }

  Vector sqlTableScanExclude()
  {
    if (this.sqlTableScanExcludeList != null) {
      return this.sqlTableScanExcludeList;
    }
    this.sqlTableScanExcludeList = new Vector();
    try
    {
      String str = MXServer.getMXServer().getConfig().getProperty("mxe.db.sqlTableScanExclude", null);
      if (str == null) {
        return this.sqlTableScanExcludeList;
      }
      StringTokenizer strTok = new StringTokenizer(str);
      while (strTok.hasMoreElements())
      {
        String tableName = strTok.nextToken(",").trim();
        this.sqlTableScanExcludeList.add(tableName);
      }
    }
    catch (RemoteException ex)
    {
      ex.printStackTrace();
    }

    return this.sqlTableScanExcludeList;
  }

  StringBuffer logSQLPlan(Connection conn, String sqlStatement)
  {
    StringBuffer strBuffer = new StringBuffer();
    try
    {
      String dbProductName = mboSetDBProductName;
      if (dbProductName.equalsIgnoreCase("Oracle"))
      {
        String query = "delete from plan_table where statement_id='000'";
        Statement stmt = conn.createStatement();
        stmt.executeUpdate(query);

        query = "explain plan set statement_id='000' for " + sqlStatement;
        stmt.execute(query);

        query = "select operation, options, object_name from plan_table where statement_id='000'";
        ResultSet rs = stmt.executeQuery(query);

        strBuffer.append("\nExplan plan: Operation, Options, Object Name\n");
        strBuffer.append("==========================================\n");
        while (rs.next())
        {
          strBuffer.append(rs.getString(1) + ", " + rs.getString(2) + ", " + rs.getString(3) + "\n");
        }
        stmt.close();

        if (strBuffer.indexOf("FULL") == -1)
          return null;
        return strBuffer;
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    return null;
  }

  public void clearTransactionReference()
  {
    this.activeTransaction = null;
    this.potentialTransaction = null;
  }

  public String getWhereForUIDisplay()
    throws MXException, RemoteException
  {
    return "";
  }
}
