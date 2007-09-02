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

import org.testng.annotations.*;
import org.testng.annotations.Test;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.LDAPResultCode;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.config.ConfigConstants.*;

/**
 * Unit test to test the extop ACI keyword.
 */

public class ExtOpTestCase extends AciTestCase {

  private static final String superUser="uid=superuser,ou=admins,o=test";
  private static final String level5User="uid=user.5,ou=People,o=test";
  private static final String level4User="uid=user.4,ou=People,o=test";
  private static final String level3User="uid=user.3,ou=People,o=test";
  private static final String level2User="uid=user.2,ou=People,o=test";
  private static final String level1User="uid=user.1,ou=People,o=test";
  private static final String newPWD="newPWD";
  private static final String peopleBase="ou=People,o=test";
  private static final String adminBase="ou=Admins,o=test";

  //Allow either reportauthzID or passwordpolicy controls. Used in the
  //bind tests.
  private static final
  String pwdControls =
          "(targetcontrol=\"" + OID_AUTHZID_REQUEST + "||" +
          OID_PASSWORD_POLICY_CONTROL + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";


  //Allow only password modify extended op.
  private static final
  String extOp =
          "(extop=\"" + OID_PASSWORD_MODIFY_REQUEST + "\")" +
          "(version 3.0; acl \"extended op\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";


  //Allow all extended ops based on extop = *.
  private static final
  String extOpWC =
          "(extop=\"" + "*" + "\")" +
          "(version 3.0; acl \"extended op WC\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";


  //Dis-allow all extended ops based on extop != *"
  private static final
  String extOpNotWC =
          "(extop!=\"" + "*" + "\")" +
          "(version 3.0; acl \"extended op no wc\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";


 //Allow all attributes to be modified - so the password can be changed.
  private static final
  String ALLOW_ALL = "(targetattr=\"*\")" +
          "(version 3.0;acl \"all access\";" +
          "allow (all) " +
          "userdn=\"ldap:///self\";)";

 //Allow pwd modify to people branch.
  private static final
  String extOpPeople = "(extop=\"" +
          OID_PASSWORD_MODIFY_REQUEST + "\")" +
          "(target=\"ldap:///" + peopleBase + "\")" +
          "(version 3.0; acl \"extended op\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";

  //Dis-allow pwd modify to admin branch.
  private static final
  String extOpAdmin =
          "(extop!=\"" + OID_PASSWORD_MODIFY_REQUEST + "\")" +
          "(target=\"ldap:///" + adminBase + "\")" +
          "(version 3.0; acl \"extended op\";" +
          "allow(read) userdn=\"ldap:///" + "anyone" + "\";)";

  //Test for side effect -- targetattr rule gives access to denied extended
  //op.
  private static final
  String complicated =
          "(extop = \"1.2.3.4\")" +
          "(targetattr != \"userpassword\")" +
          "(version 3.0; acl \"extended op\";" +
          "allow(all) userdn=\"ldap:///" + "anyone" + "\";)";

  @BeforeClass
  public void setupClass() throws Exception {
    TestCaseUtils.startServer();
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    addEntries("o=test");
  }

   @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception {
       String aciLdif=makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN,
               G_READ_ACI, G_SELF_MOD, G_SCHEMA, G_DSE, G_USER_OPS, G_CONTROL,
               E_EXTEND_OP);
       LDIFModify(aciLdif, DIR_MGR_DN, PWD);
   }

  @BeforeMethod
  public void clearBackend() throws Exception {
    deleteAttrFromEntry(peopleBase, "aci");
    deleteAttrFromEntry(adminBase, "aci");
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
  }

  /**
   * Test access to extended op using wildcard.
   *
   * @throws Exception If an unexpected result is returned.
   */
  @Test()
  public void testExtendOpPwdWC() throws Exception {
   String pwdLdifs =
        makeAddLDIF("aci", peopleBase, pwdControls, extOpWC, ALLOW_ALL);
    LDIFModify(pwdLdifs, DIR_MGR_DN, PWD);
    String pwdLdifs1 =
        makeAddLDIF("aci", adminBase, pwdControls, ALLOW_ALL);
    LDIFModify(pwdLdifs1, DIR_MGR_DN, PWD);
    //Pass the people branch has access to all extended op using wild-card.
    pwdModify(level1User, PWD, newPWD, null, null, 0);
    //Fail the admin branch has no access to the extended op.
    pwdModify(superUser, PWD, newPWD, null, null,
              LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    deleteAttrFromEntry(peopleBase, "aci");
    deleteAttrFromEntry(adminBase, "aci");
  }

  /**
    * Test denied access to extended operation based on a extop rule
    * deny all using a wild-card.
    *
    * @throws Exception If an unexpected result is returned.
    */
    @Test()
   public void testExtendOpPwdNotWC() throws Exception {
    String pwdLdifs =
         makeAddLDIF("aci", peopleBase, pwdControls, extOpNotWC, ALLOW_ALL);
     LDIFModify(pwdLdifs, DIR_MGR_DN, PWD);
     pwdModify(level5User, PWD, newPWD, null, null,
             LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
     deleteAttrFromEntry(peopleBase, "aci");
   }


  /**
   * Test access to extended op using one ACI to allow access to the
   * extended op and another ACI to allow the pwd change..
   *
   * @throws Exception If an unexpected result is returned.
   */
  @Test()
  public void testExtendOpPwd() throws Exception {
   String pwdLdifs =
        makeAddLDIF("aci", peopleBase, pwdControls, extOp, ALLOW_ALL);
    LDIFModify(pwdLdifs, DIR_MGR_DN, PWD);
    pwdModify(level3User, PWD, newPWD, null, null, 0);
    deleteAttrFromEntry(peopleBase, "aci");
  }

   /**
   * Test access to disallowed extended op based on a targetattr rule allowing
   * access.
   *
   * @throws Exception If an unexpected result is returned.
   */

  @Test()
  public void testTargetattrSideEffect() throws Exception {
   String pwdLdifs =
        makeAddLDIF("aci", peopleBase, complicated);
    LDIFModify(pwdLdifs, DIR_MGR_DN, PWD);
    //Fail because pwd not an allowed extended operation.
    pwdModify(level4User, PWD, newPWD, null, null,
             LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    deleteAttrFromEntry(peopleBase, "aci");
  }

  /**
   * Test access to pwd changes using global ACIs with target statements giving
   * access to different parts of the DIT.
   *
   * @throws Exception If an unexpected result is returned.
   */
  @Test
  public void testGlobalTargets() throws Exception {
    String globalControlAcis=
            makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN,
                    extOpAdmin, extOpPeople);
    LDIFModify(globalControlAcis, DIR_MGR_DN, PWD);
    String pwdLdifs =
         makeAddLDIF("aci", peopleBase, pwdControls, ALLOW_ALL);
    LDIFModify(pwdLdifs, DIR_MGR_DN, PWD);
    String pwdLdifs1 =
        makeAddLDIF("aci", adminBase, pwdControls, ALLOW_ALL);
    LDIFModify(pwdLdifs1, DIR_MGR_DN, PWD);
    //Succeed because ACI gives access to people branch.
    pwdModify(level2User, PWD, newPWD, null, null, 0);
    //Fail because ACI doesn't give access to admin branch.
    pwdModify(superUser, PWD, newPWD, null, null,
                LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    deleteAttrFromEntry(peopleBase, "aci");
    deleteAttrFromEntry(adminBase, "aci");
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
  }

}
