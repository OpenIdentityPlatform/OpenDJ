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
 * Portions copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.dsconfig;

import org.forgerock.testng.ForgeRockTestCase;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collection;

@Test(groups = { "precommit", "config" })
public class DSConfigParseTest extends ForgeRockTestCase {
    @DataProvider
    public Object[][] escapeSequences() {
        return new Object[][] {
            {"global-aci:\\(targetattr=\\\"userPassword\\|\\|authPassword\\\"\\)"
                    + "\\(version\\ 3.0\\;\\ acl\\ \\\"Self\\ entry\\ read\\'\\\"\\;"
                    + "\\ allow\\ \\(read,search,compare\\)\\ userdn=\\\"ldap:///self\\\"\\;\\)",
                "global-aci:(targetattr=\"userPassword||authPassword\")"
                    + "(version 3.0; acl \"Self entry read'\";"
                    + " allow (read,search,compare) userdn=\"ldap:///self\";)"
            },
            {"cn=\"admin data\"", "cn=admin data"},
            {"\"cn=admin data\"", "cn=admin data"},
            {"cn=\\\"admin", "cn=\"admin"}
        };
    }

    @Test(dataProvider = "escapeSequences")
    public void testEscapeSequenceInCommandArgument(String arg, String value) throws Exception {
        Collection<String> cmdLine = DSConfig.toCommandArgs(arg);
        Assert.assertEquals(cmdLine.iterator().next(), value);
    }
}
