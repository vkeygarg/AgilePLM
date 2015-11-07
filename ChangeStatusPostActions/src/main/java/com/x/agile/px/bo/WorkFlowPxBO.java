package com.x.agile.px.bo;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.IChange;
import com.agile.api.IItem;
import com.agile.api.ITable;
import com.agile.api.ITwoWayIterator;
import com.agile.api.ItemConstants;

/**
 * Description: Process Extension class holds implemented business logic
 *
 */
public class WorkFlowPxBO {

	Properties prop;
	final static Logger logger = Logger.getLogger(WorkFlowPxBO.class);

	public void init() throws IOException {

		InputStream inputStream = null;
		prop = new Properties();
		String propFileName = "config.properties";
		try {
			inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
			if (inputStream != null) {
				prop.load(inputStream);
			} else {
				throw new FileNotFoundException("Property file '" + propFileName + "' not found in the classpath");
			}
		} catch (IOException e) {
			throw e;
		} finally {
			if (inputStream != null)
				inputStream.close();
		}
	}

	/**
	 * @param aglSesion
	 * @param chgObj
	 * @throws APIException
	 *             purpose: Reads Affected Item Table of the Change and update
	 *             attributes of affected items
	 */
	public void updateItemAttr(IChange chgObj) throws APIException {
		ITable affTable = chgObj.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
		ITwoWayIterator affItr = affTable.getReferentIterator();
		IItem affItemObj = null;
		ITable itemBomTable = null;
		String attrVal = "";
		while (affItr.hasNext()) {
			affItemObj = (IItem) affItr.next();
			itemBomTable = affItemObj.getTable(ItemConstants.TABLE_REDLINEBOM);
			logger.info(affItemObj.getName() + "Red Line BOM Table count is " + itemBomTable.size());
			// Update P2 attribute to BOM/Item if affected item has BOM
			// components
			if (itemBomTable.size() > 0)
				attrVal = prop.getProperty("ITEMS.P2.LIST04.VAL_BOM");
			else
				attrVal = prop.getProperty("ITEMS.P2.LIST04.VAL_ITEM");
			affItemObj.setValue(Integer.parseInt(prop.getProperty("ITEMS.P2.LIST04.BASEID")), attrVal);
		}
	}

}
