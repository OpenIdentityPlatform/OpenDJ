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
 *      Copyright 2014 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import java.util.EnumSet;
import java.util.Iterator;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ResultCode.Enum.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class ResultCodeTestCase extends SdkTestCase {

    @DataProvider
    public Iterator<Object[]> valuesDataProvider() {
        return new DataProviderIterator(ResultCode.values());
    }

    @Test(dataProvider = "valuesDataProvider")
    public void valueOfInt(ResultCode val) throws Exception {
        assertSame(ResultCode.valueOf(val.intValue()), val);
    }

    @Test
    public void valueOfIntUnknown() throws Exception {
        int intValue;
        ResultCode unknown;

        intValue = -2;
        unknown = ResultCode.valueOf(intValue);
        assertEquals(unknown.intValue(), intValue);
        assertEquals(unknown.asEnum(), ResultCode.Enum.UNKNOWN);

        intValue = Integer.MAX_VALUE;
        unknown = ResultCode.valueOf(intValue);
        assertEquals(unknown.intValue(), intValue);
        assertEquals(unknown.asEnum(), ResultCode.Enum.UNKNOWN);
    }

    @Test(dataProvider = "valuesDataProvider")
    public void isExceptional(ResultCode val) {
        EnumSet<ResultCode.Enum> exceptional = EnumSet.complementOf(EnumSet.of(
            SUCCESS, COMPARE_FALSE, COMPARE_TRUE, SASL_BIND_IN_PROGRESS, NO_OPERATION));
        assertEquals(val.isExceptional(), exceptional.contains(val.asEnum()));
    }

}
