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

import java.io.IOException;
import java.sql.SQLException;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.ResultHandler;

/**
 * A factory class which combines an LDAPConnectionFactory and a JDBCConnectionFactory 
 * which can be used to obtain unified connections.
 */
public final class JoinConnectionFactory implements ConnectionFactory 
{
  private final LDAPConnectionFactory ldapFactory;
  private final JDBCConnectionFactory jdbcFactory;
  private JoinConnection join;

  /**
   * Creates a new joined connection factory which can be used to create unified
   * connections to both back-ends.
   *
   * @param ldapfactory
   *            The LDAPConnectionFactory which provides connections to the
   *            Directory Server.
   * @param jdbcfactory
   *            The JDBCConnectionFactory which provides connections to the
   *            Database Server.        
   * @throws ErrorResultException
   *            If the connection request failed for some reason.
   * @throws SQLException
   *            If a database access error occurs.       
   * @throws IOException
   *            If an I/O exception error occurs. 
   */
  public JoinConnectionFactory(final LDAPConnectionFactory ldapfactory, final JDBCConnectionFactory jdbcfactory) throws ErrorResultException, SQLException, IOException 
  {
    this.ldapFactory = ldapfactory;
    this.jdbcFactory = jdbcfactory;
  }

  /**
   * Returns the subordinate LDAPConnectionFactory.
   *
   * @return The subordinate LDAPConnectionFactory.
   */
  public LDAPConnectionFactory getLDAPFactory()
  {
    return ldapFactory;
  }

  /**
   * Returns the subordinate JDBCConnectionFactory.
   *
   * @return The subordinate JDBCConnectionFactory.
   */
  public JDBCConnectionFactory getJDBCFactory()
  {
    return jdbcFactory;
  }

  /**
   * Returns a unified connection to the directory and database back-ends associated 
   * with this connection factory. The connection returned by this method can be used
   * immediately.
   *
   * @return A connection to the directory and database back-ends associated 
   * with this connection factory
   * @throws ErrorResultException
   *             If the connection request failed for some reason.
   * @throws SQLException
   *            If a database access error occurs.       
   * @throws IOException
   *            If an I/O exception error occurs. 
   */
  @Override
  public Connection getConnection() throws ErrorResultException 
  {
    if(join == null){
      try{
        join = new JoinConnection(ldapFactory, jdbcFactory);
      }catch (SQLException e){
        System.out.println(e.toString());
      }catch (IOException e){
        System.out.println(e.toString());
      }
    }
    return join;
  }

  @Override
  public FutureResult<Connection> getConnectionAsync(
      ResultHandler<? super Connection> handler) {
    return null;
  }
}
