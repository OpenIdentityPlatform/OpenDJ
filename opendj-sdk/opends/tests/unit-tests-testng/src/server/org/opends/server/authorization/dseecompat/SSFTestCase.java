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

/**
 * Unit test to test the ssf ACI bind rule keyword.
 */

package org.opends.server.authorization.dseecompat;

import java.util.Hashtable;

import javax.naming.Context;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.testng.annotations.*;
import static org.opends.server.config.ConfigConstants.*;

public class SSFTestCase extends AciTestCase {

    private static final String newUser="uid=new.user,ou=People,o=test";
    private static final String descriptionStr = "description of user.1";
    private static final String factory = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String pwdPolicy = "Aci Temp Policy";
    private static final String pwdPolicyDN =
                       "cn=" + pwdPolicy + ",cn=Password Policies,cn=config";

    private static final String[] newEntry = new String[] {
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: new.user",
        "givenName: New",
        "sn: User",
        "cn: New User",
        "mail: new.user@test.com",
        "ds-pwp-password-policy-dn:" + pwdPolicyDN
      };

    private static final
    String integrityACI = "(targetattr=\"" + "*" + "\")" +
            "(version 3.0; acl \"integrity aci\";" +
            "allow(all) (userdn=\"ldap:///self\" and ssf = \"1\");)";

    private static final
    String greaterIntegrityACI = "(targetattr=\"" + "*" + "\")" +
            "(version 3.0; acl \"greater integrity aci\";" +
            "allow(all) (userdn=\"ldap:///self\" and ssf > \"1\");)";


    private static final
    String medStrengthACI = "(targetattr=\"" + "*" + "\")" +
            "(version 3.0; acl \"56 bit key aci\";" +
            "allow(all) (userdn=\"ldap:///self\" and ssf = \"56\");)";


    private static final
    String hiStrengthACI = "(targetattr=\"" + "*" + "\")" +
            "(version 3.0; acl \"128 bit key aci\";" +
            "allow(all) (userdn=\"ldap:///self\" and ssf = \"128\");)";


    private static final
    String hiPlusStrengthACI = "(targetattr=\"" + "*" + "\")" +
            "(version 3.0; acl \"greater 128 bit aci\";" +
            "allow(all) (userdn=\"ldap:///self\" and ssf > \"128\");)";


    @BeforeClass
    public void setupClass() throws Exception {
      TestCaseUtils.startServer();
      TestCaseUtils.dsconfig(
              "create-password-policy",
              "--policy-name", pwdPolicy,
              "--set", "password-attribute:userPassword",
              "--set", "default-password-storage-scheme: Clear"
              );
      TestCaseUtils.dsconfig(
              "set-sasl-mechanism-handler-prop",
              "--handler-name", "DIGEST-MD5",
              "--set", "server-fqdn:localhost");
      deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
      String aciLdif=makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN,
                    G_SCHEMA, G_DSE, G_USER_OPS, G_CONTROL, E_EXTEND_OP);
      LDIFAdminModify(aciLdif, DIR_MGR_DN, PWD);
      addEntries("o=test");
      String newUserLDIF=makeAddEntryLDIF(newUser, newEntry);
      LDIFAdd(newUserLDIF, DIR_MGR_DN, PWD, null, LDAPResultCode.SUCCESS);
      String pwdILDIF =
          makeAddLDIF("userpassword", newUser, "password");
      LDIFModify(pwdILDIF, DIR_MGR_DN, PWD);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
         String aciLdif=makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN,
                G_READ_ACI, G_SELF_MOD, G_SCHEMA, G_DSE, G_USER_OPS, G_CONTROL,
                E_EXTEND_OP);
         LDIFAdminModify(aciLdif, DIR_MGR_DN, PWD);
         TestCaseUtils.dsconfig(
                 "delete-password-policy",
                 "--policy-name", pwdPolicy
                 );
         TestCaseUtils.dsconfig(
                 "set-sasl-mechanism-handler-prop",
                 "--handler-name", "DIGEST-MD5",
                 "--reset", "server-fqdn",
                 "--reset", "quality-of-protection");

     }

    //Valid ssf statements. Not the complete ACI.
    @DataProvider(name = "validStatements")
    public Object[][] valids() {
      return new Object[][] {
              {"1"},
              {"40"},
              {"56"},
              {"128"},
              {"256"},
              {"129"},
      };
    }

     //Invalid ssf statements. Not the complete ACI.
    @DataProvider(name = "invalidStatements")
    public Object[][] invalids() {
      return new Object[][] {
              {"-1"},
              {"0"},
              {"not valid"},
              {"1025"},
              {"10000"},
      };
    }

    private EnumBindRuleType bindRuleType = EnumBindRuleType.EQUAL_BINDRULE_TYPE;

    /**
     * Test valid ssf statements.
     *
     * @param statement The ssf statement to attempt to decode.
     * @throws AciException  If an unexpected result happens.
     */
    @Test(dataProvider = "validStatements")
    public void testValidStatements(String statement) throws AciException {
        SSF.decode(statement, bindRuleType);
    }

    /**
     * Test invalid ssf statements.
     *
     * @param statement The ssf statement to attempt to decode.
     * @throws Exception  If an unexpected result happens.
     */
    @Test(expectedExceptions= AciException.class,
          dataProvider="invalidStatements")
    public void testInvalidStatements(String statement)  throws Exception {
      try {
        SSF.decode(statement, bindRuleType);
      } catch (AciException e) {
        throw e;
      } catch (Exception e) {
        System.out.println(
                "Invalid ssf  <" + statement +
                "> threw wrong exception type.");
        throw e;
      }
      throw new RuntimeException(
              "Invalid ssf <" + statement +
              "> did not throw an exception.");
    }

    /**
     * Test ssf bind rule using ssf value for integrity.
     *
     * @throws Exception If a test doesn't pass.
     */
    @Test()
    public void testIntegrity() throws Exception {
        //set QOP to integrity.
        TestCaseUtils.dsconfig(
                "set-sasl-mechanism-handler-prop",
                "--handler-name", "DIGEST-MD5",
                "--set", "quality-of-protection:" + "integrity");

        //Configure JNDI props.
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, factory);
        int port = TestCaseUtils.getServerLdapPort();
        String url = "ldap://localhost:" + Integer.valueOf(port);
        env.put(Context.PROVIDER_URL, url);
        env.put(Context.SECURITY_AUTHENTICATION, "DIGEST-MD5");
        String principal = "dn:" + newUser;
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, "password");
        //Select integrity QOP.
        env.put("javax.security.sasl.qop", "auth-int");
        //Add ACI with ssf > 1, should fail.
        String addACILDIF = makeAddLDIF("aci", newUser, greaterIntegrityACI);
        LDIFModify(addACILDIF, DIR_MGR_DN, PWD);
        JNDIModify(env, newUser, "description", descriptionStr,
                  LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
        deleteAttrFromEntry(newUser, "aci");
        //Add ACI with ssf = 1.
         addACILDIF = makeAddLDIF("aci", newUser, integrityACI);
        LDIFModify(addACILDIF, DIR_MGR_DN, PWD);
        //Should succeed.
        JNDIModify(env, newUser, "description", descriptionStr,
                   LDAPResultCode.SUCCESS);
        deleteAttrFromEntry(newUser, "aci");
        deleteAttrFromEntry(newUser, "description");
    }

    /**
     * Test confidentiality settings using DIGEST-MD5.
     * @throws Exception
     */
    @Test()
    public void testConfidentiality() throws Exception {
        //set QOP to integrity.
        TestCaseUtils.dsconfig(
                "set-sasl-mechanism-handler-prop",
                "--handler-name", "DIGEST-MD5",
                "--set", "quality-of-protection:" + "confidentiality");
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, factory);
        int port = TestCaseUtils.getServerLdapPort();
        String url = "ldap://localhost:" + Integer.valueOf(port);
        env.put(Context.PROVIDER_URL, url);
        env.put(Context.SECURITY_AUTHENTICATION, "DIGEST-MD5");
        String principal = "dn:" + newUser;
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, "password");
        //Select integrity QOP.
        env.put("javax.security.sasl.qop", "auth-conf");
        //Add ACI with ssf > 1, should succeed.
        String addACILDIF = makeAddLDIF("aci", newUser, greaterIntegrityACI);
        LDIFModify(addACILDIF, DIR_MGR_DN, PWD);
        JNDIModify(env, newUser, "description", descriptionStr,
                  LDAPResultCode.SUCCESS);
        deleteAttrFromEntry(newUser, "aci");
        deleteAttrFromEntry(newUser, "description");
        //Test medium strength.
        addACILDIF = makeAddLDIF("aci", newUser, medStrengthACI);
        LDIFModify(addACILDIF, DIR_MGR_DN, PWD);
        env.put("javax.security.sasl.strength", "medium");
        JNDIModify(env, newUser, "description", descriptionStr,
                LDAPResultCode.SUCCESS);
        deleteAttrFromEntry(newUser, "aci");
        deleteAttrFromEntry(newUser, "description");
        //Test high strength.
        addACILDIF = makeAddLDIF("aci", newUser, hiStrengthACI);
        LDIFModify(addACILDIF, DIR_MGR_DN, PWD);
        env.put("javax.security.sasl.strength", "high");
        JNDIModify(env, newUser, "description", descriptionStr,
                LDAPResultCode.SUCCESS);
        deleteAttrFromEntry(newUser, "aci");
        deleteAttrFromEntry(newUser, "description");
        //Fail DIGEST-MD5 only goes to 128.
        addACILDIF = makeAddLDIF("aci", newUser, hiPlusStrengthACI);
        LDIFModify(addACILDIF, DIR_MGR_DN, PWD);
        env.put("javax.security.sasl.strength", "high");
        JNDIModify(env, newUser, "description", descriptionStr,
                   LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
        deleteAttrFromEntry(newUser, "aci");
        deleteAttrFromEntry(newUser, "description");
    }
}
