package com.x.agile.plm.rest.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.agile.api.APIException;
import com.agile.api.AgileSessionFactory;
import com.agile.api.IAgileSession;
import com.agile.api.IAttachmentFile;
import com.agile.api.IItem;
import com.agile.api.IRow;
import com.agile.api.ITable;
import com.agile.api.ITwoWayIterator;
import com.agile.api.ItemConstants;

/**
 * @author 
 *
 */
public class AgilePLMServiceImpl {
	Properties prop;
	final static Logger logger = Logger.getLogger(AgilePLMServiceImpl.class);

	public void init() throws IOException {
		InputStream inputStream = null;
		prop = new Properties();
		String propFileName = "config.properties";
		inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
		if (inputStream != null) {
			prop.load(inputStream);
		} else {
			throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
		}
	}

	/**
	 * @return
	 * @throws ClassNotFoundException
	 * @throws APIException
	 */
	public IAgileSession getAgileSession() throws ClassNotFoundException, APIException {
		AgileSessionFactory f = AgileSessionFactory.getInstance(prop.getProperty("AGILE_URL"));
		Map<Integer, String> params = new HashMap<Integer, String>();
		params.put(AgileSessionFactory.USERNAME, prop.getProperty("AGILE_USER_NAME"));
		params.put(AgileSessionFactory.PASSWORD, prop.getProperty("AGILE_PASSWORD"));
		logger.info("doing createSession(params)");
		IAgileSession session = f.createSession(params);
		return session;
	}

	/**
	 * @param docNumber
	 * @param fileName
	 * @param fileDesc
	 * @return
	 * @throws ClassNotFoundException
	 * @throws APIException
	 */
	public InputStream getAttachment(String docNumber, String fileName, String fileDesc)
			throws ClassNotFoundException, APIException {
		InputStream stream = null;
		logger.info(docNumber);
		IAgileSession session = getAgileSession();
		StringBuilder attCriteria = new StringBuilder();
		IRow finalAttachment = null;
//default file desc
		if (fileName != null)
			attCriteria.append(prop.getProperty("ATTACHMENT_FILE_NAME_CRITERIA").replace("{filename}", fileName));

		if (fileDesc != null)
			attCriteria.append(prop.getProperty("ATTACHMENT_FILE_DESC_CRITERIA").replace("{fileDesc}", fileDesc));

		if (session != null) {
			IItem docObject = (IItem) session.getObject(IItem.OBJECT_TYPE, docNumber);
			if (docObject != null) {
				logger.info("got Item object");
				ITable attTab = docObject.getTable(ItemConstants.TABLE_ATTACHMENTS).where(attCriteria.toString(), null);
				ITwoWayIterator attItr = attTab.getTableIterator();
				while (attItr.hasNext()) {
					logger.info("in attachment");
					IRow row = (IRow) attItr.next();
					if (finalAttachment == null)
						finalAttachment = row;
					else {
						Date crRowDate = (Date) row.getValue(prop.getProperty("ATTACHMENT_ROW_MODIFIED_DATE"));
						Date finRowDate = (Date) finalAttachment
								.getValue(prop.getProperty("ATTACHMENT_ROW_MODIFIED_DATE"));

						if (crRowDate.after(finRowDate))
							finalAttachment = row;
					}
				}
			}
		}
		if (finalAttachment != null) {
			stream = ((IAttachmentFile) finalAttachment).getFile();
		}
		return stream;
	}
}
