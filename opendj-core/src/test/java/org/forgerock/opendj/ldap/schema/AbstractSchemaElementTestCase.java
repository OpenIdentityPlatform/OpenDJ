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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.DecodeException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Abstract schema element tests.
 */
@SuppressWarnings("javadoc")
public abstract class AbstractSchemaElementTestCase extends AbstractSchemaTestCase {
    protected static final Map<String, List<String>> EMPTY_PROPS = Collections.emptyMap();
    protected static final List<String> EMPTY_NAMES = Collections.emptyList();

    @DataProvider(name = "equalsTestData")
    public abstract Object[][] createEqualsTestData() throws SchemaException, DecodeException;

    /**
     * Check that the equals operator works as expected.
     *
     * @param e1
     *            The first element
     * @param e2
     *            The second element
     * @param result
     *            The expected result.
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(dataProvider = "equalsTestData")
    public final void testEquals(final SchemaElement e1, final SchemaElement e2,
            final boolean result) throws Exception {
        Assert.assertEquals(e1.equals(e2), result);
        Assert.assertEquals(e2.equals(e1), result);
    }

    /**
     * Check that the {@link SchemaElement#getDescription()} method returns a
     * description.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testGetDescription() throws Exception {
        final SchemaElement e = getElement("hello", EMPTY_PROPS);
        Assert.assertEquals(e.getDescription(), "hello");
    }

    /**
     * Check that the {@link SchemaElement#getDescription()} method returns
     * <code>null</code> when there is no description.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testGetDescriptionDefault() throws Exception {
        final SchemaElement e = getElement("", EMPTY_PROPS);
        Assert.assertEquals(e.getDescription(), "");
    }

    /**
     * Check that the {@link SchemaElement#getExtraProperties()} method
     * returns values.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testGetExtraProperty() throws Exception {
        final List<String> values = new ArrayList<>();
        values.add("one");
        values.add("two");
        final Map<String, List<String>> props = Collections.singletonMap("test", values);
        final SchemaElement e = getElement("", props);

        int i = 0;
        for (final String value : e.getExtraProperties().get("test")) {
            Assert.assertEquals(value, values.get(i));
            i++;
        }
    }

    /**
     * Check that the {@link SchemaElement#getExtraProperties()} method
     * returns <code>null</code> when there is no property.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test
    public final void testGetExtraPropertyDefault() throws Exception {
        final SchemaElement e = getElement("", EMPTY_PROPS);
        Assert.assertNull(e.getExtraProperties().get("test"));
    }

    /**
     * Check that the hasCode method operator works as expected.
     *
     * @param e1
     *            The first element
     * @param e2
     *            The second element
     * @param result
     *            The expected result.
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(dataProvider = "equalsTestData")
    public final void testHashCode(final SchemaElement e1, final SchemaElement e2,
            final boolean result) throws Exception {
        Assert.assertEquals(e1.hashCode() == e2.hashCode(), result);
    }

    protected abstract SchemaElement getElement(String description,
            Map<String, List<String>> extraProperties) throws SchemaException;
}
