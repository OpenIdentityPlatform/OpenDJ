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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2012 ForgeRock AS.
 */

package org.forgerock.opendj.virtual;


import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.ResultHandler;

/**
 * Create a JDBC driver instance which contains all the methods for 
 * connection to the database.
 */

public final class JDBCConnectionFactory implements ConnectionFactory {
	private String ConnectionUrl = "";
	private final String Host;
	private final int Port;
	private final String DbName;
    private JDBCConnection jdbc;
	
	/**
     * Set up a JDBC connection configuration.
     *
     * @param host
     *            The hostname of the database to connect. 
     * @param port
     * 			  The port used to connect to the database.
     * @param dbName
     *            The name of the database.
     * @param userName
     *            The username required for authentication to the database.
     * @param userPass
     * 			  The password required for authentication to the database.
     */
    public JDBCConnectionFactory(final String host, final int port, final String dbName) {
        this.Host = host;
        this.Port = port;
        this.DbName = dbName;
        
        this.ConnectionUrl="jdbc:mysql://"
        		.concat(this.Host+":")
        		.concat(this.Port+"/")
        		.concat(this.DbName);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() throws ErrorResultException {
    	if (this.jdbc == null){
    		this.jdbc = new JDBCConnection(this.ConnectionUrl);
    	}
        	return this.jdbc;
    }

	@Override
	public FutureResult<Connection> getConnectionAsync(
			ResultHandler<? super Connection> handler) {
		//TODO
		return null;
	}
	
}



