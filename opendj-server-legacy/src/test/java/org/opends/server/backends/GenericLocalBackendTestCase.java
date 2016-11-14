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
package org.opends.server.backends;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.LocalBackend.BackendOperation;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** A set of generic test cases that apply to all Directory Server backends. */
@SuppressWarnings("javadoc")
public class GenericLocalBackendTestCase extends BackendTestCase
{
  /** Ensures that the Directory Server is running. */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
  }

  /**
   * Retrieves the backends defined in the server.
   *
   * @return  The backends defined in the server.
   */
  @DataProvider(name = "backends")
  public Object[][] getLocalBackends()
  {
    List<LocalBackend<?>> backendList = new ArrayList<>(
        TestCaseUtils.getServerContext().getBackendConfigManager().getLocalBackends());
    Object[][] objectArray = new Object[backendList.size()][1];
    for (int i=0; i < objectArray.length; i++)
    {
      objectArray[i][0] = backendList.get(i);
    }
    return objectArray;
  }

  /** Tests the {@link LocalBackend#getBaseDNs} method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testGetBaseDNs(LocalBackend<?> b)
  {
    Set<DN> baseDNs = b.getBaseDNs();
    assertNotNull(baseDNs);
    assertNotEquals(baseDNs.size(), 0);
  }

  /** Tests the {@link LocalBackend#getSupportedControls} method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testGetSupportedControls(LocalBackend<?> b)
  {
    assertNotNull(b.getSupportedControls());
  }

  /** Tests the supports control method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testSupportsControl(LocalBackend<?> b)
  {
    assertFalse(b.supportsControl("1.2.3.4"));
  }

  /** Tests the {@link LocalBackend#getSupportedFeatures} method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testGetSupportedFeatures(LocalBackend<?> b)
  {
    assertNotNull(b.getSupportedFeatures());
    b.getSupportedFeatures();
  }

  /** Tests the supports LDIF export method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testSupportsLDIFExport(LocalBackend<?> b)
  {
    b.supports(BackendOperation.LDIF_EXPORT);
  }

  /** Tests the supports LDIF import method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testSupportsLDIFImport(LocalBackend<?> b)
  {
    b.supports(BackendOperation.LDIF_IMPORT);
  }

  /** Tests the supports Backup method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testSupportsBackup(LocalBackend<?> b)
  {
    b.supports(BackendOperation.BACKUP);
  }

  /** Tests the supports restore method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testSupportsRestore(LocalBackend<?> b)
  {
    b.supports(BackendOperation.RESTORE);
  }

  /** Tests the {@link LocalBackend#getBackendID} method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testGetBackendID(LocalBackend<?> b)
  {
    assertNotNull(b.getBackendID());
    assertTrue(b.getBackendID().length() > 0);
  }

  /** Tests the {@link LocalBackend#isPrivateBackend} method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testIsPrivateBackend(LocalBackend<?> b)
  {
    b.isPrivateBackend();
  }

  /** Tests the {@link LocalBackend#getWritabilityMode} method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testGetWritabilityMode(LocalBackend<?> b)
  {
    assertNotNull(b.getWritabilityMode());
  }

  /** Tests the {@link LocalBackend#getBackendMonitor} method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testGetBackendMonitor(LocalBackend<?> b)
  {
    assertNotNull(b.getBackendMonitor());
  }

  /** Tests the {@link LocalBackend#getEntryCount} method for the provided backend. */
  @Test(dataProvider = "backends")
  public void testGetEntryCount(LocalBackend<?> b)
  {
    b.getEntryCount();
  }

  /**
   * Tests the {@link LocalBackend#handlesEntry} method for the provided backend for each of the declared
   * base DNs.
   */
  @Test(dataProvider = "backends")
  public void testHandlesEntry(LocalBackend<?> b) throws Exception
  {
    for (DN baseDN : b.getBaseDNs())
    {
      assertTrue(b.handlesEntry(baseDN));
      assertTrue(b.handlesEntry(DN.valueOf("cn=child," + baseDN)));
    }
  }
}
