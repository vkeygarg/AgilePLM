package com.x.agile.batchjob.extract.action;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.log4j.Logger;

import com.agile.api.APIException;
import com.x.agile.batchjob.extract.ExtractItemData;

public class ExtractDataAction {
	final static Logger logger = Logger.getLogger(ExtractDataAction.class);

	public static void main(String[] args) {

		Calendar calobj = Calendar.getInstance();
		DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
		
		logger.info("Extract Started @ -- " + df.format(calobj.getTime()));
        //logger.info("Passes argument is: "+arg[0]);
		
		try {
			ExtractItemData srcObj = new ExtractItemData();
			srcObj.init();
			if(args !=null && "Attachments".equalsIgnoreCase(args[0])){
				srcObj.getAgileSearchResults();
				srcObj.getAllBOMItems();
				srcObj.getAllMPNMFR();
				srcObj.getAllChanges();
				srcObj.createIndexFiles();
				srcObj.populateAttachments();
			} else {
				
				srcObj.getAgileSearchResults();
				//srcObj.getAllBOMItems();
				srcObj.extractBOMItem();
				srcObj.getAllMPNMFR();
				srcObj.getAllChanges();
				srcObj.createIndexFiles();
				srcObj.populateItemDetails();
				srcObj.populateChangeDetails();
				srcObj.populateMFRDetails();
				srcObj.populateMPNDetails();
			}
		}catch (APIException e) {
			logger.error(e.getMessage(), e);
		} 
		catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		logger.info("Extract Completed @ -- " + df.format(Calendar.getInstance().getTime()));
	}

}