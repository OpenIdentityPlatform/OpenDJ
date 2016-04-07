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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ConditionResult.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the ConditionResult class.
 */
@SuppressWarnings("javadoc")
public class ConditionResultTestCase extends SdkTestCase {

    @DataProvider
    public Iterator<Object[]> allPermutationsUpTo4Operands() {
        final ConditionResult[] values = ConditionResult.values();

        final List<Object[]> results = new ArrayList<>();
        results.add(new Object[] { new ConditionResult[0] });
        for (int arrayLength = 1; arrayLength <= 4; arrayLength++) {
            final ConditionResult[] template = new ConditionResult[arrayLength];
            allSubPermutations(values, results, template, 0, arrayLength);
        }
        return results.iterator();
    }

    private void allSubPermutations(ConditionResult[] values, final List<Object[]> results,
            final ConditionResult[] template, int currentIndex, int endIndex) {
        if (currentIndex < endIndex) {
            for (ConditionResult r : values) {
                template[currentIndex] = r;
                allSubPermutations(values, results, template, currentIndex + 1, endIndex);
                if (currentIndex + 1 == endIndex) {
                    results.add(new Object[] {
                          Arrays.copyOf(template, template.length, ConditionResult[].class)
                    });
                }
            }
        }
    }

    /**
     * Tests some basic assumptions of the enumeration.
     */
    @Test
    public void testBasic() {
        assertEquals(values().length, 3);
        assertNotSame(TRUE, FALSE);
        assertNotSame(FALSE, UNDEFINED);
        assertNotSame(UNDEFINED, TRUE);
    }

    @Test
    public void testNot() {
        assertEquals(not(FALSE), TRUE);
        assertEquals(not(TRUE), FALSE);
        assertEquals(not(UNDEFINED), UNDEFINED);
    }

    @Test
    public void testAnd() {
        assertEquals(and(), TRUE);

        assertEquals(and(TRUE), TRUE);
        assertEquals(and(FALSE), FALSE);
        assertEquals(and(UNDEFINED), UNDEFINED);

        assertEquals(and(TRUE, TRUE), TRUE);
        assertEquals(and(FALSE, FALSE), FALSE);
        assertEquals(and(UNDEFINED, UNDEFINED), UNDEFINED);

        assertAndIsCommutative(TRUE, FALSE, FALSE);
        assertAndIsCommutative(TRUE, UNDEFINED, UNDEFINED);
        assertAndIsCommutative(FALSE, UNDEFINED, FALSE);
    }

    private void assertAndIsCommutative(ConditionResult operand1, ConditionResult operand2,
            ConditionResult expectedResult) {
        assertEquals(and(operand1, operand2), expectedResult);
        assertEquals(and(operand2, operand1), expectedResult);
    }

    @Test(dataProvider = "allPermutationsUpTo4Operands")
    public void testVarargsAndIsCommutative(ConditionResult[] operands) {
        if (operands.length == 0) {
            assertEquals(and(operands), and());
            return;
        }

        final EnumSet<ConditionResult> ops = EnumSet.copyOf(Arrays.asList(operands));
        if (ops.contains(FALSE)) {
            assertEquals(and(operands), FALSE);
        } else if (ops.contains(UNDEFINED)) {
            assertEquals(and(operands), UNDEFINED);
        } else {
            assertEquals(and(operands), TRUE);
        }
    }

    @Test
    public void testOr() {
        assertEquals(or(), FALSE);

        assertEquals(or(TRUE), TRUE);
        assertEquals(or(FALSE), FALSE);
        assertEquals(or(UNDEFINED), UNDEFINED);

        assertEquals(or(TRUE, TRUE), TRUE);
        assertEquals(or(FALSE, FALSE), FALSE);
        assertEquals(or(UNDEFINED, UNDEFINED), UNDEFINED);

        assertOrIsCommutative(TRUE, FALSE, TRUE);
        assertOrIsCommutative(TRUE, UNDEFINED, TRUE);
        assertOrIsCommutative(FALSE, UNDEFINED, UNDEFINED);
    }

    private void assertOrIsCommutative(ConditionResult operand1, ConditionResult operand2,
            ConditionResult expectedResult) {
        assertEquals(or(operand1, operand2), expectedResult);
        assertEquals(or(operand2, operand1), expectedResult);
    }

    @Test(dataProvider = "allPermutationsUpTo4Operands")
    public void testVarargsOrIsCommutative(ConditionResult[] operands) {
        if (operands.length == 0) {
            assertEquals(or(operands), or());
            return;
        }

        final EnumSet<ConditionResult> ops = EnumSet.copyOf(Arrays.asList(operands));
        if (ops.contains(TRUE)) {
            assertEquals(or(operands), TRUE);
        } else if (ops.contains(UNDEFINED)) {
            assertEquals(or(operands), UNDEFINED);
        } else {
            assertEquals(or(operands), FALSE);
        }
    }

    @Test
    public void testValueOf() {
        assertEquals(valueOf(true), TRUE);
        assertEquals(valueOf(false), FALSE);
    }

    @Test
    public void testToBoolean() {
        assertEquals(TRUE.toBoolean(), true);
        assertEquals(FALSE.toBoolean(), false);
        assertEquals(UNDEFINED.toBoolean(), false);
    }
}
