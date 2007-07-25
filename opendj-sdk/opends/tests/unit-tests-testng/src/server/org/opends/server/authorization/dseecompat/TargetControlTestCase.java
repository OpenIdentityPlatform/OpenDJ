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


package org.opends.server.authorization.dseecompat;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.testng.annotations.*;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.LDAPResultCode;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.config.ConfigConstants.ATTR_AUTHZ_GLOBAL_ACI;

/**
 * Unit test to test the targetcontrol ACI keyword.
 */
public class TargetControlTestCase extends AciTestCase {

  private static final String superUser="uid=superuser,ou=admins,o=test";
  private static final String level3User="uid=user.3,ou=People,o=test";
  private static final String level4User="uid=user.4,ou=People,o=test";
  private static final String newPWD="newPWD";


  private static final String level1User="uid=user.1,ou=People,o=test";
  private static final String base="uid=user.3,ou=People,o=test";
  private static final String newRDN = "uid=user.3x";
  private static final String newSup="ou=new,o=test";
  private static final String newDN="uid=user.4," + base;

  private static final String peopleBase="ou=People,o=test";
  private static final String adminBase="ou=Admins,o=test";
  private static final String newPeopleDN="uid=user.6," + peopleBase;
  private static final String newAdminDN="uid=user.6," + adminBase;


  @BeforeClass
  public void setupClass() throws Exception {
    TestCaseUtils.startServer();
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    addEntries();
  }

  @AfterClass
  public void tearDown() throws Exception {
       String aciLdif=makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN,
               G_READ_ACI, G_SELF_MOD, G_SCHEMA, G_DSE, G_USER_OPS, G_CONTROL,
               E_EXTEND_OP);
       LDIFModify(aciLdif, DIR_MGR_DN, PWD);
   }


  @BeforeMethod
  public void clearBackend() throws Exception {
    deleteAttrFromEntry(peopleBase, "aci");
    deleteAttrFromEntry(base, "aci");
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
  }

  private static final String[] newEntry = new String[] {
    "objectClass: top",
    "objectClass: person",
    "objectClass: organizationalPerson",
    "objectClass: inetOrgPerson",
    "uid: john.doe",
    "givenName: John",
    "sn: Doe",
    "cn: John Doe",
    "mail: john.doe@example.com",
    "userPassword: password",
  };

  //Valid targetcontrol statements. Not the complete ACI.
  @DataProvider(name = "validStatements")
  public Object[][] valids() {
    return new Object[][] {
            {"1.3.6.1.4.1.42.2.27.8.5.1"},
            {"2.16.840.1.113730.3.4.18"},
            {"*"},
    };
  }

   //Invalid targetcontrol statements. Not the complete ACI.
  @DataProvider(name = "invalidStatements")
  public Object[][] invalids() {
    return new Object[][] {
            {"1.3.6.1.4.1.42.2.27..8.5.1"},
            {"2.16.840.1.113730.3.XXX.18"},
            {"2.16.840.1.113730.*.4.18"},
            {"2.16.840,1.113730.3.4.18"},
            {"+"},
    };
  }

  private static final
  String ALLOW_ALL = "(targetattr=\"*\")" +
          "(version 3.0;acl \"aclRights access\";" +
          "allow (all) " +
          "userdn=\"ldap:///self\";)";

  private static final
  String aclRightsAci = "(targetattr=\"aclRights\")" +
          "(version 3.0;acl \"aclRights access\";" +
          "allow (search, read) " +
          "userdn=\"ldap:///uid=superuser,ou=admins,o=test\";)";

 //Disallow all controls with wild-card.
  private static final
  String controlNotWC = "(targetcontrol!=\"" + "*" + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///" + superUser + "\";)";

  //Allow all controls with wild-card.
  private static final
  String controlWC = "(targetcontrol=\"" + "*" + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///" + superUser + "\";)";

  //People branch can do any control but geteffectiverights assertion control.
  private static final
  String controlPeople = "(targetcontrol!=\"" +
          OID_GET_EFFECTIVE_RIGHTS + "\")" +
          "(target=\"ldap:///" + peopleBase + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";

  //Admin branch can only do geteffectiverights control.
  private static final
  String controlAdmin = "(targetcontrol=\"" + OID_GET_EFFECTIVE_RIGHTS + "\")" +
          "(target=\"ldap:///" + adminBase + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";

  //Allow either reportauthzID or passwordpolicy controls. Used in the
  //bind tests.
  private static final
  String pwdControls =
          "(targetcontrol=\"" + OID_AUTHZID_REQUEST + "||" +
          OID_PASSWORD_POLICY_CONTROL + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";


  //Allow either no-op or passwordpolicy controls. Used in the
  //ext op tests.
  private static final
  String extOpControls =
          "(targetcontrol=\"" + OID_LDAP_NOOP_OPENLDAP_ASSIGNED + "||" +
          OID_PASSWORD_POLICY_CONTROL + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";

 //Allow all to extended op.
  private static final
  String extOpAll =
          "(extop=\"" + "*" + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";

  //Only allow access to the password policy control. Used to test if the
  //targetattr rule will give access erroneously.
  private static final
  String complicated =
          "(targetcontrol=\"" + OID_PASSWORD_POLICY_CONTROL + "\")" +
          "(targetattr != \"userpassword\")" +
          "(version 3.0; acl \"control\";" +
          "allow(all) userdn=\"ldap:///" + "anyone" + "\";)";

  /**
   * Test valid targetcontrol statements.
   *
   * @param statement The targetcontrol statement to attempt to decode.
   * @throws AciException  If an unexpected result happens.
   */
  @Test(dataProvider = "validStatements")
  public void testValidStatements(String statement) throws AciException {
      TargetControl.decode(EnumTargetOperator.EQUALITY, statement);
  }

  /**
   * Test invalid targetcontrol statements.
   *
   * @param statement The targetcontrol statement to attempt to decode.
   * @throws Exception  If an unexpected result happens.
   */
  @Test(expectedExceptions= AciException.class, dataProvider="invalidStatements")
  public void testInvalidStatements(String statement)  throws Exception {
    try {
      TargetControl.decode(EnumTargetOperator.EQUALITY,statement);
    } catch (AciException e) {
      throw e;
    } catch (Exception e) {
      System.out.println(
              "Invalid targetcontrol  <" + statement +
              "> threw wrong exception type.");
      throw e;
    }
    throw new RuntimeException(
            "Invalid targetcontrol <" + statement +
            "> did not throw an exception.");
  }

  /**
   * Test access to disallowed control based on a targetattr rule allowing
   * access.
   *
   * @throws Exception If an unexpected result is returned.
   */

  @Test()
  public void testTargetattrSideEffect() throws Exception {
   String pwdLdifs =
        makeAddLDIF("aci", peopleBase, complicated);
    LDIFModify(pwdLdifs, DIR_MGR_DN, PWD);
    String noOpCtrlStr=OID_LDAP_NOOP_OPENLDAP_ASSIGNED + ":true";
    //This should fail beacause this ACI only allows acces to the
    //password policy control.
    pwdModify(level4User, PWD, newPWD, noOpCtrlStr, null,
            LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    deleteAttrFromEntry(peopleBase, "aci");
  }

  /**
   * Test access to extended op controls (no-op and userPasswordPolicy).
   *
   * @throws Exception If an unexpected result is returned.
   */
   @Test()
  public void testExtendOpControls() throws Exception {
   String pwdLdifs =
        makeAddLDIF("aci", peopleBase, extOpControls, extOpAll, ALLOW_ALL);
    LDIFModify(pwdLdifs, DIR_MGR_DN, PWD);
    String noOpCtrlStr=OID_LDAP_NOOP_OPENLDAP_ASSIGNED + ":true";
    //This pwd change should return no-op since the no-op control is
    //specified and it is allowed for authorization dn.
    pwdModify(level3User, PWD, newPWD, noOpCtrlStr, null,
            LDAPResultCode.NO_OPERATION);
    //This pwd change should fail even though the no-op is specified, since
    //since the no-op control is not allowed for this authorization dn.
    pwdModify(superUser, PWD, newPWD, noOpCtrlStr, null,
            LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    deleteAttrFromEntry(peopleBase, "aci");
  }

  /**
   * Test access to bind controls (reportAuthzID and usePasswordPolicy).
   *
   * @throws Exception If an unexpected result is returned.
   */
   @Test()
  public void testBindControl() throws Exception {
    String pwdLdifs =
            makeAddLDIF("aci", peopleBase, pwdControls, ALLOW_ALL);
    LDIFModify(pwdLdifs, DIR_MGR_DN, PWD);
    //The bind operation control access is based on the  bind DN so this
    //should succeed since both pwd policy and authzID control are allowed on
    //ou=people, o=test suffix.
    LDAPSearchParams(level3User, PWD, null, null, null,
            superUser, filter, "aclRights mail description", true,
            false, 0);
    LDAPSearchParams(level3User, PWD, null, null, null,
            superUser, filter, "aclRights mail description", true,
            true, 0);
    //This should fail since the both controls are not allowed for the
    //ou=admins, o=test suffix.
    LDAPSearchParams(superUser, PWD, null, null, null,
            superUser, filter, "aclRights mail description", true,
            true, LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    deleteAttrFromEntry(peopleBase, "aci");
  }

  /**
   * Test target from global ACI level. Two global ACIs are added, one allowing
   * all controls except geteffective rights to the ou=people, o=test
   * suffix. The other ACI only allows the geteffectiverights control on
   * the ou=admin, o=test suffix. Comments in method should explain more
   * what operations and controls are attempted.
   *
   * @throws Exception If an unexpected result happens.
   */
  @Test()
  public void testGlobalTargets() throws Exception {
    String globalControlAcis=
            makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN,
                    controlAdmin, controlPeople);
    LDIFModify(globalControlAcis, DIR_MGR_DN, PWD);
    //Fails because geteffectiverights control not allowed on
    //ou=people, o=test
    LDAPSearchParams(level3User, PWD, null,
            "dn: " + level1User, null,
            level1User, filter, "aclRights mail description",
            false, false, LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    //Ok because geteffectiverights control is allowed on
    //ou=admin, o=test
    LDAPSearchParams(level3User, PWD, null,
            "dn: " + level1User, null,
            superUser, filter, "aclRights mail description",
            false, false, 0);
    String controlStr=OID_LDAP_ASSERTION + ":true:junk";
    //Test add to ou=people, o=test with assertion control,
    //should get protocol error since this control is allowed but value is
    //junk.
    String addEntryLDIF=makeAddEntryLDIF(newPeopleDN, newEntry);
    LDIFAdd(addEntryLDIF, superUser, PWD, controlStr,
            LDAPResultCode.PROTOCOL_ERROR);
    //Test add to ou=admin, o=test with assertion control,
    //should get access denied since this control is not allowed.
    String addEntryLDIF1=makeAddEntryLDIF(newAdminDN, newEntry);
    LDIFAdd(addEntryLDIF1, superUser, PWD, controlStr,
            LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
  }

  /**
   * Test wildcard access. First test "targetcontrol != *"
   * expression. Should all be access denied. Remove that ACI and add
   * "targetcontrol = *" expression. Use assertion control with bad filter,
   * all should return protocol error (modify, add, delete, modifyDN). Search
   * with geteffectiverights should succeed.
   *
   * @throws Exception If an unexpected result happens.
   */
  @Test()
  public void testWildCard() throws Exception {

    String aciDeny=makeAddLDIF("aci", base, controlNotWC);
    String aciRight=makeAddLDIF("aci", base, aclRightsAci);
    LDIFModify(aciDeny, DIR_MGR_DN, PWD);
    LDAPSearchParams(superUser, PWD, null,
            "dn: " + superUser, null,
            base, filter, "aclRights mail description", false, false,
            LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    LDIFModify(aciRight, superUser, PWD, OID_LDAP_READENTRY_PREREAD,
            LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    deleteAttrFromEntry (base, "aci");
    String aciAllow=makeAddLDIF("aci", base, controlWC, ALLOW_ALL);
    LDIFModify(aciAllow, DIR_MGR_DN, PWD);
    //Search with geteffectiverights control.
    LDAPSearchParams(superUser, PWD, null,
            "dn: " + superUser, null,
            base, filter, "aclRights mail description");
    String controlStr=OID_LDAP_ASSERTION + ":true:junk";
    //Attempt modify. Protocol error means we  passed access control
    LDIFModify(aciRight, superUser, PWD, controlStr ,
            LDAPResultCode.PROTOCOL_ERROR);
    //Attempt add, protocol error means we  passed access control
    String addEntryLDIF=makeAddEntryLDIF(newDN, newEntry);
    LDIFAdd(addEntryLDIF, superUser, PWD, controlStr,
            LDAPResultCode.PROTOCOL_ERROR);
    //Attempt delete. Protocol error means we  passed access control.
    LDIFDelete(base, superUser, PWD, controlStr,
            LDAPResultCode.PROTOCOL_ERROR);
    String modDNLDIF=makeModDNLDIF(base, newRDN , "0", newSup);
    //Attempt modify DN. Protocol error means we  passed access control.
    LDIFModify(modDNLDIF, superUser, PWD, controlStr ,
            LDAPResultCode.PROTOCOL_ERROR);
    deleteAttrFromEntry(base, "aci");
  }
}

