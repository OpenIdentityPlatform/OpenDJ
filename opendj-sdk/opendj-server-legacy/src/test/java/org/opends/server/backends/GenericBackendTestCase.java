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
package org.opends.server.backends;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.util.ArrayList;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * A set of generic test cases that apply to all Directory Server backends.
 */
public class GenericBackendTestCase
       extends BackendTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
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
  public Object[][] getBackends()
  {
    ArrayList<Backend> backendList = new ArrayList<Backend>(DirectoryServer.getBackends().values());
    Object[][] objectArray = new Object[backendList.size()][1];
    for (int i=0; i < objectArray.length; i++)
    {
      objectArray[i][0] = backendList.get(i);
    }

    return objectArray;
  }



  /**
   * Tests the {@code getBaseDNs} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testGetBaseDNs(Backend<?> b)
  {
    DN[] baseDNs = b.getBaseDNs();
    assertNotNull(baseDNs);
    assertNotEquals(baseDNs.length, 0);
  }



  /**
   * Tests the {@code getSupportedControls} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testGetSupportedControls(Backend<?> b)
  {
    assertNotNull(b.getSupportedControls());
  }



  /**
   * Tests the {@code supportsControl} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testSupportsControl(Backend<?> b)
  {
    assertFalse(b.supportsControl("1.2.3.4"));
  }



  /**
   * Tests the {@code getSupportedFeatures} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testGetSupportedFeatures(Backend<?> b)
  {
    assertNotNull(b.getSupportedFeatures());
    b.getSupportedFeatures();
  }



  /**
   * Tests the {@code supportsLDIFExport} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testSupportsLDIFExport(Backend<?> b)
  {
    b.supports(BackendOperation.LDIF_EXPORT);
  }



  /**
   * Tests the {@code supportsLDIFImport} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testSupportsLDIFImport(Backend<?> b)
  {
    b.supports(BackendOperation.LDIF_IMPORT);
  }



  /**
   * Tests the {@code supportsBackup} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testSupportsBackup(Backend<?> b)
  {
    b.supports(BackendOperation.BACKUP);
  }



  /**
   * Tests the {@code supportsRestore} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testSupportsRestore(Backend<?> b)
  {
    b.supports(BackendOperation.RESTORE);
  }



  /**
   * Tests the {@code getBackendID} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testGetBackendID(Backend<?> b)
  {
    assertNotNull(b.getBackendID());
    assertTrue(b.getBackendID().length() > 0);
  }



  /**
   * Tests the {@code isPrivateBackend} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testIsPrivateBackend(Backend<?> b)
  {
    b.isPrivateBackend();
  }



  /**
   * Tests the {@code getWritabilityMode} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testGetWritabilityMode(Backend<?> b)
  {
    assertNotNull(b.getWritabilityMode());
  }



  /**
   * Tests the {@code getBackendMonitor} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testGetBackendMonitor(Backend<?> b)
  {
    assertNotNull(b.getBackendMonitor());
  }



  /**
   * Tests the {@code getEntryCount} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testGetEntryCount(Backend<?> b)
  {
    b.getEntryCount();
  }



  /**
   * Tests the {@code getParentBackend} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testGetParentBackend(Backend<?> b)
  {
    b.getParentBackend();
  }



  /**
   * Tests the {@code getSubordinateBackends} method for the provided backend.
   *
   * @param  b  The backend to test.
   */
  @Test(dataProvider = "backends")
  public void testGetSubordinateBackends(Backend<?> b)
  {
    assertNotNull(b.getSubordinateBackends());
  }



  /**
   * Tests the {@code handlesEntry} method for the provided backend for each
   * of the declared base DNs.
   *
   * @param  b  The backend to test.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "backends")
  public void testHandlesEntry(Backend<?> b) throws Exception
  {
    for (DN baseDN : b.getBaseDNs())
    {
      assertTrue(b.handlesEntry(baseDN));
      assertTrue(b.handlesEntry(DN.valueOf("cn=child," + baseDN)));
    }
  }
}
