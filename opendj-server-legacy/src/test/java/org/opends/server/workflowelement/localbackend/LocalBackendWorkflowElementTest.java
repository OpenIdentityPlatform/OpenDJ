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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.workflowelement.localbackend;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.DirectoryException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/** This set of tests test the LocalBackendWorkflowElement. */
@SuppressWarnings("javadoc")
public class LocalBackendWorkflowElementTest extends DirectoryServerTestCase
{

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
  }

  /**
   * This test checks that workflows are updated as appropriate when backend
   * base DNs are added or removed.
   * <p>
   * When a new backend base DN is added, the new suffix should be accessible
   * for the route process - ie. a workflow should be created and be a potential
   * candidate for the route process.
   * <p>
   * Similarly, when a backend base DN is removed its associated workflow should
   * be removed; subsequently, any request targeting the removed suffix should
   * be rejected and a no such entry status code be returned.
   */
  @Test
  public void testBackendBaseDNModification() throws Exception
  {
    String suffix = "dc=example,dc=com";
    String suffix2 = "o=workflow suffix";
    String backendBaseDNName = "ds-cfg-base-dn";

    // Initialize a backend with a base entry.
    TestCaseUtils.clearBackend("userRoot", suffix);

    // Check that suffix is accessible while suffix2 is not.
    searchEntry(suffix, ResultCode.SUCCESS);
    searchEntry(suffix2, ResultCode.NO_SUCH_OBJECT);

    // Add a new suffix in the backend and create a base entry for the new suffix
    String backendConfigDN = "ds-cfg-backend-id=userRoot," + DN_BACKEND_BASE;
    modifyAttribute(backendConfigDN, ADD, backendBaseDNName, suffix2);
    addBaseEntry(suffix2, "workflow suffix");

    // Both old and new suffix should be accessible.
    searchEntry(suffix, ResultCode.SUCCESS);
    searchEntry(suffix2, ResultCode.SUCCESS);

    // Remove the new suffix...
    modifyAttribute(backendConfigDN, DELETE, backendBaseDNName, suffix2);

    // ...and check that the removed suffix is no more accessible.
    searchEntry(suffix, ResultCode.SUCCESS);
    searchEntry(suffix2, ResultCode.NO_SUCH_OBJECT);

    // Replace the suffix with suffix2 in the backend
    modifyAttribute(backendConfigDN, REPLACE, backendBaseDNName, suffix2);

    // Now none of the suffixes are accessible: this means the entries
    // under the old suffix are not moved to the new suffix.
    searchEntry(suffix, ResultCode.NO_SUCH_OBJECT);
    searchEntry(suffix2, ResultCode.NO_SUCH_OBJECT);

    // Add a base entry for the new suffix
    addBaseEntry(suffix2, "workflow suffix");

    // The new suffix is accessible while the old one is not.
    searchEntry(suffix, ResultCode.NO_SUCH_OBJECT);
    searchEntry(suffix2, ResultCode.SUCCESS);

    // Reset the configuration with previous suffix
    modifyAttribute(backendConfigDN, REPLACE, backendBaseDNName, suffix);
  }

  /**
   * This test checks that the workflow takes into account the subordinate
   * naming context defined in the RootDSEBackend.
   */
  @Test
  public void testNonRootDseSubordinateNamingContext() throws Exception
  {
    // Backends for the test
    String backendID1 = "test-dc-example-dc-com-subordinate1,dc=example,dc=com";
    String backendID2 = "test-dc-example-dc-com-subordinate2,dc=example,dc=com";
    String backend1 = "o=" + backendID1;
    String backend2 = "o=" + backendID2;

    try
    {
      TestCaseUtils.clearDataBackends();

      // At this point, the list of subordinate naming context is not defined
      // yet (null): any public backend should be visible. Create a backend
      // with a base entry and check that the test naming context is visible.
      TestCaseUtils.initializeMemoryBackend(backendID1, backend1, true);
      searchEntries("dc=example,dc=com", ResultCode.SUCCESS, 1);

      // Create another test backend and check that the new backend is visible
      TestCaseUtils.initializeMemoryBackend(backendID2, backend2, true);
      searchEntries("dc=example,dc=com", ResultCode.SUCCESS, 2);
    }
    finally
    {
      // Clean the test backends. There is no more naming context.
      TestCaseUtils.clearMemoryBackend(backendID1);
      TestCaseUtils.clearMemoryBackend(backendID2);
      searchEntries("dc=example,dc=com", ResultCode.NO_SUCH_OBJECT, 0);
    }
  }

  @Test
  public void testParentBackendSelection() throws Exception
  {
    String parentID = "parent";
    String childID1 = "child1";
    String strangerID = "stranger";
    String parent = "dc=abc";
    String child1 = "dc=example,dc=abc";
    String child2 = "dc=example2,dc=abc";
    String stranger = "dc=abc1";

    try
    {
      TestCaseUtils.clearDataBackends();
      TestCaseUtils.initializeMemoryBackend(parentID, parent, true);
      TestCaseUtils.initializeMemoryBackend(childID1, child1, true);
      TestCaseUtils.initializeMemoryBackend(strangerID, stranger, true);

      assertEquals(getMatchedDN("cn=user," + parent), DN.valueOf(parent));
      assertEquals(getMatchedDN("cn=user," + child1), DN.valueOf(child1));
      assertEquals(getMatchedDN("cn=user," + child2), DN.valueOf(parent));
      assertEquals(getMatchedDN("cn=user," + stranger), DN.valueOf(stranger));
    }
    finally
    {
      TestCaseUtils.clearMemoryBackend(parentID);
      TestCaseUtils.clearMemoryBackend(childID1);
      TestCaseUtils.clearMemoryBackend(strangerID);
    }
  }

  /**
   * This test checks that the workflow takes into account the subordinate
   * naming context defined in the RootDSEBackend.
   */
  @Test
  public void testRootDseSubordinateNamingContext() throws Exception
  {
    // Backends for the test
    String backend1 = "o=test-rootDSE-subordinate-naming-context-1";
    String backend2 = "o=test-rootDSE-subordinate-naming-context-2";
    String backendID1 = "test-rootDSE-subordinate-naming-context-1";
    String backendID2 = "test-rootDSE-subordinate-naming-context-2";

    try
    {
      TestCaseUtils.clearDataBackends();

      // At this point, the list of subordinate naming context is not defined
      // yet (null): any public backend should be visible. Create a backend
      // with a base entry and check that the test naming context is visible.
      TestCaseUtils.initializeMemoryBackend(backendID1, backend1, true);
      searchPublicNamingContexts(ResultCode.SUCCESS, 1);

      // Create another test backend and check that the new backend is visible
      TestCaseUtils.initializeMemoryBackend(backendID2, backend2, true);
      searchPublicNamingContexts(ResultCode.SUCCESS, 2);

      // Now put in the list of subordinate naming context the backend1 naming context.
      // This white list will prevent the backend2 to be visible.
      TestCaseUtils.dsconfig(
          "set-root-dse-backend-prop",
          "--set", "subordinate-base-dn:" + backend1);
      searchPublicNamingContexts(ResultCode.SUCCESS, 1);

      // === Cleaning

      // Reset the subordinate naming context list.
      // Both naming context should be visible again.
      TestCaseUtils.dsconfig(
          "set-root-dse-backend-prop",
          "--reset", "subordinate-base-dn");
      searchPublicNamingContexts(ResultCode.SUCCESS, 2);
    }
    finally
    {
      // Clean the test backends. There is no more naming context.
      TestCaseUtils.clearMemoryBackend(backendID1);
      TestCaseUtils.clearMemoryBackend(backendID2);
      searchPublicNamingContexts(ResultCode.NO_SUCH_OBJECT, 0);
    }
  }

  /**
   * Searches the list of naming contexts.
   *
   * @param expectedRC  the expected result code
   * @param expectedNamingContexts  the number of expected naming contexts
   */
  private void searchPublicNamingContexts(ResultCode expectedRC, int expectedNamingContexts) throws Exception
  {
    searchEntries("", expectedRC, expectedNamingContexts);
  }

  private void searchEntries(String baseDN, ResultCode expectedRC, int expectedNbEntries) throws DirectoryException
  {
    SearchRequest request = newSearchRequest(DN.valueOf(baseDN), SearchScope.SINGLE_LEVEL);
    SearchOperation search = getRootConnection().processSearch(request);

    assertEquals(search.getResultCode(), expectedRC);
    if (expectedRC == ResultCode.SUCCESS)
    {
      assertEquals(search.getEntriesSent(), expectedNbEntries);
    }
  }

  /**
   * Searches an entry on a given connection.
   *
   * @param baseDN the request base DN string
   * @param expectedRC the expected result code
   */
  private void searchEntry(String baseDN, ResultCode expectedRC) throws Exception
  {
    SearchRequest request = newSearchRequest(DN.valueOf(baseDN), SearchScope.BASE_OBJECT);
    SearchOperation search = getRootConnection().processSearch(request);
    assertEquals(search.getResultCode(), expectedRC);
  }

  /**
   * Creates a base entry for the given suffix.
   *
   * @param suffix      the suffix for which the base entry is to be created
   */
  private void addBaseEntry(String suffix, String namingAttribute) throws Exception
  {
    TestCaseUtils.addEntry(
        "dn: " + suffix,
        "objectClass: top",
        "objectClass: organization",
        "o: " + namingAttribute);
  }

  /**
   * Adds/Deletes/Replaces an attribute in a given entry.
   *
   * @param baseDN          the request base DN string
   * @param modType         the modification type (add/delete/replace)
   * @param attrName   the name  of the attribute to add/delete/replace
   * @param attrValue  the value of the attribute to add/delete/replace
   */
  private void modifyAttribute(String baseDN, ModificationType modType, String attrName, String attrValue)
      throws Exception
  {
    ModifyOperation modifyOperation = getModifyOperation(baseDN, modType, attrName, attrValue);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }

  private DN getMatchedDN(String entryDN)
  {
    return getModifyOperation(entryDN, ADD, "sn", "dummy").getMatchedDN();
  }

  private ModifyOperation getModifyOperation(String baseDN, ModificationType modType, String attrName, String attrValue)
  {
    ModifyRequest modifyRequest = Requests.newModifyRequest(baseDN).addModification(modType, attrName, attrValue);
    return getRootConnection().processModify(modifyRequest);
  }
}
