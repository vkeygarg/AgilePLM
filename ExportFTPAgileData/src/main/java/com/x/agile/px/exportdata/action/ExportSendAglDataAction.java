package com.x.agile.px.exportdata.action;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.agile.api.APIException;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.INode;
import com.agile.px.ActionResult;
import com.agile.px.ICustomAction;
import com.x.agile.px.exportdata.bo.ProcessBO;
import com.x.agile.px.exportdata.exception.CustomException;
import com.x.agile.px.exportdata.util.Utils;

/**
 * @author 
 * Description: 
 *
 */
public class ExportSendAglDataAction implements ICustomAction {

	public ActionResult doAction(IAgileSession aglSession, INode node, IDataObject dataObj) {
		ActionResult actRes = null;
		//IObjectEventInfo eventInfoObj = (IObjectEventInfo) eventInfo;
		IChange chgObj = null;
		
		try {
			PropertyConfigurator.configure(Utils.loadPropertyFile("D:/Agile/Agile934/integration/sdk/extensions/log4j.properties"));
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		Logger logger = Logger.getLogger(ExportSendAglDataAction.class);
		logger.info("ExportFTPAgileData Starts for Change ::"+chgObj);
		try {
			
			chgObj = (IChange) dataObj;
			ProcessBO boObj = new ProcessBO();
			boObj.init();
			boObj.processRequest(chgObj);
			actRes = new ActionResult(ActionResult.STRING, "Completed Successfulyy!");
			logger.info("ExportFTPAgileData Completed Successfully");
		} catch (APIException e) {
			logger.error(e.getMessage(), e);
			actRes = new ActionResult(ActionResult.EXCEPTION, e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			actRes = new ActionResult(ActionResult.EXCEPTION, e);
		} catch (CustomException e) {
			logger.error(e.getMessage(), e);
			actRes = new ActionResult(ActionResult.EXCEPTION, e);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			actRes = new ActionResult(ActionResult.EXCEPTION, e);
		}
		return actRes;
	}

	/*public static void main (String [] args){
		//List<String> rowdata = Arrays.asList("Vikram","Garg");
		Map <String,List<String>> dataMap = new HashMap<String,List<String>>();
		dataMap.put("1", Arrays.asList("Vikram","Garg"));
		//dataMap.put("2", Arrays.asList("Anchal","Jain"));
		//dataMap.put("3", Arrays.asList("Aashni","G"));
		//dataMap.put("4", Arrays.asList("Shivi","Mam"));
		try {
			File file = Utils.getCSVFile("FamilyName.txt", dataMap, Arrays.asList("First Name","Last Name"), ",", ";", null);
			//Utils.sendEmail(file, new Properties());
			Utils.ftpFile(file, "ftp.bacsexperts.com", "fileload", "2uploadfiles", logger);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}*/
	
	
}
