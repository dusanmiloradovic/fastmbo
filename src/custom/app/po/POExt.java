package custom.app.po;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection; 
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import psdi.app.inventory.InventoryRemote;
import psdi.app.po.PO;
import psdi.app.po.POLine;
import psdi.app.po.POLineRemote;
import psdi.app.pr.PRLineRemote;
import psdi.app.pr.PRRemote;
import psdi.app.pr.PRStatusRemote;
import psdi.app.rfq.RFQLineRemote;
import psdi.mbo.Mbo;
import psdi.mbo.MboRemote;
import psdi.mbo.MboSet;
import psdi.mbo.MboSetRemote;
import psdi.mbo.MboValueInfo;
import psdi.mbo.SqlFormat;
import psdi.mbo.StatusHandler;
import psdi.server.MXServer;
import psdi.util.MXApplicationException;
import psdi.util.MXException;
import psdi.util.Resolver;
import custom.app.pr.PRExt;
import custom.app.rfq.QuotationLineExt;
import custom.app.rfq.RFQExt;

public class POExt extends PO implements PORemoteExt {

	int j =0;
	public POExt(MboSet ms) throws MXException, RemoteException {
		super(ms);
		j=1;
	}

	protected StatusHandler getStatusHandler() {
		try {
			if ("RFT".equals(getString("APPTYPE"))) {
				return new RFTStatusHandler(this);
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (MXException e) {
			e.printStackTrace();
		}
		return new PoStatusHandlerExt(this);
	}

	public String getStatusListName() {
		try {
			String app = getThisMboSet().getApp();
			if (app != null) {
				if (app.equals("SWO")) {
					return "SWOSTATUS";
				}
				if ("RFT".equals(app)) {
					return "RFTSTATUS";
				}
			}
		} catch (RemoteException e) {
			e.printStackTrace();
			throw new RuntimeException(e);

		}
		return super.getStatusListName();
	}

	public void save() throws MXException, RemoteException {

		MboSetRemote polines = getMboSet("POLINE");
		int size = polines.count();
		int i = 0;
		if (size > 0) {
			Date min = null;
			Date max = null;
			while (i < size) {
				MboRemote currentLine = polines.getMbo(i);
				if (!currentLine.toBeDeleted()
						&& !currentLine.isNull("VENDELIVERYDATE")) {
					Date currentLineDate = currentLine
							.getDate("VENDELIVERYDATE");
					if (min == null || min.after(currentLineDate)) {
						min = currentLineDate;
					}
					if (max == null || max.before(currentLineDate)) {
						max = currentLineDate;
					}
				}
				/*
				 * Added by Dusan Miloradovic, 31.1.2010 When the user deletes
				 * the po line, if the po line had different siteid than the
				 * conforming rfq line, rfq line will not be de-linked because
				 * the default relationship includes the siteid, while the
				 * customised doesn't.
				 */
				if (currentLine.toBeDeleted()) {
					delinkPOAndRFQLines(currentLine);
				}
				i++;
			}

			setValue("REQUIREDDATE", min, 11L);
			setValue("VENDELIVERYDATE", max, 11L);
		}
		if (isNew()) {
			String ponum = getString("PONUM");
			SqlFormat sqlf = new SqlFormat(getUserInfo(),
					"ponum=:0 and ORGID=:1");
			sqlf.setObject(0, "PO", "PONUM", ponum);
			String orgid = getString("ORGID");
			sqlf.setObject(1, "PO", "ORGID", orgid);
			MboSetRemote set = getMboSet("$PO", "PO");
			set.setWhere(sqlf.format());
			set.reset();
			if (set.count() > 0) {
				Object params[] = { "PO=" + ponum + ", Organization=" + orgid };
				throw new MXApplicationException("system", "duplicatekey",
						params);
			}
			setValue("PAYONRECEIPT", false, 11);

		}
		super.save();
	}

	private void delinkPOAndRFQLines(MboRemote poLineRemote)
			throws RemoteException, MXException {
		// Copy/paste from the private original MBO:
		String ponum = poLineRemote.getString("ponum");
		String polinenum = poLineRemote.getString("polinenum");
		String polineid = poLineRemote.getString("polineid");

		// The MBOset names are inherited from the original PO class
		// they have to have the same names,
		// otherwise the "record was updated by another user" occurs.

		SqlFormat sqf = new SqlFormat(getUserInfo(),
				"prnum in (select prnum from prline where ponum=:1 ) ");
		sqf.setObject(1, "PRLINE", "ponum", ponum);

		MboSetRemote mboPRSet = getMboSet("$getponum"
				+ poLineRemote.getString("polineid"), "pr", sqf.format());

		SqlFormat sqlf = new SqlFormat(getUserInfo(),
				"ponum=:1 and polinenum=:2");
		sqlf.setObject(1, "RFQLINE", "ponum", ponum);
		sqlf.setObject(2, "RFQLINE", "polinenum", polinenum);

		MboSetRemote mboRFQLineSet = getMboSet("$rfqline"
				+ poLineRemote.getString("polineid"), "rfqline", sqlf.format());
		MboRemote linkedPR = mboPRSet.getMbo(0);
		MboRemote linkedRFQ = mboRFQLineSet.getMbo(0);
		int m = 0;
		do {
			MboRemote prRemote = mboPRSet.getMbo(m);
			if (prRemote == null)
				break;
			MboSetRemote prLineSet = prRemote.getMboSet("PRLINE");
			int n = 0;
			do {
				MboRemote prLineRemote = prLineSet.getMbo(n);
				if (prLineRemote == null)
					break;
				if (prLineRemote.getString("polineid").equals(polineid))
					((PRLineRemote) prLineRemote).setNullValuesToPOVariables();
				n++;
			} while (true);
			m++;
		} while (true);

		// if (linkedPR != null) {
		// ((PRLineRemote) linkedPR).setNullValuesToPOVariables();
		// }
		if (linkedRFQ != null) {
			((RFQLineRemote) linkedRFQ).setNullValuesToPOVariables();
		}

	}

	public void setRelatedMboEditibility(String relationName,
			MboSetRemote mboSet) throws MXException, RemoteException {
		super.setRelatedMboEditibility(relationName, mboSet);

		if (relationName.equals("DOCLINKS")) {
			super.setRelatedMboEditibility(relationName, mboSet);
			if (deleteAddStatusSet()) {
				mboSet.setFlag(7L, false);
			}
			return;
		}

		String app = getThisMboSet().getApp();
		if ("SWO".equals(app)
				&& ("CAN".equalsIgnoreCase(getInternalStatus()) || "CLOSE"
						.equalsIgnoreCase(getInternalStatus())))
			setFlag(7L, true);
	}

	private boolean docLinksAlwaysAccessible() throws RemoteException,
			MXException {
		MboRemote personGroup = getMboSet("#pg", "PERSONGROUP",
				"PERSONGROUP='CDP_CLOSE'").getMbo(0);
		MboSetRemote allUsers = personGroup.getMboSet("ALLPEOPLEINPERSONGROUP");
		allUsers.setUserWhere("personid='" + getUserInfo().getUserName() + "'");
		allUsers.reset();
		return !allUsers.isEmpty();
		// return !getMboSet("$#dlaw", "GROUPUSER",
		// "userid='" + getUserInfo().getUserName() + "' and groupname in
		// ('CDP/4','CDP/6','MAXADMIN')").isEmpty();

	}

	protected void setEditibilityFlags(boolean flag) throws MXException,
			RemoteException {
		super.setEditibilityFlags(flag);
		String poFields[] = { "ACTCOMPDATE", "SUPERVISOR",
				"DESCRIPTION_LONGDESCRIPTION", "PRTYPE", "DEPARTMENT", "PROJ",
				"ORIGREF", "PURMODE", "SHUTID", "AREA", "REQUESTEDBY",
				"FOLLOWUPDATE", "VENDELIVERYDATE" };
		setFieldFlag(poFields, 7L, flag);
	}

	public void add() throws MXException, RemoteException {

		super.add();
		setValue("buyahead", false, 11L);
		if (getThisMboSet().getApp() == null
				|| getThisMboSet().getApp().equals("PO")) {
			setValue("APPTYPE", "PO", 11);
			getMboValue("PONUM").autoKey();
			// setValue("PAYONRECEIPT", true, 11);
			Date creationDate = getDate("ORDERDATE");
			long towWeeks = 2 * 7 * 24 * 60 * 60 * 1000;
			Date deliveryDate = new Date(creationDate.getTime() + towWeeks);
			setValue("REQUIREDDATE", deliveryDate, 11L);
			setValue("VENDELIVERYDATE", deliveryDate, 11L);
		} else if (getThisMboSet().getApp().equals("IA")) {
			setValue("APPTYPE", "INSP", 11);
			setValue("POTYPE", "INSP", 11);
			getMboValue("PONUM").autoKey();
		} else if (getThisMboSet().getApp().equals("SWO")) {
			setValue("APPTYPE", "SWO", 11);
			setValue("PAYONRECEIPT", false, 11);
			if (isNull("PONUM"))
				getMboValue("PONUM").autoKey();
		} else if (getThisMboSet().getApp().equals("RO")) {
			setValue("APPTYPE", "RO", 11);
			setValue("PAYONRECEIPT", false, 11);
			setValue("requestedby", getUserName(), 11L);
		} else if (getThisMboSet().getApp().equals("RFT")) {
			getMboValue("PONUM").autoKey();
			setValue("APPTYPE", "RFT", 11);
			setValue("INTERNAL", true, 11);
			setValue("RECEIPTS", "NONE", 11);
		}
	}

	public void createIA() throws RemoteException, MXException {
		Mbo ia = (Mbo) getMboSet("RELATEDIA").add();
		ia.setValue("APPTYPE", "INSP", 11);
		ia.setValue("POTYPE", "INSP", 11);
		ia.getMboValue("PONUM").autoKey();
		ia.setValue("RELPO", getString("PONUM"));
		ia.setValue("DESCRIPTION", getString("DESCRIPTION"), 11);
		ia.setValue("VENDOR", getString("LOCALVENDOR"), 2L);
		ia.setValue("CONTACT", getString("CONTACT"), 2L);
		ia.setValue("PURCHASEAGENT", getString("PURCHASEAGENT"), 2L);
		ia.setValue("REQUIREDDATE", getString("REQUIREDDATE"), 2L);
		ia.setValue("VENDELIVERYDATE", getString("VENDELIVERYDATE"), 2L);
		ia.setValue("LOCALVENDOR", getString("VENDOR"), 2L);
	}

	public void duplicateIA() throws MXException, RemoteException {
		MboSetRemote iaSet = (MboSetRemote) getMboSet("RELATEDIA");
		if (iaSet.isEmpty()) {
			throw new MXApplicationException("poext", "emptyia");
		}
		Mbo duplicatedIA = (Mbo) iaSet.getMbo(0).duplicate();
		duplicatedIA.getMboValue("PONUM").autoKey();
	}

	public MboRemote createSupplementOrder(String ponum, String description)
			throws MXException, RemoteException {
		getThisMboSet().setAutoKeyFlag(false);
		MboRemote poRemote = duplicate();
		getThisMboSet().setAutoKeyFlag(true);
		poRemote.setValue("ponum", ponum, 11L);
		poRemote.setValue("originalponum", getString("ponum"), 11L);
		poRemote.setValue("description", description, 11L);
		poRemote.setValue("potype", getTranslator().toExternalDefaultValue(
				"POTYPE", "CHG", this), 11L);
		MXServer.getBulletinBoard().post("PO.CREATESUPORDER", getUserInfo());

		// changeStatus(getTranslator().toExternalDefaultValue("POSTATUS",
		// "CAN", this), MXServer.getMXServer().getDate(), memo);
		return poRemote;
	}

	public void canCreateSupOrder() throws RemoteException, MXException {
		// canCreateChangeOrder();
	}

	public void init() throws MXException {
		super.init();
		getMboValue("siteid").setReadOnly(false);
		try {
			String app = getThisMboSet().getApp();
			if (app != null && app.equals("RFT")) {
				setFieldFlag("storeloc", READONLY, false);
				setFieldFlag("storelocsiteid", READONLY, false);
			}
			if (app != null
					&& app.equals("SWO")
					&& (!"WAPPR".equals(getInternalStatus()) && getInternalStatus() != null)) {
				setFieldFlag("CONTRACTCLASS", READONLY, true);
				setFieldFlag("CONTRACTCATEGORY", READONLY, true);
				setFieldFlag("BANKGUARANTEE", READONLY, true);
				setFieldFlag("DURATION", READONLY, true);
			}
		} catch (RemoteException e) {
			e.printStackTrace();
			throw new MXApplicationException("system", "systemerror");
		}
	}

	public MboSetRemote getList(String attribute) throws MXException,
			RemoteException {
		String app = getThisMboSet().getApp();
		if (attribute.equalsIgnoreCase("status")) {
			return getStatusList(app);
		}
		return super.getList(attribute);
	}

	private MboSetRemote getStatusList(String app) throws RemoteException,
			MXException {
		if (app != null) {
			if (app.equals("SWO") || "RECEIPTS".equals(app)) {
				return getStatusForApp("SWOSTATUS");
			}
			if (app.equals("RFT")) {
				return getStatusForApp("RFTSTATUS");
			}
		}
		return super.getList("status");
	}

	private MboSetRemote getStatusForApp(String domainid) throws MXException,
			RemoteException {
		SqlFormat sqlf = new SqlFormat("domainid=:0");
		sqlf.setObject(0, "SYNONYMDOMAIN", "domainid", domainid);
		MboSetRemote mbs = getMboSet(domainid + "$", "SYNONYMDOMAIN", sqlf
				.format());
		return mbs;
	}

	

	public void canDeleteAttachedDocs() throws MXException, RemoteException {

		if (!deleteAddStatusSet()) {
			super.canDeleteAttachedDocs();
			return;
		}
		if (!canDeleteDoclinks()) {
			throw new MXApplicationException("access", "notauthorized");
		}

	}

	private boolean canDeleteDoclinks() throws RemoteException, MXException {
	
		return !getMboSet(
				"$#dlaw",
				"GROUPUSER",
				"userid='" + getUserInfo().getUserName()
						+ "' and groupname in	 ('CDP','CDP/6')").isEmpty()
				&& deleteAddStatusSet();

	}
	
	private boolean deleteAddStatusSet() throws RemoteException, MXException{
		return getString("status").startsWith("PODOC");
	}
	public void changeStatus(String status, Date date, String memo,
			long accessModifier) throws MXException, RemoteException {

		if ("RFT".equals(getString("APPTYPE")) && getMboSet("POLINE").isEmpty()
				&& "APPR".equals(status)) {
			throw new MXApplicationException("inventory", "nolines");
		}
		super.changeStatus(status, date, memo, accessModifier);

		MboSetRemote polineSet = getMboSet("POLINE");

		if (getInternalStatus().equalsIgnoreCase("CLOSE")
				&& getString("RECEIPTS").equals("PARTIAL")) {
			// MboSetRemote polineSet = getMboSet("POLINE_$", "POLINE",
			// "PONUM='" + getString("ponum") + "'");
			if (!polineSet.isEmpty())
				closePO(polineSet);
		}

		if (getInternalStatus().equalsIgnoreCase("CLOSE")
				&& getString("RECEIPTS").equals("NONE")) {
			// MboSetRemote polineSet = getMboSet("POLINE_$", "POLINE",
			// "PONUM='" + getString("ponum") + "'");
			if (!polineSet.isEmpty()) {
				closePOForNoneRec(polineSet);
			}
		}
	}

	public MboSetRemote getMboSet(String name) throws MXException,
			RemoteException {

		MboSetRemote mboSet = null;
		try {
			mboSet = super.getMboSet(name);
		} catch (MXException e) {

			e.printStackTrace();
			throw e;
		}
		return mboSet;
	}

	private void closePO(MboSetRemote polineSet) throws RemoteException,
			MXException {
		if (MXServer.getBulletinBoard().isPosted("PO.PARTCHGORDER",
				getUserInfo())) {
			/*
			 * Added by Dusan Miloradovic Fix for "record was updated by another
			 * user". It was happening just during the creation of a change
			 * order for partially received po(new functionality). It has to
			 * reference newly created po from rfqline and poline, and the
			 * update to these mboSets is clashing with the update below.
			 */
			return;
		}
		MboRemote poline = polineSet.getMbo(0);
		String crt = "  PONUM = '" + getString("PONUM")
				+ "' and POLINENUM in (";
		boolean first = true;
		boolean hasPoLines = false;
		for (int i = 0; polineSet.getMbo(i) != null; i++) {
			if (polineSet.getMbo(i).getDouble("RECEIVEDQTY") == 0) {
				hasPoLines = true;
				if (!first) {
					crt += ", ";
				} else {
					first = false;
				}
				crt = crt + "'" + polineSet.getMbo(i).getString("POLINENUM")
						+ "'";
			}
		}
		if (!hasPoLines) {
			throw new MXApplicationException("po",
					"canNotClosePoWithNoLinesNone");
		}
		// String crt1 = crt.substring(0, crt.length() - 1);
		crt = crt + ")";
		MboRemote rfq = getMboSet(
				"RFQ_$",
				"RFQ",
				"RFQNUM ='" + poline.getString("RFQNUM") + "' and siteid ='"
						+ getString("siteid") + "'").getMbo(0);
		if (rfq != null) {
			rfq.setValue("HISTORYFLAG", false, 11l);
			rfq.setValue("STATUS", "SENT", 11l);

			MboSetRemote rfqLines = getMboSet("RFQLINE_$", "RFQLINE", crt
					+ " and rfqnum='" + rfq.getString("rfqnum")
					+ "' and siteid ='" + rfq.getString("siteid") + "'");
			for (int i = 0; rfqLines.getMbo(i) != null; i++) {

				MboRemote rfqline = rfqLines.getMbo(i);
				rfqline.setValueNull("POLINENUM", 11l);
				rfqline.setValueNull("POLINEID", 11l);
				rfqline.setValueNull("PONUM", 11l);
			}

		}
		MboRemote pr = getMboSet(
				"PR_$",
				"PR",
				"PRNUM ='" + poline.getString("PRNUM") + "' and siteid ='"
						+ getString("siteid") + "'").getMbo(0);
		if (pr != null) {
			pr.setValue("HISTORYFLAG", false, 11l);
			pr.setValue("STATUS", "APPR", 11l);

			MboSetRemote prlines = getMboSet("PRLINE_$", "PRLINE", crt
					+ " and prnum='" + pr.getString("prnum")
					+ "' and siteid ='" + pr.getString("siteid") + "'");
			for (int i = 0; prlines.getMbo(i) != null; i++) {
				MboRemote prline = prlines.getMbo(i);
				prline.setValueNull("POLINENUM", 11l);
				prline.setValueNull("POLINEID", 11l);
				prline.setValueNull("PONUM", 11l);
			}
		}

	}

	private void closePOForNoneRec(MboSetRemote polineSet)
			throws RemoteException, MXException {
		if (MXServer.getBulletinBoard().isPosted("PO.PARTCHGORDER",
				getUserInfo())) {
			/*
			 * Added by Dusan Miloradovic Fix for "record was updated by another
			 * user". It was happening just during the creation of a change
			 * order for partially received po(new functionality). It has to
			 * reference newly created po from rfqline and poline, and the
			 * update to these mboSets is clashing with the update below.
			 */
			return;
		}
		MboRemote poline = polineSet.getMbo(0);
		if (polineSet.count() == 0) {
			return;
		}
		String crt = "  PONUM = '" + getString("PONUM")
				+ "' and POLINENUM in (";
		for (int i = 0; polineSet.getMbo(i) != null; i++) {
			crt = crt + "'" + polineSet.getMbo(i).getString("POLINENUM") + "',";
		}
		String crt1 = crt.substring(0, crt.length() - 1);
		crt1 = crt1 + ")";
		MboRemote rfq = getMboSet(
				"RFQ_$",
				"RFQ",
				"RFQNUM ='" + poline.getString("RFQNUM") + "' and siteid ='"
						+ getString("siteid") + "'").getMbo(0);
		if (rfq != null) {
			rfq.setValue("HISTORYFLAG", false, 11l);
			rfq.setValue("STATUS", "SENT", 11l);
			MboSetRemote rfqLines = getMboSet("RFQLINE_$", "RFQLINE", crt1
					+ " and rfqnum='" + rfq.getString("rfqnum")
					+ "' and siteid ='" + rfq.getString("siteid") + "'");
			for (int i = 0; rfqLines.getMbo(i) != null; i++) {
				MboRemote rfqline = rfqLines.getMbo(i);
				rfqline.setValueNull("POLINENUM", 11l);
				rfqline.setValueNull("POLINEID", 11l);
				rfqline.setValueNull("PONUM", 11l);
			}
		}
		MboRemote pr = getMboSet(
				"PR_$",
				"PR",
				"PRNUM ='" + poline.getString("PRNUM") + "' and siteid ='"
						+ getString("siteid") + "'").getMbo(0);
		if (pr != null) {
			pr.setValue("HISTORYFLAG", false, 11l);
			pr.setValue("STATUS", "APPR", 11l);
			MboSetRemote prlines = getMboSet("PRLINE_$", "PRLINE", crt1
					+ " and prnum='" + pr.getString("prnum")
					+ "' and siteid ='" + pr.getString("siteid") + "'");
			for (int i = 0; prlines.getMbo(i) != null; i++) {
				MboRemote prline = prlines.getMbo(i);
				prline.setValueNull("POLINENUM", 11l);
				prline.setValueNull("POLINEID", 11l);
				prline.setValueNull("PONUM", 11l);
			}
		}
	}

	public void prorateServices() throws RemoteException, MXException {
		MboSetRemote poLines = getMboSet("POLINE");
		double tempTotalProrateServiceCost = 0.0D;
		double tempTotalMaterialCost = 0.0D;
		boolean onlyDirectIssue = false;
		if (getMboServer().getMaxVar().getBoolean("PRSPECIALDIRECT",
				getOrgSiteForMaxvar("PRSPECIALDIRECT")))
			onlyDirectIssue = true;
		int i = 0;
		do {
			POLine oneLine = (POLine) poLines.getMbo(i);
			if (oneLine == null)
				break;
			if (oneLine.getBoolean("prorateservice"))
				tempTotalProrateServiceCost += oneLine.getDouble("loadedcost");
			if (!oneLine.isServiceType()
					&& (oneLine.getBoolean("issue") || !onlyDirectIssue))
				tempTotalMaterialCost += oneLine.getDouble("linecost");
			i++;
		} while (true);
		if (tempTotalProrateServiceCost == 0.0D
				|| tempTotalMaterialCost == 0.0D)
			return;
		double prorateFactor = tempTotalProrateServiceCost
				/ tempTotalMaterialCost;
		i = 0;
		do {
			POLine oneLine = (POLine) poLines.getMbo(i);
			if (oneLine != null) {
				if (!oneLine.isServiceType()
						&& (oneLine.getBoolean("issue") || !onlyDirectIssue)) {
					oneLine.setValue("proratecost", oneLine
							.getDouble("linecost")
							* prorateFactor, 2L);
					oneLine.setValue("loadedcost", oneLine
							.getDouble("loadedcost")
							+ oneLine.getDouble("proratecost"), 2L);
				} else if (oneLine.getBoolean("prorateservice")) {
					oneLine.setValue("proratecost", -oneLine
							.getDouble("loadedcost"), 2L);
					oneLine.setValue("loadedcost", 0, 2L);
				}
				i++;
			} else {
				return;
			}
		} while (true);
	}

	public MboRemote duplicate() throws MXException, RemoteException {
		MboRemote result = super.duplicate();
		// result.setValue("EXCHANGERATE", getString("EXCHANGERATE"), 11L);
		result.setValue("EXCHANGERATE", getExchangeRate(MXServer.getMXServer()
				.getDate()), 11L);
		return result;

	}

	public MboRemote createChangeOrder(String ponum, String description)
			throws MXException, RemoteException {
		// TODO Auto-generated method stub
		MboRemote result = super.createChangeOrder(ponum, description);
		result.setValue("EXCHANGERATE", getString("EXCHANGERATE"), 11L);
		resetPONumOnRFQLines(ponum);
		return result;
	}

	public MboRemote createPartialChangeOrder(String ponum, String description)
			throws MXException, RemoteException {
		if (createPartialChangeOrderNotAllowed()) {
			MXServer.getBulletinBoard()
					.remove("PO.PARTCHGORDER", getUserInfo());
			throw new MXApplicationException("po", "justpartial");

		}
		POLine.loadSkipFieldCopyHashSet();
		POLine.getHashSet().remove("POLINENUM");
		POLine.getHashSet().remove("MRNUM");
		POLine.getHashSet().remove("MRLINENUM");
		POLine.getHashSet().remove("REQUESTEDBY");
		POLine.getHashSet().remove("RECEIPTSCOMPLETE");
		// POLine.getHashSet().remove("RECEIVEDQTY");
		// POLine.getHashSet().remove("RECEIVEDTOTALCOST");
		// POLine.getHashSet().remove("RECEIVEDUNITCOST");
		POLine.getHashSet().remove("REJECTEDQTY");
		POLine.getHashSet().remove("ORGID");
		POLine.getHashSet().remove("SITEID");

		PORemoteExt poRemote = null;

		MXServer.getBulletinBoard().post("PO.CREATECHGORDER", getUserInfo());

		try {
			getThisMboSet().setAutoKeyFlag(false);
			poRemote = (PORemoteExt) duplicate();
			getThisMboSet().setAutoKeyFlag(true);
			poRemote.setValue("ponum", ponum);
			poRemote.setValue("orgid", getString("orgid"));
			poRemote.setValue("siteid", getString("siteid"));
			poRemote.setValue("originalponum", getString("ponum"), 11L);

			poRemote.setValue("description", description);

			poRemote.setValue("potype", getTranslator().toExternalDefaultValue(
					"POTYPE", "CHG", this), 11L);

			String memo = Resolver.getResolver().getMessage("po",
					"AllowedCreationOfChangeOrder").getMessage();

			super.changeStatus(getTranslator().toExternalDefaultValue(
					"POSTATUS", "CLOSE", this), MXServer.getMXServer()
					.getDate(), memo);

			poRemote.fixProrateLinesBeforeDeletion();
			poRemote.deleteCompleteLines();

			reMapRFQAndPRLinks(poRemote);
			poRemote.setValue("receipts", "NONE", 11L);

			MboSetRemote poLines = poRemote.getMboSet("POLINE");
			for (MboRemote mr = poLines.moveFirst(); mr != null; mr = poLines
					.moveNext()) {
				mr.setValue("receiptscomplete", false, 11L);
			}

		} finally {
			POLine.getHashSet().add("POLINENUM");
			POLine.getHashSet().add("MRNUM");
			POLine.getHashSet().add("MRLINENUM");
			POLine.getHashSet().add("REQUESTEDBY");

			POLine.getHashSet().add("RECEIPTSCOMPLETE");
			// POLine.getHashSet().add("RECEIVEDQTY");
			// POLine.getHashSet().add("RECEIVEDTOTALCOST");
			// POLine.getHashSet().add("RECEIVEDUNITCOST");
			POLine.getHashSet().add("REJECTEDQTY");
			POLine.getHashSet().add("ORGID");
			POLine.getHashSet().add("SITEID");
			MXServer.getBulletinBoard().remove("PO.CREATECHGORDER",
					getUserInfo());

		}

		return poRemote;
	}

	private boolean createPartialChangeOrderNotAllowed() throws MXException,
			RemoteException {
		/*
		 * Changed by Dusan Miloradovic 28.1.2010 If the transaction record
		 * exist for the PO (it can have receivedqty=0), original Maximo classes
		 * disallow the creation of the change order, and this is the sole
		 * purpose of the new action Create change order for partially received
		 * POs. I just need to check if the transaction exists or not
		 */

		if (!getMboSet("MATRECTRANS").isEmpty()) {
			return false;
		}
		if (!getMboSet("SERVRECTRANS").isEmpty()) {
			return false;
		}
		// boolean cond1 = getTranslator().toInternalString("RECEIPTS",
		// getString("RECEIPTS")).equals("PARTIAL");
		// if (cond1)return false;
		// MboSetRemote poLineSet = getMboSet("POLINE");
		// for (MboRemote
		// pl=poLineSet.moveFirst();pl!=null;pl=poLineSet.moveNext()){
		// if (pl.getDouble("REJECTEDQTY")>0){
		// return false;
		// }
		// }
		return true;
	}

	public void updateInternalLeadTime(InventoryRemote invmbo)
			throws MXException, RemoteException {
		MboSetRemote polines = getMboSet("POLINE");
		int i = 0;
		do {
			POLineRemote poline = (POLineRemote) polines.getMbo(i++);
			if (poline == null) {
				break;
			}
			if (poline.getString("STORELOC") == null) {
				continue;
			}
			Date prApprovalDate = getPrApprovalDate(poline);
			if (prApprovalDate == null) {
				invmbo.setValue("INTERNALLEAD", 0);
				continue;
			}

			Date date = getLastPOApprDate();

			int dayDiff = custom.app.common.Utils.getDayDifference(
					prApprovalDate, date);
			// MboSetRemote inventorySet = (MboSetRemote)
			// poline.getMboSet("ITEM")
			// .getMbo(0).getMboSet("INVENTORY");
			// for (MboRemote inventory = inventorySet.moveFirst(); inventory !=
			// null; inventory = inventorySet
			// .moveNext()) {
			// if (inventory != null) {
			// inventory.setValue("INTERNALLEAD", dayDiff);
			// }
			// }
			invmbo.setValue("INTERNALLEAD", dayDiff);

		} while (true);
	}

	private Date getLastPOApprDate() throws RemoteException, MXException {
		MboSetRemote poStatusSet = getMboSet("POSTATUS");
		poStatusSet.setWhere("status='APPR'");
		poStatusSet.setOrderBy("changedate desc");
		if (poStatusSet.isEmpty()) {
			return null;
		}
		return poStatusSet.getMbo(0).getDate("CHANGEDATE");
	}

	private Date getPrApprovalDate(POLineRemote poline) throws RemoteException,
			MXException {
		MboSetRemote prlinesSet = poline.getMboSet("#PRLLL#", "PRLINE",
				"polineid=" + poline.getLong("polineid"));
		Date prApprovalDate = null;
		if (!prlinesSet.isEmpty()) {
			PRLineRemote prline = (PRLineRemote) prlinesSet.getMbo(0);
			PRRemote pr = (PRRemote) prline.getMboSet("PR").getMbo(0);
			PRStatusRemote prApprStatus = (PRStatusRemote) pr.getMboSet(
					"PRSTATUS",
					"PRSTATUS",
					"STATUS = 'APPRO' and prnum='" + pr.getString("prnum")
							+ "'").getMbo(0);
			if (prApprStatus == null) {
				return null;
			}
			prApprovalDate = prApprStatus.getDate("CHANGEDATE");
		}
		return prApprovalDate;
	}

	public static int getDayDifference(Date date1, Date date2) {
		Calendar time1 = Calendar.getInstance();
		time1.setTime(date1);
		time1.set(Calendar.HOUR_OF_DAY, 0);
		time1.set(Calendar.MINUTE, 0);
		time1.set(Calendar.SECOND, 0);
		time1.set(Calendar.MILLISECOND, 0);

		Calendar time2 = Calendar.getInstance();
		time2.setTime(date2);
		time2.set(Calendar.HOUR_OF_DAY, 0);
		time2.set(Calendar.MINUTE, 0);
		time2.set(Calendar.SECOND, 0);
		time2.set(Calendar.MILLISECOND, 0);

		long difMilli = time2.getTimeInMillis()
				+ time2.getTimeZone().getOffset(time2.getTimeInMillis())
				- time1.getTimeInMillis()
				- time1.getTimeZone().getOffset(time1.getTimeInMillis());

		return ((int) ((float) difMilli / (float) (24 * 60 * 60 * 1000)));
	}

	private void reMapRFQAndPRLinks(MboRemote copyPo) throws RemoteException,
			MXException {
		String originalPoNum = getString("PONUM");
		String poNum = copyPo.getString("PONUM");
		Map linesMap = new HashMap();
		MboSetRemote copyPoLineSet = copyPo.getMboSet("POLINE");
		// int cnt = 0;

		for (MboRemote poLine = copyPoLineSet.moveFirst(); poLine != null; poLine = copyPoLineSet
				.moveNext()) {
			int poLineNum = poLine.getInt("POLINENUM");
			MboRemote origPOLine = getMboSet("$_origPoLines", "POLINE",
					"ponum='" + originalPoNum + "' and polinenum=" + poLineNum)
					.getMbo(0);
			long poLineIdOrig = origPOLine.getUniqueIDValue();

			if (poLine.getBoolean("RECEIPTSCOMPLETE")
					&& !poLine.getBoolean("PRORATESERVICE")) {
				continue;
			}
			linesMap.put(new POLineKey(this, originalPoNum, poLineNum,
					poLineIdOrig), new POLineKey(this, poNum, poLine
					.getInt("POLINENUM"), poLine.getUniqueIDValue(), poLine
					.getString("ORGID"), poLine.getString("SITEID")));
			// linesMap.put(new POLineKey(this, originalPoNum, poLineNum,
			// poLineIdOrig), new POLineKey(this, poNum, ++cnt,
			// poLine.getUniqueIDValue(), poLine.getString("ORGID"),
			// poLine.getString("SITEID")));
			// poLine.setValue("POLINENUM", cnt, 11L);
			double orderQty = poLine.getDouble("ORDERQTY");
			double receivedQty = poLine.getDouble("RECEIVEDQTY");

			poLine.setValue("ORDERQTY", (double) (orderQty - receivedQty));
			poLine.setValueNull("RECEIVEDQTY", 11L);

		}

		MboSetRemote poLineSet = getMboSet("POLINE");

		for (MboRemote poLine = poLineSet.moveFirst(); poLine != null; poLine = poLineSet
				.moveNext()) {
			POLineKey poLineKey = new POLineKey(this, poLine);
			if (linesMap.containsKey(poLineKey)) {
				POLineKey value = (POLineKey) linesMap.get(poLineKey);
				fixReferencedLine(poLine, value, "RFQLINE");
				fixReferencedLine(poLine, value, "PRLINE");

				// int poLineNum = poLine.getInt("POLINENUM");

				// MboRemote origPOLine = getMboSet("$_origPoLines", "POLINE",
				// "ponum='" + originalPoNum + "' and polinenum=" +
				// poLineNum).getMbo(0);
				// poLine.setValue("PRNUM", origPOLine.getString("PRNUM"),11L);
				// poLine.setValue("PRLINENUM",
				// origPOLine.getInt("PRLINENUM"),11L);
				// poLine.setValue("RFQNUM",
				// origPOLine.getString("RFQNUM"),11L);
				// poLine.setValue("RFQLINENUM",
				// origPOLine.getInt("RFQLINENUM"),11L);
			}
		}

	}

	private void fixReferencedLine(MboRemote poLine, POLineKey value,
			String mboSetName) throws MXException, RemoteException {
		MboRemote mr = poLine.getMboSet(
				"$" + mboSetName + "#",
				mboSetName,
				"ponum='" + poLine.getString("PONUM") + "' and polinenum="
						+ poLine.getString("POLINENUM")).getMbo(0);

		if (mr != null) {
			mr.setValue("PONUM", value.poNum, 11L);
			mr.setValue("POLINENUM", value.poLineNum, 11L);
			mr.setValue("POLINEID", value.poLineId, 11L);
			// mr.setValue("ORGID", value.orgId, 11L);
			// mr.setValue("SITEID", value.siteId, 11L);
		}
	}

	public void fixProrateLinesBeforeDeletion() throws RemoteException,
			MXException {

		/*
		 * Dusan Miloradovic,11.11.2009 . When the creation of the partial
		 * change order is executed, completely received lines are deleted. We
		 * want to keep the prorate lines though, be them marked as fully
		 * received or not The prorate unit cost will be calculated according to
		 * the following formula: (old prorate unit cost/sum(linecost) without
		 * prorated lines) * (sum linecost(i)*(1-received(i)/ordered(i)), i
		 * iterates over non-prorated lines)
		 */
		double sumlinecost = 0;
		double quotedsum = 0;

		MboSetRemote poLineSet = getMboSet("POLINE");

		for (MboRemote pl = poLineSet.moveFirst(); pl != null; pl = poLineSet
				.moveNext()) {
			if (pl.getBoolean("PRORATESERVICE")) {
				continue;
			}
			double lineCost = pl.getDouble("LINECOST");
			double orderQty = pl.getDouble("ORDERQTY");
			double receivedQty = pl.getDouble("RECEIVEDQTY");

			quotedsum += lineCost * (1 - receivedQty / orderQty);
			sumlinecost += lineCost;

		}
		if (sumlinecost == 0) {
			return;
		}
		double quot = quotedsum / sumlinecost;
		for (MboRemote pl = poLineSet.moveFirst(); pl != null; pl = poLineSet
				.moveNext()) {
			if (pl.getBoolean("PRORATESERVICE")) {
				double unitCost = pl.getDouble("UNITCOST");
				double orderQty = pl.getDouble("ORDERQTY");
				pl.setValue("UNITCOST", unitCost * quot);
				pl.setValue("ORDERQTY", orderQty);
				// pl.setValue("LINECOST", unitCost * quot * orderQty);
				pl.setValueNull("RECEIVEDQTY", 11L);
				pl.setValue("RECEIPTSCOMPLETE", false, 11L);

			}
		}
	}

	public void deleteCompleteLines() throws MXException, RemoteException {
		MboSetRemote mboSet = getMboSet("POLINE");
		int cnt = 0;
		int dlt = 0;
		for (POLineExt mr = (POLineExt) mboSet.moveFirst(); mr != null; mr = (POLineExt) mboSet
				.moveNext()) {
			cnt++;
			mr.getMboSet("POCOST").deleteAll();
			if (mr.getBoolean("RECEIPTSCOMPLETE")
					&& !mr.getBoolean("PRORATESERVICE")) {
				mr.delete(7l);
				dlt++;
			} else {
				String origPoNum = getString("ORIGINALPONUM");
				long polinenum = mr.getLong("POLINENUM");
				MboSetRemote origPOCostSet = getMboSet("origPOLine$", "POLINE",
						"PONUM='" + origPoNum + "' and polinenum=" + polinenum)
						.getMbo(0).getMboSet("POCOST");
				for (MboRemote oc = origPOCostSet.moveFirst(); oc != null; oc = origPOCostSet
						.moveNext()) {
					MboRemote newCost = mr.getMboSet("POCOST").add();
					newCost.setValue("LINECOST", oc.getDouble("LINECOST"));
					// newCost.setValue("LOADEDCOST",
					// oc.getDouble("LOADEDCOST"));
					newCost.setValue("PERCENTAGE", oc.getDouble("PERCENTAGE"));
					newCost.setValue("QUANTITY", oc.getDouble("QUANTITY"));
					newCost
							.setValue("GLDEBITACCT", oc
									.getString("GLDEBITACCT"));

				}

			}

		}

	}

	protected boolean skipCopyField(MboValueInfo mvi) throws RemoteException,
			MXException {
		if (mvi.getName().equals("selfbill")) {
			return true;
		}
		return super.skipCopyField(mvi);
	}

	public ArrayList checkQuotationsCurrency(MboSetRemote quotations)
			throws RemoteException, MXException {
		ArrayList result = new ArrayList();
		if (quotations.isEmpty())
			return result;
		Vector rfqLineSize = quotations.getSelection();
		if (rfqLineSize.size() == 0)
			return result;
		quotations.resetWithSelection();
		QuotationLineExt quotationLine = (QuotationLineExt) quotations
				.moveFirst();
		while (quotationLine != null) {
			String poCurrency = getString("CURRENCYCODE");
			MboSetRemote vendors = quotationLine.getMboSet("RFQVENDOR");
			vendors.reset();
			String quotCurrency = vendors.getMbo(0).getString("CURRENCYCODE");
			if (!poCurrency.equals(quotCurrency)) {
				String[] params = { quotationLine.getString("RFQNUM"),
						quotationLine.getString("RFQLINENUM") };
				throw new MXApplicationException("po", "notsamecurr", params);
			}
			result.add(quotationLine);
			quotationLine = (QuotationLineExt) quotations.moveNext();
		}
		return result;
	}

	public Collection copyQuotationLinesToPO(ArrayList quotations)
			throws RemoteException, MXException {
		if (quotations.isEmpty())
			return new ArrayList();
		HashMap rfqs = new HashMap();
		for (int i = 0; i < quotations.size(); i++) {
			QuotationLineExt quotationLine = (QuotationLineExt) quotations
					.get(i);
			String rfqnum = quotationLine.getString("RFQNUM");
			if (!rfqs.containsKey(rfqnum)) {
				rfqs.put(rfqnum, quotationLine);
			}
			MboSetRemote rfqLineSet = quotationLine.getMboSet("RFQLINE");
			rfqLineSet.reset();
			quotationLine.copyThisQuotationLineToPOLine((MboRemote) this,
					rfqLineSet.getMbo(0));

		}
		return rfqs.values();
	}

	public void updateRFQsPRsStatus(Collection rfqs) throws MXException,
			RemoteException {
		boolean prChange = getMboServer().getMaxVar().getBoolean("PRCHANGE",
				getOrgSiteForMaxvar("PRCHANGE"));
		if (!prChange) {
			return;
		}
		for (Iterator iterator = rfqs.iterator(); iterator.hasNext();) {
			QuotationLineExt quotation = (QuotationLineExt) iterator.next();
			RFQExt rfq = (RFQExt) quotation.getMboSet("RFQ").getMbo(0);
			MboSetRemote rfqLineSet = rfq.getMboSet("RFQLINE");
			rfqLineSet.reset();
			if (isLinePOContNumFilled(rfqLineSet)) {
				String status = getTranslator().toExternalDefaultValue(
						"RFQSTAT", "CLOSE", rfq);
				rfq.changeStatus(status, MXServer.getMXServer().getDate(), "");
			}
			MboSetRemote prLineSet = rfq.getMboSet("PRLINE");
			if (!prLineSet.isEmpty() && isLinePOContNumFilled(prLineSet)) {
				PRExt pr = (PRExt) prLineSet.getMbo(0).getMboSet("PR")
						.getMbo(0);
				String status = getTranslator().toExternalDefaultValue(
						"PRSTATUS", "COMP", pr);
				pr.changeStatus(status, MXServer.getMXServer().getDate(), "");
			}
		}
	}

	private boolean isLinePOContNumFilled(MboSetRemote lineSet)
			throws MXException, RemoteException {
		if (lineSet == null)
			return false;
		if (lineSet.isEmpty())
			return false;
		int i = 0;
		do {
			MboRemote line = lineSet.getMbo(i);
			if (line != null) {
				if (line.getString("ponum").equals("")
						&& line.getString("contractnum").equals(""))
					return false;
				i++;
			} else {
				return true;
			}
		} while (true);
	}

	private void resetPONumOnRFQLines(String ponum) throws MXException,
			RemoteException {
		SqlFormat sqf = new SqlFormat(getUserInfo(), "ponum = :1");
		sqf.setObject(1, "RFQLINE", "ponum", getString("ponum"));
		MboSetRemote rfqLineSet = getMboSet("$rfqline", "rfqline", sqf.format());
		int i = 0;
		do {
			MboRemote rfqLineRemote = rfqLineSet.getMbo(i);
			if (rfqLineRemote != null) {
				rfqLineRemote.setValue("ponum", ponum, 11L);
				i++;
			} else {
				return;
			}
		} while (true);
	}

	

	public void determineReceiptStatus(POLineRemote poLine)
			throws RemoteException, MXException {
		super.determineReceiptStatus(poLine);
		MboSetRemote poLineSet = getMboSet("POLINE");
		int cnt = poLineSet.count();
		boolean allcomplete = (cnt==0)?false:true;
		boolean firstProrated=false;//If the first poline in the loop was prorated
		for (int i = 0; i < cnt; i++) {
			MboRemote mr=poLineSet.getMbo(i);
			if (!mr.isNull("prorateservice") && mr.getBoolean("prorateservice")) {
				//I have to check for null, because this method will be called
				//from the poline.add method at the time when the "prorateservice" is null
				//and that gives the Boolean field required error.
				if (i==0){
					firstProrated=true;
					allcomplete=false;
				}
				continue;
			}
			if (firstProrated){
				allcomplete=true;
				firstProrated=false;
			}
			allcomplete &= mr.getBoolean("receiptscomplete");

		}
		if (allcomplete) {
			setValue("receipts", "COMPLETE", 11L);
		}
		
	}

}
