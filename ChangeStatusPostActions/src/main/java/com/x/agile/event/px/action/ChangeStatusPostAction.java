package com.x.agile.event.px.action;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.agile.api.APIException;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.INode;
import com.agile.px.ActionResult;
import com.agile.px.EventActionResult;
import com.agile.px.IEventAction;
import com.agile.px.IEventInfo;
import com.agile.px.IObjectEventInfo;
import com.x.agile.px.bo.WorkFlowPxBO;

/**
 * @author 
 * Description: CHnage status on work flow, Post Event Action Class 
 *
 */
public class ChangeStatusPostAction implements IEventAction {

	public EventActionResult doAction(IAgileSession aglSession, INode node, IEventInfo eventInfo) {
		ActionResult actRes = null;
		IObjectEventInfo eventInfoObj = (IObjectEventInfo) eventInfo;
		IChange chgObj = null;
		Logger logger = Logger.getLogger(EventActionResult.class);
		logger.info("ChangeStatusPostAction Starts for Change ::"+chgObj);
		try {
			chgObj = (IChange) eventInfoObj.getDataObject();
			WorkFlowPxBO wrkFlowObj = new WorkFlowPxBO();
			wrkFlowObj.init();
			wrkFlowObj.updateItemAttr(chgObj);
			actRes = new ActionResult(ActionResult.STRING, "Completed Successfulyy!");
			logger.info("ChangeStatusPostAction Completed Successfully");
		} catch (APIException e) {
			logger.error(e.getMessage(), e);
			actRes = new ActionResult(ActionResult.EXCEPTION, e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			actRes = new ActionResult(ActionResult.EXCEPTION, e);
		}
		return new EventActionResult(eventInfoObj, actRes);
	}

}
