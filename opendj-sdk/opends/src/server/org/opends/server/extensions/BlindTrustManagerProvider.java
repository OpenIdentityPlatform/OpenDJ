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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.opends.server.api.TrustManagerProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
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
       extends TrustManagerProvider
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



  /**
   * Initializes this trust manager provider based on the information in the
   * provided configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this trust manager provider.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization as a
   *                           result of the server configuration.
   *
   * @throws  InitializationException  If a problem occurs during initialization
   *                                   that is not related to the server
   *                                   configuration.
   */
  public void initializeTrustManagerProvider(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {

    // No implementation is required.
  }



  /**
   * Performs any finalization that may be necessary for this trust manager
   * provider.
   */
  public void finalizeTrustManagerProvider()
  {

    // No implementation is required.
  }



  /**
   * Retrieves a <CODE>TrustManager</CODE> object that may be used for
   * interactions requiring access to a trust manager.
   *
   * @return  A <CODE>TrustManager</CODE> object that may be used for
   *          interactions requiring access to a trust manager.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to obtain
   *                              the set of trust managers.
   */
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
  public X509Certificate[] getAcceptedIssuers()
  {

    return new X509Certificate[0];
  }
}

