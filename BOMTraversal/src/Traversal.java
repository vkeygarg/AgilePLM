import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import com.agile.api.APIException;
import com.agile.api.AgileSessionFactory;
import com.agile.api.IAgileList;
import com.agile.api.IAgileSession;
import com.agile.api.ICell;
import com.agile.api.IItem;
import com.agile.api.IManufacturerPart;
import com.agile.api.IQuery;
import com.agile.api.IRow;
import com.agile.api.ITable;
import com.agile.api.ITwoWayIterator;
import com.agile.api.ItemConstants;
import com.agile.api.ManufacturerPartConstants;

public class Traversal {
	private static Properties props;
	static IAgileSession aSession = null;
	private static String compSortValue = "";
	private static int updatedRecords = 1;
	public static void main(String[] args) {
		String propertyFilePath = "C:/Personal/Agile/polycom.properties";
		if ((args != null) && (args.length > 0)) {
			propertyFilePath = args[0];
		}
		try {
			props = new Properties();
			InputStream stream = new FileInputStream(new File(propertyFilePath));
			props.load(stream);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		String shippableItemBaseID = props.getProperty("shippableItemBaseID");
		compSortValue = props.getProperty("compSortValue");
		try {
			aSession = createSession();
			ITwoWayIterator itr = getAllShippableItems(aSession, "["	+ shippableItemBaseID + "] == 'Yes'");// Get All Parts that are Shippable Items
			while (itr.hasNext()) {
				IRow iRow = (IRow) itr.next();
				IItem itemRow = (IItem) iRow.getReferent();
				System.out.println("Item Traversal:" + itemRow.getName());
				traverseBOM(itemRow, 1);
			}// traverse through Item
		} catch (APIException ex) {
			System.out.println(ex.getMessage());
			ex.printStackTrace();
		}
	}

	private static void traverseBOM(IItem item, int level) throws APIException {
		ITable bom = item.getTable(ItemConstants.TABLE_BOM);
		if (bom.size() > 0) {
			ITwoWayIterator i = bom.getReferentIterator();
			while (i.hasNext()) {
				IItem bomItem = (IItem) i.next();
				System.out.print(indent(level));
				System.out.println(bomItem.getName());
				ITable bomItemMnfrTable = bomItem.getTable(ItemConstants.TABLE_MANUFACTURERS);
				if (bomItemMnfrTable.size() > 0) {
					ITwoWayIterator itrMfr = bomItem.getTable(ItemConstants.TABLE_MANUFACTURERS).getTableIterator();
					traverseMnfrTable(itrMfr);
				}
				traverseBOM(bomItem, level + 1);
			}
		}else{
			ITable itemMnfrTable = item.getTable(ItemConstants.TABLE_MANUFACTURERS);
			if (itemMnfrTable.size() > 0) {
				ITwoWayIterator itrMfr = item.getTable(ItemConstants.TABLE_MANUFACTURERS).getTableIterator();
				traverseMnfrTable(itrMfr);
			}
		}
	}

	private static String indent(int level) {
		if (level <= 0) {
			return "";
		}
		char c[] = new char[level * 2];
		Arrays.fill(c, ' ');
		return new String(c);
	}

	private static void traverseMnfrTable(ITwoWayIterator mnfrTable) throws APIException {
		String mfrName = "";
		String mpnName = "";
		while (mnfrTable.hasNext()) {
			IRow row = (IRow) mnfrTable.next();
			Map<Integer, String> params = new HashMap<Integer, String>();

			mfrName = row.getValue(ItemConstants.ATT_MANUFACTURERS_MFR_NAME).toString();
			mpnName = row.getValue(ItemConstants.ATT_MANUFACTURERS_MFR_PART_NUMBER).toString();

			params.put(ManufacturerPartConstants.ATT_GENERAL_INFO_MANUFACTURER_NAME, mfrName);
			params.put(ManufacturerPartConstants.ATT_GENERAL_INFO_MANUFACTURER_PART_NUMBER, mpnName);
			System.out.print((updatedRecords++) + ".    " + mpnName + "+");
			System.out.print(mpnName + "=");
			IManufacturerPart mnfrPart = (IManufacturerPart) aSession
					.getObject(ManufacturerPartConstants.CLASS_MANUFACTURER_PART_BASE_CLASS, params);
			//
			if (mnfrPart != null) {
				ICell cell = mnfrPart.getCell(new Integer(props.getProperty("compSortBaseId")));
				System.out.println(mnfrPart.getValue(new Integer(props.getProperty("compSortBaseId"))));
				try {
					IAgileList list = cell.getAvailableValues();
					list.setSelection(new Object[] { compSortValue });
					cell.setValue(list);
					System.out.println("      Now="
							+ mnfrPart.getValue(new Integer(props.getProperty("compSortBaseId"))).toString());
				} catch (Exception e) {
					System.out.println(
							"Error While Update MPN record, Please update manually:" + mpnName + "(" + mfrName + ")");
				}
			}
			//
		}
	}

	private static IAgileSession createSession() throws APIException {
		String URL = props.getProperty("serverUrl");
		AgileSessionFactory f = AgileSessionFactory.getInstance(URL);
		Map<Integer, String> params = new HashMap<Integer, String>();
		String uname = props.getProperty("username");
		String pwd = props.getProperty("password");
		params.put(AgileSessionFactory.USERNAME, uname);
		params.put(AgileSessionFactory.PASSWORD, pwd);
		IAgileSession ses = f.createSession(params);
		return ses;
	}

	private static ITwoWayIterator getAllShippableItems(IAgileSession session,	String whereClause) throws APIException {
		ITwoWayIterator results = null;
		IQuery query = (IQuery) session.createObject(IQuery.OBJECT_TYPE,ItemConstants.CLASS_ITEM_BASE_CLASS);
		query.setCriteria(whereClause);

		query.setCaseSensitive(false);
		ITable tbl = query.execute();
		System.out.println("Query count=" + tbl.size());
		results = tbl.getTableIterator();
		return results;

	}

}