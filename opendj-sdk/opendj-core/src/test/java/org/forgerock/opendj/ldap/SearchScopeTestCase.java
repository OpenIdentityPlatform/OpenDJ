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
 * Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.util.Iterator;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class SearchScopeTestCase extends SdkTestCase {

    @Test
    public void valueOfInt() {
        final SearchScope unkown1 = SearchScope.valueOf(-1);
        assertEquals(unkown1.intValue(), -1);
        assertEquals(unkown1.asEnum(), SearchScope.Enum.UNKNOWN);
        final SearchScope unknownMax = SearchScope.valueOf(Integer.MAX_VALUE);
        assertEquals(unknownMax.intValue(), Integer.MAX_VALUE);
        assertEquals(unknownMax.asEnum(), SearchScope.Enum.UNKNOWN);

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

    @DataProvider
    public Iterator<Object[]> valuesDataProvider() {
        return new DataProviderIterator(SearchScope.values());
    }

    @Test(dataProvider = "valuesDataProvider")
    public void valueOfInt(SearchScope val) throws Exception {
        assertSame(SearchScope.valueOf(val.intValue()), val, val.toString());
    }

    @Test
    public void valueOfIntUnknown() throws Exception {
        int intValue = -1;
        SearchScope unknown = SearchScope.valueOf(intValue);
        assertSame(unknown.intValue(), intValue);
        assertSame(unknown.asEnum(), SearchScope.Enum.UNKNOWN);
    }

    @Test(dataProvider = "valuesDataProvider")
    public void valueOfString(SearchScope val) throws Exception {
        assertSame(SearchScope.valueOf(val.toString()), val);
    }

    @Test
    public void valueOfStringUnknown() throws Exception {
        assertNull(SearchScope.valueOf("unknown"));
    }
}
