package custom.app.po;

import java.rmi.RemoteException;
import psdi.app.po.POSet;
import psdi.app.po.POSetRemote;
import psdi.mbo.Mbo;
import psdi.mbo.MboServerInterface;
import psdi.mbo.MboSet;
import psdi.util.MXException;

public class POSetExt extends POSet
  implements POSetRemote
{
  public POSetExt(MboServerInterface ms)
    throws MXException, RemoteException
  {
    super(ms);
  }

  protected Mbo getMboInstance(MboSet ms) throws MXException, RemoteException {
    return new POExt(ms);
  }
}