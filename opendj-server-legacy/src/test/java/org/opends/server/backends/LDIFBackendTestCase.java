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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends;

import static org.forgerock.opendj.ldap.requests.Requests.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.testng.Assert.*;

import java.io.File;
import java.util.UUID;

import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.api.Backend.BackendOperation;
import org.opends.server.core.AddOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.tasks.LdifFileWriter;
import org.opends.server.tasks.TasksTestCase;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.LDAPSearch;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * A set of test cases for the LDIF backend.
 */
public class LDIFBackendTestCase
       extends BackendTestCase
{
  /**
   * Ensures that the Directory Server is running and that the LDIF backend
   * is populated with sample data.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();

    String templateFilePath = TestCaseUtils.createTempFile(
      "define suffix=o=ldif",
      "define numusers=25",
      "",
      "branch: [suffix]",
      "",
      "branch: ou=People,[suffix]",
      "subordinateTemplate: person:[numusers]",
      "",
      "template: person",
      "rdnAttr: uid",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "givenName: <random:alpha:6>",
      "sn: <random:alpha:6>",
      "cn: {givenName} {sn}",
      "uid: user.<sequential:1>",
      "userPassword: password");

    // Create a temporary test LDIF file.
    File ldifFile = File.createTempFile("import-test", ".ldif");
    String resourcePath = DirectoryServer.getInstanceRoot() + File.separator +
         "config" + File.separator + "MakeLDIF";
    LdifFileWriter.makeLdif(ldifFile.getPath(), resourcePath, templateFilePath);

    DN taskDN = DN.valueOf("ds-task-id=" + UUID.randomUUID() +
                    ",cn=Scheduled Tasks,cn=Tasks");

    TestCaseUtils.addEntry(
      "dn: " + taskDN,
      "objectclass: top",
      "objectclass: ds-task",
      "objectclass: ds-task-import",
      "ds-task-class-name: org.opends.server.tasks.ImportTask",
      "ds-task-import-backend-id: ldifRoot",
      "ds-task-import-ldif-file: " + ldifFile.getAbsolutePath());

    TasksTestCase.waitTaskCompletedSuccessfully(taskDN);
  }



  /**
   * Tests to ensure that add and delete operations (including subtree delete)
   * work as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAndDelete() throws Exception
  {
    // Add a number of entries to the server.
    int resultCode = TestCaseUtils.applyModifications(false,
      "dn: ou=dummy,o=ldif",
      "changetype: add",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: dummy",
      "",
      "dn: ou=sub1,ou=dummy,o=ldif",
      "changetype: add",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: sub1",
      "",
      "dn: ou=sub2,ou=dummy,o=ldif",
      "changetype: add",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: sub2",
      "",
      "dn: ou=sub3,ou=dummy,o=ldif",
      "changetype: add",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: sub3",
      "",
      "dn: ou=sub4,ou=dummy,o=ldif",
      "changetype: add",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: sub4",
      "",
      "dn: ou=sub5,ou=dummy,o=ldif",
      "changetype: add",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: sub5");
    assertEquals(resultCode, 0);


    // Verify that we can delete a single leaf entry.
    resultCode = TestCaseUtils.applyModifications(false,
      "dn: ou=sub5,ou=dummy,o=ldif",
      "changetype: delete");
    assertEquals(resultCode, 0);


    // Verify that a default attempt to delete a non-leaf entry will fail.
    String subtreeDeletePath = TestCaseUtils.createTempFile(
      "dn: ou=dummy,o=ldif",
      "changetype: delete");
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", subtreeDeletePath
    };
    resultCode = LDAPModify.mainModify(args, false, System.out, System.err);
    assertEquals(resultCode, ResultCode.NOT_ALLOWED_ON_NONLEAF.intValue());


    // Verify that the subtree delete will succeed if we include the subtree
    // delete control in the request.
    args = new String[]
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", "subtreeDelete",
      "-f", subtreeDeletePath
    };
    resultCode = LDAPModify.mainModify(args, false, System.out, System.err);
    assertEquals(resultCode, 0);
  }



  /**
   * Tests an attempt to add an entry to the LDIF backend when an entry with
   * the same DN already exists.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddAlreadyExists()
         throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
      "dn: ou=People,o=ldif",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People");

    AddOperation addOperation = getRootConnection().processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.ENTRY_ALREADY_EXISTS);
  }



  /**
   * Tests an attempt to add an entry to the LDIF backend when the parent for
   * the new entry doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddNoParent()
         throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
      "dn: ou=test,ou=doesntexist,o=ldif",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: test");

    AddOperation addOperation = getRootConnection().processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    assertEquals(addOperation.getMatchedDN(), DN.valueOf("o=ldif"));
  }



  /**
   * Tests the ability to add the base entry to the backend.  This will first
   * perform a subtree delete to get rid of everything, then add the base entry,
   * and then restore the original content.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddBaseEntry() throws Exception
  {
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=ldif")));
    assertTrue(DirectoryServer.entryExists(
                   DN.valueOf("uid=user.1,ou=People,o=ldif")));

    String path = TestCaseUtils.createTempFile(
      "dn: o=ldif",
      "changetype: delete");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-J", "subtreeDelete",
      "-f", path
    };
    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err), 0);
    assertFalse(DirectoryServer.entryExists(DN.valueOf("o=ldif")));
    assertFalse(DirectoryServer.entryExists(
                    DN.valueOf("uid=user.1,ou=People,o=ldif")));

    TestCaseUtils.addEntry(
      "dn: o=ldif",
      "objectClass: top",
      "objectClass: organization",
      "o: ldif");
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=ldif")));
    assertFalse(DirectoryServer.entryExists(
                    DN.valueOf("uid=user.1,ou=People,o=ldif")));

    setUp();
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=ldif")));
    assertTrue(DirectoryServer.entryExists(
                   DN.valueOf("uid=user.1,ou=People,o=ldif")));
  }



  /**
   * Tests to ensure that we can bind as a user contained in an LDIF backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testBind()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=user.1,ou=People,o=ldif",
      "-w", "password",
      "-b", "o=ldif",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, System.out, System.err), 0);
  }



  /**
   * Tests to ensure that we can perform a compare against entries in an LDIF
   * backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCompare() throws Exception
  {
    InternalClientConnection conn = getRootConnection();
    CompareOperation compareOperation =
         conn.processCompare("uid=user.1,ou=People,o=ldif", "uid", "user.1");
    assertEquals(compareOperation.getResultCode(), ResultCode.COMPARE_TRUE);
  }



  /**
   * Tests to ensure that we can modify entries in the LDIF backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModify() throws Exception
  {
    String path = TestCaseUtils.createTempFile(
      "dn: o=ldif",
      "changetype: modify",
      "replace: description",
      "description: foo");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };
    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err), 0);
  }



  /**
   * Tests a simple modify DN operation that targets a single leaf entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSimpleModifyDN()
         throws Exception
  {
    TestCaseUtils.addEntry(
      "dn: ou=leaf before,o=ldif",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: leaf before");

    DN beforeDN = DN.valueOf("ou=leaf before,o=ldif");
    DN afterDN  = DN.valueOf("ou=leaf after,o=ldif");

    assertTrue(DirectoryServer.entryExists(beforeDN));
    assertFalse(DirectoryServer.entryExists(afterDN));

    ModifyDNOperation modifyDNOperation = processModifyDN("ou=leaf before,o=ldif", "ou=leaf after", true);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);

    assertFalse(DirectoryServer.entryExists(beforeDN));
    assertTrue(DirectoryServer.entryExists(afterDN));

    DeleteOperation deleteOperation = getRootConnection().processDelete(afterDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(DirectoryServer.entryExists(afterDN));
  }



  /**
   * Tests a modify DN operation in which the target entry already exists.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyDNTargetAlreadyExists()
         throws Exception
  {
    TestCaseUtils.addEntry(
      "dn: ou=new entry,o=ldif",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: new entry");

    ModifyDNOperation modifyDNOperation = processModifyDN("ou=new entry,o=ldif", "ou=People", true);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.ENTRY_ALREADY_EXISTS);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("ou=new entry,o=ldif")));

    DeleteOperation deleteOperation = getRootConnection().processDelete("ou=new entry,o=ldif");
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(DirectoryServer.entryExists(DN.valueOf("ou=new entry,o=ldif")));
  }



  /**
   * Tests a modify DN operation that targets a single leaf entry and provides a
   * new superior DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyDNWithNewSuperior()
         throws Exception
  {
    TestCaseUtils.addEntry(
      "dn: ou=leaf before,o=ldif",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: leaf before");

    DN beforeDN = DN.valueOf("ou=leaf before,o=ldif");
    DN afterDN  = DN.valueOf("ou=leaf after,ou=People,o=ldif");

    assertTrue(DirectoryServer.entryExists(beforeDN));
    assertFalse(DirectoryServer.entryExists(afterDN));

    InternalClientConnection conn = getRootConnection();
    ModifyDNOperation modifyDNOperation =
      conn.processModifyDN("ou=leaf before,o=ldif", "ou=leaf after", true,
                           "ou=People,o=ldif");
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);

    assertFalse(DirectoryServer.entryExists(beforeDN));
    assertTrue(DirectoryServer.entryExists(afterDN));

    DeleteOperation deleteOperation = conn.processDelete(afterDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(DirectoryServer.entryExists(afterDN));
  }



  /**
   * Tests a modify DN operation that involves a subtree rename operation.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyDNSubtreeRename()
         throws Exception
  {
    DN beforeDN      = DN.valueOf("ou=People,o=ldif");
    DN afterDN       = DN.valueOf("ou=Users,o=ldif");
    DN childBeforeDN = DN.valueOf("uid=user.1,ou=People,o=ldif");
    DN childAfterDN  = DN.valueOf("uid=user.1,ou=Users,o=ldif");

    assertTrue(DirectoryServer.entryExists(beforeDN));
    assertFalse(DirectoryServer.entryExists(afterDN));

    assertTrue(DirectoryServer.entryExists(childBeforeDN));
    assertFalse(DirectoryServer.entryExists(childAfterDN));

    ModifyDNOperation modifyDNOperation = processModifyDN("ou=People,o=ldif", "ou=Users", true);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);

    assertFalse(DirectoryServer.entryExists(beforeDN));
    assertTrue(DirectoryServer.entryExists(afterDN));

    assertFalse(DirectoryServer.entryExists(childBeforeDN));
    assertTrue(DirectoryServer.entryExists(childAfterDN));

    modifyDNOperation = processModifyDN("ou=Users,o=ldif", "ou=People", true);

    assertTrue(DirectoryServer.entryExists(beforeDN));
    assertFalse(DirectoryServer.entryExists(afterDN));

    assertTrue(DirectoryServer.entryExists(childBeforeDN));
    assertFalse(DirectoryServer.entryExists(childAfterDN));
  }

  private ModifyDNOperation processModifyDN(String name, String newRDN, boolean deleteOldRDN)
  {
    return getRootConnection().processModifyDN(newModifyDNRequest(name, newRDN).setDeleteOldRDN(deleteOldRDN));
  }

  /**
   * Tests to ensure that a base-level search works as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testBaseSearch() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.valueOf("o=ldif"), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 1);
  }



  /**
   * Tests to ensure that a base-level search works as expected when the filter
   * doesn't match the target entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testBaseSearchNonMatchingFilter() throws Exception
  {
    InternalSearchOperation searchOperation =
        getRootConnection().processSearch(newSearchRequest("o=ldif", SearchScope.BASE_OBJECT, "(o=not ldif)"));
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 0);
  }



  /**
   * Tests to ensure that a base-level search works as expected when the target
   * entry does not exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testBaseSearchNoSuchEntry() throws Exception
  {
    DN dn = DN.valueOf("o=nonexistent2,o=nonexistent1,o=ldif");
    SearchRequest request = newSearchRequest(dn, SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    assertEquals(searchOperation.getMatchedDN(), DN.valueOf("o=ldif"));
  }



  /**
   * Tests to ensure that a single-level search works as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSingleLevelSearch() throws Exception
  {
    SearchRequest request = newSearchRequest(DN.valueOf("o=ldif"), SearchScope.SINGLE_LEVEL);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 1);
  }



  /**
   * Tests to ensure that subtree search operations work as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSubtreeSearch() throws Exception
  {
    SearchRequest request = newSearchRequest("o=ldif", SearchScope.WHOLE_SUBTREE, "(uid=user.1)");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 1);
  }



  /**
   * Tests to ensure that subordinate subtree search operations work as
   * expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSubordinateSubtreeSearch() throws Exception
  {
    SearchRequest request = newSearchRequest("o=ldif", SearchScope.SUBORDINATES, "(uid=user.1)");
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 1);
  }



  /**
   * Tests the {@code hasSubordinates} method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testHasSubordinates() throws Exception
  {
    Backend<?> b = getLDIFBackend();

    assertEquals(b.hasSubordinates(DN.valueOf("o=ldif")), ConditionResult.TRUE);
    assertEquals(b.hasSubordinates(DN.valueOf("uid=user.1,ou=People,o=ldif")),
                 ConditionResult.FALSE);

    try
    {
      b.hasSubordinates(DN.valueOf("ou=nonexistent,o=ldif"));
      fail("Expected an exception when calling hasSubordinates on a " +
           "non-existent entry");
    }
    catch (DirectoryException de)
    {
      assertEquals(de.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    }
  }

  /**
   * Tests the {@code numSubordinates} method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testNumSubordinates() throws Exception
  {
    Backend<?> b = getLDIFBackend();

    assertEquals(b.getNumberOfChildren(DN.valueOf("o=ldif")), 1);
    assertEquals(b.getNumberOfEntriesInBaseDN(DN.valueOf("o=ldif")), 27);
    assertEquals(b.getNumberOfChildren(DN.valueOf("uid=user.1,ou=People,o=ldif")), 0);
    try
    {
      b.getNumberOfChildren(DN.valueOf("ou=nonexistent,o=ldif"));
      fail("Expected an exception when calling numSubordinates on a " + "non-existent entry");
    }
    catch (DirectoryException de)
    {
      assertEquals(de.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    }
  }

  @Test(expectedExceptions=DirectoryException.class)
  public void testCannotGetNumberOfEntriesForNotBaseDN() throws Exception {
    assertEquals(getLDIFBackend().getNumberOfEntriesInBaseDN(DN.valueOf("uid=user.1,ou=People,o=ldif")), 0);
  }

  /**
   * Tests LDIF export functionality.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testLDIFExport() throws Exception
  {
    Backend<?> b = getLDIFBackend();
    assertTrue(b.supports(BackendOperation.LDIF_EXPORT));

    String tempFilePath = TestCaseUtils.createTempFile();

    String taskDN = "ds-task-id=" + UUID.randomUUID() +
                    ",cn=Scheduled Tasks,cn=Tasks";
    TestCaseUtils.addEntry(
      "dn: " + taskDN,
      "objectclass: top",
      "objectclass: ds-task",
      "objectclass: ds-task-export",
      "ds-task-class-name: org.opends.server.tasks.ExportTask",
      "ds-task-export-backend-id: ldifRoot",
      "ds-task-export-ldif-file: " + tempFilePath);

    TasksTestCase.waitTaskCompletedSuccessfully(DN.valueOf(taskDN));
  }



  /**
   * Tests a number of miscellaneous backend methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testMiscellaneousBackendMethods()
         throws Exception
  {
    LDIFBackend b = getLDIFBackend();
    assertTrue(b.getEntryCount() > 0);
    assertFalse(b.supports(BackendOperation.BACKUP));

    try
    {
      b.createBackup(null);
      fail("Expected an exception when calling createBackup");
    } catch (DirectoryException de) {}

    try
    {
      b.removeBackup(null, null);
      fail("Expected an exception when calling removeBackup");
    } catch (DirectoryException de) {}

    assertFalse(b.supports(BackendOperation.RESTORE));

    try
    {
      b.restoreBackup(null);
      fail("Expected an exception when calling restoreBackup");
    } catch (DirectoryException de) {}

    assertNotNull(b.getClassName());
    assertNotNull(b.getAlerts());
    assertFalse(b.getAlerts().isEmpty());
  }

  private LDIFBackend getLDIFBackend()
  {
    Backend<?> b = DirectoryServer.getBackend("ldifRoot");
    assertNotNull(b);
    assertTrue(b instanceof LDIFBackend);
    return (LDIFBackend) b;
  }
}
