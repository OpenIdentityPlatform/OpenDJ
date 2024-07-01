/*
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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.browser;

import java.io.Closeable;

import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.controls.ManageDsaITRequestControl;
import org.forgerock.opendj.ldap.controls.ServerSideSortRequestControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.forgerock.util.Reject;
import org.opends.admin.ads.util.ConnectionWrapper;

/**
 * Associates a {@link ConnectionWrapper} with the LDAP
 * {@link org.forgerock.opendj.ldap.controls.Control Control}s it should use when performing
 * operation.
 * <p>
 * The relevant controls are automatically added to the request when calling
 * {@link #add(AddRequest)}, {@link #delete(DeleteRequest)}, {@link #modify(ModifyRequest)},
 * {@link #modifyDN(ModifyDNRequest)}, {@link #search(SearchRequest)} or
 * {@link #searchSingleEntry(SearchRequest)}.
 */
public final class ConnectionWithControls implements Closeable
{
  private final ConnectionWrapper conn;
  private ServerSideSortRequestControl sortControl;
  private ManageDsaITRequestControl followReferralsControl;

  /**
   * Constructor.
   *
   * @param conn
   *          the connection wrapper
   * @param sortControl
   *          the sort control, may be {@code null}
   * @param followReferralsControl
   *          the manage dsa IT control, may be {@code null}
   */
  public ConnectionWithControls(ConnectionWrapper conn,
                                ServerSideSortRequestControl sortControl,
                                ManageDsaITRequestControl followReferralsControl)
  {
    this.conn = Reject.checkNotNull(conn);
    setRequestControls(sortControl, followReferralsControl);
  }

  /**
   * Returns the connection wrapper.
   * <p>
   * DO NOT USE TO PERFORM LDAP OPERATIONS!
   * <p>
   * This getter is only used to allow to query its state.
   *
   * @return the read-only connection wrapper.
   */
  public ConnectionWrapper getConnectionWrapper()
  {
    return conn;
  }

  /**
   * Sets the sort and manage dsa it controls to use when making LDAP operations.
   *
   * @param sortControl
   *          the sort control, may be {@code null}
   * @param followReferralsControl
   *          the manage dsa IT control, may be {@code null}
   */
  public void setRequestControls(
      ServerSideSortRequestControl sortControl,
      ManageDsaITRequestControl followReferralsControl)
  {
    this.sortControl = sortControl;
    this.followReferralsControl = followReferralsControl;
  }

  /**
   * Adds the sort and referral controls if needed.
   *
   * @param request
   *          the request
   */
  void addControls(Request request)
  {
    if (sortControl != null && request instanceof SearchRequest)
    {
      request.addControl(sortControl);
    }
    if (followReferralsControl != null)
    {
      request.addControl(followReferralsControl);
    }
  }

  /**
   * Searches a single entry.
   *
   * @param request
   *          the request
   * @return the non-{@code null} single SearchResultEntry
   * @throws LdapException
   *           if an error occurred.
   * @see org.forgerock.opendj.ldap.Connection#searchSingleEntry(SearchRequest)
   */
  public SearchResultEntry searchSingleEntry(SearchRequest request) throws LdapException
  {
    addControls(request);
    return conn.getConnection().searchSingleEntry(request);
  }

  /**
   * Searches using the provided request.
   *
   * @param request
   *          the request
   * @return a non-{@code null} {@link ConnectionEntryReader}
   * @see org.forgerock.opendj.ldap.Connection#search(SearchRequest)
   */
  public ConnectionEntryReader search(SearchRequest request)
  {
    addControls(request);
    return conn.getConnection().search(request);
  }

  /**
   * Adds with the provided request.
   *
   * @param request
   *          the request
   * @throws LdapException
   *           if an error occurred.
   * @see org.forgerock.opendj.ldap.Connection#add(AddRequest)
   */
  public void add(AddRequest request) throws LdapException
  {
    addControls(request);
    conn.getConnection().add(request);
  }

  /**
   * Deletes with the provided request.
   *
   * @param request
   *          the request
   * @throws LdapException
   *           if an error occurred.
   * @see org.forgerock.opendj.ldap.Connection#delete(DeleteRequest)
   */
  public void delete(DeleteRequest request) throws LdapException
  {
    addControls(request);
    conn.getConnection().delete(request);
  }

  /**
   * Modifies with the provided request.
   *
   * @param request
   *          the request
   * @throws LdapException
   *           if an error occurred.
   * @see org.forgerock.opendj.ldap.Connection#modify(ModifyRequest)
   */
  public void modify(ModifyRequest request) throws LdapException
  {
    addControls(request);
    conn.getConnection().modify(request);
  }

  /**
   * modifies a DN with the provided request.
   *
   * @param request
   *          the request
   * @throws LdapException
   *           if an error occurred.
   * @see org.forgerock.opendj.ldap.Connection#modifyDN(ModifyDNRequest)
   */
  public void modifyDN(ModifyDNRequest request) throws LdapException
  {
    addControls(request);
    conn.getConnection().modifyDN(request);
  }

  @Override
  public void close()
  {
    conn.close();
  }
}
