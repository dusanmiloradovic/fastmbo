/*jadclipse*/// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.

package psdi.mbo;

import java.rmi.RemoteException;
import java.util.*;
import psdi.app.system.SystemServiceRemote;
import psdi.server.MXServer;
import psdi.util.*;
import psdi.util.logging.MXLogger;

// Referenced classes of package psdi.mbo:
//            BasicDomain, MAXTABLEDomainInfo, SqlFormat, MaxLookupMapCache, 
//            MboValue, MaximoDD, MboSetInfo, Mbo, 
//            MboSetRemote, MboValueInfo, MboServerInterface, MboRemote

public class MAXTableDomain extends BasicDomain
{

    public MAXTableDomain(MboValue mbv)
    {
        super(mbv);
        errorGroup = "system";
        errorKey = "notvalid";
        relationWhere = null;
        objectName = null;
        listWhere = null;
        conditionalListWhere = new ArrayList();
        cachedKeyMapHash = new HashMap(2);
        multiKeyWhereForLookup = null;
        allAttrsNullable = false;
        notAllowNullAttrs = null;
    }

    public void validate()
        throws MXException, RemoteException
    {
        if(getMboValue().isNull())
            return;
        String cachedKeyMap[][] = getCachedKeyMapFromHash(objectName);
        if(cachedKeyMap == null && notAllowNullAttrs == null && !allAttrsNullable && objectName != null && MXServer.getMXServer().getMaximoDD().getMboSetInfo(objectName).getKeyAttributes().length > 1)
            try
            {
                cachedKeyMap = getMatchingAttrs(objectName);
            }
            catch(MXException e)
            {
                MXLogger l = getMboValue().getMbo().getMboLogger();
                if(l.isWarnEnabled())
                    l.warn(e);
            }
        if(!allAttrsNullable && notAllowNullAttrs != null)
        {
            for(int i = 0; i < notAllowNullAttrs.length; i++)
            {
                String attr = notAllowNullAttrs[i];
                if(getMboValue(attr).isNull())
                {
                    Object params[] = {
                        getMboValue(attr).getColumnTitle(), getMboValue().getColumnTitle()
                    };
                    throw new MXApplicationException("system", "nullBeforeSet", params);
                }
            }

        }
        resetDomainValues();
        MboSetRemote msi = getMboSet();
        msi.reset();
        if(msi == null || msi.isEmpty())
        {
            if(msi != null)
                msi.close();
            Object params[] = {
                mboValue.getColumnTitle(), mboValue.getCurrentValue().toString()
            };
            throw new MXApplicationException(errorGroup, errorKey, params);
        } else
        {
            msi.close();
            return;
        }
    }

    public void chooseActualDomainValues()
        throws MXException, RemoteException
    {
        if(domainId == null)
            return;
        String siteOrg[] = getSiteOrg();
        String siteId = siteOrg[0];
        String orgId = siteOrg[1];
        DomainInfo domainInfo = getMboValue().getMboValueInfo().getDomainInfo();
        if(domainInfo == null)
            domainInfo = getMboValue().getMbo().getMboServer().getMaximoDD().getDomainInfo(domainId);
        if(domainInfo == null)
        {
            throw new MXApplicationException("system", "domainnotexist", new String[] {
                domainId
            });
        } else
        {
            setRelationship(((MAXTABLEDomainInfo)domainInfo).getObjectName(siteId, orgId), ((MAXTABLEDomainInfo)domainInfo).getValidationWhere(siteId, orgId));
            setListCriteria(((MAXTABLEDomainInfo)domainInfo).getListWhere(siteId, orgId));
            setErrorMessage(((MAXTABLEDomainInfo)domainInfo).getErrorGroup(siteId, orgId), ((MAXTABLEDomainInfo)domainInfo).getErrorKey(siteId, orgId));
            return;
        }
    }

    public MboSetRemote getMboSet(String where)
        throws MXException, RemoteException
    {
        return getMboSet(where, "");
    }

    public MboSetRemote getMboSet(String where, String identifier)
        throws MXException, RemoteException
    {
        if(identifier == null)
            identifier = "";
        if(getMboValue().getMbo().isZombie())
            return mboValue.getMbo().getMboSet("__" + objectName + mboValue.getName() + identifier, objectName, (new SqlFormat(getMboValue().getMbo(), where)).format());
        else
            return mboValue.getMbo().getMboSet("__" + objectName + mboValue.getName() + identifier, objectName, where);
    }

    public MboSetRemote getMboSet()
        throws MXException, RemoteException
    {
        return getMboSet(combineWheres());
    }

    public boolean hasList()
    {
        return true;
    }

    public MboSetRemote getList()
        throws MXException, RemoteException
    {
        resetDomainValues();
        String cachedKeyMap[][] = getCachedKeyMapFromHash(objectName);
        if(listWhere == null && multiKeyWhereForLookup == null && cachedKeyMap == null && objectName != null && MXServer.getMXServer().getMaximoDD().getMboSetInfo(objectName).getKeyAttributes().length > 1)
            cachedKeyMap = getMatchingAttrs(objectName);
        String w = null;
        if(listWhere != null && listWhere.trim().length() > 0)
            w = listWhere;
        String cw = evalConditionalWhere(conditionalListWhere);
        if(cw != null && !cw.trim().equals(""))
            if(w == null)
                w = cw;
            else
                w = "(" + w + ") and (" + cw + ")";
        if(w == null && multiKeyWhereForLookup != null)
            w = multiKeyWhereForLookup;
        if(w == null)
            w = "";
        MboSetRemote ms = getMboSet(w, "list");
        ms.setTableDomainLookup(true);
        if(listWhere == null && multiKeyWhereForLookup == null && cachedKeyMap != null && cachedKeyMap.length > 1)
        {
            String thisAttr = getMboValue().getName();
            multiKeyWhereForLookup = null;
            for(int i = 0; i < cachedKeyMap.length; i++)
                if(!cachedKeyMap[i][0].equalsIgnoreCase(thisAttr))
                    ms.setQbe(cachedKeyMap[i][1], getMboValue().getMbo().getString(cachedKeyMap[i][0]));
                else
                    ms.setQbe(cachedKeyMap[i][1], "");

        }
        return ms;
    }

    public void setRelationship(String objectName, String whereClause)
    {
        this.objectName = objectName;
        relationWhere = whereClause;
    }

    public void setListCriteria(String listWhere)
    {
        this.listWhere = listWhere;
    }

    public void setErrorMessage(String eg, String ek)
    {
        if(eg != null)
        {
            errorGroup = eg;
            if(ek != null)
                errorKey = ek;
        }
    }

    String combineWheres()
    {
        StringBuffer result = new StringBuffer("");
        if(relationWhere != null && relationWhere.length() > 0)
            result.append("(" + relationWhere + " ) ");
        if(listWhere != null && listWhere.length() > 0)
        {
            if(result.length() > 0)
                result.append("AND ");
            result.append("(" + listWhere + " )");
        }
        return result.toString();
    }

    public String getListCriteria()
    {
        return listWhere;
    }

    public void setValueFromLookup(MboRemote sourceMbo)
        throws MXException, RemoteException
    {
        Mbo mbo = getMboValue().getMbo();
        String targetAttrName = getMboValue().getAttributeName();
        String cachedKeyMap[][] = getCachedKeyMapFromHash(sourceMbo.getName());
        if(cachedKeyMap == null)
            cachedKeyMap = getMatchingAttrs(sourceMbo.getName());
        for(int i = 0; i < cachedKeyMap.length; i++)
            if(cachedKeyMap[i][0].equalsIgnoreCase(targetAttrName) || !mbo.getString(cachedKeyMap[i][0]).equals(sourceMbo.getString(cachedKeyMap[i][1])))
                mbo.setValue(cachedKeyMap[i][0], sourceMbo.getString(cachedKeyMap[i][1]), 16L);

    }

    protected String[][] getMatchingAttrs(String sourceName)
        throws MXException, RemoteException
    {
        Mbo mbo = getMboValue().getMbo();
        String targetAttrName = getMboValue().getName();
        String cachedKeyMap[][] = getCachedKeyMapFromHash(sourceName.toUpperCase());
        if(cachedKeyMap != null)
            return cachedKeyMap;
        Object keyMap[] = mbo.getMatchingAttrs(sourceName.toUpperCase(), getMboValue().getName());
        if(keyMap == null)
            throw new MXApplicationException("system", "LookupNeedRegister", new String[] {
                sourceName, targetAttrName
            });
        cachedKeyMap = new String[keyMap.length][2];
        int k = 0;
        String tmp[] = new String[keyMap.length];
        for(int i = 0; i < keyMap.length; i++)
        {
            cachedKeyMap[i][0] = (String)((Object[])keyMap[i])[0];
            cachedKeyMap[i][1] = (String)((Object[])keyMap[i])[1];
            if(!((Boolean)((Object[])keyMap[i])[2]).booleanValue() && !cachedKeyMap[i][0].equalsIgnoreCase(targetAttrName))
            {
                tmp[k] = cachedKeyMap[i][0];
                k++;
            }
        }

        notAllowNullAttrs = new String[k];
        for(int index = 0; index < k; index++)
            notAllowNullAttrs[index] = tmp[index];

        if(notAllowNullAttrs == null)
            allAttrsNullable = true;
        return cachedKeyMap;
    }

    public void setLookupKeyMapInOrder(String targetKeys[], String sourceKeys[])
    {
        String cachedKeyMap[][] = new String[targetKeys.length][2];
        for(int i = 0; i < targetKeys.length; i++)
        {
            cachedKeyMap[i][0] = targetKeys[i];
            cachedKeyMap[i][1] = sourceKeys[i];
        }

        cachedKeyMapHash.put(objectName != null ? ((Object) (objectName.toUpperCase())) : "", cachedKeyMap);
        try
        {
            addToLookupMapCache(objectName != null ? objectName.toUpperCase() : "", cachedKeyMap);
        }
        catch(Exception e)
        {
            if(getMboValue().getMbo().getMboLogger().isWarnEnabled())
                getMboValue().getMbo().getMboLogger().warn(e);
        }
    }

    public void setKeyMap(String sourceMboName, String targetKeys[], String sourceKeys[])
    {
        String cachedKeyMap[][] = new String[targetKeys.length][2];
        for(int i = 0; i < targetKeys.length; i++)
        {
            cachedKeyMap[i][0] = targetKeys[i];
            cachedKeyMap[i][1] = sourceKeys[i];
        }

        cachedKeyMapHash.put(sourceMboName.toUpperCase(), cachedKeyMap);
        try
        {
            addToLookupMapCache(sourceMboName.toUpperCase(), cachedKeyMap);
        }
        catch(Exception e)
        {
            if(getMboValue().getMbo().getMboLogger().isWarnEnabled())
                getMboValue().getMbo().getMboLogger().warn(e);
        }
    }

    public void setLookupKeyMapInOrder(String map[][])
    {
        cachedKeyMapHash.put(objectName != null ? ((Object) (objectName.toUpperCase())) : "", map);
        try
        {
            addToLookupMapCache(objectName != null ? objectName.toUpperCase() : "", map);
        }
        catch(Exception e)
        {
            if(getMboValue().getMbo().getMboLogger().isWarnEnabled())
                getMboValue().getMbo().getMboLogger().warn(e);
        }
    }

    public void addToLookupMapCache(String source, String map[][])
        throws MXException, RemoteException
    {
        String target = getMboValue().getMbo().getName();
        Object keyMap[] = ((SystemServiceRemote)MXServer.getMXServer().lookup("SYSTEM")).getLookupKeyMap(target, source, getMboValue().getName(), getMboValue().getMbo().getUserInfo());
        if(keyMap == null)
        {
            Object newMap[] = new Object[map.length];
            for(int i = 0; i < map.length; i++)
                newMap[i] = ((Object) (new Object[] {
                    map[i][0], map[i][1], new Boolean(true)
                }));

            ((MaxLookupMapCache)MXServer.getMXServer().getFromMaximoCache("LookupKeyMap")).setLookupKeyMap(target, source, getMboValue().getName(), newMap);
        }
    }

    public final void setAllAttrsNullable()
    {
        allAttrsNullable = true;
    }

    public final void setNotAllowNullAttrs(String attrs[])
    {
        notAllowNullAttrs = attrs;
    }

    public void setMultiKeyWhereForLookup(String w)
    {
        multiKeyWhereForLookup = w;
    }

    public MboSetRemote smartFill(String value, boolean exact)
        throws MXException, RemoteException
    {
        MboSetRemote retSet = smartFind(value, exact);
        if(retSet == null)
            return retSet;
        if(retSet.getMbo(0) != null && retSet.getMbo(1) == null)
            setValueFromLookup(retSet.getMbo(0));
        return retSet;
    }

    public MboSetRemote smartFillWithoutReset(String value, boolean exact)
        throws MXException, RemoteException
    {
        String cachedKeyMap[][] = getCachedKeyMapFromHash(objectName);
        if(cachedKeyMap == null && objectName != null)
            cachedKeyMap = getMatchingAttrs(objectName);
        MboSetRemote retSet = getList();
        if(!retSet.getMboSetInfo().isPersistent())
            return null;
        int i;
        for(i = cachedKeyMap.length - 1; i >= 0 && !cachedKeyMap[i][0].equalsIgnoreCase(getMboValue().getName()); i--);
        Mbo thisMbo = getMboValue().getMbo();
        MboSetRemote emptySet = thisMbo.getMboServer().getMboSet(objectName, thisMbo.getUserInfo());
        emptySet.setWhere("1=2");
        emptySet.setFlag(8L, true);
        MboRemote each = null;
        int count = 0;
        MboRemote lastFound = null;
        for(int j = 0; (each = retSet.getMbo(j)) != null; j++)
        {
            if(exact)
            {
                if(each.getString(cachedKeyMap[i][0]).equalsIgnoreCase(value))
                {
                    lastFound = each;
                    each.blindCopy(emptySet);
                    count++;
                }
                continue;
            }
            if(each.getString(cachedKeyMap[i][0]).toUpperCase().indexOf(value.toUpperCase()) != -1)
            {
                lastFound = each;
                each.blindCopy(emptySet);
                count++;
            }
        }

        if(count == 1)
            setValueFromLookup(thisMbo);
        return emptySet;
    }

    public MboSetRemote smartFind(String value, boolean exact)
        throws MXException, RemoteException
    {
        String cachedKeyMap[][] = getCachedKeyMapFromHash(objectName);
        if(cachedKeyMap == null && objectName != null)
            cachedKeyMap = getMatchingAttrs(objectName);
        MboSetRemote listSet = getList();
        String where = listSet.getCompleteWhere();
        MboSetRemote retSet = getMboValue().getMbo().getMboServer().getMboSet(objectName, getMboValue().getMbo().getUserInfo());
        retSet.setTableDomainLookup(true);
        retSet.setOwner(listSet.getOwner());
        retSet.setApp(listSet.getApp());
        retSet.setWhere(where);
        retSet.setFlag(8L, true);
        if(!retSet.getMboSetInfo().isPersistent())
            return null;
        int i;
        for(i = cachedKeyMap.length - 1; i >= 0 && !cachedKeyMap[i][0].equalsIgnoreCase(getMboValue().getName()); i--);
        if(exact)
        {
            retSet.setQbeExactMatch(true);
            retSet.setQbe(cachedKeyMap[i][1], value);
        } else
        {
            retSet.setQbeExactMatch(false);
            retSet.setQbeCaseSensitive(false);
            String searchType = getMboValue().getMboValueInfo().getSearchType();
            int maxtype = getMboValue().getType();
            if(maxtype == 8 || maxtype == 9 || maxtype == 6 || maxtype == 7 || maxtype == 11)
                retSet.setQbe(cachedKeyMap[i][1], value);
            else
                retSet.setQbe(cachedKeyMap[i][1], value + "%");
        }
        return retSet;
    }

    public MboSetRemote smartFindWithoutReset(String value, boolean exact)
        throws MXException, RemoteException
    {
        String cachedKeyMap[][] = getCachedKeyMapFromHash(objectName);
        if(cachedKeyMap == null && objectName != null)
            cachedKeyMap = getMatchingAttrs(objectName);
        MboSetRemote retSet = getList();
        if(!retSet.getMboSetInfo().isPersistent())
            return null;
        int i;
        for(i = cachedKeyMap.length - 1; i >= 0 && !cachedKeyMap[i][0].equalsIgnoreCase(getMboValue().getName()); i--);
        Mbo thisMbo = getMboValue().getMbo();
        MboSetRemote emptySet = thisMbo.getMboServer().getMboSet(objectName, thisMbo.getUserInfo());
        emptySet.setWhere("1=2");
        emptySet.setFlag(8L, true);
        MboRemote each = null;
        int count = 0;
        MboRemote lastFound = null;
        for(int j = 0; (each = retSet.getMbo(j)) != null; j++)
        {
            if(exact)
            {
                if(each.getString(cachedKeyMap[i][0]).equalsIgnoreCase(value))
                {
                    lastFound = each;
                    each.blindCopy(emptySet);
                    count++;
                }
                continue;
            }
            if(each.getString(cachedKeyMap[i][0]).toUpperCase().startsWith(value.toUpperCase()))
            {
                lastFound = each;
                each.blindCopy(emptySet);
                count++;
            }
        }

        return emptySet;
    }

    public MboSetRemote smartFind(String sourceObj, String value, boolean exact)
        throws MXException, RemoteException
    {
        String cachedKeyMap[][] = getCachedKeyMapFromHash(sourceObj);
        if(cachedKeyMap != null && sourceObj != null)
            return getMboValue().getMbo().smartFindByObjectName(sourceObj, getMboValue().getName(), value, exact, cachedKeyMap);
        if(cachedKeyMap == null)
            return getMboValue().getMbo().smartFindByObjectNameDirect(sourceObj, getMboValue().getName(), value, exact);
        else
            return null;
    }

    private String[][] getCachedKeyMapFromHash(String sourceMboName)
    {
        String map[][] = (String[][])cachedKeyMapHash.get(sourceMboName != null ? ((Object) (sourceMboName.toUpperCase())) : "");
        if(map == null && cachedKeyMapHash.size() == 1)
            map = (String[][])cachedKeyMapHash.get("");
        return map;
    }

    public String getMatchingAttr()
        throws MXException, RemoteException
    {
        return getMatchingAttr(objectName);
    }

    public String getMatchingAttr(String sourceObjectName)
        throws MXException, RemoteException
    {
        String cachedKeyMap[][] = getMatchingAttrs(sourceObjectName);
        int i;
        for(i = cachedKeyMap.length - 1; i >= 0 && !cachedKeyMap[i][0].equalsIgnoreCase(getMboValue().getName()); i--);
        return cachedKeyMap[i][1];
    }

    public void addConditionalListWhere(String attribute, String condition, String where)
    {
        conditionalListWhere.add(new String[] {
            attribute, condition, where
        });
    }

    public void clearConditionalListWhere()
    {
        conditionalListWhere.clear();
    }

    public String evalConditionalWhere(ArrayList conditionalWhereList)
        throws MXException, RemoteException
    {
        if(conditionalWhereList == null || conditionalWhereList.isEmpty())
            return null;
        StringBuffer sb = new StringBuffer();
        if(!conditionalListWhere.isEmpty())
        {
            Iterator it = conditionalListWhere.iterator();
            boolean first = true;
            do
            {
                if(!it.hasNext())
                    break;
                if(!first)
                    sb.append(" and ");
                else
                    first = false;
                String cw[] = (String[])it.next();
                if(cw[1].equals("0"))
                {
                    if(getMboValue(cw[0]).isNull())
                    {
                        sb.append("(");
                        sb.append(cw[2]);
                        sb.append(")");
                    }
                } else
                if(cw[1].equals("1") && !getMboValue(cw[0]).isNull())
                {
                    sb.append("(");
                    sb.append(cw[2]);
                    sb.append(")");
                }
            } while(true);
        }
        return sb.toString();
    }

    String errorGroup;
    String errorKey;
    String relationWhere;
    String objectName;
    String listWhere;
    ArrayList conditionalListWhere;
    HashMap cachedKeyMapHash;
    String multiKeyWhereForLookup;
    boolean allAttrsNullable;
    String notAllowNullAttrs[];
    public static final String ISNULL = "0";
    public static final String ISNOTNULL = "1";
}


/*
	DECOMPILATION REPORT

	Decompiled from: C:\maxdev\applications\maximo\businessobjects\classes/psdi/mbo/MAXTableDomain.class
	Total time: 29 ms
	Jad reported messages/errors:
	Exit status: 0
	Caught exceptions:
*/