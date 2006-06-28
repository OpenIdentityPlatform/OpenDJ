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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import javax.net.ssl.TrustManager;

import org.opends.server.api.TrustManagerProvider;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryException;
import org.opends.server.core.InitializationException;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class provides an implementation of a trust manager provider that does
 * not actually have the ability to provide a trust manager.  It will be used
 * when no other trust manager provider has been defined in the server
 * configuration.
 */
public class NullTrustManagerProvider
       extends TrustManagerProvider
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.NullTrustManagerProvider";



  /**
   * Creates a new instance of this null trust manager provider.  The
   * <CODE>initializeTrustManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public NullTrustManagerProvider()
  {
    assert debugConstructor(CLASS_NAME);

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
    assert debugEnter(CLASS_NAME, "initializeTrustManagerProvider",
                      String.valueOf(configEntry));

    // No implementation is required.
  }



  /**
   * Performs any finalization that may be necessary for this trust manager
   * provider.
   */
  public void finalizeTrustManagerProvider()
  {
    assert debugEnter(CLASS_NAME, "finalizeTrustManagerProvider");

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
    assert debugEnter(CLASS_NAME, "getTrustManagers");

    return new TrustManager[0];
  }
}

