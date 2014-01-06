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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.client.ldap;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldif.LDIF.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.AttributeParser;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;

/**
 * A mock LDAP connection which fakes up search results based on some LDIF
 * content. Implementations should override the modify operations in order to
 * get provide the correct fake behavior.
 */
public class MockLDAPConnection extends LDAPConnection {

    /**
     * A mock entry.
     */
    private static final class MockEntry {

        private final Entry entry;

        private final List<MockEntry> children;

        private final DN dn;

        public MockEntry(DN dn, Entry entry) {
            this.dn = dn;
            this.entry = entry;
            this.children = new LinkedList<MockEntry>();
        }

        public Entry getEntry() {
            return entry;
        }

        public List<MockEntry> getChildren() {
            return children;
        }

        public DN getDN() {
            return dn;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("dn:");
            builder.append(dn);
            builder.append(", attributes:");
            builder.append(entry.getAllAttributes());
            return builder.toString();
        }
    }

    /** All the entries. */
    private final Map<DN, MockEntry> entries;

    /** The single root entry. */
    private final MockEntry rootEntry;

    /**
     * Create a mock connection.
     */
    public MockLDAPConnection() {
        this.rootEntry = new MockEntry(DN.rootDN(), new LinkedHashMapEntry(DN.rootDN()));
        this.entries = new HashMap<DN, MockEntry>();
        this.entries.put(DN.rootDN(), this.rootEntry);
    }

    /**
     * {@inheritDoc}
     */
    public void createEntry(Entry entry) throws ErrorResultException {
        throw new UnsupportedOperationException("createEntry");
    }

    /**
     * {@inheritDoc}
     */
    public void deleteSubtree(DN dn) throws ErrorResultException {
        throw new UnsupportedOperationException("deleteSubtree");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean entryExists(DN dn) throws ErrorResultException {
        return getEntry(dn) != null;
    }

    /**
     * Imports the provided LDIF into this mock connection.
     *
     * @param lines
     *            The LDIF.
     */
    public final void importLDIF(String... lines) {
        try {
            for (Entry entry : makeEntries(lines)) {
                addEntry(entry);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<DN> listEntries(DN dn, String filter) throws ErrorResultException {
        MockEntry entry = getEntry(dn);

        if (entry == null) {
            throw ErrorResultException.newErrorResult(ResultCode.NO_SUCH_OBJECT, "Could not find entry: " + dn);
        } else {
            List<DN> names = new LinkedList<DN>();
            for (MockEntry child : entry.children) {
                names.add(DN.valueOf(child.getDN().toString()));
            }
            return names;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void modifyEntry(ModifyRequest request) throws ErrorResultException {
        throw new UnsupportedOperationException("modifyEntry");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SearchResultEntry readEntry(DN dn, Collection<String> attrIds) throws ErrorResultException {
        final MockEntry entry = getEntry(dn);
        return new SearchResultEntry() {

            public AttributeParser parseAttribute(String attributeDescription) {
                throw new RuntimeException("not implemented");
            }

            public AttributeParser parseAttribute(AttributeDescription attributeDescription) {
                throw new RuntimeException("not implemented");
            }

            public boolean containsControl(String oid) {
                return false;
            }

            public SearchResultEntry setName(String dn) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public SearchResultEntry setName(DN dn) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public SearchResultEntry replaceAttribute(String attributeDescription, Object... values) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public boolean replaceAttribute(Attribute attribute) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public SearchResultEntry removeAttribute(String attributeDescription, Object... values) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public boolean removeAttribute(AttributeDescription attributeDescription) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public boolean removeAttribute(Attribute attribute, Collection<? super ByteString> missingValues) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public DN getName() {
                return entry.getDN();
            }

            @Override
            public List<Control> getControls() {
                throw new RuntimeException("not implemented");
            }

            @Override
            public <C extends Control> C getControl(ControlDecoder<C> decoder, DecodeOptions options)
                    throws DecodeException {
                throw new RuntimeException("not implemented");
            }

            @Override
            public int getAttributeCount() {
                return entry.getEntry().getAttributeCount();
            }

            @Override
            public Attribute getAttribute(String attributeDescription) {
                return entry.getEntry().getAttribute(attributeDescription);
            }

            @Override
            public Attribute getAttribute(AttributeDescription attributeDescription) {
                return entry.getEntry().getAttribute(attributeDescription);
            }

            @Override
            public Iterable<Attribute> getAllAttributes(String attributeDescription) {
                return entry.getEntry().getAllAttributes(attributeDescription);
            }

            @Override
            public Iterable<Attribute> getAllAttributes(AttributeDescription attributeDescription) {
                return entry.getEntry().getAllAttributes(attributeDescription);
            }

            @Override
            public Iterable<Attribute> getAllAttributes() {
                return entry.getEntry().getAllAttributes();
            }

            @Override
            public boolean containsAttribute(String attributeDescription, Object... values) {
                return entry.getEntry().containsAttribute(attributeDescription, values);
            }

            @Override
            public boolean containsAttribute(Attribute attribute, Collection<? super ByteString> missingValues) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public SearchResultEntry clearAttributes() {
                throw new RuntimeException("not implemented");
            }

            @Override
            public SearchResultEntry addControl(Control control) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public SearchResultEntry addAttribute(String attributeDescription, Object... values) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public boolean addAttribute(Attribute attribute, Collection<? super ByteString> duplicateValues) {
                throw new RuntimeException("not implemented");
            }

            @Override
            public boolean addAttribute(Attribute attribute) {
                throw new RuntimeException("not implemented");
            }
        };
    }

    /**
     * Asserts whether the provided attribute contains exactly the set of values
     * contained in the provided collection.
     *
     * @param attr
     *            The attribute.
     * @param values
     *            The expected values.
     * @throws ErrorResultException
     *             If an unexpected problem occurred.
     */
    protected final void assertAttributeEquals(Attribute attr, Collection<String> values) throws ErrorResultException {
        List<String> actualValues = new LinkedList<String>();
        for (ByteString actualValue : attr) {
            actualValues.add(actualValue.toString());
        }

        assertThat(actualValues).hasSize(values.size());
        assertThat(actualValues).containsOnly(values.toArray());
    }

    /**
     * Create a new mock entry.
     *
     * @param entry
     *            The entry to be added.
     */
    private void addEntry(Entry entry) {
        MockEntry parent = rootEntry;
        DN entryDN = entry.getName();

        // Create required glue entries.
        for (int i = 0; i < entryDN.size() - 1; i++) {
            RDN rdn = entryDN.parent(entryDN.size() - i - 1).rdn();
            DN dn = parent.getDN().child(rdn);

            if (!entries.containsKey(dn)) {
                MockEntry glue = new MockEntry(dn, new LinkedHashMapEntry(dn));
                parent.getChildren().add(glue);
                entries.put(dn, glue);
            }

            parent = entries.get(dn);
        }

        // We now have the parent entry - so construct the new entry.
        MockEntry child = new MockEntry(entryDN, LinkedHashMapEntry.deepCopyOfEntry(entry));
        parent.getChildren().add(child);
        entries.put(entryDN, child);
    }

    /**
     * Gets the named entry.
     *
     * @param dn
     *            The name of the entry.
     * @return Returns the mock entry or <code>null</code> if it does not exist.
     */
    private MockEntry getEntry(DN dn) {
        DN name = DN.valueOf(dn.toString());
        return entries.get(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unbind() {
        // nothing to do
    }

}
