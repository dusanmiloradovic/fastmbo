package psdi.security;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import psdi.app.person.PersonRemote;
import psdi.app.signature.MaxUserRemote;
import psdi.mbo.MaximoDD;
import psdi.mbo.MboRemote;
import psdi.mbo.MboSetRemote;
import psdi.mbo.SqlFormat;
import psdi.mbo.Translate;
import psdi.server.AppService;
import psdi.server.DBCredentialHandler;
import psdi.server.DBManager;
import psdi.server.MXServer;
import psdi.server.MXServerInfo;
import psdi.server.MaxVarServiceRemote;
import psdi.server.MaximoThread;
import psdi.server.Service;
import psdi.util.MXAccessException;
import psdi.util.MXCipher;
import psdi.util.MXCipherX;
import psdi.util.MXException;
import psdi.util.MXRowUpdateException;
import psdi.util.MXSystemException;
import psdi.util.MaxType;
import psdi.util.logging.FixedLoggers;
import psdi.util.logging.MXLogger;
import psdi.util.logging.MXLoggerFactory;

public class SecurityService extends AppService
  implements Service, SecurityServiceRemote
{
  static final long serialVersionUID = -5847676518092104544L;
  Hashtable users = new Hashtable();
  UserMonitor monitor;
  SessionCounter sessionCounter;
  String dbURL;
  UserInfo systemUserInfo = null;

  private HashMap internalDBCredential = new HashMap();

  boolean concurrentFlag = false;

  String concurrentCount = "clienthost";

  long sessionCounterDateInterval = 900000L;

  private Object lockObject = new Object();

  public SecurityService(MXServer mxServer)
    throws RemoteException
  {
    super(mxServer);
  }

  public SecurityService(String url)
    throws RemoteException
  {
    this.dbURL = url;
  }

  public SecurityService(String url, MXServer mxServer)
    throws MXException, RemoteException
  {
    super(mxServer);

    setURL(url);
  }

  public void configure(Properties configData)
  {
    this.dbURL = configData.getProperty("mxe.db.url");

    String propValue = configData.getProperty("mxe.enableConcurrentCheck", "false");
    this.concurrentFlag = Boolean.valueOf(propValue).booleanValue();

    if (this.concurrentFlag)
      this.concurrentCount = configData.getProperty("mxe.ConcurrentUserCount", "userid");
    else {
      this.concurrentCount = configData.getProperty("mxe.ConcurrentUserCount", "clienthost");
    }
    Integer timeInterval = Integer.getInteger("mxe.ClientCountMinutes");

    if ((timeInterval != null) && (timeInterval.intValue() >= 15))
      this.sessionCounterDateInterval = (60000 * timeInterval.intValue());
  }

  public void init()
  {
    super.init();

    this.monitor = new UserMonitor();
    this.monitor.start();

    this.sessionCounter = new SessionCounter();
    this.sessionCounter.start();
  }

  public void destroy()
  {
  }

  public UserInfo authenticateUser(String user, String password, String clientHost)
    throws MXException, RemoteException
  {
    return authenticateUser(user, password, Locale.getDefault(), TimeZone.getDefault(), null, clientHost, null);
  }

  public UserInfo authenticateUser(String user, String password, Locale locale, TimeZone timeZone, String clientHost)
    throws MXException, RemoteException
  {
    return authenticateUser(user, password, locale, timeZone, null, clientHost, null);
  }

  public UserInfo authenticateUser(String user, Object cert, String password, Locale locale, TimeZone timeZone, String clientHost) throws MXException, RemoteException
  {
    return authenticateUser(user, cert, password, locale, timeZone, null, clientHost);
  }

  public UserInfo authenticateUser(String loginID, Object cert, String password, Locale locale, TimeZone timeZone, String siteId, String clientHost)
    throws MXException, RemoteException
  {
    return authenticateUser(loginID, cert, password, locale, timeZone, siteId, clientHost, null);
  }

  public UserInfo authenticateUser(String loginID, Object cert, String password, Locale locale, TimeZone timeZone, String clientHost, String[] remoteNames)
    throws MXException, RemoteException
  {
    return authenticateUser(loginID, cert, password, locale, timeZone, null, clientHost, remoteNames);
  }

  public UserInfo authenticateUser(String loginID, Object cert, String password, Locale locale, TimeZone timeZone, String siteId, String clientHost, String[] remoteNames)
    throws MXException, RemoteException
  {
    UserInfo ui = authenticateUser(loginID, password, locale, timeZone, siteId, clientHost, remoteNames);

    int type = getMXServer().getDBManager().getDBAuthenticationType();
    if (type != 0)
    {
      ui.setCredential(getMXServer().getDBManager().getCredentialHandler().handleInput(loginID, password, cert));
    }

    if (type > 0)
    {
      Connection con = getDBConnection(ui.getConnectionKey());
      if (con == null)
      {
        throw new MXAccessException("access", "invalidCertificate", new Object[] { loginID });
      }

      getMXServer().getDBManager().freeConnection(ui.getConnectionKey());
    }
    return ui;
  }

  public UserInfo authenticateUser(String loginID, String password, Locale locale, TimeZone timeZone, String siteId, String clientHost, String[] remoteNames)
    throws MXException, RemoteException
  {
    if (useAppServerSecurity())
    {
      throw new MXAccessException("access", "mxauthdisabled");
    }

    String clientAddr = null;
    if (remoteNames != null) {
      clientAddr = remoteNames[0];
    }

    Object[] tempObj = commonUserValidation(loginID, false, clientHost, clientAddr);
    String userID = (String)tempObj[0];
    MboSetRemote userSet = (MboSetRemote)tempObj[1];
    MboRemote userMbo = (MboRemote)tempObj[2];

    boolean badPassword = false;
    long maxsessionid = 0L;
    MboRemote sessMbo = null;
    UserInfo userInfo = null;
    try
    {
      try
      {
        verifyUser(userID, password, userMbo);
      }
      catch (Exception e)
      {
        badPassword = true;
        throw e;
      }

      sessMbo = ((MaxUserRemote)userMbo).addMaxSession();
      maxsessionid = sessMbo.getLong("maxsessionuid");

      userInfo = registerUser(userMbo, locale, timeZone, maxsessionid, clientHost, remoteNames);
    }
    catch (Exception e)
    {
      if ((clientHost == null) && (remoteNames != null) && (remoteNames[1] != null) && (!(remoteNames[1].equals(""))))
        clientHost = remoteNames[1];
      if ((clientHost == null) || (clientHost.equals(""))) {
        clientHost = "UNKNOWN";
      }

      try
      {
        if (sessMbo != null) {
          sessMbo.getThisMboSet().clear();
        }
        MboRemote lt = ((MaxUserRemote)userMbo).addLoginTracking(getMaximoDD().getTranslator().toExternalDefaultValue("ATTEMPTRESULT", "FAILED", userMbo));

        if (lt != null)
        {
          lt.setValue("clienthost", clientHost, 11L);
          if (sessMbo != null)
          {
            lt.setValue("servername", sessMbo.getString("servername"), 11L);
            lt.setValue("serverhost", sessMbo.getString("serverhost"), 11L);
          }
          else
          {
            lt.setValue("servername", this.mxServer.getName(), 11L);
            lt.setValue("serverhost", this.mxServer.getServerHost(), 11L);
          }

          if (remoteNames != null)
          {
            lt.setValue("clientaddr", clientAddr, 11L);
          }

        }

        userSet.save();
      }
      catch (Exception e2)
      {
      }
      if (((MaxUserRemote)userMbo).isBlocked())
        throw new MXAccessException("signature", "blockedUser");
      if (badPassword) {
        throw new MXAccessException("signature", "badPswd");
      }
      throw new MXAccessException("access", "invaliduser");
    }

    try
    {
      if ((clientHost == null) && (remoteNames != null) && (remoteNames[1] != null) && (!(remoteNames[1].equals(""))))
        clientHost = remoteNames[1];
      if ((clientHost == null) || (clientHost.equals(""))) {
        clientHost = "UNKNOWN";
      }
      MboRemote lt = ((MaxUserRemote)userMbo).addLoginTracking(getMaximoDD().getTranslator().toExternalDefaultValue("ATTEMPTRESULT", "LOGIN", userMbo));
      if (lt != null)
      {
        lt.setValue("maxsessionuid", maxsessionid, 11L);
        lt.setValue("clienthost", clientHost, 11L);
        lt.setValue("servername", sessMbo.getString("servername"), 11L);
        lt.setValue("serverhost", sessMbo.getString("serverhost"), 11L);

        if (remoteNames != null)
        {
          lt.setValue("clientaddr", clientAddr, 11L);
        }

      }

      sessMbo.setValue("clienthost", clientHost, 11L);
      sessMbo.setValue("maxsessionid", maxsessionid, 11L);

      if (remoteNames != null)
      {
        sessMbo.setValue("clientaddr", clientAddr, 11L);
      }

      userSet.save();
    }
    catch (Exception e)
    {
    	e.printStackTrace();
      throw new MXAccessException("signature", "ConnectNotAllowed");
    }

    return userInfo;
  }

  private Object[] commonUserValidation(String loginID, boolean haveToken, String clientHost, String clientAddr)
    throws MXException, RemoteException
  {
    String userID = null;
    MboSetRemote userSet = null;
    MboRemote userMbo = null;

    String convertToUpperStr = this.mxServer.getConfig().getProperty("mxe.convertloginid");
    boolean convertToUpper = false;
    if ((convertToUpperStr != null) && (!(convertToUpperStr.equals("")))) {
      convertToUpper = convertToUpperStr.equals("1");
    }
    if (convertToUpper) {
      loginID = loginID.toUpperCase();
    }
    try
    {
      userSet = this.mxServer.getMboSet("MAXUSER", getSystemUserInfo());
      SqlFormat sqf = new SqlFormat("loginid = :1");
      sqf.setObject(1, "MAXUSER", "LOGINID", loginID);
      userSet.setWhere(sqf.format());

      userSet.reset();

      if (userSet.isEmpty())
      {
        if (haveToken) {
          throw new MXAccessException("access", "userdoesnotexist", new Object[] { loginID });
        }
        throw new MXAccessException("access", "invaliduser");
      }

      userMbo = userSet.getMbo(0);
      userID = userMbo.getString("userid");

      if (haveToken)
      {
        checkConcurrentUser(userID, null);
      }

      if (((MaxUserRemote)userMbo).isBlocked()) {
        throw new MXAccessException("signature", "blockedUser");
      }
      if (((MaxUserRemote)userMbo).isInactive()) {
        throw new MXAccessException("signature", "inactiveUser");
      }

      if (!(((MaxUserRemote)userMbo).isActive())) {
        throw new MXAccessException("access", "invaliduser");
      }

      if (this.users.containsKey(userID))
      {
        Profile profile = ((SecurityInfo)this.users.get(userID)).getProfile();
        profile.checkLogout();
      }
    }
    catch (MXException me)
    {
      if (getMaxVar().getBoolean("LOGINTRACKING", ""))
      {
        try
        {
          if (clientHost == null)
            clientHost = "UNKNOWN";
          if ((userID == null) || (userMbo == null))
          {
            MboSetRemote ltSet = this.mxServer.getMboSet("LOGINTRACKING", getSystemUserInfo());
            MboRemote ltMbo = ltSet.add();
            ltMbo.setValue("attemptresult", false);
            if ((userID == null) || (userID.equals("")))
              userID = "<null>";
            ltMbo.setValue("userid", userID, 11L);
            ltMbo.setValue("serverhost", this.mxServer.getServerHost(), 11L);
            ltMbo.setValue("servername", this.mxServer.getName(), 11L);
            ltMbo.setValue("attemptresult7", getMaximoDD().getTranslator().toExternalDefaultValue("ATTEMPTRESULT", "FAILED", ltMbo), 11L);
            ltMbo.setValue("clienthost", clientHost, 11L);
            ltMbo.setValue("clientaddr", clientAddr, 11L);
            ltMbo.setValue("loginid", loginID, 11L);
            ltSet.save();
          }
          else
          {
            MboRemote ltMbo = ((MaxUserRemote)userMbo).addLoginTracking(getMaximoDD().getTranslator().toExternalDefaultValue("ATTEMPTRESULT", "FAILED", userMbo), false);
            ltMbo.setValue("clienthost", clientHost, 11L);
            ltMbo.setValue("clientaddr", clientAddr, 11L);
            userSet.save();
          }
        }
        catch (Exception e)
        {
        }
      }

      throw me;
    }

    Object[] retObj = { userID, userSet, userMbo };

    return retObj;
  }

  private boolean useAppServerSecurity()
  {
    boolean useAppServerSecurity = false;

    useAppServerSecurity = this.mxServer.getMXServerInfo().useAppServerSecurity();

    return useAppServerSecurity;
  }

  public UserInfo getUserInfo(AuthenticatedAccessToken session, Object cert, Locale locale, TimeZone timeZone)
    throws MXException, RemoteException
  {
    UserInfo ui = getUserInfo(session, locale, timeZone);
    int type = getMXServer().getDBManager().getDBAuthenticationType();
    if (type == 2)
    {
      throw new MXAccessException("access", "pwdNotAllowedWithAppSecurity");
    }
    if (type != 0)
    {
      ui.setCredential(getMXServer().getDBManager().getCredentialHandler().handleInput(session.getUserName(), null, cert));
    }

    if (type > 0)
    {
      Connection con = getDBConnection(ui.getConnectionKey());
      if (con == null)
      {
        throw new MXAccessException("access", "invalidCertificate", new Object[] { session.getUserName() });
      }

      getMXServer().getDBManager().freeConnection(ui.getConnectionKey());
    }
    return ui;
  }

  public UserInfo getUserInfo(AuthenticatedAccessToken session, Locale locale, TimeZone timeZone)
    throws MXException, RemoteException
  {
    if (!(useAppServerSecurity()))
    {
      throw new MXAccessException("access", "mxauthnotenabled");
    }

    UserInfo userInfo = null;

    long currentTime = System.currentTimeMillis();

    if (currentTime < session.getSessionCreationTime())
    {
      throw new MXAccessException("access", "sessionexpired");
    }

    if ((session.getMaximoBindingName() != null) && 
      (!(session.getMaximoBindingName().equals(this.mxServer.getURL()))))
    {
      throw new MXAccessException("access", "invalidsession");
    }

    byte[] sessionData = session.getSessionData();
    if (!(ApplicationSessionKeys.isValidSessionData(sessionData, session.getUserName())))
    {
      throw new MXAccessException("access", "invalidsession");
    }

    String userLoginName = session.getUserName();

    Object[] tempObj = commonUserValidation(userLoginName, true, null, null);

    MboSetRemote userSet = (MboSetRemote)tempObj[1];
    MboRemote userMbo = (MboRemote)tempObj[2];

    MboRemote sessMbo = ((MaxUserRemote)userMbo).addMaxSession();
    long maxsessionid = sessMbo.getLong("maxsessionuid");

    userInfo = registerUser(userMbo, locale, timeZone, maxsessionid, null, null);

    MboRemote lt = ((MaxUserRemote)userMbo).addLoginTracking(getMaximoDD().getTranslator().toExternalDefaultValue("ATTEMPTRESULT", "LOGIN", userMbo));
    if (lt != null)
    {
      lt.setValue("maxsessionuid", maxsessionid, 11L);
      lt.setValue("clientHost", "UNKNOWN", 11L);
    }

    sessMbo.setValue("clienthost", "UNKNOWN", 11L);
    sessMbo.setValue("maxsessionid", maxsessionid, 11L);

    userSet.save();

    return userInfo;
  }

  public UserInfo authenticateUser(String userIdentity)
    throws MXException, RemoteException
  {
    return authenticateUser(userIdentity, false);
  }

  public UserInfo authenticateUserForLoginID(String loginID, boolean silentLogin)
    throws MXException, RemoteException
  {
    String userID = getUserIDForLoginID(loginID, getSystemUserInfo());

    return authenticateUser(userID, silentLogin);
  }

  public UserInfo authenticateUser(String userIdentity, boolean silentLogin)
    throws MXException, RemoteException
  {
    userIdentity = userIdentity.toUpperCase();

    MboRemote userMbo = getUserMbo(userIdentity, getSystemUserInfo(), true);

    long maxsessionid = 0L;
    MboRemote sessMbo = null;

    if (!(silentLogin))
    {
      sessMbo = ((MaxUserRemote)userMbo).addMaxSession();
      maxsessionid = sessMbo.getLong("maxsessionuid");
    }

    MboRemote person = userMbo.getMboSet("PERSON").getMbo(0);
    Locale loc = new Locale(person.getString("locale"));
    TimeZone tz = (person.isNull("timezone")) ? TimeZone.getDefault() : TimeZone.getTimeZone(person.getString("timezone"));

    UserInfo userInfo = registerUser(userMbo, loc, tz, maxsessionid, null, null);

    int type = getMXServer().getDBManager().getDBAuthenticationType();
    if (type != 0)
    {
      Object credential = this.internalDBCredential.get(userIdentity);
      if (credential == null)
      {
        MboSetRemote dbAuthInfos = getMboSet("maxusrdbauthinfo", getSystemUserInfo());
        dbAuthInfos.setQbe("loginId", userMbo.getString("loginid"));
        MaxUsrDBAuthInfoRemote info = (MaxUsrDBAuthInfoRemote)dbAuthInfos.getMbo(0);
        credential = this.mxServer.getDBManager().getCredentialHandler().handleInput(userMbo.getString("loginID"), info);
        this.internalDBCredential.put(userIdentity, credential);
      }
      userInfo.setCredential(credential);

      Connection con = getDBConnection(userInfo.getConnectionKey());
      if (con == null)
      {
        throw new MXAccessException("access", "invalidCertificate", new Object[] { userMbo.getString("loginid") });
      }

      getMXServer().getDBManager().freeConnection(userInfo.getConnectionKey());
    }

    if (!(silentLogin))
    {
      MboRemote lt = ((MaxUserRemote)userMbo).addLoginTracking(getMaximoDD().getTranslator().toExternalDefaultValue("ATTEMPTRESULT", "LOGIN", userMbo));
      if (lt != null)
      {
        lt.setValue("maxsessionuid", maxsessionid, 11L);
        lt.setValue("clienthost", "UNKNOWN", 11L);
      }

      sessMbo.setValue("clienthost", "UNKNOWN", 11L);
      sessMbo.setValue("maxsessionid", maxsessionid, 11L);

      userMbo.getThisMboSet().save();
    }

    return userInfo;
  }

  public UserInfo getUserInfo(String userIdentity)
    throws MXException, RemoteException
  {
    userIdentity = userIdentity.toUpperCase();

    UserInfo userInfo = null;
    UserInfo systemUserInfoTemp = null;

    String systemUserIdentity = this.mxServer.getConfig().getProperty("mxe.adminuserid").toUpperCase();
    if (userIdentity.equalsIgnoreCase(systemUserIdentity))
    {
      systemUserInfoTemp = createUserInfo(null, Locale.getDefault(), TimeZone.getDefault(), 0L, userIdentity, null, null);
      setSystemCredential(systemUserInfoTemp);
    }
    else
    {
      systemUserInfoTemp = getSystemUserInfo();
    }

    MboRemote userMbo = getUserMbo(userIdentity, systemUserInfoTemp, true);

    userInfo = createUserInfo(userMbo, Locale.getDefault(), TimeZone.getDefault(), 0L, userIdentity, null, null);

    SecurityInfo si = (SecurityInfo)this.users.get(userIdentity);

    if (si == null)
    {
      synchronized (this.lockObject)
      {
        this.users.remove(userInfo.getUserName());
        this.users.put(userInfo.getUserName(), new SecurityInfo(userInfo));
      }
    }

    return userInfo;
  }

  private MboRemote getUserMbo(String userIdentity, UserInfo systemUserInfoTemp, boolean activeStatusOnly)
    throws MXException, RemoteException
  {
    MboSetRemote userSet = this.mxServer.getMboSet("MAXUSER", systemUserInfoTemp);
    String sql = "userid = :1";
    if (activeStatusOnly) {
      sql = sql + " and status in (select value from synonymdomain where domainid = 'MAXUSERSTATUS' and maxvalue = 'ACTIVE')";
    }
    SqlFormat sqf = new SqlFormat(sql);
    sqf.setObject(1, "MAXUSER", "USERID", userIdentity);
    userSet.setWhere(sqf.format());

    if (userSet.isEmpty())
    {
      throw new MXAccessException("access", "userdoesnotexist", new Object[] { userIdentity });
    }

    return userSet.getMbo(0);
  }

  private String getUserIDForLoginID(String loginID, UserInfo systemUserInfoTemp)
    throws MXException, RemoteException
  {
    String userID = null;
    MboSetRemote userSet = this.mxServer.getMboSet("MAXUSER", systemUserInfoTemp);
    String sql = "loginid = :1";

    SqlFormat sqf = new SqlFormat(sql);
    sqf.setObject(1, "MAXUSER", "LOGINID", loginID);
    userSet.setWhere(sqf.format());

    if (!(userSet.isEmpty())) {
      userID = userSet.getMbo(0).getString("userid");
    }
    userSet.close();
    return userID;
  }

  protected void verifyUser(String userName, String enteredPassword, MboRemote userMbo)
    throws Exception
  {
    if (!(userName.equals(userMbo.getString("userid")))) {
      throw new MXAccessException("access", "invaliduser");
    }
    byte[] storedPassword = userMbo.getBytes("password");
    MaxType storedMT = new MaxType(16, storedPassword);

    byte[] enteredPasswordBytes = getMXCipherX().encData(enteredPassword);
    MaxType enteredMT = new MaxType(16, enteredPasswordBytes);

    if (!(storedMT.equals(enteredMT)))
      throw new MXAccessException("access", "invaliduser");
  }

  public boolean isUser(UserInfo userinfo, String loginCheck, String passCheck)
    throws MXException, RemoteException
  {
    boolean userIsGood = true;

    if ((loginCheck == null) || (!(loginCheck.equals(userinfo.getLoginID())))) {
      userIsGood = false;
    }
    try
    {
      MboRemote userMbo = getUserMbo(userinfo.getUserName(), getSystemUserInfo(), false);

      if (userMbo != null)
      {
        byte[] storedPassword = userMbo.getBytes("password");
        MaxType storedMT = new MaxType(16, storedPassword);

        byte[] enteredPasswordBytes = getMXCipherX().encData(passCheck);
        MaxType enteredMT = new MaxType(16, enteredPasswordBytes);

        if (!(storedMT.equals(enteredMT)))
          userIsGood = false;
      }
      else
      {
        userIsGood = false;
      }
    }
    catch (Exception e)
    {
      userIsGood = false;
    }

    return userIsGood;
  }

  private String getOrgId(String siteid)
  {
    String orgid = null;
    try
    {
      orgid = MXServer.getMXServer().getMaximoDD().getOrgId(siteid);
    }
    catch (RemoteException ex)
    {
      ex.printStackTrace();
    }

    return orgid;
  }

  private String getBaseCurrency(String orgId)
  {
    String baseCurrency = null;
    try
    {
      baseCurrency = MXServer.getMXServer().getMaximoDD().getBaseCurrency(orgId);
    }
    catch (RemoteException ex)
    {
      ex.printStackTrace();
    }

    return baseCurrency;
  }

  private UserInfo createUserInfo(MboRemote userMbo, Locale locale, TimeZone timeZone, long maxsessionid, String systemUserID, String clientHost, String[] remoteNames)
    throws MXException, RemoteException
  {
    UserInfo userInfo = null;
    MboRemote personMbo = null;
    MboRemote emailMbo = null;
    String userid = null;
    String loginid = null;
    String personid = null;
    String displayName = null;
    boolean queryWithSite = false;
    byte[] password = null;
    String siteid = null;
    String orgid = null;
    String emailaddress = null;
    String defLang = null;
    String defLocaleStr = null;
    String defTimeZoneStr = null;
    String defStoreroom = null;
    String defStoreroomSite = null;
    boolean screenReader = false;

    if (userMbo != null)
    {
      userid = userMbo.getString("userid");
      loginid = userMbo.getString("loginid");
      personid = userMbo.getString("personid");
      password = userMbo.getBytes("password");
      queryWithSite = userMbo.getBoolean("querywithsite");
      defStoreroom = userMbo.getString("defstoreroom");
      screenReader = userMbo.getBoolean("screenreader");

      if (!(userMbo.isNull("defsite")))
      {
        siteid = userMbo.getString("defsite");
        orgid = getOrgId(siteid);
      }
      if (!(userMbo.isNull("storeroomsite")))
      {
        defStoreroomSite = userMbo.getString("storeroomsite");
      }

      MboSetRemote personSet = userMbo.getMboSet("PERSON");
      if (!(personSet.isEmpty()))
      {
        personMbo = personSet.getMbo(0);
        defLang = personMbo.getString("language");
        defLocaleStr = ((PersonRemote)personMbo).getLocaleStr();
        defTimeZoneStr = ((PersonRemote)personMbo).getTimezoneStr();
        displayName = personMbo.getString("displayname");

        MboSetRemote emailSet = personMbo.getMboSet("PRIMARYEMAIL");
        if (!(emailSet.isEmpty()))
        {
          emailMbo = emailSet.getMbo(0);
          emailaddress = emailMbo.getString("emailaddress");
        }
      }

    }
    else
    {
      userid = systemUserID;
      loginid = systemUserID;
    }

    if ((siteid != null) && 
      (!(this.mxServer.isValidSite(siteid)))) {
      throw new MXSystemException("system", "invalidsite", new Object[] { siteid });
    }
    if ((defStoreroomSite != null) && 
      (!(this.mxServer.isValidSite(defStoreroomSite)))) {
      throw new MXSystemException("system", "invalidsite", new Object[] { defStoreroomSite });
    }

    if ((orgid != null) && 
      (!(this.mxServer.isValidOrganization(orgid)))) {
      throw new MXSystemException("system", "invalidorg", new Object[] { orgid });
    }

    if ((defLang == null) || (defLang.equals(""))) {
      defLang = this.mxServer.getBaseLang();
    }
    UserLoginDetails loginDetails = new UserLoginDetails();

    loginDetails.setLoginID(loginid);
    loginDetails.setUserName(userid);

    loginDetails.setInsertSite(siteid);
    loginDetails.setDefaultSite(siteid);
    loginDetails.setInsertOrg(orgid);
    loginDetails.setDefaultOrg(orgid);

    loginDetails.setBaseCurrency(getBaseCurrency(orgid));
    loginDetails.setSchemaOwner(this.mxServer.getDBManager().getSchemaOwner());
    loginDetails.setPersonId(personid);
    loginDetails.setDisplayName(displayName);
    loginDetails.setQueryWithSite(queryWithSite);
    loginDetails.setEmail(emailaddress);
    loginDetails.setDefaultLang(defLang);
    loginDetails.setDefaultTZStr(defTimeZoneStr);
    loginDetails.setDefaultLocaleStr(defLocaleStr);
    loginDetails.setMaxSessionID(maxsessionid);
    loginDetails.setDefaultStoreroom(defStoreroom);
    loginDetails.setDefStoreroomSite(defStoreroomSite);
    loginDetails.setScreenReader(screenReader);

    loginDetails.setClientHost(clientHost);
    loginDetails.setRemoteNames(remoteNames);

    userInfo = new UserInfo(loginDetails);
    userInfo.setMXServer(this.mxServer);
    userInfo.setLocale(locale);
    userInfo.setTimeZone(timeZone);

    return userInfo;
  }

  protected MXCipher getMXCipher()
  {
    return this.mxServer.getMXCipher();
  }

  protected MXCipherX getMXCipherX() {
    return this.mxServer.getMXCipherX();
  }

  public void checkConcurrentUser(String userId, String loginID)
    throws RemoteException, MXException
  {
    String user = userId;

    String convertToUpperStr = this.mxServer.getConfig().getProperty("mxe.convertloginid");
    boolean convertToUpper = false;
    if ((convertToUpperStr != null) && (!(convertToUpperStr.equals("")))) {
      convertToUpper = convertToUpperStr.equals("1");
    }
    if (convertToUpper) {
      loginID = loginID.toUpperCase();
    }
    if (!(this.concurrentFlag))
    {
      return;
    }

    if ((userId == null) || (userId.trim().length() == 0))
    {
      MboSetRemote userSet = this.mxServer.getMboSet("MAXUSER", getSystemUserInfo());
      SqlFormat sqf = new SqlFormat("loginid = :1");
      sqf.setObject(1, "MAXUSER", "LOGINID", loginID);
      userSet.setWhere(sqf.format());

      if (userSet.isEmpty()) {
        throw new MXAccessException("access", "invaliduser");
      }
      MboRemote userMbo = userSet.getMbo(0);
      user = userMbo.getString("userid");
    }

    if ((this.users == null) || (user == null))
      return;
    if (this.users.containsKey(user.toUpperCase()))
    {
      throw new MXSystemException("system", "dupuser");
    }

    MboSetRemote sessSet = this.mxServer.getMboSet("MAXSESSION", getSystemUserInfo());
    SqlFormat sqf = new SqlFormat("userid = :1");
    sqf.setObject(1, "MAXSESSION", "USERID", user);
    sessSet.setWhere(sqf.format());
    if (sessSet.isEmpty())
      return;
    throw new MXSystemException("system", "dupuser");
  }

  private UserInfo registerUser(MboRemote userMbo, Locale locale, TimeZone timeZone, long maxsessionid, String clientHost, String[] remoteNames)
    throws MXException, RemoteException
  {
    UserInfo userInfo = null;

    userInfo = createUserInfo(userMbo, locale, timeZone, maxsessionid, null, clientHost, remoteNames);

    SecurityInfo si = (SecurityInfo)this.users.get(userInfo.getUserName());

    if ((si == null) || ((si.getUserInfo().getPersonId() == null) && (userInfo.getPersonId() != null)))
    {
      synchronized (this.lockObject)
      {
        this.users.remove(userInfo.getUserName());
        this.users.put(userInfo.getUserName(), new SecurityInfo(userInfo));
      }
    }

    return userInfo;
  }

  public UserInfo getSystemUserInfo()
    throws MXException, RemoteException
  {
    synchronized (this)
    {
      if (this.systemUserInfo != null)
      {
        UserInfo retSystemUserInfo;
        
        try {
          retSystemUserInfo = (UserInfo)this.systemUserInfo.clone();
        }
        catch (CloneNotSupportedException ce)
        {
          String userIdentity = this.mxServer.getConfig().getProperty("mxe.adminuserid").toUpperCase();
          retSystemUserInfo = getUserInfo(userIdentity);
        }

        return retSystemUserInfo;
      }

      String userIdentity = this.mxServer.getConfig().getProperty("mxe.adminuserid").toUpperCase();

      this.systemUserInfo = getUserInfo(userIdentity);

      setSystemCredential(this.systemUserInfo);
    }

    return this.systemUserInfo;
  }

  public boolean isSystemUserInfo(UserInfo ui)
  {
    if (this.systemUserInfo == null)
    {
      String systemUserIdentity = this.mxServer.getConfig().getProperty("mxe.adminuserid").toUpperCase();

      return (ui.getUserName().equalsIgnoreCase(systemUserIdentity));
    }

    return ((this.systemUserInfo == ui) || (ui.isClonedFrom(this.systemUserInfo)));
  }

  public void setSystemCredential(UserInfo uiObject)
    throws MXException, RemoteException
  {
    int type = getMXServer().getDBManager().getDBAuthenticationType();
    if (type == 0)
      return;
    if (uiObject.getCredential() != null) {
      return;
    }
    String userIdentity = this.mxServer.getConfig().getProperty("mxe.adminuserid").toUpperCase();

    Object credential = this.internalDBCredential.get(userIdentity);

    if (credential == null)
    {
      String credentialProperty = this.mxServer.getConfig().getProperty("mxe.adminusercredential");
      String loginid = this.mxServer.getConfig().getProperty("mxe.adminuserloginid");
      if ((loginid != null) && (credentialProperty != null) && (!(loginid.equals(""))) && (!(credentialProperty.equals(""))))
      {
        try
        {
          credential = this.mxServer.getDBManager().getCredentialHandler().handleInput(loginid.trim(), credentialProperty);
        }
        catch (Exception e) {
        }
      }
    }
    if (credential == null)
    {
      MboRemote userMbo = null;
      try
      {
        userMbo = getUserMbo(userIdentity, uiObject, true);

        MboSetRemote dbAuthInfos = getMboSet("maxusrdbauthinfo", uiObject);
        dbAuthInfos.setQbe("loginId", userMbo.getString("loginid"));
        MaxUsrDBAuthInfoRemote info = (MaxUsrDBAuthInfoRemote)dbAuthInfos.getMbo(0);
        credential = this.mxServer.getDBManager().getCredentialHandler().handleInput(userMbo.getString("loginID"), info);
      }
      catch (Exception e)
      {
        if (MAXIMOLOGGER.isWarnEnabled())
          MAXIMOLOGGER.warn(e);
      }
    }
    if (credential != null)
    {
      this.internalDBCredential.put(userIdentity, credential);
      uiObject.setCredential(credential);

      Connection con = getDBConnection(uiObject.getConnectionKey());
      if (con == null)
      {
        throw new MXAccessException("access", "invalidCertificate", new Object[] { this.systemUserInfo.getLoginID() });
      }

      getMXServer().getDBManager().freeConnection(uiObject.getConnectionKey());
    }
    else {
      throw new MXAccessException("access", "invalidCertificate", new Object[] { uiObject.getLoginID() });
    }
  }

  private SecurityInfo getSecurityInfo(UserInfo userInfo)
  {
    SecurityInfo si = (SecurityInfo)this.users.get(userInfo.getUserName());
    long maxsessionid = userInfo.getUserLoginDetails().getMaxSessionID();
    String userid = userInfo.getUserName();

    if (si == null)
    {
      setSessionActive(maxsessionid);

      synchronized (this.lockObject)
      {
        this.users.remove(userid);
        si = new SecurityInfo(userInfo);
        si.setLastUsed(new Date(), maxsessionid);
        this.users.put(userid, si);
      }
    }
    else
    {
      if (!(si.getSessions().containsKey(new Long(maxsessionid))))
      {
        setSessionActive(maxsessionid);
      }

      synchronized (this.lockObject)
      {
        si.setLastUsed(new Date(), maxsessionid);
        this.users.put(userid, si);
      }
    }

    return si;
  }

  private void setSessionActive(long maxsessionid)
  {
    try
    {
      MboSetRemote sessSet = this.mxServer.getMboSet("MAXSESSION", getSystemUserInfo());
      SqlFormat sqf = new SqlFormat("maxsessionuid = :1 and active != :yes and adminlogout != :yes");
      sqf.setLong(1, maxsessionid);
      sessSet.setWhere(sqf.format());
      if (!(sessSet.isEmpty()))
      {
        MboRemote sessMbo = sessSet.getMbo(0);
        sessMbo.setValue("active", true, 11L);
        sessSet.save();
      }
    }
    catch (Exception e)
    {
    }
  }

  public ProfileRemote getProfile(UserInfo userInfo)
    throws MXException, RemoteException
  {
    SecurityInfo si = getSecurityInfo(userInfo);

    Profile pf = null;
    synchronized (si)
    {
      pf = si.getProfile();
    }
    return pf;
  }

  public ProfileRemote getProfile(String userID)
    throws MXException, RemoteException
  {
    userID = userID.toUpperCase();

    ProfileRemote profile = null;

    if (!(this.users.containsKey(userID))) {
      return profile;
    }
    SecurityInfo si = (SecurityInfo)this.users.get(userID);

    return si.getProfile();
  }

  public void refreshProfile(UserInfo userInfo, Profile profile)
    throws MXException, RemoteException
  {
    refreshProfile(userInfo.getUserName(), profile);
  }

  public void refreshProfile(String userID, Profile profile)
    throws MXException, RemoteException
  {
    if (!(this.users.containsKey(userID))) {
      return;
    }
    SecurityInfo si = (SecurityInfo)this.users.get(userID);

    si.refreshProfile(profile);
  }

  public void refreshSecurityInfo(String userID, MboRemote userMbo, MboRemote personMbo)
    throws MXException, RemoteException
  {
    if ((userID == null) || ((userMbo == null) && (personMbo == null))) {
      return;
    }
    if ((userMbo != null) && (!(userMbo.getString("userid").equals(userID)))) {
      return;
    }
    if ((userMbo != null) && (personMbo != null) && (!(userMbo.getString("personid").equals(personMbo.getString("personid"))))) {
      return;
    }
    if (!(this.users.containsKey(userID))) {
      return;
    }
    synchronized (this.lockObject)
    {
      SecurityInfo si = (SecurityInfo)this.users.get(userID);
      UserInfo ui = si.getUserInfo();
      UserLoginDetails uld = ui.getUserLoginDetails();
      Profile p = si.getProfile();

      if (userMbo != null)
      {
        String defaultSite = userMbo.getString("defsite");

        if ((defaultSite != null) && 
          (defaultSite.trim().equalsIgnoreCase(""))) {
          defaultSite = null;
        }

        String defaultOrg = getOrgId(defaultSite);

        uld.setInsertSite(defaultSite);
        uld.setDefaultSite(defaultSite);
        p.setInsertSite(defaultSite);
        p.setDefaultSite(defaultSite);

        uld.setInsertOrg(defaultOrg);
        uld.setDefaultOrg(defaultOrg);
        p.setInsertOrg(defaultOrg);
        p.setDefaultOrg(defaultOrg);

        uld.setBaseCurrency(getBaseCurrency(defaultOrg));

        uld.setInsertCompanySet(null);
        uld.setInsertItemSet(null);

        uld.setQueryWithSite(userMbo.getBoolean("querywithsite"));

        uld.setDefStoreroomSite(userMbo.getString("storeroomsite"));
        uld.setDefaultStoreroom(userMbo.getString("defstoreroom"));
      }

      if (personMbo != null)
      {
        uld.setEmail(personMbo.getString("primaryemail"));
      }

      ui.refreshLoginDetails(uld);
      p.refreshLoginUserInfo(ui);
      si.refreshUserInfo(ui);
      si.refreshProfile(p);
      si.setLastUsed(new Date());
    }
  }

  public String getDBUrl()
  {
    return this.dbURL;
  }

  /** @deprecated */
  public void disconnectUser(UserInfo userInfo)
  {
    disconnectUser(userInfo, 3);
  }

  public void disconnectUser(UserInfo userInfo, int disconnectType)
  {
    try
    {
      boolean noMoreSessions = removeUserFromCache(userInfo.getUserName(), userInfo.getMaxSessionID(), disconnectType, null);

      if (noMoreSessions)
      {
        synchronized (this.lockObject)
        {
          getMXServer().clearUserInput(userInfo);
        }
      }
    }
    catch (Exception e)
    {
      MXLogger secLogger = MXLoggerFactory.getLogger("maximo.service.SECURITY");

      if ((secLogger != null) && (secLogger.isErrorEnabled()))
        secLogger.error(e);
    }
  }

  public void disconnectUser(String userid, long maxsessionid, int disconnectType, String adminUserID)
  {
    try
    {
      removeUserFromCache(userid, maxsessionid, disconnectType, adminUserID);
    }
    catch (Exception e)
    {
      MXLogger secLogger = MXLoggerFactory.getLogger("maximo.service.SECURITY");

      if ((secLogger != null) && (secLogger.isErrorEnabled()))
        secLogger.error(e);
    }
  }

  private boolean removeUserFromCache(String userid, long maxsessionid, int disconnectType, String adminUserID)
    throws MXException, RemoteException
  {
    boolean deleteMaxsession = false;

    if ((disconnectType == 3) || (disconnectType == 1)) {
      deleteMaxsession = true;
    }
    boolean noMoreActiveSessions = false;
    MboSetRemote sessSet = this.mxServer.getMboSet("MAXSESSION", getSystemUserInfo());
    SqlFormat sqf = null;
    String clientHost = null;
    String clientAddr = null;

    if (maxsessionid != 0L)
    {
      sqf = new SqlFormat("maxsessionuid = :1");
      sqf.setLong(1, maxsessionid);
      sessSet.setWhere(sqf.format());
      if (!(sessSet.isEmpty()))
      {
        MboRemote sessMbo = sessSet.getMbo(0);
        clientHost = sessMbo.getString("clienthost");
        clientAddr = sessMbo.getString("clientaddr");

        if (disconnectType != 2)
        {
          if (deleteMaxsession)
            sessMbo.delete();
          else
            sessMbo.setValue("active", false, 11L);
          sessSet.save();
        }

      }

    }

    sqf = new SqlFormat("servername = :1 and serverhost = :2 and userid = :3 and active = :yes");
    sqf.setObject(1, "MAXSESSION", "SERVERNAME", this.mxServer.getName());
    sqf.setObject(2, "MAXSESSION", "SERVERHOST", this.mxServer.getServerHost());
    sqf.setObject(3, "MAXSESSION", "USERID", userid);
    sessSet.setWhere(sqf.format());
    sessSet.reset();
    if (sessSet.isEmpty())
    {
      noMoreActiveSessions = true;
    }

    if ((disconnectType != 0) && (getMaxVar().getBoolean("LOGINTRACKING", "")))
    {
      MboSetRemote userSet = this.mxServer.getMboSet("MAXUSER", getSystemUserInfo());
      sqf = new SqlFormat("userid = :1");
      sqf.setObject(1, "MAXUSER", "USERID", userid);
      userSet.setWhere(sqf.format());
      if (!(userSet.isEmpty()))
      {
        MboRemote userMbo = userSet.getMbo(0);
        String attemptResult = (disconnectType == 1) ? getMaximoDD().getTranslator().toExternalDefaultValue("ATTEMPTRESULT", "TIMEOUT", userMbo) : getMaximoDD().getTranslator().toExternalDefaultValue("ATTEMPTRESULT", "LOGOUT", userMbo);

        MboRemote lt = ((MaxUserRemote)userMbo).addLoginTracking(attemptResult, false);
        if (lt != null)
        {
          lt.setValue("maxsessionuid", maxsessionid, 11L);

          if (clientHost == null)
          {
            if (this.users.contains(userid))
            {
              clientHost = ((SecurityInfo)this.users.get(userid)).getUserInfo().getClientHost();
            }
            else
            {
              clientHost = "UNKNOWN";
            }
          }
          lt.setValue("clientHost", clientHost, 11L);
          lt.setValue("clientaddr", clientAddr, 11L);
          if ((adminUserID != null) && (!(adminUserID.equals("")))) {
            lt.setValue("adminuserid", adminUserID);
          }

          lt.setValue("serverhost", this.mxServer.getServerHost(), 11L);
          lt.setValue("servername", this.mxServer.getName(), 11L);
        }

        userSet.save();
      }
    }

    if ((disconnectType != 0) && (this.users.containsKey(userid)))
    {
      synchronized (this.lockObject)
      {
        SecurityInfo si = (SecurityInfo)this.users.get(userid);
        si.removeSession(maxsessionid);

        if (disconnectType == 2)
        {
          Profile profile = si.getProfile();
          profile.setLogout(true);
          si.refreshProfile(profile);
          this.users.put(userid, si);
        }
        else if (noMoreActiveSessions)
        {
          this.users.remove(userid);
          getMXServer().clearUserInput(si.getUserInfo());
        }
        else
        {
          this.users.put(userid, si);
        }
      }

    }

    return noMoreActiveSessions;
  }

  public String getURL()
  {
    return this.url; }

  public void setURL(String url) {
    this.url = url;
  }

  public boolean isAppService() {
    return false;
  }

  public void restart()
    throws RemoteException
  {
  }

  public boolean isSingletonService()
  {
    return false;
  }

  public int getSessionCounter()
  {
    return this.sessionCounter.getSessionCounter();
  }

  class SessionCounter extends MaximoThread
  {
    int loopFreq = 0;

    int sessionCount = 0;

    public SessionCounter()
    {
      super("SessionCounter");
      setLoopFreq();
    }

    public int getSessionCounter()
    {
      return this.sessionCount;
    }

    private void doCount()
    {
      ConnectionKey conKey = null;
      try
      {
        conKey = SecurityService.this.getSystemUserInfo().getConnectionKey();
        Connection con = SecurityService.this.getDBConnection(conKey);
        Statement statement = con.createStatement();
        statement.execute("select count(distinct clienthost) from maxsession where issystem = 0");
        ResultSet rs = statement.getResultSet();
        if (rs.next())
        {
          this.sessionCount = rs.getInt(1);
        }
      }
      catch (Exception e)
      {
        FixedLoggers.MAXIMOLOGGER.error(e);
      }
      finally
      {
        if (conKey != null)
        {
          SecurityService.this.freeDBConnection(conKey);
          conKey = null;
        }
      }
    }

    private void setLoopFreq()
    {
      try
      {
        if (SecurityService.this.mxServer != null)
          this.loopFreq = new Integer(SecurityService.this.mxServer.getConfig().getProperty("mxe.ClientCountMinutes")).intValue();
        if (this.loopFreq < 15)
          this.loopFreq = 15;
      }
      catch (Exception e)
      {
        this.loopFreq = 15;
      }

      this.loopFreq *= 60000;
    }

    public void run()
    {
      while (!(isMarkedForShutDown()))
      {
        try
        {
          sleep(this.loopFreq);
        }
        catch (InterruptedException e)
        {
          if (isMarkedForShutDown())
          {
            return;
          }
        }

        doCount();

        setLoopFreq();
      }
    }
  }

  class UserMonitor extends MaximoThread
  {
    long TIMEOUT = 1800000L;

    String userMonitorProp = null;

    public UserMonitor()
    {
      super("UserMonitor");
      try
      {
        if (SecurityService.this.mxServer != null)
          this.userMonitorProp = SecurityService.this.mxServer.getConfig().getProperty("mxe.usermonitor.timeout");
        if (this.userMonitorProp != null)
          this.TIMEOUT = (new Long(this.userMonitorProp).longValue() * 60000L);
      }
      catch (Exception e)
      {
        this.TIMEOUT = 1800000L;
      }
    }

    public void run()
    {
      while (!(isMarkedForShutDown()))
      {
        try
        {
          sleep(60000L);
        }
        catch (InterruptedException e)
        {
          if (isMarkedForShutDown())
          {
            return;
          }
        }

        long currentTime = new Date().getTime();
        MboSetRemote sessSet = null;

        ConnectionKey conKey = null;
        Connection con = null;
        try
        {
          conKey = SecurityService.this.getSystemUserInfo().getConnectionKey();
          con = SecurityService.this.getDBConnection(conKey);
        }
        catch (Exception e) {
        }
        String sessWhere = " where servername = '" + SecurityService.this.mxServer.getName() + "' and serverhost = '" + SecurityService.this.mxServer.getServerHost() + "'";

        for (Enumeration e = SecurityService.this.users.elements(); e.hasMoreElements(); )
        {
          try
          {
            SecurityService.SecurityInfo si = (SecurityService.SecurityInfo)e.nextElement();
            UserInfo ui = si.getUserInfo();
            String userid = ui.getUserName();
            boolean noMoreActiveSessions = false;
            HashSet sessToRemove = new HashSet();

            Map sessionsSynch = si.getSessions();
            Set keySet = sessionsSynch.keySet();

            synchronized (sessionsSynch)
            {
              Iterator i = keySet.iterator();

              while (i.hasNext())
              {
                Long maxsessionid = (Long)i.next();
                Date lastUsed = (Date)sessionsSynch.get(maxsessionid);

                sessSet = updateLastActivity(sessSet, maxsessionid.longValue(), lastUsed, 0);

                if (lastUsed.getTime() < currentTime - this.TIMEOUT)
                {
                  noMoreActiveSessions = SecurityService.this.removeUserFromCache(userid, maxsessionid.longValue(), 0, null);
                  sessToRemove.add(maxsessionid);
                }
              }
            }

            if (noMoreActiveSessions)
            {
              synchronized (SecurityService.this.lockObject)
              {
                SecurityService.this.users.remove(userid);
                SecurityService.this.getMXServer().clearUserInput(ui);
              }
            }
            else if (!(sessToRemove.isEmpty()))
            {
              synchronized (SecurityService.this.lockObject)
              {
                Iterator i2 = sessToRemove.iterator();
                while (i2.hasNext())
                {
                  si.removeSession(((Long)i2.next()).longValue());
                }
                SecurityService.this.users.put(userid, si);
              }

            }

            if (SecurityService.this.users.containsKey(userid))
            {
              Profile profile = si.getProfile();
              if ((!(profile.getLogout())) && (con != null))
              {
                Statement s = null;
                boolean adminLogout = false;
                try
                {
                  s = con.createStatement();
                  String tempWhere = sessWhere + " and userid = '" + userid + "'";
                  ResultSet rs = s.executeQuery("select adminlogout from maxsession " + tempWhere);

                  if (rs.next())
                    adminLogout = rs.getInt(1) == 1;
                }
                finally
                {
                  if (s != null) {
                    s.close();
                  }
                }
                if (adminLogout)
                {
                  profile.setLogout(true);
                  si.refreshProfile(profile);
                  SecurityService.this.users.put(userid, si);
                }

              }

            }

          }
          catch (Exception ee)
          {
            MXLogger secLogger = MXLoggerFactory.getLogger("maximo.service.SECURITY");

            if ((secLogger != null) && (secLogger.isErrorEnabled())) {
              secLogger.error(ee);
            }
          }
        }

        if (conKey != null)
          SecurityService.this.freeDBConnection(conKey);
      }
    }

    private MboSetRemote updateLastActivity(MboSetRemote sessSet, long maxsessionid, Date lastActivity, int numAttempts)
      throws MXException, RemoteException
    {
      if (sessSet == null)
        sessSet = SecurityService.this.mxServer.getMboSet("MAXSESSION", SecurityService.this.getSystemUserInfo());
      SqlFormat sqf = new SqlFormat("maxsessionuid = :1 and (lastactivity is null or lastactivity != :2)");
      sqf.setLong(1, maxsessionid);
      sqf.setTimestamp(2, lastActivity);
      sessSet.setWhere(sqf.format());
      sessSet.reset();
      if (!(sessSet.isEmpty()))
      {
        MboRemote sessMbo = sessSet.getMbo(0);
        try
        {
          sessMbo.setValue("lastactivity", lastActivity, 11L);
          sessSet.save();
        }
        catch (MXRowUpdateException e)
        {
          if (numAttempts == 0)
          {
            return updateLastActivity(sessSet, maxsessionid, lastActivity, 1);
          }
        }
      }

      return sessSet;
    }
  }

  class SecurityInfo
  {
    UserInfo userInfo = null;

    Profile profile = null;

    Date connectDate = new Date();

    Date lastUsed = new Date();

    HashMap sessions = new HashMap();

    public SecurityInfo(UserInfo ui)
    {
      this.userInfo = ui;
      getSessions().put(new Long(ui.getUserLoginDetails().getMaxSessionID()), new Date());
    }

    public UserInfo getUserInfo()
    {
      return this.userInfo;
    }

    public void setLastUsed(Date date)
    {
      this.lastUsed = date;
      Map sessionsSynch = getSessions();
      Set s = sessionsSynch.keySet();

      synchronized (sessionsSynch)
      {
        Iterator i = s.iterator();
        while (i.hasNext())
        {
          sessionsSynch.put((Long)i.next(), date);
        }
      }
    }

    public void setLastUsed(Date date, long maxsessionid)
    {
      this.lastUsed = date;
      getSessions().put(new Long(maxsessionid), date);
    }

    public Date getLastUsed()
    {
      return this.lastUsed;
    }

    public Date getLastUsed(long maxsessionid)
    {
      Long longID = new Long(maxsessionid);
      if (getSessions().containsKey(longID)) {
        return ((Date)getSessions().get(longID));
      }
      return this.lastUsed;
    }

    public Map getSessions()
    {
      return Collections.synchronizedMap(this.sessions);
    }

    public void removeSession(long maxsessionid)
    {
      getSessions().remove(new Long(maxsessionid));
    }

    public Profile getProfile()
      throws MXException, RemoteException
    {
      if (this.profile == null)
      {
        this.profile = new Profile(SecurityService.this.mxServer, SecurityService.this.getSystemUserInfo(), this.userInfo, false);
      }

      return this.profile;
    }

    public void refreshProfile(Profile newProfile)
    {
      this.profile = newProfile;
    }

    public void refreshUserInfo(UserInfo newUserInfo)
    {
      this.userInfo = newUserInfo;
    }
  }
}

/* Location:           C:\maxdev\applications\maximo\businessobjects\classes\
 * Qualified Name:     psdi.security.SecurityService
 * Java Class Version: 1.3 (47.0)
 * JD-Core Version:    0.5.3
 */