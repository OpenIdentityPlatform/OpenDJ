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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.util.Validator.*;



/**
 * This class defines a data structure that may be used to store
 * information about an authenticated user.  Note that structures in
 * this class allow for multiple authentication types for the same
 * user, which is not currently supported by LDAP but may be offered
 * through some type of extension.
 */
public class AuthenticationInfo
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.AuthenticationInfo";



  // The password used to authenticate using simple authentication.
  private ByteString simplePassword;

  // Indicates whether this connection is currently authenticated.
  private boolean isAuthenticated;

  // Indicates whether this connection is authenticated as a root
  // user.
  private boolean isRoot;

  // Indicates whether the user's password must be changed before any
  // other operation will be allowed.
  private boolean mustChangePassword;

  // The entry of the user that is currently authenticated.
  private Entry authenticationEntry;

  // The entry of the user that will be used as the default
  // authorization identity.
  private Entry authorizationEntry;

  // The type of authentication performed on this connection.
  private Set<AuthenticationType> authenticationTypes;

  // The SASL mechanism used to authenticate.
  private Set<String> saslMechanisms;



  /**
   * Creates a new set of authentication information to be used for
   * unauthenticated clients.
   */
  public AuthenticationInfo()
  {
    assert debugConstructor(CLASS_NAME);

    isAuthenticated     = false;
    isRoot              = false;
    mustChangePassword  = false;
    simplePassword      = null;
    authenticationTypes = new HashSet<AuthenticationType>(0);
    authenticationEntry = null;
    authorizationEntry  = null;
    saslMechanisms      = new HashSet<String>(0);
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
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(authenticationEntry),
                            String.valueOf(isRoot));

    this.authenticationEntry = authenticationEntry;
    this.isRoot              = isRoot;

    isAuthenticated     = (authenticationEntry != null);
    mustChangePassword  = false;
    simplePassword      = null;
    authorizationEntry  = authenticationEntry;
    saslMechanisms      = new HashSet<String>(0);
    authenticationTypes = new HashSet<AuthenticationType>(1);

    authenticationTypes.add(AuthenticationType.INTERNAL);
  }



  /**
   * Creates a new set of authentication information to be used for
   * clients that have successfully performed simple authentication.
   *
   * @param  authenticationEntry  The entry of the user that has
   *                              authenticated.  It must not be
   *                              {@code null}.
   * @param  simplePassword       The password that was used to
   *                              perform the simple authentication.
   *                              It must not be {@code null}.
   * @param  isRoot               Indicates whether the authenticated
   *                              user is a root user.
   */
  public AuthenticationInfo(Entry authenticationEntry,
                            ByteString simplePassword, boolean isRoot)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(authenticationEntry),
                            String.valueOf(simplePassword),
                            String.valueOf(isRoot));

    ensureNotNull(authenticationEntry, simplePassword);

    this.authenticationEntry = authenticationEntry;
    this.simplePassword      = simplePassword;
    this.isRoot              = isRoot;

    isAuthenticated     = true;
    mustChangePassword  = false;
    authorizationEntry  = authenticationEntry;
    saslMechanisms      = new HashSet<String>(0);
    authenticationTypes = new HashSet<AuthenticationType>(1);

    authenticationTypes.add(AuthenticationType.SIMPLE);
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
                            String saslMechanism, boolean isRoot)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(authenticationEntry),
                            String.valueOf(saslMechanism),
                            String.valueOf(isRoot));

    ensureNotNull(authenticationEntry, saslMechanism);

    this.authenticationEntry = authenticationEntry;
    this.isRoot              = isRoot;

    isAuthenticated    = true;
    mustChangePassword = false;
    authorizationEntry = authenticationEntry;
    simplePassword     = null;

    authenticationTypes = new HashSet<AuthenticationType>(1);
    authenticationTypes.add(AuthenticationType.SASL);

    saslMechanisms = new HashSet<String>(1);
    saslMechanisms.add(saslMechanism);
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
   *                              indicate that it should be the same
   *                              as the authentication entry.
   * @param  saslMechanism        The SASL mechanism used to
   *                              authenticate.  This must be provided
   *                              in all-uppercase characters and must
   *                              not be {@code null}.
   * @param  isRoot               Indicates whether the authenticated
   *                              user is a root user.
   */
  public AuthenticationInfo(Entry authenticationEntry,
                            Entry authorizationEntry,
                            String saslMechanism, boolean isRoot)
  {
    assert debugConstructor(CLASS_NAME,
                            String.valueOf(authenticationEntry),
                            String.valueOf(authorizationEntry),
                            String.valueOf(saslMechanism),
                            String.valueOf(isRoot));

    ensureNotNull(authenticationEntry, saslMechanism);

    this.authenticationEntry = authenticationEntry;
    this.isRoot              = isRoot;

    if (authorizationEntry == null)
    {
      this.authorizationEntry = authenticationEntry;
    }
    else
    {
      this.authorizationEntry = authorizationEntry;
    }

    isAuthenticated    = true;
    mustChangePassword = false;
    simplePassword     = null;

    authenticationTypes = new HashSet<AuthenticationType>(1);
    authenticationTypes.add(AuthenticationType.SASL);

    saslMechanisms = new HashSet<String>(1);
    saslMechanisms.add(saslMechanism);
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
    assert debugEnter(CLASS_NAME, "isAuthenticated");

    return isAuthenticated;
  }



  /**
   * Sets this authentication info structure to reflect that the
   * client is not authenticated.
   */
  public void setUnauthenticated()
  {
    assert debugEnter(CLASS_NAME, "setUnauthenticated");

    isAuthenticated     = false;
    isRoot              = false;
    mustChangePassword  = false;
    simplePassword      = null;
    authenticationEntry = null;
    authorizationEntry  = null;

    authenticationTypes.clear();
    saslMechanisms.clear();
  }



  /**
   * Indicates whether this client should be considered a root user.
   *
   * @return  {@code true} if this client should be considered a root
   *          user, or {@code false} if not.
   */
  public boolean isRoot()
  {
    assert debugEnter(CLASS_NAME, "isRoot");

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
    assert debugEnter(CLASS_NAME, "mustChangePassword");

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
    assert debugEnter(CLASS_NAME, "setMustChangePassword",
                      String.valueOf(mustChangePassword));

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
    assert debugEnter(CLASS_NAME, "hasAuthenticationType",
                      String.valueOf(authenticationType));

    return authenticationTypes.contains(authenticationType);
  }



  /**
   * Indicates whether this client has authenticated using any of the
   * authentication types in the given collection.
   *
   * @param  types  The collection of authentication types for which
   *                to make the determination.
   *
   * @return  {@code true} if the client has authenticated using any
   *          of the specified authentication types, or {@code false}
   *          if not.
   */
  public boolean hasAnyAuthenticationType(
                      Collection<AuthenticationType> types)
  {
    assert debugEnter(CLASS_NAME, "hasAnyAuthenticationType",
                      String.valueOf(types));

    for (AuthenticationType t : types)
    {
      if (authenticationTypes.contains(t))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * Retrieves the set of authentication types performed by the
   * client.
   *
   * @return  The set of authentication types performed by the client.
   */
  public Set<AuthenticationType> getAuthenticationTypes()
  {
    assert debugEnter(CLASS_NAME, "getAuthenticationTypes");

    return authenticationTypes;
  }



  /**
   * Adds the provided authentication type to the set of
   * authentication types completed by the client.  This should only
   * be used in conjunction with multi-factor or step-up
   * authentication mechanisms.
   *
   * @param  authenticationType  The authentication type to add for
   *                             this client.
   */
  public void addAuthenticationType(AuthenticationType
                                         authenticationType)
  {
    assert debugEnter(CLASS_NAME, "addAuthenticationType");

    authenticationTypes.add(authenticationType);
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
    assert debugEnter(CLASS_NAME, "getAuthenticationEntry");

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
    assert debugEnter(CLASS_NAME, "getAuthenticationDN");

    if (authenticationEntry == null)
    {
      return null;
    }
    else
    {
      return authenticationEntry.getDN();
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
    assert debugEnter(CLASS_NAME, "getAuthorizationEntry");

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
    assert debugEnter(CLASS_NAME, "getAuthorizationDN");

    if (authorizationEntry == null)
    {
      return null;
    }
    else
    {
      return authorizationEntry.getDN();
    }
  }



  /**
   * Retrieves the password that the client used for simple
   * authentication.
   *
   * @return  The password that the client used for simple
   *          authentication, or {@code null} if the client is not
   *          authenticated using simple authentication.
   */
  public ByteString getSimplePassword()
  {
    assert debugEnter(CLASS_NAME, "getSimplePassword");

    return simplePassword;
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
    assert debugEnter(CLASS_NAME, "hasSASLMechanism",
                      String.valueOf(saslMechanism));

    return saslMechanisms.contains(saslMechanism);
  }



  /**
   * Indicates whether this client has authenticated using any of the
   * SASL mechanisms in the given collection.
   *
   * @param  mechanisms  The collection of SASL mechanisms for which
   *                     to make the determination.
   *
   * @return  {@code true} if the client has authenticated using any
   *          of the provided SASL mechanisms, or {@code false} if
   *          not.
   */
  public boolean hasAnySASLMechanism(Collection<String> mechanisms)
  {
    assert debugEnter(CLASS_NAME, "hasAnySASLMechanism",
                      String.valueOf(mechanisms));

    for (String s : mechanisms)
    {
      if (saslMechanisms.contains(s))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * Retrieves the set of mechanisms that the client used for SASL
   * authentication.
   *
   * @return  The set of mechanisms that the client used for SASL
   *          authentication, or an empty set if SASL mechanism has
   *          not been used.
   */
  public Set<String> getSASLMechanisms()
  {
    assert debugEnter(CLASS_NAME, "getSASLMechanisms");

    return saslMechanisms;
  }



  /**
   * Adds the provided mechanism to the set of SASL mechanisms used by
   * the client.  This should only be used in conjunction with
   * multi-factor or step-up authentication mechanisms.
   *
   * @param  saslMechanism  The SASL mechanism to add to set of
   *                        mechanisms for this client.  Note that
   *                        this must be provided in all uppercase
   *                        characters.
   */
  public void addSASLMechanism(String saslMechanism)
  {
    assert debugEnter(CLASS_NAME, "addSASLMechanism",
                      String.valueOf(saslMechanism));

    saslMechanisms.add(saslMechanism);
  }



  /**
   * Retrieves a string representation of this authentication info
   * structure.
   *
   * @return  A string representation of this authentication info
   *          structure.
   */
  public String toString()
  {
    assert debugEnter(CLASS_NAME, "toString");

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
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder");

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

    if (! authenticationTypes.isEmpty())
    {
      Iterator<AuthenticationType> iterator =
           authenticationTypes.iterator();
      AuthenticationType authType = iterator.next();

      if (iterator.hasNext())
      {
        buffer.append(",authTypes={");
        buffer.append(authType);

        while (iterator.hasNext())
        {
          buffer.append(",");
          buffer.append(iterator.next());
        }

        buffer.append("}");
      }
      else
      {
        buffer.append(",authType=");
        buffer.append(authType);
      }
    }

    if (! saslMechanisms.isEmpty())
    {
      Iterator<String> iterator = saslMechanisms.iterator();
      String mech = iterator.next();

      if (iterator.hasNext())
      {
        buffer.append(",saslMechanisms={");
        buffer.append(mech);

        while (iterator.hasNext())
        {
          buffer.append(",");
          buffer.append(iterator.next());
        }

        buffer.append("}");
      }
      else
      {
        buffer.append(",saslMechanism=");
        buffer.append(mech);
      }
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
    assert debugEnter(CLASS_NAME, "duplicate",
                      String.valueOf(newAuthenticationEntry),
                      String.valueOf(newAuthorizationEntry));

    AuthenticationInfo authInfo = new AuthenticationInfo();

    authInfo.simplePassword      = simplePassword;
    authInfo.isAuthenticated     = isAuthenticated;
    authInfo.isRoot              = isRoot;
    authInfo.mustChangePassword  = mustChangePassword;
    authInfo.authenticationEntry = newAuthenticationEntry;
    authInfo.authorizationEntry  = newAuthorizationEntry;

    authInfo.authenticationTypes.addAll(authenticationTypes);
    authInfo.saslMechanisms.addAll(saslMechanisms);

    return authInfo;
  }
}

