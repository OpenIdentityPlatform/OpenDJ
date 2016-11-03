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
package org.opends.server.core;

import java.util.ArrayList;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.LocalBackend;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * A set of generic test cases that cover adding, modifying, and removing
 * Directory Server backends.
 */
public class BackendConfigManagerTestCase
       extends CoreTestCase
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
  }

  /**
   * Tests that the server will reject an attempt to register a base DN that is
   * already defined in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRegisterBaseThatAlreadyExists() throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);

    DN baseDN = DN.valueOf("o=test");
    String backendID = createBackendID(baseDN);
    Entry backendEntry = createBackendEntry(backendID, false, baseDN);

    AddOperation addOperation = getRootConnection().processAdd(backendEntry);
    assertNotEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests that the server will reject an attempt to deregister a base DN that
   * is not defined in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { DirectoryException.class })
  public void testDeregisterNonExistentBaseDN() throws Exception
  {
    getBackendConfigManager()
      .deregisterBaseDN(DN.valueOf("o=unregistered"));
  }



  /**
   * Tests that the server will reject an attempt to register a base DN using a
   * backend with a backend ID that is already defined in the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testRegisterBackendIDThatAlreadyExists() throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);

    DN baseDN = DN.valueOf("o=test");
    String backendID = "test";
    Entry backendEntry = createBackendEntry(backendID, false, baseDN);

    AddOperation addOperation = getRootConnection().processAdd(backendEntry);
    assertNotEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the ability of the server to create and remove a backend that is
   * never enabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAndRemoveDisabledBackend() throws Exception
  {
    DN baseDN = DN.valueOf("o=bcmtest");
    String backendID = createBackendID(baseDN);
    Entry backendEntry = createBackendEntry(backendID, false, baseDN);

    processAdd(backendEntry);
    assertNull(getBackendConfigManager().getLocalBackend(backendID));
    assertNull(getBackendConfigManager().getLocalBackendWithBaseDN(baseDN));

    DeleteOperation deleteOperation = getRootConnection().processDelete(backendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the ability of the server to create and remove a backend that is
   * enabled.  It will also test the ability of that backend to hold entries.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAndRemoveEnabledBackend() throws Exception
  {
    DN baseDN = DN.valueOf("o=bcmtest");
    String backendID = createBackendID(baseDN);
    Entry backendEntry = createBackendEntry(backendID, true, baseDN);

    processAdd(backendEntry);

    LocalBackend<?> backend = getBackendConfigManager().getLocalBackend(backendID);
    assertBackend(baseDN, backend);
    createEntry(baseDN, backend);

    DeleteOperation deleteOperation = getRootConnection().processDelete(backendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackend(backendID));
  }



  /**
   * Tests the ability of the server to create a backend that is disabled and
   * then enable it through a configuration change, and then subsequently
   * disable it.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testEnableAndDisableBackend() throws Exception
  {
    // Create the backend and make it disabled.
    DN baseDN = DN.valueOf("o=bcmtest");
    String backendID = createBackendID(baseDN);
    Entry backendEntry = createBackendEntry(backendID, false, baseDN);

    processAdd(backendEntry);
    assertNull(getLocalBackend(backendID));
    assertFalse(DirectoryServer.isNamingContext(baseDN));

    // Modify the backend to enable it.
    enableBackend(backendEntry, true);

    LocalBackend<?> backend = getLocalBackend(backendID);
    assertBackend(baseDN, backend);
    createEntry(baseDN, backend);

    // Modify the backend to disable it.
    enableBackend(backendEntry, false);
    assertNull(getLocalBackend(backendID));
    assertFalse(DirectoryServer.entryExists(baseDN));
    assertFalse(DirectoryServer.isNamingContext(baseDN));


    // Delete the disabled backend.
    DeleteOperation deleteOperation = getRootConnection().processDelete(backendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }

  private LocalBackend<?> getLocalBackend(String backendID)
  {
    return getServerContext().getBackendConfigManager().getLocalBackend(backendID);
  }



  /**
   * Tests the ability of the Directory Server to work properly when adding
   * nested backends in which the parent is added first and the child second.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNestedBackendParentFirst() throws Exception
  {
    // Create the parent backend and the corresponding base entry.
    DN parentBaseDN = DN.valueOf("o=parent");
    String parentBackendID = createBackendID(parentBaseDN);
    Entry parentBackendEntry = createBackendEntry(parentBackendID, true,
                                                  parentBaseDN);
    processAdd(parentBackendEntry);

    LocalBackend<?> parentBackend = getBackendConfigManager().getLocalBackend(parentBackendID);
    assertBackend(parentBaseDN, parentBackend);
    createEntry(parentBaseDN, parentBackend);


    // Create the child backend and the corresponding base entry.
    DN childBaseDN = DN.valueOf("ou=child,o=parent");
    String childBackendID = createBackendID(childBaseDN);
    Entry childBackendEntry = createBackendEntry(childBackendID, true,
                                                 childBaseDN);
    processAdd(childBackendEntry);

    LocalBackend<?> childBackend = getBackendConfigManager().getLocalBackend(childBackendID);
    assertNotNull(childBackend);
    assertEquals(childBackend, getBackendConfigManager().getLocalBackendWithBaseDN(childBaseDN));
    assertNotNull(childBackend.getParentBackend());
    assertEquals(parentBackend, childBackend.getParentBackend());
    assertEquals(parentBackend.getSubordinateBackends().length, 1);
    assertFalse(childBackend.entryExists(childBaseDN));
    assertFalse(DirectoryServer.isNamingContext(childBaseDN));

    createEntry(childBaseDN, childBackend);


    InternalClientConnection conn = getRootConnection();
    // Make sure that both entries exist.
    final SearchRequest request = newSearchRequest(parentBaseDN, SearchScope.WHOLE_SUBTREE);
    InternalSearchOperation internalSearch = conn.processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);
    assertEquals(internalSearch.getSearchEntries().size(), 2);



    // Make sure that we can't remove the parent backend with the child still in place.
    DeleteOperation deleteOperation = conn.processDelete(parentBackendEntry.getName());
    assertNotEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNotNull(getBackendConfigManager().getLocalBackend(parentBackendID));

    // Delete the child and then delete the parent.
    deleteOperation = conn.processDelete(childBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackend(childBackendID));
    assertEquals(parentBackend.getSubordinateBackends().length, 0);

    deleteOperation = conn.processDelete(parentBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackend(parentBackendID));
  }

  private BackendConfigManager getBackendConfigManager()
  {
    return TestCaseUtils.getServerContext().getBackendConfigManager();
  }



  /**
   * Tests the ability of the Directory Server to work properly when adding
   * nested backends in which the child is added first and the parent second.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNestedBackendChildFirst() throws Exception
  {
    // Create the child backend and the corresponding base entry (at the time
    // of the creation, it will be a naming context).
    DN childBaseDN = DN.valueOf("ou=child,o=parent");
    String childBackendID = createBackendID(childBaseDN);
    Entry childBackendEntry = createBackendEntry(childBackendID, true,
                                                 childBaseDN);
    processAdd(childBackendEntry);

    LocalBackend<?> childBackend = getBackendConfigManager().getLocalBackend(childBackendID);
    assertBackend(childBaseDN, childBackend);
    createEntry(childBaseDN, childBackend);
    assertTrue(DirectoryServer.isNamingContext(childBaseDN));


    // Create the parent backend and the corresponding entry (and verify that
    // its DN is now a naming context and the child's is not).
    DN parentBaseDN = DN.valueOf("o=parent");
    String parentBackendID = createBackendID(parentBaseDN);
    Entry parentBackendEntry = createBackendEntry(parentBackendID, true,
                                                  parentBaseDN);
    processAdd(parentBackendEntry);

    LocalBackend<?> parentBackend = getBackendConfigManager().getLocalBackend(parentBackendID);
    assertNotNull(parentBackend);
    assertEquals(parentBackend, getBackendConfigManager().getLocalBackendWithBaseDN(parentBaseDN));
    assertNotNull(childBackend.getParentBackend());
    assertEquals(parentBackend, childBackend.getParentBackend());
    assertEquals(parentBackend.getSubordinateBackends().length, 1);

    createEntry(parentBaseDN, parentBackend);
    assertTrue(DirectoryServer.isNamingContext(parentBaseDN));
    assertFalse(DirectoryServer.isNamingContext(childBaseDN));


    // Verify that we can see both entries with a subtree search.
    final SearchRequest request = newSearchRequest(parentBaseDN, SearchScope.WHOLE_SUBTREE);
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);
    assertEquals(internalSearch.getSearchEntries().size(), 2);


    // Delete the backends from the server.
    DeleteOperation deleteOperation = getRootConnection().processDelete(childBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackend(childBackendID));
    assertEquals(parentBackend.getSubordinateBackends().length, 0);

    deleteOperation = getRootConnection().processDelete(parentBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackend(parentBackendID));
  }

  private void assertBackend(DN baseDN, LocalBackend<?> backend) throws DirectoryException
  {
    assertNotNull(backend);
    assertEquals(backend, getBackendConfigManager().getLocalBackendWithBaseDN(baseDN));
    assertFalse(backend.entryExists(baseDN));
    assertNull(backend.getParentBackend());
    assertEquals(backend.getSubordinateBackends().length, 0);
    assertFalse(backend.entryExists(baseDN));
    assertTrue(DirectoryServer.isNamingContext(baseDN));
  }

  /**
   * Tests the ability of the Directory Server to work properly when inserting
   * an intermediate backend between a parent backend and an existing nested
   * backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testInsertIntermediateBackend() throws Exception
  {
    // Add the parent backend to the server and its corresponding base entry.
    DN parentBaseDN = DN.valueOf("o=parent");
    String parentBackendID = createBackendID(parentBaseDN);
    Entry parentBackendEntry = createBackendEntry(parentBackendID, true,
                                                  parentBaseDN);
    processAdd(parentBackendEntry);

    LocalBackend<?> parentBackend = getBackendConfigManager().getLocalBackend(parentBackendID);
    assertBackend(parentBaseDN, parentBackend);
    createEntry(parentBaseDN, parentBackend);
    assertTrue(DirectoryServer.isNamingContext(parentBaseDN));


    // Add the grandchild backend to the server.
    DN grandchildBaseDN = DN.valueOf("ou=grandchild,ou=child,o=parent");
    String grandchildBackendID = createBackendID(grandchildBaseDN);
    Entry grandchildBackendEntry = createBackendEntry(grandchildBackendID, true,
                                                      grandchildBaseDN);
    processAdd(grandchildBackendEntry);

    LocalBackend<?> grandchildBackend = getBackendConfigManager().getLocalBackend(grandchildBackendID);
    assertNotNull(grandchildBackend);
    assertEquals(grandchildBackend, getBackendConfigManager().getLocalBackendWithBaseDN(grandchildBaseDN));
    assertNotNull(grandchildBackend.getParentBackend());
    assertEquals(grandchildBackend.getParentBackend(), parentBackend);
    assertEquals(parentBackend.getSubordinateBackends().length, 1);
    assertFalse(grandchildBackend.entryExists(grandchildBaseDN));

    // Verify that we can't create the grandchild base entry because its parent
    // doesn't exist.
    Entry e = StaticUtils.createEntry(grandchildBaseDN);
    AddOperation addOperation = getRootConnection().processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    assertFalse(grandchildBackend.entryExists(grandchildBaseDN));


    // Add the child backend to the server and create its base entry.
    DN childBaseDN = DN.valueOf("ou=child,o=parent");
    String childBackendID = createBackendID(childBaseDN);
    Entry childBackendEntry = createBackendEntry(childBackendID, true,
                                                 childBaseDN);
    processAdd(childBackendEntry);

    LocalBackend<?> childBackend = getBackendConfigManager().getLocalBackend(childBackendID);
    createBackend(childBaseDN, childBackend, parentBackend, grandchildBackend);
    createEntry(childBaseDN, childBackend);

    // Now we can create the grandchild base entry.
    createEntry(grandchildBaseDN, grandchildBackend);

    InternalClientConnection conn = getRootConnection();
    // Verify that a subtree search can see all three entries.
    final SearchRequest request = newSearchRequest(parentBaseDN, SearchScope.WHOLE_SUBTREE);
    assertSearchResultsSize(request, 3);

    // Disable the intermediate (child) backend.  This should be allowed.
    enableBackend(childBackendEntry, false);

    assertSearchResultsSize(request, 2);


    // Re-enable the intermediate backend.
    enableBackend(childBackendEntry, true);


    // Update our reference to the child backend since the old one is no longer
    // valid, and make sure that it got re-inserted back into the same place in
    // the hierarchy.
    childBackend = getBackendConfigManager().getLocalBackend(childBackendID);
    createBackend(childBaseDN, childBackend, parentBackend, grandchildBackend);


    // Since the memory backend that we're using for this test doesn't retain
    // entries across stops and restarts, a subtree search below the parent
    // should still only return two entries, which means that it's going through
    // the entire chain of backends.
    assertSearchResultsSize(request, 2);


    // Add the child entry back into the server to get things back to the way
    // they were before we disabled the backend.
    createEntry(childBaseDN, childBackend);


    // We should again be able to see all three entries when performing a search
    assertSearchResultsSize(request, 3);


    // Get rid of the entries in the proper order.
    DeleteOperation deleteOperation =
         conn.processDelete(grandchildBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackend(grandchildBackendID));
    assertEquals(childBackend.getSubordinateBackends().length, 0);
    assertEquals(parentBackend.getSubordinateBackends().length, 1);

    deleteOperation = conn.processDelete(childBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackend(childBackendID));
    assertEquals(parentBackend.getSubordinateBackends().length, 0);

    deleteOperation = conn.processDelete(parentBackendEntry.getName());
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertNull(getBackendConfigManager().getLocalBackend(parentBackendID));
  }

  private void enableBackend(Entry entry, boolean enabled)
  {
    ModifyRequest modifyRequest = newModifyRequest(entry.getName())
        .addModification(REPLACE, "ds-cfg-enabled", Boolean.toString(enabled));
    ModifyOperation modifyOperation = getRootConnection().processModify(modifyRequest);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  private void assertSearchResultsSize(final SearchRequest request, int expected)
  {
    InternalSearchOperation internalSearch = getRootConnection().processSearch(request);
    assertEquals(internalSearch.getResultCode(), ResultCode.SUCCESS);
    assertEquals(internalSearch.getSearchEntries().size(), expected);
  }

  private void createBackend(DN childBaseDN, LocalBackend<?> childBackend, LocalBackend<?> parentBackend,
      LocalBackend<?> grandchildBackend) throws DirectoryException
  {
    assertNotNull(childBackend);
    assertEquals(childBackend, getBackendConfigManager().getLocalBackendWithBaseDN(childBaseDN));
    assertNotNull(childBackend.getParentBackend());
    assertEquals(parentBackend, childBackend.getParentBackend());
    assertEquals(parentBackend.getSubordinateBackends().length, 1);
    assertFalse(childBackend.entryExists(childBaseDN));
    assertEquals(childBackend.getSubordinateBackends().length, 1);
    assertEquals(childBackend.getSubordinateBackends()[0], grandchildBackend);
    assertEquals(grandchildBackend.getParentBackend(), childBackend);
  }

  private void createEntry(DN baseDN, LocalBackend<?> backend) throws DirectoryException
  {
    Entry e = StaticUtils.createEntry(baseDN);
    processAdd(e);
    assertTrue(backend.entryExists(baseDN));
  }

  private void processAdd(Entry e)
  {
    AddOperation addOperation = getRootConnection().processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Creates an entry that may be used to add a new backend to the server.  It
   * will be an instance of the memory backend.
   *
   * @param  backendID  The backend ID to use for the backend.
   * @param  enabled    Indicates whether the backend should be enabled.
   * @param  baseDNs    The set of base DNs to use for the new backend.
   *
   * @return  An entry that may be used to add a new backend to the server.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private Entry createBackendEntry(String backendID, boolean enabled,
                                   DN... baseDNs)
          throws Exception
  {
    assertNotNull(baseDNs);
    assertFalse(baseDNs.length == 0);

    ArrayList<String> lines = new ArrayList<>();
    lines.add("dn: ds-cfg-backend-id=" + backendID + ",cn=Backends,cn=config");
    lines.add("objectClass: top");
    lines.add("objectClass: ds-cfg-backend");
    lines.add("objectClass: ds-cfg-memory-backend");
    lines.add("ds-cfg-backend-id: " + backendID);
    lines.add("ds-cfg-java-class: org.opends.server.backends.MemoryBackend");
    lines.add("ds-cfg-enabled: " + enabled);
    lines.add("ds-cfg-writability-mode: enabled");

    for (DN dn : baseDNs)
    {
      lines.add("ds-cfg-base-dn: " + dn);
    }

    String[] lineArray = new String[lines.size()];
    lines.toArray(lineArray);
    return TestCaseUtils.makeEntry(lineArray);
  }



  /**
   * Constructs a backend ID to use for a backend with the provided set of base
   * DNs.
   *
   * @param  baseDNs  The set of base DNs to use when creating the backend ID.
   *
   * @return  The constructed backend ID based on the given base DNs.
   */
  private String createBackendID(DN... baseDNs)
  {
    StringBuilder buffer = new StringBuilder();

    for (DN dn : baseDNs)
    {
      if (buffer.length() > 0)
      {
        buffer.append("___");
      }

      String ndn = dn.toNormalizedUrlSafeString();
      for (int i=0; i < ndn.length(); i++)
      {
        char c = ndn.charAt(i);
        if (Character.isLetterOrDigit(c))
        {
          buffer.append(c);
        }
        else
        {
          buffer.append('_');
        }
      }
    }

    return buffer.toString();
  }
}

