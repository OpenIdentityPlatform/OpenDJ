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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.core;

import static org.testng.Assert.assertFalse;

import java.lang.reflect.Constructor;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class AccessControlConfigManagerTest extends CoreTestCase
{
  /**
   * Until initializeAccessControl() has run, the manager holds no configuration and its handler
   * permits everything: access control is not enabled, which a sorted search asks of every caller
   * (GHSA-6f6q-q9gw-4jr4), rather than an error.
   */
  @Test
  public void accessControlIsNotEnabledBeforeItIsInitialized() throws Exception
  {
    // The server's own manager has been initialized: only a manager of its own has not.
    final Constructor<AccessControlConfigManager> constructor =
        AccessControlConfigManager.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    assertFalse(constructor.newInstance().isAccessControlEnabled());
  }
}
