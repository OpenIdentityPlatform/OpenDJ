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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.extensions;

import javax.net.ssl.TrustManager;

import org.opends.server.admin.std.server.TrustManagerProviderCfg;
import org.opends.server.api.TrustManagerProvider;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This class provides an implementation of a trust manager provider that does
 * not actually have the ability to provide a trust manager.  It will be used
 * when no other trust manager provider has been defined in the server
 * configuration.
 */
public class NullTrustManagerProvider
       extends TrustManagerProvider<TrustManagerProviderCfg>
{
  /**
   * Creates a new instance of this null trust manager provider.  The
   * <CODE>initializeTrustManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public NullTrustManagerProvider()
  {
    // No implementation is required.
  }



  /** {@inheritDoc} */
  public void initializeTrustManagerProvider(
                     TrustManagerProviderCfg configuration)
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
    return new TrustManager[0];
  }
}

