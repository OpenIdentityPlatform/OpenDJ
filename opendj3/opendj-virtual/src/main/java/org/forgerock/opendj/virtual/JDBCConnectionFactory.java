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

public final class JDBCConnectionFactory implements ConnectionFactory {
  private String ConnectionUrl = "";
  private final String Host;
  private final int Port;
  private final String DbName;
  private JDBCConnection jdbc;

  public JDBCConnectionFactory(final String host, final int port, final String dbName) {
    this.Host = host;
    this.Port = port;
    this.DbName = dbName;

    this.ConnectionUrl="jdbc:mysql://"
        .concat(this.Host+":")
        .concat(this.Port+"/")
        .concat(this.DbName);
  }

  public String getDbName(){
    return this.DbName;
  }

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
    return null;
  }
}



