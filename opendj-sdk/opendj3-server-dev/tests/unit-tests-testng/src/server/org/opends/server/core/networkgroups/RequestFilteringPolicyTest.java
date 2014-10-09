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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.core.networkgroups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.RequestFilteringQOSPolicyCfgDefn.AllowedOperations;
import org.opends.server.admin.std.meta.RequestFilteringQOSPolicyCfgDefn.AllowedSearchScopes;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.OperationType;
import org.opends.server.types.operation.PreParseAddOperation;
import org.opends.server.types.operation.PreParseBindOperation;
import org.opends.server.types.operation.PreParseCompareOperation;
import org.opends.server.types.operation.PreParseDeleteOperation;
import org.opends.server.types.operation.PreParseExtendedOperation;
import org.opends.server.types.operation.PreParseModifyDNOperation;
import org.opends.server.types.operation.PreParseModifyOperation;
import org.opends.server.types.operation.PreParseOperation;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.server.admin.std.meta.RequestFilteringQOSPolicyCfgDefn.AllowedOperations.*;
import static org.opends.server.admin.std.meta.RequestFilteringQOSPolicyCfgDefn.AllowedSearchScopes.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * This set of tests test the resource limits.
 */
@SuppressWarnings("javadoc")
public class RequestFilteringPolicyTest extends DirectoryServerTestCase {
  //===========================================================================
  //                      B E F O R E    C L A S S
  //===========================================================================

  /**
   * Sets up the environment for performing the tests in this suite.
   *
   * @throws Exception if the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available,
    // so we'll start the server.
    TestCaseUtils.startServer();
  }


  //===========================================================================
  //                      D A T A    P R O V I D E R
  //===========================================================================

  /** Provides information to create an allowedAttribute policy and a filter to test. */
  @DataProvider (name = "AllowedAttributesSet")
  public Object[][] initAllowedAttributesSet()
  {
    TreeSet<String> allowedAttr_uid_cn = newTreeSet("uid", "cn");
    TreeSet<String> allowedAttr_cn = newTreeSet("cn");
    TreeSet<String> allowedAttr_uid = newTreeSet("uid");

    return new Object[][] {
      // allowed attributes, attribute to test, success
      {allowedAttr_uid_cn, "uid=*", true},
      {allowedAttr_uid_cn, "cn=*", true},
      {allowedAttr_uid_cn, "(&(uid=user.1)(cn=*))", true},
      {allowedAttr_cn, "cn=*", true},
      {allowedAttr_cn, "uid=*", false},
      {allowedAttr_cn, "(&(uid=user.1)(cn=*))", false},
      {allowedAttr_uid, "cn=*", false},
      {allowedAttr_uid, "uid=*", true},
      {allowedAttr_uid, "(&(uid=user.1)(cn=*))", false}
    };
  }

  /** Provides information to create a prohibitedAttribute policy and a filter to test. */
  @DataProvider (name = "ProhibitedAttributesSet")
  public Object[][] initProhibitedAttributesSet()
  {
    TreeSet<String> prohibitedAttr_uid = newTreeSet("uid");
    TreeSet<String> prohibitedAttr_cn = newTreeSet("cn");

    return new Object[][] {
      // prohibited attributes, attribute to test, success
      {prohibitedAttr_uid, "uid=*", false},
      {prohibitedAttr_cn, "uid=*", true},
      {prohibitedAttr_cn, "(&(uid=user.1)(cn=*))", false}
    };
  }

  /** Provides information to create an allowedSearchScopes policy and a scope to test. */
  @DataProvider (name = "AllowedSearchScopesSet")
  public Object[][] initAllowedSearchScopesSet()
  {
    TreeSet<AllowedSearchScopes> scopes_all = newTreeSet2(BASE, CHILDREN, ONE, SUB);
    TreeSet<AllowedSearchScopes> scope_base = newTreeSet2(BASE);
    TreeSet<AllowedSearchScopes> scope_children = newTreeSet2(CHILDREN);
    TreeSet<AllowedSearchScopes> scope_one = newTreeSet2(ONE);
    TreeSet<AllowedSearchScopes> scope_sub = newTreeSet2(SUB);

    return new Object[][] {
      // allowed search scopes, scope to test, success
      {scopes_all, SearchScope.BASE_OBJECT, true},
      {scope_base, SearchScope.BASE_OBJECT, true},
      {scope_base, SearchScope.SINGLE_LEVEL, false},
      {scope_base, SearchScope.SUBORDINATES, false},
      {scope_base, SearchScope.WHOLE_SUBTREE, false},
      {scope_children, SearchScope.BASE_OBJECT, false},
      {scope_children, SearchScope.SINGLE_LEVEL, false},
      {scope_children, SearchScope.SUBORDINATES, true},
      {scope_children, SearchScope.WHOLE_SUBTREE, false},
      {scope_one, SearchScope.BASE_OBJECT, false},
      {scope_one, SearchScope.SINGLE_LEVEL, true},
      {scope_one, SearchScope.SUBORDINATES, false},
      {scope_one, SearchScope.WHOLE_SUBTREE, false},
      {scope_sub, SearchScope.BASE_OBJECT, false},
      {scope_sub, SearchScope.SINGLE_LEVEL, false},
      {scope_sub, SearchScope.SUBORDINATES, false},
      {scope_sub, SearchScope.WHOLE_SUBTREE, true}
    };
  }

  /**
   * Provides information to create a allowedSubtree policy and
   * a subtree search to test.
   */
  @DataProvider (name = "AllowedSubtreesSet")
  public Object[][] initAllowedSubtreesSet()
          throws DirectoryException
  {
    TreeSet<DN> subtrees1 = newTreeSet(DN.valueOf("ou=people,dc=example,dc=com"));
    TreeSet<DN> subtrees2 = newTreeSet(DN.valueOf("ou=test,dc=example,dc=com"));
    TreeSet<DN> subtrees3 = newTreeSet(DN.valueOf("dc=example,dc=com"));
    TreeSet<DN> subtrees4 = newTreeSet(
        DN.valueOf("dc=example,dc=com"),
        DN.valueOf("dc=test,dc=com"));

    return new Object[][] {
      // allowed subtrees, subtree to test, success
      {subtrees1, "ou=people,dc=example,dc=com", true},
      {subtrees2, "ou=people,dc=example,dc=com", false},
      {subtrees3, "ou=people,dc=example,dc=com", true},
      {subtrees1, "dc=example,dc=com", false},
      {subtrees4, "dc=example,dc=com", true},
      {subtrees4, "ou=people,dc=example,dc=com", true}
    };
  }

  /** Provides information to create a prohibitedSubtree policy and
   * a subtree search to test.
   */
  @DataProvider (name = "ProhibitedSubtreesSet")
  public Object[][] initProhibitedSubtreesSet() throws DirectoryException
  {
    TreeSet<DN> subtrees1 = newTreeSet(DN.valueOf("ou=people,dc=example,dc=com"));
    TreeSet<DN> subtrees2 = newTreeSet(DN.valueOf("ou=test,dc=example,dc=com"));
    TreeSet<DN> subtrees3 = newTreeSet(DN.valueOf("dc=example,dc=com"));
    TreeSet<DN> subtrees4 = newTreeSet(
        DN.valueOf("dc=example,dc=com"),
        DN.valueOf("dc=test,dc=com"));

    return new Object[][] {
      // prohibited subtrees, subtree to test, success
      {subtrees1, "ou=people,dc=example,dc=com", false},
      {subtrees2, "ou=people,dc=example,dc=com", true},
      {subtrees3, "ou=people,dc=example,dc=com", false},
      {subtrees1, "dc=example,dc=com", true},
      {subtrees4, "ou=people,dc=example,dc=com", false}
    };
  }

  /** Provides information to create a complex subtree policy and a
   * subtree search to test.
   */
  @DataProvider (name = "ComplexSubtreesSet")
  public Object[][] initComplexSubtreesSet() throws DirectoryException
  {
    TreeSet<DN> subtrees_empty = newTreeSet();
    TreeSet<DN> subtrees_root = newTreeSet(DN.valueOf("dc=example,dc=com"));
    TreeSet<DN> subtrees_people = newTreeSet(DN.valueOf("ou=people,dc=example,dc=com"));
    TreeSet<DN> subtrees_entry = newTreeSet(DN.valueOf("uid=user.1,ou=people,dc=example,dc=com"));

    return new Object[][] {
      // allowed subtree, prohibited subtree, subtree to test, success
      {subtrees_root, subtrees_people, "dc=example,dc=com", true},
      {subtrees_root, subtrees_people, "ou=people,dc=example,dc=com", false},
      {subtrees_root, subtrees_entry, "ou=people,dc=example,dc=com", true},
      {subtrees_empty, subtrees_people, "dc=example,dc=com", true},
      {subtrees_empty, subtrees_people, "ou=people,dc=example,dc=com", false}
    };
  }

  /** Provides information to create an allowed operations policy. */
  @DataProvider (name = "AllowedOperationsSet")
  public Object[][] initAllowedOperationsSet()
  {
    TreeSet<AllowedOperations> ops_all = newTreeSet2(
        ADD, BIND, COMPARE, DELETE, EXTENDED, INEQUALITY_SEARCH, MODIFY, RENAME, SEARCH);
    TreeSet<AllowedOperations> ops_search = newTreeSet2(INEQUALITY_SEARCH, SEARCH);
    TreeSet<AllowedOperations> ops_add_del = newTreeSet2(ADD, DELETE);

    return new Object[][] {
      // allowed operations, operation to test, success
      {ops_all, OperationType.ABANDON, true},
      {ops_all, OperationType.ADD, true},
      {ops_all, OperationType.BIND, true},
      {ops_all, OperationType.COMPARE, true},
      {ops_all, OperationType.DELETE, true},
      {ops_all, OperationType.EXTENDED, true},
      {ops_all, OperationType.MODIFY, true},
      {ops_all, OperationType.MODIFY_DN, true},
      {ops_all, OperationType.SEARCH, true},
      {ops_all, OperationType.UNBIND, true},
      {ops_search, OperationType.SEARCH, true},
      {ops_search, OperationType.ADD, false},
      {ops_search, OperationType.BIND, false},
      {ops_add_del, OperationType.ADD, true},
      {ops_add_del, OperationType.DELETE, true},
      {ops_add_del, OperationType.EXTENDED, false}
    };
  }

  //===========================================================================
  //                        T E S T   C A S E S
  //===========================================================================

  private <T extends java.lang.Enum<T>> TreeSet<T> newTreeSet2(T op1, T... ops)
  {
    return new TreeSet<T>(EnumSet.of(op1, ops));
  }

  /** Tests the "allowed attributes" policy. */
  @Test (dataProvider = "AllowedAttributesSet", groups = "virtual")
  public void testAllowedAttributes(
          final SortedSet<String> allowedAttributes,
          String searchFilter,
          boolean success)
          throws Exception
  {
    ArrayList<LocalizableMessage> messages = new ArrayList<LocalizableMessage>();

    RequestFilteringPolicyFactory factory = new RequestFilteringPolicyFactory();
    RequestFilteringPolicy policy = factory.createQOSPolicy(new MockRequestFilteringQOSPolicyCfg() {

      @Override
      public SortedSet<String> getAllowedAttributes()
      {
        return Collections.unmodifiableSortedSet(allowedAttributes);
      }

    });

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch("dc=example,dc=com", BASE_OBJECT, searchFilter);
    assertEquals(policy.isAllowed(search, messages), success);
  }

  /** Tests the "prohibited operations" policy. */
  @Test (dataProvider = "ProhibitedAttributesSet", groups = "virtual")
  public void testProhibitedAttributes(
          final SortedSet<String> prohibitedAttributes,
          String searchFilter,
          boolean success)
          throws Exception
  {
    ArrayList<LocalizableMessage> messages = new ArrayList<LocalizableMessage>();

    RequestFilteringPolicyFactory factory = new RequestFilteringPolicyFactory();
    RequestFilteringPolicy policy = factory.createQOSPolicy(new MockRequestFilteringQOSPolicyCfg() {

      @Override
      public SortedSet<String> getProhibitedAttributes()
      {
        return Collections.unmodifiableSortedSet(prohibitedAttributes);
      }

    });

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch("dc=example,dc=com", BASE_OBJECT, searchFilter);
    assertEquals(policy.isAllowed(search, messages), success);
  }

  /**
   * Tests the "allowed search scopes" policy.
   */
  @Test (dataProvider = "AllowedSearchScopesSet", groups = "virtual")
  public void testAllowedSearchScopes(
          final SortedSet<AllowedSearchScopes> allowedScopes,
          SearchScope searchScope,
          boolean success)
          throws Exception
  {
    ArrayList<LocalizableMessage> messages = new ArrayList<LocalizableMessage>();

    RequestFilteringPolicyFactory factory = new RequestFilteringPolicyFactory();
    RequestFilteringPolicy policy = factory.createQOSPolicy(new MockRequestFilteringQOSPolicyCfg() {

      @Override
      public SortedSet<AllowedSearchScopes> getAllowedSearchScopes()
      {
        return Collections.unmodifiableSortedSet(allowedScopes);
      }

    });

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch("dc=example,dc=com", searchScope, "objectclass=*");
    assertEquals(policy.isAllowed(search, messages), success);
  }

  /**
   * Tests the "allowed subtrees" policy.
   */
  @Test (dataProvider = "AllowedSubtreesSet", groups = "virtual")
  public void testAllowedSubtrees(
          final SortedSet<DN> allowedSubtrees,
          String searchSubtree,
          boolean success)
          throws Exception
  {
    ArrayList<LocalizableMessage> messages = new ArrayList<LocalizableMessage>();

    RequestFilteringPolicyFactory factory = new RequestFilteringPolicyFactory();
    RequestFilteringPolicy policy = factory.createQOSPolicy(new MockRequestFilteringQOSPolicyCfg() {

      @Override
      public SortedSet<DN> getAllowedSubtrees()
      {
        return Collections.unmodifiableSortedSet(allowedSubtrees);
      }

    });

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch(searchSubtree, WHOLE_SUBTREE, "objectclass=*");
    assertEquals(policy.isAllowed(search, messages), success);
  }

  /**
   * Tests the "prohibited subtrees" policy.
   */
  @Test (dataProvider = "ProhibitedSubtreesSet", groups = "virtual")
  public void testProhibitedSubtrees(
          final SortedSet<DN> prohibitedSubtrees,
          String searchSubtree,
          boolean success)
          throws Exception
  {
    ArrayList<LocalizableMessage> messages = new ArrayList<LocalizableMessage>();

    RequestFilteringPolicyFactory factory = new RequestFilteringPolicyFactory();
    RequestFilteringPolicy policy = factory.createQOSPolicy(new MockRequestFilteringQOSPolicyCfg() {

      @Override
      public SortedSet<DN> getProhibitedSubtrees()
      {
        return Collections.unmodifiableSortedSet(prohibitedSubtrees);
      }

    });

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch(searchSubtree, WHOLE_SUBTREE, "objectclass=*");
    assertEquals(policy.isAllowed(search, messages), success);
  }

  /**
   * Tests the subtrees policy.
   */
  @Test (dataProvider = "ComplexSubtreesSet", groups = "virtual")
  public void testComplexSubtrees(
          final SortedSet<DN> allowedSubtrees,
          final SortedSet<DN> prohibitedSubtrees,
          String searchSubtree,
          boolean success)
          throws Exception
  {
    ArrayList<LocalizableMessage> messages = new ArrayList<LocalizableMessage>();

    RequestFilteringPolicyFactory factory = new RequestFilteringPolicyFactory();
    RequestFilteringPolicy policy = factory.createQOSPolicy(new MockRequestFilteringQOSPolicyCfg() {

      @Override
      public SortedSet<DN> getAllowedSubtrees()
      {
        return Collections.unmodifiableSortedSet(allowedSubtrees);
      }

      @Override
      public SortedSet<DN> getProhibitedSubtrees()
      {
        return Collections.unmodifiableSortedSet(prohibitedSubtrees);
      }

    });

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch(searchSubtree, WHOLE_SUBTREE, "objectclass=*");
    assertEquals(policy.isAllowed(search, messages), success);
  }


  /**
   * Tests the allowed operations policy.
   */
   @Test (dataProvider = "AllowedOperationsSet", groups = "virtual")
   public void testAllowedOperations(
           final SortedSet<AllowedOperations> allowedOps,
           OperationType type,
           boolean success)
           throws Exception
   {
     ArrayList<LocalizableMessage> messages = new ArrayList<LocalizableMessage>();

     RequestFilteringPolicyFactory factory = new RequestFilteringPolicyFactory();
     RequestFilteringPolicy policy = factory.createQOSPolicy(new MockRequestFilteringQOSPolicyCfg() {

       @Override
       public SortedSet<AllowedOperations> getAllowedOperations()
       {
         return Collections.unmodifiableSortedSet(allowedOps);
       }

     });

     InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
     PreParseOperation op = null;

     switch (type) {
       case ABANDON:
         return;
       case ADD:
         Entry e = TestCaseUtils.makeEntry(
                 "dn: ou=People,o=ldif",
                 "objectClass: top",
                 "objectClass: organizationalUnit",
                 "ou: People");

         op = (PreParseAddOperation) conn.processAdd(e);
         break;
       case BIND:
         op = (PreParseBindOperation) conn.processSimpleBind(
                 "cn=Directory Manager", "password");
         break;
       case COMPARE:
         op = (PreParseCompareOperation) conn.processCompare(
                 "uid=user.1,ou=People,o=ldif", "uid", "user.1");
         break;
       case DELETE:
         op = (PreParseDeleteOperation) conn.processDelete(
                 "uid=user.1,ou=people,dc=example,dc=com");
         break;
       case EXTENDED:
         op = (PreParseExtendedOperation) conn.processExtendedOperation(
                 OID_WHO_AM_I_REQUEST, null);
         break;
       case MODIFY:
         ArrayList<Modification> mods = new ArrayList<Modification>();
         Attribute attributeToModify = Attributes.create("attr", "newVal");
         mods.add(new Modification(ModificationType.ADD, attributeToModify));
         op = (PreParseModifyOperation) conn.processModify(
                 DN.valueOf("uid=user.1,ou=people,dc=example,dc=com"), mods);
         break;
       case MODIFY_DN:
         op = (PreParseModifyDNOperation) conn.processModifyDN(
                 "uid=user.1,ou=people,dc=example,dc=com",
                 "uid=usr.1,ou=people,dc=example,dc=com", true);
         break;
       case SEARCH:
         op = conn.processSearch("dc=example,dc=com", WHOLE_SUBTREE, "uid>=user.1");
         break;
       case UNBIND:
         return;
     }

     assertEquals(policy.isAllowed(op, messages), success);
   }
}
