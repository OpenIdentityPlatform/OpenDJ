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
 * Copyright 2024 3A Systems, LLC.
 */
package org.opends.server.extensions;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.server.config.meta.VirtualAttributeCfgDefn;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.VirtualAttributeProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Entry;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.VirtualAttributeRule;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.forgerock.opendj.ldap.schema.CoreSchema.getGoverningStructureRuleAttributeType;
import static org.testng.Assert.*;

/**
 * A set of test cases for the governing structure rule virtual attribute
 * provider.
 */
public class Issue387TestCase
       extends ExtensionsTestCase
{

  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.clearBackend("userRoot", "dc=example,dc=com");

    int resultCode = TestCaseUtils.applyModifications(true,
    "dn: cn=schema",
    "changetype: modify",
    "add: nameForms",
    "nameForms: ( 1.3.6.1.4.1.56521.999.8.1.7 NAME 'commonNameForm' DESC 'Name Form for a commonName orgRole structure' OC organizationalRole MUST cn )",
    "-",
    "add: ditStructureRules",
    "dITStructureRules: ( 150 NAME 'commonNameStructureRule'  FORM commonNameForm )",
    "dITStructureRules: ( 151 NAME 'commonNameSubStructureRule' FORM commonNameForm SUP 150 )"
    );
    assertEquals(resultCode, 0);
  }

    @Test
    public void test()
            throws Exception
    {
        GoverningStructureRuleVirtualAttributeProvider provider =
                new GoverningStructureRuleVirtualAttributeProvider();

        Entry entry = TestCaseUtils.addEntry(
                "dn: cn=first,dc=example,dc=com",
                "objectClass: top",
                "objectClass: organizationalRole"
                );

        entry.processVirtualAttributes();
        assertTrue(provider.hasValue(entry, getRule(provider), ByteString.valueOfUtf8("150")));


        entry = DirectoryServer.getEntry(DN.valueOf("dc=example,dc=com"));
        assertNotNull(entry);
        assertFalse(entry.hasAttribute(getGoverningStructureRuleAttributeType()));

        entry = TestCaseUtils.addEntry(
                "dn: cn=second,cn=first,dc=example,dc=com",
                "objectClass: top",
                "objectClass: organizationalRole"
        );
        entry.processVirtualAttributes();
        assertTrue(provider.hasValue(entry, getRule(provider), ByteString.valueOfUtf8("150")));
    }

    private VirtualAttributeRule getRule(VirtualAttributeProvider<?> provider)
    {
        return new VirtualAttributeRule(getGoverningStructureRuleAttributeType(), provider,
                Collections.<DN>emptySet(), SearchScope.WHOLE_SUBTREE,
                Collections.<DN>emptySet(),
                Collections.<SearchFilter>emptySet(),
                VirtualAttributeCfgDefn.ConflictBehavior.VIRTUAL_OVERRIDES_REAL);
    }
}
