/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends;



import java.io.File;
import java.util.UUID;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.Backend;
import org.opends.server.backends.LDIFBackend;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.core.AddOperation;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.tasks.LdifFileWriter;
import org.opends.server.tasks.TasksTestCase;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.tools.LDIFDiff;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;

import static org.testng.Assert.*;

import static org.opends.server.util.StaticUtils.*;



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
  @BeforeClass()
  public void setUp()
         throws Exception
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
    String resourcePath = DirectoryServer.getServerRoot() + File.separator +
         "config" + File.separator + "MakeLDIF";
    LdifFileWriter.makeLdif(ldifFile.getPath(), resourcePath, templateFilePath);

    String taskDN = "ds-task-id=" + UUID.randomUUID() +
                    ",cn=Scheduled Tasks,cn=Tasks";

    TestCaseUtils.addEntry(
      "dn: " + taskDN,
      "objectclass: top",
      "objectclass: ds-task",
      "objectclass: ds-task-import",
      "ds-task-class-name: org.opends.server.tasks.ImportTask",
      "ds-task-import-backend-id: ldifRoot",
      "ds-task-import-ldif-file: " + ldifFile.getAbsolutePath());

    Task t = TasksTestCase.getCompletedTask(DN.decode(taskDN));
    assertNotNull(t);
    assertEquals(t.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);
  }



  /**
   * Tests to ensure that add and delete operations (including subtree delete)
   * work as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddAndDelete()
         throws Exception
  {
    // Add a number of entries to the server.
    int resultCode = TestCaseUtils.applyModifications(
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
    resultCode = TestCaseUtils.applyModifications(
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
    assertEquals(resultCode, ResultCode.NOT_ALLOWED_ON_NONLEAF.getIntValue());


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
  @Test()
  public void testAddAlreadyExists()
         throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
      "dn: ou=People,o=ldif",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.ENTRY_ALREADY_EXISTS);
  }



  /**
   * Tests an attempt to add an entry to the LDIF backend when the parent for
   * the new entry doesn't exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddNoParent()
         throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
      "dn: ou=test,ou=doesntexist,o=ldif",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: test");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    assertEquals(addOperation.getMatchedDN(), DN.decode("o=ldif"));
  }



  /**
   * Tests the ability to add the base entry to the backend.  This will first
   * perform a subtree delete to get rid of everything, then add the base entry,
   * and then restore the original content.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddBaseEntry()
         throws Exception
  {
    assertTrue(DirectoryServer.entryExists(DN.decode("o=ldif")));
    assertTrue(DirectoryServer.entryExists(
                   DN.decode("uid=user.1,ou=People,o=ldif")));

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
    assertFalse(DirectoryServer.entryExists(DN.decode("o=ldif")));
    assertFalse(DirectoryServer.entryExists(
                    DN.decode("uid=user.1,ou=People,o=ldif")));

    Entry e = TestCaseUtils.makeEntry(
      "dn: o=ldif",
      "objectClass: top",
      "objectClass: organization",
      "o: ldif");

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    AddOperation addOperation = conn.processAdd(e);
    assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    assertTrue(DirectoryServer.entryExists(DN.decode("o=ldif")));
    assertFalse(DirectoryServer.entryExists(
                    DN.decode("uid=user.1,ou=People,o=ldif")));

    setUp();
    assertTrue(DirectoryServer.entryExists(DN.decode("o=ldif")));
    assertTrue(DirectoryServer.entryExists(
                   DN.decode("uid=user.1,ou=People,o=ldif")));
  }



  /**
   * Tests to ensure that we can bind as a user contained in an LDIF backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
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
  @Test()
  public void testCompare()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    CompareOperation compareOperation =
         conn.processCompare("uid=user.1,ou=People,o=ldif", "uid", "user.1");
    assertEquals(compareOperation.getResultCode(), ResultCode.COMPARE_TRUE);
  }



  /**
   * Tests to ensure that we can modify entries in the LDIF backend.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModify()
         throws Exception
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
  @Test()
  public void testSimpleModifyDN()
         throws Exception
  {
    TestCaseUtils.addEntry(
      "dn: ou=leaf before,o=ldif",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: leaf before");

    DN beforeDN = DN.decode("ou=leaf before,o=ldif");
    DN afterDN  = DN.decode("ou=leaf after,o=ldif");

    assertTrue(DirectoryServer.entryExists(beforeDN));
    assertFalse(DirectoryServer.entryExists(afterDN));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyDNOperation modifyDNOperation =
      conn.processModifyDN("ou=leaf before,o=ldif", "ou=leaf after", true);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);

    assertFalse(DirectoryServer.entryExists(beforeDN));
    assertTrue(DirectoryServer.entryExists(afterDN));

    DeleteOperation deleteOperation = conn.processDelete(afterDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(DirectoryServer.entryExists(afterDN));
  }



  /**
   * Tests a modify DN operation in which the target entry already exists.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNTargetAlreadyExists()
         throws Exception
  {
    TestCaseUtils.addEntry(
      "dn: ou=new entry,o=ldif",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: new entry");
    assertTrue(DirectoryServer.entryExists(DN.decode("ou=new entry,o=ldif")));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN("ou=new entry,o=ldif", "ou=People", true);
    assertEquals(modifyDNOperation.getResultCode(),
                 ResultCode.ENTRY_ALREADY_EXISTS);
    assertTrue(DirectoryServer.entryExists(DN.decode("ou=new entry,o=ldif")));

    DeleteOperation deleteOperation = conn.processDelete("ou=new entry,o=ldif");
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(DirectoryServer.entryExists(DN.decode("ou=new entry,o=ldif")));
  }



  /**
   * Tests a modify DN operation that targets a single leaf entry and provides a
   * new superior DN.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNWithNewSuperior()
         throws Exception
  {
    TestCaseUtils.addEntry(
      "dn: ou=leaf before,o=ldif",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: leaf before");

    DN beforeDN = DN.decode("ou=leaf before,o=ldif");
    DN afterDN  = DN.decode("ou=leaf after,ou=People,o=ldif");

    assertTrue(DirectoryServer.entryExists(beforeDN));
    assertFalse(DirectoryServer.entryExists(afterDN));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
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
  @Test()
  public void testModifyDNSubtreeRename()
         throws Exception
  {
    DN beforeDN      = DN.decode("ou=People,o=ldif");
    DN afterDN       = DN.decode("ou=Users,o=ldif");
    DN childBeforeDN = DN.decode("uid=user.1,ou=People,o=ldif");
    DN childAfterDN  = DN.decode("uid=user.1,ou=Users,o=ldif");

    assertTrue(DirectoryServer.entryExists(beforeDN));
    assertFalse(DirectoryServer.entryExists(afterDN));

    assertTrue(DirectoryServer.entryExists(childBeforeDN));
    assertFalse(DirectoryServer.entryExists(childAfterDN));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyDNOperation modifyDNOperation =
      conn.processModifyDN("ou=People,o=ldif", "ou=Users", true);
    assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);

    assertFalse(DirectoryServer.entryExists(beforeDN));
    assertTrue(DirectoryServer.entryExists(afterDN));

    assertFalse(DirectoryServer.entryExists(childBeforeDN));
    assertTrue(DirectoryServer.entryExists(childAfterDN));

    modifyDNOperation =
         conn.processModifyDN("ou=Users,o=ldif", "ou=People", true);

    assertTrue(DirectoryServer.entryExists(beforeDN));
    assertFalse(DirectoryServer.entryExists(afterDN));

    assertTrue(DirectoryServer.entryExists(childBeforeDN));
    assertFalse(DirectoryServer.entryExists(childAfterDN));
  }



  /**
   * Tests to ensure that a base-level search works as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBaseSearch()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch("o=ldif", SearchScope.BASE_OBJECT,
                         "(objectClass=*)");
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 1);
  }



  /**
   * Tests to ensure that a base-level search works as expected when the filter
   * doesn't match the target entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBaseSearchNonMatchingFilter()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch("o=ldif", SearchScope.BASE_OBJECT,
                         "(o=not ldif)");
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 0);
  }



  /**
   * Tests to ensure that a base-level search works as expected when the target
   * entry does not exist.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testBaseSearchNoSuchEntry()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch("o=nonexistent2,o=nonexistent1,o=ldif",
                            SearchScope.BASE_OBJECT, "(objectClass=*)");
    assertEquals(searchOperation.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    assertEquals(searchOperation.getMatchedDN(), DN.decode("o=ldif"));
  }



  /**
   * Tests to ensure that a single-level search works as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSingleLevelSearch()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch("o=ldif", SearchScope.SINGLE_LEVEL,
                         "(objectClass=*)");
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 1);
  }



  /**
   * Tests to ensure that subtree search operations work as expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSubtreeSearch()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch("o=ldif", SearchScope.WHOLE_SUBTREE,
                            "(uid=user.1)");
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 1);
  }



  /**
   * Tests to ensure that subordinate subtree search operations work as
   * expected.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSubordinateSubtreeSearch()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         conn.processSearch("o=ldif", SearchScope.SUBORDINATE_SUBTREE,
                            "(uid=user.1)");
    assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    assertEquals(searchOperation.getSearchEntries().size(), 1);
  }



  /**
   * Tests the {@code hasSubordinates} method.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testHasSubordinates()
         throws Exception
  {
    Backend b = DirectoryServer.getBackend("ldifRoot");
    assertNotNull(b);
    assertTrue(b instanceof LDIFBackend);

    assertEquals(b.hasSubordinates(DN.decode("o=ldif")), ConditionResult.TRUE);
    assertEquals(b.hasSubordinates(DN.decode("uid=user.1,ou=People,o=ldif")),
                 ConditionResult.FALSE);

    try
    {
      b.hasSubordinates(DN.decode("ou=nonexistent,o=ldif"));
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
  @Test()
  public void testNumSubordinates()
         throws Exception
  {
    Backend b = DirectoryServer.getBackend("ldifRoot");
    assertNotNull(b);
    assertTrue(b instanceof LDIFBackend);

    assertEquals(b.numSubordinates(DN.decode("o=ldif")), 1);
    assertEquals(b.numSubordinates(DN.decode("uid=user.1,ou=People,o=ldif")),
                 0);

    try
    {
      b.numSubordinates(DN.decode("ou=nonexistent,o=ldif"));
      fail("Expected an exception when calling numSubordinates on a " +
           "non-existent entry");
    }
    catch (DirectoryException de)
    {
      assertEquals(de.getResultCode(), ResultCode.NO_SUCH_OBJECT);
    }
  }



  /**
   * Tests LDIF export functionality.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testLDIFExport()
         throws Exception
  {
    Backend b = DirectoryServer.getBackend("ldifRoot");
    assertNotNull(b);
    assertTrue(b instanceof LDIFBackend);
    assertTrue(b.supportsLDIFExport());

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

    Task t = TasksTestCase.getCompletedTask(DN.decode(taskDN));
    assertNotNull(t);
    assertEquals(t.getTaskState(), TaskState.COMPLETED_SUCCESSFULLY);
  }



  /**
   * Tests a number of miscellaneous backend methods.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMiscellaneousBackendMethods()
         throws Exception
  {
    Backend b = DirectoryServer.getBackend("ldifRoot");
    assertNotNull(b);
    assertTrue(b instanceof LDIFBackend);

    assertTrue(b.getEntryCount() > 0);

    assertTrue(b.isLocal());

    assertFalse(b.supportsBackup());
    assertFalse(b.supportsBackup(null, null));

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

    assertFalse(b.supportsRestore());

    try
    {
      b.restoreBackup(null);
      fail("Expected an exception when calling restoreBackup");
    } catch (DirectoryException de) {}

    LDIFBackend ldifBackend = (LDIFBackend) b;
    assertNotNull(ldifBackend.getClassName());
    assertNotNull(ldifBackend.getAlerts());
    assertFalse(ldifBackend.getAlerts().isEmpty());
  }
}

