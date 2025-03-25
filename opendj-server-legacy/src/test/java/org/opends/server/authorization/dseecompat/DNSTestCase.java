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
 * Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import org.opends.server.DirectoryServerTestCase;

public class DNSTestCase extends DirectoryServerTestCase {

    private DNS dns=new DNS(null, null);

    @DataProvider(name = "wildCardMatch")
    public Object[][] wcMatchData() {
        return new Object[][] {
                { "foo.example.com", "*.com" },
                { "foo.example.com", "*.example.com" },
                { "very.long.dns.foo.example.com", "*.example.com" },
                { "foo.example.com", "*" },
        };
    }

    @DataProvider(name = "nonWildCardMatch")
    public Object[][] nonWCMatchData() {
        return new Object[][] {
                { "foo.example.com", "foo.example.com" },
                { "example.com", "example.com" },
                { "com", "com" },
                { "very.long.dns.example.com", "very.long.dns.example.com" },
        };
    }

    @DataProvider(name = "invalidMatch")
    public Object[][] invalidMatchData() {
        return new Object[][] {
                { "foo.example.com", "example.com" },
                { "foo.example.com", "com" },
                { "foo.example.com", "*.foo.com" },
                { "foo.bar.com", "*.foo.bar.com" },
                { "bar.com", "foo.bar.com" },
                { "very.long.dns.example.com", "very.long.dns.test.com" },
        };
    }


    /**
     * Test wild-card match patterns. They all should succeed.
     * @param hostString The string representing a host name.
     * @param patString The pattern to evaluate with.
     */
    @Test(dataProvider = "wildCardMatch")
    public void testWildCardMatch(String hostString, String patString) {
        String[] patArray = patString.split("\\.", -1);
        String[] hostArray = hostString.split("\\.", -1);
        assertTrue(dns.evalHostName(hostArray, patArray));
    }

    /**
     * Test non wild-card match patterns. They all should succeed.
     * @param hostString The string representing a host name.
     * @param patString The pattern to evaluate with.
     */
    @Test(dataProvider = "nonWildCardMatch")
    public void testNonWildCardMatch(String hostString, String patString) {
        String[] patArray = patString.split("\\.", -1);
        String[] hostArray = hostString.split("\\.", -1);
        assertTrue(dns.evalHostName(hostArray, patArray));
    }

    /**
     * Test with various invalid patterns and hostname combinations. They all
     * should fail.
     * @param hostString The string representing a host name.
     * @param patString The pattern to evaluate with.
     */
    @Test(dataProvider = "invalidMatch", singleThreaded = true)
    public void testInvalidMatch(String hostString, String patString) {
        String[] patArray = patString.split("\\.", -1);
        String[] hostArray = hostString.split("\\.", -1);
        assertFalse(dns.evalHostName(hostArray, patArray));
    }
}
