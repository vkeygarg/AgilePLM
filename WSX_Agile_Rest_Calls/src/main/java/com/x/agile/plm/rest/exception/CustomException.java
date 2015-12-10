package com.x.agile.plm.rest.exception;

public class CustomException extends Exception {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	String msg= "";
	public CustomException (String msg){
		this.msg = msg;
	}

}
