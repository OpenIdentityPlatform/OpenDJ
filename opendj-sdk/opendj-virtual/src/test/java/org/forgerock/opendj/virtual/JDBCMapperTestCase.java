/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.virtual;

import java.util.ArrayList;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;


public class JDBCMapperTestCase {
	 public static void main(String args[])throws Exception {		       
		    final JDBCConnectionFactory JDBC = new JDBCConnectionFactory("localhost", 3306, "opendj_db");
		    final Connection jdbcconnection = JDBC.getConnection(); 
		    jdbcconnection.bind("root", "".toCharArray());
		    
		    final LDAPConnectionFactory LDAP = new LDAPConnectionFactory("localhost", 389);
	        final Connection ldapconnection = LDAP.getConnection();
	        ldapconnection.bind("cn=Directory Manager", "opendj".toCharArray());
		    			    
		    final JDBCMapper JDBCM = new JDBCMapper(jdbcconnection, ldapconnection);
		    JDBCM.fillMaps();
		    final ArrayList<String> tablesList = JDBCM.getTables();
		    final ArrayList<String> organizationalUnitsList = JDBCM.getOrganizationalUnits();
		    
		    for(int i = 0; i < tablesList.size(); i++){
		    	final String tableName = tablesList.get(i);
		    	System.out.println("Table: " + tableName);
		    	final ArrayList<String> columnsList = JDBCM.getTableColumns(tableName);	
		    	
		    	if(columnsList != null){
		    		
		    		for(int j = 0; j < columnsList.size(); j++){
				    	System.out.println(columnsList.get(j));
				    }
			    	System.out.println();
		    	}
		    }
		    
		    for(int i = 0; i < organizationalUnitsList.size(); i++){
		    	final String organizationalUnitName = organizationalUnitsList.get(i);
		    	System.out.println("OU: " + organizationalUnitName);
		    	final ArrayList<String> attributesList = JDBCM.getOrganizationalUnitAttributes(organizationalUnitName);	
		    	
		    	if(attributesList != null){
		    		for(int j = 0; j < attributesList.size(); j++){
			    		System.out.println(attributesList.get(j));	    	
				    }
			    	System.out.println();
		    	}
		    }
	 }
}



