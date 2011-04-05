package psdi.app.signature;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Properties;
import psdi.app.signature.virtual.UserProfileHierarchySet;
import psdi.configure.UpgConstants;
import psdi.mbo.Mbo;
import psdi.mbo.MboRemote;
import psdi.mbo.MboServerInterface;
import psdi.mbo.MboSet;
import psdi.mbo.MboSetInfo;
import psdi.mbo.MboSetRemote;
import psdi.mbo.MboValue;
import psdi.mbo.MboValueInfo;
import psdi.mbo.SqlFormat;
import psdi.mbo.StatefulMbo;
import psdi.mbo.StatusHandler;
import psdi.mbo.Translate;
import psdi.security.ConnectionKey;
import psdi.security.Profile;
import psdi.security.ProfileRemote;
import psdi.security.SecurityServiceRemote;
import psdi.security.UserInfo;
import psdi.server.MXServer;
import psdi.server.MXServerInfo;
import psdi.server.MaxVarServiceRemote;
import psdi.util.MXAccessException;
import psdi.util.MXApplicationException;
import psdi.util.MXApplicationYesNoCancelException;
import psdi.util.MXException;
import psdi.util.MXFormat;
import psdi.util.MXSystemException;
import psdi.util.MaxType;
import psdi.util.logging.MXLogger;

public class MaxUser extends StatefulMbo
  implements MaxUserRemote, UpgConstants
{
  SignatureService sigserv = null;

  int dbIn = 999;

  boolean deleteThisUser = false;

  private String origDbUserID = null;

  boolean deleteNativeUser = false;

  boolean dupMbo = false;

  private boolean wrotePasswordHistory = false;

  private boolean addedGroupUser = false;

  HashMap dialogStates = new HashMap();

  HiddenValueSet hiddenValues = null;

  public MaxUser(MboSet ms)
    throws MXException, RemoteException
  {
    super(ms);
  }

  public String getProcess()
  {
    return "USERSTATUS";
  }

  protected MboSetRemote getStatusHistory()
    throws MXException, RemoteException
  {
    return getMboSet("MAXUSERSTATUS");
  }

  protected StatusHandler getStatusHandler()
  {
    return new MaxUserStatusHandler(this);
  }

  public String getStatusListName()
  {
    return "MAXUSERSTATUS";
  }

  public void init()
    throws MXException
  {
    super.init();
    try
    {
      String dbVendor = MXServer.getMXServer().getDatabaseProductName().toUpperCase();
      if (dbVendor.indexOf("ORACLE") >= 0)
        this.dbIn = 1;
      else if (dbVendor.indexOf("MICROSOFT") >= 0)
        this.dbIn = 2;
      else if (dbVendor.indexOf("DB2") >= 0)
        this.dbIn = 3;
      else {
        this.dbIn = 999;
      }
      this.sigserv = ((SignatureService)getMboServer());

      setValue("synchpasswords", false, 11L);

      if (!(toBeAdded()))
      {
        this.origDbUserID = getString("databaseuserid");
        setFieldFlag("passwordinput", 8L, false);
        setFieldFlag("passwordcheck", 8L, false);
        setFieldFlag("passwordinput", 7L, true);
        setFieldFlag("passwordcheck", 7L, true);
      }
      else
      {
        setFieldFlag("passwordinput", 8L, true);
        setFieldFlag("passwordcheck", 8L, true);
      }

      if (isNull("databaseuserid"))
      {
        setFieldFlag("synchpasswords", 7L, true);
        setFieldFlag("dbpassword", 7L, true);
        setFieldFlag("dbpasswordcheck", 7L, true);
      }
    }
    catch (Exception e)
    {
      if (getMboLogger().isErrorEnabled()) {
        getMboLogger().error(e);
      }
    }
    if (!(toBeAdded()))
    {
      String[] roFields = { "USERID", "STATUS", "PWEXPIRATION" };
      setFieldFlag(roFields, 7L, true);
    }
    else
    {
      setFieldFlag("status", 7L, true);
    }

    String[] ldapReadonly = { "passwordinput", "passwordcheck", "passwordold", "password" };
    try
    {
      if ((getThisMboSet().getApp() != null) && (MXServer.getMXServer().getMXServerInfo().useAppServerSecurity()))
        setFieldFlag(ldapReadonly, 7L, true);
    }
    catch (Exception e)
    {
      setFieldFlag(ldapReadonly, 7L, true);
    }

    try
    {
      this.hiddenValues = new HiddenValueSet(this, new String[] { "password", "passwordinput", "passwordcheck", "passwordold", "dbpassword", "dbpasswordcheck" });
    }
    catch (RemoteException re)
    {
    }
  }

  public int getDbIn()
  {
    return this.dbIn;
  }

  public void add()
    throws MXException, RemoteException
  {
    super.add();

    setValue("status", getTranslator().toExternalDefaultValue("maxuserstatus", "ACTIVE", this), 11L);
    setValue("type", getTranslator().toExternalDefaultValue("usertype", "TYPE 1", this), 11L);

    setValue("synchpasswords", false, 11L);
    setValue("sysuser", false, 11L);

    setValue("failedlogins", 0, 11L);

    if (!(MXServer.getMXServer().getMXServerInfo().useAppServerSecurity()))
      return;
    setValue("forceexpiration", false, 11L);
    setValue("password", "ABC", 11L);
    setValue("passwordinput", "ABC", 11L);
    setValue("passwordcheck", "ABC", 11L);
  }

  public void delete(long accessModifier)
    throws MXException, RemoteException
  {
    super.delete(accessModifier);

    deleteChildren();
  }

  public void undelete() throws MXException, RemoteException
  {
    super.undelete();

    undeleteChildren();
  }

  public boolean deleteThisUser()
    throws MXException, RemoteException
  {
    return deleteThisUser(0L);
  }

  public void undeleteThisUser() throws MXException, RemoteException
  {
    MaxVarServiceRemote mvs = getMboServer().getMaxVar();

    if (!(mvs.getBoolean("LOGINTRACKING", "")))
    {
      undelete();
    }
    else
    {
      setValue("status", getMboValue("status").getInitialValue().asString(), 11L);
      undeleteChildren();
    }

    this.deleteThisUser = false;
  }

  public boolean deleteThisUser(long accessModifier)
    throws MXException, RemoteException
  {
    if (this.deleteThisUser) {
      return true;
    }
    this.deleteNativeUser = false;

    if ((getUserInfo().isInteractive()) && (!(isNull("databaseuserid"))))
    {
      int userInput = MXApplicationYesNoCancelException.getUserInput("MaxUserDeleteThisUser", MXServer.getMXServer(), getUserInfo());

      switch (userInput)
      {
      case -1:
        throw new MXApplicationYesNoCancelException("MaxUserDeleteThisUser", "signature", "DeleteDBUserQuestion");
      case 16:
        this.deleteNativeUser = false;
        break;
      case 8:
        this.deleteNativeUser = true;
        break;
      case 4:
        return false;
      }
    }

    MaxVarServiceRemote mvs = getMboServer().getMaxVar();

    if (!(mvs.getBoolean("LOGINTRACKING", "")))
    {
      delete(accessModifier);
    }
    else
    {
      setValue("status", getTranslator().toExternalDefaultValue("maxuserstatus", "DELETED", this), 11L);
      deleteChildren();
    }

    this.deleteThisUser = true;

    return true;
  }

  private void deleteChildren()
    throws MXException, RemoteException
  {
    getMboSet("USERPREF").deleteAll(2L);
    getMboSet("GRPREASSIGNAUTH").deleteAll(2L);
    getMboSet("PASSWORDHISTORY").deleteAll(2L);
    getMboSet("USERPURGL").deleteAll(2L);
    getMboSet("GROUPUSER").deleteAll(2L);
    getMboSet("MAXUSERSTATUS").deleteAll(2L);
    getMboSet("LOGINTRACKING").deleteAll(2L);
    getMboSet("QUERY").deleteAll(2L);
    getMboSet("BOOKMARK").deleteAll(2L);

    if ((!(this.deleteNativeUser)) || (isNull("databaseuserid")))
      return;
    deleteDBUser(getString("databaseuserid"));
    setValueNull("databaseuserid", 11L);
  }

  private void undeleteChildren()
    throws MXException, RemoteException
  {
    getMboSet("USERPREF").undeleteAll();
    getMboSet("GRPREASSIGNAUTH").undeleteAll();
    getMboSet("PASSWORDHISTORY").undeleteAll();
    getMboSet("USERPURGL").undeleteAll();
    getMboSet("GROUPUSER").undeleteAll();
    getMboSet("MAXUSERSTATUS").undeleteAll();
    getMboSet("LOGINTRACKING").undeleteAll();
    getMboSet("QUERY").undeleteAll();
    getMboSet("BOOKMARK").undeleteAll();

    if (!(isNull("databaseuserid")))
      return;
    String origDBUser = getMboValue("databaseuserid").getInitialValue().asString();

    if ((origDBUser == null) || (origDBUser.equals(""))) {
      return;
    }

    ConnectionKey conKey = null;
    Connection con = null;
    boolean changedAutoCommit = false;
    boolean origAutoCommit = false;
    try
    {
      conKey = MaxUserSet.getSystemUserInfo().getConnectionKey();
      con = getMboServer().getDBConnection(conKey);
      origAutoCommit = con.getAutoCommit();

      if ((this.dbIn == 2) && 
        (!(origAutoCommit)))
      {
        con.setAutoCommit(true);
        changedAutoCommit = true;
      }

      if (userExistsOnDB(con, origDBUser))
      {
        setValue("databaseuserid", origDBUser, 11L);
      }
    }
    catch (SQLException e)
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      Object[] params = { new Integer(e.getErrorCode()).toString() };
      throw new MXSystemException("system", "sql", params, e);
    }
    catch (Exception e2)
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      throw new MXApplicationException("signature", "dbUpdateMiscError");
    }
    finally
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      if (conKey != null)
        getMboServer().freeDBConnection(conKey);
    }
  }

  public void maxUserCanDelete()
    throws MXException, RemoteException
  {
    if (getString("userid").equals(getUserInfo().getUserName())) {
      throw new MXApplicationException("signature", "cannotDeleteOneself");
    }
    if (getBoolean("sysuser"))
    {
      Object[] params = { getString("userid") };
      throw new MXApplicationException("signature", "cannotDeleteSysUser", params);
    }

    if (activeWorkflowAssignment()) {
      throw new MXApplicationException("signature", "ActiveWF");
    }
    MboSetRemote cronSet = getMboSet("CRONTASKINSTANCE");
    if (!(cronSet.isEmpty())) {
      throw new MXApplicationException("signature", "ActiveCronTask");
    }
    if ((!(MXServer.getMXServer().getMXServerInfo().useAppServerSecurity())) || (!(MXServer.getMXServer().getConfig().getProperty("mxe.LDAPUserMgmt", "1").equals("1")))) {
      return;
    }
    throw new MXAccessException("access", "mxauthenabled");
  }

  public void canDelete()
    throws MXException, RemoteException
  {
    MaxVarServiceRemote mvs = getMboServer().getMaxVar();

    if (mvs.getBoolean("LOGINTRACKING", "")) {
      throw new MXApplicationException("access", "notauthorized");
    }
    maxUserCanDelete();
  }

  public boolean toBeSaved()
    throws RemoteException
  {
    if ((toBeAdded()) && (toBeDeleted())) {
      return super.toBeSaved();
    }
    boolean modifiedAttr = false;
    try
    {
      if (this.hiddenValues.getMboValue("password").isModified())
        modifiedAttr = true;
      if (!(modifiedAttr))
        modifiedAttr = this.hiddenValues.getMboValue("passwordcheck").isModified();
      if (!(modifiedAttr))
        modifiedAttr = this.hiddenValues.getMboValue("dbpassword").isModified();
      if (!(modifiedAttr))
        modifiedAttr = this.hiddenValues.getMboValue("dbpasswordcheck").isModified();
      if ((!(modifiedAttr)) && (!(isNull("newpersonid")))) {
        modifiedAttr = true;
      }
    }
    catch (Exception e)
    {
    }
    if ((modifiedAttr) || (this.deleteThisUser))
    {
      setModified(true);
      return true;
    }

    return super.toBeSaved();
  }

  public boolean userWasDuplicated()
  {
    return this.dupMbo;
  }

  public MboRemote duplicate()
    throws MXException, RemoteException
  {
    MboRemote newUser = copy();
    ((MaxUser)newUser).dupMbo = true;

    MboSetRemote oldGUSet = getMboSet("GROUPUSER");
    MboSetRemote newGUSet = newUser.getMboSet("GROUPUSER");
    oldGUSet.copy(newGUSet);

    MboSetRemote oldGLSet = getMboSet("USERPURGL");
    MboSetRemote newGLSet = newUser.getMboSet("USERPURGL");
    oldGLSet.copy(newGLSet);

    return newUser;
  }

  protected boolean skipCopyField(MboValueInfo mvi)
    throws RemoteException, MXException
  {
    if (mvi.getName().indexOf("USERID") >= 0)
      return true;
    if (mvi.getName().indexOf("PERSONID") >= 0)
      return true;
    if (mvi.getName().indexOf("PASSWORD") >= 0)
      return true;
    if (mvi.getName().indexOf("PSWD") >= 0)
      return true;
    if (mvi.getName().startsWith("PW"))
      return true;
    if (mvi.getName().equalsIgnoreCase("LOGINID")) {
      return true;
    }
    return (mvi.getName().equalsIgnoreCase("FORCEEXPIRATION"));
  }

  public void appValidate()
    throws MXException, RemoteException
  {
    recheckPasswordAuthority();

    if (toBeAdded())
    {
      if (!(MXServer.getMXServer().getMXServerInfo().useAppServerSecurity()))
      {
        if ((this.hiddenValues.getMboValue("password").isNull()) || (this.hiddenValues.getMboValue("passwordcheck").isNull()))
        {
          throw new MXApplicationException("signature", "passwordIsRequired");
        }

        if (!(isPasswordValid()))
        {
          throw new MXApplicationException("signature", "invalidPassword");
        }
      }
    }
    else if ((!(toBeDeleted())) && (!(this.deleteThisUser)))
    {
      if ((((this.hiddenValues.getMboValue("password").isModified()) || (this.hiddenValues.getMboValue("passwordcheck").isModified()))) && (((!(this.hiddenValues.getMboValue("password").isNull())) || (!(this.hiddenValues.getMboValue("passwordcheck").isNull())))))
      {
        if (!(canChangePassword()))
        {
          throw new MXApplicationException("signature", "cannotChangePassword");
        }
        if (!(isPasswordValid()))
        {
          throw new MXApplicationException("signature", "invalidPassword");
        }
      }

      if (!(isNull("databaseuserid")))
      {
        if (getBoolean("synchpasswords"))
        {
          this.hiddenValues.setValue("dbpassword", this.hiddenValues.getMboValue("password").getString(), 11L);
          this.hiddenValues.setValue("dbpasswordcheck", this.hiddenValues.getMboValue("passwordcheck").getString(), 11L);
        }

        if ((this.hiddenValues.getMboValue("dbpassword").isNull()) && (!(this.hiddenValues.getMboValue("dbpasswordcheck").isNull())))
          throw new MXApplicationException("signature", "passwordIsRequired");
        if ((!(this.hiddenValues.getMboValue("dbpassword").isNull())) && (this.hiddenValues.getMboValue("dbpasswordcheck").isNull())) {
          throw new MXApplicationException("signature", "passwordIsRequired");
        }
        if ((!(this.hiddenValues.getMboValue("dbpassword").isNull())) && (!(this.hiddenValues.getMboValue("dbpassword").getString().equals(this.hiddenValues.getMboValue("dbpasswordcheck").getString())))) {
          throw new MXApplicationException("signature", "pswdNEpswdcheck");
        }
        if (!(this.hiddenValues.getMboValue("dbpassword").isNull()))
          isDBPasswordValid();
      }
      else if ((!(this.hiddenValues.getMboValue("dbpassword").isNull())) || (!(this.hiddenValues.getMboValue("dbpasswordcheck").isNull())))
      {
        this.hiddenValues.setValueNull("dbpassword", 11L);
        this.hiddenValues.setValueNull("dbpasswordcheck", 11L);
      }

    }

    if ((!(isNull("pwhintquestion"))) && (isNull("pwhintanswer")))
    {
      Object[] params = { getMboValue("pwhintquestion").getColumnTitle(), getMboValue("pwhintanswer").getColumnTitle() };
      throw new MXApplicationException("configure", "ifAthenB", params);
    }
    if ((!(isNull("pwhintanswer"))) && (isNull("pwhintquestion")))
    {
      Object[] params = { getMboValue("pwhintanswer").getColumnTitle(), getMboValue("pwhintquestion").getColumnTitle() };
      throw new MXApplicationException("configure", "ifAthenB", params);
    }

    if (toBeAdded())
    {
      setExpiration(null);
      writePasswordHistory();
    }
    else if ((!(toBeDeleted())) && (!(this.deleteThisUser)) && (this.hiddenValues.getMboValue("password").isModified()))
    {
      setExpiration(null);
      writePasswordHistory();
    }
    else if (!(isNull("newpersonid")))
    {
      setValue("personid", getString("newpersonid"), 11L);
    }
    else
    {
      MboSetRemote guSet = getInstanciatedMboSet("GROUPUSER");

      if ((guSet == null) || ((guSet.count(2) <= 0) && (guSet.count(4) <= 0)))
        return;
      Date startDate = null;
      MboSetRemote pwHistSet = getMboSet("PASSWORDHISTORY");
      if (!(pwHistSet.isEmpty())) {
        startDate = pwHistSet.latestDate("changedate");
      }

      setExpiration(startDate);
    }
  }

  public void recheckPasswordAuthority()
    throws MXException, RemoteException
  {
    String checkApp = null;
    String checkOption = null;
    String checkOption2 = null;
    String app = getThisMboSet().getApp();

    if (toBeAdded())
    {
      if ((app != null) && (app.equals("USER")))
      {
        checkApp = "USER";
        checkOption = "INSERT";
        checkOption2 = "SAVE";
      }
      else
      {
        MboRemote owner = getThisMboSet().getOwner();
        String ownerApp = null;
        if (owner != null)
        {
          ownerApp = owner.getThisMboSet().getApp();
          if ((owner.getName().equals("ADDUSER")) && (ownerApp != null) && (ownerApp.equals("SELFREG")))
          {
            checkApp = "SELFREG";
            checkOption = "INSERT";
            checkOption2 = "SAVE";
          }
        }
      }

      if (checkApp == null)
      {
        checkApp = "USER";
        checkOption = "INSERT";
        checkOption2 = "SAVE";
      }
    }
    else if ((!(this.hiddenValues.getMboValue("passwordinput").isNull())) || (!(this.hiddenValues.getMboValue("passwordcheck").isNull())) || (this.hiddenValues.getMboValue("password").isModified()))
    {
      String thisApp = getThisMboSet().getApp();
      MboSetRemote changePswdsSet = getInstanciatedMboSet("MYPROFILECHANGEPASSWORDS");

      if ((thisApp != null) && (thisApp.equals("USER")))
      {
        checkApp = "USER";
        checkOption = "PWCHANGE";
      }
      else if ((thisApp != null) && (thisApp.equals("CHANGEPSWD")))
      {
        checkApp = "CHANGEPSWD";
        checkOption = "SAVE";
      }
      else if ((thisApp == null) && (changePswdsSet != null) && (changePswdsSet.count() == 1))
      {
        checkApp = "STARTCNTR";
        checkOption = "PASSWORD";
      }

      if (checkApp == null)
      {
        checkApp = "USER";
        checkOption = "PWCHANGE";
      }
    }

    if (checkApp == null)
      return;
    ProfileRemote profile = getProfile();
    if (!(profile.getAppOptionAuth(checkApp, checkOption, null)))
    {
      Object[] args = { checkApp + "-" + checkOption, getName() };
      throw new MXAccessException("access", "method", args);
    }

    if ((checkOption2 == null) || 
      (profile.getAppOptionAuth(checkApp, checkOption2, null)))
      return;
    Object[] args = { checkApp + "-" + checkOption2, getName() };
    throw new MXAccessException("access", "method", args);
  }

  public void save()
    throws MXException, RemoteException
  {
    if ((!(this.hiddenValues.getMboValue("dbpassword").isNull())) && (!(isNull("databaseuserid"))))
    {
      changeDBPassword();
    }

    super.save();
  }

  public boolean isActive()
    throws MXException, RemoteException
  {
    Translate tr = getTranslator();
    return tr.toInternalString("MAXUSERSTATUS", getString("status")).equalsIgnoreCase("ACTIVE");
  }

  public boolean isInactive()
    throws MXException, RemoteException
  {
    Translate tr = getTranslator();
    return tr.toInternalString("MAXUSERSTATUS", getString("status")).equalsIgnoreCase("INACTIVE");
  }

  void setActive(String memo)
    throws MXException, RemoteException
  {
    String status = getTranslator().toExternalDefaultValue("MAXUSERSTATUS", "ACTIVE", this);
    changeStatus(status, MXServer.getMXServer().getDate(), memo);
  }

  public boolean isBlocked()
    throws MXException, RemoteException
  {
    Translate tr = getTranslator();
    return tr.toInternalString("MAXUSERSTATUS", getString("status")).equalsIgnoreCase("BLOCKED");
  }

  void setBlocked(String memo)
    throws MXException, RemoteException
  {
    String status = getTranslator().toExternalDefaultValue("MAXUSERSTATUS", "BLOCKED", this);
    changeStatus(status, MXServer.getMXServer().getDate(), memo);
  }

  public boolean isDeleted()
    throws MXException, RemoteException
  {
    Translate tr = getTranslator();
    return tr.toInternalString("MAXUSERSTATUS", getString("status")).equalsIgnoreCase("DELETED");
  }

  /** @deprecated */
  public void addLoginTracking(boolean attemptResult)
    throws MXException, RemoteException
  {
    addLoginTracking(attemptResult, null, null, null, null);
  }

  /** @deprecated */
  public void addLoginTracking(boolean attemptResult, String app, String reason, String transid, String[] keyvalue)
    throws MXException, RemoteException
  {
    addLoginTracking(attemptResult, app, reason, transid, keyvalue, null, null);
  }

  /** @deprecated */
  public void addLoginTracking(boolean attemptResult, String app, String reason, String transid, String[] keyvalue, String ownerTable, String ownerId)
    throws MXException, RemoteException
  {
    String attemptResultStr = (attemptResult) ? getTranslator().toExternalDefaultValue("ATTEMPTRESULT", "SUCCESS", this) : getTranslator().toExternalDefaultValue("ATTEMPTRESULT", "FAILED", this);

    MboRemote loginMbo = addLoginTracking(attemptResultStr);

    if (loginMbo == null) {
      return;
    }

    if (app != null)
      loginMbo.setValue("app", app);
    if (reason != null)
      loginMbo.setValue("reason", reason);
    if (transid != null)
      loginMbo.setValue("transid", transid);
    if ((keyvalue != null) && (keyvalue.length > 0) && (keyvalue.length <= 7))
    {
      for (int xx = 0; xx < keyvalue.length; ++xx)
      {
        if (keyvalue[xx] == null)
          continue;
        String attrNum = new Integer(xx + 1).toString();
        String attrName = "keyvalue" + attrNum;
        loginMbo.setValue(attrName, keyvalue[xx]);
      }
    }

    if ((ownerTable != null) && (ownerTable.length() > 0))
      loginMbo.setValue("ownertable", ownerTable);
    if ((ownerId != null) && (ownerId.length() > 0)) {
      loginMbo.setValue("ownerid", new Long(ownerId).longValue());
    }
    loginMbo.setValue("maxsessionuid", getUserInfo().getMaxSessionID(), 11L);
    loginMbo.setValue("clientHost", getUserInfo().getClientHost(), 11L);
    String[] remoteNames = getUserInfo().getRemoteNames();
    if (remoteNames == null)
      return;
    loginMbo.setValue("clientaddr", remoteNames[0], 11L);
  }

  public MboRemote addLoginTracking(String attemptResult)
    throws MXException, RemoteException
  {
    return addLoginTracking(attemptResult, true);
  }

  public MboRemote addLoginTracking(String attemptResult, boolean updateStatus)
    throws MXException, RemoteException
  {
    MaxVarServiceRemote mvs = getMboServer().getMaxVar();

    if (!(mvs.getBoolean("LOGINTRACKING", ""))) {
      return null;
    }
    MboSetRemote personSet = getMboSet("PERSON");

    MboSetRemote loginSet = getMboSet("LOGINTRACKING");
    MboRemote loginMbo = loginSet.add(2L);

    loginMbo.setValue("userid", getString("userid"));
    loginMbo.setValue("loginid", getString("loginid"));
    loginMbo.setValue("attemptresult7", attemptResult);

    if ((!(personSet.isEmpty())) && (!(personSet.getMbo(0).isNull("displayname"))))
    {
      loginMbo.setValue("name", personSet.getMbo(0).getString("displayname"));
    }

    int currFailedLogins = 0;

    if (!(isNull("failedLogins"))) {
      currFailedLogins = getInt("failedlogins");
    }
    if (getTranslator().toInternalString("ATTEMPTRESULT", attemptResult).equalsIgnoreCase("SUCCESS"))
    {
      if (currFailedLogins > 0)
      {
        setValue("failedlogins", 0, 11L);
      }
      if ((!(isActive())) && (updateStatus))
      {
        setActive(null);
      }
      loginMbo.setValue("attemptresult", true);
    }
    else if (getTranslator().toInternalString("ATTEMPTRESULT", attemptResult).equalsIgnoreCase("FAILED"))
    {
      setValue("failedlogins", currFailedLogins + 1, 11L);
      int maxvarLoginAttempts = mvs.getInt("LOGINATTEMPTS", "");

      if ((currFailedLogins + 1 >= maxvarLoginAttempts) && (updateStatus))
      {
        setBlocked(null);
      }
      loginMbo.setValue("attemptresult", false);
    }
    else {
      loginMbo.setValue("attemptresult", true);
    }
    return loginMbo;
  }

  public MboRemote addMaxSession()
    throws MXException, RemoteException
  {
    MboSetRemote sessSet = getMboSet("MAXSESSION");
    sessSet.reset();
    MboRemote sessMbo = sessSet.add(2L);
    return sessMbo;
  }

  public boolean isPasswordValid()
    throws MXException, RemoteException
  {
    if (this.hiddenValues.getMboValue("password").isNull()) {
      throw new MXApplicationException("signature", "pswdIsNull");
    }
    if (this.hiddenValues.getMboValue("passwordcheck").isNull())
    {
      Object[] params = { getMboValue("passwordcheck").getColumnTitle() };
      throw new MXApplicationException("signature", "pswdcheckIsNull", params);
    }

    String password = this.hiddenValues.getMboValue("password").getString();

    if (!(password.equals(this.hiddenValues.getMboValue("passwordcheck").getString()))) {
      throw new MXApplicationException("signature", "pswdNEpswdcheck");
    }
    if (!(canChangePassword())) {
      throw new MXApplicationException("signature", "cannotChangePassword");
    }
    SignatureServiceRemote sigServ = (SignatureServiceRemote)getMboServer();
    sigServ.validatePassword(getString("userid"), this.hiddenValues.getMboValue("password").getString(), getUserInfo());

    if (!(toBeAdded()))
    {
      SecurityServiceRemote secServ = (SecurityServiceRemote)MXServer.getMXServer().lookup("SECURITY");

      if (secServ.isUser(getUserInfo(), getString("loginid"), this.hiddenValues.getMboValue("password").getString())) {
        throw new MXApplicationException("signature", "pswdNewEqualsOld");
      }
    }
    return true;
  }

  public void isDBPasswordValid()
    throws MXException, RemoteException
  {
    if ((this.hiddenValues.getMboValue("dbpassword").isNull()) || (isNull("databaseuserid"))) {
      return;
    }
    String fooID = "MAXIMOFOOBARUSER";
    try
    {
      try
      {
        deleteDBUser(fooID);
      }
      catch (Exception e1) {
      }
      addDBUser(fooID);
      deleteDBUser(fooID);
    }
    catch (Exception e)
    {
      MboValueInfo mvi = getMboSetInfo().getMboValueInfo("DBPASSWORD");
      Object[] params = { mvi.getTitle(), "" };
      throw new MXApplicationException("system", "notvalid", params);
    }
  }

  public final void addDBUser(String dbuserid)
    throws MXException, RemoteException
  {
    String schemaOwner = this.sigserv.getSchemaOwner();
    ConnectionKey conKey = null;
    Connection con = null;
    boolean changedAutoCommit = false;
    boolean origAutoCommit = false;
    try
    {
      conKey = MaxUserSet.getSystemUserInfo().getConnectionKey();
      con = getMboServer().getDBConnection(conKey);

      if ((this.dbIn == 1) && (!(userExistsOnDB(con, dbuserid))))
      {
        Statement statement = con.createStatement();
        Statement statement2 = null;
        Statement statement3 = null;
        ResultSet rs = null;

        statement.execute("select default_tablespace, temporary_tablespace  from USER_USERS where username = '" + schemaOwner + "'");

        rs = statement.getResultSet();
        if (rs.next())
        {
          String defSpace = rs.getString(1);
          String tempSpace = rs.getString(2);
          statement2 = con.createStatement();
          statement2.execute("create user " + dbuserid + " identified by " + this.hiddenValues.getMboValue("dbpassword").getString() + " quota unlimited on " + defSpace + " temporary tablespace " + tempSpace + " default tablespace " + defSpace);

          statement3 = con.createStatement();
          statement3.execute("grant create session to " + dbuserid);
        }
        else
        {
          rs.close();
          statement.close();
          if (statement2 != null)
            statement2.close();
          if (statement3 != null)
            statement3.close();
          throw new MXApplicationException("signature", "dbUpdateError");
        }
      }
      else if (this.dbIn == 2)
      {
        origAutoCommit = con.getAutoCommit();
        if (!(origAutoCommit))
        {
          con.setAutoCommit(true);
          changedAutoCommit = true;
        }

        Statement statement = con.createStatement();
        Statement statement2 = null;
        Statement statement3 = null;
        ResultSet rs = null;
        boolean sysUserExists = false;

        statement.execute("select l.name from master.dbo.syslogins l, sysusers u  where l.name = '" + dbuserid + "' and l.sid = u.sid");

        rs = statement.getResultSet();
        if (rs.next())
        {
          sysUserExists = true;
        }

        if (!(userExistsOnDB(con, dbuserid)))
        {
          con.commit();
          statement2 = con.createStatement();
          statement2.execute("sp_addlogin " + dbuserid + ", " + this.hiddenValues.getMboValue("dbpassword").getString());
        }

        if (!(sysUserExists))
        {
          con.commit();
          statement3 = con.createStatement();
          statement3.execute("sp_adduser " + dbuserid);
        }

        con.commit();
        rs.close();
        statement.close();
        if (statement2 != null)
          statement2.close();
        if (statement3 != null)
          statement3.close();
      }
      else if ((this.dbIn == 3) && (!(userExistsOnDB(con, dbuserid))))
      {
        Statement statement = con.createStatement();
        statement.execute("create user mapping for " + dbuserid + " options ( remote_password '" + this.hiddenValues.getMboValue("dbpassword").getString() + "')");

        statement.close();
      }
    }
    catch (SQLException e)
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      Object[] params = { new Integer(e.getErrorCode()).toString() };
      throw new MXSystemException("system", "sql", params, e);
    }
    catch (Exception e2)
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      throw new MXApplicationException("signature", "dbUpdateMiscError");
    }
    finally
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      if (conKey != null)
        getMboServer().freeDBConnection(conKey);
    }
  }

  public final void deleteDBUser(String deleteID)
    throws MXException, RemoteException
  {
    ConnectionKey conKey = null;
    Connection con = null;
    boolean changedAutoCommit = false;
    boolean origAutoCommit = false;
    try
    {
      conKey = MaxUserSet.getSystemUserInfo().getConnectionKey();
      con = getMboServer().getDBConnection(conKey);
      origAutoCommit = con.getAutoCommit();

      if ((this.dbIn == 2) && 
        (!(origAutoCommit)))
      {
        con.setAutoCommit(true);
        changedAutoCommit = true;
      }

      if (userExistsOnDB(con, deleteID))
      {
        Statement statement = con.createStatement();
        Statement statement2 = null;

        if (this.dbIn == 1)
        {
          statement.execute("drop user " + deleteID);
        }
        else if (this.dbIn == 2)
        {
          con.commit();
          statement.execute("sp_dropuser " + deleteID);
          con.commit();
          try
          {
            statement2 = con.createStatement();
            statement2.execute("sp_droplogin " + deleteID);
            con.commit();
          }
          catch (SQLException e3)
          {
            if ((e3.getErrorCode() != 229) || (e3.getMessage().indexOf("sysjobs") < 0))
              throw e3;
          }
        }
        else if (this.dbIn == 3)
        {
          statement.execute("drop user mapping for " + deleteID);
        }

        statement.close();
        if (statement2 != null)
          statement2.close();
      }
    }
    catch (SQLException e)
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      Object[] params = { new Integer(e.getErrorCode()).toString() };
      throw new MXSystemException("system", "sql", params, e);
    }
    catch (Exception e2)
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      throw new MXApplicationException("signature", "dbUpdateMiscError");
    }
    finally
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      if (conKey != null)
        getMboServer().freeDBConnection(conKey);
    }
  }

  public boolean canChangePassword()
    throws MXException, RemoteException
  {
    return ((getThisMboSet().getApp() == null) || (!(MXServer.getMXServer().getMXServerInfo().useAppServerSecurity())));
  }

  public final void changeDBPassword()
    throws MXException, RemoteException
  {
    ConnectionKey conKey = null;
    Connection con = null;
    boolean changedAutoCommit = false;
    boolean origAutoCommit = false;
    try
    {
      conKey = MaxUserSet.getSystemUserInfo().getConnectionKey();
      con = getMboServer().getDBConnection(conKey);
      Statement statement = con.createStatement();

      if (this.dbIn == 1)
      {
        statement.execute("alter user " + getString("databaseuserid") + " identified by " + this.hiddenValues.getMboValue("dbpassword").getString());
      }
      else if (this.dbIn == 2)
      {
        origAutoCommit = con.getAutoCommit();

        if (!(origAutoCommit))
        {
          con.setAutoCommit(true);
          changedAutoCommit = true;
        }

        statement.execute("sp_password NULL, " + this.hiddenValues.getMboValue("dbpassword").getString() + ", " + getString("databaseuserid"));

        con.commit();
      }
      else if (this.dbIn == 3)
      {
        statement.execute("alter user mapping for " + getString("databaseuserid") + " options ( remote_password '" + this.hiddenValues.getMboValue("dbpassword").getString() + "')");
      }

      statement.close();
    }
    catch (SQLException e)
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      Object[] params = { new Integer(e.getErrorCode()).toString() };
      throw new MXSystemException("system", "sql", params, e);
    }
    catch (Exception e2)
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      throw new MXApplicationException("signature", "dbUpdateMiscError");
    }
    finally
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      if (conKey != null)
        getMboServer().freeDBConnection(conKey);
    }
  }

  public final boolean userExistsOnDB(Connection con, String checkID)
    throws MXException, RemoteException
  {
    if ((checkID == null) || (checkID.equals(""))) {
      return false;
    }
    ConnectionKey conKey = null;
    boolean changedAutoCommit = false;
    boolean origAutoCommit = false;
    try
    {
      if (con == null)
      {
        conKey = MaxUserSet.getSystemUserInfo().getConnectionKey();
        con = getMboServer().getDBConnection(conKey);

        if (this.dbIn == 2)
        {
          origAutoCommit = con.getAutoCommit();
          if (!(origAutoCommit))
          {
            con.setAutoCommit(true);
            changedAutoCommit = true;
          }
        }
      }

      Statement statement = con.createStatement();

      if (this.dbIn == 1)
      {
        statement.execute("Select USERNAME from ALL_USERS  where USERNAME = '" + checkID + "'");
      }
      else if (this.dbIn == 2)
      {
        con.commit();

        statement.execute("select name from master.dbo.syslogins  where name = '" + checkID + "'");
      }
      else if (this.dbIn == 3)
      {
        statement.execute("select grantee from syscat.dbauth  where grantee = '" + checkID + "'");
      }
      else
      {
        if (changedAutoCommit) {
          con.setAutoCommit(origAutoCommit);
        }
        return false;
      }

      ResultSet rs = statement.getResultSet();

      if (rs.next())
      {
        try
        {
          rs.close();
          statement.close();
          if (changedAutoCommit) {
            con.setAutoCommit(origAutoCommit);
          }
          return true;
        }
        catch (SQLException e2)
        {
          if (changedAutoCommit) {
            con.setAutoCommit(origAutoCommit);
          }
          return true;
        }
      }
      rs.close();
      statement.close();
    }
    catch (SQLException e)
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      Object[] params = { new Integer(e.getErrorCode()).toString() };
      throw new MXSystemException("system", "sql", params, e);
    }
    finally
    {
      try
      {
        if (changedAutoCommit)
          con.setAutoCommit(origAutoCommit);
      }
      catch (Exception ee) {
      }
      if (conKey != null) {
        getMboServer().freeDBConnection(conKey);
      }
    }
    return false;
  }

  void setExpiration(Date startDate)
    throws MXException, RemoteException
  {
    setValueNull("pwexpiration", 11L);

    int duration = 0;

    ProfileRemote profile = new Profile(MXServer.getMXServer(), MaxUserSet.getSystemUserInfo(), this);
    duration = profile.getPwDuration();

    if (duration <= 0) {
      return;
    }
    if (startDate == null) {
      startDate = MXServer.getMXServer().getDate();
    }
    Calendar date = new GregorianCalendar();
    date.setTime(startDate);
    date.add(5, duration);

    setValue("pwexpiration", date.getTime(), 2L);
  }

  boolean showPasswordWarning()
    throws MXException, RemoteException
  {
    int profileDays = 0;

    if (getUserInfo().getUserName().equals(getString("userid")))
    {
      profileDays = getProfile().getPwWarning();
    }
    else
    {
      ProfileRemote profile = new Profile(MXServer.getMXServer(), MaxUserSet.getSystemUserInfo(), this);
      profileDays = profile.getPwWarning();
    }

    int actualDays = passwordWillExpire();

    return (actualDays <= profileDays);
  }

  boolean hasPasswordExpired()
    throws MXException, RemoteException
  {
    if (getBoolean("forceexpiration")) {
      return true;
    }
    int actualDays = passwordWillExpire();

    return (actualDays < 0);
  }

  boolean testForcePasswordChange()
    throws MXException, RemoteException
  {
    return (getBoolean("forceexpiration"));
  }

  int passwordWillExpire()
    throws MXException, RemoteException
  {
    if (isNull("pwexpiration")) {
      return 999;
    }
    Date expDate = getDate("pwexpiration");
    Date today = MXServer.getMXServer().getDate();

    if (expDate.before(today)) {
      return -1;
    }
    expDate = MXFormat.getDateOnly(expDate);
    today = MXFormat.getDateOnly(today);

    long diffMilli = expDate.getTime() - today.getTime();
    double diffDays = diffMilli / 86400000.0D;

    return (int)diffDays;
  }

  private void writePasswordHistory()
    throws MXException, RemoteException
  {
    if (this.wrotePasswordHistory) {
      return;
    }
    MboSetRemote histSet = getMboSet("PASSWORDHISTORY");
    MboRemote histMbo = histSet.add();
    histMbo.setValue("password", this.hiddenValues.getMboValue("password").getBytes(), 11L);

    this.wrotePasswordHistory = true;
  }

  public void addGroupUser()
    throws MXException, RemoteException
  {
    if (this.addedGroupUser) {
      return;
    }
    if (!(toBeAdded())) {
      return;
    }
    MboSetRemote guSet = getMboSet("GROUPUSER");
    MboRemote guMbo = guSet.add(2L);
    guMbo.setValue("userid", getString("userid"), 11L);
    guMbo.setValue("groupname", this.sigserv.getNewUserGroup(getUserInfo()), 11L);
    guMbo.setFieldFlag("userid", 7L, true);
    guMbo.setFieldFlag("groupname", 7L, true);

    this.addedGroupUser = true;
  }

  private boolean activeWorkflowAssignment()
    throws MXException, RemoteException
  {
    String statuses = getTranslator().toExternalList("WFASGNSTATUS", "ACTIVE");

    SqlFormat sqf = new SqlFormat(getUserInfo(), "ownertable = 'MAXUSER' and ownerid = :1 and assignstatus in (" + statuses + ")");
    sqf.setObject(1, "WFASSIGNMENT", "OWNERID", getString("maxuserid"));

    MboSetRemote wfaSet = getMboSet("$tempWFA", "WFASSIGNMENT", sqf.format());
    return (!(wfaSet.isEmpty()));
  }

  public MboRemote createPersonMbo(String personID, MboSetRemote personSet)
    throws MXException, RemoteException
  {
    MboRemote personMbo = null;

    if (!(personSet.isEmpty())) {
      personSet.clear();
    }
    personMbo = personSet.add();
    personMbo.setValue("personid", personID, 11L);

    return personMbo;
  }

  public void openMainRecordDialog(String id)
    throws MXException, RemoteException
  {
    id = id.toLowerCase();

    if ((!(id.equals("grpassign"))) && (!(id.equals("chgperson"))) && (!(id.equals("pwhint")))) {
      return;
    }
    this.dialogStates = new HashMap();
    this.dialogStates.put(id, new Boolean(isModified()));

    if (!(id.equals("pwhint")))
      return;
    this.dialogStates.put("pwhintquestion", getString("pwhintquestion"));
    this.dialogStates.put("pwhintanswer", getString("pwhintanswer"));
  }

  public void cancelMainRecordDialog(String id)
    throws MXException, RemoteException
  {
    id = id.toLowerCase();

    if ((!(this.dialogStates.containsKey(id))) || ((!(id.equals("grpassign"))) && (!(id.equals("chgperson"))) && (!(id.equals("pwhint"))))) {
      return;
    }
    if (id.equals("grpassign"))
    {
      getMboSet("GRPREASSIGNAUTH").reset();
    }
    else if (id.equals("chgperson"))
    {
      setValueNull("newpersonid", 11L);
    }
    else if (id.equals("pwhint"))
    {
      setValue("pwhintquestion", (String)this.dialogStates.get("pwhintquestion"), 11L);
      setValue("pwhintanswer", (String)this.dialogStates.get("pwhintanswer"), 11L);
    }

    boolean origModified = ((Boolean)this.dialogStates.get(id)).booleanValue();
    if (origModified)
      return;
    setModified(origModified);
    if (getThisMboSet().count() == 1)
      ((MaxUserSet)getThisMboSet()).setToBeSaved(false);
  }

  public void clearUserProfileHierarchySet()
    throws MXException, RemoteException
  {
    MboSetRemote hierSet = getInstanciatedMboSet("UP_TREE");
    if (hierSet == null)
      return;
    ((UserProfileHierarchySet)hierSet).clearProfile(getString("userid"));
    hierSet.clear();
  }

  public boolean showProfileWarning()
    throws MXException, RemoteException
  {
    MboSetRemote personSet = getMboSet("PERSON");
    if (personSet.isEmpty()) {
      return false;
    }
    Mbo person = (Mbo)personSet.getMbo(0);

    if ((person.getMboValue("language").isModified()) || (person.getMboValue("locale").isModified()) || (person.getMboValue("timezone").isModified()))
    {
      SqlFormat sqf = new SqlFormat(getUserInfo(), "servername = :1 and serverhost = :2 and userid = :3");
      sqf.setObject(1, "MAXSESSION", "SERVERNAME", MXServer.getMXServer().getName());
      sqf.setObject(2, "MAXSESSION", "SERVERHOST", MXServer.getMXServer().getServerHost());
      sqf.setObject(3, "MAXSESSION", "USERID", getString("userid"));
      MboSetRemote sessSet = getMboSet("$checkSession", "MAXSESSION", sqf.format());
      if (sessSet.count() > 1)
      {
        return true;
      }
    }

    return false;
  }

  public void setValue(String attributeName, String val, long accessModifier)
    throws MXException, RemoteException
  {
    int index = attributeName.indexOf(46);
    attributeName = attributeName.toUpperCase();

    if ((index >= 0) || ((!(attributeName.equals("PASSWORDINPUT"))) && (!(attributeName.equals("PASSWORDCHECK"))) && (!(attributeName.equals("PASSWORDOLD"))) && (!(attributeName.equals("PASSWORD"))) && (!(attributeName.equals("DBPASSWORD"))) && (!(attributeName.equals("DBPASSWORDCHECK")))) || (val.startsWith("FOO")))
    {
      super.setValue(attributeName, val, accessModifier);
      return;
    }
    if (this.hiddenValues == null)
      return;
    this.hiddenValues.setValue(attributeName, val, accessModifier);
  }
}