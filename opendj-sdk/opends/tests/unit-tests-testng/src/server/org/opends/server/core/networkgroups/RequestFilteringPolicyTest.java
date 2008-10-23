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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;


import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import org.opends.messages.Message;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.
        NetworkGroupRequestFilteringPolicyCfgDefn.AllowedOperations;
import org.opends.server.admin.std.meta.
        NetworkGroupRequestFilteringPolicyCfgDefn.AllowedSearchScopes;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.OperationType;
import org.opends.server.types.SearchScope;
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

import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;


/*
 * This set of tests test the resource limits.
 */
public class RequestFilteringPolicyTest extends DirectoryServerTestCase {
  //===========================================================================
  //
  //                      B E F O R E    C L A S S
  //
  //===========================================================================

  /**
   * Sets up the environment for performing the tests in this suite.
   *
   * @throws Exception if the environment could not be set up.
   */
  @BeforeClass
  public void setUp()
    throws Exception
  {
    // This test suite depends on having the schema available,
    // so we'll start the server.
    TestCaseUtils.startServer();
  }


  //===========================================================================
  //
  //                      D A T A    P R O V I D E R
  //
  //===========================================================================

  /* Provides information to create an allowedAttribute policy and a filter
   * to test.
   */
  @DataProvider (name = "AllowedAttributesSet")
  public Object[][] initAllowedAttributesSet()
  {
    TreeSet<String> allowedAttr_uid_cn = new TreeSet<String>();
    allowedAttr_uid_cn.add("uid");
    allowedAttr_uid_cn.add("cn");

    TreeSet<String> allowedAttr_cn = new TreeSet<String>();
    allowedAttr_cn.add("cn");

    TreeSet<String> allowedAttr_uid = new TreeSet<String>();
    allowedAttr_uid.add("uid");

    Object[][] myData = {
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

    return myData;
  }

  /* Provides information to create a prohibitedAttribute policy and a filter
   * to test.
   */
  @DataProvider (name = "ProhibitedAttributesSet")
  public Object[][] initProhibitedAttributesSet()
  {
    TreeSet<String> prohibitedAttr_uid = new TreeSet<String>();
    prohibitedAttr_uid.add("uid");

    TreeSet<String> prohibitedAttr_cn = new TreeSet<String>();
    prohibitedAttr_cn.add("cn");

    Object[][] myData = {
      // prohibited attributes, attribute to test, success
      {prohibitedAttr_uid, "uid=*", false},
      {prohibitedAttr_cn, "uid=*", true},
      {prohibitedAttr_cn, "(&(uid=user.1)(cn=*))", false}
    };

    return myData;
  }

  /* Provides information to create an allowedSearchScopes policy and a
   * scope to test.
   */
  @DataProvider (name = "AllowedSearchScopesSet")
  public Object[][] initAllowedSearchScopesSet()
  {
    TreeSet<AllowedSearchScopes> scopes_all =
            new TreeSet<AllowedSearchScopes>();
    scopes_all.add(AllowedSearchScopes.BASE);
    scopes_all.add(AllowedSearchScopes.CHILDREN);
    scopes_all.add(AllowedSearchScopes.ONE);
    scopes_all.add(AllowedSearchScopes.SUB);

    TreeSet<AllowedSearchScopes> scope_base =
            new TreeSet<AllowedSearchScopes>();
    scope_base.add(AllowedSearchScopes.BASE);

    TreeSet<AllowedSearchScopes> scope_children =
            new TreeSet<AllowedSearchScopes>();
    scope_children.add(AllowedSearchScopes.CHILDREN);

    TreeSet<AllowedSearchScopes> scope_one =
            new TreeSet<AllowedSearchScopes>();
    scope_one.add(AllowedSearchScopes.ONE);

    TreeSet<AllowedSearchScopes> scope_sub =
            new TreeSet<AllowedSearchScopes>();
    scope_sub.add(AllowedSearchScopes.SUB);

    Object[][] myData = {
      // allowed search scopes, scope to test, success
      {scopes_all, SearchScope.BASE_OBJECT, true},
      {scope_base, SearchScope.BASE_OBJECT, true},
      {scope_base, SearchScope.SINGLE_LEVEL, false},
      {scope_base, SearchScope.SUBORDINATE_SUBTREE, false},
      {scope_base, SearchScope.WHOLE_SUBTREE, false},
      {scope_children, SearchScope.BASE_OBJECT, false},
      {scope_children, SearchScope.SINGLE_LEVEL, false},
      {scope_children, SearchScope.SUBORDINATE_SUBTREE, true},
      {scope_children, SearchScope.WHOLE_SUBTREE, false},
      {scope_one, SearchScope.BASE_OBJECT, false},
      {scope_one, SearchScope.SINGLE_LEVEL, true},
      {scope_one, SearchScope.SUBORDINATE_SUBTREE, false},
      {scope_one, SearchScope.WHOLE_SUBTREE, false},
      {scope_sub, SearchScope.BASE_OBJECT, false},
      {scope_sub, SearchScope.SINGLE_LEVEL, false},
      {scope_sub, SearchScope.SUBORDINATE_SUBTREE, false},
      {scope_sub, SearchScope.WHOLE_SUBTREE, true}
    };

    return myData;
  }

  /* Provides information to create a allowedSubtree policy and
   * a subtree search to test.
   */
  @DataProvider (name = "AllowedSubtreesSet")
  public Object[][] initAllowedSubtreesSet()
          throws DirectoryException
  {
    TreeSet<DN> subtrees1 = new TreeSet<DN>();
    subtrees1.add(DN.decode("ou=people,dc=example,dc=com"));

    TreeSet<DN> subtrees2 = new TreeSet<DN>();
    subtrees2.add(DN.decode("ou=test,dc=example,dc=com"));

    TreeSet<DN> subtrees3 = new TreeSet<DN>();
    subtrees3.add(DN.decode("dc=example,dc=com"));

    TreeSet<DN> subtrees4 = new TreeSet<DN>();
    subtrees4.add(DN.decode("dc=example,dc=com"));
    subtrees4.add(DN.decode("dc=test,dc=com"));

    Object[][] myData = {
      // allowed subtrees, subtree to test, success
      {subtrees1, "ou=people,dc=example,dc=com", true},
      {subtrees2, "ou=people,dc=example,dc=com", false},
      {subtrees3, "ou=people,dc=example,dc=com", true},
      {subtrees1, "dc=example,dc=com", false},
      {subtrees4, "dc=example,dc=com", true},
      {subtrees4, "ou=people,dc=example,dc=com", true}
    };

    return myData;
  }

  /* Provides information to create a prohibitedSubtree policy and
   * a subtree search to test.
   */
  @DataProvider (name = "ProhibitedSubtreesSet")
  public Object[][] initProhibitedSubtreesSet() throws DirectoryException
  {
    TreeSet<DN> subtrees1 = new TreeSet<DN>();
    subtrees1.add(DN.decode("ou=people,dc=example,dc=com"));

    TreeSet<DN> subtrees2 = new TreeSet<DN>();
    subtrees2.add(DN.decode("ou=test,dc=example,dc=com"));

    TreeSet<DN> subtrees3 = new TreeSet<DN>();
    subtrees3.add(DN.decode("dc=example,dc=com"));

    TreeSet<DN> subtrees4 = new TreeSet<DN>();
    subtrees4.add(DN.decode("dc=example,dc=com"));
    subtrees4.add(DN.decode("dc=test,dc=com"));

    Object[][] myData = {
      // prohibited subtrees, subtree to test, success
      {subtrees1, "ou=people,dc=example,dc=com", false},
      {subtrees2, "ou=people,dc=example,dc=com", true},
      {subtrees3, "ou=people,dc=example,dc=com", false},
      {subtrees1, "dc=example,dc=com", true},
      {subtrees4, "ou=people,dc=example,dc=com", false}
    };

    return myData;
  }

  /* Provides information to create a complex subtree policy and a
   * subtree search to test.
   */
  @DataProvider (name = "ComplexSubtreesSet")
  public Object[][] initComplexSubtreesSet() throws DirectoryException
  {
    TreeSet<DN> subtrees_root = new TreeSet<DN>();
    subtrees_root.add(DN.decode("dc=example,dc=com"));

    TreeSet<DN> subtrees_people = new TreeSet<DN>();
    subtrees_people.add(DN.decode("ou=people,dc=example,dc=com"));

    TreeSet<DN> subtrees_entry = new TreeSet<DN>();
    subtrees_entry.add(DN.decode("uid=user.1,ou=people,dc=example,dc=com"));

    Object[][] myData = {
      // allowed subtree, prohibited subtree, subtree to test, success
      {subtrees_root, subtrees_people, "dc=example,dc=com", true},
      {subtrees_root, subtrees_people, "ou=people,dc=example,dc=com", false},
      {subtrees_root, subtrees_entry, "ou=people,dc=example,dc=com", true},
      {null, subtrees_people, "dc=example,dc=com", true},
      {null, subtrees_people, "ou=people,dc=example,dc=com", false}
    };
    return myData;
  }

  /* Provides information to create an allowed operations policy.
   */
  @DataProvider (name = "AllowedOperationsSet")
  public Object[][] initAllowedOperationsSet()
  {
    TreeSet<AllowedOperations> ops_all = new TreeSet<AllowedOperations>();
    ops_all.add(AllowedOperations.ADD);
    ops_all.add(AllowedOperations.BIND);
    ops_all.add(AllowedOperations.COMPARE);
    ops_all.add(AllowedOperations.DELETE);
    ops_all.add(AllowedOperations.EXTENDED);
    ops_all.add(AllowedOperations.INEQUALITY_SEARCH);
    ops_all.add(AllowedOperations.MODIFY);
    ops_all.add(AllowedOperations.RENAME);
    ops_all.add(AllowedOperations.SEARCH);

    TreeSet<AllowedOperations> ops_search = new TreeSet<AllowedOperations>();
    ops_search.add(AllowedOperations.INEQUALITY_SEARCH);
    ops_search.add(AllowedOperations.SEARCH);

    TreeSet<AllowedOperations> ops_add_del = new TreeSet<AllowedOperations>();
    ops_add_del.add(AllowedOperations.ADD);
    ops_add_del.add(AllowedOperations.DELETE);

    Object[][] myData = {
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
    return myData;
  }

  //===========================================================================
  //
  //                        T E S T   C A S E S
  //
  //===========================================================================

  /**
   * Tests the "allowed attributes" policy
   */
  @Test (dataProvider = "AllowedAttributesSet", groups = "virtual")
  public void testAllowedAttributes(
          Set<String> allowedAttributes,
          String searchFilter,
          boolean success)
          throws DirectoryException, LDAPException
  {
    ArrayList<Message> messages = new ArrayList<Message>();
    RequestFilteringPolicy policy = new RequestFilteringPolicy(null);
    policy.setAllowedAttributes(allowedAttributes);

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch(
        DN.decode("dc=example,dc=com"),
        SearchScope.BASE_OBJECT,
        LDAPFilter.decode(searchFilter).toSearchFilter());

    boolean check = policy.checkPolicy(search, messages);
    if (success) {
      assertTrue(check);
    } else {
      assertFalse(check);
    }
  }

  /**
   * Tests the "prohibited operations" policy
   */
  @Test (dataProvider = "ProhibitedAttributesSet", groups = "virtual")
  public void testProhibitedAttributes(
          Set<String> prohibitedAttributes,
          String searchFilter,
          boolean success)
          throws DirectoryException, LDAPException
  {
    ArrayList<Message> messages = new ArrayList<Message>();
    RequestFilteringPolicy policy = new RequestFilteringPolicy(null);
    policy.setProhibitedAttributes(prohibitedAttributes);

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch(
        DN.decode("dc=example,dc=com"),
        SearchScope.BASE_OBJECT,
        LDAPFilter.decode(searchFilter).toSearchFilter());

    boolean check = policy.checkPolicy(search, messages);
    if (success) {
      assertTrue(check);
    } else {
      assertFalse(check);
    }
  }

  /**
   * Tests the "allowed search scopes" policy.
   */
  @Test (dataProvider = "AllowedSearchScopesSet", groups = "virtual")
  public void testAllowedSearchScopes(
          Set<AllowedSearchScopes> allowedScopes,
          SearchScope searchScope,
          boolean success)
          throws DirectoryException, LDAPException
  {
    ArrayList<Message> messages = new ArrayList<Message>();
    RequestFilteringPolicy policy = new RequestFilteringPolicy(null);
    policy.setAllowedSearchScopes(allowedScopes);

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch(
            DN.decode("dc=example,dc=com"),
            searchScope,
            LDAPFilter.decode("objectclass=*").toSearchFilter());

    boolean check = policy.checkPolicy(search, messages);
    if (success) {
      assertTrue(check);
    } else {
      assertFalse(check);
    }
  }

  /**
   * Tests the "allowed subtrees" policy.
   */
  @Test (dataProvider = "AllowedSubtreesSet", groups = "virtual")
  public void testAllowedSubtrees(
          Set<DN> allowedSubtrees,
          String searchSubtree,
          boolean success)
          throws DirectoryException, LDAPException
  {
    ArrayList<Message> messages = new ArrayList<Message>();
    RequestFilteringPolicy policy = new RequestFilteringPolicy(null);
    policy.setAllowedSubtrees(allowedSubtrees);

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch(
            DN.decode(searchSubtree),
            SearchScope.WHOLE_SUBTREE,
            LDAPFilter.decode("objectclass=*").toSearchFilter());

    boolean check = policy.checkPolicy(search, messages);
    if (success) {
      assertTrue(check);
    } else {
      assertFalse(check);
    }
  }

  /**
   * Tests the "prohibited subtrees" policy.
   */
  @Test (dataProvider = "ProhibitedSubtreesSet", groups = "virtual")
  public void testProhibitedSubtrees(
          Set<DN> prohibitedSubtrees,
          String searchSubtree,
          boolean success)
          throws DirectoryException, LDAPException
  {
    ArrayList<Message> messages = new ArrayList<Message>();
    RequestFilteringPolicy policy = new RequestFilteringPolicy(null);
    policy.setProhibitedSubtrees(prohibitedSubtrees);

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch(
            DN.decode(searchSubtree),
            SearchScope.WHOLE_SUBTREE,
            LDAPFilter.decode("objectclass=*").toSearchFilter());

    boolean check = policy.checkPolicy(search, messages);
    if (success) {
      assertTrue(check);
    } else {
      assertFalse(check);
    }
  }

  /**
   * Tests the subtrees policy.
   */
  @Test (dataProvider = "ComplexSubtreesSet", groups = "virtual")
  public void testComplexSubtrees(
          Set<DN> allowedSubtrees,
          Set<DN> prohibitedSubtrees,
          String searchSubtree,
          boolean success)
          throws DirectoryException, LDAPException
  {
    ArrayList<Message> messages = new ArrayList<Message>();
    RequestFilteringPolicy policy = new RequestFilteringPolicy(null);
    policy.setAllowedSubtrees(allowedSubtrees);
    policy.setProhibitedSubtrees(prohibitedSubtrees);

    InternalClientConnection conn = new InternalClientConnection(DN.NULL_DN);
    InternalSearchOperation search = conn.processSearch(
            DN.decode(searchSubtree),
            SearchScope.WHOLE_SUBTREE,
            LDAPFilter.decode("objectclass=*").toSearchFilter());

    boolean check = policy.checkPolicy(search, messages);
    if (success) {
      assertTrue(check);
    } else {
      assertFalse(check);
    }
  }

  /**
   * Tests the allowed operations policy.
   */
   @Test (dataProvider = "AllowedOperationsSet", groups = "virtual")
   public void testAllowedOperations(
           Set<AllowedOperations> allowedOps,
           OperationType type,
           boolean success)
           throws DirectoryException, LDAPException, Exception
   {
     ArrayList<Message> messages = new ArrayList<Message>();
     RequestFilteringPolicy policy = new RequestFilteringPolicy(null);
     policy.setAllowedOperations(allowedOps);

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
                 DN.decode("uid=user.1,ou=people,dc=example,dc=com"), mods);
         break;
       case MODIFY_DN:
         op = (PreParseModifyDNOperation) conn.processModifyDN(
                 "uid=user.1,ou=people,dc=example,dc=com",
                 "uid=usr.1,ou=people,dc=example,dc=com", true);
         break;
       case SEARCH:
         op = conn.processSearch(DN.decode("dc=example,dc=com"),
            SearchScope.WHOLE_SUBTREE,
            LDAPFilter.decode("uid>=user.1").toSearchFilter());
         break;
       case UNBIND:
         return;
     }

     boolean check = policy.checkPolicy(op, messages);
     if (success) {
       assertTrue(check);
     } else {
       assertFalse(check);
     }
   }
}
