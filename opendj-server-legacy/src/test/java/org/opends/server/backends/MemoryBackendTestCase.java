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
package org.opends.server.backends;

import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;
import static org.opends.server.protocols.internal.Requests.newSearchRequest;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.DirectoryException;
import org.opends.server.workflowelement.localbackend.LocalBackendSearchOperation;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Test cases for the memory backend. */
@SuppressWarnings("javadoc")
public class MemoryBackendTestCase extends BackendTestCase
{
  private MemoryBackend backend;

  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
    backend = (MemoryBackend) TestCaseUtils.getServerContext().getBackendConfigManager()
        .getLocalBackendById(TestCaseUtils.TEST_BACKEND_ID);
  }

  /** A base DN this backend does not hold has no entry either. */
  @Test
  public void testSearchBaseDNNotHeldByBackend() throws Exception
  {
    assertSearchFailsWithNoSuchObject(DN.valueOf("o=not-held-by-this-backend"));
  }

  /** A base DN this backend holds, but which has never been added. */
  @Test
  public void testSearchBaseDNWithoutEntry() throws Exception
  {
    assertSearchFailsWithNoSuchObject(DN.valueOf("ou=missing,o=test"));
  }

  private void assertSearchFailsWithNoSuchObject(DN baseDN) throws Exception
  {
    final SearchRequest request = newSearchRequest(baseDN, SearchScope.BASE_OBJECT);
    final InternalSearchOperation search = new InternalSearchOperation(getRootConnection(), -1, -1, request);
    try
    {
      backend.search(new LocalBackendSearchOperation(search));
      fail("Searching base DN " + baseDN + " should have failed.");
    }
    catch (DirectoryException e)
    {
      assertEquals(e.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    }
  }
}
