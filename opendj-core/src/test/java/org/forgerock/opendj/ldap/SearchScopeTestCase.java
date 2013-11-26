/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.*;
import static org.testng.Assert.*;

import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class SearchScopeTestCase extends SdkTestCase {

    @Test
    public void valueOfInt() {
        assertNull(SearchScope.valueOf(-1));
        assertNull(SearchScope.valueOf(Integer.MAX_VALUE));
        assertEquals(SearchScope.valueOf(0), SearchScope.BASE_OBJECT);
        assertEquals(SearchScope.valueOf(1), SearchScope.SINGLE_LEVEL);
        assertEquals(SearchScope.valueOf(2), SearchScope.WHOLE_SUBTREE);
        assertEquals(SearchScope.valueOf(3), SearchScope.SUBORDINATES);
    }

    @Test
    public void valueOfString() {
        assertNull(SearchScope.valueOf(null));
        assertEquals(SearchScope.valueOf("base"), SearchScope.BASE_OBJECT);
        assertEquals(SearchScope.valueOf("one"), SearchScope.SINGLE_LEVEL);
        assertEquals(SearchScope.valueOf("sub"), SearchScope.WHOLE_SUBTREE);
        assertEquals(SearchScope.valueOf("subordinates"), SearchScope.SUBORDINATES);
    }

    @Test
    public void values() {
        assertThat(SearchScope.values()).containsExactly(SearchScope.BASE_OBJECT,
            SearchScope.SINGLE_LEVEL, SearchScope.WHOLE_SUBTREE,
            SearchScope.SUBORDINATES);
    }
}
