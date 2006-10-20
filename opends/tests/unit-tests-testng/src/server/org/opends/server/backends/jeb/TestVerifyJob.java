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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.DN;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TestVerifyJob extends JebTestCase
{
    private static String cfgDN="ds-cfg-backend-id=userRoot,cn=Backends,cn=config";
    /* number of entries to test with */
    private static int numEntries=10;
    private static String vBranch = "ou=verify";
    private static String suffix="dc=example,dc=com";
    private static String beID="userRoot";
    
    @DataProvider(name = "indexes")
    public Object[][] indexes() {
        return new Object[][] {
            { "telephoneNumber"},
            { "id2subtree"},
            {"id2children"},
            {"dn2id"}
        };
    }
    
    private static String[] template = new String[] {
        "define suffix="+suffix,
        "define maildomain=example.com",
        "define numusers=" + numEntries,
        "",
        "branch: [suffix]",
        "",
       "branch: " + vBranch +",[suffix]",
        "subordinateTemplate: person:[numusers]",
        "",
        "template: person",
        "rdnAttr: uid",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "givenName: <first>",
        "sn: <last>",
        "cn: {givenName} {sn}",
        "initials: {givenName:1}<random:chars:" +
             "ABCDEFGHIJKLMNOPQRSTUVWXYZ:1>{sn:1}",
        "employeeNumber: <sequential:0>",
        "uid: user.{employeeNumber}",
        "mail: {uid}@[maildomain]",
        "userPassword: password",
        "telephoneNumber: <random:telephone>",
        "homePhone: <random:telephone>",
        "pager: <random:telephone>",
        "mobile: <random:telephone>",
        "street: <random:numeric:5> <file:streets> Street",
        "l: <file:cities>",
        "st: <file:states>",
        "postalCode: <random:numeric:5>",
        "postalAddress: {cn}${street}${l}, {st}  {postalCode}",
        "description: This is the description for {cn}.",
        ""};
    
    @BeforeClass
    public void setup() throws Exception {
      TestCaseUtils.startServer();
      createLoadEntries(template, numEntries);
    }
    
    @AfterClass
    public void cleanUp() throws Exception {
        removeLoadedEntries();
    }
    
    /**
     * Performs a non-clean verify against a backend using the
     * entries loaded in the setup initializer.
     * 
     * @throws Exception if the verify fails.
     */
    @Test()
    public void testVerifyJob()  throws Exception {
        DN configDN= DN.decode(cfgDN);
        DN[] baseDNs = new DN[] {
                DN.decode(suffix)
        };
        VerifyConfig verifyConfig = new VerifyConfig();
        verifyConfig.setBaseDN(baseDNs[0]);
        ConfigEntry configEntry = DirectoryServer.getConfigEntry(configDN);
        BackendImpl be=(BackendImpl) DirectoryServer.getBackend(beID);
        be.verifyBackend(verifyConfig, configEntry, baseDNs, null);
    }
    
    
    /**
     * Performs a clean verify against a backend using the index
     * name passed into it from the indexes array above.
     * @param index Index name to verify.
     * @throws Exception if the backend index cannot be verified.
     */
    @Test(dataProvider = "indexes")
    public void testCleanVerifyJob(String index)  throws Exception {
        DN configDN= DN.decode(cfgDN);
        DN[] baseDNs = new DN[] {
                DN.decode(suffix)
        };
        VerifyConfig verifyConfig = new VerifyConfig();
        verifyConfig.setBaseDN(baseDNs[0]);
        verifyConfig.addCleanIndex(index);
        ConfigEntry configEntry = DirectoryServer.getConfigEntry(configDN);
        BackendImpl be=(BackendImpl) DirectoryServer.getBackend(beID);
        be.verifyBackend(verifyConfig, configEntry, baseDNs, null);
    }
}
