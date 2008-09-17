/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
/*
 * Copyright (c) 1998. Sun Microsystems, Inc. All Rights Reserved.
 *
 * "@(#)addAnewEntry.java	1.2	98/04/22 SMI"
 */

import java.util.Properties;
import java.lang.*;
import java.util.Hashtable;
import	 javax.naming.Context;
import	 javax.naming.NamingException;
import	 javax.naming.directory.Attribute;
import	 javax.naming.directory.Attributes;
import	 javax.naming.ldap.LdapContext;
import	 javax.naming.ldap.InitialLdapContext;
import  javax.naming.CompositeName;
import	 javax.naming.directory.BasicAttribute;
import	 javax.naming.directory.BasicAttributes;
import  javax.naming.*;
import  javax.naming.directory.ModificationItem;
import  java.util.HashSet;
import  java.util.StringTokenizer;
import java.util.Iterator;

/**
  *  add a new   entry
  *  input : 
  *    -D : principal
  *    -w : credential
  *    -p : ldapport
  *    -h : ldaphost
  *    -d : dn to add
  *    -v : attribut name:attribut value
  *        : this option is multi valued
  */

public class addAnEntry {

    public static void main(String[] args) {

     String hostname=null;
     String ldapPort=null;
     String principal=null;
     String credential=null;
     String dnToAdd=null;
     String attributeToAdd=null;
     String errorCode=null;
     String errorMessage=null;
     
     int ind1;
     String attributeName;
     String attributeValue;
     
     Attributes attributes = new BasicAttributes(); 
     HashSet attributeSet = new HashSet(); 

     for (int k=0; k< args.length; k++) {
    	 String opt1 = args[k]; 
    	 String val1 = args[k+1];
    	 
    	 if (opt1.equals("-h")) {
    		 hostname = val1;
    	 }
    	 if (opt1.equals("-p")) {
    		 ldapPort = val1;
    	 }
    	 if (opt1.equals("-D")) {
    		 principal = val1;
    	 }
    	 if (opt1.equals("-w")) {
    		 credential = val1;
    	 }
    	 if (opt1.equals("-d")) {
    		 dnToAdd = val1;
    	 }
    	 if (opt1.equals("-v")) {
    		attributeToAdd = val1;
    		 
   		 	ind1= val1.indexOf (":");

   		 	attributeName=val1.substring (0,ind1);
   		 	attributeValue=val1.substring (ind1+1);

   		 	BasicAttribute attrToComplete = null;
   		 
   		 	Iterator it = attributeSet.iterator();
   		 	while (attrToComplete == null && it.hasNext()) {
   		 		BasicAttribute attr = (BasicAttribute) it.next();
   		 		if ((attr.getID()).equalsIgnoreCase(attributeName)) {
   		 			attrToComplete = attr;
   		 		}
   		 	}
   		 	
   		 	if (attrToComplete == null) {
   		 		attrToComplete = new BasicAttribute(attributeName);
   		 		attributeSet.add(attrToComplete);
   		 	}
   		 	attrToComplete.add(attributeValue);
    	 }
    	 k++;
     } 
      
     
     Iterator it2 = attributeSet.iterator();
     while (it2.hasNext()) {
		 BasicAttribute attr = (BasicAttribute)it2.next();
		 attributes.put(attr);
	 }
     
    String provider = "ldap://"  + hostname + ":" + ldapPort  + "/";
 
    Hashtable envLdap  = new Hashtable();
    
	envLdap.put("java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory");
	envLdap.put(Context.SECURITY_AUTHENTICATION, "simple");
	envLdap.put(Context.SECURITY_PRINCIPAL,  principal);
	envLdap.put(Context.SECURITY_CREDENTIALS, credential);
	envLdap.put(Context.PROVIDER_URL, provider);

	LdapContext ctx;
	
	try {
		
	    CompositeName entryDN = new CompositeName (dnToAdd);
	    System.out.println("Add a new  entry " + entryDN); 
		
	    // connect to server
	    ctx = new InitialLdapContext(envLdap, null);
            ctx.createSubcontext(entryDN, attributes);
            ctx.close();
        
	} catch (CommunicationException e1) {
		errorMessage = e1.getMessage();

	} catch (NamingException e2) {
		errorMessage = e2.getMessage();

    } catch (Exception e3) {
    	errorMessage= e3.getMessage();
	}
    // No error, the modify is success 
    if ( errorMessage == null ) {
    	errorCode="0";
    } 
    else {
    	System.out.println (errorMessage);
    	int ind=errorMessage.indexOf("-");
    	if ( ind > 0 ) {
    	 errorCode=errorMessage.substring(18, ind-1);
    	}
    	else errorCode="0";
    }
 
    int RC = Integer.parseInt(errorCode);
    System.exit(RC);
    }    
   
}