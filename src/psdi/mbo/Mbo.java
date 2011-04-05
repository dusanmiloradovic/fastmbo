package psdi.mbo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.Vector;
import psdi.app.common.MboSetEnumeration;
import psdi.app.sets.SetsServiceRemote;
import psdi.app.system.SystemServiceRemote;
import psdi.security.ProfileRemote;
import psdi.security.UserInfo;
import psdi.server.MXServer;
import psdi.server.MaxVarServiceRemote;
import psdi.server.PerformanceStats;
import psdi.server.ServiceRemote;
import psdi.server.event.EventMessage;
import psdi.server.event.EventTopic;
import psdi.server.event.EventTopicTree;
import psdi.txn.MXTransaction;
import psdi.util.BitFlag;
import psdi.util.MXAccessException;
import psdi.util.MXApplicationException;
import psdi.util.MXException;
import psdi.util.MXFormat;
import psdi.util.MXSystemException;
import psdi.util.MaxType;
import psdi.util.logging.MXLogger;
import psdi.workflow.WorkflowTargetDeletionMonitor;

public class Mbo extends UnicastRemoteObject implements MboRemote, MboConstants {
	
	public boolean ojsa=false;
	private MboSet mySet = null;

	private Hashtable mboSets = null;

	private boolean bPropagateKey = true;

	private boolean bHierarchy = false;

	private MboRecordData mboRecordData = null;

	private boolean modified = false;

	private boolean validated = false;

	private boolean newmbo = false;

	private boolean markedForDelete = false;

	private boolean markedForSelect = false;

	private Hashtable valueHash = null;

	BitFlag userFlags = null;

	private String recordType = null;

	private MXException mxException = null;

	private Hashtable fieldExceptions = null;

	Hashtable fieldFlags = null;

	private int fetchIndex = -1;

	private boolean eSigFieldModified = false;

	private boolean eSigFlagForDelete = false;

	private boolean eAuditFieldModified = false;

	private boolean zombie = false;

	private String[] propagateKeyExcludeObjName = null;

	private HashMap valueInML = new HashMap();

	private boolean readonlyForSite = false;

	boolean propagateKeyValueImplemented = true;

	MboCounter mboCounter = null;

	private boolean accessCheckForDelayedValidation = true;

	Hashtable methodAccessList = null;

	private Object[] eventSubjectTopic = null;

	private boolean checkpoint = false;

	public Mbo(MboSet ms) throws RemoteException {
		
		this.mySet = ms;

		this.mboCounter = new MboCounter(getName());

		logMboCreate(this.mySet.getName());
	}

	void logMboCreate(String name) {
		PerformanceStats ps = (PerformanceStats) MboSet.perfStats.get();

		if (ps == null)
			return;
		ps.incrementMboCount(name);
	}

	void setMboRecordData(MboRecordData data) {
		this.mboRecordData = data;
	}

	public MboRecordData getMboRecordData() {
		return this.mboRecordData;
	}

	public void setNewMbo(boolean flag) {
		this.newmbo = flag;
	}

	Object getFetchValue(String attributeName) {
		if (this.mboRecordData == null) {
			return null;
		}

		MboValueInfo mboValueInfo = getMboSetInfo().getAttribute(attributeName);
		if (mboValueInfo.isFetchAttribute()) {
			int attribNo = mboValueInfo.getFetchAttributeNumber();

			List attributeData = this.mboRecordData.getAttributeData();

			return attributeData.get(attribNo);
		}

		return null;
	}

	public String getStringInBaseLanguage(String attributeName)
			throws MXException, RemoteException {
		return getString(attributeName + "_BASELANGUAGE");
	}

	String getInitialBaseLanguageString(String attributeName)
			throws MXException, RemoteException {
		if (this.mboRecordData == null) {
			return null;
		}

		MboValueInfo mboValueInfo = getMboValueInfo(attributeName);
		if ((this.mySet.processML()) && (mboValueInfo.isMLInUse())) {
			return ((String) this.mboRecordData
					.getBaseLanguageData(attributeName));
		}

		return getString(attributeName);
	}

	public String getString(String attributeName, String langCode)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo != this) {
			return mbo.getString(getAttributeName(attributeName), langCode);
		}

		MboValueInfo mboValueInfo = getMboValueInfo(attributeName);
		if ((!(mboValueInfo.isMLInUse()))
				|| (!(getMboServer().getMaxVar().getBoolean("MLSUPPORTED",
						(String) null)))) {
			return getString(attributeName);
		}

		if (langCode.equals(getUserInfo().getLangCode()))
			return getString(attributeName);
		if (langCode.equals(getMboServer().getMaxVar().getString(
				"BASELANGUAGE", ""))) {
			return getStringInBaseLanguage(attributeName);
		}

		MboSetInfo msi = getMboSetInfo();

		String[] tableAndColumn = msi.getTableAndColumn(attributeName
				.toUpperCase());
		String langTbName = MXServer.getMXServer().getMaximoDD()
				.getLangTableName(tableAndColumn[0]);

		MboSetRemote langMboSet = getMboSet(langTbName);

		MboRemote langMbo = null;
		for (int i = 0; (langMbo = langMboSet.getMbo(i)) != null; ++i) {
			if (langMbo.getString(
					MXServer.getMXServer().getMaximoDD().getLangCodeColumn(
							langTbName)).equals(langCode)) {
				return langMbo.getString(tableAndColumn[1]);
			}
		}
		return "";
	}

	public String getStringTransparent(String attributeName, String langCode)
			throws MXException, RemoteException {
		String specificToLang = getString(attributeName, langCode);
		if (!(specificToLang.equals("")))
			return specificToLang;
		return getStringInBaseLanguage(attributeName);
	}

	public void setMLValue(String attributeName, String langCode, String value,
			long accessModifier) throws MXException, RemoteException {
		if (langCode.equals(getUserInfo().getLangCode())) {
			setValue(attributeName, value, accessModifier);
		} else {
			MboValueInfo mboValueInfo = getMboValueInfo(attributeName);
			if ((!(getMboServer().getMaxVar().getBoolean("MLSUPPORTED",
					(String) null)))
					|| (!(mboValueInfo.isMLInUse()))) {
				return;
			}
			if (langCode.equals(getMboServer().getMaxVar().getString(
					"BASELANGUAGE", ""))) {
				setValue(attributeName + "_BASELANGUAGE", value, accessModifier);
			} else {
				String relationName = getRelationshipNameToLangTable(attributeName);
				MboSetRemote langMboSet = getMboSet(relationName);
				MboRemote langMbo = null;
				String tbAttrName = this.mySet.getMboSetInfo()
						.getTableAndColumn(attributeName)[1];
				String langCodeCol = MXServer.getMXServer().getMaximoDD()
						.getLangCodeColumn(relationName);
				for (int i = 0; (langMbo = langMboSet.getMbo(i)) != null; ++i) {
					if (!(langMbo.getString(langCodeCol).equals(langCode)))
						continue;
					langMbo.setValue(tbAttrName, value, 2L);
					break;
				}

				if (langMbo != null)
					return;
				langMbo = langMboSet.add();
				langMbo.setValue(langCodeCol, langCode, 2L);
				langMbo.setValue(tbAttrName, value, 2L);
			}
		}
	}

	public String getRelationshipNameToLangTable(String attributeName)
			throws MXException, RemoteException {
		MboValueInfo mboValueInfo = getMboValueInfo(attributeName);
		if (!(mboValueInfo.isMLInUse()))
			return null;
		MboSetInfo msi = this.mySet.getMboSetInfo();
		String tbName = msi.getTableAndColumn(attributeName)[0];
		String[] tableAndColumn = msi.getTableAndColumn(attributeName);
		String langTbName = MXServer.getMXServer().getMaximoDD()
				.getLangTableName(tableAndColumn[0]);
		String relationName = langTbName;
		return relationName;
	}

	public Object getDatabaseValue(String attributeName) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			return getFetchValue(attributeName);
		}

		return mbo.getDatabaseValue(getAttributeName(attributeName));
	}

	public void setFetchIndex(int index) {
		this.fetchIndex = index;
	}

	public int getFetchIndex() {
		return this.fetchIndex;
	}

	public void setModified(boolean modified) {
		this.modified = modified;

		if (modified == true)
			this.validated = false;
	}

	public String getName() {
		return this.mySet.getName();
	}

	public MboRemote getOwner() {
		return this.mySet.getOwner();
	}

	public MboSetRemote getThisMboSet() {
		return this.mySet;
	}

	public UserInfo getUserInfo() throws RemoteException {
		return this.mySet.getUserInfo();
	}

	private MboValue generateMboValueInstance(MboValueInfo mvInfo)
			throws MXException {
		MboValue mv = null;
		boolean instance = false;
		try {
			Class[] paramTypes = new Class[0];
			Object[] params = new Object[0];

			Class mvc = mvInfo.getMboValueClass();
			Constructor mvctor = mvc.getConstructor(paramTypes);
			mv = (MboValue) mvctor.newInstance(params);
			mv.construct(this, mvInfo);

			Class customClass = mvInfo.getCustomClass();
			if (customClass != null) {
				Class[] paramType = { Class.forName("psdi.mbo.MboValue") };
				Object[] param = { mv };
				Constructor ctor = customClass.getConstructor(paramType);
				MboValueListener mboValList = (MboValueListener) ctor
						.newInstance(param);
				if (mboValList instanceof FldMboKey) {
					((FldMboKey) mboValList).setKeyAttributes(getMboSetInfo()
							.getKeyAttributes(), getMboSetInfo()
							.getKeyRelationship());
					instance = true;
				}
				if (mboValList instanceof FldMboKeyAsMAXTableDomain) {
					((FldMboKeyAsMAXTableDomain) mboValList).setKeyAttributes(
							getMboSetInfo().getKeyAttributes(), getMboSetInfo()
									.getKeyRelationship());
					instance = true;
				}
				mv.addMboValueListener(mboValList);
			}

			if ((mvInfo.getDomainName() != null)
					&& (mvInfo.getDomainInfo() != null)) {
				MboValueListener mvl = mvInfo.getDomainInfo().getDomainObject(
						mv);
				if (mvl != null) {
					mv.addMboValueListener(mvl);
				}

			}

			if ((mvInfo.isKey()) && (!(instance))) {
				FldMboKey keyField = new FldMboKey(mv);
				keyField.setKeyAttributes(getMboSetInfo().getKeyAttributes(),
						getMboSetInfo().getKeyRelationship());
				mv.addMboValueListener(keyField);
			}
		} catch (ClassNotFoundException cne) {
			throw new MXSystemException("system", "noclass", cne);
		} catch (InstantiationException ie) {
			throw new MXSystemException("system", "execute", ie);
		} catch (NoSuchMethodException ne) {
			throw new MXSystemException("system", "nomethod", ne);
		} catch (InvocationTargetException ie) {
			throw new MXSystemException("system", "nomethod", ie);
		} catch (IllegalAccessException ie) {
			throw new MXSystemException("system", "access", ie);
		}

		return mv;
	}

	public void init() throws MXException {
	}

	void initialize() throws MXException {
		String eventName = getMboSetInfo().getInitEventName();
		EventTopicTree topicTree = MXServer.getEventTopicTree();
		EventMessage message = new EventMessage(eventName, this);
		EventTopic topic = getMboSetInfo().getInitEventTopic();
		topicTree.eventValidate(eventName, message, topic);
		init();
		topicTree.eventAction(eventName, message, topic);
	}

	public void initRelationship(String relationName, MboSetRemote mboSet)
			throws MXException, RemoteException {
	}

	public Locale getClientLocale() {
		return this.mySet.getClientLocale();
	}

	public TimeZone getClientTimeZone() {
		return this.mySet.getClientTimeZone();
	}

	Hashtable getValueHash() {
		if (this.valueHash == null) {
			this.valueHash = new Hashtable();
		}
		return this.valueHash;
	}

	public MboValue getMboValue(String nameInput) throws MXException {
		String name = nameInput.toUpperCase();

		//System.out.println("enter 1");
		MboValue mv = (MboValue) getValueHash().get(name);
		//System.out.println("exit 1");
		if (mv != null) {
			return mv;
		}

		MboValueInfo mvInfo = getMboValueInfo(name);

		mv = generateMboValueInstance(mvInfo);

	//	System.out.println("enter 2");
		getValueHash().put(name, mv);
	//	System.out.println("exit 2");
		
		return mv;
	}

	private MboValueInfo getMboValueInfo(String attributeName)
			throws MXException {
		MboValueInfo mvInfo = getMboSetInfo().getMboValueInfo(attributeName);
		if (mvInfo == null) {
			if (getMboLogger().isWarnEnabled()) {
				getMboLogger().warn(
						"Mbo getMboValue, attribute " + attributeName
								+ " does not exist for object " + getName());
			}

			Object[] params = { attributeName };
			throw new MXApplicationException("system", "noattribute", params);
		}

		return mvInfo;
	}

	public void moveFieldFlagsToMboValue(MboValue mv) {
		String name = mv.getName();
		MXException mxe = (MXException) getFieldExceptions().get(name);
		BitFlag bf = (BitFlag) getFieldFlags().get(name);
		if (bf == null)
			return;
		getFieldFlags().remove(name);
		if (mxe != null) {
			getFieldExceptions().remove(name);
		}
		mv.setFlags(bf.getFlags(), mxe);
	}

	public MboValue getInstanciatedMboValue(String name) {
		name = name.toUpperCase();

		MboValue mv = (MboValue) getValueHash().get(name);

		return mv;
	}

	public MaxType getMboInitialValue(String name) throws MXException,
			RemoteException {
		return getMboValue(name).getInitialValue();
	}

	public MboSetRemote getInstanciatedMboSet(String relationshipName) {
		return ((MboSetRemote) getRelatedSets().get(
				relationshipName.toUpperCase()));
	}

	Hashtable getRelatedSets() {
		if (this.mboSets == null) {
			this.mboSets = new Hashtable();
		}
		return this.mboSets;
	}

	public MboValueData getMboValueData(String attribute) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attribute);

		if (mbo == this) {
			MboValue mv = getMboValue(attribute);

			if (isZombie()) {
				return mv.getMboValueData(true);
			}
			return mv.getMboValueData();
		}

		return mbo.getMboValueData(getAttributeName(attribute));
	}

	public MboValueData getMboValueData(String attribute,
			boolean ignoreFieldFlags) throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attribute);

		if (mbo == this) {
			MboValue mv = getMboValue(attribute);
			return mv.getMboValueData(ignoreFieldFlags);
		}

		return mbo.getMboValueData(getAttributeName(attribute),
				ignoreFieldFlags);
	}

	public MboValueData[] getMboValueData(String[] attribute)
			throws MXException, RemoteException {
		MboValueData[] result = new MboValueData[attribute.length];

		for (int i = 0; i < attribute.length; ++i) {
			try {
				result[i] = getMboValueData(attribute[i]);
			} catch (MXException e) {
				result[i] = null;
			}
		}

		return result;
	}

	public MboData getMboData(String[] attributes) {
		if (isZombie()) {
			return null;
		}

		return new MboData(this, attributes);
	}

	public MboValueInfoStatic getMboValueInfoStatic(String attribute)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttributeStatic(attribute);

		if (mbo == this) {
			MboValueInfoStatic mvis = MXServer.getMXServer().getMaximoMLDD()
					.getMboValueInfoStatic(getUserInfo(), getName(), attribute);
			if (mvis != null)
				mvis.setFromMboValueInfo(getMboValueInfo(attribute));
			return mvis;
		}

		return mbo.getMboValueInfoStatic(getAttributeName(attribute));
	}

	public MboValueInfoStatic[] getMboValueInfoStatic(String[] attribute)
			throws MXException, RemoteException {
		MboValueInfoStatic[] result = new MboValueInfoStatic[attribute.length];

		for (int i = 0; i < attribute.length; ++i) {
			try {
				result[i] = getMboValueInfoStatic(attribute[i]);
			} catch (MXException e) {
				result[i] = null;
			}
		}
		return result;
	}

	MboRemote getMboForAttribute(String attributeName) throws MXException,
			RemoteException {
		int index = attributeName.indexOf(46);
		if (index != -1) {
			MboSetRemote ms = getMboSet(attributeName.substring(0, index)
					.toUpperCase());
			if (ms != null) {
				MboRemote mbo = null;
				if (isZombie()) {
					mbo = ms.getZombie();
				} else {
					mbo = ms.getMbo(0);
					if (mbo == null)
						mbo = ms.getZombie();
				}
				if (mbo != null) {
					return mbo;
				}
				throw new MXApplicationException("system", "nocurrentmbo");
			}
		}

		return this;
	}

	MboRemote getMboForAttributeStatic(String attributeName)
			throws MXException, RemoteException {
		int index = attributeName.indexOf(46);
		if (index != -1) {
			MboSetRemote ms = getMboSet(attributeName.substring(0, index)
					.toUpperCase());
			if (ms != null) {
				MboRemote mbo = null;
				if (isZombie())
					mbo = ms.getZombie();
				else
					mbo = ms.getMbo(0);
				if (mbo != null) {
					return mbo;
				}
				return ms.getZombie();
			}
		}

		return this;
	}

	String getAttributeName(String attributeName) {
		int index = attributeName.indexOf(46);
		if (index != -1) {
			return attributeName.substring(index + 1);
		}
		return attributeName;
	}

	public MboSetRemote getList(String attribute) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attribute);
		MboSetRemote ms = null;

		if (mbo == this) {
			MboValue mv = getMboValue(attribute);
			ms = mv.getList();
			if (ms != null) {
				ms.setTableDomainLookup(true);
			}
			return ms;
		}
		ms = mbo.getList(getAttributeName(attribute));
		ms.setTableDomainLookup(true);
		return ms;
	}

	public MboSetRemote smartFill(String attribute, String value, boolean exact)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attribute);

		if (mbo == this) {
			MboValue mv = getMboValue(attribute);
			return mv.smartFill(value, exact);
		}

		return mbo.smartFill(getAttributeName(attribute), value, exact);
	}

	public MboSetRemote smartFind(String attribute, String value, boolean exact)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attribute);

		if (mbo == this) {
			MboValue mv = getMboValue(attribute);
			return mv.smartFind(value, exact);
		}

		return mbo.smartFind(getAttributeName(attribute), value, exact);
	}

	public MboSetRemote smartFind(String appName, String attribute,
			String value, boolean exact) throws MXException, RemoteException {
		SqlFormat sqf = new SqlFormat("app=:1");
		sqf.setObject(1, "maxapps", "app", appName);
		String objectName = getMboSet("$$maxapp" + appName, "maxapps",
				sqf.format()).getMbo(0).getString("maintbname");
		return smartFindByObjectName(objectName, attribute, value, exact);
	}

	public MboSetRemote smartFindByObjectName(String sourceObj,
			String targetAttrName, String value, boolean exact,
			String[][] cachedKeyMap) throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(targetAttrName);

		if (mbo == this) {
			MboSetRemote retSet = getMboSet("$$tempset" + sourceObj, sourceObj,
					"1=1");

			if (!(retSet.getMboSetInfo().isPersistent())) {
				return null;
			}

			boolean foundTargetAttr = false;
			for (int i = cachedKeyMap.length - 1; i >= 0; --i) {
				String qbeValue = null;
				if ((!(foundTargetAttr))
						&& (cachedKeyMap[i][0].equalsIgnoreCase(targetAttrName))) {
					foundTargetAttr = true;
					qbeValue = value;
				} else {
					qbeValue = (isNull(cachedKeyMap[i][0])) ? null
							: getString(cachedKeyMap[i][0]);
				}

				if (qbeValue == null) {
					continue;
				}
				if (exact) {
					retSet.setQbeExactMatch(true);
					retSet.setQbe(cachedKeyMap[i][1], qbeValue);
				} else {
					retSet.setQbeExactMatch(false);
					retSet.setQbeCaseSensitive(false);
					String searchType = getMboValue(cachedKeyMap[i][0])
							.getMboValueInfo().getSearchType();
					int maxtype = getMboValue(cachedKeyMap[i][0]).getType();
					if ((maxtype == 8) || (maxtype == 9) || (maxtype == 6)
							|| (maxtype == 7) || (maxtype == 11)) {
						retSet.setQbe(cachedKeyMap[i][1], qbeValue);
					}

					retSet.setQbe(cachedKeyMap[i][1], qbeValue + "%");
				}

			}

			retSet.reset();
			return retSet;
		}

		return mbo.smartFindByObjectName(sourceObj,
				getAttributeName(targetAttrName), value, exact);
	}

	public MboSetRemote smartFindByObjectNameDirect(String sourceObj,
			String targetAttrName, String value, boolean exact)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(targetAttrName);

		if (mbo == this) {
			Object[] lookupMap = getMatchingAttrs(sourceObj, targetAttrName);
			if (lookupMap == null) {
				return null;
			}
			String[][] cachedKeyMap = new String[lookupMap.length][2];
			int k = 0;
			String[] tmp = new String[lookupMap.length];
			for (int i = 0; i < lookupMap.length; ++i) {
				cachedKeyMap[i][0] = ((String) ((Object[]) lookupMap[i])[0]);
				cachedKeyMap[i][1] = ((String) ((Object[]) lookupMap[i])[1]);
				if ((((Boolean) ((Object[]) lookupMap[i])[2]).booleanValue())
						|| (cachedKeyMap[i][0].equalsIgnoreCase(targetAttrName))) {
					continue;
				}
				tmp[k] = cachedKeyMap[i][0];
				++k;
			}

			return smartFindByObjectName(sourceObj, targetAttrName, value,
					exact, cachedKeyMap);
		}

		return mbo.smartFindByObjectName(sourceObj,
				getAttributeName(targetAttrName), value, exact);
	}

	public MboSetRemote smartFindByObjectName(String sourceObj,
			String targetAttrName, String value, boolean exact)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(targetAttrName);

		if (mbo == this) {
			MboValue mv = getMboValue(targetAttrName);
			MboSetRemote found = null;
			try {
				found = mv.smartFind(sourceObj, value, exact);
			} catch (MXApplicationException e) {
			}
			if (found != null) {
				return found;
			}

			return smartFindByObjectNameDirect(sourceObj,
					getAttributeName(targetAttrName), value, exact);
		}

		return mbo.smartFindByObjectName(sourceObj,
				getAttributeName(targetAttrName), value, exact);
	}

	public String getString(String attributeName) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			return mv.getString();
		}

		return mbo.getString(getAttributeName(attributeName));
	}

	public boolean getBoolean(String attributeName) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			return mv.getBoolean();
		}

		return mbo.getBoolean(getAttributeName(attributeName));
	}

	public byte getByte(String attributeName) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			return mv.getByte();
		}

		return mbo.getByte(getAttributeName(attributeName));
	}

	public int getInt(String attributeName) throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			return mv.getInt();
		}

		return mbo.getInt(getAttributeName(attributeName));
	}

	public long getLong(String attributeName) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			return mv.getLong();
		}

		return mbo.getLong(getAttributeName(attributeName));
	}

	public float getFloat(String attributeName) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			return mv.getFloat();
		}

		return mbo.getFloat(getAttributeName(attributeName));
	}

	public double getDouble(String attributeName) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			return mv.getDouble();
		}

		return mbo.getDouble(getAttributeName(attributeName));
	}

	public Date getDate(String attributeName) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			return mv.getDate();
		}

		return mbo.getDate(getAttributeName(attributeName));
	}

	public byte[] getBytes(String attributeName) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			return mv.getBytes();
		}

		return mbo.getBytes(getAttributeName(attributeName));
	}

	public void setValue(String attributeName, String val, long accessModifier)
			throws MXException, RemoteException {
		if (attributeName.equals("COMMODITYGROUP")){
			new Throwable().printStackTrace();
		}
		MboRemote mbo = getMboForAttribute(attributeName);
		String value = null;

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValue(val, accessModifier);
		} else {
			mbo.setValue(getAttributeName(attributeName), val, accessModifier);
		}
	}

	public void setValue(String attributeName, String val) throws MXException,
			RemoteException {
		setValue(attributeName, val, 0L);
	}

	public void setValue(String attributeName, boolean val) throws MXException,
			RemoteException {
		setValue(attributeName, val, 0L);
	}

	public void setValue(String attributeName, boolean val, long accessModifier)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValue(val, accessModifier);
		} else {
			mbo.setValue(getAttributeName(attributeName), val, accessModifier);
		}
	}

	public void setValue(String attributeName, byte val) throws MXException,
			RemoteException {
		setValue(attributeName, val, 0L);
	}

	public void setValue(String attributeName, byte val, long accessModifier)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValue(val, accessModifier);
		} else {
			mbo.setValue(getAttributeName(attributeName), val, accessModifier);
		}
	}

	public void setValue(String attributeName, int val) throws MXException,
			RemoteException {
		setValue(attributeName, val, 0L);
	}

	public void setValue(String attributeName, int val, long accessModifier)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValue(val, accessModifier);
		} else {
			mbo.setValue(getAttributeName(attributeName), val, accessModifier);
		}
	}

	public void setValue(String attributeName, float val) throws MXException,
			RemoteException {
		setValue(attributeName, val, 0L);
	}

	public void setValue(String attributeName, float val, long accessModifier)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValue(val, accessModifier);
		} else {
			mbo.setValue(getAttributeName(attributeName), val, accessModifier);
		}
	}

	public void setValue(String attributeName, byte[] val) throws MXException,
			RemoteException {
		setValue(attributeName, val, 0L);
	}

	public void setValue(String attributeName, byte[] val, long accessModifier)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValue(val, accessModifier);
		} else {
			mbo.setValue(getAttributeName(attributeName), val, accessModifier);
		}
	}

	public void setValue(String attributeName, Date val) throws MXException,
			RemoteException {
		setValue(attributeName, val, 0L);
	}

	public void setValue(String attributeName, Date val, long accessModifier)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValue(val, accessModifier);
		} else {
			mbo.setValue(getAttributeName(attributeName), val, accessModifier);
		}
	}

	public void setValue(String attributeName, short val) throws MXException,
			RemoteException {
		setValue(attributeName, val, 0L);
	}

	public void setValue(String attributeName, short val, long accessModifier)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValue(val, accessModifier);
		} else {
			mbo.setValue(getAttributeName(attributeName), val, accessModifier);
		}
	}

	public void setValue(String attributeName, long val) throws MXException,
			RemoteException {
		setValue(attributeName, val, 0L);
	}

	public void setValue(String attributeName, long val, long accessModifier)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValue(val, accessModifier);
		} else {
			mbo.setValue(getAttributeName(attributeName), val, accessModifier);
		}
	}

	public void setValue(String attributeName, double val) throws MXException,
			RemoteException {
		setValue(attributeName, val, 0L);
	}

	public void setValue(String attributeName, double val, long accessModifier)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValue(val, accessModifier);
		} else {
			mbo.setValue(getAttributeName(attributeName), val, accessModifier);
		}
	}

	public boolean isNull(String attributeName) throws MXException,
			RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			return mv.isNull();
		}

		return mbo.isNull(getAttributeName(attributeName));
	}

	public void setValueNull(String attributeName) throws MXException,
			RemoteException {
		setValueNull(attributeName, 0L);
	}

	public void setValueNull(String attributeName, long accessModifier)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(attributeName);

		if (mbo == this) {
			MboValue mv = getMboValue(attributeName);
			mv.setValueNull(accessModifier);
		} else {
			mbo.setValueNull(getAttributeName(attributeName), accessModifier);
		}
	}

	public KeyValue getKeyValue() throws MXException {
		String[] keys = getMboSetInfo().getKeyAttributes();
		if (keys.length == 0) {
			keys = new String[] { "rowstamp" };
		}

		return new KeyValue(keys);
	}

	public String getRecordIdentifer() throws MXException, RemoteException {
		String[] keys = getMboSetInfo().getKeyAttributes();
		StringBuffer sb = new StringBuffer(getName());
		for (int i = 0; i < keys.length; ++i) {
			sb.append(" ");
			sb.append(getMboValueInfo(keys[i]).getTitle());
			sb.append("=");
			sb.append(getString(keys[i]));
		}
		return sb.toString();
	}

	public boolean isAutoKeyed(String attributeName) throws MXException,
			RemoteException {
		MboValueInfo mvi = getMboValueInfo(attributeName);
		if (mvi == null) {
			return false;
		}
		String defaultValue = mvi.getDefaultValue();
		if (defaultValue == null) {
			return false;
		}
		defaultValue = defaultValue.trim();

		return (defaultValue.toUpperCase().equals("&AUTOKEY&"));
	}

	public MboSetRemote getMboSet(String name, String objectName)
			throws MXException, RemoteException {
		name = name.toUpperCase();
		if ("OPENWO".equals(name)){
			new Throwable().printStackTrace();
		}

		MboSetRemote ms = (MboSetRemote) getRelatedSets().get(name);

		if (ms == null) {
			ms = getMboServer().getMboSet(objectName.toUpperCase(),
					getUserInfo());
			if (ms != null) {
				if (isFlagSet(8L)) {
					ms.setFlag(8L, true);
				}

				ms.setMXTransaction(this.mySet.getMXTransaction());

				ms.setOwner(this);

				getRelatedSets().put(name, ms);

				if (!(isZombie())) {
					initRelationship(name, ms);
				}
			}
		}
		if (this.mySet.isFlagSet(7L)) {
			ms.setFlag(7L, true);
		}

		if ((isFlagSet(7L)) && (this.readonlyForSite))
			ms.setFlag(7L, true);
		return ms;
	}

	public MboSetRemote getMboSet(String name, String objectName,
			String relationship) throws MXException, RemoteException {
		boolean relationshipWhereHasNullBindValue = false;
		if ("OPENWO".equals(name)){
			new Throwable().printStackTrace();
		}
		MboSetRemote ms = getMboSet(name, objectName);

		SqlFormat sf = new SqlFormat(this, relationship);
		String whereClause;
		if (isZombie()) {
			whereClause = relationship;
		} else {
			whereClause = sf.format();
			MXException e;
			if ((e = sf.getEncounteredError()) != null)
				throw e;
		}
		if (sf.hasNullBoundValue()) {
			relationshipWhereHasNullBindValue = true;
		} else
			relationshipWhereHasNullBindValue = false;
		if (ms != null) {
			String currWhere = ms.getRelationship();

			if (!(currWhere.equals(whereClause))) {
				if (relationshipWhereHasNullBindValue)
					ms.setNoNeedtoFetchFromDB(true);
				else {
					ms.setNoNeedtoFetchFromDB(false);
				}
			}

			if ((!(currWhere.equals(whereClause)))
					|| ((ms.hasQbe()) && (ms.getSize() <= 0) && (!(isZombie())))) {
				if (getMboLogger().isDebugEnabled()) {
					getMboLogger().debug(
							"Jump to foreign MboSet : " + name + " - "
									+ objectName + " - " + whereClause);
				}

				ms.resetQbe();
				ms.reset();
				ms.setRelationship(whereClause);
				ms.setRelationName(name);
				if (!(isZombie())) {
					initRelationship(name, ms);
				}
			}
		}
		if (this.mySet.isFlagSet(1L)) {
			ms.setFlag(1L, true);
		}
		if (this.mySet.isFlagSet(4L)) {
			ms.setFlag(4L, true);
		}
		if (this.mySet.isFlagSet(2L)) {
			ms.setFlag(2L, true);
		}
		if (this.mySet.isFlagSet(8L)) {
			ms.setFlag(8L, true);
		}

		if ((isFlagSet(7L)) && (this.readonlyForSite))
			ms.setFlag(7L, true);
		return ms;
	}

	public MboSetRemote getMboSet(String name) throws MXException,
			RemoteException {
		if ("OPENWO".equals(name)){
			new Throwable().printStackTrace();
		}
		MboSetRemote mboSet = null;
		name = name.toUpperCase();

		RelationInfo ri = getMboSetInfo().getRelationInfo(name);
		if (ri == null) {
			Object[] params = { name, getName() };
			throw new MXSystemException("system", "norelationship", params);
		}

		mboSet = getMboSet(name, ri.getDest(), ri.getSqlExpr());

		return mboSet;
	}

	public Vector getMboDataSet(String relationship) throws RemoteException,
			MXException {
		return getMboDataSet(parseRelationship(relationship));
	}

	private Vector getMboDataSet(Stack relationship) throws RemoteException,
			MXException {
		Vector ret = new Vector();

		if (relationship.size() == 0) {
			ret.add(this);
		} else {
			String nextRel = (String) relationship.pop();
			MboSetEnumeration mse = new MboSetEnumeration(getMboSet(nextRel));
			while (mse.hasMoreElements()) {
				Vector v = ((Mbo) mse.nextMbo()).getMboDataSet(relationship);
				ret.addAll(v);
			}

			relationship.push(nextRel);
		}

		return ret;
	}

	private Stack parseRelationship(String relationship) {
		if (relationship.startsWith(":"))
			relationship = relationship.substring(1);
		Stack retStack = new Stack();
		int lastDot = relationship.lastIndexOf(46);
		while (lastDot != -1) {
			String part = relationship.substring(lastDot + 1);
			retStack.push(part);

			relationship = relationship.substring(0, lastDot);
			lastDot = relationship.lastIndexOf(46);
		}
		retStack.push(relationship);

		return retStack;
	}

	protected void save() throws MXException, RemoteException {
		setRecordType();

		setSenderSysId();

		this.checkpoint = false;
	}

	protected void commit() {
	}

	private void setSenderSysId() throws RemoteException {
		if (!(toBeSaved())) {
			return;
		}
		MXTransaction txn = getMXTransaction();
		if ((txn == null) || (!(txn.getBoolean("integration"))))
			return;
		try {
			MboValue mboValue = getMboValue("SENDERSYSID");
			mboValue.setFlag(16L, true);
			mboValue.setValue(txn.getString("sender"), 2L);
		} catch (MXException mxe) {
			return;
		}
	}

	public boolean isValid() {
		return (!(isZombie()));
	}

	public boolean isZombie() {
		return this.zombie;
	}

	void setZombie(boolean isZombie) {
		this.zombie = isZombie;
	}

	Map getModifiedPersistentAttributeValues() {
		Hashtable vh = getValueHash();
		HashMap modifiedPersistentAttributes = null;

		for (Enumeration e = vh.keys(); e.hasMoreElements();) {
			String attributeName = (String) e.nextElement();
			MboValue mv = (MboValue) vh.get(attributeName);
			if ((mv == null) || (!(mv.isPersistent())) || (!(mv.isModified())))
				continue;
			if (modifiedPersistentAttributes == null) {
				modifiedPersistentAttributes = new HashMap();
			}

			modifiedPersistentAttributes.put(attributeName, mv);
		}

		return modifiedPersistentAttributes;
	}

	Map getPersistentAttributeValues() throws MXException {
		Hashtable vh = getValueHash();
		HashMap persistentAttributes = null;
		boolean storeClobAsClob = getMboServer().getMaximoDD()
				.storeClobAsClob();
		boolean storeBlobAsBlob = getMboServer().getMaximoDD()
				.storeBlobAsBlob();

		for (Enumeration e = vh.keys(); e.hasMoreElements();) {
			String attributeName = (String) e.nextElement();
			MboValue mv = (MboValue) vh.get(attributeName);

			if (((mv == null)
					&& (((mv.getType() != 17) || (!(storeClobAsClob)))) && (((mv
					.getType() != 18) || (!(storeBlobAsBlob)))))
					|| (!(mv.isPersistent()))) {
				continue;
			}

			if (persistentAttributes == null) {
				persistentAttributes = new HashMap();
			}

			persistentAttributes.put(attributeName, mv);
		}
		MboValueInfo mvi;
		Enumeration e2;
		if ((storeClobAsClob) || (storeBlobAsBlob)) {
			mvi = null;

			for (e2 = getMboSetInfo().getMboValuesInfo(); e2.hasMoreElements();) {
				mvi = (MboValueInfo) e2.nextElement();
				String attributeName2 = mvi.getAttributeName();
				System.out.println("*****************"+attributeName2);

				if ((!(persistentAttributes.containsKey(attributeName2)))
						&& (mvi.isPersistent())
						&& ((((mvi.getTypeAsInt() == 17) && (storeClobAsClob)) || ((mvi
								.getTypeAsInt() == 18)
								&& (storeBlobAsBlob) && (!(mvi.isRequired())))))) {
					try {
						MboValue mv2 = getMboValue(attributeName2);
						persistentAttributes.put(attributeName2, mv2);
					} catch (Exception e) {
						throw new MXApplicationException("system", "clobError");
					}
				}
			}
		}

		return persistentAttributes;
	}

	public void delete(long accessModifier) throws MXException, RemoteException {
		if (toBeDeleted()) {
			return;
		}

		if (isZombie()) {
			return;
		}

		checkMethodAccess("delete", accessModifier);

		if (((accessModifier & 0x10) == 16L)
				&& (getThisMboSet().isESigNeeded("DELETE"))) {
			MboValueInfo info = null;
			Enumeration e = getThisMboSet().getMboSetInfo().getMboValuesInfo();
			do {
				if (!(e.hasMoreElements()))
					return;
				info = (MboValueInfo) e.nextElement();
			} while ((info == null) || (!(info.isESigEnabled()))
					|| (isNull(info.getAttributeName())));

			setESigFieldModified(true);
		}

		MboSetInfo mi = getMboSetInfo();
		Iterator longDescAttributes = mi.getLongDescriptionAttributes();
		if (longDescAttributes.hasNext()) {
			MboValueInfo mvInfo = (MboValueInfo) longDescAttributes.next();

			String attributeName = mvInfo.getAttributeName();

			getString(attributeName + "_LONGDESCRIPTION");

			String relName = "__LONGDESCRIPTION";

			MboSetRemote ms = (MboSet) getRelatedSets().get(relName);
			if (ms != null) {
				ms.deleteAll();
			}

			if ((ms != null)
					&& (ms.getRelationship().indexOf("langcode") != -1)) {
				String uniqueid = getMboSetInfo().getUniqueIDName();
				SqlFormat sqf = new SqlFormat(this, "ldkey=:" + uniqueid
						+ " and ldownertable='" + getName()
						+ "' and langcode!=:2 and langcode!=:3");
				sqf.setObject(2, "longdescription", "langcode", getUserInfo()
						.getLangCode());
				sqf.setObject(3, "longdescription", "langcode", getMboServer()
						.getMaxVar().getString("BASELANGUAGE", ""));
				ms = getMboSet("$$longdescrest", "longdescription", sqf
						.format());
				ms.deleteAll();
			}
		}

		this.markedForDelete = true;
		getThisMboSet().incrementDeletedCount(true);

		if ((!(this.newmbo)) || (!(toBeDeleted()))
				|| (!(isESigFieldModified())))
			return;
		setESigFieldModified(false);
		this.eSigFlagForDelete = true;
	}

	public void delete() throws MXException, RemoteException {
		delete(0L);
	}

	public void canDelete() throws MXException, RemoteException {
	}

	public void undelete() throws MXException, RemoteException {
		if (!(toBeDeleted())) {
			return;
		}

		if (isZombie()) {
			return;
		}

		String[] keyInfo = getMboSetInfo().getKeyAttributes();
		boolean keyIsNull = false;
		if (keyInfo != null) {
			for (int i = 0; i < keyInfo.length; ++i) {
				if (getMboValue(keyInfo[i]).isNull()) {
					keyIsNull = true;
				}
			}
			if (!(keyIsNull)) {
				MboSet newMboSet = (MboSet) getThisMboSet();
				int numDuplicateKey = 0;
				int numKeys = keyInfo.length;

				for (int i = 0; i < newMboSet.getSize(); ++i) {
					Mbo nextMbo = (Mbo) newMboSet.getMbo(i);
					if (!(nextMbo.toBeAdded()))
						continue;
					int m = 0;
					boolean keyMatch = true;
					while ((keyMatch) && (m < numKeys)) {
						keyMatch = getString(keyInfo[m]).equals(
								nextMbo.getString(keyInfo[m]));
						++m;
					}
					if (keyMatch == true) {
						throw new MXApplicationException("system",
								"undeletedup");
					}
				}
			}

		}

		MboSetInfo mi = getMboSetInfo();
		Iterator longDescAttributes = mi.getLongDescriptionAttributes();
		while (longDescAttributes.hasNext()) {
			MboValueInfo mvInfo = (MboValueInfo) longDescAttributes.next();

			String attributeName = mvInfo.getAttributeName();

			getString(attributeName + "_LONGDESCRIPTION");

			String relName = "__LONGDESCRIPTION" + attributeName
					+ "_LONGDESCRIPTION";

			MboSetRemote ms = (MboSet) getRelatedSets().get(relName);
			if (ms != null) {
				ms.undeleteAll();
			}
		}

		if ((this.newmbo) && (toBeDeleted()) && (this.eSigFlagForDelete)
				&& (!(isESigFieldModified()))) {
			setESigFieldModified(true);
			this.eSigFlagForDelete = false;
		}

		this.markedForDelete = false;
		getThisMboSet().incrementDeletedCount(false);
	}

	public boolean toBeDeleted() {
		if (isZombie()) {
			return false;
		}
		return this.markedForDelete;
	}

	public boolean toBeAdded() {
		if (isZombie()) {
			return false;
		}
		return ((this.newmbo) && (!(toBeDeleted())));
	}

	public boolean isNew() throws RemoteException {
		if (isZombie()) {
			return false;
		}
		return this.newmbo;
	}

	public boolean toBeUpdated() throws RemoteException {
		if (isZombie()) {
			return false;
		}

		return ((!(this.newmbo)) && (toBeSaved()));
	}

	public boolean thisToBeUpdated() throws RemoteException {
		if (isZombie()) {
			return false;
		}

		return ((!(this.newmbo)) && (isModified()));
	}

	public boolean isModified() {
		return this.modified;
	}

	public boolean isModified(String attribute) throws MXException,
			RemoteException {
		MboValue mv = getMboValue(attribute);
		return mv.isModified();
	}

	public void setESigFieldModified(boolean eSigFieldModified)
			throws RemoteException {
		this.eSigFieldModified = eSigFieldModified;
		this.mySet.setESigFieldModified(eSigFieldModified);
	}

	public boolean isESigFieldModified() {
		return this.eSigFieldModified;
	}

	public void setEAuditFieldModified(boolean eAuditFieldModified)
			throws RemoteException {
		this.eAuditFieldModified = eAuditFieldModified;
		this.mySet.setEAuditFieldModified(eAuditFieldModified);
	}

	public boolean isEAuditFieldModified() {
		return this.eAuditFieldModified;
	}

	public boolean toBeValidated() throws RemoteException {
		if (toBeSaved()) {
			if ((getOwner() != null) && (this instanceof LinkedMboRemote)
					&& (toBeDeleted())) {
				return true;
			}

			if (!(toBeDeleted())) {
				return true;
			}
		}
		return false;
	}

	public boolean toBeSaved() throws RemoteException {
		if ((this.newmbo) && (toBeDeleted())) {
			return false;
		}

		if ((getOwner() != null) && (getOwner().isNew())
				&& (getOwner().toBeDeleted())) {
			return false;
		}
		if ((isModified()) || (toBeDeleted()) || (toBeAdded())) {
			return true;
		}
		for (Enumeration e = getRelatedSets().elements(); e.hasMoreElements();) {
			MboSetRemote mbo = (MboSetRemote) e.nextElement();
			if (mbo.toBeSaved()) {
				return true;
			}
		}

		return false;
	}

	public void appValidate() throws MXException, RemoteException {
	}

	public final void validate() throws MXException, RemoteException {
		if (this.validated) {
			return;
		}

		Hashtable errors = validateAttributes();
		if (errors.size() >= 1) {
			Enumeration enum = errors.keys();

			while (enum.hasMoreElements()) {
				String key = (String) enum.nextElement();
				Exception e = (Exception) errors.get(key);

				if (getMboLogger().isErrorEnabled()) {
					getMboLogger().error(
							"Mbo batch validation error, Mboset :" + getName()
									+ ", error :" + key, e);
				}

			}

			Enumeration enum1 = errors.keys();
			String key = (String) enum1.nextElement();
			Exception e = (Exception) errors.get(key);

			if (e instanceof MXException)
				throw ((MXException) e);
			if (e instanceof RemoteException) {
				throw ((RemoteException) e);
			}

			throw new MXSystemException("system", "fieldvalidationerror",
					new String[] { key }, e);
		}

		for (Enumeration e = getMboSetInfo().getMboValuesInfo(); e
				.hasMoreElements();) {
			MboValueInfo mvi = (MboValueInfo) e.nextElement();

			if (mvi == null)
				continue;
			String name = mvi.getName().toUpperCase();

			MboValue mbv = (MboValue) getValueHash().get(name);
			if (mbv == null) {
				BitFlag fieldFlag = (BitFlag) getFieldFlags().get(name);

				if (((fieldFlag != null) && (fieldFlag.isFlagSet(8L)))
						|| (mvi.isRequired())) {
					MXException mxe = (MXException) getFieldExceptions().get(
							name);

					MboValue mv = getMboValue(name);
					if ((mv == null) || (mv.isNull())) {
						if (mxe != null) {
							throw mxe;
						}

						if (getMboLogger().isErrorEnabled()) {
							getMboLogger().error(
									"Mbo Validate, Mboset :" + getName()
											+ ", field :" + mvi.getName());
						}

						String[] keyAtts = getMboSetInfo().getKeyAttributes();
						Object[] p = new Object[keyAtts.length + 1];

						String title = mv.getColumnTitle();
						if ((title != null) && (title.indexOf(",") >= 0)) {
							StringBuffer strBuff = new StringBuffer(title);
							strBuff = strBuff.deleteCharAt(title.indexOf(","));
							title = strBuff.toString();
						}

						p[0] = title;

						for (int i = 1; i < p.length; ++i) {
							p[i] = keyAtts[(i - 1)] + "="
									+ getString(keyAtts[(i - 1)]);
						}
						throw new MXApplicationException("system", "null", p);
					}
				}

			} else if ((mbv.isNull()) && (mbv.isRequired())) {
				MXException mxe = mbv.getMXException();
				if (mxe != null) {
					throw mxe;
				}

				if (getMboLogger().isErrorEnabled()) {
					getMboLogger().error(
							"Mbo Validate, Mboset :" + getName() + ", field :"
									+ mvi.getName());
				}

				String[] keyAtts = getMboSetInfo().getKeyAttributes();
				Object[] p = new Object[keyAtts.length + 1];

				String title = mbv.getColumnTitle();
				if ((title != null) && (title.indexOf(",") >= 0)) {
					StringBuffer strBuff = new StringBuffer(title);
					strBuff = strBuff.deleteCharAt(title.indexOf(","));
					title = strBuff.toString();
				}

				p[0] = title;

				for (int i = 1; i < p.length; ++i) {
					p[i] = keyAtts[(i - 1)] + "=" + getString(keyAtts[(i - 1)]);
				}
				throw new MXApplicationException("system", "null", p);
			}

		}

		appValidate();

		this.validated = true;
	}

	boolean checkAccessForDelayedValidation() {
		return this.accessCheckForDelayedValidation;
	}

	public Hashtable validateAttributes() throws RemoteException {
		Hashtable errors = new Hashtable();
		MboValue mbv;
		Enumeration e;
		try {
			ojsa=true;
			System.out.println("Getting value hash from validateAttrs");
		//	System.out.println("Klasa::"+getValueHash().getClass().getName());
			Hashtable valHash=null;
			try {
			System.out.println("MBO:"+getClass().getClassLoader());
				System.out.println("EEEE");
				Object davidim= getValueHash();
				ojsa=false;
				valHash=(Hashtable)davidim;
				//valHash = getValueHash();
			} catch (ClassCastException e2) {
				// TODO Auto-generated catch block
				System.out.println("MOSHA"+e2.getLocalizedMessage());
				e2.printStackTrace();
			}
			System.out.println(valHash.getClass().getName());
			Hashtable tempVals = (Hashtable) ((Hashtable)valHash).clone();

			if (tempVals == null) {
				return errors;
			}
			String[] valOrder = getValidateOrder();
			for (int i = 0; i < valOrder.length; ++i) {
				valOrder[i] = valOrder[i].toUpperCase();

				mbv = (MboValue) tempVals.get(valOrder[i]);

				if ((mbv == null) || (!(mbv.isToBeValidated())))
					continue;
				this.accessCheckForDelayedValidation = false;
				try {
					mbv
							.validate(mbv.getCurrentFieldAccess() & 0xFFFFFFFB & 0xFFFFFFFE & 0xFFFFFFF7);
				} catch (Exception ex) {
					errors.put(mbv.getName(), ex);
				}
				tempVals.remove(valOrder[i]);
				this.accessCheckForDelayedValidation = true;
			}

			for (e = tempVals.elements(); e.hasMoreElements();) {
				mbv = (MboValue) e.nextElement();
				if (mbv.isToBeValidated()) {
					this.accessCheckForDelayedValidation = false;
					try {
						mbv
								.validate(mbv.getCurrentFieldAccess() & 0xFFFFFFFB & 0xFFFFFFFE & 0xFFFFFFF7);
					} catch (Exception e1) {
						errors.put(mbv.getName(), e1);
					}
					this.accessCheckForDelayedValidation = true;
				}

			}

			tempVals = (Hashtable) getValueHash().clone();
			for (e = tempVals.elements(); e.hasMoreElements();) {
				mbv = (MboValue) e.nextElement();
				if (mbv.isToBeValidated()) {
					mbv.setToBeValidated(false);
				}

			}

		} catch (Throwable t) {
			t.printStackTrace();
		}
		return errors;
	}

	public String[] getValidateOrder() {
		return new String[0];
	}

	protected final void setDatabaseDefaultValues() throws MXException {
		setDatabaseDefaultValues(true);
	}

	protected final void setDatabaseDefaultValues(boolean setAutoKey)
			throws MXException {
		for (Enumeration e = getMboSetInfo().getMboValuesInfo(); e
				.hasMoreElements();) {
			try {
				MboValueInfo mvi = (MboValueInfo) e.nextElement();
				String dv = mvi.getDefaultValue();

				if ((dv != null) && (getMboValue(mvi.getName()).isNull())) {
					if (dv.toUpperCase().equals("&USERNAME&"))
						setValue(mvi.getName(), getUserInfo().getUserName());
					else if (dv.toUpperCase().equals("&SYSDATE&"))
						setValue(mvi.getName(), MXServer.getMXServer()
								.getDate());
					else if (dv.toUpperCase().equals("&AUTOKEY&")) {
						if (setAutoKey) {
							if ((!(this instanceof HierarchicalMboRemote))
									|| (getOwner() == null)
									|| (!(getOwner() instanceof HierarchicalMboRemote))) {
								getMboValue(mvi.getName()).autoKey();
							}

						}

					} else if ((mvi.getMaxType().equalsIgnoreCase("INTEGER"))
							|| (mvi.getMaxType().equalsIgnoreCase("SMALLINT"))) {
						setValue(mvi.getName(), MXFormat.stringToLong(dv,
								Locale.getDefault()));
					} else if ((mvi.getMaxType().equalsIgnoreCase("DECIMAL"))
							|| (mvi.getMaxType().equalsIgnoreCase("FLOAT"))) {
						setValue(mvi.getName(), MXFormat.stringToDouble(dv,
								Locale.getDefault()));
					} else if (mvi.getMaxType().equalsIgnoreCase("DURATION"))
						setValue(mvi.getName(), MXFormat.durationToDouble(dv,
								Locale.getDefault()));
					else if (mvi.getMaxType().equalsIgnoreCase("AMOUNT"))
						setValue(mvi.getName(), MXFormat.stringToAmount(dv,
								Locale.getDefault()));
					else {
						setValue(mvi.getName(), dv);
					}
				}

			} catch (Exception ex) {
				if (ex instanceof MXException) {
					throw ((MXException) ex);
				}
				ex.printStackTrace();
			}
		}
	}

	final void setDefaultSiteOrgSetValues() {
		try {
			int siteOrgType = getMboSetInfo().getSiteOrgType();

			if (siteOrgType == 1) {
				String site = getInsertSite();
				if (site != null) {
					setValue("SiteID", site, 11L);

					String org = MXServer.getMXServer().getMaximoDD().getOrgId(
							site);
					setValue("OrgID", org, 11L);
				}

			}

			if (siteOrgType == 2) {
				String org = getInsertOrganization();
				if (org != null) {
					setValue("OrgID", org, 11L);
				}
			}

			if (siteOrgType == 3) {
				String itemSetId = getInsertItemSetId();
				if (itemSetId != null) {
					setValue("itemsetid", itemSetId, 11L);
				}
			}

			if (siteOrgType == 4) {
				String companySetId = getInsertCompanySetId();
				if (companySetId != null) {
					setValue("companysetid", companySetId, 11L);
				}

			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public String getInsertItemSetId() throws MXException, RemoteException {
		MboRemote ownerMbo = getThisMboSet().getOwner();
		if ((ownerMbo == null) || (ownerMbo.isZombie())) {
			if ((this.mySet.getInsertItemSet() != null)
					&& (this.mySet.getInsertItemSet().trim().length() > 0)) {
				return this.mySet.getInsertItemSet();
			}

			String userCurrentItemSet = getProfile().getInsertItemSet();
			return userCurrentItemSet;
		}

		MboSetInfo mi = getMboSetInfo(ownerMbo.getName());
		int ownerMboSiteOrgType = mi.getSiteOrgType();
		if (ownerMboSiteOrgType == 3) {
			return ownerMbo.getString("itemsetid");
		}

		String userCurrentItemSet = getProfile().getInsertItemSet();
		return userCurrentItemSet;
	}

	public String getInsertCompanySetId() throws MXException, RemoteException {
		MboRemote ownerMbo = getThisMboSet().getOwner();
		if ((ownerMbo == null) || (ownerMbo.isZombie())) {
			if ((this.mySet.getInsertCompanySet() != null)
					&& (this.mySet.getInsertCompanySet().trim().length() > 0)) {
				return this.mySet.getInsertCompanySet();
			}

			String userCurrentCompanySet = getProfile().getInsertCompanySet();
			return userCurrentCompanySet;
		}

		MboSetInfo mi = getMboSetInfo(ownerMbo.getName());
		int ownerMboSiteOrgType = mi.getSiteOrgType();
		if (ownerMboSiteOrgType == 4) {
			return ownerMbo.getString("companysetid");
		}

		String userCurrentCompanySet = getProfile().getInsertCompanySet();
		return userCurrentCompanySet;
	}

	public String getInsertSite() throws MXException, RemoteException {
		MboRemote ownerMbo = getThisMboSet().getOwner();
		if ((ownerMbo == null) || (ownerMbo.isZombie())) {
			if ((this.mySet.getInsertSite() != null)
					&& (this.mySet.getInsertSite().trim().length() > 0)) {
				return this.mySet.getInsertSite();
			}

			String userCurrentSite = getProfile().getInsertSite();
			return userCurrentSite;
		}

		MboSetInfo mi = getMboSetInfo(ownerMbo.getName());
		int ownerMboSiteOrgType = mi.getSiteOrgType();
		if ((ownerMboSiteOrgType == 1) || (ownerMboSiteOrgType == 8)) {
			return ownerMbo.getString("siteid");
		}

		String userCurrentSite = getProfile().getInsertSite();
		return userCurrentSite;
	}

	public String getInsertOrganization() throws MXException, RemoteException {
		MboRemote ownerMbo = getThisMboSet().getOwner();
		if ((ownerMbo == null) || (ownerMbo.isZombie())) {
			if ((this.mySet.getInsertOrg() != null)
					&& (this.mySet.getInsertOrg().trim().length() > 0)) {
				return this.mySet.getInsertOrg();
			}

			String userCurrentOrg = getProfile().getInsertOrg();

			return userCurrentOrg;
		}

		MboSetInfo mi = getMboSetInfo(ownerMbo.getName());
		int ownerMboSiteOrgType = mi.getSiteOrgType();
		if ((ownerMboSiteOrgType == 1) || (ownerMboSiteOrgType == 2)
				|| (ownerMboSiteOrgType == 7) || (ownerMboSiteOrgType == 10)
				|| (ownerMboSiteOrgType == 8)) {
			return ownerMbo.getString("orgid");
		}

		String userCurrentOrg = getProfile().getInsertOrg();
		return userCurrentOrg;
	}

	public String getOrgSiteForMaxvar(String maxvarName) throws MXException,
			RemoteException {
		String siteOrOrg = null;
		MaxVarServiceRemote maxvarServiceRemote = (MaxVarServiceRemote) MXServer
				.getMXServer().lookup("MAXVARS");
		String maxvarType = maxvarServiceRemote.getMaxVarType(maxvarName);
		if (maxvarType.equalsIgnoreCase("ORG")) {
			if (toBeAdded())
				siteOrOrg = getInsertOrganization();
			else {
				siteOrOrg = getString("orgid");
			}
		} else if (maxvarType.equalsIgnoreCase("SITE")) {
			if (toBeAdded())
				siteOrOrg = getInsertSite();
			else {
				siteOrOrg = getString("siteid");
			}
		}
		return siteOrOrg;
	}

	public void add() throws MXException, RemoteException {
	}

	public void modify() throws MXException, RemoteException {
	}

	public MboServerInterface getMboServer() {
		return this.mySet.getMboServer();
	}

	protected MboSetInfo getMboSetInfo() {
		return this.mySet.getMboSetInfo();
	}

	public MboSetInfo getMboSetInfo(String mboName) {
		return getMboServer().getMaximoDD().getMboSetInfo(mboName);
	}

	public Translate getTranslator() {
		return getMboServer().getMaximoDD().getTranslator();
	}

	/** @deprecated */
	public void setFlags(long flags) {
		getUserFlags().setFlags(flags);
	}

	BitFlag getUserFlags() {
		if (this.userFlags == null) {
			this.userFlags = new BitFlag();
		}
		return this.userFlags;
	}

	/** @deprecated */
	public long getFlags() {
		return getUserFlags().getFlags();
	}

	public void setFlag(long flag, boolean state) {
		getUserFlags().setFlag(flag, state);
	}

	public void setFlag(long flag, boolean state, MXException mxe) {
		this.mxException = mxe;
		setFlag(flag, state);
	}

	public boolean isFlagSet(long flag) {
		return getUserFlags().isFlagSet(flag);
	}

	Hashtable getFieldFlags() {
		if (this.fieldFlags == null) {
			this.fieldFlags = new Hashtable();
		}
		return this.fieldFlags;
	}

	public Hashtable getFieldExceptions() {
		if (this.fieldExceptions == null) {
			this.fieldExceptions = new Hashtable();
		}
		return this.fieldExceptions;
	}

	/** @deprecated */
	public void setFieldFlags(String name, long flags) {
		setFieldFlag(name, flags, true);
	}

	public void setFieldFlag(String name, long flag, boolean state) {
		setFieldFlag(name, flag, state, null);
	}

	public void setFieldFlag(String name, long flag, boolean state,
			MXException mxe) {
		name = name.toUpperCase();

		MboRemote m = null;
		try {
			m = getMboForAttribute(name);

			if (m != this) {
				m.setFieldFlag(getAttributeName(name), flag, state, mxe);
				return;
			}
		} catch (Exception e) {
			if (getMboLogger().isErrorEnabled()) {
				getMboLogger().error(e);
			}
		}

		MboValue mv = (MboValue) getValueHash().get(name);

		BitFlag bf = (BitFlag) getFieldFlags().get(name);

		if (bf != null) {
			getFieldFlags().remove(name);
			bf.setFlag(flag, state);
			getFieldFlags().put(name, bf);
		} else {
			BitFlag newFlag = new BitFlag();
			if (mv != null) {
				newFlag.setFlags(mv.getFlags());
			}
			newFlag.setFlag(flag, state);
			getFieldFlags().put(name, newFlag);
		}

		if (mxe == null)
			return;
		MXException mxeExisting = (MXException) getFieldExceptions().get(name);

		if (mxeExisting != null)
			getFieldExceptions().remove(name);
		getFieldExceptions().put(name, mxe);
	}

	public void setFieldFlag(String[] names, long flag, boolean state) {
		setFieldFlag(names, flag, state, null);
	}

	public void setFieldFlag(String[] names, long flag, boolean state,
			MXException mxe) {
		for (int i = 0; i < names.length; ++i)
			setFieldFlag(names[i], flag, state, mxe);
	}

	public void setFieldFlag(String[] names, boolean inclusive, long flag,
			boolean state) {
		setFieldFlag(names, inclusive, flag, state, null);
	}

	public void setFieldFlag(String[] names, boolean inclusive, long flag,
			boolean state, MXException mxe) {
		if (inclusive) {
			setFieldFlag(names, flag, state, mxe);
		} else {
			Hashtable exclude = new Hashtable(names.length);

			Enumeration e = getMboSetInfo().getMboValuesInfo();

			MboValueInfo element = null;

			for (int i = 0; i < names.length; ++i) {
				exclude.put(names[i].toUpperCase(), names[i].toUpperCase());
			}
			while (e.hasMoreElements()) {
				element = (MboValueInfo) e.nextElement();

				if (!(exclude.contains(element.getName())))
					;
				setFieldFlag(element.getName(), flag, state, mxe);
			}
		}
	}

	public void checkFieldAccess(long accessModifier) throws MXException {
		if (new BitFlag(accessModifier).isFlagSet(2L)) {
			return;
		}

		if ((getUserFlags().isFlagSet(2L)) && (!(toBeAdded()))) {
			if (this.mxException == null) {
				throw new MXAccessException("access", "objectreadonly",
						new Object[] { getName() });
			}
			throw this.mxException;
		}

		if ((!(this.mySet.isFlagSet(2L))) || (toBeAdded()))
			return;
		if (this.mxException == null) {
			throw new MXAccessException("access", "objectreadonly",
					new Object[] { getName() });
		}
		throw this.mxException;
	}

	Hashtable getMethodAccessList() {
		if (this.methodAccessList == null) {
			this.methodAccessList = new Hashtable();
		}
		return this.methodAccessList;
	}

	public void enableMethod(String methodName, boolean state) {
		if (!(state)) {
			getMethodAccessList().put(methodName.toUpperCase(),
					new Boolean(state));
		} else {
			getMethodAccessList().remove(methodName.toUpperCase());
		}
	}

	public void checkMethodAccess(String methodName, long accessModifier)
			throws MXException, RemoteException {
		if (new BitFlag(accessModifier).isFlagSet(2L)) {
			return;
		}

		if (getMethodAccessList().get(methodName.toUpperCase()) != null) {
			if (this.mxException == null) {
				throw new MXAccessException("access", "notavailable");
			}
			throw this.mxException;
		}

		if (!(methodName.equalsIgnoreCase("delete"))) {
			return;
		}

		WorkflowTargetDeletionMonitor.doCheck(this);

		canDelete();
		if ((!(isFlagSet(4L))) && (!(this.mySet.isFlagSet(4L))))
			return;
		if (this.mxException == null) {
			Object[] args = { methodName.toUpperCase(), getName() };
			throw new MXAccessException("access", "method", args);
		}
		throw this.mxException;
	}

	public final void checkMethodAccess(String methodName) throws MXException,
			RemoteException {
		checkMethodAccess(methodName, 0L);
	}

	public String getUserName() throws MXException, RemoteException {
		return this.mySet.getUserName();
	}

	private Object[] getEventSubjectAndTopic() throws MXException {
		if (this.eventSubjectTopic == null) {
			String name = "maximo." + getName().toLowerCase() + "."
					+ getRecordType();
			EventTopic eventTopic = MXServer.getEventTopicTree()
					.findEventTopic(name);
			this.eventSubjectTopic = new Object[2];
			this.eventSubjectTopic[0] = name;
			this.eventSubjectTopic[1] = eventTopic;
		}
		return this.eventSubjectTopic;
	}

	public void fireEvent(String type) throws MXException, RemoteException {
		if (getRecordType() == null)
			return;
		Object[] subjectAndTopic = getEventSubjectAndTopic();
		EventTopic topic = (EventTopic) subjectAndTopic[1];
		if (type.equalsIgnoreCase("validate")) {
			MXServer.getEventTopicTree().eventValidate(
					(String) subjectAndTopic[0],
					new EventMessage((String) subjectAndTopic[0], this), topic);
		} else if (type.equalsIgnoreCase("preSaveEventAction")) {
			MXServer.getEventTopicTree().preSaveEventAction(
					(String) subjectAndTopic[0],
					new EventMessage((String) subjectAndTopic[0], this), topic);
		} else if (type.equalsIgnoreCase("preSaveInternalEventAction")) {
			MXServer.getEventTopicTree().preSaveInternalEventAction(
					(String) subjectAndTopic[0],
					new EventMessage((String) subjectAndTopic[0], this), topic);
		} else if (type.equalsIgnoreCase("eventAction")) {
			MXServer.getEventTopicTree().eventAction(
					(String) subjectAndTopic[0],
					new EventMessage((String) subjectAndTopic[0], this), topic);
		} else if (type.equalsIgnoreCase("postSaveInternalEventAction")) {
			MXServer.getEventTopicTree().postSaveInternalEventAction(
					(String) subjectAndTopic[0],
					new EventMessage((String) subjectAndTopic[0], this), topic);
		} else {
			if (!(type.equalsIgnoreCase("postCommitEventAction")))
				return;
			MXServer.getEventTopicTree().postCommitEventAction(
					(String) subjectAndTopic[0],
					new EventMessage((String) subjectAndTopic[0], this));
		}
	}

	protected void setRecordType() throws RemoteException {
		if (toBeDeleted())
			this.recordType = "delete";
		else if (toBeAdded())
			this.recordType = "add";
		else if (toBeUpdated())
			this.recordType = "update";
	}

	protected String getRecordType() {
		return this.recordType;
	}

	public ProfileRemote getProfile() throws MXException, RemoteException {
		return this.mySet.getProfile();
	}

	public MboRemote copy() throws MXException, RemoteException {
		return copy(this.mySet);
	}

	public MboRemote copy(MboSetRemote mboset) throws MXException,
			RemoteException {
		return copy(mboset, 0L);
	}

	public MboRemote copy(MboSetRemote mboset, long mboAddFlags)
			throws MXException, RemoteException {
		boolean flag = getThisMboSet().setAutoKeyFlag(false);
		Mbo mbo = (Mbo) mboset.addAtEnd(mboAddFlags);

		MboSetInfo msi = getMboSetInfo();

		Enumeration e = getMboSetInfo().getMboValuesInfo();

		while (e.hasMoreElements()) {
			MboValueInfo mv = (MboValueInfo) e.nextElement();

			if (skipCopyField(mv)) {
				continue;
			}
			String columnName = mv.getAttributeName();

			if ((columnName.equalsIgnoreCase("OWNERSYSID"))
					|| (columnName.equalsIgnoreCase("EXTERNALREFID"))
					|| (columnName.equalsIgnoreCase("SOURCESYSID"))
					|| (columnName.equalsIgnoreCase("SENDERSYSID")))
				continue;
			if (columnName.equalsIgnoreCase("SAP_UPG")) {
				continue;
			}

			if (msi.isAsUniqueId(columnName)) {
				continue;
			}
			String fieldname = mv.getName();
			String defaultvalue = mv.getDefaultValue();

			if (!(getName().equalsIgnoreCase(mbo.getName()))) {
				MboValueInfo passMvi = mboset.getMboSetInfo().getMboValueInfo(
						fieldname);
				if (passMvi == null) {
					continue;
				}

			}

			String uniqueColumnName = getMboSetInfo().getUniqueIDName();
			if ((defaultvalue == null)
					|| ((!(defaultvalue.equalsIgnoreCase("&AUTOKEY&")))
							&& (!(defaultvalue.equalsIgnoreCase("&SYSDATE&"))) && (!(defaultvalue
							.equalsIgnoreCase("&USERNAME&"))))) {
				String val = null;
				try {
					val = getString(fieldname);
					mbo.setValue(fieldname, val, 11L);
				} catch (Exception ex) {
					if (getMboLogger().isErrorEnabled()) {
						getMboLogger().error(
								"Mbo copy, failed to set value " + val
										+ " to field :" + fieldname
										+ " for object " + mbo.getName()
										+ " from object, ignoring error", ex);
					}

				}

			}

		}

		getThisMboSet().setAutoKeyFlag(flag);
		if (flag) {
			mbo.setAutokeyFields();
		}
		mbo.setCopyDefaults();

		return mbo;
	}

	public void setAutokeyFields() throws MXException, RemoteException {
		Enumeration e = getMboSetInfo().getMboValuesInfo();

		while (e.hasMoreElements()) {
			MboValueInfo mvi = (MboValueInfo) e.nextElement();
			String dv = mvi.getDefaultValue();

			if ((dv != null) && (dv.toUpperCase().equals("&AUTOKEY&"))) {
				if ((!(this instanceof HierarchicalMboRemote))
						|| (getOwner() == null)
						|| (!(getOwner() instanceof HierarchicalMboRemote))) {
					getMboValue(mvi.getName()).autoKeyByMboSiteOrg();
				}
			}
		}
	}

	public MboRemote blindCopy(MboSetRemote mboset) throws MXException,
			RemoteException {
		MboRemote mbo = mboset.addAtEnd(2L);

		Enumeration e = getMboSetInfo().getMboValuesInfo();

		while (e.hasMoreElements()) {
			MboValueInfo mv = (MboValueInfo) e.nextElement();

			String columnName = mv.getAttributeName();

			String fieldname = mv.getName();

			if (!(getName().equalsIgnoreCase(mbo.getName()))) {
				MboValueInfo passMvi = mboset.getMboSetInfo().getMboValueInfo(
						fieldname);
				if (passMvi == null) {
					continue;
				}

			}

			String val = null;
			try {
				val = getString(fieldname);
				mbo.setValue(fieldname, val, 11L);
			} catch (Exception ex) {
				if (getMboLogger().isErrorEnabled()) {
					getMboLogger().error(
							"Mbo copy, failed to set value " + val
									+ " to field :" + fieldname
									+ " for object " + mbo.getName()
									+ " from object, ignoring error", ex);
				}

			}

		}

		return mbo;
	}

	protected boolean skipCopyField(MboValueInfo mvi) throws RemoteException,
			MXException {
		return false;
	}

	public void setCopyDefaults() throws MXException, RemoteException {
	}

	public MXTransaction getMXTransaction() throws RemoteException {
		return this.mySet.getMXTransaction();
	}

	public String getRelatedWhere() throws MXException, RemoteException {
		return getRelatedWhere("");
	}

	public String getRelatedWhere(String alias) throws MXException,
			RemoteException {
		String where = "";
		int count = 0;
		for (Enumeration e = getRelatedSets().elements(); e.hasMoreElements();) {
			MboSetRemote msr = (MboSetRemote) e.nextElement();
			if (msr.hasQbe()) {
				if (count != 0) {
					where = where + " "
							+ ((MboSet) getThisMboSet()).qbe.getOperator()
							+ " ";
				}

				if (!(msr.getName().equalsIgnoreCase(getName())))
					where = where
							+ new SubQueryFormatter(this, msr, msr
									.getCompleteWhere(), alias).format(msr
									.getName());
				else {
					where = where
							+ new SubQueryFormatter(this, msr, msr
									.getCompleteWhere(), alias).format(msr
									.getName(), msr.getRelationship());
				}
				++count;
			}
		}
		return where;
	}

	public boolean hasRelatedQbe() throws MXException, RemoteException {
		for (Enumeration e = getRelatedSets().elements(); e.hasMoreElements();) {
			MboSetRemote msr = (MboSetRemote) e.nextElement();
			if (msr.hasQbe()) {
				return true;
			}
		}
		return false;
	}

	private String getSchemaOwner() {
		return getMboServer().getSchemaOwner();
	}

	public ServiceRemote getIntegrationService() throws RemoteException,
			MXException {
		return MXServer.getMXServer().lookupLocal("INTEGRATION");
	}

	public void generateAutoKey() throws RemoteException, MXException {
		Object[] param = { getName() };
		throw new MXAccessException("access", "NoGenAutoKey", param);
	}

	public boolean getCheckpoint() {
		return this.checkpoint;
	}

	public void startCheckpoint() throws MXException, RemoteException {
		if (getMboLogger().isDebugEnabled()) {
			getMboLogger().debug("Mbo startCheckpoint");
		}

		Enumeration e = getMboSetInfo().getMboValuesInfo();

		while (e.hasMoreElements()) {
			MboValueInfo mvi = (MboValueInfo) e.nextElement();

			String fieldname = mvi.getName();
			MboValue mv = getMboValue(fieldname);
			mv.takeCheckpoint();
		}

		this.checkpoint = true;
	}

	public void rollbackToCheckpoint() throws MXException, RemoteException {
		if (this.checkpoint) {
			this.checkpoint = false;
			Enumeration e = getMboSetInfo().getMboValuesInfo();

			while (e.hasMoreElements()) {
				MboValueInfo mvi = (MboValueInfo) e.nextElement();

				String fieldname = mvi.getName();
				MboValue mv = getMboValue(fieldname);
				mv.rollbackToCheckpoint();
			}
		}

		if (!(getMboLogger().isDebugEnabled()))
			return;
		getMboLogger().debug("Mbo rollbackToCheckpoint");
	}

	public void select() throws MXException, RemoteException {
		this.markedForSelect = true;
	}

	public void unselect() throws MXException, RemoteException {
		this.markedForSelect = false;
	}

	public boolean isSelected() throws MXException, RemoteException {
		return this.markedForSelect;
	}

	public void copyValue(MboRemote sourceMbo, String attrSource,
			String attrTarget, long flags) throws MXException, RemoteException {
		MboValueInfo sourcemboInfo = sourceMbo.getThisMboSet().getMboSetInfo()
				.getMboValueInfo(attrSource);

		MboValueInfo targetmboInfo = getMboValueInfo(attrTarget);

		if ((sourcemboInfo.isMLInUse()) && (targetmboInfo.isMLInUse())) {
			return;
		}

		setValue(attrTarget, sourceMbo.getString(attrSource), flags);
	}

	public void copyValue(MboRemote sourceMbo, String[] attrSource,
			String[] attrTarget, long flags) throws MXException,
			RemoteException {
		int i = 0;
		int j = 0;

		for (; (i < attrSource.length) && (j < attrTarget.length); ++j) {
			copyValue(sourceMbo, attrSource[i], attrTarget[j], flags);

			++i;
		}
	}

	public MboRemote duplicate() throws MXException, RemoteException {
		throw new MXApplicationException("system", "noduplicate");
	}

	public void setPropagateKeyFlag(boolean flag) throws MXException,
			RemoteException {
		this.bPropagateKey = flag;
	}

	public void setPropagateKeyFlag(String[] objectName, boolean flag)
			throws MXException, RemoteException {
		this.propagateKeyExcludeObjName = objectName;
		this.bPropagateKey = flag;
	}

	public boolean excludeObjectForPropagate(String name) throws MXException,
			RemoteException {
		if (this.propagateKeyExcludeObjName == null) {
			return false;
		}
		for (int i = 0; i < this.propagateKeyExcludeObjName.length; ++i) {
			if (this.propagateKeyExcludeObjName[i].equalsIgnoreCase(name))
				return true;
		}
		return false;
	}

	public boolean getPropagateKeyFlag() throws MXException, RemoteException {
		return this.bPropagateKey;
	}

	public void propagateKeyValue(String keyName, String keyValue)
			throws MXException, RemoteException {
		this.propagateKeyValueImplemented = false;
	}

	public void setHierarchyLink(boolean flag) {
		this.bHierarchy = flag;
		if (flag == true)
			this.mboSets = null;
	}

	public boolean hasHierarchyLink() throws MXException, RemoteException {
		return this.bHierarchy;
	}

	public void setDefaultValue() {
		HashMap defaultVal = ((MboSet) getThisMboSet()).defaultValue;

		if (defaultVal == null)
			return;
		Set mapSet = defaultVal.keySet();
		Iterator it = mapSet.iterator();
		while (it.hasNext()) {
			try {
				String attributeName = (String) it.next();
				String value = (String) defaultVal.get(attributeName
						.toUpperCase());
				setValue(attributeName, value);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public void setDefaultValues() {
		HashMap defaultJspVal = ((MboSet) getThisMboSet()).jspDefaultValue;

		if (defaultJspVal == null)
			return;
		Set mapSet = defaultJspVal.keySet();
		Iterator it = mapSet.iterator();
		while (it.hasNext()) {
			try {
				String attributeName = (String) it.next();
				String value = (String) defaultJspVal.get(attributeName);
				setValue(attributeName, value);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public void setAppDefaultValue() throws MXException, RemoteException {
		String appStr = null;

		appStr = getThisMboSet().getApp();
		if (appStr == null) {
			MboRemote ownerMbo = getOwner();
			while (ownerMbo != null) {
				appStr = ownerMbo.getThisMboSet().getApp();
				if ((appStr != null) && (appStr.trim().length() > 0))
					break;
				ownerMbo = ownerMbo.getOwner();
			}

		}

		if (appStr == null) {
			return;
		}
		String siteStr = "";
		if (getInsertSite() != null)
			siteStr = getInsertSite().toUpperCase();
		HashSet groupSet = getProfile().getGroupNames();

		HashMap defaultAppVal = getMboServer().getMaximoDD()
				.getAppFieldDefaults(appStr.toUpperCase(),
						getMboSetInfo().getObjectName().toUpperCase(), siteStr,
						getUserName().toUpperCase(), groupSet);

		if (getSqlLogger().isInfoEnabled()) {
			Iterator i = groupSet.iterator();
			StringBuffer grpBuff = new StringBuffer();
			while (i.hasNext()) {
				grpBuff = grpBuff.append((String) i.next());
				grpBuff = grpBuff.append("  ");
			}
			getSqlLogger().info(
					"Getting record from APPFIELDDEFAULT for App: "
							+ appStr.toUpperCase() + " ,Object: "
							+ getMboSetInfo().getObjectName().toUpperCase()
							+ " ,Site:" + siteStr + " ,User:"
							+ getUserName().toUpperCase() + " ,Groups: "
							+ grpBuff.toString());
		}

		if (defaultAppVal == null)
			return;
		Set mapAppSet = defaultAppVal.keySet();
		Iterator it = mapAppSet.iterator();
		String attributeName = null;
		String value = null;
		getUserInfo().setInteractive(false);
		while (it.hasNext()) {
			try {
				attributeName = (String) it.next();
				value = (String) defaultAppVal.get(attributeName);
				if (value != null) {
					MboValueInfo mvi = getMboValueInfo(attributeName);
					MboValue mv = getMboValue(attributeName);

					if (mv.isNull()) {
						if (value.toUpperCase().indexOf(":OWNEROBJECT") > -1) {
							int ownerFieldIndex = value.indexOf(".");
							if (ownerFieldIndex > -1) {
								String ownerFieldName = value
										.substring(ownerFieldIndex + 1);
								if (getOwner() != null) {
									setValue(attributeName, getOwner()
											.getString(ownerFieldName));
								}

							}

						} else if ((mvi.getMaxType()
								.equalsIgnoreCase("INTEGER"))
								|| (mvi.getMaxType()
										.equalsIgnoreCase("SMALLINT"))) {
							setValue(attributeName, MXFormat.stringToLong(
									value, Locale.getDefault()));
						} else if ((mvi.getMaxType()
								.equalsIgnoreCase("DECIMAL"))
								|| (mvi.getMaxType().equalsIgnoreCase("FLOAT"))) {
							setValue(attributeName, MXFormat.stringToDouble(
									value, Locale.getDefault()));
						} else if (mvi.getMaxType()
								.equalsIgnoreCase("DURATION")) {
							setValue(attributeName, MXFormat.durationToDouble(
									value, Locale.getDefault()));
						} else if (mvi.getMaxType().equalsIgnoreCase("AMOUNT")) {
							setValue(attributeName, MXFormat.stringToAmount(
									value, Locale.getDefault()));
						} else {
							setValue(attributeName, value);
						}
					}
				}

			} catch (Exception ex) {
				if (getMboLogger().isErrorEnabled()) {
					getMboLogger().error(
							"Error setting appfielddefault value for attribute: "
									+ attributeName + " value: " + value, ex);
				}

			}

		}

		getUserInfo().setInteractive(true);
	}

	public boolean isBasedOn(String objectName) throws RemoteException {
		return this.mySet.isBasedOn(objectName);
	}

	public void clear() throws MXException, RemoteException {
		Hashtable rs = getRelatedSets();
		for (Enumeration ee = rs.elements(); ee.hasMoreElements();) {
			MboSetRemote msr = (MboSetRemote) ee.nextElement();
			if (msr != null) {
				msr.clear();
			}
		}
	}

	public MXLogger getMboLogger() {
		return getMboSetInfo().getMboLogger();
	}

	public MXLogger getSqlLogger() {
		return getMboSetInfo().getSqlLogger();
	}

	public String getOrgForGL(String lookupAttr) throws MXException,
			RemoteException {
		String orgId = "";
		try {
			int orgSiteType = getThisMboSet().getMboSetInfo().getSiteOrgType();

			if ((orgSiteType == 2) || (orgSiteType == 1) || (orgSiteType == 7)
					|| (orgSiteType == 10) || (orgSiteType == 8)) {
				orgId = getMboValue("orgid").getString();
			} else {
				Object[] keyMap = ((SystemServiceRemote) MXServer.getMXServer()
						.lookup("SYSTEM")).getLookupKeyMap(getName(),
						lookupAttr, getUserInfo());

				if (keyMap != null) {
					int k = 0;
					String[] tmp = new String[keyMap.length];
					String targetStr = null;
					String SourceStr = null;
					for (int i = 0; i < keyMap.length; ++i) {
						targetStr = (String) ((Object[]) keyMap[i])[0];
						SourceStr = (String) ((Object[]) keyMap[i])[1];
						if ((((Boolean) ((Object[]) keyMap[i])[2])
								.booleanValue())
								|| (targetStr.equalsIgnoreCase(lookupAttr))) {
							continue;
						}
						orgId = getMboValue(targetStr).getString();
					}

				} else {
					MboValueInfo mvi = getThisMboSet().getMboSetInfo()
							.getMboValueInfo("orgid");
					if (mvi != null) {
						orgId = getMboValue("orgid").getString();
					}
				}
			}
			if ((orgId == null) || (orgId.trim().length() == 0)) {
				throw new MXAccessException("system", "orgneededforgl",
						new Object[] { lookupAttr });
			}
			return orgId;
		} catch (RemoteException e) {
			MXLogger l = getMboLogger();
			if (l.isErrorEnabled()) {
				l.error(e.getMessage());
			}
		}
		return new String("");
	}

	public String[] getSiteOrg()
  throws MXException, RemoteException
{
  String siteId;
  String orgId;
  siteId = "";
  orgId = "";
  try {
	int orgSiteType = getThisMboSet().getMboSetInfo().getSiteOrgType();
	  if(orgSiteType == 1 || orgSiteType == 8 || orgSiteType == 9 || orgSiteType == 5)
	  {
	      siteId = getMboValue("siteid").getString();
	      orgId = getMboValue("orgid").getString();
	  } else
	  if(orgSiteType == 2 || orgSiteType == 7 || orgSiteType == 10 || orgSiteType == 11)
	      orgId = getMboValue("orgid").getString();
	  return (new String[] {
	      siteId, orgId
	  });
} catch (RemoteException e) {
	// TODO Auto-generated catch block
	e.printStackTrace();
	MXLogger l = getMboLogger();
	  if(l.isErrorEnabled())
	      l.error(e.getMessage());
	  return (new String[] {
	      "", ""
	  });
}
  //RemoteException e;
 // e;
  
}

	public void setValue(String targetAttrName, MboRemote sourceMbo)
			throws MXException, RemoteException {
		MboRemote mbo = getMboForAttribute(targetAttrName);
		if (mbo == this)
			getMboValue(targetAttrName).setValueFromLookup(sourceMbo);
		else
			mbo.setValue(getAttributeName(targetAttrName), sourceMbo);
	}

	public void setValue(String targetAttrName, MboSetRemote sourceMboSet)
			throws MXException, RemoteException {
		setValue(targetAttrName, sourceMboSet.getMbo());
	}

	public final void setLangCodeDefault() throws MXException, RemoteException {
		MboSetInfo msi = getThisMboSet().getMboSetInfo();

		if (!(msi.isPersistent()))
			return;
		if (msi.isLanguageTable())
			return;
		Entity entity = msi.getEntity();
		Iterator tbs = entity.getTables();
		while (tbs.hasNext()) {
			String tbName = (String) tbs.next();
			String langColumnName = entity.getLangColumnName(tbName);

			if ((langColumnName != null) && (!(langColumnName.equals("")))) {
				if (msi.isMLInUse())
					setValue(langColumnName, getMboServer().getMaxVar()
							.getString("BASELANGUAGE", ""), 3L);
				else
					setValue(langColumnName, getUserInfo().getLangCode(), 3L);
			}
		}
	}

	public String getUniqueIDName() throws RemoteException, MXException {
		return getMboSetInfo().getUniqueIDName();
	}

	public long getUniqueIDValue() throws RemoteException, MXException {
		String uniqueColumnName = getMboSetInfo().getUniqueIDName();
		return getLong(uniqueColumnName);
	}

	public void setUniqueIDValue() throws RemoteException, MXException {
		Entity entity = getMboSetInfo().getEntity();

		if (entity == null) {
			return;
		}

		Iterator tableIterator = entity.getTablesInHierarchyOrder();
		while (tableIterator.hasNext()) {
			String tableName = (String) tableIterator.next();
			String uniqueColumnName = getMboServer().getMaximoDD()
					.getUniqueIdColumn(tableName);

			if ((uniqueColumnName != null) && (uniqueColumnName.length() > 0))
				getMboValue(uniqueColumnName).generateUniqueID();
		}
	}

	public int getDocLinksCount() throws MXException, RemoteException {
		String rels = null;
		String name = getName() + "REL";
		MboSetRemote maxMessages = getMboServer().getMboSet("maxmessages",
				getUserInfo());
		maxMessages
				.setWhere("msggroup='jspsettings' and msgkey='" + name + "'");
		maxMessages.reset();
		MboRemote refRel = maxMessages.getMbo(0);
		if (refRel != null) {
			rels = refRel.getString("value");
		}

		if ((rels == null) || (rels.equals(""))) {
			rels = "SELF";
		}

		int count = 0;
		StringTokenizer relTok = new StringTokenizer(rels, ":");
		while (relTok.hasMoreTokens()) {
			String rel = relTok.nextToken();
			int xx;
			if (rel.equalsIgnoreCase("SELF")) {
				MboSetRemote docLinksSet = getMboSet("DOCLINKS");
				count += docLinksSet.count();

				String lineRel = getLinesRelationship();
				if (lineRel != null) {
					MboSetRemote lines = getMboSet(lineRel);
					if (lines != null) {
						xx = 0;
						MboRemote line = null;
						while ((line = lines.getMbo(xx)) != null) {
							count += line.getDocLinksCount();
							++xx;
						}
					}
				}
			} else {
				MboRemote mbo = this;
				MboSetRemote mboSet = null;
				StringTokenizer strTok = new StringTokenizer(rel, ".");
				while (strTok.hasMoreTokens()) {
					String subRel = strTok.nextToken();
					mboSet = mbo.getMboSet(subRel);
					if (mboSet != null) {
						mbo = mboSet.getMbo(0);
						if (mbo == null) {
							break;
						}
					}
				}
				if (mboSet != null) {
					xx = 0;
					mbo = null;
					while ((mbo = mboSet.getMbo(xx)) != null) {
						count += mbo.getDocLinksCount();
						++xx;
					}
				}
			}
		}
		return count;
	}

	public String getLinesRelationship() throws MXException, RemoteException {
		return null;
	}

	public String getMessage(String errGrp, String errKey)
			throws RemoteException {
		return getThisMboSet().getMessage(errGrp, errKey);
	}

	public String getMessage(String errGrp, String errKey, Object[] params)
			throws RemoteException {
		return getThisMboSet().getMessage(errGrp, errKey, params);
	}

	public String getMessage(String errGrp, String errKey, Object param)
			throws RemoteException {
		return getThisMboSet().getMessage(errGrp, errKey, param);
	}

	public String getMessage(MXException ex) throws RemoteException {
		return getThisMboSet().getMessage(ex);
	}

	public MaxMessage getMaxMessage(String errGrp, String errKey)
			throws MXException, RemoteException {
		return getThisMboSet().getMaxMessage(errGrp, errKey);
	}

	public MboRemote createComm() throws MXException, RemoteException {
		MboSetRemote commLogSet = getMboSet("COMMLOG");
		MboRemote commLog = commLogSet.add();
		commLog.setValue("inbound", false, 11L);
		return commLog;
	}

	public String getMatchingAttr(String attribute) throws MXException,
			RemoteException {
		return getMboValue(attribute).getMatchingAttr();
	}

	public String getMatchingAttr(String sourceObjectName, String attribute)
			throws MXException, RemoteException {
		String matchAttr = getMboValue(attribute).getMatchingAttr(
				sourceObjectName);
		if (matchAttr == null) {
			Object[] keyMap = getMatchingAttrs(sourceObjectName, attribute);
			for (int i = 0; i < keyMap.length; ++i) {
				String targetAttr = (String) ((Object[]) keyMap[i])[0];
				if (targetAttr.equalsIgnoreCase(attribute))
					return ((String) ((Object[]) keyMap[i])[1]);
			}
		}
		return matchAttr;
	}

	public String getStringInSpecificLocale(String attribute, Locale l,
			TimeZone tz) throws MXException, RemoteException {
		MboValue mv = getMboValue(attribute);
		return mv.getCurrentValue().asLocaleString(l, tz);
	}

	public Object[] getMatchingAttrs(String sourceName, String targetAttr)
			throws MXException, RemoteException {
		String targetAttrName = targetAttr.toUpperCase();
		String[][] cachedKeyMap = (String[][]) null;

		Object[] keyMap = ((SystemServiceRemote) MXServer.getMXServer().lookup(
				"SYSTEM")).getLookupKeyMap(getName(), sourceName,
				targetAttrName, getUserInfo());
		if (keyMap == null) {
			String[] sourceKeys = MXServer.getMXServer().getMaximoDD()
					.getMboSetInfo(sourceName).getKeyAttributes();
			cachedKeyMap = new String[sourceKeys.length][2];

			boolean lookupAttrHasMatched = false;
			int unmatchedIndex = -1;
			boolean lookupAttrMatched = false;
			for (int i = 0; i < sourceKeys.length; ++i) {
				if (getMboSetInfo().getAttribute(sourceKeys[i]) == null) {
					if (unmatchedIndex >= 0) {
						throw new MXApplicationException("system",
								"LookupNeedRegister", new String[] {
										sourceName, targetAttrName });
					}
					unmatchedIndex = i;
				} else if ((!(lookupAttrMatched))
						&& (sourceKeys[i].equalsIgnoreCase(targetAttrName))) {
					lookupAttrMatched = true;
				}
			}
			if (unmatchedIndex >= 0) {
				if (lookupAttrMatched) {
					throw new MXApplicationException("system",
							"LookupNeedRegister", new String[] { sourceName,
									targetAttrName });
				}
				MboValueInfo tmvi = getMboValue(targetAttrName)
						.getMboValueInfo();
				MboValueInfo smvi = MXServer.getMXServer().getMaximoDD()
						.getMboSetInfo(sourceName).getAttribute(
								sourceKeys[unmatchedIndex]);

				if ((sourceKeys.length == 1)
						|| ((tmvi.getTypeAsInt() == smvi.getTypeAsInt())
								&& (tmvi.getLength() == smvi.getLength()) && ((((tmvi
								.getSameAsObject() != null)
								&& (smvi.getSameAsObject() != null)
								&& (tmvi.getSameAsObject()
										.equalsIgnoreCase(smvi
												.getSameAsObject())) && (tmvi
								.getSameAsAttribute().equalsIgnoreCase(smvi
								.getSameAsAttribute()))) || ((tmvi
								.getSameAsObject() != null)
								&& (tmvi.getSameAsObject()
										.equalsIgnoreCase(sourceName)) && (tmvi
								.getSameAsAttribute()
								.equalsIgnoreCase(sourceKeys[unmatchedIndex]))))))) {
					cachedKeyMap[unmatchedIndex][1] = sourceKeys[unmatchedIndex];
					cachedKeyMap[unmatchedIndex][0] = targetAttrName;
				} else {
					throw new MXApplicationException("system",
							"LookupNeedRegister", new String[] { sourceName,
									targetAttrName });
				}
			} else if (!(lookupAttrMatched)) {
				int sameAsLookup = 0;
				MboValueInfo tmvi = getMboValue(targetAttrName)
						.getMboValueInfo();
				int thePossibleOne = -1;
				for (int j = 0; j < sourceKeys.length; ++j) {
					MboValueInfo smvi = MXServer.getMXServer().getMaximoDD()
							.getMboSetInfo(sourceName).getAttribute(
									sourceKeys[j]);

					if ((tmvi.getTypeAsInt() != smvi.getTypeAsInt())
							|| (tmvi.getLength() != smvi.getLength())
							|| ((((tmvi.getSameAsObject() == null)
									|| (smvi.getSameAsObject() == null)
									|| (!(tmvi.getSameAsObject()
											.equalsIgnoreCase(smvi
													.getSameAsObject()))) || (!(tmvi
									.getSameAsAttribute().equalsIgnoreCase(smvi
									.getSameAsAttribute()))))) && (((tmvi
									.getSameAsObject() == null)
									|| (!(tmvi.getSameAsObject()
											.equalsIgnoreCase(sourceName))) || (!(tmvi
									.getSameAsAttribute()
									.equalsIgnoreCase(sourceKeys[j]))))))) {
						continue;
					}

					++sameAsLookup;
					thePossibleOne = j;
					if (sameAsLookup > 1) {
						break;
					}
				}

				if (sameAsLookup != 1) {
					throw new MXApplicationException("system",
							"LookupNeedRegister", new String[] { sourceName,
									targetAttrName });
				}

				cachedKeyMap[thePossibleOne][1] = sourceKeys[thePossibleOne];
				cachedKeyMap[thePossibleOne][0] = targetAttrName;
				unmatchedIndex = thePossibleOne;
			}

			for (int i = 0; i < sourceKeys.length; ++i) {
				if (unmatchedIndex == i) {
					continue;
				}

				cachedKeyMap[i][1] = sourceKeys[i];
				cachedKeyMap[i][0] = sourceKeys[i];
			}

			int length = cachedKeyMap.length;
			for (int i = length - 1; i >= 0; --i) {
				if (!(cachedKeyMap[i][0].equalsIgnoreCase(targetAttrName)))
					continue;
				if (i == length - 1) {
					break;
				}

				String tmp0 = cachedKeyMap[i][0];
				String tmp1 = cachedKeyMap[i][1];
				cachedKeyMap[i][0] = cachedKeyMap[(length - 1)][0];
				cachedKeyMap[i][1] = cachedKeyMap[(length - 1)][1];
				cachedKeyMap[(length - 1)][0] = tmp0;
				cachedKeyMap[(length - 1)][1] = tmp1;
			}

			keyMap = new Object[cachedKeyMap.length];
			for (int i = 0; i < cachedKeyMap.length; ++i) {
				Object[] piece = new Object[3];
				piece[0] = cachedKeyMap[i][0];
				piece[1] = cachedKeyMap[i][1];
				piece[2] = new Boolean(false);
				keyMap[i] = piece;
			}
			MaxLookupMapCache cache = (MaxLookupMapCache) MXServer
					.getMXServer().getFromMaximoCache("LookupKeyMap");
			cache
					.setLookupKeyMap(getName(), sourceName, targetAttrName,
							keyMap);
		}

		return keyMap;
	}

	public void sigOptionAccessAuthorized(String optionname)
			throws MXException, RemoteException {
		String app = getThisMboSet().getApp();
		if ((app == null) || (app.equals("")) || (optionname == null)
				|| (optionname.equals(""))) {
			return;
		}
		ProfileRemote profile = getProfile();
		optionname = optionname.toUpperCase();

		if (profile.sysLevelApp(app)) {
			if (!(profile.getAppOptionAuth(app, optionname, null)))
				throw new MXAccessException("access", "NotAuthorizedForMbo");
		} else if (profile.siteLevelApp(app)) {
			if (!(profile
					.getAppOptionAuth(app, optionname, getString("siteid")))) {
				throw new MXAccessException("access", "NotAuthorizedForMbo");
			}

		} else if (!(profile.getAppOptionAuth(app, optionname,
				getString("orgid"))))
			throw new MXAccessException("access", "NotAuthorizedForMbo");
	}

	public void initFieldFlagsOnMbo(String attrName) throws MXException {
	}

	public boolean needCallInitFieldFlag(String attrName) {
		return false;
	}

	boolean hasFieldFlagsOnMbo(String attrName) {
		if (this.fieldFlags == null)
			return false;
		return this.fieldFlags.containsKey(attrName);
	}

	public void checkSiteOrgAccessForSave() throws MXException, RemoteException {
		String appName = this.mySet.getApp();
		int orgSiteType = getMboSetInfo().getSiteOrgType();
		if ((appName == null) || (appName.trim().length() <= 0))
			return;
		String siteOrg = null;

		if ((orgSiteType == 1) || (orgSiteType == 8)) {
			siteOrg = getString("siteid");
			if ((siteOrg == null)
					|| (siteOrg.trim().length() <= 0)
					|| (getProfile().getAppOptionAuth(appName, "SAVE", siteOrg))) {
				return;
			}
			throw new MXAccessException("access", "objectreadonly",
					new Object[] { getName() });
		}

		if ((orgSiteType == 2) || (orgSiteType == 7)) {
			siteOrg = getString("orgid");
			if ((siteOrg == null)
					|| (siteOrg.trim().length() <= 0)
					|| (getProfile().getAppOptionAuth(appName, "SAVE", siteOrg))) {
				return;
			}
			throw new MXAccessException("access", "objectreadonly",
					new Object[] { getName() });
		}

		if (orgSiteType == 9) {
			siteOrg = getString("siteid");

			if ((siteOrg != null)
					&& (siteOrg.trim().length() > 0)
					&& (!(getProfile().getNonStandardAppOptionAuth(appName,
							"SAVE", null, siteOrg)))) {
				throw new MXAccessException("access", "objectreadonly",
						new Object[] { getName() });
			}

			if ((siteOrg != null) && (siteOrg.trim().length() != 0))
				return;
			siteOrg = getString("orgid");
			if ((siteOrg == null)
					|| (siteOrg.trim().length() <= 0)
					|| (getProfile().getNonStandardAppOptionAuth(appName,
							"SAVE", siteOrg, null))) {
				return;
			}
			throw new MXAccessException("access", "objectreadonly",
					new Object[] { getName() });
		}

		if (orgSiteType == 11) {
			siteOrg = getString("orgid");
			if ((siteOrg == null)
					|| (siteOrg.trim().length() <= 0)
					|| (siteOrg == null)
					|| (siteOrg.trim().length() <= 0)
					|| (getProfile().getNonStandardAppOptionAuth(appName,
							"SAVE", siteOrg, null))) {
				return;
			}
			throw new MXAccessException("access", "objectreadonly",
					new Object[] { getName() });
		}

		if (orgSiteType == 5) {
			siteOrg = getString("siteid");

			if ((siteOrg == null)
					|| (siteOrg.trim().length() <= 0)
					|| (getProfile().getNonStandardAppOptionAuth(appName,
							"SAVE", null, siteOrg))) {
				return;
			}
			throw new MXAccessException("access", "objectreadonly",
					new Object[] { getName() });
		}

		if (orgSiteType == 10) {
			siteOrg = getString("siteid");

			if ((siteOrg == null)
					|| (siteOrg.trim().length() <= 0)
					|| (getProfile().getNonStandardAppOptionAuth(appName,
							"SAVE", null, siteOrg))) {
				return;
			}
			throw new MXAccessException("access", "objectreadonly",
					new Object[] { getName() });
		}
		String setId;
		boolean saveOrg;
		SetsServiceRemote setsServiceRemote;
		Vector org;
		int i;
		if (orgSiteType == 3) {
			setId = getString("itemsetid");

			if ((setId != null) && (setId.trim().length() > 0)) {
				saveOrg = false;
				setsServiceRemote = (SetsServiceRemote) MXServer.getMXServer()
						.lookup("SETS");

				org = setsServiceRemote.getOrgsForItemSet(setId, getUserInfo());

				for (i = 0; i < org.size(); ++i) {
					siteOrg = (String) org.get(i);
					if (!(getProfile().getAppOptionAuth(appName, "SAVE",
							siteOrg)))
						continue;
					saveOrg = true;
					break;
				}

				if (!(saveOrg)) {
					throw new MXAccessException("access", "objectreadonly",
							new Object[] { getName() });
				}
			}
		} else {
			if (orgSiteType != 4)
				return;
			setId = getString("companysetid");

			if ((setId == null) || (setId.trim().length() <= 0))
				return;
			saveOrg = false;
			setsServiceRemote = (SetsServiceRemote) MXServer.getMXServer()
					.lookup("SETS");

			org = setsServiceRemote.getOrgsForCompanySet(setId, getUserInfo());

			for (i = 0; i < org.size(); ++i) {
				siteOrg = (String) org.get(i);
				if (!(getProfile().getAppOptionAuth(appName, "SAVE", siteOrg)))
					continue;
				saveOrg = true;
				break;
			}

			if (saveOrg)
				return;
			throw new MXAccessException("access", "objectreadonly",
					new Object[] { getName() });
		}
	}

	class SubQueryFormatter extends SqlFormat {
		String alias = null;
		MboSetRemote msr = null;

		SubQueryFormatter(MboRemote mbo, MboSetRemote msRemote, String sql,
				String alias) {
			super(mbo, sql);
			this.alias = alias;
			this.msr = msRemote;
		}

		public String format(String tbName) throws MXException {
			String[] attrArray = Mbo.this.getMboServer().getMaximoDD()
					.getMboSetInfo(tbName).getKeyAttributes();
			String attr;
			if (attrArray.length == 0)
				attr = "*";
			else {
				attr = attrArray[0];
			}
			try {
				if ((this.msr.hasMLQbe())
						&& (!(super.format().trim().startsWith("exists")))) {
					StringBuffer fromClause = new StringBuffer("");
					fromClause = this.msr.getMLFromClause(true);

					return "exists (select 1 from " + new String(fromClause)
							+ " where " + super.format() + ")";
				}
			} catch (RemoteException e) {
				throw new MXApplicationException("system", "remoteexception");
			}
			return "exists (select " + attr.toLowerCase() + " from "
					+ Mbo.this.getSchemaOwner() + "." + tbName.toLowerCase()
					+ " where " + super.format() + ")";
		}

		public String format(String tbName, String relationshipWhere)
				throws MXException {
			relationshipWhere = relationshipWhere.trim();

			int index = relationshipWhere.indexOf(58);
			if (index == -1) {
				return null;
			}

			String str1 = relationshipWhere.substring(0, index).trim();
			String str2 = relationshipWhere.substring(index + 1).trim();

			str1 = str1.substring(0, str1.length() - 1).trim();
			int i = str1.lastIndexOf(32);
			String innerFieldName = str1.substring(i + 1);
			str1 = (i == -1) ? "" : str1.substring(0, i);

			String outerFieldName = null;
			i = str2.indexOf(32);
			if (i == -1) {
				outerFieldName = str2;
				if (str1.equals(""))
					str2 = "1=1";
				else
					str2 = "";
			} else {
				outerFieldName = str2.substring(0, i);
				str2 = str2.substring(i, str2.length()).trim();
				if (str2.toLowerCase().startsWith("and")) {
					str2 = str2.substring(3).trim();
				}
			}

			i = this.statement.indexOf(relationshipWhere);
			if (i != -1) {
				this.statement = this.statement.substring(0, i)
						+ str1
						+ str2
						+ this.statement.substring(i
								+ relationshipWhere.length());
			}

			try {
				if ((this.msr.hasMLQbe())
						&& (!(super.format().trim().startsWith("exists")))) {
					StringBuffer fromClause = new StringBuffer("");
					fromClause = this.msr.getMLFromClause(true);

					return outerFieldName + " in (select " + innerFieldName
							+ " from " + new String(fromClause) + " where "
							+ super.format() + ")";
				}

			} catch (RemoteException e) {
				throw new MXApplicationException("system", "remoteexception");
			}
			return outerFieldName + " in (select " + innerFieldName + " from "
					+ Mbo.this.getSchemaOwner() + "." + tbName.toLowerCase()
					+ " where " + super.format() + ")";
		}

		public String getFieldValue(String attribute, MboRemote mbo)
				throws MXException {
			MboValueInfo mvi = Mbo.this.getMboSetInfo().getMboValueInfo(
					attribute);

			if (mvi != null) {
				if ((this.alias != null) && (this.alias.length() > 0)) {
					return this.alias + "." + attribute.toLowerCase();
				}
				return Mbo.this.getName() + "." + attribute.toLowerCase();
			}

			return super.getFieldValue(attribute, mbo);
		}

		public String getFieldValue(String attribute, MboRemote mbo,
				boolean useLocale) throws MXException {
			MboValueInfo mvi = Mbo.this.getMboSetInfo().getMboValueInfo(
					attribute);

			if (mvi != null) {
				if ((this.alias != null) && (this.alias.length() > 0)) {
					return this.alias + "." + attribute.toLowerCase();
				}
				return Mbo.this.getName() + "." + attribute.toLowerCase();
			}

			return super.getFieldValue(attribute, mbo, useLocale);
		}
	}
}
