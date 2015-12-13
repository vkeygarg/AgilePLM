package com.x.agile.batchjob.extract.action;

import java.io.File;
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
			boolean idxExists = false;
			String idxDirecrory  = "";
			if(args != null && args.length > 1)
			{
				idxDirecrory = args[1];
				idxExists  =isIDXfilesExists(idxDirecrory);
				
			}
			
			if(idxExists){
				logger.info("IDX files already exist, will only extract Data");
				ExtractItemData.timeStamp  =idxDirecrory;
			}else {
				logger.info("Creating IDX files");
				srcObj.getAgileSearchResults();
				srcObj.getAllBOMItems();
				srcObj.getAllMPNMFR();
				srcObj.getAllChanges();
				srcObj.createIndexFiles();
			}
			
			
			if(args !=null && "Attachments".equalsIgnoreCase(args[0])){
				logger.info("Pull Attachments only...");
				srcObj.populateAttachments();
			} 
			else if(args !=null && "Items".equalsIgnoreCase(args[0])){
				logger.info("Pull Items only...");
				srcObj.populateItemDetails();
			} 
			else if(args !=null && "Changes".equalsIgnoreCase(args[0])){
				logger.info("Pull Changes only...");
				srcObj.populateChangeDetails();
			} 
			else if(args !=null && "MFR".equalsIgnoreCase(args[0])){
				logger.info("Pull MFR only...");
				srcObj.populateMFRDetails();
			} 
			else if(args !=null && "MPN".equalsIgnoreCase(args[0])){
				logger.info("Pull MPN only...");
				srcObj.populateMPNDetails();
			} 
			else {
				logger.info("Pull All Agile objects except attachments only...");
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
	
	public static boolean isIDXfilesExists(String idxDir){
			File f = new File(ExtractItemData.prop.getProperty("BASE_PATH_FOR_INDEX_FILES")+idxDir);
			return (f.exists() && f.isDirectory());
	}

}