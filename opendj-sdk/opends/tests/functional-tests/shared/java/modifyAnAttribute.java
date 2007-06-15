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
 * 
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



/**
  *  modify an entry with an attribute 
  *  the operation can be a replace, delete or a add new attribute
  */

public class modifyAnAttribute {

    public static void main(String[] args) {

     String hostname=null;
     String ldapPort=null;
     String principal=null;
     String credential=null;
     String attributeToModify=null;
     String dnToModify=null;
     String newAttributeValue=null;
     String changetype=null;
     
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
    		 dnToModify = val1;
    	 }
    	 if (opt1.equals("-v")) {
    		 newAttributeValue = val1;
    	 }
    	 if (opt1.equals("-a")) {
    		 attributeToModify = val1;
    	 }
    	 if (opt1.equals("-t")) {
    		 changetype = val1;
    	 }
    	 k++;
     }
 
    newAttributeValue=newAttributeValue.replaceAll("QUOT","\\\"");
   
    String provider = "ldap://"  + hostname + ":" + ldapPort  + "/";

    Hashtable envLdap  = new Hashtable();
    
	envLdap.put("java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory");
	envLdap.put(Context.SECURITY_AUTHENTICATION, "simple");
	envLdap.put(Context.SECURITY_PRINCIPAL,  principal);
	envLdap.put(Context.SECURITY_CREDENTIALS, credential);
	envLdap.put(Context.PROVIDER_URL, provider);

	LdapContext ctx;
	
	try {
		
		CompositeName entryDN = new CompositeName (dnToModify);
		System.out.println(changetype + " attribute " + attributeToModify + " for entry " + dnToModify);
		
		// connect to server
		ctx = new InitialLdapContext(envLdap, null);
		
		Attributes attrs = new BasicAttributes(true);
		Attribute attr = new BasicAttribute(attributeToModify);
		attr.add(newAttributeValue);
		attrs.put (attr);

		// replace attribute
  		 if (changetype.equals("replace")) {
  			ctx.modifyAttributes(entryDN, LdapContext.REPLACE_ATTRIBUTE , attrs);
		 }
		 else if (changetype.equals("add")) { 
			 // add attribute	
			 ctx.modifyAttributes(entryDN, LdapContext.ADD_ATTRIBUTE , attrs);
		 }
		 else if (changetype.equals("delete")) { 
			 // add attribute
			 ctx.modifyAttributes(entryDN, LdapContext.REMOVE_ATTRIBUTE , attrs);
		 }
		
        ctx.close();

	} catch (CommunicationException e1) {
		String error = e1.getMessage();
        System.out.println(" Catch exception : " + e1.getMessage());
        System.exit(1);
	} catch (NamingException e2) {
        System.out.println(" Catch exception : " + e2.getMessage());
        System.exit(1);
    } catch (Exception e3) {
            System.out.println(" Catch exception : " + e3.getMessage());
            System.exit(1);
	}
}
}
