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

import javax.net.ssl.KeyManager;

import org.forgerock.opendj.server.config.server.KeyManagerProviderCfg;
import org.opends.server.api.KeyManagerProvider;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * This class provides an implementation of a key manager provider that does not
 * actually have the ability to provide a key manager.  It will be used when no
 * other key manager provider has been defined in the server configuration.
 */
public class NullKeyManagerProvider
       extends KeyManagerProvider<KeyManagerProviderCfg>{
  /**
   * Creates a new instance of this null key manager provider.  The
   * <CODE>initializeKeyManagerProvider</CODE> method must be called on the
   * resulting object before it may be used.
   */
  public NullKeyManagerProvider()
  {
    // No implementation is required.
  }

  @Override
  public void initializeKeyManagerProvider(
      KeyManagerProviderCfg configuration) throws ConfigException,
      InitializationException {
    // No implementation is required.
  }

  @Override
  public void finalizeKeyManagerProvider()
  {
    // No implementation is required.
  }

  /**
   * Retrieves a <CODE>KeyManager</CODE> object that may be used for
   * interactions requiring access to a key manager.
   *
   * @return  A <CODE>KeyManager</CODE> object that may be used for interactions
   *          requiring access to a key manager.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to obtain
   *                              the set of key managers.
   */
  @Override
  public KeyManager[] getKeyManagers()
         throws DirectoryException
  {
    return new KeyManager[0];
  }
}
