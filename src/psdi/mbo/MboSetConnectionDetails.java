package psdi.mbo;

import java.rmi.RemoteException;
import java.sql.ResultSet;
import java.sql.Statement;
import psdi.security.ConnectionKey;
import psdi.util.MXException;

class MboSetConnectionDetails
{
  ResultSet mboSetResultSet;
  Statement mboSetSqlStatement;
  ConnectionKey resultConKey;
  boolean performedFetch;
  boolean closed;
  MboServerInterface mboServer;

  MboSetConnectionDetails()
  {
    this.mboSetResultSet = null;

    this.mboSetSqlStatement = null;

    this.resultConKey = null;

    this.performedFetch = false;

    this.closed = false;

    this.mboServer = null;
  }

  public void init() {
    this.closed = false;
  }

  public void close()
    throws MXException, RemoteException
  {
    if (isClosed()) {
      return;
    }

    this.closed = true;

    closeFetchConnection();
    this.performedFetch = true;
  }

  public void closeFetchConnection()
  {
    try
    {
      if (this.mboSetResultSet != null)
        this.mboSetResultSet.close();
    } catch (Exception ex) {
    }
    try {
      if (this.mboSetSqlStatement != null)
        this.mboSetSqlStatement.close();
    }
    catch (Exception ex)
    {
    }
    try {
      if (this.resultConKey != null)
        getMboServer().freeDBConnection(this.resultConKey);
    } catch (Exception ex) {
    }
    this.mboSetResultSet = null;
    this.mboSetSqlStatement = null;
    this.resultConKey = null;
  }

  public boolean isClosed()
  {
    return this.closed;
  }

  protected MboServerInterface getMboServer()
  {
    return this.mboServer;
  }

  public void setMboServer(MboServerInterface mboServer)
  {
    this.mboServer = mboServer;
  }

  public boolean isfetchPerformed()
  {
    return this.performedFetch;
  }

  public void setFetchPerformed(boolean performedFetch)
  {
    this.performedFetch = performedFetch;
  }

  public void setResultConnectionKey(ConnectionKey resultConKey)
  {
    this.resultConKey = resultConKey;
  }

  public ConnectionKey getResultConnectionKey()
  {
    return this.resultConKey;
  }

  public void setStatement(Statement statement)
  {
    this.mboSetSqlStatement = statement;
  }

  public Statement getStatement()
  {
    return this.mboSetSqlStatement;
  }

  public void setResultSet(ResultSet resultSet)
  {
    this.mboSetResultSet = resultSet;
  }

  public ResultSet getResultSet()
  {
    return this.mboSetResultSet;
  }
}