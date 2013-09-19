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
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS
 */
package org.opends.server.types;

import static org.opends.server.util.Validator.*;

/**
 * This class defines a data structure that may be used to store
 * information about an authenticated user.  Note that structures in
 * this class allow for multiple authentication types for the same
 * user, which is not currently supported by LDAP but may be offered
 * through some type of extension.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class AuthenticationInfo
{

  /** Indicates whether this connection is currently authenticated. */
  private boolean isAuthenticated;

  /** Indicates whether this connection is authenticated as a root user. */
  private boolean isRoot;

  /**
   * Indicates whether the user's password must be changed before any other
   * operation will be allowed.
   */
  private boolean mustChangePassword;

  /** The entry of the user that is currently authenticated. */
  private Entry authenticationEntry;

  /**
   * The entry of the user that will be used as the default authorization
   * identity.
   */
  private Entry authorizationEntry;

  /** The type of authentication performed on this connection. */
  private AuthenticationType authenticationType;

  /** The SASL mechanism used to authenticate. */
  private String saslMechanism;

  /** The bind DN used to authenticate using simple authentication. */
  private DN simpleBindDN;

  /**
   * Creates a new set of authentication information to be used for
   * unauthenticated clients.
   */
  public AuthenticationInfo()
  {
    isAuthenticated     = false;
    isRoot              = false;
    mustChangePassword  = false;
    authenticationType  = null;
    authenticationEntry = null;
    authorizationEntry  = null;
    simpleBindDN        = null;
    saslMechanism       = null;
  }



  /**
   * Creates a new set of authentication information to be used for
   * clients that are authenticated internally.
   *
   * @param  authenticationEntry  The entry of the user that has
   *                              authenticated, or {@code null} to
   *                              indicate an unauthenticated user.
   * @param  isRoot               Indicates whether the authenticated
   *                              user is a root user.
   */
  public AuthenticationInfo(Entry authenticationEntry, boolean isRoot)
  {
    this.authenticationEntry = authenticationEntry;
    this.isRoot              = isRoot;

    isAuthenticated     = (authenticationEntry != null);
    mustChangePassword  = false;
    simpleBindDN        = authenticationEntry != null ?
        authenticationEntry.getDN() : null;
    authorizationEntry  = authenticationEntry;
    saslMechanism       = null;
    authenticationType  = AuthenticationType.INTERNAL;
  }

  /**
   * Creates a new set of authentication information to be used for
   * clients that have successfully performed simple authentication.
   *
   * @param  authenticationEntry  The entry of the user that has
   *                              authenticated.  It must not be
   *                              {@code null}.
   * @param  simpleBindDN         The bind DN that was used to
   *                              perform the simple authentication.
   * @param  isRoot               Indicates whether the authenticated
   */
  public AuthenticationInfo(Entry authenticationEntry, DN simpleBindDN,
      boolean isRoot)
  {
    ensureNotNull(authenticationEntry);

    this.authenticationEntry = authenticationEntry;
    this.simpleBindDN        = simpleBindDN;
    this.isRoot              = isRoot;

    this.isAuthenticated     = true;
    this.mustChangePassword  = false;
    this.authorizationEntry  = authenticationEntry;
    this.saslMechanism       = null;
    this.authenticationType  = AuthenticationType.SIMPLE;
  }



  /**
   * Creates a new set of authentication information to be used for
   * clients that have authenticated using a SASL mechanism.
   *
   * @param  authenticationEntry  The entry of the user that has
   *                              authenticated.  It must not be
   *                              {@code null}.
   * @param  saslMechanism        The SASL mechanism used to
   *                              authenticate.  This must be provided
   *                              in all-uppercase characters and must
   *                              not be {@code null}.
   * @param  isRoot               Indicates whether the authenticated
   *                              user is a root user.
   */
  public AuthenticationInfo(Entry authenticationEntry,
                            String saslMechanism,
                            boolean isRoot)
  {
    ensureNotNull(authenticationEntry, saslMechanism);

    this.authenticationEntry = authenticationEntry;
    this.isRoot              = isRoot;

    this.isAuthenticated    = true;
    this.mustChangePassword = false;
    this.authorizationEntry = authenticationEntry;
    this.simpleBindDN       = null;
    this.authenticationType = AuthenticationType.SASL;
    this.saslMechanism      = saslMechanism;
  }



  /**
   * Creates a new set of authentication information to be used for
   * clients that have authenticated using a SASL mechanism.
   *
   * @param  authenticationEntry  The entry of the user that has
   *                              authenticated.  It must not be
   *                              {@code null}.
   * @param  authorizationEntry   The entry of the user that will be
   *                              used as the default authorization
   *                              identity, or {@code null} to
   *                              indicate that the authorization
   *                              identity should be the
   *                              unauthenticated user.
   * @param  saslMechanism        The SASL mechanism used to
   *                              authenticate.  This must be provided
   *                              in all-uppercase characters and must
   *                              not be {@code null}.
   * @param  saslCredentials      The SASL credentials used to
   *                              authenticate.
   *                              It must not be {@code null}.
   * @param  isRoot               Indicates whether the authenticated
   *                              user is a root user.
   */
  public AuthenticationInfo(Entry authenticationEntry,
                            Entry authorizationEntry,
                            String saslMechanism,
                            ByteString saslCredentials,
                            boolean isRoot)
  {
    ensureNotNull(authenticationEntry, saslMechanism);

    this.authenticationEntry = authenticationEntry;
    this.authorizationEntry  = authorizationEntry;
    this.isRoot              = isRoot;

    this.isAuthenticated    = true;
    this.mustChangePassword = false;
    this.simpleBindDN       = null;
    this.authenticationType = AuthenticationType.SASL;
    this.saslMechanism      = saslMechanism;
  }



  /**
   * Indicates whether this client has successfully authenticated to
   * the server.
   *
   * @return  {@code true} if this client has successfully
   *          authenticated to the server, or {@code false} if not.
   */
  public boolean isAuthenticated()
  {
    return isAuthenticated;
  }



  /**
   * Indicates whether this client should be considered a root user.
   *
   * @return  {@code true} if this client should be considered a root
   *          user, or {@code false} if not.
   */
  public boolean isRoot()
  {
    return isRoot;
  }



  /**
   * Indicates whether the authenticated user must change his/her
   * password before any other operation will be allowed.
   *
   * @return  {@code true} if the user must change his/her password
   *          before any other operation will be allowed, or
   *          {@code false} if not.
   */
  public boolean mustChangePassword()
  {
    return mustChangePassword;
  }



  /**
   * Specifies whether the authenticated user must change his/her
   * password before any other operation will be allowed.
   *
   * @param  mustChangePassword  Specifies whether the authenticated
   *                             user must change his/her password
   *                             before any other operation will be
   *                             allowed.
   */
  public void setMustChangePassword(boolean mustChangePassword)
  {
    this.mustChangePassword = mustChangePassword;
  }



  /**
   * Indicates whether this client has authenticated using the
   * specified authentication type.
   *
   * @param  authenticationType  The authentication type for which to
   *                             make the determination.
   *
   * @return  {@code true} if the client has authenticated using the
   *          specified authentication type, or {@code false} if not.
   */
  public boolean hasAuthenticationType(AuthenticationType
                                            authenticationType)
  {
    return this.authenticationType == authenticationType;
  }



  /**
   * Retrieves the entry for the user as whom the client is
   * authenticated.
   *
   * @return  The entry for the user as whom the client is
   *          authenticated, or {@code null} if the client is
   *          unauthenticated.
   */
  public Entry getAuthenticationEntry()
  {
    return authenticationEntry;
  }



  /**
   * Retrieves the DN of the user as whom the client is authenticated.
   *
   * @return  The DN of the user as whom the client is authenticated,
   *          or {@code null} if the client is unauthenticated.
   */
  public DN getAuthenticationDN()
  {
    if (authenticationEntry != null)
    {
      return authenticationEntry.getDN();
    }
    return null;
  }



  /**
   * Sets the DN of the user as whom the client is authenticated,
   * does nothing if the client is unauthenticated.
   *
   * @param dn authentication identity DN.
   */
  public void setAuthenticationDN(DN dn)
  {
    if (authenticationEntry != null)
    {
      authenticationEntry.setDN(dn);
    }
  }



  /**
   * Retrieves the entry for the user that should be used as the
   * default authorization identity.
   *
   * @return  The entry for the user that should be used as the
   *          default authorization identity, or {@code null} if the
   *          authorization identity should be the unauthenticated
   *          user.
   */
  public Entry getAuthorizationEntry()
  {
    return authorizationEntry;
  }



  /**
   * Retrieves the DN for the user that should be used as the default
   * authorization identity.
   *
   * @return  The DN for the user that should be used as the default
   *          authorization identity, or {@code null} if the
   *          authorization identity should be the unauthenticated
   *          user.
   */
  public DN getAuthorizationDN()
  {
    if (authorizationEntry != null)
    {
      return authorizationEntry.getDN();
    }
    return null;
  }



  /**
   * Sets the DN for the user that should be used as the default
   * authorization identity, does nothing if the client is
   * unauthorized.
   *
   * @param dn authorization identity DN.
   */
  public void setAuthorizationDN(DN dn)
  {
    if (authorizationEntry != null)
    {
      authorizationEntry.setDN(dn);
    }
  }



  /**
   * Retrieves the bind DN that the client used for simple
   * authentication.
   *
   * @return  The bind DN that the client used for simple
   *          authentication, or {@code null} if the client is not
   *          authenticated using simple authentication.
   */
  public DN getSimpleBindDN()
  {
    return simpleBindDN;
  }



  /**
   * Indicates whether the client is currently authenticated using the
   * specified SASL mechanism.
   *
   * @param  saslMechanism  The SASL mechanism for which to make the
   *                        determination.  Note that this must be
   *                        provided in all uppercase characters.
   *
   * @return  {@code true} if the client is authenticated using the
   *          specified SASL mechanism, or {@code false} if not.
   */
  public boolean hasSASLMechanism(String saslMechanism)
  {
    return this.saslMechanism.equals(saslMechanism);
  }

  /**
   * Retrieves a string representation of this authentication info
   * structure.
   *
   * @return  A string representation of this authentication info
   *          structure.
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);

    return buffer.toString();
  }



  /**
   * Appends a string representation of this authentication info
   * structure to the provided buffer.
   *
   * @param  buffer  The buffer to which the information is to be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("AuthenticationInfo(isAuthenticated=");
    buffer.append(isAuthenticated);
    buffer.append(",isRoot=");
    buffer.append(isRoot);
    buffer.append(",mustChangePassword=");
    buffer.append(mustChangePassword);
    buffer.append(",authenticationDN=\"");

    if (authenticationEntry != null)
    {
      authenticationEntry.getDN().toString(buffer);
    }

    if (authorizationEntry == null)
    {
      buffer.append("\",authorizationDN=\"\"");
    }
    else
    {
      buffer.append("\",authorizationDN=\"");
      authorizationEntry.getDN().toString(buffer);
      buffer.append("\"");
    }

    if (authenticationType != null)
    {
      buffer.append(",authType=");
      buffer.append(authenticationType);
    }

    if (saslMechanism != null)
    {
      buffer.append(",saslMechanism=");
      buffer.append(saslMechanism);
    }

    buffer.append(")");
  }



  /**
   * Creates a duplicate of this {@code AuthenticationInfo} object
   * with the new authentication and authorization entries.
   *
   * @param  newAuthenticationEntry  The updated entry for the user
   *                                 as whom the associated client
   *                                 connection is authenticated.
   * @param  newAuthorizationEntry   The updated entry for the default
   *                                 authorization identity for the
   *                                 associated client connection.
   *
   * @return  The duplicate of this {@code AuthenticationInfo} object
   *          with the specified authentication and authorization
   *          entries.
   */
  public AuthenticationInfo duplicate(Entry newAuthenticationEntry,
                                      Entry newAuthorizationEntry)
  {
    AuthenticationInfo authInfo = new AuthenticationInfo();

    authInfo.isAuthenticated     = isAuthenticated;
    authInfo.isRoot              = isRoot;
    authInfo.mustChangePassword  = mustChangePassword;
    authInfo.authenticationEntry = newAuthenticationEntry;
    authInfo.authorizationEntry  = newAuthorizationEntry;

    authInfo.authenticationType  = authenticationType;
    authInfo.saslMechanism       = saslMechanism;

    return authInfo;
  }
}

