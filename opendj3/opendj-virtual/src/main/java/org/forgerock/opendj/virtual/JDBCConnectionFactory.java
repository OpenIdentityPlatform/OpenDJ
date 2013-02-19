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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Create a JDBC driver instance which contains all the methods for 
 * connection and commands to the database.
 */

public class JDBCConnectionFactory {
    String driverName = "com.mysql.jdbc.Driver";
	Connection con = null;
    
    public JDBCConnectionFactory() {
            try {
                    Class.forName(driverName);
            } catch (ClassNotFoundException e) {
                    System.out.println(e.toString());
            }
    }
    
    /**
     * Set up a JDBC connection using the defined parameters.
     *
     * @param host
     *            The host address of the database. 
     * @param port
     *            The port used to connect to the database.
     * @param databaseName
     *            The name of the database to connect with.
     * @param userName
     *            The username required for authentication to the database.
     * @param userPass
     * 			  The password required for authentication to the database.
     * @return The created connection.
     */
    public Connection createConnection(String host, String port, String databaseName, String userName, String userPass) {
            try {
                    String connectionUrl="jdbc:mysql://"
		                    		.concat(host+":")
		                    		.concat(port+"/")
		                    		.concat(databaseName);
                    con = DriverManager
                                    .getConnection(connectionUrl,userName,userPass);
                    System.out.println("Connection created.");
                    } catch (SQLException e) {
                    System.out.println(e.toString());
            }
            return con;
    }
    
    /**
     * Close the open connection to the database.
     */
    public void closeConnection(){
            try{
                    this.con.close();
            System.out.println("Connection terminated.");
            }catch(Exception e){
                    System.out.println(e.toString());
            }
    }
}



