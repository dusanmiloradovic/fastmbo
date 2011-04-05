package psdi.mbo;

import java.rmi.RemoteException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Vector;
import psdi.security.UserInfo;
import psdi.server.MXServer;
import psdi.util.MXApplicationException;
import psdi.util.MXException;
import psdi.util.MXFormat;
import psdi.util.MXSystemException;

public class SqlFormat
{
  private static final String OLD_PREFIX = "$old_";
  private static final String NEW_PREFIX = "$new_";
  private boolean ignoreUnresolved = false;

  private boolean simpleReplacement = false;

  private boolean validateOnly = false;
  private static final int MAXBINDS = 10;
  String statement;
  private Vector binds = null;

  private Locale locale = Locale.getDefault();

  private TimeZone timeZone = TimeZone.getDefault();

  private String userName = null;

  private MboRemote mboRemote = null;

  private static Properties dbProperties = null;

  private boolean nullBindValueExist = false;
  private boolean hasOr = false;
  private StringFinder strF=new StringFinder() ;

  private final String CANTBINDERROR = "**Error**";

  private MXException encounteredError = null;

  private boolean noSpaces = false;

  static final String[] URL_PREFIX = { "http://", "https://", "ftp://" };

  public boolean hasNullBoundValue()
  {
    return ((this.nullBindValueExist) && (!(this.hasOr)));
  }

  public static Properties getDBProperties()
  {
    return dbProperties;
  }

  public static void setDBProperties(Properties p)
  {
    dbProperties = p;
  }

  public static String getUpperFunction(String param)
  {
    if (dbProperties != null)
    {
      String ucaseFunction = (String)dbProperties.get("mxe.db.format.upper");
      if ((ucaseFunction != null) && (ucaseFunction.length() > 0))
      {
        return ucaseFunction + "(" + param + ")";
      }
    }

    return "{ fn UCASE (" + param + ") }";
  }

  public static String getTimestampFunction(java.util.Date param)
    throws MXException
  {
    String dateString = MXFormat.dateTimeToSQLString(param);

    if (dbProperties != null)
    {
      String dateFunction = (String)dbProperties.get("mxe.db.format.timestamp");
      if ((dateFunction != null) && (dateFunction.length() > 0))
      {
        if (dateFunction.equalsIgnoreCase("none"))
        {
          dateString = MXFormat.dateTimeToString(param);
          return "'" + dateString + "'";
        }

        return dateFunction + "('" + dateString + "')";
      }
    }

    return "{ ts '" + dateString + "' }";
  }

  public static String getTimeFunction(java.util.Date param)
    throws MXException
  {
    String timeString = MXFormat.timeToSQLString(param);

    if (dbProperties != null)
    {
      String timeFunction = (String)dbProperties.get("mxe.db.format.time");
      if ((timeFunction != null) && (timeFunction.length() > 0))
      {
        if (timeFunction.equalsIgnoreCase("none"))
        {
          timeString = MXFormat.timeToString(param);
          return "'" + timeString + "'";
        }

        return timeFunction + "('" + timeString + "')";
      }
    }

    return "{ t '" + timeString + "' }";
  }

  public static String getNullValueFunction(String param, String nullVal)
    throws MXException
  {
    if (dbProperties != null)
    {
      String nvlFunction = (String)dbProperties.get("mxe.db.format.nullvalue");
      if ((nvlFunction != null) && (nvlFunction.length() > 0))
      {
        return nvlFunction + "(" + param + ", " + nullVal + ")";
      }

    }

    return "NVL(" + param + ", " + nullVal + ")";
  }

  public static String getDateFunction(java.util.Date param)
    throws MXException
  {
    String dateString = MXFormat.dateToSQLString(param);

    if (dbProperties != null)
    {
      String dateFunction = (String)dbProperties.get("mxe.db.format.date");
      if ((dateFunction != null) && (dateFunction.length() > 0))
      {
        if (dateFunction.equalsIgnoreCase("none"))
        {
          dateString = MXFormat.dateToString(param);
          return "'" + dateString + "'";
        }

        return dateFunction + "('" + dateString + "')";
      }
    }

    return "{ d '" + dateString + "' }";
  }

  public SqlFormat(String stmt)
  {
    this.locale = Locale.getDefault();
    this.timeZone = TimeZone.getDefault();
    this.statement = stmt;
  }

  public SqlFormat(Locale locale, TimeZone timeZone, String stmt)
  {
    this.locale = locale;
    this.timeZone = timeZone;
    this.statement = stmt;
  }

  public SqlFormat(MboRemote mr, String stmt)
  {
    UserInfo uInfo = null;
    try
    {
      uInfo = mr.getUserInfo();
      this.locale = uInfo.getLocale();
      this.timeZone = uInfo.getTimeZone();
      this.userName = uInfo.getUserName();
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
    }

    this.statement = stmt;
    this.mboRemote = mr;
  }

  public SqlFormat(UserInfo uInfo, String stmt)
  {
    this.locale = uInfo.getLocale();
    this.timeZone = uInfo.getTimeZone();
    this.statement = stmt;
    this.userName = uInfo.getUserName();
  }

  private Vector getBinds()
  {
    if (this.binds == null) {
      this.binds = new Vector(10);
    }
    return this.binds;
  }

  void setString(int col, String val)
  {
    setElementAt(val, col);
  }

  public void setBoolean(int col, boolean val)
  {
    if (val)
      setElementAt(new Long(MXFormat.getStoreYesValue()), col);
    else
      setElementAt(new Long(MXFormat.getStoreNoValue()), col);
  }

  public void setLong(int col, long val)
  {
    setElementAt(new Long(val), col);
  }

  public void setInt(int col, int val)
  {
    setLong(col, val);
  }

  public void setFloat(int col, float val)
  {
    setDouble(col, val);
  }

  public void setDouble(int col, double val)
  {
    setElementAt(new Double(val), col);
  }

  public void setDate(int col, java.util.Date val)
  {
    setElementAt(val, col);
  }

  public void setTime(int col, java.util.Date val)
  {
    setElementAt(new Time(val.getTime()), col);
  }

  public void setTimestamp(int col, java.util.Date val)
  {
    setElementAt(new Timestamp(val.getTime()), col);
  }

  public void setBytes(int col, byte[] val)
  {
    setElementAt(val, col);
  }

  public void setObject(int col, String tbName, String colName, String val)
    throws MXException, RemoteException
  {
    String type = MXServer.getMXServer().getMaximoDD().getMboSetInfo(tbName).getMboValueInfo(colName).getType();
    int intType = MXFormat.getMaxTypeAsInt(type);
    switch (intType)
    {
    case 1:
      setString(col, val.toUpperCase());
      break;
    case 2:
      setString(col, val.toLowerCase());
      break;
    case 0:
    case 13:
    case 15:
    case 17:
      setString(col, val);
      break;
    case 12:
      setInt(col, new Integer(MXFormat.convertToStoreYNValue(val, this.locale)).intValue());
      break;
    case 3:
      setDate(col, MXFormat.stringToDate(val, this.locale));
      break;
    case 4:
      setTimestamp(col, MXFormat.stringToDateTime(val, this.locale, this.timeZone));
      break;
    case 5:
      setTime(col, MXFormat.stringToTime(val, this.locale));
      break;
    case 6:
    case 7:
      setLong(col, MXFormat.stringToLong(val, this.locale));
      break;
    case 8:
    case 9:
      setDouble(col, MXFormat.stringToDouble(val, this.locale));
      break;
    case 11:
      setDouble(col, MXFormat.stringToAmount(val, this.locale));
    case 10:
      setDouble(col, MXFormat.durationToDouble(val, this.locale));
      break;
    case 14:
    case 16:
    default:
      throw new MXSystemException("system", "invalidtype");
    }
  }

  private void setElementAt(Object o, int col)
  {
    Vector v = getBinds();

    for (int i = v.size(); i <= col; ++i) {
      v.insertElementAt(new Integer(-1), i);
    }
    v.setElementAt(o, col);
  }

  public String simpleFormat()
  {
    this.simpleReplacement = true;
    return format();
  }

  public String validateFormat()
    throws MXException
  {
    this.validateOnly = true;
    return format(this.mboRemote);
  }

  public String format()
  {
    try
    {
      return format(this.mboRemote);
    }
    catch (MXException e)
    {
      this.encounteredError = e;
      e.printStackTrace();
    }

    return this.statement;
  }

  public String formatRaw()
    throws MXException
  {
    return format(this.mboRemote);
  }

  public MXException getEncounteredError()
  {
    MXException e = this.encounteredError;
    this.encounteredError = null;
    return e;
  }

  private void addEncounteredError(MXException e) {
    this.encounteredError = new MXApplicationException("system", "SqlFormatError", e);
  }

  private String format(MboRemote mbo)
    throws MXException
  {
    this.strF.reset();
    this.nullBindValueExist = false;
    this.hasOr = false;

    StringBuffer result = new StringBuffer();
    StringBuffer fieldName = null;
    char[] ca = this.statement.toCharArray();
    boolean inField = false;
    boolean inQuote = false;
    boolean inURL = false;

    for (int i = 0; i < ca.length; ++i)
    {
      if (ca[i] == '\'') {
        inQuote = !(inQuote);
      }

      if ((!(this.simpleReplacement)) && (!(inQuote)) && (ca[i] >= 'A') && (ca[i] <= 'Z')) {
        ca[i] = (char)(ca[i] - 'A' + 97);
      }

      if ((inField) && (!(Character.isLetterOrDigit(ca[i]))) && (ca[i] != '.') && (ca[i] != '$') && (ca[i] != '_'))
      {
        inField = false;

        if (fieldName.toString().endsWith("."))
        {
          fieldName.deleteCharAt(fieldName.length() - 1);
          --i;
        }

        result.append(replaceFieldText(mbo, fieldName, !(inURL)));
      }

      if ((inField) && (i == ca.length - 1))
      {
        if (ca[i] == '.')
          --i;
        else
          fieldName.append(ca[i]);
        inField = false;
        result.append(replaceFieldText(mbo, fieldName, !(inURL)));
      }
      else if ((ca[i] == ':') && (((this.simpleReplacement) || (!(inQuote)))) && (i + 1 < ca.length) && (ca[(i + 1)] != ' ') && (ca[(i + 1)] != '\t') && (ca[(i + 1)] != '/'))
      {
        fieldName = new StringBuffer();
        inField = true;
        this.strF.reset();
      }
      else if (!(inField))
      {
        if (isURLPrefix(ca, i))
        {
          inURL = true;
        }

        result.append(ca[i]);
        if ((!(inQuote)) && 
          (!(this.hasOr)) && (this.strF.checkForString(ca[i]))) {
          this.hasOr = true;
        }

      }
      else
      {
        fieldName.append(ca[i]);
      }
      if ((inURL) && (i < ca.length) && (ca[i] == ' ')) {
        inURL = false;
      }
    }
    return result.toString();
  }

  private String replaceFieldText(MboRemote mbo, StringBuffer fieldName, boolean useLocale)
    throws MXException
  {
    try
    {
      String fieldVal = getFieldValue(fieldName.toString(), mbo, useLocale);
      if ((fieldVal.trim().equals("''")) || (fieldVal.trim().equals("-32767"))) {
        this.nullBindValueExist = true;
      }
      if ((fieldVal == null) || (!(fieldVal.equals("**Error**")))) {
        return fieldVal;
      }
      return ":" + fieldName.toString();
    }
    catch (MXException e)
    {
      if (this.ignoreUnresolved)
      {
        return ":" + fieldName.toString();
      }

      throw e;
    }
  }

  private int getDataType(String tableName, String columnName)
    throws RemoteException
  {
    String type = MXServer.getMXServer().getMaximoDD().getMboSetInfo(tableName).getMboValueInfo(columnName).getType();
    int intType = MXFormat.getMaxTypeAsInt(type);
    return intType;
  }

  public String getFieldValue(String field, MboRemote mbo)
    throws MXException
  {
    return getFieldValue(field, mbo, true);
  }

  public String getFieldValue(String field, MboRemote mbo, boolean useLocale)
    throws MXException
  {
    boolean ignore = this.ignoreUnresolved;
    setIgnoreUnresolved(true);
    try
    {
      int col = MXFormat.stringToInt(field);
      return getBindValue(col);
    }
    catch (MXException re)
    {
      setIgnoreUnresolved(ignore);

      if (field.equalsIgnoreCase("YES")) {
        return MXFormat.getStoreYesValue();
      }
      if (field.equalsIgnoreCase("NO")) {
        return MXFormat.getStoreNoValue();
      }

      if (field.equalsIgnoreCase("DATE"))
      {
        if (this.simpleReplacement) {
          return MXFormat.dateToString(new java.util.Date(), this.locale);
        }
        return getDateFunction(new java.util.Date());
      }

      if (field.equalsIgnoreCase("DATETIME"))
      {
        if (this.simpleReplacement) {
          return MXFormat.dateTimeToString(new java.util.Date(), this.locale, this.timeZone);
        }
        return getTimestampFunction(new java.util.Date());
      }

      if (field.equalsIgnoreCase("TIME"))
      {
        if (this.simpleReplacement) {
          return MXFormat.timeToString(new java.util.Date(), this.locale);
        }
        return getTimeFunction(new java.util.Date());
      }

      try
      {
        if (mbo != null)
        {
          if (field.equalsIgnoreCase("SITEFILTERING")) {
            return SiteOrgRestriction.getSiteLookupFilter(mbo);
          }
          if (field.equalsIgnoreCase("ORGFILTERING")) {
            return SiteOrgRestriction.getOrgLookupFilter(mbo);
          }
        }
        if (field.equalsIgnoreCase("USER"))
        {
          if (mbo != null)
          {
            return makeString(mbo.getUserName());
          }

          return makeString(this.userName);
        }

        int dataType = 0;
        int fDataType = 0;
        boolean dbValue = false;

        if (mbo == null) {
          return "''";
        }

        dbValue = needDBValue(field);

        field = getFieldName(field);

        if (field.indexOf("$") != -1)
        {
          int dIndex = field.indexOf("$");
          String fieldName = field.substring(0, dIndex);
          String restOf = field.substring(dIndex + 1).toLowerCase();
          int dotIndex = restOf.indexOf(".");
          String tableName = restOf.substring(0, dotIndex);
          String colName = restOf.substring(dotIndex + 1);

          dataType = getDataType(tableName, colName);
          field = fieldName;

          MboValueData mvd = mbo.getMboValueData(field, true);
          fDataType = mvd.getTypeAsInt();
        }
        else
        {
          MboValueData mvd = mbo.getMboValueData(field, true);
          dataType = mvd.getTypeAsInt();
          fDataType = dataType;
        }

        if ((dbValue) && (!(mbo.toBeAdded())))
        {
          return getDatabaseValue(mbo, field, dataType);
        }

        return getCurrentValue(mbo, field, dataType, fDataType, useLocale);
      }
      catch (RemoteException ree)
      {
        throw new MXSystemException("system", "remoteexception", ree);
      }
    }
  }

  private boolean needDBValue(String fieldName)
  {
    boolean needDBValue = false;

    int index = fieldName.indexOf(46);
    while (index != -1)
    {
      fieldName = fieldName.substring(index + 1);
      index = fieldName.indexOf(46);
    }

    needDBValue = fieldName.toLowerCase().startsWith("$old_");
    return needDBValue;
  }

  private String getFieldName(String field)
  {
    StringBuffer retField = new StringBuffer();

    if (field.toLowerCase().startsWith("$old_"))
    {
      field = field.substring("$old_".length());
    }
    else if (field.toLowerCase().startsWith("$new_"))
    {
      field = field.substring("$new_".length());
    }

    int index = field.indexOf(46);
    while (index != -1)
    {
      retField.append(field.substring(0, index + 1));
      field = field.substring(index + 1);
      index = field.indexOf(46);
    }

    if (field.toLowerCase().startsWith("$old_"))
    {
      retField.append(field.substring("$old_".length()));
    }
    else if (field.toLowerCase().startsWith("$new_"))
    {
      retField.append(field.substring("$new_".length()));
    }
    else
    {
      retField.append(field);
    }

    return retField.toString();
  }

  private String getCurrentValue(MboRemote mbo, String field, int dataType, int fDataType, boolean useLocale)
    throws RemoteException, MXException
  {
    switch (dataType)
    {
    case 0:
    case 1:
    case 2:
    case 14:
    case 15:
    case 17:
      return makeString(mbo.getMboValueData(field, true).getDataAsObject(fDataType).toString());
    case 13:
      if (this.simpleReplacement)
      {
        return mbo.getString(field);
      }
      if (this.validateOnly)
      {
        return "'" + mbo.getString(field) + "'";
      }

      String orgId = mbo.getOrgForGL(field);
      GLFormat fmt = new GLFormat(mbo.getString(field), orgId);
      return " '" + fmt.toQueryString() + "' ";
    case 6:
    case 7:
    case 12:
      if (this.simpleReplacement)
      {
        if (!(useLocale))
          return "" + mbo.getLong(field) + "";
        return mbo.getString(field);
      }

      if (!(mbo.isNull(field))) {
        return " " + mbo.getLong(field) + " ";
      }
      return " -32767 ";
    case 8:
    case 9:
    case 10:
    case 11:
      if (this.simpleReplacement)
      {
        return mbo.getString(field);
      }

      return " " + mbo.getDouble(field) + " ";
    case 4:
      if (mbo.isNull(field))
      {
        return " null ";
      }

      if (this.simpleReplacement) {
        return MXFormat.dateTimeToString(mbo.getDate(field), this.locale, this.timeZone);
      }
      return getTimestampFunction(mbo.getDate(field));
    case 3:
      if (mbo.isNull(field))
      {
        return " null ";
      }

      if (this.simpleReplacement) {
        return MXFormat.dateToString(mbo.getDate(field), this.locale);
      }
      return getDateFunction(mbo.getDate(field));
    case 5:
      if (mbo.isNull(field))
      {
        return " null ";
      }

      if (this.simpleReplacement) {
        return MXFormat.timeToString(mbo.getDate(field), this.locale);
      }
      return getTimeFunction(mbo.getDate(field));
    case 16:
    }

    throw new MXSystemException("system", "invalidtype");
  }

  private String getDatabaseValue(MboRemote mbo, String field, int dataType)
    throws RemoteException, MXException
  {
    Object value = mbo.getDatabaseValue(field);
    double dValue;
    java.util.Date dt;
    switch (dataType)
    {
    case 0:
    case 1:
    case 2:
    case 12:
    case 14:
      return makeString(value.toString());
    case 13:
      if (this.simpleReplacement)
      {
        String orgId = mbo.getOrgForGL(field);
        GLFormat fmt = new GLFormat(value.toString(), orgId);
        return fmt.toDisplayString();
      }

      String orgId = mbo.getOrgForGL(field);
      GLFormat fmt = new GLFormat(value.toString(), orgId);
      return " '" + fmt.toQueryString() + "' ";
    case 6:
    case 7:
      if (this.simpleReplacement)
      {
        long lValue = ((Number)value).longValue();
        return MXFormat.longToString(lValue, this.locale);
      }

      if (value != null) {
        return " " + ((Number)value).longValue() + " ";
      }
      return " -32767 ";
    case 8:
    case 9:
    case 11:
      if (this.simpleReplacement)
      {
        dValue = ((Number)value).doubleValue();
        return MXFormat.doubleToString(dValue, this.locale);
      }

      return " " + ((Number)value).doubleValue() + " ";
    case 10:
      if (this.simpleReplacement)
      {
        dValue = ((Number)value).doubleValue();
        return MXFormat.doubleToDuration(dValue, this.locale);
      }

      return " " + ((Number)value).doubleValue() + " ";
    case 4:
      if (value instanceof java.sql.Date)
        dt = new java.util.Date(((java.sql.Date)value).getTime());
      else {
        dt = (java.util.Date)value;
      }
      if (this.simpleReplacement) {
        return MXFormat.dateTimeToString(dt, this.locale, this.timeZone);
      }
      return getTimestampFunction(dt);
    case 3:
      if (value instanceof java.sql.Date)
        dt = new java.util.Date(((java.sql.Date)value).getTime());
      else {
        dt = (java.util.Date)value;
      }
      if (this.simpleReplacement) {
        return MXFormat.dateToString(dt, this.locale);
      }
      return getDateFunction(dt);
    case 5:
      if (value instanceof java.sql.Date)
        dt = new java.util.Date(((java.sql.Date)value).getTime());
      else {
        dt = (java.util.Date)value;
      }
      if (this.simpleReplacement) {
        return MXFormat.timeToString(dt, this.locale);
      }
      return getTimeFunction(dt);
    }

    throw new MXSystemException("system", "invalidtype");
  }

  private String getBindValue(int col)
  {
    try
    {
      Object o = getBinds().elementAt(col);

      if (o instanceof String) {
        return makeString((String)o);
      }
      if (o instanceof Long) {
        return "" + ((Long)o).longValue();
      }
      if (o instanceof Double) {
        return "" + ((Double)o).doubleValue();
      }
      if (o instanceof Time) {
        return getTimeFunction((java.util.Date)o);
      }
      if (o instanceof Timestamp) {
        return getTimestampFunction((java.util.Date)o);
      }
      if (o instanceof java.util.Date)
        return getDateFunction((java.util.Date)o);
    }
    catch (Throwable e)
    {
      if (!(this.ignoreUnresolved)) {
        e.printStackTrace();
      }
    }
    return "**Error**";
  }

  public void setNoSpaces(boolean b)
  {
    this.noSpaces = b;
  }

  private String makeString(String s)
  {
    String spaces = (this.noSpaces) ? "" : " ";
    return " '" + getSQLString(s) + "' ";
  }

  public static String getSQLString(String s)
  {
    StringBuffer buf = new StringBuffer(s);
    StringBuffer newBuf = new StringBuffer();
    int bufLength = buf.length();
    for (int i = 0; i < bufLength; ++i)
    {
      if (buf.charAt(i) == '\'')
        newBuf.append('\'');
      newBuf.append(buf.charAt(i));
    }

    return newBuf.toString();
  }

  public String resolveContent()
    throws MXException, RemoteException
  {
    setNoSpaces(true);
    return simpleFormat();
  }

  public void setIgnoreUnresolved(boolean b)
  {
    this.ignoreUnresolved = b;
  }

  private boolean isURLPrefix(char[] ca, int beginIndex)
  {
    int i = 0; if (i < URL_PREFIX.length)
    {
      char[] urlPrefix = URL_PREFIX[i].toCharArray();
      int j = 0;
      while (j < urlPrefix.length)
      {
        if ((beginIndex + j < ca.length) && (ca[(beginIndex + j)] != urlPrefix[j]))
          break;
        ++j;
      }

      return true;
    }
    return false;
  }
  private class StringFinder
  {

      public boolean checkForString(char c)
      {
          if(expecting && (c == '(' || c == ')' || c == ' ' || c == ','))
          {
              reset();
              return true;
          }
          if(skip && (c == '(' || c == ')' || c == ' ' || c == ','))
          {
              reset();
              return false;
          }
          if(expecting)
          {
              skip = true;
              expecting = false;
              return false;
          }
          if(skip)
              return false;
          if(c == '(' || c == ')' || c == ' ' || c == ',')
          {
              if(skip)
                  reset();
              return false;
          }
          if(caseIndex == -1)
          {
              for(int i = 0; i < 2; i++)
                  if(c == special[i][0])
                  {
                      caseIndex = i;
                      charIndex = 0;
                      return false;
                  }

              skip = true;
              return false;
          }
          if(c == special[caseIndex][charIndex + 1])
          {
              charIndex++;
              if(charIndex + 1 == special[caseIndex].length)
                  expecting = true;
              return false;
          } else
          {
              skip = true;
              return false;
          }
      }

      public void reset()
      {
          caseIndex = -1;
          charIndex = -1;
          expecting = false;
          skip = false;
      }

      int specialCases;
      char special[][] = {
          {
              'o', 'r'
          }, {
              'c', 'a', 's', 'e'
          }
      };
      int caseIndex;
      int charIndex;
      boolean expecting;
      boolean skip;

      private StringFinder()
      {
          specialCases = 2;
          caseIndex = -1;
          charIndex = -1;
          expecting = false;
          skip = false;
      }

  }
}