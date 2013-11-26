/*
 * CDDL HEADER START
 * 
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * CDDL HEADER END
 *
 * Copyright 2013 ForgeRock AS.
 * Portions Copyright 2013 IS4U.
 */

package org.forgerock.opendj.virtual;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.ResultHandler;

/**
 * A factory class which can be used to obtain connections to an SQL Database
 * Server.
 */
public final class JDBCConnectionFactory implements ConnectionFactory 
{
  private String ConnectionUrl = "";
  private final String Host;
  private final int Port;
  private final String DbName;
  private JDBCConnection jdbc;

  /**
   * Creates a new JDBC connection factory which can be used to create JDBC
   * connections to the Database Server at the provided host and port
   * address.
   *
   *  @param host
   *            The host name.
   *  @param port
   *            The port number.
   *  @param dbName
   *            The name of the SQL database.              
   * @throws NullPointerException
   *             If {@code dbName} was {@code null}.
   */
  public JDBCConnectionFactory(final String host, final int port, final String dbName) 
  {
    this.Host = host;
    this.Port = port;
    this.DbName = dbName;

    //For connection to h2 database, use this.ConnectionUrl="jdbc:h2"
    this.ConnectionUrl="jdbc:mysql://"
        .concat(Host+":")
        .concat(Port+"/")
        .concat(DbName);
  }

  /**
   * Returns a connection to the Database Server associated with this
   * connection factory. The connection returned by this method can be used
   * immediately.
   *
   * @return A connection to the Database Server associated with this
   *         connection factory.
   * @throws ErrorResultException
   *             If the connection request failed for some reason.
   */
  @Override
  public Connection getConnection() throws ErrorResultException 
  {
    if (jdbc == null){
      this.jdbc = new JDBCConnection(ConnectionUrl);
    }
    return jdbc;
  }

  /**
   * Returns the SQL database host name that was provided to this 
   * connection factory.
   *
   * @return The SQL database host name that this connection factory uses.
   */
  public String getHostName() 
  {
    return Host;
  }

  /**
   * Returns the SQL database port number that was provided to this 
   * connection factory. 
   *
   * @return The SQL database name that this connection factory uses.
   */
  public int getPort() 
  {
    return Port;
  }

  /**
   * Returns the SQL database name that was provided to this
   * connection factory. 
   *
   * @return The SQL database name that this connection factory uses.
   */
  public String getDatabaseName()
  {
    return DbName;
  }

  @Override
  public FutureResult<Connection> getConnectionAsync(
      ResultHandler<? super Connection> handler) 
  {
    return null;
  }
}
