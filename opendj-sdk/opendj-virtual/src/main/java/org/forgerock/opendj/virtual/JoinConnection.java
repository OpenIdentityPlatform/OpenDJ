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
import java.util.List;

import org.forgerock.opendj.ldap.AbstractSynchronousConnection;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionEventListener;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.ErrorResultIOException;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.requests.UnbindRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldif.ConnectionEntryReader;

/**
 * Join connection implementation.
 */
public class JoinConnection extends AbstractSynchronousConnection 
{
  private final LDAPConnectionFactory ldapFactory;
  private final JDBCConnectionFactory jdbcFactory;
  private final Connection ldapConnection;
  private final JDBCConnection jdbcConnection;
  private JDBCMapper jdbcMapper;
  private ConnectionEntryReader ldapEntries;
  private List<Entry> jdbcEntries;

  /**
   * Creates a new join connection.
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
  public JoinConnection(final LDAPConnectionFactory ldapfactory, final JDBCConnectionFactory jdbcfactory) throws ErrorResultException, SQLException, IOException
  {
    this.ldapFactory = ldapfactory;
    this.jdbcFactory = jdbcfactory;
    this.ldapConnection = ldapFactory.getConnection();
    this.jdbcConnection = (JDBCConnection) jdbcFactory.getConnection(); 
  }

  /**
   * Loads the mapping component for the JDBC connection.
   *
   * @param jdbcMapper
   *            The JDBCMapper object used to map the directory and database 
   *            structure.
   * @throws SQLException
   *            If the SQL query has an invalid format.
   * @throws ErrorResultException
   *            If the result code indicates that the request failed for some
   *            reason.  
   * @throws IOException
   *            If an I/O exception error occurs. 
   */
  public void loadMapper(final JDBCMapper jdbcMapper)
  {
    try
    {
      jdbcConnection.initializeMapper(jdbcMapper);
    }
    catch (SQLException e)
    {
      System.out.println(e.toString());
    }
    catch (ErrorResultException e)
    {
      System.out.println(e.toString());
    }
    catch (IOException e)
    {
      System.out.println(e.toString());
    }
  }

  /**
   * Authenticates to the Directory server and the Database Server using the provided bind requests.
   *       
   * @param ldapBindRequest
   *            The bind request for the Directory Server.
   * @param jdbcBindRequest
   *            The bind request for the Database Server.
   * @return The result of the operation.
   * @throws ErrorResultException
   *            If the result code indicates that the request failed for some
   *            reason.
   * @throws SQLException
   *            If the SQL query has an invalid format.
   * @throws IOException
   *            If an I/O exception error occurs. 
   */
  public BindResult bind(final BindRequest ldapBindRequest, final BindRequest jdbcBindRequest) throws ErrorResultException, SQLException, IOException
  {
    BindResult r = ldapConnection.bind(ldapBindRequest);
    if(r.isSuccess()) r = jdbcConnection.bind(jdbcBindRequest);

    jdbcMapper = new JDBCMapper(jdbcConnection, ldapConnection);
    jdbcMapper.setDatabaseName(jdbcFactory.getDatabaseName());
    jdbcConnection.initializeMapper(jdbcMapper);
    return r;
  }

  /**
   * Sends the provided add request to the back-ends of the subordinate connections.
   *       
   * @param request
   *            The add request.
   * @return The result of the operation.
   * @throws ErrorResultException
   *            If the result code indicates that the request failed for some
   *            reason.
   */
  @Override
  public Result add(AddRequest request) throws ErrorResultException
  {
    Result r = ldapConnection.add(request);
    if(r.isSuccess()) r = jdbcConnection.add(request);
    return r;
  }

  @Override
  public void addConnectionEventListener(ConnectionEventListener listener)
  {
    // TODO Auto-generated method stub
  }

  @Override
  public BindResult bind(BindRequest request) throws ErrorResultException
  {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close(UnbindRequest request, String reason)
  {
    if(ldapConnection != null) ldapConnection.close();
    if(jdbcConnection != null) jdbcConnection.close();
  }

  /**
   * Sends the provided compare request to the back-ends of the subordinate connections.
   *       
   * @param request
   *            The compare request.
   * @return The result of the operation.
   * @throws ErrorResultException
   *            If the result code indicates that the request failed for some
   *            reason.
   */
  @Override
  public CompareResult compare(CompareRequest request) throws ErrorResultException
  {
    CompareResult r = ldapConnection.compare(request);
    if(r.isSuccess()) r = jdbcConnection.compare(request);
    return r;
  }

  /**
   * Sends the provided delete request to the back-ends of the subordinate connections.
   *       
   * @param request
   *            The delete request.
   * @return The result of the operation.
   * @throws ErrorResultException
   *            If the result code indicates that the request failed for some
   *            reason.
   */
  @Override
  public Result delete(DeleteRequest request) throws ErrorResultException
  {
    Result r = ldapConnection.delete(request);
    if(r.isSuccess()) r = jdbcConnection.delete(request);
    return r;
  }

  @Override
  public <R extends ExtendedResult> R extendedRequest(ExtendedRequest<R> request, IntermediateResponseHandler handler) throws ErrorResultException
  {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isClosed()
  {
    if(ldapConnection.isClosed() && jdbcConnection.isClosed()) return true;
    else return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isValid()
  {
    if(ldapConnection.isValid() && jdbcConnection.isValid()) return true;
    else return false;
  }

  /**
   * Sends the provided modify request to the back-ends of the subordinate connections.
   *       
   * @param request
   *            The modify request.
   * @return The result of the operation.
   * @throws ErrorResultException
   *            If the result code indicates that the request failed for some
   *            reason.
   */
  @Override
  public Result modify(ModifyRequest request) throws ErrorResultException
  {
    Result r = ldapConnection.modify(request);
    if(r.isSuccess()) r = jdbcConnection.modify(request);
    return r;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Result modifyDN(ModifyDNRequest request) throws ErrorResultException
  {
    Result r = ldapConnection.modifyDN(request);
    return r;
  }

  @Override
  public void removeConnectionEventListener(ConnectionEventListener listener)
  {
    // TODO Auto-generated method stub
  }

  /**
   * Returns a ConnectionEntryReader to iterate over the directory search results of the last search request.
   *       
   * @return The ConnectionEntryReader to iterate over the directory search results.
   */
  public ConnectionEntryReader getLDAPSearchEntries()
  {
    return ldapEntries;
  }

  /**
   * Returns a list containing the database search results of the last search request.
   *       
   * @return The list containing the database search results.
   * @throws SearchResultReferenceIOException
   *            If the iteration over the set of search results using a ConnectionEntryReader 
   *            encountered a SearchResultReference.
   * @throws ErrorResultException
   *            If the result code indicates that the request failed for some
   *            reason.
   */
  public List<Entry> getJDBCSearchEntries() throws SearchResultReferenceIOException, ErrorResultIOException
  {
    return jdbcEntries;
  }

  /**
   * Sends the provided search request to the back-ends of the subordinate connections and saves
   * the entry and record results.
   *       
   * @param request
   *            The search request.
   * @param handler
   *            A search result handler which can be used to asynchronously process the 
   *            search result entries and references as they are received, may be null.
   * @return The result of the operation.
   * @throws ErrorResultException
   *            If the result code indicates that the request failed for some
   *            reason.
   */
  @Override
  public Result search(SearchRequest request, SearchResultHandler handler)throws ErrorResultException
  {
    if(request.getAttributes().get(0).isEmpty()){
      request = Requests.newSearchRequest(request.getName(), request.getScope(), request.getFilter());
    }
    ldapEntries = ldapConnection.search(request);
    Result r = jdbcConnection.search(request, handler);
    if(r.isSuccess()) jdbcEntries = jdbcConnection.getSearchEntries();
    return r;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return ldapConnection.toString() + " " + jdbcConnection.toString();
  }
}
