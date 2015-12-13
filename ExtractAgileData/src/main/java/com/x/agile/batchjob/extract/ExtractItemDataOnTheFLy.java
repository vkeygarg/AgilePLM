package com.x.agile.batchjob.extract;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import com.agile.api.APIException;
import com.agile.api.AgileSessionFactory;
import com.agile.api.ChangeConstants;
import com.agile.api.IAgileSession;
import com.agile.api.IAttachmentFile;
import com.agile.api.ICell;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.IFolder;
import com.agile.api.IItem;
import com.agile.api.IManufacturer;
import com.agile.api.IManufacturerPart;
import com.agile.api.IQuery;
import com.agile.api.IRow;
import com.agile.api.ITable;
import com.agile.api.ITwoWayIterator;
import com.agile.api.ItemConstants;
import com.agile.api.ManufacturerPartConstants;

public class ExtractItemDataOnTheFLy {
	IAgileSession session = null;
	Map<String, Set<String>> itemMap = null;
	Set<String> mfrList = null;
	Set<String> mpnList = null;
	Map<String, Set<String>> chgMap = null;
	static Properties prop;
	final static Logger logger = Logger.getLogger(ExtractItemDataOnTheFLy.class);
	static String DELIMITER = "^";
	static String timeStamp = "";
	static String getHistoricalBom = "N";
	Map<String, Set<String>> bomItemMap = null;

	public void init() throws IOException, APIException {

		loadPropertyFile();
		this.session = getAgileSession();
		itemMap = new HashMap<String, Set<String>>();
		bomItemMap = new HashMap<String, Set<String>>();
		mfrList = new HashSet<String>();
		mpnList = new HashSet<String>();
		chgMap = new HashMap<String, Set<String>>();
		DELIMITER = prop.getProperty("TXT_FILE_DELIMITER");
		Calendar calobj = Calendar.getInstance();
		DateFormat df = new SimpleDateFormat("yyyyMMddHHmm");
		timeStamp = df.format(calobj.getTime());
		getHistoricalBom = prop.getProperty("EXTRACT_BOM_HISTORY");

	}



	private void loadPropertyFile() throws FileNotFoundException, IOException {
		prop = new Properties();
		FileInputStream file = null;
		String propFileName = "./config.properties";
		try {
			file = new FileInputStream(propFileName);
			prop.load(file);
			logger.info("config File initialized");
		} catch (IOException e) {
			throw e;
		} finally {
			if (file != null)
				file.close();
		}
	}



	public IAgileSession getAgileSession() throws APIException {
		HashMap<Integer, String> params = new HashMap<Integer, String>();
		params.put(AgileSessionFactory.USERNAME, prop.getProperty("AGL_USER"));
		params.put(AgileSessionFactory.PASSWORD, prop.getProperty("AGL_PWD"));
		params.put(AgileSessionFactory.URL, prop.getProperty("AGL_URL"));
		IAgileSession session = AgileSessionFactory.createSessionEx(params);
		logger.info("Connected to Agile!!!");
		return session;
	}

	public void getAgileSearchResults() throws APIException, IOException {
		Set<String> itemSet = null;
		IFolder folder = (IFolder) session.getObject(IFolder.OBJECT_TYPE, "/" + prop.getProperty("AGL_SEARCH_FOLDER"));
		IQuery query = (IQuery) folder.getChild(prop.getProperty("AGL_SEARCH_NAME"));
		ITable results = query.execute();
		logger.info("Search result count:"+results.size());
		ITwoWayIterator itr = results.getTableIterator();
		String itemType = "";
		while (itr.hasNext()) {
			IRow row = (IRow) itr.next();
			IItem itmObj = (IItem)row.getReferent();
			itemType = row.getValue(ItemConstants.ATT_TITLE_BLOCK_ITEM_TYPE).toString();
			if (itemMap.containsKey(itemType)) {
				itemSet = itemMap.get(itemType);
			} else {
				itemSet = new HashSet<String>();
				if(itmObj!=null){
					createDTLSFile(itmObj);
				}
			}
			itemSet.add(row.getValue(ItemConstants.ATT_TITLE_BLOCK_NUMBER).toString());
			itemMap.put(itemType, itemSet);
			if(itmObj!=null){
				try{
					populateItemsDTLSOnTheFly(itmObj);
				}catch(APIException e){
					logger.error("Exception while gettign search result Item: "+itmObj+"\n"+e.getMessage(),e);
				}
			}
		}
		logger.info(prop.getProperty("AGL_SEARCH_NAME")+ "executed successfully.");
	}

	public Connection getDBConnection() throws ClassNotFoundException, SQLException {
		final String dbURL = prop.getProperty("AGL_DB_URL");
		final String dbDriver = prop.getProperty("AGL_DB_DRIVER");
		final String dbLogin = prop.getProperty("AGL_DB_LOGIN");
		final String dbPassword = prop.getProperty("AGL_DB_PWD");
		try {
			Class.forName(dbDriver);
		} catch (ClassNotFoundException e) {
			logger.error(e.getMessage(),e);
			throw e;
		}
		Connection connection = null;
		try {
			connection = DriverManager.getConnection(dbURL, dbLogin, dbPassword);
		} catch (SQLException e) {
			logger.error(e.getMessage(),e);
			throw e;
		}
		return connection;
	}

	private void getBOMItem(IItem parentItem) {
		logger.info("parentItem:"+parentItem);
		if (parentItem != null) {
			try {
				if ("Y".equalsIgnoreCase(getHistoricalBom)) {
					Map revMap = parentItem.getRevisions();
					Iterator revitr = revMap.keySet().iterator();
					while (revitr.hasNext()) {
						parentItem.setRevision(revMap.get(revitr.next()));
						addToBOMMap(parentItem);
					}
				} else {
					addToBOMMap(parentItem);
				}
			} catch (APIException e) {
				logger.error(e.getMessage() + " for Item: " + parentItem);
			}
		}
	}
	
	private void addToBOMMap(IItem parentItem) {
		Set<String> bomSet = null;
		String itemNum = "";
		String itemType = "";
		try {
			ITable bomTab = parentItem.getTable(ItemConstants.TABLE_BOM);
			ITwoWayIterator itr = bomTab.getReferentIterator();
			while (itr.hasNext()) {
				IItem bomItem = (IItem) itr.next();
				itemNum = bomItem.getName();
				itemType = bomItem.getValue(ItemConstants.ATT_TITLE_BLOCK_ITEM_TYPE).toString();
				if (bomItemMap.containsKey(itemType)) {
					bomSet = bomItemMap.get(itemType);
				} else {
					bomSet = new HashSet<String>();
					if(!itemMap.containsKey(itemType)){
						createDTLSFile(bomItem);
					}
				}
				if(!bomSet.contains(itemNum)){
					bomSet.add(itemNum);
					bomItemMap.put(itemType, bomSet);
					if(itemMap.containsKey(itemType)){
						if(!itemMap.get(itemType).contains(bomItem))
							populateItemsDTLSOnTheFly(bomItem);
					}
					getBOMItem(bomItem);
				}
			}
		} catch (APIException e) {
			logger.error(e.getMessage() + " for Item: " + parentItem);
		}
		catch (Exception e) {
			logger.error(e.getMessage() + " for Item: " + parentItem);
		}
	}
	
	public void extractBOMItem() throws APIException, IOException {
		logger.info("Start looking for BOM Items");
		Set<String> itemSet = null;
		Iterator<String> itemClassItr = itemMap.keySet().iterator();
		while (itemClassItr.hasNext()) {
			itemSet = itemMap.get(itemClassItr.next());
			Iterator<String> itemItr = itemSet.iterator();
			while (itemItr.hasNext()) {
				String itemNo = itemItr.next();
				IItem parentItem = (IItem)session.getObject(IItem.OBJECT_TYPE, itemNo);
				getBOMItem(parentItem);
			}
		}
		
		String childListclass = "";
		Set<String> childListSet = null;
		Set<String> masterItemSet = null;
		Iterator<String> childListItr = bomItemMap.keySet().iterator();
		while (childListItr.hasNext()) {
			childListclass = childListItr.next();
			childListSet = bomItemMap.get(childListclass);
			if (itemMap.containsKey(childListclass)) {
				masterItemSet = itemMap.get(childListclass);
				masterItemSet.addAll(childListSet);
			} else {
				masterItemSet = childListSet;
			}
			itemMap.put(childListclass, masterItemSet);
		}
		logger.info("All BOM Items loaded successfully.");
		
		
	}
	
	
	
	
	
	
	public void getAllBOMItems() {
		logger.info("Start looking for BOM Items");
		Map<String, Set<String>> bomMap = new HashMap<String, Set<String>>();
		Set<String> bomSet = null;
		Set<String> itemSet = null;
		String itemNum = "";
		String itemType = "";
		Connection connectionObj = null;
		try {
			connectionObj = getDBConnection();
		} catch (ClassNotFoundException e) {
			logger.error(e.getMessage(),e);
		} catch (SQLException e) {
			logger.error(e.getMessage(),e);
		}
		if (connectionObj != null) {
			String query = prop.getProperty("DB_BOM_QUERY");
			logger.info("BOM DB Query: "+query);
			PreparedStatement ps = null;
			ResultSet rs = null;
			try {
				Iterator<String> itemClassItr = itemMap.keySet().iterator();
				while (itemClassItr.hasNext()) {
					itemSet = itemMap.get(itemClassItr.next());
					Iterator<String> itemItr = itemSet.iterator();
					while (itemItr.hasNext()) {
						String itemNo = itemItr.next();
						try{
						ps = connectionObj.prepareStatement(query);
						
						ps.setString(1, itemNo);
						rs = ps.executeQuery();
						while (rs.next()) {
							itemNum = rs.getString(1);
							itemType = rs.getString(2);
							if (bomMap.containsKey(itemType)) {
								bomSet = bomMap.get(itemType);
							} else {
								bomSet = new HashSet<String>();
							}
							bomSet.add(itemNum);
							bomMap.put(itemType, bomSet);
						}
						rs.close();
						ps.close();
						}catch(SQLException e){
							logger.error(e.getMessage()+itemNo);
						}
						finally{
							if(rs!=null)
								rs.close();
							if(ps!=null)
								ps.close();
								
						}
					}
				}
				connectionObj.close();
			} catch (SQLException e) {
				logger.error(e.getMessage(),e);
			} finally {
				if (rs != null)
					try {
						rs.close();
					} catch (SQLException e) {
						logger.error(e.getMessage(),e);
					}
				if (ps != null)
					try {
						ps.close();
					} catch (SQLException e) {
						logger.error(e.getMessage(),e);
					}
				if (connectionObj != null)
					try {
						connectionObj.close();
					} catch (SQLException e) {
						logger.error(e.getMessage(),e);
					}
			}
		}
		String childListclass = "";
		Set<String> childListSet = null;
		Set<String> masterItemSet = null;
		Iterator<String> childListItr = bomMap.keySet().iterator();
		while (childListItr.hasNext()) {
			childListclass = childListItr.next();
			childListSet = bomMap.get(childListclass);
			if (itemMap.containsKey(childListclass)) {
				masterItemSet = itemMap.get(childListclass);
				masterItemSet.addAll(childListSet);
			} else {
				masterItemSet = childListSet;
			}
			itemMap.put(childListclass, masterItemSet);
		}
		logger.info("All BOM Items loaded successfully.");
	}

	public void getAllMPNMFR() {
		logger.info("Start looking for MPN and MFR.");
		Set<String> itemSet = null;
		Connection connectionObj = null;
		try {
			connectionObj = getDBConnection();
		} catch (ClassNotFoundException e) {
			logger.error(e.getMessage(),e);
		} catch (SQLException e) {
			logger.error(e.getMessage(),e);
		}
		if (connectionObj != null) {
			String query = prop.getProperty("DB_AML_QUERY");
			PreparedStatement ps = null;
			ResultSet rs = null;
			try {
				Iterator<String> itemClassItr = itemMap.keySet().iterator();
				while (itemClassItr.hasNext()) {
					itemSet = itemMap.get(itemClassItr.next());
					Iterator<String> itemItr = itemSet.iterator();
					while (itemItr.hasNext()) {
						ps = connectionObj.prepareStatement(query);
						ps.setString(1, itemItr.next());
						rs = ps.executeQuery();
						while (rs.next()) {
							mpnList.add(rs.getString(1)+DELIMITER+rs.getString(2));
							mfrList.add(rs.getString(2));
						}
						rs.close();
						ps.close();
					}
				}
				connectionObj.close();
			} catch (SQLException e) {
				logger.error(e.getMessage(),e);
			} finally {
				if (rs != null)
					try {
						rs.close();
					} catch (SQLException e) {
						logger.error(e.getMessage(),e);
					}
				if (ps != null)
					try {
						ps.close();
					} catch (SQLException e) {
						logger.error(e.getMessage(),e);
					}
				if (connectionObj != null)
					try {
						connectionObj.close();
					} catch (SQLException e) {
						logger.error(e.getMessage(),e);
					}
			}
		}
		logger.info("MPN MFR lodaed successfully!!!");
	}

	public void getAllChanges() {
		logger.info("Start looking for Changes.");
		Set<String> itemSet = null;
		Set<String> chgSet = null;
		String chgType = "";
		String chgNum = "";
		Connection connectionObj = null;
		try {
			connectionObj = getDBConnection();
		} catch (ClassNotFoundException e) {
			logger.error(e.getMessage(),e);
		} catch (SQLException e) {
			logger.error(e.getMessage(),e);
		}
		if (connectionObj != null) {
			String query = prop.getProperty("DB_CHANGES_QUERY");
			PreparedStatement ps = null;
			ResultSet rs = null;
			try {
				Iterator<String> itemClassItr = itemMap.keySet().iterator();
				while (itemClassItr.hasNext()) {
					itemSet = itemMap.get(itemClassItr.next());
					Iterator<String> itemItr = itemSet.iterator();
					while (itemItr.hasNext()) {
						ps = connectionObj.prepareStatement(query);
						ps.setString(1, itemItr.next());
						rs = ps.executeQuery();
						while (rs.next()) {
							chgNum = rs.getString(1);
							chgType = rs.getString(2);
							if (chgMap.containsKey(chgType)) {
								chgSet = chgMap.get(chgType);
							} else {
								chgSet = new HashSet<String>();
							}
							chgSet.add(chgNum);
							chgMap.put(chgType, chgSet);
						}
						rs.close();
						ps.close();
					}
				}
				connectionObj.close();
			} catch (SQLException e) {
				logger.error(e.getMessage(),e);
			} finally {
				if (rs != null)
					try {
						rs.close();
					} catch (SQLException e) {
						logger.error(e.getMessage(),e);
					}
				if (ps != null)
					try {
						ps.close();
					} catch (SQLException e) {
						logger.error(e.getMessage(),e);
					}
				if (connectionObj != null)
					try {
						connectionObj.close();
					} catch (SQLException e) {
						logger.error(e.getMessage(),e);
					}
			}
		}
		logger.info("All Changes loaded successfully!!!");
	}

	public void createIndexFiles() throws IOException {
		String path = prop.getProperty("BASE_PATH_FOR_INDEX_FILES")+timeStamp+"/";
		logger.info("Start generating Index files @"+path);
		new File(path).mkdirs();
		String finalPath = "";
		Set<String> numSet = null;
		Iterator<String> numItr = null;
		String subClass = "";
		StringBuilder fileData = null;
		try {
			if (!itemMap.isEmpty()) {
				Iterator<String> subClassItr = itemMap.keySet().iterator();
				while (subClassItr.hasNext()) {
					fileData = new StringBuilder();
					subClass = subClassItr.next();
					finalPath = path + "ITEMS_" + subClass + ".idx";
					numSet = itemMap.get(subClass);
					numItr = numSet.iterator();
					while (numItr.hasNext()) {
						fileData.append(numItr.next()).append("\n");
					}
					writeInFile(finalPath, fileData.toString());
					logger.info("Index file for Items created.");
				}
			}
			if (!chgMap.isEmpty()) {
				Iterator<String> subClassItr = chgMap.keySet().iterator();
				while (subClassItr.hasNext()) {
					fileData = new StringBuilder();
					subClass = subClassItr.next();
					finalPath = path + "CHANGES_" + subClass + ".idx";
					numSet = chgMap.get(subClass);
					numItr = numSet.iterator();
					while (numItr.hasNext()) {
						fileData.append(numItr.next()).append("\n");
					}
					writeInFile(finalPath, fileData.toString());
					logger.info("Index file for Changes created.");
				}
			}
			if (!mpnList.isEmpty()) {
				fileData = new StringBuilder();
				finalPath = path + "MPN.idx";
				for (String mpn : mpnList) {
					fileData.append(mpn).append("\n");
				}
				writeInFile(finalPath, fileData.toString());
				logger.info("Index file for MPNs created.");
			}
			if (!mfrList.isEmpty()) {
				fileData = new StringBuilder();
				finalPath = path + "MFR.idx";
				for (String mfr : mfrList) {
					fileData.append(mfr).append("\n");
				}
				writeInFile(finalPath, fileData.toString());
				logger.info("Index file for MFRs created.");
			}
		} catch (IOException e) {
			logger.error(e.getMessage(),e);
			throw e;
		}
	}
	
	public void createDTLSFile(IItem itemObj) throws APIException, IOException{
		String path = prop.getProperty("BASE_PATH_FOR_OUTPUT_FILES")+timeStamp+"/";
		logger.info("Start creating data files for Items.");
		new File(path).mkdirs();
		String dtlsFileName = path+"/ITEMS_"+itemObj.getAgileClass().getName().replace(" ", "")+"_DTLS.txt"; //indexFileName.replace("Index", "Data").replace(".idx", ".txt").replace("ITEMS_", "ITEMS_DTLS_");
		genDTLSFile(itemObj,dtlsFileName);
		
		String revFileName = path+"/ITEMS_REV.txt";
		String pendRevFileName = path+"/ITEMS_PENDING_REV.txt";
		String bomFileName = path+"/ITEMS_BOM.txt";
		String amlFileName = path+"/ITEMS_AML.txt";
		
		createFileForTab("AML",itemObj,amlFileName);
		createFileForTab("REV",itemObj,revFileName);
		createFileForTab("BOM",itemObj,bomFileName);
		createFileForTab("PEND_REV",itemObj,pendRevFileName);
	}
	
	
	public void populateItemsDTLSOnTheFly(IItem itemObj) throws APIException, IOException{
		String path = prop.getProperty("BASE_PATH_FOR_OUTPUT_FILES")+timeStamp+"/";
		String dtlsFileName = path+"/ITEMS_"+itemObj.getAgileClass().getName().replace(" ", "")+"_DTLS.txt"; //indexFileName.replace("Index", "Data").replace(".idx", ".txt").replace("ITEMS_", "ITEMS_DTLS_");
		String revFileName = path+"/ITEMS_REV.txt";
		String pendRevFileName = path+"/ITEMS_PENDING_REV.txt";
		String bomFileName = path+"/ITEMS_BOM.txt";
		String amlFileName = path+"/ITEMS_AML.txt";
		String latestRev = "";
		if (itemObj != null) {
			try{
				latestRev = itemObj.getRevision();
				extractDtlsTab(itemObj, dtlsFileName);
			}
			catch(APIException e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			catch(Exception e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			try{
				itemObj.setRevision(latestRev);
				extractChangesTab(itemObj, revFileName);
			}
			catch(APIException e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			catch(Exception e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			try{
				itemObj.setRevision(latestRev);
				extractPendingChangesTab(itemObj, pendRevFileName);
			}
			catch(APIException e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			catch(Exception e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			try{
				itemObj.setRevision(latestRev);
				extractBOMTab(itemObj, bomFileName);
			}
			catch(APIException e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			catch(Exception e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			try{
				itemObj.setRevision(latestRev);
				extractAMLTab(itemObj, amlFileName);
			}
			catch(APIException e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			catch(Exception e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			try{
				itemObj.setRevision(latestRev);
				//extractAttachmentTab(itemObj, indexFileName.replace(".idx", "/attachment"));
			}
			catch(APIException e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
			catch(Exception e){
				logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
			}
		}
	}

	public void populateItemDetails() throws APIException, IOException {
		String path = prop.getProperty("BASE_PATH_FOR_OUTPUT_FILES")+timeStamp+"/";
		logger.info("Start creating data files for Items.");
		new File(path).mkdirs();
		String idxFilePath = prop.getProperty("BASE_PATH_FOR_INDEX_FILES")+timeStamp+"/";
		List<File> files = getFilesfromDir(idxFilePath, ".idx", "ITEMS_");
		String itemNum = "";
		IItem itemObj = null;
		String indexFileName = "";
		String latestRev = "";
		String dtlsFileName = "";
		String amlHeader = "";
		String bomHeader = "";
		String revHeader = "";
		String pendRevHeader = "";
		String revFileName = path+"/ITEMS_REV.txt";
		String pendRevFileName = path+"/ITEMS_PENDING_REV.txt";
		String bomFileName = path+"/ITEMS_BOM.txt";
		String amlFileName = path+"/ITEMS_AML.txt";
		
		
		for (File idxFile : files) {
			indexFileName = idxFile.getAbsolutePath();
			dtlsFileName = indexFileName.replace("Index", "Data").replace(".idx", ".txt").replace("ITEMS_", "ITEMS_DTLS_");
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(indexFileName));
				String line = br.readLine();
				
				if (line != null && !line.isEmpty()) {
					itemObj = (IItem)session.getObject(IItem.OBJECT_TYPE, line);
					if(itemObj!=null)
						genDTLSFile(itemObj,dtlsFileName);
				}
				while (line != null && !line.isEmpty()) {
					itemNum = line;// .next();
					itemObj = (IItem) session.getObject(IItem.OBJECT_TYPE, itemNum);
					if (itemObj != null) {
						if(amlHeader.isEmpty())
							amlHeader = createFileForTab("AML",itemObj,amlFileName);
						if(revHeader.isEmpty())
							revHeader = createFileForTab("REV",itemObj,revFileName);
						if(bomHeader.isEmpty())
							bomHeader = createFileForTab("BOM",itemObj,bomFileName);
						if(pendRevHeader.isEmpty())
							pendRevHeader = createFileForTab("PEND_REV",itemObj,pendRevFileName);
											
						
						try{
							latestRev = itemObj.getRevision();
							extractDtlsTab(itemObj, dtlsFileName);
						}
						catch(APIException e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						catch(Exception e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						try{
							itemObj.setRevision(latestRev);
							extractChangesTab(itemObj, revFileName);
						}
						catch(APIException e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						catch(Exception e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						try{
							itemObj.setRevision(latestRev);
							extractPendingChangesTab(itemObj, pendRevFileName);
						}
						catch(APIException e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						catch(Exception e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						try{
							itemObj.setRevision(latestRev);
							extractBOMTab(itemObj, bomFileName);
						}
						catch(APIException e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						catch(Exception e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						try{
							itemObj.setRevision(latestRev);
							extractAMLTab(itemObj, amlFileName);
						}
						catch(APIException e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						catch(Exception e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						try{
							itemObj.setRevision(latestRev);
							//extractAttachmentTab(itemObj, indexFileName.replace(".idx", "/attachment"));
						}
						catch(APIException e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
						catch(Exception e){
							logger.error("Exception while retreiving Item Details for Item:"+itemObj,e);
						}
					}
					line = br.readLine();
				}
			} catch (FileNotFoundException e) {
				logger.info("Exception during item:"+itemObj);
				logger.error(e.getMessage(),e);
			} catch (IOException e) {
				logger.info("Exception during item:"+itemObj);
				logger.error(e.getMessage(),e);
			} finally {
				try {
					br.close();
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
	}
	
	
	public void populateChangeDetails() throws APIException, IOException {
		String path = prop.getProperty("BASE_PATH_FOR_OUTPUT_FILES")+timeStamp+"/";
		logger.info("Start creating data files for Changes.");
		new File(path).mkdirs();
		String idxFilePath = prop.getProperty("BASE_PATH_FOR_INDEX_FILES")+timeStamp+"/";
		List<File> files = getFilesfromDir(idxFilePath, ".idx", "CHANGES_");
		String chgNum = "";
		IChange chgObj = null;
		String indexFileName = "";
		String dtlsFileName = "";
		String line = "";
		
		for (File idxFile : files) {
			indexFileName = idxFile.getAbsolutePath();
			dtlsFileName = indexFileName.replace("Index", "Data").replace(".idx", ".txt");
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(indexFileName));
				line = br.readLine();
				
				if (line != null && !line.isEmpty()) {
					chgObj = (IChange)session.getObject(IChange.OBJECT_TYPE, line);
					if(chgObj!=null)
						genDTLSFile(chgObj,dtlsFileName);
				}
				while (line != null && !line.isEmpty()) {
					chgNum = line;// .next();
					chgObj = (IChange)session.getObject(IChange.OBJECT_TYPE, chgNum);
					if (chgObj != null) {
						try {
							extractDtlsTab(chgObj, dtlsFileName);
							// extractAttachmentTab(chgObj,
							// indexFileName.replace(".idx", "/attachment"));
						} catch (APIException e) {
							logger.error("Exception while retreiving Change Details for Change:" + chgObj, e);
						} catch (Exception e) {
							logger.error("Exception while retreiving Change Details for Change:" + chgObj, e);
						}
					}
					
					line = br.readLine();
				}
			} catch (FileNotFoundException e) {
				logger.info("Exception during Change:"+line);
				logger.error(e.getMessage(),e);
			} catch (IOException e) {
				logger.info("Exception during Change:"+line);
				logger.error(e.getMessage(),e);
			} finally {
				try {
					br.close();
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
	}
	
	public void populateMPNDetails() throws APIException, IOException {
		String path = prop.getProperty("BASE_PATH_FOR_OUTPUT_FILES")+timeStamp+"/";
		logger.info("Start creating data files for MPNs.");
		new File(path).mkdirs();
		String idxFilePath = prop.getProperty("BASE_PATH_FOR_INDEX_FILES")+timeStamp+"/";
		List<File> files = getFilesfromDir(idxFilePath, ".idx", "MPN");
		String mpnNum = "";
		String mfrName = "";
		IManufacturerPart mpnObj = null;
		String indexFileName = "";
		String dtlsFileName = "";
		String line = "";
		Map param = null;
		for (File idxFile : files) {
			indexFileName = idxFile.getAbsolutePath();
			dtlsFileName = indexFileName.replace("Index", "Data").replace(".idx", ".txt");
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(indexFileName));
				line = br.readLine();
				if(line!=null && !line.isEmpty()){
					mpnNum = line.split("\\"+DELIMITER)[0];
					mfrName = line.split("\\"+DELIMITER)[1];
					
					param = new HashMap();
					param.put(ManufacturerPartConstants.ATT_GENERAL_INFO_MANUFACTURER_PART_NUMBER, mpnNum);
					param.put(ManufacturerPartConstants.ATT_GENERAL_INFO_MANUFACTURER_NAME, mfrName);
					
					mpnObj = (IManufacturerPart)session.getObject(IManufacturerPart.OBJECT_TYPE, param);
					if(mpnObj!=null)
						genDTLSFile(mpnObj,dtlsFileName);
				}
				while (line != null && !line.isEmpty()) {
					try {
						mpnNum = line.split("\\" + DELIMITER)[0];
						mfrName = line.split("\\" + DELIMITER)[1];
						param = new HashMap();
						param.put(ManufacturerPartConstants.ATT_GENERAL_INFO_MANUFACTURER_PART_NUMBER, mpnNum);
						param.put(ManufacturerPartConstants.ATT_GENERAL_INFO_MANUFACTURER_NAME, mfrName);

						mpnObj = (IManufacturerPart) session.getObject(IManufacturerPart.OBJECT_TYPE, param);
						if (mpnObj != null) {

							extractDtlsTab(mpnObj, dtlsFileName);
							// extractAttachmentTab(chgObj,
							// indexFileName.replace(".idx", "/attachment"));

						}
					} catch (APIException e) {
						logger.error("Exception while retreiving MPN Details for MPN:" + mpnObj, e);
					} catch (Exception e) {
						logger.error("Exception while retreiving MPN Details for MPN:" + mpnObj, e);
					}
					line = br.readLine();
				}
			} catch (FileNotFoundException e) {
				logger.info("Exception during MPN:"+line);
				logger.error(e.getMessage(),e);
			} catch (IOException e) {
				logger.info("Exception during MPN:"+line);
				logger.error(e.getMessage(),e);
			} finally {
				try {
					br.close();
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
	}
	
	public void populateMFRDetails() throws APIException, IOException {
		String path = prop.getProperty("BASE_PATH_FOR_OUTPUT_FILES")+timeStamp+"/";
		logger.info("Start creating data files for MFRs.");
		new File(path).mkdirs();
		String idxFilePath = prop.getProperty("BASE_PATH_FOR_INDEX_FILES")+timeStamp+"/";
		List<File> files = getFilesfromDir(idxFilePath, ".idx", "MFR");
		String mfrNum = "";
		IManufacturer mfrObj = null;
		String indexFileName = "";
		String dtlsFileName = "";
		String line = "";
		
		for (File idxFile : files) {
			indexFileName = idxFile.getAbsolutePath();
			dtlsFileName = indexFileName.replace("Index", "Data").replace(".idx", ".txt");
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(indexFileName));
				line = br.readLine();
				
				if(line !=null && !line .isEmpty()){
					mfrObj = (IManufacturer)session.getObject(IManufacturer.OBJECT_TYPE, line);
					if(mfrObj!=null)
						genDTLSFile(mfrObj,dtlsFileName);
				}
				while (line != null && !line.isEmpty()) {
					mfrNum = line;// .next();
					try {
						mfrObj = (IManufacturer) session.getObject(IManufacturer.OBJECT_TYPE, mfrNum);
						if (mfrObj != null) {

							extractDtlsTab(mfrObj, dtlsFileName);
							// extractAttachmentTab(mfrObj,
							// indexFileName.replace(".idx", "/attachment"));
						}
					} catch (APIException e) {
						logger.error("Exception while retreiving MFR Details for MFR:" + mfrObj, e);
					} catch (Exception e) {
						logger.error("Exception while retreiving MFR Details for MFR:" + mfrObj, e);
					}
					line = br.readLine();
				}
			} catch (FileNotFoundException e) {
				logger.info("Exception during MFR:"+line);
				logger.error(e.getMessage(),e);
			} catch (IOException e) {
				logger.info("Exception during MFR:"+line);
				logger.error(e.getMessage(),e);
			} 
			catch (Exception e) {
				logger.info("Exception during MFR:"+line);
				logger.error(e.getMessage(),e);
			}finally {
				try {
					br.close();
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
	}

	private String createFileForTab(String tabName, IDataObject dataObj, String fileName) throws APIException, IOException {
		StringBuilder headerList = new StringBuilder();
		ITable tabObj = null;
		if("AML".equals(tabName)){
			tabObj = dataObj.getTable(ItemConstants.TABLE_MANUFACTURERS);
			headerList.append("Item Number"+DELIMITER+"Rev"+DELIMITER);
		} else if("BOM".equals(tabName)){
			tabObj = dataObj.getTable(ItemConstants.TABLE_BOM);
			headerList.append("Item Number"+DELIMITER+"Rev"+DELIMITER);
		}else if("REV".equals(tabName)){
			tabObj = dataObj.getTable(ItemConstants.TABLE_CHANGEHISTORY);
			headerList.append("Item Number"+DELIMITER);
		}else if("PEND_REV".equals(tabName)){
			tabObj = dataObj.getTable(ItemConstants.TABLE_PENDINGCHANGES);
			headerList.append("Item Number"+DELIMITER);
		}
		ITwoWayIterator tabItr = tabObj.getTableIterator();
		if(tabItr.hasNext()){
			IRow row = (IRow)tabItr.next();
			ICell [] attrCellArry = row.getCells();
			for(ICell cell : attrCellArry){
				headerList.append(cell.getName()).append(DELIMITER);
			}
			writeInFile(fileName, headerList.toString());
		}else{
			headerList = new StringBuilder();
		}
		return headerList.toString();
	}



	private void genDTLSFile(IDataObject dataObj, String dtlsFileName) throws APIException, IOException {
		StringBuilder attrNameList = new StringBuilder();
		ICell [] attrCellArry = dataObj.getCells();
		for(ICell cell : attrCellArry){
			attrNameList.append(cell.getName()).append(DELIMITER);
		}
		writeInFile(dtlsFileName, attrNameList.toString());
	}

	

	

	private void extractAMLTab(IItem dataObj, String fileName) throws APIException, IOException {
		String getHistoricalAML = prop.getProperty("EXTRACT_AML_HISTORY");
		if ("Y".equalsIgnoreCase(getHistoricalAML)) {
			Map revMap = dataObj.getRevisions();
			Iterator revitr = revMap.keySet().iterator();
			while (revitr.hasNext()) {
				dataObj.setRevision(revMap.get(revitr.next()));
				String attrValList = extractTableDTLS(dataObj, dataObj.getTable(ItemConstants.TABLE_MANUFACTURERS));
				if (!attrValList.isEmpty())
					writeInFile(fileName, attrValList.toString());
			}
		} else {
			String attrValList = extractTableDTLS(dataObj, dataObj.getTable(ItemConstants.TABLE_MANUFACTURERS));
			if (!attrValList.isEmpty())
				writeInFile(fileName, attrValList.toString());
		}
		
		
		
	}

	private String extractTableDTLS(IItem dataObj, ITable itemTable) throws APIException, IOException {
		StringBuilder attrValList = new StringBuilder();
		String attrVal = "";
		Object valObj = null;
		ITwoWayIterator itemTabItr = itemTable.getTableIterator();
		IRow amlRow = null;
		while (itemTabItr.hasNext()) {
			amlRow = (IRow) itemTabItr.next();
			attrValList.append(dataObj.getName()).append(DELIMITER);
			if(itemTable.getId() == ItemConstants.TABLE_BOM || itemTable.getId() == ItemConstants.TABLE_MANUFACTURERS)
				attrValList.append(dataObj.getRevision()).append(DELIMITER);
			ICell[] amlCells = amlRow.getCells();
			for (ICell amlCell : amlCells) {
				valObj = amlCell.getValue();
				attrVal = (valObj == null ? "" : valObj.toString()).replace("\n", "").replace("\r", "").replace(DELIMITER, "");
				attrValList.append(attrVal).append(DELIMITER);
			}
			attrValList.append("\n");
		}
		return attrValList.toString();
	}

	private void extractBOMTab(IItem dataObj, String fileName) throws APIException, IOException {
		String attrValList = "";
		String getHistoricalBom = prop.getProperty("EXTRACT_BOM_HISTORY");;
		if ("Y".equalsIgnoreCase(getHistoricalBom)) {
			Map revMap = dataObj.getRevisions();
			Iterator revitr = revMap.keySet().iterator();
			while (revitr.hasNext()) {
				dataObj.setRevision(revMap.get(revitr.next()));
				attrValList = extractTableDTLS(dataObj, dataObj.getTable(ItemConstants.TABLE_BOM));
				if (!attrValList.isEmpty())
					writeInFile(fileName, attrValList.toString());
			}
		} else {
			attrValList = extractTableDTLS(dataObj, dataObj.getTable(ItemConstants.TABLE_BOM));
			if (!attrValList.isEmpty())
				writeInFile(fileName, attrValList.toString());
		}
	}

	private void extractChangesTab(IItem dataObj, String fileName) throws APIException, IOException {
		String attrValList = extractTableDTLS(dataObj, dataObj.getTable(ItemConstants.TABLE_CHANGEHISTORY));
		if (!attrValList.isEmpty())
			writeInFile(fileName, attrValList.toString());
		/*attrValList = extractTableDTLS(dataObj, dataObj.getTable(ItemConstants.TABLE_PENDINGCHANGES));
		if (!attrValList.isEmpty())
			writeInFile(fileName, attrValList.toString());*/
	}
	
	
	private void extractPendingChangesTab(IItem dataObj, String fileName) throws APIException, IOException {
		String attrValList = extractTableDTLS(dataObj, dataObj.getTable(ItemConstants.TABLE_PENDINGCHANGES));
		if (!attrValList.isEmpty())
			writeInFile(fileName, attrValList.toString());
	}

	private static void extractDtlsTab(IDataObject dataObj, String fileName) throws IOException, APIException {
		Object valObj = null;
		String attrVal = null;
		StringBuilder attrValList = new StringBuilder();
		ICell [] attrCellArry = dataObj.getCells();
		for(ICell attrCell : attrCellArry) {
			valObj = attrCell.getValue();
			attrVal = (valObj == null ? "" : valObj.toString()).replace("\n", "").replace("\r", "").replace(DELIMITER, "");
			attrValList.append(attrVal).append(DELIMITER);
		}
		writeInFile(fileName, attrValList.toString());
	}

	private static void writeInFile(String fileName, String fileData) throws IOException {
		PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
		out.println(fileData);
		out.close();
	}

	public List<File> getFilesfromDir(String path, final String extension, final String prefix) throws IOException {
		File dir = new File(path);
		/*String[] extensions = new String[] { filter };
		return (List<File>) FileUtils.listFiles(dir, extensions, true);*/
		File [] files = dir.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(extension) && name.toUpperCase().startsWith(prefix);
			}
		});
		return Arrays.asList(files);
	}



	public void populateItemsAttachments() throws APIException, IOException, Exception {
		String path = prop.getProperty("BASE_PATH_FOR_OUTPUT_FILES")+timeStamp+"/Attachments/";
		logger.info("Start exporting Attachments");
		//new File(path).mkdirs();
		String idxFilePath = prop.getProperty("BASE_PATH_FOR_INDEX_FILES")+timeStamp+"/";
		List<File> files = getFilesfromDir(idxFilePath, ".idx", "ITEMS_");
		String itemNum = "";
		IItem itemObj = null;
		String indexFileName = "";

		for (File idxFile : files) {
			indexFileName = idxFile.getAbsolutePath();
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(indexFileName));
				String line = br.readLine();
				
				
				while (line != null && !line.isEmpty()) {
					itemNum = line;// .next();
					try {
						itemObj = (IItem) session.getObject(IItem.OBJECT_TYPE, itemNum);
						if (itemObj != null) {
							extractItemAttachmentTab(itemObj, path);
						}
					} catch (APIException e) {
						logger.info("Exception during attachment import for dataobject:" + itemObj);
						logger.error(e.getMessage(), e);
					} catch (Exception e) {
						logger.info("Exception during attachment import for dataobject:" + itemObj);
						logger.error(e.getMessage(), e);
					}
					line = br.readLine();
				}
			} catch (FileNotFoundException e) {
				logger.info("Exception during item:"+itemObj);
				logger.error(e.getMessage(),e);
				throw e;
			} catch (IOException e) {
				logger.info("Exception during item:"+itemObj);
				logger.error(e.getMessage(),e);
				throw e;
			}
			catch (Exception e) {
				logger.info("Exception during item:"+itemObj);
				logger.error(e.getMessage(),e);
				throw e;
			}
			finally {
				try {
					br.close();
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
	}
	
	private void extractItemAttachmentTab(IItem itemObj, String attPath) throws APIException, IOException {
		itemObj.setRevision(itemObj.getRevision());
		String oldAttachments = prop.getProperty("EXTRACT_HISTORICAL_ATTACHMENTS");
		attPath = attPath+"Items/";
		ITable attachmnetTab = null;
		if ("Y".equalsIgnoreCase(oldAttachments)) {
			Map revMap = itemObj.getRevisions();
			Iterator revitr = revMap.keySet().iterator();
			while (revitr.hasNext()) {
				itemObj.setRevision(revMap.get(revitr.next()));
				attachmnetTab = itemObj.getAttachments();
				copyAttachments(attachmnetTab, attPath+itemObj.getName() + "/" + itemObj.getRevision());
			}
		} else {
			attachmnetTab = itemObj.getAttachments();
			copyAttachments(attachmnetTab, attPath+itemObj.getName() + "/" + itemObj.getRevision());
		}
	}
	
	private static void copyAttachments(ITable attachmentTable, String attachmentPath) 
			throws APIException, IOException {
		InputStream inStream = null;
		OutputStream outputStream = null;
		try {
			if (attachmentTable.size() > 0) {
				File theFile = new File(attachmentPath);
				theFile.mkdirs();
				String path = theFile.getAbsolutePath();
				Iterator ffItr = attachmentTable.getTableIterator();
				IRow attachRow = null;
				while (ffItr.hasNext()) {
					attachRow = (IRow) ffItr.next();
					inStream = ((IAttachmentFile) attachRow).getFile();
					String fileName = attachRow.getValue(ChangeConstants.ATT_ATTACHMENTS_FILE_NAME).toString();
					File file = new File(path + "/" + fileName);
					outputStream = new FileOutputStream(file);
					IOUtils.copy(inStream, outputStream);
				}
			}
		} catch (APIException e) {
			throw e;
		} catch (IOException e) {
			throw e;
		} finally {
			if (inStream != null)
				inStream.close();
			if (outputStream != null)
				outputStream.close();
		}
	}
	
	public void populateChangesAttachments() throws APIException, IOException, Exception {
		String path = prop.getProperty("BASE_PATH_FOR_OUTPUT_FILES") + timeStamp + "/Attachments/";
		logger.info("Start exporting Attachments");
		// new File(path).mkdirs();
		String idxFilePath = prop.getProperty("BASE_PATH_FOR_INDEX_FILES") + timeStamp + "/";
		List<File> files = getFilesfromDir(idxFilePath, ".idx", "CHANGES_");
		String chgNum = "";
		IChange chgObj = null;
		String indexFileName = "";

		for (File idxFile : files) {
			indexFileName = idxFile.getAbsolutePath();
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(indexFileName));
				String line = br.readLine();

				while (line != null && !line.isEmpty()) {
					chgNum = line;
					try {
						chgObj = (IChange) session.getObject(IChange.OBJECT_TYPE, chgNum);
						if (chgObj != null) {
							extractChangeAttachmentTab(chgObj, path);
						}
					} catch (APIException e) {
						logger.info("Exception during attachment import for dataobject:" + chgObj);
						logger.error(e.getMessage(), e);
						throw e;
					} catch (IOException e) {
						logger.info("Exception during attachment import for dataobject:" + chgObj);
						logger.error(e.getMessage(), e);
						throw e;
					} catch (Exception e) {
						logger.info("Exception during attachment import for dataobject:" + chgObj);
						logger.error(e.getMessage(), e);
						throw e;
					}
					line = br.readLine();
				}
			} catch (FileNotFoundException e) {
				logger.info("Exception during item:" + chgObj);
				logger.error(e.getMessage(), e);
			} catch (IOException e) {
				logger.info("Exception during item:" + chgObj);
				logger.error(e.getMessage(), e);
			} finally {
				try {
					br.close();
				} catch (IOException e) {
					logger.error(e.getMessage(), e);
				}
			}
		}
	}
	
	private void extractChangeAttachmentTab(IChange chgObj, String attPath) throws APIException, IOException {
		attPath = attPath+"Changes/";
		ITable attachmnetTab = null;
		attachmnetTab = chgObj.getAttachments();
		copyAttachments(attachmnetTab, attPath+chgObj.getName());
	}
	
	private void extractMPNAttachmentTab(IManufacturerPart mpnObj, String attPath) throws APIException, IOException {
		attPath = attPath+"MPN/";
		ITable attachmnetTab = null;
		attachmnetTab = mpnObj.getAttachments();
		copyAttachments(attachmnetTab, attPath+mpnObj.getName());
	}
	
	private void extractMFRAttachmentTab(IManufacturer mfrObj, String attPath) throws APIException, IOException {
		attPath = attPath+"MFR/";
		ITable attachmnetTab = null;
		attachmnetTab = mfrObj.getAttachments();
		copyAttachments(attachmnetTab, attPath+mfrObj.getName());
	}
	
	public void populateMPNAttachments() throws APIException, IOException {
		String path = prop.getProperty("BASE_PATH_FOR_OUTPUT_FILES") + timeStamp + "/Attachments/";
		logger.info("Start exporting MPN Attachments");
		String idxFilePath = prop.getProperty("BASE_PATH_FOR_INDEX_FILES")+timeStamp+"/";
		List<File> files = getFilesfromDir(idxFilePath, ".idx", "MPN");
		String mpnNum = "";
		String mfrName = "";
		IManufacturerPart mpnObj = null;
		String indexFileName = "";
		String line = "";
		Map param = null;
		for (File idxFile : files) {
			indexFileName = idxFile.getAbsolutePath();
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(indexFileName));
				line = br.readLine();
				while (line != null && !line.isEmpty()) {
					try {
						mpnNum = line.split("\\" + DELIMITER)[0];
						mfrName = line.split("\\" + DELIMITER)[1];
						param = new HashMap();
						param.put(ManufacturerPartConstants.ATT_GENERAL_INFO_MANUFACTURER_PART_NUMBER, mpnNum);
						param.put(ManufacturerPartConstants.ATT_GENERAL_INFO_MANUFACTURER_NAME, mfrName);

						mpnObj = (IManufacturerPart) session.getObject(IManufacturerPart.OBJECT_TYPE, param);
						if (mpnObj != null) {
							extractMPNAttachmentTab(mpnObj, path);
						}
					} catch (APIException e) {
						logger.info("Exception during attachment import for dataobject:" + mpnObj);
						logger.error(e.getMessage(), e);
					} catch (IOException e) {
						logger.info("Exception during attachment import for dataobject:" + mpnObj);
						logger.error(e.getMessage(), e);
					}
					line = br.readLine();
				}
			} catch (FileNotFoundException e) {
				logger.info("Exception during MPN:"+line);
				logger.error(e.getMessage(),e);
				throw e;
			} catch (IOException e) {
				logger.info("Exception during MPN:"+line);
				logger.error(e.getMessage(),e);
				throw e;
			} finally {
				try {
					br.close();
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
	}
	
	public void populateMFRAttachments() throws APIException, IOException {
		String path = prop.getProperty("BASE_PATH_FOR_OUTPUT_FILES") + timeStamp + "/Attachments/";
		logger.info("Start exporting MFR Attachments");
		String idxFilePath = prop.getProperty("BASE_PATH_FOR_INDEX_FILES")+timeStamp+"/";
		List<File> files = getFilesfromDir(idxFilePath, ".idx", "MFR");
		String mfrNum = "";
		IManufacturer mfrObj = null;
		String indexFileName = "";
		String line = "";
		
		for (File idxFile : files) {
			indexFileName = idxFile.getAbsolutePath();
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(indexFileName));
				line = br.readLine();
				while (line != null && !line.isEmpty()) {
					mfrNum = line;// .next();
					mfrObj = (IManufacturer)session.getObject(IManufacturer.OBJECT_TYPE, mfrNum);
					if (mfrObj != null) {
						try {
							extractMFRAttachmentTab(mfrObj, path);
						} catch (APIException e) {
							logger.info("Exception during attachment import for dataobject:" + mfrObj);
							logger.error(e.getMessage(), e);
						} catch (Exception e) {
							logger.info("Exception during attachment import for dataobject:" + mfrObj);
							logger.error(e.getMessage(), e);
						}
					}
					line = br.readLine();
				}
			} catch (FileNotFoundException e) {
				logger.info("Exception during MFR:"+line);
				logger.error(e.getMessage(),e);
			} catch (IOException e) {
				logger.info("Exception during MFR:"+line);
				logger.error(e.getMessage(),e);
			} finally {
				try {
					br.close();
				} catch (IOException e) {
					logger.error(e.getMessage(),e);
				}
			}
		}
	}



	public void populateAttachments() throws APIException, IOException, Exception {
		populateItemsAttachments();
		populateChangesAttachments();
		populateMFRAttachments();
		populateMPNAttachments();
		
	}
}
