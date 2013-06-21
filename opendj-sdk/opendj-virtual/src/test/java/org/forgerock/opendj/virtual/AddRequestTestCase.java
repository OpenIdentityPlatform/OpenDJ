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

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.Requests;

public class AddRequestTestCase {	
	public static void main(String args[])throws Exception {	
	    final JDBCConnectionFactory JDBC = new JDBCConnectionFactory("localhost", 3306, "opendj_db");
	    JDBCConnection jdbcconnection = (JDBCConnection) JDBC.getConnection(); 
	    jdbcconnection.bind("root", "".toCharArray());
	    
	    final LDAPConnectionFactory LDAP = new LDAPConnectionFactory("localhost", 389);
        final Connection ldapconnection = LDAP.getConnection();
        ldapconnection.bind("cn=Directory Manager", "opendj".toCharArray());

	    final JDBCMapper JDBCM = new JDBCMapper(jdbcconnection, ldapconnection);
	    jdbcconnection.initializeMapper(JDBCM);
	    
	    AddRequest testRequest1 = Requests.newAddRequest("dn: uid=user.1,ou=People,dc=example,dc=com",
	    		"objectClass: top",
	    		"objectClass: person",
	    		"objectClass: inetOrgPerson",
	    		"givenName: Glenn",
	    		"sn: Van Lint",
	    		"cn: Glenn Van Lint",
	    		"employeeNumber: 1");	
		jdbcconnection.add(testRequest1);

		String[] ldifLines = {"dn: uid=user.2,ou=People,dc=example,dc=com",
				"objectClass: top",
				"objectClass: person",
				"objectClass: inetOrgPerson",
				"givenName: Jochen",
				"sn: Raymaekers",
				"cn: Jochen Raymaekers",
				"employeeNumber: 2"};
		jdbcconnection.add(ldifLines);
	 }
}



