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
 * Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.util;

import static java.util.Locale.*;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.LinkedHashSet;

import org.testng.annotations.Test;

/** Tests for the {@link SmallSet} class. */
@SuppressWarnings("javadoc")
public class SmallSetTest extends UtilTestCase {
    // @Checkstyle:off
    private final CaseInsentiveString _a = c("a");
    private final CaseInsentiveString _A = c("A");
    private final CaseInsentiveString _b = c("b");
    private final CaseInsentiveString _c = c("c");
    private final CaseInsentiveString _d = c("d");
    // @Checkstyle:on

    @Test
    public void testFirstElement() throws Exception {
        SmallSet<String> set = new SmallSet<>();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.contains("d")).isFalse();
        assertThat(set.remove("d")).isFalse();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.add("a")).isTrue();
        assertThat(set).hasSize(1)
                       .containsExactly("a");
        assertThat(set.contains("a")).isTrue();
        assertThat(set.contains("d")).isFalse();
        assertThat(set.add("a")).isFalse();
        assertThat(set).hasSize(1)
                       .containsExactly("a");
        assertThat(set.remove("c")).isFalse();
        assertThat(set).hasSize(1)
                       .containsExactly("a");
        assertThat(set.remove("a")).isTrue();
        assertThat(set).hasSize(0)
                       .isEmpty();
    }

    @Test
    public void testElements() throws Exception {
        SmallSet<String> set = new SmallSet<>();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.add("a")).isTrue();
        assertThat(set).hasSize(1)
                       .containsExactly("a");
        assertThat(set.add("a")).isFalse();
        assertThat(set).hasSize(1)
                       .containsExactly("a");
        assertThat(set.add("b")).isTrue();
        assertThat(set).hasSize(2)
                       .containsExactly("a", "b");
        assertThat(set.add("c")).isTrue();
        assertThat(set).hasSize(3)
                       .containsExactly("a", "b", "c");
        assertThat(set.contains("a")).isTrue();
        assertThat(set.contains("d")).isFalse();

        assertThat(set.remove("d")).isFalse();
        assertThat(set).hasSize(3)
                       .containsExactly("a", "b", "c");
        assertThat(set.remove("b")).isTrue();
        assertThat(set).hasSize(2)
                       .containsExactly("a", "c");
        assertThat(set.remove("a")).isTrue();
        assertThat(set).hasSize(1)
                       .containsExactly("c");
        assertThat(set.remove("c")).isTrue();

        assertThat(set).hasSize(0)
                       .isEmpty();
    }

    @Test
    public void testFirstElementWithNormalizedValues() throws Exception {
        SmallSet<CaseInsentiveString> set = new SmallSet<>();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.get(_d)).isNull();
        assertThat(set.remove(_c)).isFalse();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.add(_a)).isTrue();
        assertThat(set).hasSize(1)
                       .containsExactly(_a);
        assertThat(set.get(_a)).isSameAs(_a);
        assertThat(set.get(_d)).isNull();
        assertThat(set.add(_A)).isFalse();
        assertThat(set.get(_a)).isSameAs(_a);
        assertThat(set).hasSize(1)
                       .containsExactly(_a);
        set.addOrReplace(_A);
        assertThat(set).hasSize(1)
                       .containsExactly(_a);
        set.addOrReplace(_A);
        assertThat(set).hasSize(1)
                       .containsExactly(_A);
        assertThat(set.remove(_c)).isFalse();
        assertThat(set).hasSize(1)
                       .containsExactly(_a);
        assertThat(set.remove(_a)).isTrue();
        assertThat(set).hasSize(0)
                       .isEmpty();
    }

    @Test
    public void testElementsWithNormalizedValues() throws Exception {
        SmallSet<CaseInsentiveString> set = new SmallSet<>();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.add(c("a"))).isTrue();
        assertThat(set).hasSize(1)
                       .containsExactly(c("a"));
        assertThat(set.add(c("a"))).isFalse();
        assertThat(set).hasSize(1)
                       .containsExactly(c("a"));
        assertThat(set.add(c("b"))).isTrue();
        assertThat(set).hasSize(2)
                       .containsExactly(c("a"), c("b"));
        assertThat(set.add(c("c"))).isTrue();
        assertThat(set).hasSize(3)
                       .containsExactly(c("a"), c("b"), c("c"));

        assertThat(set.remove(c("d"))).isFalse();
        assertThat(set).hasSize(3)
                       .containsExactly(c("a"), c("b"), c("c"));
        assertThat(set.remove(c("b"))).isTrue();
        assertThat(set).hasSize(2)
                       .containsExactly(c("a"), c("c"));
        assertThat(set.remove(c("a"))).isTrue();
        assertThat(set).hasSize(1)
                       .containsExactly(c("c"));
        assertThat(set.remove(c("c"))).isTrue();

        assertThat(set).hasSize(0)
                       .isEmpty();
    }

    @Test
    public void testElementsWithNormalizedValuesSpecificMethods() throws Exception {
        final SmallSet<CaseInsentiveString> set = new SmallSet<>();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.addAll(newLinkedHashSet(_a, _b, _c))).isTrue();
        assertThat(set).hasSize(3)
                       .containsExactly(_a, _b, _c);

        assertThat(set.add(_a)).isFalse();
        assertThat(set.get(_a)).isSameAs(_a);

        assertThat(set.add(_A)).isFalse();
        assertThat(set.get(_a)).isSameAs(_a);

        assertThat(set.get(_d)).isNull();

        set.addOrReplace(_a);
        assertThat(set).hasSize(3)
                       .containsExactly(_b, _c, _a);
        assertThat(set.get(_a)).isSameAs(_a);

        set.addOrReplace(_A);
        assertThat(set).hasSize(3)
                       .containsExactly(_b, _c, _A);
        assertThat(set.get(_a)).isSameAs(_A);
    }

    @Test
    public void testAddAllOnInitiallyEmptySet() {
        SmallSet<String> set = new SmallSet<>();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.addAll(SmallSetTest.<String> newLinkedHashSet())).isFalse();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.addAll(newLinkedHashSet("a"))).isTrue();
        assertThat(set).hasSize(1)
                       .containsExactly("a");
        set.clear();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.addAll(newLinkedHashSet("a", "b", "c"))).isTrue();
        assertThat(set).hasSize(3)
                       .containsExactly("a", "b", "c");
    }

    @Test
    public void testAddAllOnFirstElementAndAllElements() {
        SmallSet<String> set = new SmallSet<>();
        assertThat(set).hasSize(0)
                       .isEmpty();
        assertThat(set.add("a")).isTrue();
        assertThat(set).hasSize(1)
                       .containsExactly("a");
        assertThat(set.addAll(newLinkedHashSet("a", "b", "c"))).isTrue();
        assertThat(set).hasSize(3)
                       .containsExactly("a", "b", "c");
        assertThat(set.addAll(newLinkedHashSet("a", "b", "c"))).isFalse();
        assertThat(set).hasSize(3)
                       .containsExactly("a", "b", "c");
    }

    @SuppressWarnings("unchecked")
    private static <E> LinkedHashSet<E> newLinkedHashSet(E... elements) {
        return new LinkedHashSet<>(Arrays.asList(elements));
    }

    private static CaseInsentiveString c(String s) {
        return new CaseInsentiveString(s);
    }

    private static final class CaseInsentiveString {
        private String s;

        public CaseInsentiveString(String s) {
            this.s = s;
        }

        private String lowerCase() {
            return s.toLowerCase(ROOT);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof CaseInsentiveString) {
                return lowerCase().equals(((CaseInsentiveString) obj).lowerCase());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return lowerCase().hashCode();
        }

        @Override
        public String toString() {
            return s;
        }
    }
}
