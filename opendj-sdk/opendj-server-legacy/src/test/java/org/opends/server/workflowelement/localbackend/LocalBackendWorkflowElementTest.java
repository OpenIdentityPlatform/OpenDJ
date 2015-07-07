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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS.
 */
package org.opends.server.workflowelement.localbackend;

import java.util.ArrayList;

import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Modification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

/**
 * This set of tests test the LocalBackendWorkflowElement.
 */
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
    TestCaseUtils.clearJEBackend("userRoot", suffix);

    // Check that suffix is accessible while suffix2 is not.
    searchEntry(suffix, ResultCode.SUCCESS);
    searchEntry(suffix2, ResultCode.NO_SUCH_OBJECT);

    // Add a new suffix in the backend and create a base entry for the new suffix
    String backendConfigDN = "ds-cfg-backend-id=userRoot," + DN_BACKEND_BASE;
    modifyAttribute(backendConfigDN, ModificationType.ADD, backendBaseDNName, suffix2);
    addBaseEntry(suffix2, "workflow suffix");

    // Both old and new suffix should be accessible.
    searchEntry(suffix, ResultCode.SUCCESS);
    searchEntry(suffix2, ResultCode.SUCCESS);

    // Remove the new suffix...
    modifyAttribute(backendConfigDN, ModificationType.DELETE, backendBaseDNName, suffix2);

    // ...and check that the removed suffix is no more accessible.
    searchEntry(suffix, ResultCode.SUCCESS);
    searchEntry(suffix2, ResultCode.NO_SUCH_OBJECT);

    // Replace the suffix with suffix2 in the backend
    modifyAttribute(backendConfigDN, ModificationType.REPLACE, backendBaseDNName, suffix2);

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
    modifyAttribute(backendConfigDN, ModificationType.REPLACE, backendBaseDNName, suffix);
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
   * @param attributeName   the name  of the attribute to add/delete/replace
   * @param attributeValue  the value of the attribute to add/delete/replace
   */
  private void modifyAttribute(String baseDN, ModificationType modType, String attributeName, String attributeValue)
      throws Exception
  {
    ArrayList<Modification> mods = new ArrayList<>();
    Attribute attributeToModify = Attributes.create(attributeName, attributeValue);
    mods.add(new Modification(modType, attributeToModify));
    ModifyOperation modifyOperation = getRootConnection().processModify(DN.valueOf(baseDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }
}
