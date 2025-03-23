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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 * Portions Copyright 2013 Manuel Gaupp
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.forgerock.opendj.ldap.tools.LDAPCompare;
import com.forgerock.opendj.ldap.tools.LDIFDiff;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.LDAPResultCode;
import com.forgerock.opendj.ldap.tools.LDAPModify;
import com.forgerock.opendj.ldap.tools.LDAPSearch;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * These are more functional tests than unit tests.  They go directly over
 * LDAP for all of their operations.  We use the builtin LDAPModify,
 * LDAPSearch, etc. tools to do this, as well as the ones that can
 * do similar operations directly on LDIF (e.g. LDIFDiff).  This is
 * probably the easiest way to test the code, and it makes it easy to test
 * failures outside of the unit test by just running the ldif code
 * directly.
 * <br>
 * Most of the complexity is in the DataProviders because they try to
 * squeeze everything they can out of what we have.  We've scaled some of this
 * back to make the tests run a little bit faster.  If the tests fail quietly,
 * then there is likely a problem in a DataProvider (e.g. a RuntimeException).
 * In this case, running with -Dorg.opends.test.suppressOutput=false should
 * help to diagnose the problem.
 * <br>
 * Most of the redundancy and error-prone-ness has also been factored out.
 * For instance, in general the code doesn't craft the ACIs directly; instead
 * they are built by buildAciValue, so that we are less likely to screw up
 * the syntax.
 */
@SuppressWarnings("javadoc")
@Test(singleThreaded = true, groups="slow")
public class AciTests extends AciTestCase {
// TODO: test modify use cases
// TODO: test searches where we expect a subset of attributes and entries
// TODO: test delete
// TODO: test more combinations of attributes
// TODO: test multiple permission bind rules in the same ACI
// TODO: test more invalid filters.  We should have at least one for each concept in the spec.
// TODO: test more with network addresses once this is working
// TODO: test ipv6
// TODO: test stuff happening in parallel!
// TODO: test ACI evaluation on adding, replacing, and with other operations
// TODO: check bypass-acl and modify-acl
// TODO: should we check that we get an error message on failures?
// TODO: test that the target has to be a subordinate
// TODO: test aci's with funky spacing
// TODO: Test anonymous access, i.e. all vs anyone
// TODO: Test ldap:///parent
// TODO: Test userattr

  /**
   * Tests are disabled this way because a class-level @Test(enabled=false)
   * doesn't appear to work.
   */
  private static final boolean TESTS_ARE_DISABLED = false;


  /**
   * This is used to lookup the day of the week from the calendar field.
   * The calendar field is 1 based and starts with sun.  We make [0] point
   * to 'sat' instead of a bogus value since we need to be able to find the set of days without a
   * specific day.  It needs to be at the top since it's used by other
   * static initialization.
   */
  private static final String[] DAYS_OF_WEEK =
     {"sat", "sun", "mon", "tue", "wed", "thu", "fri", "sat"};

  private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HHmm");

// -----------------------------------------------------------------------------
//  USERS
// -----------------------------------------------------------------------------

  private static final String DIR_MGR_DN = "cn=Directory Manager";
  private static final String DIR_MGR_PW = "password";

  private static final String ADMIN_DN = "cn=aci admin,dc=example,dc=com";
  private static final String ADMIN_PW = "PASSWORD";
  private static final String ADMIN_DN_LDAP_URL = "ldap:///" + ADMIN_DN;

  private static final String USER_DN = "cn=some user,dc=example,dc=com";
  private static final String USER_PW = "userPass";

  private static final String ANNONYMOUS_DN = "<<anonymous>>";
  private static final String ANNONYMOUS_PW = "<<no password>>";

  private static final Map<String,String> DN_TO_PW;
  static {
    Map<String,String> dnToPw = new HashMap<>();
    dnToPw.put(DIR_MGR_DN, DIR_MGR_PW);
    dnToPw.put(ADMIN_DN, ADMIN_PW);
    dnToPw.put(ANNONYMOUS_DN, ANNONYMOUS_PW);
    DN_TO_PW = Collections.unmodifiableMap(dnToPw);
  }

  private static final String OBJECTCLASS_STAR = "(objectclass=*)";

  private static final String SCOPE_BASE = "base";
  private static final String SCOPE_ONE = "one";
  private static final String SCOPE_SUB = "sub";

// -----------------------------------------------------------------------------
//
// -----------------------------------------------------------------------------

  private static final String MONITOR_DN = "cn=monitor";
  private static final String OU_BASE_DN = "ou=acitest,dc=example,dc=com";
  private static final String OU_GROUPS_DN = "ou=groups,dc=example,dc=com";
 //These entries are used to test groupdn, roledn and userattr stuff.
  private static final String OU_GROUP_1_DN = "cn=group1," + OU_GROUPS_DN;
  private static final String OU_GROUP_2_DN = "cn=group2," + OU_GROUPS_DN;
  //End group entries.
  /** Used by modrdn new superior */
  private static final String MANAGER_NEW_DN =
                                        "cn=new managers," + OU_BASE_DN;
  private static final String MGR_NEW_DN_URL = "ldap:///" + MANAGER_NEW_DN;
  private static final String MANAGER_DN = "cn=the managers," + OU_BASE_DN;
  private static final String MGR_DN_URL = "ldap:///" + MANAGER_DN;
  //These entries are going to be used to test userattr parent stuff.
  private static final String SALES_DN = "cn=sales dept," + MANAGER_DN;
  private static final String SALES_NEW_DN = "cn=sales dept," + MANAGER_NEW_DN;
  private static final String SALES_USER_1 = "cn=sales1 person," + SALES_DN;
  private static final String SALES_USER_NEW_1 =
                                           "cn=sales1 person," + SALES_NEW_DN;
  private static final String SALES_USER_2 = "cn=sales2 person," + SALES_DN;
  private static final String SALES_USER_3 = "cn=sales3 person," + SALES_DN;
  private static final String LEVEL_1_USER_URL =
                               "ldap:///??base?(cn=level1 user)";
 //End userattr entries.
  private static final String OU_INNER_DN = "ou=inner," + OU_BASE_DN;
  private static final String LDAP_URL_OU_INNER = "ldap:///" + OU_INNER_DN;

  private static final String OU_LEAF_DN = "ou=leaf," + OU_INNER_DN;
  private static final String LDAP_URL_OU_LEAF = "ldap:///" + OU_LEAF_DN;

  private static final String LEVEL_1_USER_DN = "cn=level1 user," + OU_BASE_DN;
  private static final String LEVEL_2_USER_DN = "cn=level2 user," + OU_INNER_DN;
  private static final String LEVEL_3_USER_DN = "cn=level3 user," + OU_LEAF_DN;
  /** The proxy DN. */
  private static final String PROXY_USER_DN = "cn=proxy user," + OU_BASE_DN;

  /**
   * We need to delete all of these between each test.  This list needs to be
   * bottom up so that it can be handed to LDAPDelete.
   */
  private static final String[] ALL_TEST_ENTRY_DNS_BOTTOM_UP = {
    SALES_USER_1,
    SALES_USER_2,
    SALES_USER_3,
    PROXY_USER_DN,
    LEVEL_3_USER_DN,
    LEVEL_2_USER_DN,
    LEVEL_1_USER_DN,
    SALES_DN,
    OU_GROUP_2_DN,
    OU_GROUP_1_DN,
    OU_LEAF_DN,
    OU_INNER_DN,
    MANAGER_DN,
    MANAGER_NEW_DN,
    OU_GROUPS_DN,
    OU_BASE_DN,
    ADMIN_DN,
    USER_DN
  };

  private static final String BIND_RULE_USERDN_SELF = "userdn=\"ldap:///self\"";
  private static final String BIND_RULE_USERDN_ALL = "userdn=\"ldap:///all\"";
  private static final String BIND_RULE_USERDN_ADMIN = "userdn=\"ldap:///" + ADMIN_DN + "\"";
  private static final String BIND_RULE_USERDN_LEVEL_1 = "userdn=\"ldap:///" + LEVEL_1_USER_DN + "\"";
  /** The proxy userdn bind rule. */
  private static final String BIND_RULE_USERDN_PROXY =
                                     "userdn=\"ldap:///" + PROXY_USER_DN + "\"";

  private static final String BIND_RULE_USERDN_ANYONE = "userdn=\"ldap:///anyone\"";
  private static final String BIND_RULE_USERDN_PARENT = "userdn=\"ldap:///parent\"";
  private static final String BIND_RULE_USERDN_CN_RDN = "userdn=\"ldap:///CN=*,dc=example,dc=com\"";
  private static final String BIND_RULE_USERDN_NOT_UID_RDN = "userdn!=\"ldap:///uid=*,dc=example,dc=com\"";
  // @Checkstyle:off
  private static final String BIND_RULE_USERDN_UID_OR_CN_RDN = "userdn=\"ldap:///uid=*,dc=example,dc=com || ldap:///cn=*,dc=example,dc=com\"";
  private static final String BIND_RULE_USERDN_ALL_CN_ADMINS = "userdn=\"ldap:///dc=example,dc=com??sub?(cn=*admin*)\"";
  /** TODO: this might be invalid? */
  private static final String BIND_RULE_USERDN_TOP_LEVEL_CN_ADMINS = "userdn=\"ldap:///dc=example,dc=com??one?(cn=*admin*)\"";
  // @Checkstyle:on
  private static final String BIND_RULE_GROUPDN_GROUP_1 =
                                    "groupdn=\"ldap:///" + OU_GROUP_1_DN + "\"";
  private static final String BIND_RULE_IP_LOCALHOST = "ip=\"127.0.0.1\"";
  private static final String BIND_RULE_IP_LOCALHOST_WITH_MASK = "ip=\"127.0.0.1+255.255.255.254\"";
  private static final String BIND_RULE_IP_LOCALHOST_SUBNET = "ip=\"127.0.0.*\"";
  private static final String BIND_RULE_IP_LOCALHOST_SUBNET_WITH_MASK = "ip=\"127.0.0.*+255.255.255.254\"";
  private static final String BIND_RULE_IP_NOT_LOCALHOST = "ip!=\"127.0.0.1\"";
  private static final String BIND_RULE_IP_MISC_AND_LOCALHOST = "ip=\"72.5.124.61,127.0.0.1\"";
  private static final String BIND_RULE_IP_NOT_MISC_AND_LOCALHOST = "ip!=\"72.5.124.61,127.0.0.1\"";
  private static final String BIND_RULE_DNS_LOCALHOST = "dns=\"localhost\"";
  private static final String BIND_RULE_DNS_NOT_LOCALHOST = "dns!=\"localhost\"";
  private static final String BIND_RULE_DNS_ALL= "dns=\"*\"";

  private static final String BIND_RULE_THIS_HOUR = getTimeOfDayRuleNextHour();
  private static final String BIND_RULE_PREVIOUS_HOUR = getTimeOfDayRulePreviousHour();

  private static final String BIND_RULE_AUTHMETHOD_SIMPLE = "authmethod=\"simple\"";
  private static final String BIND_RULE_AUTHMETHOD_SSL = "authmethod=\"ssl\"";
  private static final String BIND_RULE_AUTHMETHOD_SASL_DIGEST_MD5 = "authmethod=\"sasl DIGEST-MD5\"";

  // @Checkstyle:off
  /** Admin, but not anonymous. */
  private static final String BIND_RULE_USERDN_NOT_ADMIN = and(not(BIND_RULE_USERDN_ADMIN), BIND_RULE_AUTHMETHOD_SIMPLE);

  private static final String BIND_RULE_TODAY = "dayofweek=\"" + getThisDayOfWeek() + "\"";
  private static final String BIND_RULE_TODAY_AND_TOMORROW = "dayofweek=\"" + getThisDayOfWeek() + "," + getTomorrowDayOfWeek() + "\"";
  private static final String BIND_RULE_NOT_TODAY = "dayofweek=\"" + getNotThisDayOfWeek() + "\"";

  private static final String BIND_RULE_USERDN_ADMIN_AND_SSL = and(BIND_RULE_USERDN_ADMIN, BIND_RULE_AUTHMETHOD_SSL);
  private static final String BIND_RULE_IP_NOT_LOCALHOST_OR_USERDN_ADMIN = or(BIND_RULE_IP_NOT_LOCALHOST, BIND_RULE_USERDN_ADMIN);

  private static final String BIND_RULE_ADMIN_AND_LOCALHOST_OR_SSL = and(BIND_RULE_USERDN_ADMIN, or(BIND_RULE_AUTHMETHOD_SSL, BIND_RULE_DNS_LOCALHOST));
  // @Checkstyle:on


  // These are made up
  // @Checkstyle:off
  private static final String BIND_RULE_GROUPDN_1 = "groupdn=\"ldap:///cn=SomeGroup,dc=example,dc=com\"";
  private static final String BIND_RULE_GROUPDN_2 = "groupdn=\"ldap:///cn=SomeGroup,dc=example,dc=com || ldap:///cn=SomeOtherGroup,dc=example,dc=com\"";
  private static final String BIND_RULE_GROUPDN_3 = "groupdn=\"ldap:///cn=SomeGroup,dc=example,dc=com || ldap:///cn=SomeOtherGroup,dc=example,dc=com || ldap:///cn=SomeThirdGroup,dc=example,dc=com\"";
  private static final String BIND_RULE_USERDN_FILTER = "userdn=\"ldap:///dc=example,dc=com??one?(|(ou=eng)(ou=acct))\"";
  // @Checkstyle:on

  //bind rule user attr ACIs
  private static final String BIND_RULE_USERATTR_USERDN = "userattr=\"manager#USERDN\"";
  private static final String BIND_RULE_USERATTR_USERDN_1 = "userattr=\"ldap:///dc=example,dc=com?owner#USERDN\"";
  private static final String BIND_RULE_USERATTR_URL = "userattr=\"cn#LDAPURL\"";
  private static final String BIND_RULE_USERATTR_GROUPDN = "userattr=\"manager#GROUPDN\"";
  private static final String BIND_RULE_USERATTR_GROUPDN_1 = "userattr=\"ldap:///dc=example,dc=com?owner#GROUPDN\"";
  private static final String BIND_RULE_USERATTR_USERDN_INHERITANCE = "userattr=\"parent[0,1,2].cn#USERDN\"";
  private static final String BIND_RULE_USERATTR_GROUPDN_INHERITANCE = "userattr=\"parent[0,1,2].cn#GROUPDN\"";
  private static final String BIND_RULE_USERATTR_VALUE = "userattr=\"manager#a manager\"";

  private static final String BIND_RULE_INVALID_DAY = "dayofweek=\"sumday\"";

  private static final String BIND_RULE_ONLY_AT_NOON = "timeofday=\"1200\"";

  private static final String BIND_RULE_NOT_AT_NOON = "timeofday!=\"1200\"";
  private static final String BIND_RULE_AFTERNOON = "timeofday>\"1200\"";
  private static final String BIND_RULE_NOON_AND_AFTER = "timeofday>=\"1200\"";
  private static final String BIND_RULE_BEFORE_NOON = "timeofday<\"1200\"";
  private static final String BIND_RULE_NOON_AND_BEFORE = "timeofday<=\"1200\"";
  // @Checkstyle:off
  //targattrfilters
  private static final String TARG_ATTR_FILTERS =  "add=cn:(!(cn=superAdmin))";
  private static final String TARG_ATTR_FILTERS_1 =  "add=cn:(!(cn=superAdmin)) && telephoneNumber:(telephoneNumber=123*)";
  private static final String TARG_ATTR_FILTERS_2 =  "add=cn:(!(cn=superAdmin)), del=sn:(!(sn=nonSuperAdmin))";
  private static final String TARG_ATTR_FILTERS_4 =  "del=cn:(&(cn=foo)(cn=f*)) && sn:(sn=joe*)";
  private static final String TARG_ATTR_FILTERS_5 = TARG_ATTR_FILTERS_1 + "," + TARG_ATTR_FILTERS_4 ;
  //targattrfilters invalids
  private static final String TARG_ATTR_FILTERS_INVALID_FILTER =  "del=cn:(&(cnfoo)(cn=f*)) && sn:(snjoe*)";
  private static final String TARG_ATTR_FILTERS_BAD_OP =  "delete=cn:(&(cn=foo)(cn=f*)) && sn:(sn=joe*)";
  private static final String TARG_ATTR_FILTERS_BAD_OP_MATCH = TARG_ATTR_FILTERS_1 + "," + TARG_ATTR_FILTERS_1 ;
  private static final String TARG_ATTR_FILTERS_BAD_FILTER_ATTR =  "del=cn:(&(cn=foo)(cn=f*)) && sn:(cn=joe*)";
  private static final String TARG_ATTR_FILTERS_BAD_FORMAT =  "delete=cn;(&(cn=foo)(cn=f*)) && sn:(sn=joe*)";
  private static final String TARG_ATTR_FILTERS_TOO_MANY_LISTS = TARG_ATTR_FILTERS_1 + "," + TARG_ATTR_FILTERS_4 + "," + TARG_ATTR_FILTERS_1;
  private static final String TARG_ATTR_FILTERS_BAD_TOK =  "delete=cn:(&(cn=foo)(cn=f*)) && sn:(sn=joe*) || pager:(pager=123-*)";
  private static final String TARG_ATTR_FILTERS_ATTR_TYPE_NAME =  "del=cn:(&(cn=foo)(cn=f*)) && 1sn_:(1sn_=joe*)";

  private static final String SELF_MODIFY_ACI = "aci: (targetattr=\"*\")(version 3.0; acl \"self modify\";allow(all) userdn=\"userdn=\"ldap:///self\";)";
  // @Checkstyle:on

  private static final String ALLOW_ALL_TO_ALL =
             buildAciValue("name", "allow all", "targetattr", "*", "allow(all)", BIND_RULE_USERDN_ALL);

  // @Checkstyle:off
  private static final String ALLOW_ALL_TO_COMPARE =
             buildAciValue("name", "allow compare", "targetattr", "*", "target", "ldap:///cn=*," + OU_LEAF_DN, "allow(compare)", BIND_RULE_USERDN_ALL);
  // @Checkstyle:on

  private static final String DENY_READ_CN_SN_IF_PERSON = buildAciValue("name",
      "deny read cn sn if person", "targetfilter", "objectClass=person",
      "targetattr", "cn || sn", "deny(read)", BIND_RULE_USERDN_ALL);

  //The ACIs for the proxy tests.
  private static final String ALLOW_PROXY_CONTROL_TO_LEVEL_1=
             buildAciValue("name", "allow proxy control", "targetcontrol",
                     OID_PROXIED_AUTH_V2, "allow(read)",
                     BIND_RULE_USERDN_LEVEL_1);

  private static final String ALLOW_PROXY_TO_IMPORT_MGR_NEW =
          buildAciValue("name", "allow proxy import new mgr new tree", "target",
                     MGR_NEW_DN_URL, "allow(import)", BIND_RULE_USERDN_PROXY);

  private static final String ALLOW_PROXY_TO_IMPORT_MGR=
             buildAciValue("name", "allow proxy import mgr tree", "target",
                     MGR_DN_URL, "allow(import)", BIND_RULE_USERDN_PROXY);

  private static final String ALLOW_PROXY_TO_EXPORT_MGR_NEW =
          buildAciValue("name", "allow proxy export new mgr new tree", "target",
                     MGR_NEW_DN_URL, "allow(export)", BIND_RULE_USERDN_PROXY);

  private static final String ALLOW_PROXY_TO_EXPORT_MGR=
             buildAciValue("name", "allow proxy export mgr tree", "target",
                     MGR_DN_URL, "allow(export)", BIND_RULE_USERDN_PROXY);

  private static final String ALLOW_PROXY_TO_WRITE_RDN_ATTRS=
           buildAciValue("name", "allow proxy write to RDN attrs", "targetattr",
                     "uid || cn || sn", "allow(write)", BIND_RULE_USERDN_PROXY);

  private static final String ALLOW_PROXY_TO_MOVED_ENTRY =
          buildAciValue("name", "allow proxy to moved entry", "targetattr", "*",
                     "allow(search,read)", BIND_RULE_USERDN_PROXY);

   private static final String ALLOW_PROXY_TO_LEVEL1 =
          buildAciValue("name", "allow proxy to userdn level1", "targetattr", "*",
                     "allow(proxy)", BIND_RULE_USERDN_LEVEL_1);

  // @Checkstyle:off
  private static final String ALLOW_ALL_TO_IMPORT_MGR_NEW =
             buildAciValue("name", "allow import mgr new tree", "target", MGR_NEW_DN_URL, "allow(import)", BIND_RULE_USERDN_ALL);

  private static final String ALLOW_ALL_TO_IMPORT_MGR=
             buildAciValue("name", "allow import mgr tree", "target", MGR_DN_URL, "allow(import)", BIND_RULE_USERDN_ALL);

  private static final String ALLOW_ALL_TO_EXPORT_MGR_NEW =
             buildAciValue("name", "allow export mgr new tree", "target", MGR_NEW_DN_URL, "allow(export)", BIND_RULE_USERDN_ALL);

  private static final String ALLOW_ALL_TO_EXPORT_MGR=
             buildAciValue("name", "allow export mgr tree", "target", MGR_DN_URL, "allow(export)", BIND_RULE_USERDN_ALL);

  private static final String ALLOW_ALL_TO_WRITE_RDN_ATTRS=
             buildAciValue("name", "allow write to RDN attrs", "targetattr", "uid || cn || sn", "allow(write)", BIND_RULE_USERDN_ALL);

  private static final String ALLOW_ALL_TO_MOVED_ENTRY =
             buildAciValue("name", "allow all to moved", "targetattr", "*", "allow(search,read)", BIND_RULE_USERDN_ALL);

  private static final String ALLOW_ALL_TO_SELFWRITE =
             buildAciValue("name", "allow selfwrite", "targetattr", "member", "allow(selfwrite)", BIND_RULE_USERDN_ALL);

  private static final String ALLOW_ALL_TO_ADMIN =
          buildAciValue("name", "allow all to admin", "targetattr", "*", "allow(all)", BIND_RULE_USERDN_ADMIN);

  private static final String ALLOW_ALL_TO_ANYONE =
          buildAciValue("name", "allow all to anyone", "targetattr", "*", "allow(all)", BIND_RULE_USERDN_ANYONE);

  private static final String ALLOW_SEARCH_TO_GROUP1_GROUPDN =
          buildAciValue("name", "allow search to group1 groupdn", "targetattr",
                        "*", "allow(search, read)", BIND_RULE_GROUPDN_GROUP_1);

  private static final String ALLOW_SEARCH_TO_ADMIN =
          buildAciValue("name", "allow search to admin", "targetattr", "*", "allow(search, read)", BIND_RULE_USERDN_ADMIN);

  private static final String DENY_ALL_TO_ALL =
          buildAciValue("name", "deny all", "targetattr", "*", "deny(all)", BIND_RULE_USERDN_ALL);

  private static final String DENY_READ_TO_ALL =
          buildAciValue("name", "deny read", "targetattr", "*", "deny(read)", BIND_RULE_USERDN_ALL);

  private static final String DENY_SEARCH_TO_ALL =
          buildAciValue("name", "deny search", "targetattr", "*", "deny(search)", BIND_RULE_USERDN_ALL);

  private static final String ALLOW_SEARCH_TO_ALL =
          buildAciValue("name", "allow search", "targetattr", "*", "allow(search, read)", BIND_RULE_USERDN_ALL);

  private static final String ALLOW_READ_TO_ALL =
          buildAciValue("name", "allow read", "targetattr", "*", "allow(read)", BIND_RULE_USERDN_ALL);

  private static final String DENY_ALL_TO_ADMIN =
          buildAciValue("name", "deny to admin", "targetattr", "*", "deny(all)", BIND_RULE_USERDN_ADMIN);

  private static final String DENY_PERSON_OU_TO_ALL =
          buildAciValue("name", "deny person, ou to all", "targetfilter", "(|(objectclass=person)(objectclass=*))", "deny(all)", BIND_RULE_USERDN_ALL);

  private static final String DENY_ALL_OU_INNER =
          buildAciValue("name", "deny inner to all", "target", LDAP_URL_OU_INNER, "deny(all)", BIND_RULE_USERDN_ALL);

  private static final String DENY_ALL_REAL_ATTRS_VALUE =
          buildAciValue("name", "deny all attrs but 'bogus'", "targetattr!=", "bogusAttr", "deny(all)", BIND_RULE_USERDN_ALL);

  private static final String DENY_READ_REAL_ATTRS_VALUE =
          buildAciValue("name", "deny read attrs but 'bogus'", "targetattr!=", "bogusAttr", "deny(read)", BIND_RULE_USERDN_ALL);

  private static final String ALL0W_ALL_TO_ALL_OTHER_OBJECTCLASSES =
          buildAciValue("name", "allow all to non ou person", "targetattr", "*", "targetfilter", "(!(|(objectclass=organizationalunit)(objectclass=person)))", "allow(all)", BIND_RULE_USERDN_ALL);

  private static final String ALL0W_ALL_TO_ALL_OTHER_OBJECTCLASSES_ALT =
          buildAciValue("name", "allow all to non ou person", "targetattr", "*", "targetfilter!=", "(|(objectclass=organizationalunit)(objectclass=person))", "allow(all)", BIND_RULE_USERDN_ALL);

  private static final String ALLOW_WRITE_DELETE_SEARCH_TO_ALL =
          buildAciValue("name", "allow write, delete, and search,", "targetattr", "*", "allow(write, delete, search, read)", BIND_RULE_USERDN_ALL);

  private static final String DENY_WRITE_DELETE_READ_TO_ALL =
          buildAciValue("name", "deny write delete read to all", "targetattr", "*", "deny(write, delete, read)", BIND_RULE_USERDN_ALL);

  private static final String DENY_READ_TO_CN_RDN_USERS =
          buildAciValue("name", "deny read to cn rdn", "targetattr", "*", "deny(read)", BIND_RULE_USERDN_CN_RDN);

  private static final String DENY_READ_TO_UID_OR_CN_RDN_USERS =
          buildAciValue("name", "deny read to uid or cn rdn", "targetattr", "*", "deny(read)", BIND_RULE_USERDN_UID_OR_CN_RDN);

  private static final String DENY_READ_TO_NON_UID_RDN_USERS =
          buildAciValue("name", "deny read to non uid rdn users", "targetattr", "*", "deny(read)", BIND_RULE_USERDN_NOT_UID_RDN);

  private static final String DENY_READ_TO_CN_ADMINS =
          buildAciValue("name", "deny read to users with 'admin' in their cn", "targetattr", "*", "deny(read)", BIND_RULE_USERDN_ALL_CN_ADMINS);

  private static final String ALLOW_SEARCH_TO_CN_ADMINS =
          buildAciValue("name", "allow search to users with 'admin' in their cn", "targetattr", "*", "allow(search, read)", BIND_RULE_USERDN_ALL_CN_ADMINS);

  private static final String DENY_READ_TO_TOP_LEVEL_CN_ADMINS =
          buildAciValue("name", "deny read to users with 'admin' in their cn", "targetattr", "*", "deny(read)", BIND_RULE_USERDN_TOP_LEVEL_CN_ADMINS);

  private static final String DENY_ALL_TO_LOCALHOST =
          buildAciValue("name", "deny all to localhost", "targetattr", "*", "deny(all)", BIND_RULE_IP_LOCALHOST);

  private static final String DENY_ALL_TO_LOCALHOST_WITH_MASK =
          buildAciValue("name", "deny all to localhost with mask", "targetattr", "*", "deny(all)", BIND_RULE_IP_LOCALHOST_WITH_MASK);

  private static final String DENY_ALL_TO_LOCALHOST_SUBNET =
          buildAciValue("name", "deny all to localhost subnet", "targetattr", "*", "deny(all)", BIND_RULE_IP_LOCALHOST_SUBNET);

  private static final String DENY_ALL_TO_LOCALHOST_SUBNET_WITH_MASK =
          buildAciValue("name", "deny all to localhost subnet", "targetattr", "*", "deny(all)", BIND_RULE_IP_LOCALHOST_SUBNET_WITH_MASK);

  private static final String DENY_ALL_TO_MISC_AND_LOCALHOST =
          buildAciValue("name", "deny all to misc and localhost", "targetattr", "*", "deny(all)", BIND_RULE_IP_MISC_AND_LOCALHOST);

  private static final String ALLOW_ALL_TO_NON_LOCALHOST =
          buildAciValue("name", "allow all to non-localhost", "targetattr", "*", "allow(all)", BIND_RULE_IP_NOT_LOCALHOST);

  private static final String ALLOW_ALL_TO_NON_MISC_AND_LOCALHOST =
          buildAciValue("name", "allow all to non misc and localhost", "targetattr", "*", "allow(all)", BIND_RULE_IP_NOT_MISC_AND_LOCALHOST);

  private static final String ALLOW_ALL_TO_NON_DNS_LOCALHOST =
          buildAciValue("name", "allow all to non localhost", "targetattr", "*", "allow(all)", BIND_RULE_DNS_NOT_LOCALHOST);

  private static final String ALLOW_ALL_TO_DNS_ALL =
          buildAciValue("name", "allow all to dns all", "targetattr", "*", "allow(all)", BIND_RULE_DNS_ALL);

  private static final String DENY_ALL_TO_DNS_LOCALHOST =
          buildAciValue("name", "deny all to localhost", "targetattr", "*", "deny(all)", BIND_RULE_DNS_LOCALHOST);

  private static final String ALLOW_ALL_TO_SSL =
          buildAciValue("name", "allow all to ssl", "targetattr", "*", "allow(all)", BIND_RULE_AUTHMETHOD_SSL);

  private static final String ALLOW_ALL_TO_SASL_DIGEST_MD5 =
          buildAciValue("name", "allow all to sasl DIGEST-MD5", "targetattr", "*", "allow(all)", BIND_RULE_AUTHMETHOD_SASL_DIGEST_MD5);

  private static final String DENY_ALL_TO_SIMPLE =
          buildAciValue("name", "deny all to simple", "targetattr", "*", "deny(all)", BIND_RULE_AUTHMETHOD_SIMPLE);

  private static final String ALLOW_ALL_TO_SIMPLE =
          buildAciValue("name", "allow all to simple", "targetattr", "*", "allow(all)", BIND_RULE_AUTHMETHOD_SIMPLE);

  private static final String DENY_ALL_TODAY =
          buildAciValue("name", "deny all today", "targetattr", "*", "deny(all)", BIND_RULE_TODAY);

  private static final String ALLOW_ALL_TODAY =
          buildAciValue("name", "allow all today", "targetattr", "*", "allow(all)", BIND_RULE_TODAY);

  private static final String DENY_ALL_TODAY_AND_TOMORROW =
          buildAciValue("name", "deny all today and tomorrow", "targetattr", "*", "deny(all)", BIND_RULE_TODAY_AND_TOMORROW);

  private static final String ALLOW_ALL_NOT_TODAY =
          buildAciValue("name", "allow all not today", "targetattr", "*", "allow(all)", BIND_RULE_NOT_TODAY);

  private static final String DENY_ALL_THIS_HOUR =
          buildAciValue("name", "deny this hour", "targetattr", "*", "deny(all)", BIND_RULE_THIS_HOUR);

  private static final String ALLOW_ALL_THIS_HOUR =
          buildAciValue("name", "allow this hour", "targetattr", "*", "allow(all)", BIND_RULE_THIS_HOUR);

  private static final String ALLOW_ALL_PREVIOUS_HOUR =
          buildAciValue("name", "allow previous hour", "targetattr", "*", "allow(all)", BIND_RULE_PREVIOUS_HOUR);

  private static final String ALLOW_ALL_ADMIN_AND_SSL =
          buildAciValue("name", "allow if admin and ssl", "targetattr", "*", "allow(all)", BIND_RULE_USERDN_ADMIN_AND_SSL);

  private static final String DENY_ALL_NOT_LOCALHOST_OR_ADMIN =
          buildAciValue("name", "deny if not localhost or admin", "targetattr", "*", "deny(all)", BIND_RULE_IP_NOT_LOCALHOST_OR_USERDN_ADMIN);

  /** This makes more sense as an allow all. */
  private static final String DENY_ALL_TO_ADMIN_AND_LOCALHOST_OR_SSL =
          buildAciValue("name", "deny if admin and localhost or ssl", "targetattr", "*", "deny(all)", BIND_RULE_ADMIN_AND_LOCALHOST_OR_SSL);

  private static final String ALLOW_ALL_TO_ADMIN_AND_LOCALHOST_OR_SSL =
          buildAciValue("name", "allow if admin and localhost or ssl", "targetattr", "*", "allow(all)", BIND_RULE_ADMIN_AND_LOCALHOST_OR_SSL);

  private static final String ALLOW_ALL_NOT_ADMIN =
          buildAciValue("name", "allow not admin", "targetattr", "*", "allow(all)", BIND_RULE_USERDN_NOT_ADMIN);

  private static final String ALLOW_SEARCH_TO_LOCALHOST =
          buildAciValue("name", "allow search to localhost", "targetattr", "*", "allow(search, read)", BIND_RULE_IP_LOCALHOST);

  private static final String ALLOW_SEARCH_REALATTRS_TO_LOCALHOST =
          buildAciValue("name", "allow search to localhost", "targetattr!=", "bogusAttr", "allow(search, read)", BIND_RULE_IP_LOCALHOST);

  private static final String ALLOW_SEARCH_OUR_ATTRS_TO_ADMIN =
          buildAciValue("name", "allow search to our attributes to admin", "targetattr", "objectclass||ou||cn||sn||givenname", "target", LDAP_URL_OU_INNER, "allow(search, read)", BIND_RULE_USERDN_ADMIN);

  private static final String ALLOW_SEARCH_TARGET_INNER_TO_LOCALHOST =
          buildAciValue("name", "allow search inner to localhost", "targetattr", "*", "target", LDAP_URL_OU_INNER, "allow(search, read)", BIND_RULE_IP_LOCALHOST);

  private static final String ALLOW_SEARCH_OU_AND_PERSON_TO_SIMPLE =
          buildAciValue("name", "allow search ou and person to localhost", "targetattr", "*", "targetfilter", "(|(objectclass=organizationalunit)(objectclass=person))", "allow(search, read)", BIND_RULE_AUTHMETHOD_SIMPLE);
  // @Checkstyle:on

// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
//
//   S E T U P
//
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------


  @BeforeClass
  public void setupClass() throws Exception {
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI, true);
  }



  @BeforeMethod
  public void clearBackend() throws Exception {
    deleteAllTestEntries();
  }

// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
//
//   T E S T S
//
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------

// -----------------------------------------------------------------------------
//  VALID AND INVALID ACIS
// -----------------------------------------------------------------------------

  private static final String ADMIN_LDIF_VALIDITY_TESTS = TestCaseUtils.makeLdif(
     "dn: " + ADMIN_DN,
     "objectclass: person",
     "cn: aci admin",
     "sn: admin",
     "userpassword: " + ADMIN_PW,
     "ds-privilege-name: modify-acl" );

  /** By default aci admin can do anything! */
  private static final String OU_LDIF_VALDITY_TESTS = TestCaseUtils.makeLdif(
     "dn: " + OU_BASE_DN,
     "objectclass: organizationalunit",
     "ou: acitest",
     "aci: (targetattr=\"*\")(version 3.0; acl \"test\";allow(all) userdn=\"" + ADMIN_DN_LDAP_URL + "\";)"
  );

  private static final String VALIDITY_TESTS_DIT = ADMIN_LDIF_VALIDITY_TESTS + OU_LDIF_VALDITY_TESTS;


  private static final String[] VALID_ACIS = {
    // Test each feature in isolation.
// <PASSES>
//    // TARGETS
    // @Checkstyle:off
    buildAciValue("name", "self mod", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "parenthesis (dummy) and ( ) and () test", "allow (read)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ target", "target", LDAP_URL_OU_INNER, "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ 1 targetattr", "targetattr", "cn", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ 2 targetattr", "targetattr", "cn || sn", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ 3 targetattr", "targetattr", "cn || sn || uid", "allow (write)", BIND_RULE_USERDN_SELF),
    //These are four are OpenDS specific attr names
    buildAciValue("name", "opends targetattr", "targetattr", "1-digitinfirst", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "opendstargetattr", "targetattr", "this_has_underscores", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "oid targetattr", "targetattr", " 2.16.840.1.113730.3.3.2.18.1.4", "allow (write)", BIND_RULE_USERDN_SELF),
    // OpenDJ 2.5 doesn't support acis with options
    // buildAciValue("name", "locality targetattr", "targetattr", "locality;lang-fr-ca", "allow (write)", BIND_RULE_USERDN_SELF),
    // buildAciValue("name", "complicated targetattr", "targetattr", "1ocal_ity;lang-fr-ca", "allow (write)", BIND_RULE_USERDN_SELF),

    buildAciValue("name", "w/ * targetattr", "targetattr", "*", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ non-existing attr", "targetattr", "notanattr", "allow (write)", BIND_RULE_USERDN_SELF),       // DS 5.2p4 accepts this so we should too.
    buildAciValue("name", "w/ non-existing attr", "targetattr", "cn || notanattr", "allow (write)", BIND_RULE_USERDN_SELF), // DS 5.2p4 accepts this so we should too.
    buildAciValue("name", "w/ targetfilter", "targetfilter", "(sn=admin)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetfilter", "targetfilter", "(objectclass=*)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetfilter", "targetfilter", "(objectclass=inetorgperson)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetfilter", "targetfilter", "(cn;lang-en=Jonathan Smith)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetfilter", "targetfilter", "(cn=\\4Aohn*)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetfilter", "targetfilter", "(title~=tattoos)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetfilter", "targetfilter", "(labeledUri=http://opends.org/john)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetfilter", "targetfilter", "(cn>=J)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetfilter", "targetfilter", "(2.5.4.4=Smith)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetfilter", "targetfilter", "(sn:caseExactMatch:=Smith)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetScope", "targetScope", "base", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetScope", "targetScope", "onelevel", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetScope", "targetScope", "subtree", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetScope", "targetScope", "subordinate", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ !target", "target!=", LDAP_URL_OU_INNER, "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ 1 !targetattr", "targetattr!=", "cn", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ 2 !targetattr", "targetattr!=", "cn || sn", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targetfilter", "targetfilter!=", "(sn=admin)", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targattrfilters", "targattrfilters=",  TARG_ATTR_FILTERS, "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targattrfilters", "targattrfilters=",  TARG_ATTR_FILTERS_1 , "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targattrfilters", "targattrfilters=",  TARG_ATTR_FILTERS_2 , "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "w/ targattrfilters", "targattrfilters=",  TARG_ATTR_FILTERS_5 , "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "bad_ATTR_TYPE_NAME", "targattrfilters",TARG_ATTR_FILTERS_ATTR_TYPE_NAME, "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "read", "targetattr", "*", "allow (read)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "write", "targetattr", "*", "allow (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "add", "targetattr", "*", "allow (add)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "delete", "targetattr", "*", "allow (delete)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "search", "targetattr", "*", "allow (search, read)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "compare", "targetattr", "*", "allow (compare)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "selfwrite", "targetattr", "*", "allow (selfwrite)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "all", "targetattr", "*", "allow (all)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "proxy", "targetattr", "*", "allow (proxy)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "read|write", "targetattr", "*", "allow (read, write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "read|write", "targetattr", "*", "allow (read, write, add)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "read|write", "targetattr", "*", "allow (read, write, add, delete, search, compare, selfwrite, all, proxy)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "read", "targetattr", "*", "deny (read)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "write", "targetattr", "*", "deny (write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "add", "targetattr", "*", "deny (add)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "delete", "targetattr", "*", "deny (delete)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "search", "targetattr", "*", "deny (search)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "compare", "targetattr", "*", "deny (compare)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "selfwrite", "targetattr", "*", "deny (selfwrite)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "all", "targetattr", "*", "deny (all)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "proxy", "targetattr", "*", "deny (proxy)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "read|write", "targetattr", "*", "deny (read, write)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "read|write|add", "targetattr", "*", "deny (read, write, add)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "all", "targetattr", "*", "deny (read, write, add, delete, search, compare, selfwrite, all, proxy)", BIND_RULE_USERDN_SELF),
    buildAciValue("name", "read all", "targetattr", "*", "allow (read)", BIND_RULE_USERDN_ALL),
    buildAciValue("name", "read anyone", "targetattr", "*", "allow (read)", BIND_RULE_USERDN_ANYONE),
    buildAciValue("name", "read filter", "targetattr", "*", "allow (read)", BIND_RULE_USERDN_FILTER),
    buildAciValue("name", "read parent", "targetattr", "*", "allow (read)", BIND_RULE_USERDN_PARENT),
    buildAciValue("name", "read group dn 1", "targetattr", "*", "allow (read)", BIND_RULE_GROUPDN_1),
    buildAciValue("name", "read group dn 2", "targetattr", "*", "allow (read)", BIND_RULE_GROUPDN_2),
    buildAciValue("name", "read group dn 3", "targetattr", "*", "allow (read)", BIND_RULE_GROUPDN_3),
    buildAciValue("name", "userattr", "targetattr", "*", "allow (read)", BIND_RULE_USERATTR_USERDN),
    buildAciValue("name", "userattr", "targetattr", "*", "allow (read)", BIND_RULE_USERATTR_USERDN_1),
    buildAciValue("name", "userattr", "targetattr", "*", "allow (read)", BIND_RULE_USERATTR_URL),
    buildAciValue("name", "userattr", "targetattr", "*", "allow (read)", BIND_RULE_USERATTR_GROUPDN),
    buildAciValue("name", "userattr", "targetattr", "*", "allow (read)", BIND_RULE_USERATTR_GROUPDN_1),
    buildAciValue("name", "userattr", "targetattr", "*", "allow (read)", BIND_RULE_USERATTR_USERDN_INHERITANCE),
    buildAciValue("name", "userattr", "targetattr", "*", "allow (read)", BIND_RULE_USERATTR_GROUPDN_INHERITANCE),
    buildAciValue("name", "userattr", "targetattr", "*", "allow (read)", BIND_RULE_USERATTR_VALUE),
    // @Checkstyle:on
    // BUG!  These work with DS 5.2p4, but not with OpenDS.
// <FAIL>
//    DENY_ALL_TO_LOCALHOST_SUBNET,
//    ALLOW_ALL_TO_NON_MISC_AND_LOCALHOST,
//    DENY_ALL_TO_LOCALHOST_WITH_MASK,
//    DENY_ALL_TO_LOCALHOST_SUBNET_WITH_MASK,
// </FAIL>

     ALLOW_ALL_TO_NON_DNS_LOCALHOST,
     DENY_ALL_TO_DNS_LOCALHOST,
     buildAciValue("name", "deny all to example.com", "targetattr", "*", "deny(all)", "dns=\"*.example.com\""),
     ALLOW_ALL_TO_SSL,
     ALLOW_ALL_TO_SASL_DIGEST_MD5,
     DENY_ALL_TO_SIMPLE,
     DENY_ALL_TODAY,
     DENY_ALL_TODAY_AND_TOMORROW,
     ALLOW_ALL_NOT_TODAY,

     buildAciValue("name", "allow at noon", "targetattr", "*", "allow(all)", BIND_RULE_ONLY_AT_NOON),
     buildAciValue("name", "allow at non-noon", "targetattr", "*", "allow(all)", BIND_RULE_NOT_AT_NOON),
     buildAciValue("name", "allow at afternoon", "targetattr", "*", "allow(all)", BIND_RULE_AFTERNOON),
     buildAciValue("name", "allow at noon and after", "targetattr", "*", "allow(all)", BIND_RULE_NOON_AND_AFTER),
     buildAciValue("name", "allow at before noon", "targetattr", "*", "allow(all)", BIND_RULE_BEFORE_NOON),
     buildAciValue("name", "allow at noon and before", "targetattr", "*", "allow(all)", BIND_RULE_NOON_AND_BEFORE),
     buildAciValue("name", "allow at next hour", "targetattr", "*", "allow(all)", BIND_RULE_THIS_HOUR),
     buildAciValue("name", "allow at next hour", "targetattr", "*", "allow(all)", BIND_RULE_PREVIOUS_HOUR),

     ALLOW_ALL_ADMIN_AND_SSL,
     DENY_ALL_NOT_LOCALHOST_OR_ADMIN,
     DENY_ALL_TO_ADMIN_AND_LOCALHOST_OR_SSL,
     ALLOW_ALL_NOT_ADMIN

// </PASSES>
    // TODO: bind rules for 'ip', 'dns', 'dayofweek', 'timeofday', 'authmethod'
    // TODO: combinations of these things, including multiple bind rules.
    // TODO: need to test wild cards!
  };

  private static final String[] INVALID_ACIS = {
    // Test each feature in isolation.
// <PASSES>
    "aci: ",
          buildAciValue("allow (write)", BIND_RULE_USERDN_SELF),  // No name
          buildAciValue("name", "invalid", "target", "ldap:///", "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "invalid", "target", "ldap:///not a DN", "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "invalid", "target", "ldap:///cn=", "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "invalid", "targetattr", "", "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "invalid", "targetattr", "not an attr", "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "invalid", "targetattr", "cn ||", "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "invalid", "targetattr", "not/an/attr", "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "invalid", "targetattr", "cn", "allow (write)", BIND_RULE_INVALID_DAY),
          // @Checkstyle:off
          /* Test cases for OPENDJ-433 */
          buildAciValue("name", "invalid", "targetattr", "cn", "garbage allow (read)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "invalid", "targetattr", "cn", "allow (read)", BIND_RULE_USERDN_SELF, "garbage allow (search)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "invalid", "targetattr", "cn", "allow (read)", BIND_RULE_USERDN_SELF, "allow (search)", BIND_RULE_USERDN_SELF, "garbage allow (compare)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "invalid", "targetattr", "cn", "allow (read)", BIND_RULE_USERDN_SELF, "allow (search)", BIND_RULE_USERDN_SELF, "allow (compare)", BIND_RULE_USERDN_SELF, "garbage allow (delete)", BIND_RULE_USERDN_SELF),
          // Add tests with invalid keywords : typos in "targetattr", "targattfilters", "targetfilter"
          buildAciValue("name", "bad_filters", "targattrfilters",TARG_ATTR_FILTERS_INVALID_FILTER, "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "bad_op", "targattrfilters",TARG_ATTR_FILTERS_BAD_OP, "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "bad_op_match", "targattrfilters",TARG_ATTR_FILTERS_BAD_OP_MATCH, "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "bad_filter_attr", "targattrfilters",TARG_ATTR_FILTERS_BAD_FILTER_ATTR, "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "bad_format", "targattrfilters",TARG_ATTR_FILTERS_BAD_FORMAT, "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "too_many_lists", "targattrfilters",TARG_ATTR_FILTERS_TOO_MANY_LISTS, "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "bad_tok", "targattrfilters",TARG_ATTR_FILTERS_BAD_TOK, "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "bad_targetfilter", "targetfilter","this is a bad filter", "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "bad targetScope", "targetScope", "sub_tree", "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "bad right", "targetattr", "*", "allow (read, write, add, delete, search, compare, selfwrite, all, foo)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "bad access type", "targetattr", "*", "allows (read, write, add, delete, search, compare, selfwrite, all)", BIND_RULE_USERDN_SELF),
          //no name
          buildAciValue("targetattr", "*", "allows (read, write, add, delete, search, compare, selfwrite, all)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "bad groupdn url", "targetattr", "*", "allow (read, write, add, delete, search, compare, selfwrite, all)", "groupdn=\"ldap:///bogus\""),
          buildAciValue("name", "bad groupdn url2", "targetattr", "*", "allow (read, write, add, delete, search, compare, selfwrite, all)", "groupdn=\"ldap1:///bogus\""),
          //Roledn keyword is not supported anymore.
          buildAciValue("name", "unsupported roledn", "targetattr", "*", "allow (all)", "roledn=\"ldap:///cn=foo, dc=bar\""),
          // OpenDJ 2.5 doesn't support acis with options
          buildAciValue("name", "unsupported option in targetattr", "targetattr", "locality;lang-fr-ca", "allow (write)", BIND_RULE_USERDN_SELF),
          buildAciValue("name", "complicated unsupported option in targetattr", "targetattr", "1ocal_ity;lang-fr-ca", "allow (write)", BIND_RULE_USERDN_SELF),
          // @Checkstyle:on
// </PASSES>
  };

  /**
   * This is a little bit confusing.  The first element of each array of two elements contains
   * the aci that is valid but becomes invalid if any single character is removed.
   * There has to be a lot of redundancy between the two arrays because of what
   * it takes for an aci to be minimally valid, and hence we end up doing a lot of
   * work twice.  This takes time and also reports some identical failures.
   * Therefore, we also provide a mask in the second element in the array
   * But since the aci has \" characters that are single characters, taking up
   * the space of two, we have to use another "two-column" character in the mask.
   * By convention, a character is removed if the corresponding mask character
   * is a - or a \" character.  X and \' imply that it was previously tested and
   * does not need to be tested again.
   */
  private static final String[][] INVALID_ACIS_IF_ANY_CHAR_REMOVED =
         {
           // TODO: this generates some failures.
//          {"(version3.0;acl\"\";deny(all)ip=\"1.1.1.1\";)",
//           "---------------\"\"-------------\"-------\"--"},

           // TODO: this generates some failures.
//    {"(version3.0;acl\"\";allow(read,write,add,delete,search,compare,selfwrite,all,proxy)userdn=\"ldap:///self\";)",
//     "XXXXXXXXXXXXXXX\'\'X----------------------------------------------------------------------\"-----XX-----\"XX"},

            // TODO: this generates some failures.
//          {"(version3.0;acl\"\";allow(read)userdn=\"ldap:///o=b\";)",
//           "XXXXXXXXXXXXXXX\'\'XXXXXXX----XXXXXXXX\'XXXXXXX---\'XX"},

            // TODO: this generates some failures.
//          {"(version3.0;acl\"\";allow(read)userdn=\"ldap:///o=*,o=b\";)",
//           "XXXXXXXXXXXXXXX\'\'XXXXXXXXXXXXXXXXXXX\'XXXXXXXX-------\'XX"},

          // I don't know what's wrong with this one, but OpenDS thinks the unmodified filter is not valid.
//          {"(version3.0;acl\"\";deny(all)ip=\"1.1.1.1+1.1.1.0\";)",
//           "XXXXXXXXXXXXXXX\'\'XXXXXXXXXXXXX\"---------------\"XX"},

          {"(version3.0;acl\"\";deny(all)dns=\"a\";)",
           "XXXXXXXXXXXXXXX\'\'XXXXXXXXXX----\"-\"XX"},

          {"(version3.0;acl\"\";deny(all)timeofday>\"2300\";)",
           "XXXXXXXXXXXXXXX\'\'XXXXXXXXXX----------\"----\"XX"},

          {"(version3.0;acl\"\";deny(all)authmethod=\"simple\";)",
           "XXXXXXXXXXXXXXX\'\'XXXXXXXXXX-----------\"------\"XX"},

          {"(version3.0;acl\"\";deny(all)not authmethod=\"simple\";)",
           "XXXXXXXXXXXXXXX\'\'XXXXXXXXXX----XXXXXXXXXXX\'XXXXXX\'XX"},

          {"(version3.0;acl\"\";deny(all)not authmethod=\"simple\"and not authmethod=\"ssl\";)",
           "XXXXXXXXXXXXXXX\'\'XXXXXXXXXXXXXXXXXXXXXXXXX\'XXXXXX\'--------XXXXXXXXXXX\'XXX\'XX"},

          {"(version3.0;acl\"\";deny(all)dayofweek=\"sun\";)",
           "XXXXXXXXXXXXXXX\'\'XXXXXXXXXX----------\"---\"XX"},

          {"(targetattr=\"*\")(version3.0;acl\"\";deny(all)dns=\"a\";)",
           "------------\"-\"-XXXXXXXXXXXXXXX\'\'XXXXXXXXXXXXXX\'X\'XX"},

          };

  @DataProvider
  public Object[][] validBasisOfValidityTests() throws Exception {
    TestCaseUtils.startServer();  // Apparently necessary since the DataProviders can be called before @BeforeClass.

    List<String> acis = new ArrayList<>();
    for (String[] aciAndMask: INVALID_ACIS_IF_ANY_CHAR_REMOVED) {
      acis.add("aci: " + aciAndMask[0]);
    }
    return buildAciValidationParams(acis, false /*test once per aci*/);
  }

  /**
   * This makes sure that all of the acis in the INVALID_ACIS_IF_ANY_CHAR_REMOVED
   * tests are valid acis.
   */
  @Test(dataProvider = "validBasisOfValidityTests")
  public void testBasisOfInvalidityTestsAreValid(String modifierDn, String modifierPw, String aciModLdif)
      throws Throwable {
    if (TESTS_ARE_DISABLED) {  // This is a hack to make sure we can disable the tests.
      return;
    }
    testValidAcisHelper(modifierDn, modifierPw, aciModLdif);
  }

  @DataProvider
  public Object[][] validAcis() throws Exception {
    TestCaseUtils.startServer();  // Apparently necessary since the DataProviders can be called before @BeforeClass.

    return buildAciValidationParams(Arrays.asList(VALID_ACIS), false /*test once per aci*/);
  }

  @DataProvider
  public Object[][] invalidAcis() throws Exception {
    TestCaseUtils.startServer();  // Apparently necessary since the DataProviders can be called before @BeforeClass.

    List<String> invalid = newArrayList(INVALID_ACIS);
    for (String[] aciAndMask: INVALID_ACIS_IF_ANY_CHAR_REMOVED) {
      invalid.addAll(getAciMissingCharCombos(aciAndMask[0], aciAndMask[1]));
    }
    return buildAciValidationParams(invalid, false /*test once per aci*/);
  }

  /**
   * We use this with acis that are crafted in such a way so that they are
   * invalid if any character is removed.  By convention, the character
   * is only removed if the corresponding mask character is a - or \"
   */
  protected List<String> getAciMissingCharCombos(String aci, String mask) {
    List <String> acisMissingOneChar = new ArrayList<>();
    for (int i = 0; i < aci.length(); i++) {
      // Add this test only if the mask tells us we haven't seen it before.
      // Also guard against ArrayIndexOutOfBoundsExceptions in case the
      // mask isn't long enough.
      if (i < mask.length()
          && (mask.charAt(i) == '-' || mask.charAt(i) == '\"')) {
        acisMissingOneChar.add("aci: " + aci.substring(0, i) + aci.substring(i + 1, aci.length()));
      }
    }
    return acisMissingOneChar;
  }



  /** Common between validAcis and invalidAcis. */
  private Object[][] buildAciValidationParams(List<String> acis, boolean testMultipleCombos) {
    List<String[]> paramsList = new ArrayList<>();

    for (String aci: acis) {
      List<String> aciLdifs = new ArrayList<>();

      // aci set in Add
      aciLdifs.add(TestCaseUtils.makeLdif(
              "dn: " + OU_INNER_DN,
              "changetype: add",
              "objectclass: organizationalunit",
              "ou: inner",
              aci));

      if (testMultipleCombos) {
        String ouLdif = TestCaseUtils.makeLdif(
                "dn: " + OU_INNER_DN,
                "changetype: add",
                "objectclass: organizationalunit",
                "ou: inner");

        // aci set in modify via add
        aciLdifs.add(ouLdif +
                TestCaseUtils.makeLdif(
                "dn: " + OU_INNER_DN,
                "changetype: modify",
                "add: aci",
                aci));

        // aci set in modify via replace
        aciLdifs.add(ouLdif +
                TestCaseUtils.makeLdif(
                "dn: " + OU_INNER_DN,
                "changetype: modify",
                "replace: aci",
                aci));
      }

      // Test each one with a user where ACI's aren't enforced and one where they are.
      // This is in particularly useful for invalid acis.
      for (String aciLdif: aciLdifs) {
        if (testMultipleCombos) {
          paramsList.add(new String[]{DIR_MGR_DN, DIR_MGR_PW, aciLdif});
        }
        paramsList.add(new String[]{ADMIN_DN, ADMIN_PW, aciLdif});
      }
    }

    return paramsList.toArray(new Object[][]{});
  }

  @Test(dataProvider = "validAcis")
  public void testValidAcis(String modifierDn, String modifierPw, String aciModLdif) throws Throwable {
    if (TESTS_ARE_DISABLED) {  // This is a hack to make sure we can disable the tests.
      return;
    }
    testValidAcisHelper(modifierDn, modifierPw, aciModLdif);
  }

  private void testValidAcisHelper(String modifierDn, String modifierPw, String aciModLdif) throws Throwable {
    try {
      // Setup the basic DIT
      addEntries(VALIDITY_TESTS_DIT, DIR_MGR_DN, DIR_MGR_PW);

      // Test that we can add entries with valid ACIs as well as set valid ACIs on a an entry
      modEntries(aciModLdif, modifierDn, modifierPw);
    } catch (Throwable e) {
      System.err.println("Started with dit:\nldapmodify -a -D \"cn=Directory Manager\" -w etegrity -p 13324\n"
          + VALIDITY_TESTS_DIT + "and as '" + modifierDn + "' failed to perform these modifications:\n"
          + "ldapmodify -D \"" + modifierDn + "\" -w " + modifierPw + " -p 13324\n"
          + aciModLdif);
      throw e;
    }
  }

  // I'd like to make this  dependsOnMethods = {"testBasisOfInvalidityTestsAreValid(String,String,String)"}
  // but I can't figure out how.
  @Test(dataProvider = "invalidAcis")
  public void testInvalidAcis(String modifierDn, String modifierPw, String aciModLdif) throws Throwable {
    if (TESTS_ARE_DISABLED) {  // This is a hack to make sure we can disable the tests.
      return;
    }
    try {
      // Setup the basic DIT
      addEntries(VALIDITY_TESTS_DIT, DIR_MGR_DN, DIR_MGR_PW);

      // Test that we can add entries with valid ACIs as well as set valid ACIs on a an entry
      modEntriesExpectFailure(aciModLdif, modifierDn, modifierPw);
    } catch (Throwable e) {
      System.err.println("Started with dit:\nldapmodify -a -D \"cn=Directory Manager\" -w etegrity -p 13324\n"
          + VALIDITY_TESTS_DIT + "and as '" + modifierDn + "' successfully added an invalid aci:\n"
          + "ldapmodify -D \"" + modifierDn + "\" -w " + modifierPw + " -p 13324\n"
          + aciModLdif);
      throw e;
    }
  }

  @DataProvider
  public Object[][] invalidAcisMultiCombos() throws Exception {
    TestCaseUtils.startServer();  // Apparently necessary since the DataProviders can be called before @BeforeClass.

    List<String> invalid = new ArrayList<>();
    invalid.add(INVALID_ACIS[0]);
    invalid.add(INVALID_ACIS[1]);

    return buildAciValidationParams(invalid, true /*test multiple combos*/);
  }

  /** Runs invalidity checks as DirectoryManager and by setting them
   *  different ways.  We don't check as many this way since the combinations
   *  get expensive, and if these detect any problem, then they will all probably be okay. */
  @Test(dataProvider = "invalidAcisMultiCombos")
  public void testInvalidAcisXX(String modifierDn, String modifierPw, String aciModLdif) throws Throwable {
    if (TESTS_ARE_DISABLED) {  // This is a hack to make sure we can disable the tests.
      return;
    }
    testInvalidAcis(modifierDn, modifierPw, aciModLdif);
  }

// -----------------------------------------------------------------------------
//  SEARCHING
// -----------------------------------------------------------------------------



  private static final String BASE_OU_LDIF__SEARCH_TESTS = makeOuLdif(OU_BASE_DN, "acitest");
  private static final String INNER_OU_LDIF__SEARCH_TESTS = makeOuLdif(OU_INNER_DN, "inner");
  private static final String LEAF_OU_LDIF__SEARCH_TESTS = makeOuLdif(OU_LEAF_DN, "leaf");

  private static final String ADMIN_LDIF__SEARCH_TESTS = makeUserLdif(ADMIN_DN, "aci", "admin", ADMIN_PW);
  private static final String USER_LDIF__SEARCH_TESTS = makeUserLdif(USER_DN, "some", "user", USER_PW);
  // @Checkstyle:off
  private static final String LEVEL_1_USER_LDIF__SEARCH_TESTS = makeUserLdif(LEVEL_1_USER_DN, "level1", "user", "pa$$word");
  private static final String LEVEL_2_USER_LDIF__SEARCH_TESTS = makeUserLdif(LEVEL_2_USER_DN, "level2", "user", "pa$$word");
  private static final String LEVEL_3_USER_LDIF__SEARCH_TESTS = makeUserLdif(LEVEL_3_USER_DN, "level3", "user", "pa$$word");
  private static final String PROXY_USER_LDIF__SEARCH_TESTS = makeUserLdif(PROXY_USER_DN, "proxy", "user", "pa$$word");
  // @Checkstyle:on


    private static final String SALES_USER_1__SEARCH_TESTS =
            makeUserLdif(SALES_USER_1, "sales1", "person", "pa$$word" );

    private static final String SALES_USER_2__SEARCH_TESTS =
            makeUserLdif(SALES_USER_2, "sales2", "person", "pa$$word" );

    private static final String SALES_USER_3__SEARCH_TESTS =
            makeUserLdif(SALES_USER_3, "sales3", "person", "pa$$word" );

    private static final String MANAGER__SEARCH_TESTS =
            makeUserLdif(MANAGER_DN, "the", "managers", "pa$$word",
                         ADMIN_DN, OU_GROUP_2_DN );

   private static final String MANAGER_NEW__SEARCH_TESTS =
           makeUserLdif(MANAGER_NEW_DN, "new", "managers", "pa$$word",
                        ADMIN_DN, OU_GROUP_2_DN );


    private static final String SALES__SEARCH_TESTS =
            makeUserLdif(SALES_DN, "sales", "dept", "pa$$word",
                        LEVEL_2_USER_DN, LEVEL_1_USER_URL);

  //LDIF entries used to test group stuff.
  private static final String GROUP_LDIF__SEARCH_TESTS =
                                             makeOuLdif(OU_GROUPS_DN, "groups");

  private static final
  String GROUP_1_LDIF__SEARCH_TESTS = makeGroupLdif(OU_GROUP_1_DN,
                                                    LEVEL_1_USER_DN,
                                                    LEVEL_3_USER_DN);

 private static final
 String GROUP_2_LDIF__SEARCH_TESTS = makeGroupLdif(OU_GROUP_2_DN,
                                                   LEVEL_2_USER_DN,
                                                   ADMIN_DN);
 //ACI are used to test global ACI stuff.
 private static final String ACCESS_HANDLER_DN =
                                          "cn=Access Control Handler,cn=config";

 private static final String GLOBAL_ALLOW_ALL_TO_ADMIN_ACI =
       buildGlobalAciValue("name", "allow all to admin", "targetattr",
                                     "*", "allow(all)", BIND_RULE_USERDN_ADMIN);

 private static final String GLOBAL_ALLOW_MONITOR_TO_ADMIN_ACI =
       buildGlobalAciValue("name", "monitor all to admin", "targetattr",
                                     "*", "target", "ldap:///cn=monitor",
                                     "allow(all)", BIND_RULE_USERDN_ADMIN);

 private static final String GLOBAL_ALLOW_BASE_DN_TO_LEVEL_1_ACI =
       buildGlobalAciValue("name", "monitor all to admin", "targetattr",
                                     "*", "target", "ldap:///" + OU_BASE_DN,
                                     "allow(all)", BIND_RULE_USERDN_LEVEL_1);

 private static final String ALLOW_ALL_GLOBAL_TO_ADMIN_MOD =
                  makeAttrAddAciLdif(ATTR_AUTHZ_GLOBAL_ACI,ACCESS_HANDLER_DN,
                                       GLOBAL_ALLOW_ALL_TO_ADMIN_ACI);

 private static final String GLOBAL_MODS =
                  makeAttrAddAciLdif(ATTR_AUTHZ_GLOBAL_ACI,ACCESS_HANDLER_DN,
                                       GLOBAL_ALLOW_MONITOR_TO_ADMIN_ACI,
                                       GLOBAL_ALLOW_BASE_DN_TO_LEVEL_1_ACI);

  //Global defaults
  private static final String GLOBAL_ANONYMOUS_READ_ACI =
          buildGlobalAciValue("name", "Anonymous read access", "targetattr!=",
                  "userPassword||authPassword",
                  "allow(read, search, compare)", BIND_RULE_USERDN_ANYONE);

  private static final String GLOBAL_SELF_WRITE_ACI =
          buildGlobalAciValue("name", "Self entry modification", "targetattr",
                  "*",
                  "allow(write)", BIND_RULE_USERDN_SELF);

  private static final String GLOBAL_SCHEMA_ACI =
          buildGlobalAciValue("name",
                  "User-Visible Schema Operational Attributes",
                  "target", "ldap:///cn=schema", "targetscope", "base",
                  "targetattr",
                  "attributeTypes||dITContentRules||dITStructureRules||" +
                  "ldapSyntaxes||matchingRules||matchingRuleUse||nameForms||" +
                  "objectClasses",
                  "allow(read, search, compare)", BIND_RULE_USERDN_ANYONE);

  private static final String GLOBAL_DSE_ACI = buildGlobalAciValue(
          "name","User-Visible Root DSE Operational Attributes",
          "target", "ldap:///", "targetscope", "base",
          "targetattr",
          "namingContexts||supportedAuthPasswordSchemes||supportedControl||" +
          "supportedExtension||supportedFeatures||supportedSASLMechanisms||" +
          "vendorName||vendorVersion",
          "allow(read, search, compare)",BIND_RULE_USERDN_ANYONE);

  private static final String GLOBAL_USER_OP_ATTRS_ACI = buildGlobalAciValue(
          "name", "User-Visible Operational Attributes", "targetattr",
          "createTimestamp||creatorsName||modifiersName||modifyTimestamp||" +
          "entryDN||entryUUID||subschemaSubentry",
          "allow(read, search, compare)", BIND_RULE_USERDN_ANYONE);

  private static final String GLOBAL_CONTROL_ACI = buildGlobalAciValue(
          "name", "Anonymous control access", "targetcontrol",
          "*",
          "allow(read)", BIND_RULE_USERDN_ANYONE);

  private static final String GLOBAL_EXT_OP_ACI = buildGlobalAciValue(
          "name", "Anonymous extend op access", "extop",
          "*",
          "allow(read)", BIND_RULE_USERDN_ANYONE);

  private static final String GLOBAL_DEFAULT_ACIS =
                     makeAttrAddAciLdif(ATTR_AUTHZ_GLOBAL_ACI,ACCESS_HANDLER_DN,
                                        GLOBAL_ANONYMOUS_READ_ACI,
                                        GLOBAL_SELF_WRITE_ACI, GLOBAL_SCHEMA_ACI,
                                        GLOBAL_DSE_ACI, GLOBAL_USER_OP_ATTRS_ACI,
                                        GLOBAL_CONTROL_ACI, GLOBAL_EXT_OP_ACI);

  /** ACI used to test LDAP compare. */
  private static final String COMPARE_ACI =  makeAddAciLdif(OU_LEAF_DN, ALLOW_ALL_TO_COMPARE);

  /** ACI used to test LDAP search with attributes. */
  private static final String SEARCH_ATTRIBUTES_ALLOW_ACI = makeAddAciLdif(
      OU_LEAF_DN, ALLOW_ALL_TO_ALL);
  private static final String SEARCH_ATTRIBUTES_DENY_ACI = makeAddAciLdif(
      OU_LEAF_DN, DENY_READ_CN_SN_IF_PERSON);

  /** ACI used to test selfwrite. */
  private static final String SELFWRITE_ACI = makeAddAciLdif(
      OU_GROUP_1_DN, ALLOW_ALL_TO_SELFWRITE);

  //ACIs used for standard modDN tests (export, import)

 private static final  String ACI_IMPORT_MGR_NEW =
                   makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_IMPORT_MGR_NEW);

 private static final  String ACI_IMPORT_MGR =
                   makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_IMPORT_MGR);

 private static final  String ACI_EXPORT_MGR_NEW =
                   makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_EXPORT_MGR_NEW);

 private static final  String ACI_EXPORT_MGR =
                   makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_EXPORT_MGR);

 private static final String ACI_WRITE_RDN_ATTRS =
                   makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_WRITE_RDN_ATTRS);

 private static final String ACI_MOVED_ENTRY =
                   makeAddAciLdif(SALES_USER_1, ALLOW_ALL_TO_MOVED_ENTRY);

//ACIs used for proxied auth modDN tests

  private static final  String ACI_PROXY_IMPORT_MGR_NEW =
                   makeAddAciLdif(OU_BASE_DN, ALLOW_PROXY_TO_IMPORT_MGR_NEW);


private static final  String ACI_PROXY_CONTROL_LEVEL_1 =
                  makeAddAciLdif(OU_BASE_DN, ALLOW_PROXY_CONTROL_TO_LEVEL_1);

 private static final  String ACI_PROXY_IMPORT_MGR =
                   makeAddAciLdif(OU_BASE_DN, ALLOW_PROXY_TO_IMPORT_MGR);

 private static final  String ACI_PROXY_EXPORT_MGR_NEW =
                   makeAddAciLdif(OU_BASE_DN, ALLOW_PROXY_TO_EXPORT_MGR_NEW);

 private static final  String ACI_PROXY_EXPORT_MGR =
                   makeAddAciLdif(OU_BASE_DN, ALLOW_PROXY_TO_EXPORT_MGR);

 private static final String ACI_PROXY_WRITE_RDN_ATTRS =
                   makeAddAciLdif(OU_BASE_DN, ALLOW_PROXY_TO_WRITE_RDN_ATTRS);

 private static final String ACI_PROXY_LEVEL_1=
                   makeAddAciLdif(OU_BASE_DN, ALLOW_PROXY_TO_LEVEL1);

 private static final String ACI_PROXY_MOVED_ENTRY =
                   makeAddAciLdif(SALES_USER_1, ALLOW_PROXY_TO_MOVED_ENTRY);

/** ACI used in testing the groupdn bind rule keywords. */

   private static final
 String GROUP1_GROUPDN_MODS =  makeAddAciLdif(OU_LEAF_DN,
                                         ALLOW_SEARCH_TO_GROUP1_GROUPDN);

  /** Aci to test dns="*". */
  private static final
 String DNS_ALL_ACI =  makeAddAciLdif(OU_LEAF_DN, ALLOW_ALL_TO_DNS_ALL);

  /** ou=leaf,ou=inner,ou=acitest,dc=example,dc=com and everything under it */
  private static final String LEAF_OU_FULL_LDIF__SEARCH_TESTS =
    LEAF_OU_LDIF__SEARCH_TESTS +
    LEVEL_3_USER_LDIF__SEARCH_TESTS;

  /** ou=inner,ou=acitest,dc=example,dc=com and everything under it */
  private static final String INNER_OU_FULL_LDIF__SEARCH_TESTS =
    INNER_OU_LDIF__SEARCH_TESTS +
    LEVEL_2_USER_LDIF__SEARCH_TESTS +
    LEAF_OU_FULL_LDIF__SEARCH_TESTS;

  /** ou=acitest,dc=example,dc=com and everything under it */
  private static final String BASE_OU_FULL_LDIF__SEARCH_TESTS =
    BASE_OU_LDIF__SEARCH_TESTS  +
    LEVEL_1_USER_LDIF__SEARCH_TESTS +
    INNER_OU_FULL_LDIF__SEARCH_TESTS;

  private static final String BASIC_LDIF__SEARCH_TESTS =
          ADMIN_LDIF__SEARCH_TESTS +
          USER_LDIF__SEARCH_TESTS +
          BASE_OU_FULL_LDIF__SEARCH_TESTS;

   private static final String BASIC_LDIF__GROUP_SEARCH_TESTS =
            ADMIN_LDIF__SEARCH_TESTS +
            USER_LDIF__SEARCH_TESTS +
            BASE_OU_LDIF__SEARCH_TESTS  +
            MANAGER__SEARCH_TESTS +
            MANAGER_NEW__SEARCH_TESTS +
            SALES__SEARCH_TESTS +
            SALES_USER_1__SEARCH_TESTS +
            SALES_USER_2__SEARCH_TESTS +
            SALES_USER_3__SEARCH_TESTS +
            GROUP_LDIF__SEARCH_TESTS +
            GROUP_1_LDIF__SEARCH_TESTS +
            GROUP_2_LDIF__SEARCH_TESTS +
            LEVEL_1_USER_LDIF__SEARCH_TESTS +
            PROXY_USER_LDIF__SEARCH_TESTS +
            INNER_OU_FULL_LDIF__SEARCH_TESTS;

  private static final String NO_ACIS_LDIF = "";

  // ------------------------------------------------------------
  //  THESE ALL WILL RETURN NO RESULTS FOR ADMINS AND ANONYMOUS
  // ------------------------------------------------------------

  private static final String ALLOW_ALL_BASE_DENY_ALL_BASE_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_TO_ALL);

  private static final String ALLOW_ALL_BASE_DENY_READ_BASE_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_READ_TO_ALL);

  private static final String ALLOW_READ_BASE_DENY_ALL_BASE_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_READ_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_TO_ALL);

  private static final String ALLOW_READ_BASE_DENY_ALL_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_READ_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TO_ALL);

  private static final String ALLOW_ALL_BASE_DENY_READ_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_READ_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TO_ALL);

  private static final String ALLOW_SEARCH_BASE_DENY_ALL_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_SEARCH_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TO_ALL);

  private static final String ALLOW_ALL_BASE_DENY_SEARCH_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_SEARCH_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TO_ALL);

  private static final String ALLOW_ALL_BASE_DENY_ALL_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TO_ALL);

  private static final String ALLOW_ALL_BASE_DENY_ADMIN_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TO_ADMIN);

  private static final String ALLOW_ALL_BASE_DENY_OU_PERSON_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_PERSON_OU_TO_ALL);

  private static final String DENY_ADMIN_BASE_ALLOW_ALL_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_TO_ADMIN) +
          makeAddAciLdif(OU_INNER_DN, ALLOW_ALL_TO_ALL);

  private static final String ALL0W_ALL_BASE_DENY_OU_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_OU_INNER);

  private static final String ALL0W_ALL_BASE_DENY_READ_BASE_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_OU_INNER);

  private static final String ALL0W_SEARCH_BASE_DENY_READ_BASE_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_READ_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_OU_INNER);

  private static final String ALL0W_ALL_BASE_DENY_ALL_REAL_ATTRS_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_READ_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_REAL_ATTRS_VALUE);

  private static final String ALL0W_ALL_BASE_DENY_READ_REAL_ATTRS_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_READ_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_READ_REAL_ATTRS_VALUE);

  private static final String ALL0W_ALL_TO_ALL_OTHER_OBJECTCLASSES_BASE_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALL0W_ALL_TO_ALL_OTHER_OBJECTCLASSES);

  private static final String ALL0W_ALL_TO_ALL_OTHER_OBJECTCLASSES_BASE_LDIF_ALT =
          makeAddAciLdif(OU_BASE_DN, ALL0W_ALL_TO_ALL_OTHER_OBJECTCLASSES_ALT);

  private static final String ALL0W_ALL_BASE_DENY_WRITE_DELETE_READ_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_WRITE_DELETE_READ_TO_ALL);

  private static final String ALL0W_ALL_BASE_DENY_READ_TO_CN_RDN_USERS_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_READ_TO_CN_RDN_USERS);

  private static final String ALL0W_ALL_BASE_DENY_READ_TO_UID_OR_CN_RDN_USERS_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_READ_TO_UID_OR_CN_RDN_USERS);

  private static final String ALL0W_ALL_BASE_DENY_READ_TO_NON_UID_RDN_USERS_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_READ_TO_NON_UID_RDN_USERS);

  private static final String ALL0W_ALL_BASE_DENY_READ_TO_CN_ADMINS_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_READ_TO_CN_ADMINS);

  private static final String ALL0W_ALL_BASE_DENY_READ_TO_TOP_LEVEL_CN_ADMINS_INNER_LDIF =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_READ_TO_TOP_LEVEL_CN_ADMINS);

  private static final String ALLOW_ALL_BASE_DENY_ALL_TO_LOCALHOST =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_TO_LOCALHOST);

  private static final String ALLOW_ALL_NON_LOCALHOST =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_NON_LOCALHOST);

  private static final String ALLOW_ALL_BASE_DENY_ALL_TO_MISC_AND_LOCALHOST =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_TO_MISC_AND_LOCALHOST);

  private static final String ALLOW_ALL_NON_MISC_AND_LOCALHOST =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_NON_MISC_AND_LOCALHOST);

  private static final String ALLOW_ALL_BASE_DENY_ALL_TO_MISC_AND_LOCALHOST_SUBNET =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TO_LOCALHOST_SUBNET);

  private static final String ALLOW_ALL_BASE_DENY_ALL_TO_LOCALHOST_WITH_MASK =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TO_LOCALHOST_WITH_MASK);

  private static final String ALLOW_ALL_BASE_DENY_ALL_TO_LOCALHOST_SUBNET_WITH_MASK =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TO_LOCALHOST_SUBNET_WITH_MASK);

  private static final String ALLOW_ALL_BASE_TO_NON_DNS_LOCALHOST =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_NON_DNS_LOCALHOST);

  private static final String ALLOW_ALL_BASE_TO_SSL_AUTH =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_SSL);

  private static final String ALLOW_ALL_BASE_TO_SASL_DIGEST_MD5_AUTH =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_SASL_DIGEST_MD5);

  private static final String ALLOW_ALL_BASE_DENY_ALL_TO_SIMPLE_AUTH =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TO_SIMPLE);

  private static final String ALLOW_ALL_BASE_DENY_ALL_TODAY =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TODAY);

  private static final String ALLOW_ALL_BASE_DENY_ALL_TODAY_AND_TOMORROW =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_TODAY_AND_TOMORROW);

  private static final String ALLOW_ALL_BASE_NOT_TODAY =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_NOT_TODAY);

  private static final String ALLOW_ALL_BASE_DENY_ALL_THIS_HOUR =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_INNER_DN, DENY_ALL_THIS_HOUR);

  private static final String ALLOW_ALL_BASE_PREVIOUS_HOUR =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_NOT_TODAY);

  private static final String ALLOW_ALL_BASE_ADMIN_AND_SSL =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_ADMIN_AND_SSL);

  private static final String ALLOW_ALL_BASE_DENY_ALL_NOT_LOCALHOST_OR_ADMIN =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_NOT_LOCALHOST_OR_ADMIN);

  private static final String ALLOW_ALL_BASE_DENY_ALL_TO_ADMIN_AND_LOCALHOST_OR_SSL =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL) +
          makeAddAciLdif(OU_BASE_DN, DENY_ALL_TO_ADMIN_AND_LOCALHOST_OR_SSL);

  private static final String ALLOW_ALL_BASE_NOT_ADMIN =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_NOT_ADMIN);

  // -----------------------------------------------------------------
  //  THESE ALL WILL RETURN EVERYTHING IN AT LEAST OU=INNER FOR ADMINS
  // -----------------------------------------------------------------

  private static final String ALLOW_ALL_BASE_TO_ADMIN =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ADMIN);

  private static final String ALLOW_ALL_BASE_TO_LOCALHOST =
          makeAddAciLdif(OU_BASE_DN, ALLOW_SEARCH_TO_LOCALHOST);

  private static final String ALLOW_ALL_BASE_TO_ANYONE =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ANYONE);

  private static final String ALLOW_ALL_BASE_TO_ALL =
          makeAddAciLdif(OU_BASE_DN, ALLOW_ALL_TO_ALL);

  private static final String ALL0W_SEARCH_INNER_TO_ADMIN =
          makeAddAciLdif(OU_INNER_DN, ALLOW_SEARCH_TO_ADMIN);

  private static final String ALL0W_WRITE_DELETE_SEARCH_INNER_TO_ALL =
          makeAddAciLdif(OU_INNER_DN, ALLOW_WRITE_DELETE_SEARCH_TO_ALL);

  private static final String ALLOW_INNER_SEARCH_TO_CN_ADMINS =
          makeAddAciLdif(OU_INNER_DN, ALLOW_SEARCH_TO_CN_ADMINS);

  private static final String ALLOW_INNER_ALL_TO_SIMPLE =
          makeAddAciLdif(OU_INNER_DN, ALLOW_ALL_TO_SIMPLE);

  private static final String ALLOW_INNER_ALL_TODAY =
          makeAddAciLdif(OU_INNER_DN, ALLOW_ALL_TODAY);

  private static final String ALLOW_INNER_ALL_THIS_HOUR =
          makeAddAciLdif(OU_INNER_DN, ALLOW_ALL_THIS_HOUR);

  private static final String ALLOW_INNER_ALL_TO_ADMIN_AND_LOCALHOST_OR_SSL =
          makeAddAciLdif(OU_INNER_DN, ALLOW_ALL_TO_ADMIN_AND_LOCALHOST_OR_SSL);

  private static final String ALLOW_INNER_SEARCH_FROM_BASE_LOCALHOST =
          makeAddAciLdif(OU_BASE_DN, ALLOW_SEARCH_TARGET_INNER_TO_LOCALHOST);

  private static final String ALLOW_BASE_SEARCH_REALATTRS_TO_LOCALHOST =
          makeAddAciLdif(OU_BASE_DN, ALLOW_SEARCH_REALATTRS_TO_LOCALHOST);

  private static final String ALLOW_BASE_SEARCH_OUR_ATTRS_TO_ADMIN =
          makeAddAciLdif(OU_BASE_DN, ALLOW_SEARCH_OUR_ATTRS_TO_ADMIN);

  private static final String ALLOW_BASE_SEARCH_OU_AND_PERSON_TO_SIMPLE =
          makeAddAciLdif(OU_BASE_DN, ALLOW_SEARCH_OU_AND_PERSON_TO_SIMPLE);

  // ------------------------------------------------------------
  //
  // ------------------------------------------------------------

  private static final String NO_SEARCH_RESULTS = "";



  // Potential dimensions
  //   * Who sets the ACIs to start with
  //   * Whether the entries were created with the ACIs or they were added later.  LDIFModify would work here.

  private static List<SearchTestParams> SEARCH_TEST_PARAMS = new ArrayList<>();
  private static SearchTestParams registerNewTestParams(String initialDitLdif, String... aciLdif) {
    SearchTestParams testParams = new SearchTestParams(initialDitLdif, aciLdif);
    SEARCH_TEST_PARAMS.add(testParams);
    return testParams;
  }


  static {
    SearchTestParams testParams;

    // ACIs that allow 'cn=Directory Manager' but deny the searches below to everyone else
    // in some way.
    testParams = registerNewTestParams(BASIC_LDIF__SEARCH_TESTS,
            NO_ACIS_LDIF,
            ALLOW_ALL_BASE_DENY_ALL_BASE_LDIF,
            ALLOW_ALL_BASE_DENY_READ_BASE_LDIF,
            ALL0W_ALL_BASE_DENY_READ_TO_CN_ADMINS_INNER_LDIF,
            ALL0W_ALL_BASE_DENY_READ_TO_TOP_LEVEL_CN_ADMINS_INNER_LDIF,
            ALLOW_ALL_BASE_NOT_ADMIN
            );

    testParams.addSingleSearch(DIR_MGR_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_SUB, INNER_OU_FULL_LDIF__SEARCH_TESTS);
    testParams.addSingleSearch(ADMIN_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_SUB, NO_SEARCH_RESULTS);
    testParams.addSingleSearch(ANNONYMOUS_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_SUB, NO_SEARCH_RESULTS);

    testParams.addSingleSearch(DIR_MGR_DN, OU_LEAF_DN, OBJECTCLASS_STAR, SCOPE_SUB, LEAF_OU_FULL_LDIF__SEARCH_TESTS);
    testParams.addSingleSearch(ADMIN_DN, OU_LEAF_DN, OBJECTCLASS_STAR, SCOPE_SUB, NO_SEARCH_RESULTS);
    testParams.addSingleSearch(ANNONYMOUS_DN, OU_LEAF_DN, OBJECTCLASS_STAR, SCOPE_SUB, NO_SEARCH_RESULTS);

    testParams.addSingleSearch(DIR_MGR_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_SUB, INNER_OU_FULL_LDIF__SEARCH_TESTS);
    testParams.addSingleSearch(ADMIN_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_ONE, NO_SEARCH_RESULTS);
    testParams.addSingleSearch(ANNONYMOUS_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_ONE, NO_SEARCH_RESULTS);

    // ------------------------------------------------------------------------

    // ACIs that allow 'cn=Directory Manager' but deny the searches below to everyone else
    // in some way.
    testParams = registerNewTestParams(BASIC_LDIF__SEARCH_TESTS,
            // These ACIs are all equivalent for the single search test cases below
            // (but most likely not equivalent in general).
            NO_ACIS_LDIF,
            ALLOW_ALL_BASE_DENY_ALL_BASE_LDIF,
            ALLOW_ALL_BASE_DENY_READ_BASE_LDIF,
            ALLOW_READ_BASE_DENY_ALL_BASE_LDIF,
            ALLOW_ALL_BASE_DENY_ALL_INNER_LDIF,
            ALLOW_READ_BASE_DENY_ALL_INNER_LDIF,
            ALLOW_ALL_BASE_DENY_READ_INNER_LDIF,
            ALLOW_SEARCH_BASE_DENY_ALL_INNER_LDIF,
            ALLOW_ALL_BASE_DENY_SEARCH_INNER_LDIF,
            ALLOW_ALL_BASE_DENY_ADMIN_INNER_LDIF,
            ALLOW_ALL_BASE_DENY_OU_PERSON_INNER_LDIF,
            DENY_ADMIN_BASE_ALLOW_ALL_INNER_LDIF,
            ALL0W_ALL_BASE_DENY_OU_INNER_LDIF,
            ALL0W_SEARCH_BASE_DENY_READ_BASE_LDIF,
            ALL0W_ALL_BASE_DENY_ALL_REAL_ATTRS_INNER_LDIF,
            ALL0W_ALL_BASE_DENY_READ_REAL_ATTRS_INNER_LDIF,
            ALL0W_ALL_TO_ALL_OTHER_OBJECTCLASSES_BASE_LDIF,
            ALL0W_ALL_TO_ALL_OTHER_OBJECTCLASSES_BASE_LDIF_ALT,
            ALL0W_ALL_BASE_DENY_WRITE_DELETE_READ_INNER_LDIF,
            ALL0W_ALL_BASE_DENY_READ_TO_CN_RDN_USERS_INNER_LDIF,
            ALL0W_ALL_BASE_DENY_READ_TO_UID_OR_CN_RDN_USERS_INNER_LDIF,
            ALL0W_ALL_BASE_DENY_READ_TO_NON_UID_RDN_USERS_INNER_LDIF,
            ALL0W_ALL_BASE_DENY_READ_TO_CN_ADMINS_INNER_LDIF,
            ALL0W_ALL_BASE_DENY_READ_TO_TOP_LEVEL_CN_ADMINS_INNER_LDIF,
            ALLOW_ALL_BASE_DENY_ALL_TO_LOCALHOST,
            ALLOW_ALL_NON_LOCALHOST,
            ALLOW_ALL_BASE_DENY_ALL_TO_MISC_AND_LOCALHOST,
            ALLOW_ALL_BASE_TO_NON_DNS_LOCALHOST,
            ALLOW_ALL_BASE_TO_SSL_AUTH,
            ALLOW_ALL_BASE_TO_SASL_DIGEST_MD5_AUTH,
            ALLOW_ALL_BASE_DENY_ALL_TO_SIMPLE_AUTH,
            ALLOW_ALL_BASE_DENY_ALL_TODAY,
            ALLOW_ALL_BASE_DENY_ALL_TODAY_AND_TOMORROW,
            ALLOW_ALL_BASE_NOT_TODAY,
            ALLOW_ALL_BASE_DENY_ALL_THIS_HOUR,
            ALLOW_ALL_BASE_PREVIOUS_HOUR,
            ALLOW_ALL_BASE_ADMIN_AND_SSL,
            ALLOW_ALL_BASE_DENY_ALL_NOT_LOCALHOST_OR_ADMIN,
            ALLOW_ALL_BASE_DENY_ALL_TO_ADMIN_AND_LOCALHOST_OR_SSL,
            ALLOW_ALL_BASE_NOT_ADMIN
//  <FAIL>
//            ALLOW_ALL_NON_MISC_AND_LOCALHOST,
//            ALLOW_ALL_BASE_DENY_ALL_TO_MISC_AND_LOCALHOST_SUBNET,
//            ALLOW_ALL_BASE_DENY_ALL_TO_LOCALHOST_WITH_MASK
//            ALLOW_ALL_BASE_DENY_ALL_TO_LOCALHOST_SUBNET_WITH_MASK
//  </FAIL>
    );
    testParams.addSingleSearch(ADMIN_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_SUB, NO_SEARCH_RESULTS);
    testParams.addSingleSearch(ADMIN_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_ONE, NO_SEARCH_RESULTS);
    testParams.addSingleSearch(ADMIN_DN, OU_LEAF_DN, OBJECTCLASS_STAR, SCOPE_SUB, NO_SEARCH_RESULTS);
    testParams.addSingleSearch(ANNONYMOUS_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_SUB, NO_SEARCH_RESULTS);

    // ------------------------------------------------------------------------

    // ACIs that allow cn=admin, but deny the searches below to anonymous
    // in some way.
    testParams = registerNewTestParams(BASIC_LDIF__SEARCH_TESTS,
            ALLOW_ALL_BASE_TO_ADMIN,
            ALLOW_ALL_BASE_TO_LOCALHOST,
            ALLOW_ALL_BASE_TO_ALL,
            ALLOW_ALL_BASE_TO_ANYONE,
            ALL0W_SEARCH_INNER_TO_ADMIN,
            ALL0W_WRITE_DELETE_SEARCH_INNER_TO_ALL,
            ALLOW_INNER_SEARCH_TO_CN_ADMINS,
            ALLOW_INNER_ALL_TO_SIMPLE,
            ALLOW_INNER_ALL_TODAY,
            ALLOW_INNER_ALL_THIS_HOUR,
            ALLOW_INNER_ALL_TO_ADMIN_AND_LOCALHOST_OR_SSL,
            ALLOW_INNER_SEARCH_FROM_BASE_LOCALHOST,
            ALLOW_BASE_SEARCH_REALATTRS_TO_LOCALHOST,
            ALLOW_BASE_SEARCH_OUR_ATTRS_TO_ADMIN,
            ALLOW_BASE_SEARCH_OU_AND_PERSON_TO_SIMPLE
    );

    testParams.addSingleSearch(ADMIN_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_SUB, INNER_OU_FULL_LDIF__SEARCH_TESTS);
    testParams.addSingleSearch(ADMIN_DN, OU_LEAF_DN, OBJECTCLASS_STAR, SCOPE_SUB, LEAF_OU_FULL_LDIF__SEARCH_TESTS);
    testParams.addSingleSearch(ADMIN_DN, OU_LEAF_DN, OBJECTCLASS_STAR, SCOPE_ONE, LEVEL_3_USER_LDIF__SEARCH_TESTS);
    testParams.addSingleSearch(ADMIN_DN, OU_INNER_DN, OBJECTCLASS_STAR, SCOPE_BASE, INNER_OU_LDIF__SEARCH_TESTS);
  }


  /** TODO: add explicit attribute list support to this. */
  private static class SingleSearchParams {
    private final String _bindDn;
    private final String _bindPw;
    private final String _proxyDN;
    private final String _searchBaseDn;
    private final String _searchFilter;
    private final String _searchScope;
    private final String _expectedResultsLdif;
    private final String _initialDitLdif;
    private final String _aciLdif;
    private final String[] _attributes;

    public static SingleSearchParams proxiedSearch(String bindDn,
        String bindPw, String proxyDN, String searchBaseDn,
        String searchFilter, String searchScope, String expectedResultsLdif,
        String initialDitLdif, String aciLdif, String... attributes)
    {
      return new SingleSearchParams(bindDn, bindPw, proxyDN, searchBaseDn,
          searchFilter, searchScope, expectedResultsLdif, initialDitLdif,
          aciLdif, attributes);
    }

    public static SingleSearchParams nonProxiedSearch(String bindDn,
        String bindPw, String searchBaseDn, String searchFilter,
        String searchScope, String expectedResultsLdif, String initialDitLdif,
        String aciLdif, String... attributes)
    {
      return new SingleSearchParams(bindDn, bindPw, null, searchBaseDn,
          searchFilter, searchScope, expectedResultsLdif, initialDitLdif,
          aciLdif, attributes);
    }

    private SingleSearchParams(String bindDn, String bindPw,
        String proxyDN, String searchBaseDn, String searchFilter, String searchScope,
        String expectedResultsLdif, String initialDitLdif, String aciLdif,
        String... attributes)
    {
      _bindDn = bindDn;
      _bindPw = bindPw;
      _proxyDN = proxyDN;
      _searchBaseDn = searchBaseDn;
      _searchFilter = searchFilter;
      _searchScope = searchScope;
      _expectedResultsLdif = expectedResultsLdif;
      _initialDitLdif = initialDitLdif;
      _aciLdif = aciLdif;
      _attributes = attributes;
    }

    public SingleSearchParams(SingleSearchParams that, String initialDitLdif, String aciLdif) {
      _bindDn = that._bindDn;
      _bindPw = that._bindPw;
      _proxyDN = null;
      _searchBaseDn = that._searchBaseDn;
      _searchFilter = that._searchFilter;
      _searchScope = that._searchScope;
      _expectedResultsLdif = that._expectedResultsLdif;
      _initialDitLdif = initialDitLdif;
      _aciLdif = aciLdif;
      _attributes = new String[0];
    }

    public String[] getLdapSearchArgs()
    {
      final List<String> args = new ArrayList<>();

      if (_bindDn.equals(ANNONYMOUS_DN))
      {
        args.addAll(Arrays.asList(
            "-h", "127.0.0.1",
            "-p", getServerLdapPort(),
            "-b", _searchBaseDn,
            "-s", _searchScope,
            _searchFilter));
      }
      else if (_proxyDN != null)
      {
        args.addAll(Arrays.asList(
            "-h", "127.0.0.1",
            "-p", getServerLdapPort(),
            "-D", _bindDn,
            "-w", _bindPw,
            "-b", _searchBaseDn,
            "-s", _searchScope,
            "-Y", "dn:" + _proxyDN,
            _searchFilter));
      }
      else
      {
        args.addAll(Arrays.asList(
            "-h", "127.0.0.1",
            "-p", getServerLdapPort(),
            "-D", _bindDn,
            "-w", _bindPw,
            "-b", _searchBaseDn,
            "-s", _searchScope,
            _searchFilter));
      }

      Collections.addAll(args, _attributes);

      return args.toArray(new String[args.size()]);
    }

    /** This is primarily used for debug output on a failure. */
    public String getCombinedSearchArgs() {
      return "-h 127.0.0.1" +
      " -p " + getServerLdapPort() +
      " -D " + _bindDn +
      " -w " + _bindPw +
      " -b " + _searchBaseDn +
      " -s " + _searchScope +
      " \"" + _searchFilter + "\"";
    }

    public String[] getLdapCompareArgs(String attrAssertion) {
      return new String[] {
         "-h", "127.0.0.1",
         "-p", getServerLdapPort(),
         "-D", _bindDn,
         "-w", _bindPw,
        attrAssertion,
        _searchBaseDn};
    }
  }

  private static class SearchTestParams {
    /** The server DIT to run the tests against. */
    private final String _initialDitLdif;

    /** ACIs that will produce the same search results for the above DIT. */
    private final List<String> _equivalentAciLdifs;

    private final List<SingleSearchParams> _searchTests = new ArrayList<>();

    public SearchTestParams(String initialDitLdif, String... equivalentAciLdifs) {
      _initialDitLdif = initialDitLdif;
      _equivalentAciLdifs = Arrays.asList(equivalentAciLdifs);
    }

    private void addSingleSearch(
        String bindDn, String searchBaseDn, String searchFilter, String searchScope, String expectedResultsLdif) {
      for (String equivalentAci: _equivalentAciLdifs) {
        _searchTests.add(SingleSearchParams.nonProxiedSearch(bindDn, DN_TO_PW.get(bindDn), searchBaseDn,
            searchFilter, searchScope, expectedResultsLdif, _initialDitLdif, equivalentAci));
      }
    }
  }

  @DataProvider
  private Object[][] searchTestParams() throws Throwable {
    TestCaseUtils.startServer();  // Apparently necessary since the DataProviders can be called before @BeforeClass.

    try {
      List<Object[]> allTestParams = new ArrayList<>();

      for (SearchTestParams testParams: SEARCH_TEST_PARAMS) {
        for (SingleSearchParams singleTest: testParams._searchTests) {
          allTestParams.add(new Object[]{singleTest});
        }
      }

      return allTestParams.toArray(new Object[][]{});
    } catch (Throwable e) {
      // We had some exceptions here and they were hard to track down
      // because they get hidden behind an InvocationTargetException.
      e.printStackTrace();
      throw e;
    }
  }

  /**
   * Test LDAP compare.
   * @throws Throwable If the compare is not valid for the ACI.
   */
  @Test
  public void testCompare() throws Throwable
  {
    SingleSearchParams adminParam =
        SingleSearchParams.nonProxiedSearch(ADMIN_DN, ADMIN_PW,
            LEVEL_3_USER_DN, OBJECTCLASS_STAR, SCOPE_BASE, null, null, null);

    addEntries(BASIC_LDIF__GROUP_SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
    modEntries(COMPARE_ACI, DIR_MGR_DN, DIR_MGR_PW);
    String userResults =
        ldapCompare(adminParam.getLdapCompareArgs("cn:level3 user"),
            LDAPResultCode.COMPARE_TRUE);
    assertNotEquals(userResults, "");
  }


  /**
   * Test proxy keyword using modify DN. Exact test as testModDN, except using
   * proxied authorization for modifies and searches.
   *
   * Add a set of ACIs to allow exports, imports and write rights to the
   * proxy user PROXY_USER_DN. Also add an aci low in the DIT, with search and
   * read rights to the proxy user. This is ACI is to test the
   * ACI list after a move has been made. Add an ACI that allows LEVEL_1_USER_DN
   * proxy authorization rights (proxy).
   *
   * Move the subtree binding as LEVEL_1_USER_DN using proxied authorization,
   * search with base at new DN binding as LEVEL_1_USER_DN proxied
   * authorization, then move the tree back binding as LEVEL_1_USER_DN using
   * proxied authorization and lastly re-search with base at orig DN
   * binding as LEVEL_1_USER_DN using proxied authorization.
   * @throws Throwable
   */
  @Test
  public void testProxyModDN() throws Throwable {
    SingleSearchParams userParamOrig = SingleSearchParams.proxiedSearch(LEVEL_1_USER_DN,
                                      "pa$$word",PROXY_USER_DN, SALES_USER_1,
                                      OBJECTCLASS_STAR, SCOPE_BASE,
                                      null, null, null);
    SingleSearchParams userParamNew = SingleSearchParams.proxiedSearch(LEVEL_1_USER_DN,
                                     "pa$$word",PROXY_USER_DN, SALES_USER_NEW_1,
                                      OBJECTCLASS_STAR, SCOPE_BASE,
                                      null, null, null);
    {
      addEntries(BASIC_LDIF__GROUP_SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
      modEntries(ACI_PROXY_IMPORT_MGR, DIR_MGR_DN, DIR_MGR_PW);
      modEntries(ACI_PROXY_CONTROL_LEVEL_1, DIR_MGR_DN, DIR_MGR_PW);
      modEntries(ACI_PROXY_IMPORT_MGR_NEW, DIR_MGR_DN, DIR_MGR_PW);
      modEntries(ACI_PROXY_EXPORT_MGR, DIR_MGR_DN, DIR_MGR_PW);
      modEntries(ACI_PROXY_EXPORT_MGR_NEW, DIR_MGR_DN, DIR_MGR_PW);
      modEntries(ACI_PROXY_WRITE_RDN_ATTRS, DIR_MGR_DN, DIR_MGR_PW);
      modEntries(ACI_PROXY_MOVED_ENTRY, DIR_MGR_DN, DIR_MGR_PW);
      modEntries(ACI_PROXY_LEVEL_1, DIR_MGR_DN, DIR_MGR_PW);
      String modrdnLdif =
              makeModDN(SALES_DN, "cn=sales dept", "0", MANAGER_NEW_DN);
      modEntries(modrdnLdif, LEVEL_1_USER_DN, "pa$$word", PROXY_USER_DN);
      String userNewResults = ldapSearch(userParamNew.getLdapSearchArgs());
      assertNotEquals(userNewResults, "");
      String modrdnLdif1 =
                makeModDN(SALES_NEW_DN, "cn=sales dept", "0", MANAGER_DN);
      modEntries(modrdnLdif1, LEVEL_1_USER_DN, "pa$$word", PROXY_USER_DN);
      String userOrigResults = ldapSearch(userParamOrig.getLdapSearchArgs());
      assertNotEquals(userOrigResults, "");
    }
  }

  /**
   * Test modify DN. Add a set of ACIs to allow exports, imports and write
   * rights. Also add an aci low in the DIT to test the ACI list after a move
   * has been made. Move the subtree, search with base at new DN, move the
   * tree back and re-search with base at orig DN.
   * @throws Throwable
   */
  @Test
  public void testModDN() throws Throwable {
    SingleSearchParams userParamOrig = SingleSearchParams.nonProxiedSearch(LEVEL_1_USER_DN,
                                      "pa$$word", SALES_USER_1,
                                      OBJECTCLASS_STAR, SCOPE_BASE,
                                      null, null, null);
    SingleSearchParams userParamNew = SingleSearchParams.nonProxiedSearch(LEVEL_1_USER_DN,
                                      "pa$$word", SALES_USER_NEW_1,
                                      OBJECTCLASS_STAR, SCOPE_BASE,
                                      null, null, null);


    {
        addEntries(BASIC_LDIF__GROUP_SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
        modEntries(ACI_IMPORT_MGR, DIR_MGR_DN, DIR_MGR_PW);
        modEntries(ACI_IMPORT_MGR_NEW, DIR_MGR_DN, DIR_MGR_PW);
        modEntries(ACI_EXPORT_MGR, DIR_MGR_DN, DIR_MGR_PW);
        modEntries(ACI_EXPORT_MGR_NEW, DIR_MGR_DN, DIR_MGR_PW);
        modEntries(ACI_WRITE_RDN_ATTRS, DIR_MGR_DN, DIR_MGR_PW);
        modEntries(ACI_MOVED_ENTRY, DIR_MGR_DN, DIR_MGR_PW);
        String modrdnLdif =
                makeModDN(SALES_DN, "cn=sales dept", "0", MANAGER_NEW_DN);
        modEntries(modrdnLdif, LEVEL_1_USER_DN, "pa$$word");
        String userNewResults = ldapSearch(userParamNew.getLdapSearchArgs());
        assertNotEquals(userNewResults, "");
        String modrdnLdif1 =
                makeModDN(SALES_NEW_DN, "cn=sales dept", "0", MANAGER_DN);
        modEntries(modrdnLdif1, LEVEL_1_USER_DN, "pa$$word");
        String userOrigResults = ldapSearch(userParamOrig.getLdapSearchArgs());
        assertNotEquals(userOrigResults, "");
    }
  }

  /**
   * Test anonymous modify DN with the same RDN.
   */
  @Test
  public void testAnonymousModDNSameRDN() throws Throwable {
    addEntries(BASIC_LDIF__GROUP_SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
    String modRDNLdif = makeModDN(OU_LEAF_DN, "ou=leaf", "1", null);
    LDIFModify(modRDNLdif, "", "", null, LDAPResultCode.NO_SUCH_OBJECT);
  }

  /**
   * Test selfwrite right. Attempt to bind as level3 user and remove level1
   * user from a group, should fail.
   * @throws Throwable If the delete succeeds.
   */
  @Test
  public void testNonSelfWrite() throws Throwable {
    addEntries(BASIC_LDIF__GROUP_SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
    modEntries(SELFWRITE_ACI, DIR_MGR_DN, DIR_MGR_PW);
    deleteAttrFromEntry(OU_GROUP_1_DN, "member", LEVEL_1_USER_DN,
        LEVEL_3_USER_DN, "pa$$word", false);
  }

  /**
   * Test selfwrite right. Attempt to bind as level1 user and remove itself
   * from a group, should succeed.
   * @throws Throwable If the delete fails.
   */
  @Test
  public void testSelfWrite() throws Throwable {
    addEntries(BASIC_LDIF__GROUP_SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
    modEntries(SELFWRITE_ACI, DIR_MGR_DN, DIR_MGR_PW);
    deleteAttrFromEntry(OU_GROUP_1_DN, "member", LEVEL_1_USER_DN,
        LEVEL_1_USER_DN, "pa$$word", true);
  }

  /**
   * Test ACI using dns="*" bind rule pattern. Search should succeed.
   * @throws Throwable  If the search doesn't return any entries.
   */
  @Test
  public void testDNSWildCard() throws Throwable {
        SingleSearchParams userParam =
          SingleSearchParams.nonProxiedSearch(LEVEL_1_USER_DN,
                                   "pa$$word", LEVEL_3_USER_DN,
                                   OBJECTCLASS_STAR, SCOPE_BASE,
                                   null, null, null);
        {
            addEntries(BASIC_LDIF__GROUP_SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
            modEntries(DNS_ALL_ACI, DIR_MGR_DN, DIR_MGR_PW);
            String userResults = ldapSearch(userParam.getLdapSearchArgs());
            assertNotEquals(userResults, "");
        }
  }


  /**
   * Test group  bind rule ACI keywords.
   */
 @Test
 public void testGroupAcis()  throws Throwable {
     //group2   fail
     SingleSearchParams adminParam =
       SingleSearchParams.nonProxiedSearch(ADMIN_DN, ADMIN_PW, LEVEL_3_USER_DN,
                                    OBJECTCLASS_STAR, SCOPE_BASE,
                                    null, null, null);
     //group1  pass
     SingleSearchParams userParam =
       SingleSearchParams.nonProxiedSearch(LEVEL_1_USER_DN,
                                     "pa$$word", LEVEL_3_USER_DN,
                                     OBJECTCLASS_STAR, SCOPE_BASE,
                                     null, null, null);
        {
            addEntries(BASIC_LDIF__GROUP_SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
            modEntries(GROUP1_GROUPDN_MODS, DIR_MGR_DN, DIR_MGR_PW);
            String userResults = ldapSearch(userParam.getLdapSearchArgs());
            assertNotEquals(userResults, "");
            String adminResults = ldapSearch(adminParam.getLdapSearchArgs());
            Assert.assertEquals(adminResults, "");
        }
 }

    /**
     * Test global ACI. Two ACIs are used, one protecting "cn=monitor" and the
     * other the test DIT.
     *
     * @throws Throwable
     */
    @Test
 public void testGlobalAcis()  throws Throwable {
     SingleSearchParams monitorParam =
       SingleSearchParams.nonProxiedSearch(ADMIN_DN, ADMIN_PW, MONITOR_DN,
                                    OBJECTCLASS_STAR, SCOPE_BASE,
                                    null, null, null);
      SingleSearchParams baseParam =
        SingleSearchParams.nonProxiedSearch(LEVEL_1_USER_DN,
                                     "pa$$word", OU_BASE_DN,
                                     OBJECTCLASS_STAR, SCOPE_BASE,
                                     null, null, null);

        addEntries(BASIC_LDIF__SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
        modEntries(GLOBAL_MODS, DIR_MGR_DN, DIR_MGR_PW);
        String monitorResults = ldapSearch(monitorParam.getLdapSearchArgs());
        assertNotEquals(monitorResults, "");
        String baseResults = ldapSearch(baseParam.getLdapSearchArgs());
        assertNotEquals(baseResults, "");
        deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI, true);
        monitorResults = ldapSearch(monitorParam.getLdapSearchArgs());
        Assert.assertEquals(monitorResults, "");
        baseResults = ldapSearch(baseParam.getLdapSearchArgs());
        Assert.assertEquals(baseResults, "");
  }

  @Test(dataProvider = "searchTestParams")
  public void testSearchWithAcis(SingleSearchParams params) throws Throwable {
    if (TESTS_ARE_DISABLED) {  // This is a hack to make sure we can disable the tests.
      return;
    }
    String searchResults = "<search-not-issued>";
    String diffFromExpected = "<diff-not-calculated>";
    try {
      // Modify the entries, and apply the LDIF
      addEntries(params._initialDitLdif, DIR_MGR_DN, DIR_MGR_PW);
      modEntries(params._aciLdif, DIR_MGR_DN, DIR_MGR_PW);

      // Now issue the search and see if we get what we expect.
      searchResults = ldapSearch(params.getLdapSearchArgs());
      diffFromExpected = diffLdif(params._expectedResultsLdif, searchResults);

      // Ignoring whitespace the diff should be empty.
      assertEquals(diffFromExpected.trim(), "");
    } catch (Throwable e) {
        System.err.println(
              "Started with dit:\n" +
              params._initialDitLdif +
              ((params._aciLdif.length() == 0) ?
                "" : ("And then applied the following acis on top of this:\n" + params._aciLdif)) +
              "'ldapsearch " + params.getCombinedSearchArgs() + "' returned\n" +
              searchResults + "\nInstead of:\n" +
              params._expectedResultsLdif +
              "The difference is:\n" +
              diffFromExpected);
      throw e;
    }
  }



  /**
   * Test search with target filter and target attributes do not conflict with
   * requested attributes in search request. See OpenDS issue 4583.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testSearchTargetFilterAndAttributes() throws Exception
  {
    addEntries(BASIC_LDIF__GROUP_SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
    modEntries(SEARCH_ATTRIBUTES_ALLOW_ACI, DIR_MGR_DN, DIR_MGR_PW);

    // Now issue the search and see if we get what we expect.
    SingleSearchParams params = SingleSearchParams.nonProxiedSearch(ADMIN_DN, ADMIN_PW,
        LEVEL_3_USER_DN, OBJECTCLASS_STAR, SCOPE_BASE, null, null, null, "cn", "sn", "givenName");

    // First check cn, sn, and givenName are all readable without the ACI.
    String expectedLdif = TestCaseUtils.makeLdif(
        "dn: " + LEVEL_3_USER_DN,
        "cn: level3 user",
        "sn: user",
        "givenName: level3");
    String actualResults = ldapSearch(params.getLdapSearchArgs());
    String diffFromExpected = diffLdif(actualResults, expectedLdif);

    // Ignoring whitespace the diff should be empty.
    assertEquals(diffFromExpected.trim(), "", "Got: \n" + actualResults + "\nBut expected:\n" + expectedLdif);


    // Add the ACI: this will prevent the cn and sn attributes from being read
    // for entries having the person objectClass. We need to ensure that this
    // ACI is applied even though the target filter attribute (objectClass) is
    // not being returned in the results.
    modEntries(SEARCH_ATTRIBUTES_DENY_ACI, DIR_MGR_DN, DIR_MGR_PW);

    expectedLdif = TestCaseUtils.makeLdif(
        "dn: " + LEVEL_3_USER_DN,
        "givenName: level3");
    actualResults = ldapSearch(params.getLdapSearchArgs());
    diffFromExpected = diffLdif(actualResults, expectedLdif);

    // Ignoring whitespace the diff should be empty.
    assertEquals(diffFromExpected.trim(), "", "Got: \n" + actualResults + "\nBut expected:\n" + expectedLdif);
  }



  /**
   * Test online handler re-initialization using global and selfwrite
   * right test cases.
   * @throws Throwable If any test cases fail after re-initialization.
   */
  @Test
  public void testAciHandlerReInit() throws Throwable {

    // Setup using global and selfwrite test cases.
    addEntries(BASIC_LDIF__GROUP_SEARCH_TESTS, DIR_MGR_DN, DIR_MGR_PW);
    modEntries(GLOBAL_MODS, DIR_MGR_DN, DIR_MGR_PW);
    modEntries(SELFWRITE_ACI, DIR_MGR_DN, DIR_MGR_PW);

    // Disable ACI handler.
    TestCaseUtils.dsconfig("set-access-control-handler-prop",
            "--set", "enabled:false");

    // Enable ACI handler.
    TestCaseUtils.dsconfig("set-access-control-handler-prop",
            "--set", "enabled:true");

    // Test global ACI. Two ACIs are used, one protecting
    // "cn=monitor" and the other the test DIT.
    SingleSearchParams monitorParam =
      SingleSearchParams.nonProxiedSearch(ADMIN_DN, ADMIN_PW, MONITOR_DN,
            OBJECTCLASS_STAR, SCOPE_BASE,
            null, null, null);
    SingleSearchParams baseParam =
      SingleSearchParams.nonProxiedSearch(LEVEL_1_USER_DN,
            "pa$$word", OU_BASE_DN,
            OBJECTCLASS_STAR, SCOPE_BASE,
            null, null, null);

      String monitorResults = ldapSearch(monitorParam.getLdapSearchArgs());
      assertNotEquals(monitorResults, "");
      String baseResults = ldapSearch(baseParam.getLdapSearchArgs());
      assertNotEquals(baseResults, "");
      deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI, true);
      monitorResults = ldapSearch(monitorParam.getLdapSearchArgs());
      Assert.assertEquals(monitorResults, "");
      baseResults = ldapSearch(baseParam.getLdapSearchArgs());
      Assert.assertEquals(baseResults, "");

    // Test selfwrite right. Attempt to bind as level3 user and remove
    // level1 user from a group, should fail.
    deleteAttrFromEntry(OU_GROUP_1_DN, "member", LEVEL_1_USER_DN,
        LEVEL_3_USER_DN, "pa$$word", false);

    // Test selfwrite right. Attempt to bind as level1 user and remove
    // itself from a group, should succeed.
    deleteAttrFromEntry(OU_GROUP_1_DN, "member", LEVEL_1_USER_DN,
        LEVEL_1_USER_DN, "pa$$word", true);
  }


// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
//
//   U T I L I T I E S
//
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------



    /**
     * Create an ACI string with the specified variable string list. The method
     * uses the global ACI attribute type name, instead of "aci".
     * @param aciFields The fields to use to build the ACI.
     * @return  An ACI string.
     */
    private static String buildGlobalAciValue(String... aciFields) {
     return _buildAciValue(ATTR_AUTHZ_GLOBAL_ACI + ": ", aciFields);
    }

  /**
   * Build the value for the aci from the specified fields. This is a bit of a
   * kludge, but it does help us from having nested "\"", and it does allow us
   * to more easily generate combinations of acis.
   */
  private static String buildAciValue(String... aciFields) {
     return _buildAciValue("aci:", aciFields);
  }

  /**
 * Build the value for the aci from the specified fields.
 *
 * This is a bit of a kludge, but it does help us from having nested "\"",
 * and it does allow us to more easily generate combinations of acis.
 */
private static String _buildAciValue(String attr, String... aciFields) {
  StringBuilder aci = new StringBuilder(attr);

  // Go through target* first
  for (int i = 0; i < aciFields.length - 1; i += 2) {
    String aciField = aciFields[i];
    String aciValue = aciFields[i+1];

    if (aciField.startsWith("targ") || aciField.equals("extop")) {
      if (!aciField.endsWith("=")) {  // We allow = or more importantly != to be included with the target
        aciField += "=";
      }
      aci.append("(" + aciField + "\"" + aciValue + "\")" + EOL + " ");
    }
  }

  aci.append("(version 3.0;acl ");

  // Try to get the name
  for (int i = 0; i < aciFields.length - 1; i += 2) {
    String aciField = aciFields[i];
    String aciValue = aciFields[i+1];

    if (aciField.equals("name")) {
      aci.append("\"" + aciValue + "\"");
    }
  }

  aci.append("; ");

  // Anything else is permission and a bindRule
  for (int i = 0; i < aciFields.length - 1; i += 2) {
    String permission = aciFields[i];
    String bindRule = aciFields[i+1];

    if (!permission.startsWith("targ") && !permission.equals("extop") &&
        !permission.equals("name")) {
      aci.append(EOL + " " + permission + " " + bindRule + ";");
    }
  }

  aci.append(")");

  return aci.toString();
}


    /**
     * Create a ldif entry with the specified variable ACI list. This method
     * allows the attribute type to be specified in an argument.
     * @param attr The attribute type name to use for the aci attribute.
     * @param dn  The dn to use.
     * @param acis  A variable list of ACI strings.
     * @return A ldif entry string.
     */
    private static String makeAttrAddAciLdif(String attr, String dn,
                                              String... acis) {
    return _makeAddAciLdif(attr,dn,acis);
  }

  private static String makeAddAciLdif(String dn, String... acis) {
    return _makeAddAciLdif("aci" ,dn,acis);
  }

  private static String _makeAddAciLdif(String attr, String dn, String... acis) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: " + dn).append(EOL);
    ldif.append("changetype: modify").append(EOL);
    ldif.append("add: " + attr).append(EOL);
    for(String aci : acis)
    {
      ldif.append(aci).append(EOL);
    }
    ldif.append(EOL);
    return ldif.toString();
  }

  private void addEntries(String ldif, String bindDn, String bindPassword) throws Exception {
    addEntries(ldif, bindDn, bindPassword, true);
  }


  private void addEntries(String ldif, String bindDn, String bindPassword, boolean expectSuccess) throws Exception {
    File tempFile = getTemporaryLdifFile();
    TestCaseUtils.writeFile(tempFile, ldif);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", getServerLdapPort(),
      "-D", bindDn,
      "-w", bindPassword,
      "-f", tempFile.getAbsolutePath()
    };

    ldapModify(args, expectSuccess);
  }

  private void ldapModify(String[] args, boolean expectSuccess) throws Exception {
    clearOutputStream();
    int retVal = LDAPModify.run(getOutputPrintStream(), getOutputPrintStream(), args);
    assertEquals(retVal == 0, expectSuccess, "Return value = " + retVal);
  }

  private String ldapSearch(String[] args) throws Exception {
    clearOutputStream();
    int retVal = LDAPSearch.run(getOutputPrintStream(), getOutputPrintStream(), args);
    Assert.assertEquals(retVal, 0, "Non-zero return code because, error: " + getOutputStreamContents());
    return getOutputStreamContents();
  }

  private String ldapCompare(String[] args, int expectedRc) throws Exception {
    clearOutputStream();
    int retVal = LDAPCompare.run(getOutputPrintStream(), getOutputPrintStream(), args);
    Assert.assertEquals(retVal, expectedRc,  "Non-zero return code because, error: " + getOutputStreamContents());
    return getOutputStreamContents();
  }

  private void modEntries(String ldif, String bindDn, String bindPassword)
          throws Exception {
    modEntries(ldif, bindDn, bindPassword, null, true, false);
  }

  private void modEntries(String ldif, String bindDn, String bindPassword, String proxyDN)
          throws Exception {
    modEntries(ldif, bindDn, bindPassword, proxyDN, true, false);
  }

  private void modEntriesExpectFailure(String ldif, String bindDn,
                                       String bindPassword) throws Exception {
    modEntries(ldif, bindDn, bindPassword, null, false, false);
  }

  private void _modEntries(String ldif, String bindDn, String bindPassword,
                          boolean expectSuccess) throws Exception {
    File tempFile = getTemporaryLdifFile();
    TestCaseUtils.writeFile(tempFile, ldif);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", getServerLdapPort(),
      "-D", bindDn,
      "-w", bindPassword,
      "-f", tempFile.getAbsolutePath()
    };

    ldapModify(args, expectSuccess);
  }

    private void modEntries(String ldif, String bindDn, String bindPassword,
                          String proxyDN, boolean expectSuccess,
                          boolean contFlag)
    throws Exception {
    File tempFile = getTemporaryLdifFile();
    TestCaseUtils.writeFile(tempFile, ldif);
    ArrayList<String> argList=new ArrayList<>(20);
    argList.add("-h");
    argList.add("127.0.0.1");
    argList.add("-p");
    argList.add(getServerLdapPort());
    argList.add("-D");
    argList.add(bindDn);
    argList.add("-w");
    argList.add(bindPassword);
    argList.add("-f");
    argList.add(tempFile.getAbsolutePath());
    if(contFlag)
    {
      argList.add("-c");
    }
    if(proxyDN != null) {
        argList.add("-Y");
        argList.add("dn:" + proxyDN);
    }
    String[] args = new String[argList.size()];
    ldapModify(argList.toArray(args), expectSuccess);
  }

    private void deleteAllTestEntries() throws Exception {
        deleteEntries(ALL_TEST_ENTRY_DNS_BOTTOM_UP);
    }

  private void deleteAttrFromEntry(String dn, String attr, String val,
                                   String bindDN, String pwd,
                                   boolean errorOk) throws Exception {
        StringBuilder ldif = new StringBuilder();
        ldif.append(TestCaseUtils.makeLdif(
                "dn: "  + dn,
                "changetype: modify",
                "delete: " + attr,
                attr + ":" + val));
        modEntries(ldif.toString(), bindDN, pwd, null, errorOk, false);
    }


  private static String makeModDN(String dn, String newRDN, String deleteOldRDN,
                                  String newSuperior ) throws Exception {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: " + dn).append(EOL);
    ldif.append("changetype: modrdn").append(EOL);
    ldif.append("newrdn: " + newRDN).append(EOL);
    ldif.append("deleteoldrdn: " + deleteOldRDN).append(EOL);
    if(newSuperior != null)
    {
      ldif.append("newsuperior: " + newSuperior).append(EOL);
    }
    ldif.append(EOL);
    return ldif.toString();
  }



      private void deleteAttrFromEntry(String dn, String attr, boolean errorOk) throws Exception {
          StringBuilder ldif = new StringBuilder();
          ldif.append(TestCaseUtils.makeLdif(
                  "dn: "  + dn,
                  "changetype: modify",
                  "delete: " + attr));
          modEntries(ldif.toString(), DIR_MGR_DN, DIR_MGR_PW,  null, errorOk, false);
      }

    private void deleteEntries(String[] entries) throws Exception {
        // TODO: make this actually do a search first!
        StringBuilder ldif = new StringBuilder();
        for (String dn: entries) {
            ldif.append(TestCaseUtils.makeLdif(
                    "dn: " + dn,
                    "changetype: delete"
            ));
        }
        modEntries(ldif.toString(), DIR_MGR_DN, DIR_MGR_PW, null, true, true);
    }

  /**
   * Return the difference between two ldif files.
   */
  private String diffLdif(String actualLdif, String expectedLdif) throws Exception {
    actualLdif = stripPassword(actualLdif);
    expectedLdif = stripPassword(expectedLdif);

    File actualLdifFile = getTemporaryLdifFile("aci-tests-actual");
    File expectedLdifFile = getTemporaryLdifFile("aci-tests-expected");
    File diffLdifFile = getTemporaryLdifFile("aci-tests-diff");

    TestCaseUtils.writeFile(actualLdifFile, actualLdif);
    TestCaseUtils.writeFile(expectedLdifFile, expectedLdif);
    diffLdifFile.delete();

    String[] args =
    {
      "--outputLDIF", diffLdifFile.getAbsolutePath(),
      actualLdifFile.getAbsolutePath(),
      expectedLdifFile.getAbsolutePath()
    };

    int retVal = LDIFDiff.run(System.out, System.err, args);
    assertEquals(retVal, 0, "LDIFDiff failed");

    if (diffLdifFile.exists()) {
      return stripComments(TestCaseUtils.readFile(diffLdifFile));
    }
    return "";
  }

  private static String stripPassword(String ldif) {
    return stripAttrs(ldif, "userpassword");
  }

  /** This won't catch attrs that wrap to the next line, but that shouldn't happen. */
  private static String stripAttrs(String ldif, String... attrs) {
    // Generate "((cn)|(givenname))"
    String anyAttr = "(";
    for (int i = 0; i < attrs.length; i++) {
      if (i > 0) {
        anyAttr += "|";
      }
      anyAttr += "(" + attrs[i] + ")";
    }
    anyAttr += ")";

    Pattern pattern = Pattern.compile("^" + anyAttr + "\\:(.*?)^",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    return pattern.matcher(ldif).replaceAll("");
  }

  /** This won't catch passwords that wrap to the next line, but that shouldn't happen. */
  private static final Pattern COMMENTS_REGEX = Pattern.compile("#.*", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
  private static String stripComments(String ldif) {
    return COMMENTS_REGEX.matcher(ldif).replaceAll("");
  }

  private static ThreadLocal<Map<String,File>> _tempLdifFilesByName = new ThreadLocal<>();

  /**
   * To avoid a proliferation of temporary files, use the same ones over and over.
   * We expect to use a single thread for the tests, but use a threadlocal
   * just in case.
   */
  private File getTemporaryLdifFile(String name) throws IOException {
    Map<String,File> tempFilesForThisThread = _tempLdifFilesByName.get();
    if (tempFilesForThisThread == null) {
      tempFilesForThisThread = new HashMap<>();
      _tempLdifFilesByName.set(tempFilesForThisThread);
    }
    File tempFile = tempFilesForThisThread.get(name);
    if (tempFile == null) {
      tempFile = File.createTempFile(name, ".ldif");
      tempFile.deleteOnExit();
      tempFilesForThisThread.put(name, tempFile);
    }
    return tempFile;
  }

  /** Convenience for when we only need one at time. */
  private File getTemporaryLdifFile() throws IOException {
    return getTemporaryLdifFile("aci-tests");
  }

  private static ByteArrayOutputStream _cmdOutput = new ByteArrayOutputStream();
  private static void clearOutputStream() {
    _cmdOutput.reset();
  }

  private static String getOutputStreamContents() {
    return _cmdOutput.toString();
  }

  private static OutputStream getOutputStream() {
    return _cmdOutput;
  }

  private static PrintStream getOutputPrintStream() {
    return new PrintStream(getOutputStream());
  }

  private static String makeUserLdif(String dn, String givenName, String sn, String password) {
    String cn = givenName + " " + sn;
    Assert.assertTrue(dn.startsWith("cn=" + cn));  // Enforce this since it's awkward to build the dn here too
    return TestCaseUtils.makeLdif(
            "dn: " + dn,
            "objectclass: inetorgperson",
            "objectclass: organizationalperson",
            "objectclass: person",
            "objectclass: top",
            "cn: " +  cn,
            "sn: " + sn,
            "givenName: " + givenName,
            "userpassword: " + password,
            "ds-privilege-name: proxied-auth");
  }

    private static String makeUserLdif(String dn, String givenName, String sn,
                                       String password, String... attrs) {
        StringBuilder ldif = new StringBuilder();
        String cn = givenName + " " + sn;
        // Enforce this since it's awkward to build the dn here too
        Assert.assertTrue(dn.startsWith("cn=" + cn));
        ldif.append("dn: ").append(dn).append(EOL);
        ldif.append("objectclass: inetorgperson").append(EOL);
        ldif.append("objectclass: organizationalperson").append(EOL);
        ldif.append("objectclass: person").append(EOL);
        ldif.append("objectclass: top").append(EOL);
        ldif.append("cn: ").append(cn).append(EOL);
        ldif.append("sn: ").append(sn).append(EOL);
        ldif.append("givenName: ").append(givenName).append(EOL);
        ldif.append("userpassword: ").append(password).append(EOL);
        for(String attr : attrs) {
            if(attr.startsWith("ldap://"))
            {
              ldif.append("labeledURI: ").append(attr).append(EOL);
            }
            else if(attr.startsWith("cn=group"))
            {
              ldif.append("seeAlso: ").append(attr).append(EOL);
            }
            else
            {
              ldif.append("manager: ").append(attr).append(EOL);
            }
        }
        ldif.append(EOL);
        return ldif.toString();
    }


    /**
     * Makes a group ldif entry using the the specified DN and members.
     * @param dn The dn to use in building the ldif.
     * @param members A variable list of member strings.
     * @return   The ldif entry string.
     */
    private static String makeGroupLdif(String dn,  String... members) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: " + dn).append(EOL);
    ldif.append("objectclass: groupOfNames").append(EOL);
    ldif.append("objectclass: top").append(EOL);
    for(String member : members)
    {
      ldif.append("member: " + member).append(EOL);
    }
    ldif.append(EOL);
    return ldif.toString();
  }


  private static String makeOuLdif(String dn, String ou) {
    Assert.assertTrue(dn.startsWith("ou=" + ou));  // Enforce this since it's awkward to build the dn here too
    return TestCaseUtils.makeLdif(
            "dn: " + dn,
            "objectclass: organizationalunit",
            "objectclass: top",
            "ou: " + ou);
  }

  private static String getThisDayOfWeek() {
    int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
    return DAYS_OF_WEEK[dayOfWeek];
  }

  private static String getTomorrowDayOfWeek() {
    int dayOfWeek = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 1) % 7;
    return DAYS_OF_WEEK[dayOfWeek];
  }

  private static String getNotThisDayOfWeek() {
    Set<String> otherDays = newHashSet(DAYS_OF_WEEK);
    otherDays.remove(getThisDayOfWeek());
    String dayList = "";
    for (String otherDay: otherDays) {
      if (dayList.length() > 0) {
        dayList += ",";
      }
      dayList += otherDay;
    }
    return dayList;
  }

  private static String getTimeNow() {
    return TIME_FORMATTER.format(new Date());
  }

  private static String getTimeFromNowWithHourOffset(int hourOffset) {
    GregorianCalendar calendar = new GregorianCalendar();
    calendar.setTime(new Date());
    calendar.add(Calendar.HOUR_OF_DAY, hourOffset);
    return TIME_FORMATTER.format(calendar.getTime());
  }

  private static String getTimeOfDayRuleNextHour() {
    String now = getTimeNow();
    String hourFromNow = getTimeFromNowWithHourOffset(1);
    // If we're within an hour of midnight
    if (hourFromNow.compareTo(now) < 0) {
      return "(timeofday>=\"2300\" or timeofday<=\"0100\")";
    } else {
      return "(timeofday>=\"" + now + "\" and timeofday<=\"" + hourFromNow + "\")";
    }
  }

  private static String getTimeOfDayRulePreviousHour() {
    String now = getTimeNow();
    String hourAgo = getTimeFromNowWithHourOffset(1);
    // If we're within an hour of midnight
    if (hourAgo.compareTo(now) > 0) {
      return "(timeofday>=\"2300\" or timeofday<\"" + getTimeNow() + "\")";
    } else {
      return "(timeofday<\"" + now + "\" and timeofday>=\"" + hourAgo + "\")";
    }
  }

  private static String and(String bindRule1, String bindRule2) {
    return "(" + bindRule1 + " and " + bindRule2 + ")";
  }

  private static  String or(String bindRule1, String bindRule2) {
    return "(" + bindRule1 + " or " + bindRule2 + ")";
  }

  private static  String not(String bindRule) {
    return "not " + bindRule;
  }

  private static String getServerLdapPort() {
    return String.valueOf(TestCaseUtils.getServerLdapPort());
  }
}
