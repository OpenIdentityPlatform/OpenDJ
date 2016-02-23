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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.util;

import org.forgerock.i18n.slf4j.LocalizedLogger;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.SortedSet;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

import static org.opends.messages.ExtensionMessages.INFO_MISSING_KEY_TYPE_IN_ALIASES;

/**
 * This class implements an X.509 key manager that will be used to wrap an
 * existing key manager and makes it possible to configure which certificate(s)
 * should be used for client and/or server operations.  The certificate
 * selection will be based on the alias (also called the nickname) of the
 * certificate.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class SelectableCertificateKeyManager
       extends X509ExtendedKeyManager
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The aliases of the certificates that should be selected from the key manager. */
  private final SortedSet<String> aliases;

  /** The key manager that is wrapped by this key manager. */
  private final X509KeyManager keyManager;

  /** Provide additional troubleshooting aid to localize a misconfigured SSL connection. */
  private final String componentName;

  private SelectableCertificateKeyManager(X509KeyManager keyManager, SortedSet<String> aliases, String componentName)
  {
    super();
    this.keyManager = keyManager;
    this.aliases = aliases;
    this.componentName = componentName;
  }

  private SelectableCertificateKeyManager(X509KeyManager keyManager, String alias)
  {
    super();
    this.keyManager = keyManager;
    this.aliases = CollectionUtils.newTreeSet(alias);
    this.componentName = "[unkown]";
  }

  /**
   * Chooses the alias of the client certificate that should be used based on
   * the provided criteria.  This will either return the preferred alias
   * configured for this key manager, or {@code null} if no client certificate
   * with that alias is configured in the underlying key manager.
   *
   * @param  keyType  The set of key algorithm names, ordered with the most
   *                  preferred key type first.
   * @param  issuers  The list of acceptable issuer subject names, or
   *                  {@code null} if any issuer may be used.
   * @param  socket   The socket to be used for this connection.
   *
   * @return  The alias configured for this key manager, or {@code null} if no
   *          such client certificate is available with that alias.
   */
  @Override
  public String chooseClientAlias(String[] keyType, Principal[] issuers,
                                  Socket socket)
  {
    return findClientAlias(keyType, issuers);
  }

  private String findClientAlias(String keyType[], Principal[] issuers)
  {
    for(String type : keyType)
    {
      final String clientAlias = findAlias(keyManager.getClientAliases(type, issuers));
      if ( clientAlias != null )
      {
        return clientAlias;
      }
    }
    logger.debug(INFO_MISSING_KEY_TYPE_IN_ALIASES, componentName, aliases.toString(), Arrays.toString(keyType));
    return null;
  }

  private String findAlias(String[] candidates)
  {
    if (candidates == null)
    {
      return null;
    }
    for (String alias : candidates)
    {
      for (String certificateAlias : aliases)
      {
        if (certificateAlias.equalsIgnoreCase(alias))
        {
          return alias;
        }
      }
    }
    return null;
  }

  /**
   * Chooses the alias of the client certificate that should be used based on
   * the provided criteria.  This will either return the preferred alias
   * configured for this key manager, or {@code null} if no client certificate
   * with that alias is configured in the underlying key manager.
   *
   * @param  keyType  The set of key algorithm names, ordered with the most
   *                  preferred key type first.
   * @param  issuers  The list of acceptable issuer subject names, or
   *                  {@code null} if any issuer may be used.
   * @param  engine   The SSL engine to be used for this connection.
   *
   * @return  The alias configured for this key manager, or {@code null} if no
   *          such client certificate is available with that alias.
   */
  @Override
  public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
                                        SSLEngine engine)
  {
    return findClientAlias(keyType, issuers);
  }

  /**
   * Chooses the alias of the server certificate that should be used based on
   * the provided criteria.  This will either return the preferred alias
   * configured for this key manager, or {@code null} if no server certificate
   * with that alias is configured in the underlying key manager.
   *
   * @param  keyType  The public key type for the certificate.
   * @param  issuers  The list of acceptable issuer subject names, or
   *                  {@code null} if any issuer may be used.
   * @param  socket   The socket to be used for this connection.
   *
   * @return  The alias configured for this key manager, or {@code null} if no
   *          such server certificate is available with that alias.
   */
  @Override
  public String chooseServerAlias(String keyType, Principal[] issuers,
                                  Socket socket)
  {
    return findServerAlias(new String[] { keyType }, issuers);
  }

  private String findServerAlias(String keyType[], Principal[] issuers)
  {
    for (String type : keyType)
    {
      final String serverAlias = findAlias(keyManager.getServerAliases(type, issuers));
      if (serverAlias != null)
      {
        return serverAlias;
      }
    }
    logger.debug(INFO_MISSING_KEY_TYPE_IN_ALIASES, componentName, aliases.toString(), Arrays.toString(keyType));
    return null;
  }

  /**
   * Chooses the alias of the server certificate that should be used based on
   * the provided criteria.  This will either return the preferred alias
   * configured for this key manager, or {@code null} if no server certificate
   * with that alias is configured in the underlying key manager.
   * Note that the returned alias can be transformed in lowercase, depending
   * on the KeyStore implementation. It is recommended not to use aliases in a
   * KeyStore that only differ in case.
   *
   * @param  keyType  The public key type for the certificate.
   * @param  issuers  The list of acceptable issuer subject names, or
   *                  {@code null} if any issuer may be used.
   * @param  engine   The SSL engine to be used for this connection.
   *
   * @return  The alias configured for this key manager, or {@code null} if no
   *          such server certificate is available with that alias.
   */
  @Override
  public String chooseEngineServerAlias(String keyType, Principal[] issuers,
                                        SSLEngine engine)
  {
    return findServerAlias(new String[] { keyType }, issuers);
   }

  /**
   * Retrieves the certificate chain for the provided alias.
   *
   * @param  alias  The alias for the certificate chain to retrieve.
   *
   * @return  The certificate chain for the provided alias, or {@code null} if
   *          no certificate is associated with the provided alias.
   */
  @Override
  public X509Certificate[] getCertificateChain(String alias)
  {
    return keyManager.getCertificateChain(alias);
  }

  /**
   * Retrieves the set of certificate aliases that may be used for client
   * authentication with the given public key type and set of issuers.
   *
   * @param  keyType  The public key type for the aliases to retrieve.
   * @param  issuers  The list of acceptable issuer subject names, or
   *                  {@code null} if any issuer may be used.
   *
   * @return  The set of certificate aliases that may be used for client
   *          authentication with the given public key type and set of issuers,
   *          or {@code null} if there were none.
   */
  @Override
  public String[] getClientAliases(String keyType, Principal[] issuers)
  {
    return keyManager.getClientAliases(keyType, issuers);
  }

  /**
   * Retrieves the private key for the provided alias.
   *
   * @param  alias  The alias for the private key to return.
   *
   * @return  The private key for the provided alias, or {@code null} if no
   *          private key is available for the provided alias.
   */
  @Override
  public PrivateKey getPrivateKey(String alias)
  {
    return keyManager.getPrivateKey(alias);
  }

  /**
   * Retrieves the set of certificate aliases that may be used for server
   * authentication with the given public key type and set of issuers.
   *
   * @param  keyType  The public key type for the aliases to retrieve.
   * @param  issuers  The list of acceptable issuer subject names, or
   *                  {@code null} if any issuer may be used.
   *
   * @return  The set of certificate aliases that may be used for server
   *          authentication with the given public key type and set of issuers,
   *          or {@code null} if there were none.
   */
  @Override
  public String[] getServerAliases(String keyType, Principal[] issuers)
  {
    return keyManager.getServerAliases(keyType, issuers);
  }

  /**
   * Wraps the provided set of key managers in selectable certificate key
   * managers using the provided alias.
   *
   * @param  keyManagers      The set of key managers to be wrapped.
   * @param  aliases          The aliases to use for selecting the desired
   *                          certificate.
   * @param  componentName    Name of the component to which is associated this key manager
   *
   * @return  A key manager array
   */
  public static KeyManager[] wrap(KeyManager[] keyManagers,
                                  SortedSet<String> aliases, String componentName)
  {
    final KeyManager[] newKeyManagers = new KeyManager[keyManagers.length];
    for (int i=0; i < keyManagers.length; i++)
    {
      newKeyManagers[i] = new SelectableCertificateKeyManager(
                                   (X509KeyManager) keyManagers[i], aliases, componentName);
    }

    return newKeyManagers;
  }

  /**
   * Wraps the provided set of key managers in selectable certificate key
   * managers using the provided alias.
   *
   * @param  keyManagers      The set of key managers to be wrapped.
   * @param  aliases            The aliases to use for selecting the desired
   *                          certificate.
   *
   * @return  A key manager array
   */
  public static KeyManager[] wrap(KeyManager[] keyManagers, SortedSet<String> aliases) {
    return wrap(keyManagers, aliases, "[unknown]");
  }
}
