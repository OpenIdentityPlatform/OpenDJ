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
package org.forgerock.opendj.ldap;

import static org.fest.assertions.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

/**
 * Test {@code AttributeFilter}.
 */
@SuppressWarnings("javadoc")
public final class AttributeFilterTestCase extends SdkTestCase {

    @Test
    public void testFilteredViewCanBeIteratedMoreThanOnce() {
        final Iterable<Attribute> attributes = filteredView().getAllAttributes();

        final List<String> firstPass = namesOf(attributes);
        assertThat(firstPass).isNotEmpty();
        assertThat(namesOf(attributes)).isEqualTo(firstPass);
    }

    @Test
    public void testFilteredViewCanBeIteratedAfterToString() {
        final Iterable<Attribute> attributes = filteredView().getAllAttributes();

        final List<String> expected = namesOf(attributes);
        attributes.toString();
        assertThat(namesOf(attributes)).isEqualTo(expected);
    }

    private Entry filteredView() {
        final Entry entry =
                new LinkedHashMapEntry("dn: cn=test", "objectClass: top", "objectClass: person",
                        "cn: test", "sn: user");
        return new AttributeFilter().filteredViewOf(entry);
    }

    private List<String> namesOf(final Iterable<Attribute> attributes) {
        final List<String> names = new ArrayList<>();
        for (final Attribute attribute : attributes) {
            names.add(attribute.getAttributeDescriptionAsString());
        }
        return names;
    }
}
