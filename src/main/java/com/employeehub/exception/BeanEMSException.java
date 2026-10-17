package com.employeehub.exception;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class BeanEMSException {
	
	 @ExceptionHandler(InvoiceException.class)
	    public ResponseEntity<String> handleInvoiceException(InvoiceException ex) {
	        // Log the exception or perform additional handling if needed
	        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
	    }
	 
	 @ExceptionHandler(EmployeeServiceException.class)
	    public ResponseEntity<String> handleEmployeeServiceException(EmployeeServiceException ex) {
	        // Log the exception or perform additional handling if needed
	        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
	    }

	 @ExceptionHandler(DataNotSavedException.class)
	    public ResponseEntity<String> handleDataNotSavedException(DataNotSavedException ex) {
	        return new ResponseEntity<>(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
	    }

}
