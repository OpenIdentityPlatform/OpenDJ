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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.config.dsconfig;

import org.forgerock.testng.ForgeRockTestCase;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test(groups = { "precommit", "config" })
public class BuildVersionTest extends ForgeRockTestCase {

    @Test
    public void testEqualRevisionsCompareEqual() {
        // Two distinct String instances holding the same revision: the revisions must be compared
        // by value, not by reference.
        final String rev = "abcdef";
        final String sameRev = new StringBuilder(rev).toString();
        Assert.assertEquals(new BuildVersion(4, 0, 0, rev).compareTo(new BuildVersion(4, 0, 0, sameRev)), 0);
    }

    @Test
    public void testRevisionsOrderVersionsWhichAreOtherwiseEqual() {
        Assert.assertEquals(new BuildVersion(4, 0, 0, "aaa").compareTo(new BuildVersion(4, 0, 0, "bbb")), -1);
        Assert.assertEquals(new BuildVersion(4, 0, 0, "bbb").compareTo(new BuildVersion(4, 0, 0, "aaa")), 1);
    }

    @Test
    public void testVersionNumbersTakePrecedenceOverRevisions() {
        Assert.assertEquals(new BuildVersion(3, 9, 9, "zzz").compareTo(new BuildVersion(4, 0, 0, "aaa")), -1);
        Assert.assertEquals(new BuildVersion(4, 1, 0, "aaa").compareTo(new BuildVersion(4, 0, 9, "zzz")), 1);
        Assert.assertEquals(new BuildVersion(4, 0, 1, "aaa").compareTo(new BuildVersion(4, 0, 0, "zzz")), 1);
    }
}
