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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.util;



import org.forgerock.i18n.slf4j.LocalizedLogger;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

import static org.opends.messages.ExtensionMessages.INFO_KEYSTORE_DOES_NOT_CONTAIN_ALIAS;


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
  /** The alias of the certificate that should be selected from the key manager. */
  private final String alias;

  /** The key manager that is wrapped by this key manager. */
  private final X509KeyManager keyManager;

  /** Provide additional troubleshooting aid to localize a misconfigured SSL connection. */
  private final String componentName;

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  /**
   * Creates a new instance of this key manager that will wrap the provided key
   * manager and use the certificate with the specified alias.
   *
   * @param  keyManager       The key manager to be wrapped by this key manager.
   * @param  alias            The nickname of the certificate that should be
   *                          selected for operations involving this key manager.
   * @param  componentName    Name of the component to which is associated this key manager
   */
  public SelectableCertificateKeyManager(X509KeyManager keyManager,
                                         String alias, String componentName)
  {
    super();

    this.keyManager = keyManager;
    this.alias      = alias;
    this.componentName       = componentName;
  }

  /**
   * Creates a new instance of this key manager that will wrap the provided key
   * manager and use the certificate with the specified alias.
   *
   * @param  keyManager  The key manager to be wrapped by this key manager.
   * @param  alias       The nickname of the certificate that should be
   *                     selected for operations involving this key manager.
   */
  public SelectableCertificateKeyManager(X509KeyManager keyManager,
                                         String alias)
  {
    this(keyManager, alias, "[unknown]");
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
  public String chooseClientAlias(String[] keyType, Principal[] issuers,
                                  Socket socket)
  {
    for (String type : keyType)
    {
      String[] clientAliases = keyManager.getClientAliases(type, issuers);
      if (clientAliases != null)
      {
        for (String clientAlias : clientAliases)
        {
          if (clientAlias.equals(alias))
          {
            return alias;
          }
        }
      }
    }
    logger.warn(INFO_KEYSTORE_DOES_NOT_CONTAIN_ALIAS, componentName, keyType, alias);
    return null;
  }



  /**
   * Chooses the alias of the client certificate that should be used based on
   * the provided critieria.  This will either return the preferred alias
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
    for (String type : keyType)
    {
      String[] clientAliases = keyManager.getClientAliases(type, issuers);
      if (clientAliases != null)
      {
        for (String clientAlias : clientAliases)
        {
          if (clientAlias.equals(alias))
          {
            return alias;
          }
        }
      }
    }

    logger.warn(INFO_KEYSTORE_DOES_NOT_CONTAIN_ALIAS, componentName, keyType, alias);
    return null;
  }



  /**
   * Chooses the alias of the server certificate that should be used based on
   * the provided critieria.  This will either return the preferred alias
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
  public String chooseServerAlias(String keyType, Principal[] issuers,
                                  Socket socket)
  {
    String[] serverAliases = keyManager.getServerAliases(keyType, issuers);
    if (serverAliases != null)
    {
      for (String serverAlias : serverAliases)
      {
        if (serverAlias.equals(alias))
        {
          return alias;
        }
      }
    }

    logger.warn(INFO_KEYSTORE_DOES_NOT_CONTAIN_ALIAS, componentName, keyType, alias);
    return null;
  }



  /**
   * Chooses the alias of the server certificate that should be used based on
   * the provided critieria.  This will either return the preferred alias
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
    String[] serverAliases = keyManager.getServerAliases(keyType, issuers);
    if (serverAliases != null)
    {
      for (String serverAlias : serverAliases)
      {
        if (serverAlias.equalsIgnoreCase(alias))
        {
          return serverAlias;
        }
      }
    }

    logger.warn(INFO_KEYSTORE_DOES_NOT_CONTAIN_ALIAS, componentName, keyType, alias);
    return null;
  }



  /**
   * Retrieves the certificate chain for the provided alias.
   *
   * @param  alias  The alias for the certificate chain to retrieve.
   *
   * @return  The certificate chain for the provided alias, or {@code null} if
   *          no certificate is associated with the provided alias.
   */
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
  public String[] getServerAliases(String keyType, Principal[] issuers)
  {
    return keyManager.getServerAliases(keyType, issuers);
  }



  /**
   * Wraps the provided set of key managers in selectable certificate key
   * managers using the provided alias.
   *
   * @param  keyManagers      The set of key managers to be wrapped.
   * @param  alias            The alias to use for selecting the desired
   *                          certificate.
   * @param  componentName    Name of the component to which is associated this key manager
   *
   * @return  A key manager array
   */
  public static X509ExtendedKeyManager[] wrap(KeyManager[] keyManagers,
                                              String alias, String componentName)
  {
    X509ExtendedKeyManager[] newKeyManagers =
         new X509ExtendedKeyManager[keyManagers.length];
    for (int i=0; i < keyManagers.length; i++)
    {
      newKeyManagers[i] = new SelectableCertificateKeyManager(
                                   (X509KeyManager) keyManagers[i], alias, componentName);
    }

    return newKeyManagers;
  }

  /**
   * Wraps the provided set of key managers in selectable certificate key
   * managers using the provided alias.
   *
   * @param  keyManagers      The set of key managers to be wrapped.
   * @param  alias            The alias to use for selecting the desired
   *                          certificate.
   *
   * @return  A key manager array
   */
  public static X509ExtendedKeyManager[] wrap(KeyManager[] keyManagers, String alias) {
    return wrap(keyManagers, alias, "[unknown]");
  }
}

