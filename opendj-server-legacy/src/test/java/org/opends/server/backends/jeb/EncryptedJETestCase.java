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
package org.opends.server.backends.jeb;

import org.forgerock.opendj.server.config.server.JEBackendCfg;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;

/** Encrypted {@link JEBackend} Tester. */
@Test
public class EncryptedJETestCase extends JETestCase
{
  @Override
  protected JEBackendCfg createBackendCfg()
  {
    JEBackendCfg backendCfg = super.createBackendCfg();
    when(backendCfg.getBackendId()).thenReturn("EncJETestCase");
    when(backendCfg.getDBDirectory()).thenReturn("EncJETestCase");
    when(backendCfg.isConfidentialityEnabled()).thenReturn(true);
    when(backendCfg.getCipherKeyLength()).thenReturn(128);
    when(backendCfg.getCipherTransformation()).thenReturn("AES/CBC/PKCS5Padding");
    return backendCfg;
  }
}
