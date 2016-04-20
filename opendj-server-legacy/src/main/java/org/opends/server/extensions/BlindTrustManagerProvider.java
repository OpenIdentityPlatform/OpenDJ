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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.forgerock.opendj.server.config.server.BlindTrustManagerProviderCfg;
import org.opends.server.api.TrustManagerProvider;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This class provides an implementation of a trust manager provider that will
 * indicate that any certificate presented should be blindly trusted by the
 * Directory Server.  This can provide convenience and ease of use, but that
 * added convenience will be at the expense of security and therefore it should
 * not be used in environments in which the clients may not be considered
 * trustworthy.
 */
public class BlindTrustManagerProvider
       extends TrustManagerProvider<BlindTrustManagerProviderCfg>
       implements X509TrustManager
{
  /**
   * Creates a new instance of this blind trust manager provider.  The
   * <CODE>initializeTrustManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public BlindTrustManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public void initializeTrustManagerProvider(
                  BlindTrustManagerProviderCfg configuration)
         throws ConfigException, InitializationException
  {
    // No implementation is required.
  }

  @Override
  public void finalizeTrustManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public TrustManager[] getTrustManagers()
         throws DirectoryException
  {
    return new TrustManager[] { this };
  }

  /**
   * Determines whether an SSL client with the provided certificate chain should
   * be trusted.  In this case, all client certificates will be trusted.
   *
   * @param  chain     The certificate chain for the SSL client.
   * @param  authType  The authentication type based on the client certificate.
   */
  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
  {
    // As long as we don't throw an exception, then the client certificate will
    // be considered trusted.
  }

  /**
   * Determines whether an SSL server with the provided certificate chain should
   * be trusted.  In this case, all server certificates will be trusted.
   *
   * @param  chain     The certificate chain for the SSL server.
   * @param  authType  The key exchange algorithm used.
   */
  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
  {
    // As long as we don't throw an exception, then the server certificate will
    // be considered trusted.
  }

  /**
   * Retrieves the set of certificate authority certificates which are trusted
   * for authenticating peers.
   *
   * @return  An empty array, since we don't care what certificates are
   *          presented because we will trust them all.
   */
  @Override
  public X509Certificate[] getAcceptedIssuers()
  {
    return new X509Certificate[0];
  }
}
