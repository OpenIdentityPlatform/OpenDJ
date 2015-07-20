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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.controls.PermissiveModifyRequestControl;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.ldap.schema.ObjectClassType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaValidationPolicy;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.util.Reject;
import org.forgerock.util.Function;
import org.forgerock.util.promise.NeverThrowsException;

import com.forgerock.opendj.util.Iterables;

import static org.forgerock.opendj.ldap.AttributeDescription.*;
import static org.forgerock.opendj.ldap.LdapException.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;

/**
 * This class contains methods for creating and manipulating entries.
 *
 * @see Entry
 */
public final class Entries {
    /**
     * Options for controlling the behavior of the
     * {@link Entries#diffEntries(Entry, Entry, DiffOptions) diffEntries}
     * method. {@code DiffOptions} specify which attributes are compared, how
     * they are compared, and the type of modifications generated.
     *
     * @see Entries#diffEntries(Entry, Entry, DiffOptions)
     */
    public static final class DiffOptions {
        /**
         * Selects which attributes will be compared. By default all user
         * attributes will be compared.
         */
        private AttributeFilter attributeFilter = USER_ATTRIBUTES_ONLY_FILTER;

        /**
         * When true, attribute values are compared byte for byte, otherwise
         * they are compared using their matching rules.
         */
        private boolean useExactMatching;

        /**
         * When greater than 0, modifications with REPLACE type will be
         * generated for the new attributes containing at least
         * "useReplaceMaxValues" attribute values. Otherwise, modifications with
         * DELETE + ADD types will be generated.
         */
        private int useReplaceMaxValues;

        private DiffOptions() {
            // Nothing to do.
        }

        /**
         * Specifies an attribute filter which will be used to determine which
         * attributes will be compared. By default only user attributes will be
         * compared.
         *
         * @param attributeFilter
         *            The filter which will be used to determine which
         *            attributes will be compared.
         * @return A reference to this set of options.
         */
        public DiffOptions attributes(final AttributeFilter attributeFilter) {
            Reject.ifNull(attributeFilter);
            this.attributeFilter = attributeFilter;
            return this;
        }

        /**
         * Specifies the list of attributes to be compared. By default only user
         * attributes will be compared.
         *
         * @param attributeDescriptions
         *            The names of the attributes to be compared.
         * @return A reference to this set of options.
         */
        public DiffOptions attributes(final String... attributeDescriptions) {
            return attributes(new AttributeFilter(attributeDescriptions));
        }

        /**
         * Requests that attribute values should be compared byte for byte,
         * rather than using their matching rules. This is useful when a client
         * wishes to perform trivial changes to an attribute value which would
         * otherwise be ignored by the matching rule, such as removing extra
         * white space from an attribute, or capitalizing a user's name.
         *
         * @return A reference to this set of options.
         */
        public DiffOptions useExactMatching() {
            this.useExactMatching = true;
            return this;
        }

        /**
         * Requests that all generated changes should use the
         * {@link ModificationType#REPLACE REPLACE} modification type, rather
         * than a combination of {@link ModificationType#DELETE DELETE} and
         * {@link ModificationType#ADD ADD}.
         * <p>
         * Note that the generated changes will not be reversible, nor will they
         * be efficient for attributes containing many values (such as groups).
         * Enabling this option may result in more efficient updates for single
         * valued attributes and reduce the amount of replication meta-data that
         * needs to be maintained..
         *
         * @return A reference to this set of options.
         */
        public DiffOptions alwaysReplaceAttributes() {
            return replaceMaxValues(Integer.MAX_VALUE);
        }

        /**
         * Requests that the generated changes should use the
         * {@link ModificationType#REPLACE REPLACE} modification type when the
         * new attribute contains at most one attribute value. All other changes
         * will use a combination of {@link ModificationType#DELETE DELETE} then
         * {@link ModificationType#ADD ADD}.
         * <p>
         * Specifying this option will usually provide the best overall
         * performance for single and multi-valued attribute updates, but the
         * generated changes will probably not be reversible.
         *
         * @return A reference to this set of options.
         */
        public DiffOptions replaceSingleValuedAttributes() {
            return replaceMaxValues(1);
        }

        /**
         * Requests that the generated changes should use the
         * {@link ModificationType#REPLACE REPLACE} modification type when the
         * new attribute contains {@code maxValues} attribute values or less.
         * All other changes will use a combination of
         * {@link ModificationType#DELETE DELETE} then
         * {@link ModificationType#ADD ADD}.
         *
         * @param maxValues
         *            The maximum number of attribute values a modified
         *            attribute can contain before reversible changes will be
         *            generated.
         * @return A reference to this set of options.
         */
        private DiffOptions replaceMaxValues(final int maxValues) {
            // private until we can think of a good use case and better name.
            Reject.ifFalse(maxValues >= 0, "maxValues must be >= 0");
            this.useReplaceMaxValues = maxValues;
            return this;
        }

        private Entry filter(final Entry entry) {
            return attributeFilter.filteredViewOf(entry);
        }

    }

    private static final class UnmodifiableEntry implements Entry {
        private final Entry entry;

        private UnmodifiableEntry(final Entry entry) {
            this.entry = entry;
        }

        /** {@inheritDoc} */
        @Override
        public boolean addAttribute(final Attribute attribute) {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override
        public boolean addAttribute(final Attribute attribute,
                final Collection<? super ByteString> duplicateValues) {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override
        public Entry addAttribute(final String attributeDescription, final Object... values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entry clearAttributes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAttribute(final Attribute attribute,
                final Collection<? super ByteString> missingValues) {
            return entry.containsAttribute(attribute, missingValues);
        }

        @Override
        public boolean containsAttribute(final String attributeDescription, final Object... values) {
            return entry.containsAttribute(attributeDescription, values);
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(final Object object) {
            return object == this || entry.equals(object);
        }

        @Override
        public Iterable<Attribute> getAllAttributes() {
            return Iterables.unmodifiableIterable(Iterables.transformedIterable(entry
                    .getAllAttributes(), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
        }

        @Override
        public Iterable<Attribute> getAllAttributes(final AttributeDescription attributeDescription) {
            return Iterables.unmodifiableIterable(Iterables.transformedIterable(entry
                    .getAllAttributes(attributeDescription), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
        }

        /** {@inheritDoc} */
        @Override
        public Iterable<Attribute> getAllAttributes(final String attributeDescription) {
            return Iterables.unmodifiableIterable(Iterables.transformedIterable(entry
                    .getAllAttributes(attributeDescription), UNMODIFIABLE_ATTRIBUTE_FUNCTION));
        }

        @Override
        public Attribute getAttribute(final AttributeDescription attributeDescription) {
            final Attribute attribute = entry.getAttribute(attributeDescription);
            if (attribute != null) {
                return Attributes.unmodifiableAttribute(attribute);
            } else {
                return null;
            }
        }

        /** {@inheritDoc} */
        @Override
        public Attribute getAttribute(final String attributeDescription) {
            final Attribute attribute = entry.getAttribute(attributeDescription);
            if (attribute != null) {
                return Attributes.unmodifiableAttribute(attribute);
            } else {
                return null;
            }
        }

        @Override
        public int getAttributeCount() {
            return entry.getAttributeCount();
        }

        /** {@inheritDoc} */
        @Override
        public DN getName() {
            return entry.getName();
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return entry.hashCode();
        }

        /** {@inheritDoc} */
        @Override
        public AttributeParser parseAttribute(final AttributeDescription attributeDescription) {
            return entry.parseAttribute(attributeDescription);
        }

        /** {@inheritDoc} */
        @Override
        public AttributeParser parseAttribute(final String attributeDescription) {
            return entry.parseAttribute(attributeDescription);
        }

        /** {@inheritDoc} */
        @Override
        public boolean removeAttribute(final Attribute attribute,
                final Collection<? super ByteString> missingValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAttribute(final AttributeDescription attributeDescription) {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override
        public Entry removeAttribute(final String attributeDescription, final Object... values) {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override
        public boolean replaceAttribute(final Attribute attribute) {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override
        public Entry replaceAttribute(final String attributeDescription, final Object... values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Entry setName(final DN dn) {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override
        public Entry setName(final String dn) {
            throw new UnsupportedOperationException();
        }

        /** {@inheritDoc} */
        @Override
        public String toString() {
            return entry.toString();
        }

    }

    private static final Comparator<Entry> COMPARATOR = new Comparator<Entry>() {
        @Override
        public int compare(final Entry o1, final Entry o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

    private static final AttributeFilter USER_ATTRIBUTES_ONLY_FILTER = new AttributeFilter();
    private static final DiffOptions DEFAULT_DIFF_OPTIONS = new DiffOptions();

    private static final Function<Attribute, Attribute, NeverThrowsException> UNMODIFIABLE_ATTRIBUTE_FUNCTION =
            new Function<Attribute, Attribute, NeverThrowsException>() {
                @Override
                public Attribute apply(final Attribute value) {
                    return Attributes.unmodifiableAttribute(value);
                }

            };

    /**
     * Returns a {@code Comparator} which can be used to compare entries by name
     * using the natural order for DN comparisons (parent before children).
     * <p>
     * In order to sort entries in reverse order (children first) use the
     * following code:
     *
     * <pre>
     * Collections.reverseOrder(Entries.compareByName());
     * </pre>
     *
     * For more complex sort orders involving one or more attributes refer to
     * the {@link SortKey} class.
     *
     * @return The {@code Comparator}.
     */
    public static Comparator<Entry> compareByName() {
        return COMPARATOR;
    }

    /**
     * Returns {@code true} if the provided entry is valid according to the
     * specified schema and schema validation policy.
     * <p>
     * If attribute value validation is enabled then following checks will be
     * performed:
     * <ul>
     * <li>checking that there is at least one value
     * <li>checking that single-valued attributes contain only a single value
     * </ul>
     * In particular, attribute values will not be checked for conformance to
     * their syntax since this is expected to have already been performed while
     * adding the values to the entry.
     *
     * @param entry
     *            The entry to be validated.
     * @param schema
     *            The schema against which the entry will be validated.
     * @param policy
     *            The schema validation policy.
     * @param errorMessages
     *            A collection into which any schema validation warnings or
     *            error messages can be placed, or {@code null} if they should
     *            not be saved.
     * @return {@code true} if the provided entry is valid according to the
     *         specified schema and schema validation policy.
     * @see Schema#validateEntry(Entry, SchemaValidationPolicy, Collection)
     */
    public static boolean conformsToSchema(final Entry entry, final Schema schema,
            final SchemaValidationPolicy policy, final Collection<LocalizableMessage> errorMessages) {
        return schema.validateEntry(entry, policy, errorMessages);
    }

    /**
     * Returns {@code true} if the provided entry is valid according to the
     * default schema and schema validation policy.
     * <p>
     * If attribute value validation is enabled then following checks will be
     * performed:
     * <ul>
     * <li>checking that there is at least one value
     * <li>checking that single-valued attributes contain only a single value
     * </ul>
     * In particular, attribute values will not be checked for conformance to
     * their syntax since this is expected to have already been performed while
     * adding the values to the entry.
     *
     * @param entry
     *            The entry to be validated.
     * @param policy
     *            The schema validation policy.
     * @param errorMessages
     *            A collection into which any schema validation warnings or
     *            error messages can be placed, or {@code null} if they should
     *            not be saved.
     * @return {@code true} if the provided entry is valid according to the
     *         default schema and schema validation policy.
     * @see Schema#validateEntry(Entry, SchemaValidationPolicy, Collection)
     */
    public static boolean conformsToSchema(final Entry entry, final SchemaValidationPolicy policy,
            final Collection<LocalizableMessage> errorMessages) {
        return conformsToSchema(entry, Schema.getDefaultSchema(), policy, errorMessages);
    }

    /**
     * Check if the provided entry contains the provided object class.
     * <p>
     * This method uses the default schema for decoding the object class
     * attribute values.
     * <p>
     * The provided object class must be recognized by the schema, otherwise the
     * method returns false.
     *
     * @param entry
     *            The entry which is checked against the object class.
     * @param objectClass
     *            The object class to check.
     * @return {@code true} if and only if entry contains the object class and
     *         the object class is recognized by the default schema,
     *         {@code false} otherwise
     */
    public static boolean containsObjectClass(final Entry entry, final ObjectClass objectClass) {
        return containsObjectClass(entry, Schema.getDefaultSchema(), objectClass);
    }

    /**
     * Check if the provided entry contains the provided object class.
     * <p>
     * The provided object class must be recognized by the provided schema,
     * otherwise the method returns false.
     *
     * @param entry
     *            The entry which is checked against the object class.
     * @param schema
     *            The schema which should be used for decoding the object class
     *            attribute values.
     * @param objectClass
     *            The object class to check.
     * @return {@code true} if and only if entry contains the object class and
     *         the object class is recognized by the provided schema,
     *         {@code false} otherwise
     */
    public static boolean containsObjectClass(final Entry entry, final Schema schema, final ObjectClass objectClass) {
        return getObjectClasses(entry, schema).contains(objectClass);
    }

    /**
     * Creates a new modify request containing a list of modifications which can
     * be used to transform {@code fromEntry} into entry {@code toEntry}.
     * <p>
     * The changes will be generated using a default set of {@link DiffOptions
     * options}. More specifically, only user attributes will be compared,
     * attributes will be compared using their matching rules, and all generated
     * changes will be reversible: it will contain only modifications of type
     * {@link ModificationType#DELETE DELETE} then {@link ModificationType#ADD
     * ADD}.
     * <p>
     * Finally, the modify request will use the distinguished name taken from
     * {@code fromEntry}. This method will not check to see if both
     * {@code fromEntry} and {@code toEntry} have the same distinguished name.
     * <p>
     * This method is equivalent to:
     *
     * <pre>
     * ModifyRequest request = Requests.newModifyRequest(fromEntry, toEntry);
     * </pre>
     *
     * Or:
     *
     * <pre>
     * ModifyRequest request = diffEntries(fromEntry, toEntry, Entries.diffOptions());
     * </pre>
     *
     * @param fromEntry
     *            The source entry.
     * @param toEntry
     *            The destination entry.
     * @return A modify request containing a list of modifications which can be
     *         used to transform {@code fromEntry} into entry {@code toEntry}.
     *         The returned request will always be non-{@code null} but may not
     *         contain any modifications.
     * @throws NullPointerException
     *             If {@code fromEntry} or {@code toEntry} were {@code null}.
     * @see Requests#newModifyRequest(Entry, Entry)
     */
    public static ModifyRequest diffEntries(final Entry fromEntry, final Entry toEntry) {
        return diffEntries(fromEntry, toEntry, DEFAULT_DIFF_OPTIONS);
    }

    /**
     * Creates a new modify request containing a list of modifications which can
     * be used to transform {@code fromEntry} into entry {@code toEntry}.
     * <p>
     * The changes will be generated using the provided set of
     * {@link DiffOptions}.
     * <p>
     * Finally, the modify request will use the distinguished name taken from
     * {@code fromEntry}. This method will not check to see if both
     * {@code fromEntry} and {@code toEntry} have the same distinguished name.
     *
     * @param fromEntry
     *            The source entry.
     * @param toEntry
     *            The destination entry.
     * @param options
     *            The set of options which will control which attributes are
     *            compared, how they are compared, and the type of modifications
     *            generated.
     * @return A modify request containing a list of modifications which can be
     *         used to transform {@code fromEntry} into entry {@code toEntry}.
     *         The returned request will always be non-{@code null} but may not
     *         contain any modifications.
     * @throws NullPointerException
     *             If {@code fromEntry}, {@code toEntry}, or {@code options}
     *             were {@code null}.
     */
    public static ModifyRequest diffEntries(final Entry fromEntry, final Entry toEntry,
            final DiffOptions options) {
        Reject.ifNull(fromEntry, toEntry, options);

        final ModifyRequest request = Requests.newModifyRequest(fromEntry.getName());
        final Entry tfrom = toFilteredTreeMapEntry(fromEntry, options);
        final Entry tto = toFilteredTreeMapEntry(toEntry, options);
        final Iterator<Attribute> ifrom = tfrom.getAllAttributes().iterator();
        final Iterator<Attribute> ito = tto.getAllAttributes().iterator();

        Attribute afrom = ifrom.hasNext() ? ifrom.next() : null;
        Attribute ato = ito.hasNext() ? ito.next() : null;

        while (afrom != null && ato != null) {
            final AttributeDescription adfrom = afrom.getAttributeDescription();
            final AttributeDescription adto = ato.getAttributeDescription();

            final int cmp = adfrom.compareTo(adto);
            if (cmp == 0) {
                /*
                 * Attribute is in both entries so compute the differences
                 * between the old and new.
                 */
                if (options.useReplaceMaxValues > ato.size()) {
                    // This attribute is a candidate for replacing.
                    if (diffAttributeNeedsReplacing(afrom, ato, options)) {
                        request.addModification(new Modification(ModificationType.REPLACE, ato));
                    }
                } else if (afrom.size() == 1 && ato.size() == 1) {
                    // Fast-path for single valued attributes.
                    if (diffFirstValuesAreDifferent(options, afrom, ato)) {
                        diffDeleteValues(request, afrom);
                        diffAddValues(request, ato);
                    }
                } else if (options.useExactMatching) {
                    /*
                     * Compare multi-valued attributes using exact matching. Use
                     * a hash sets for membership checking rather than the
                     * attributes in order to avoid matching rule based
                     * comparisons.
                     */
                    final Set<ByteString> oldValues = new LinkedHashSet<>(afrom);
                    final Set<ByteString> newValues = new LinkedHashSet<>(ato);

                    final Set<ByteString> deletedValues = new LinkedHashSet<>(oldValues);
                    deletedValues.removeAll(newValues);
                    diffDeleteValues(request, deletedValues.size() == afrom.size() ? afrom
                            : new LinkedAttribute(adfrom, deletedValues));

                    final Set<ByteString> addedValues = newValues;
                    addedValues.removeAll(oldValues);
                    diffAddValues(request, addedValues.size() == ato.size() ? ato
                            : new LinkedAttribute(adto, addedValues));
                } else {
                    // Compare multi-valued attributes using matching rules.
                    final Attribute deletedValues = new LinkedAttribute(afrom);
                    deletedValues.removeAll(ato);
                    diffDeleteValues(request, deletedValues);

                    final Attribute addedValues = new LinkedAttribute(ato);
                    addedValues.removeAll(afrom);
                    diffAddValues(request, addedValues);
                }

                afrom = ifrom.hasNext() ? ifrom.next() : null;
                ato = ito.hasNext() ? ito.next() : null;
            } else if (cmp < 0) {
                // afrom in source, but not destination.
                diffDeleteAttribute(request, afrom, options);
                afrom = ifrom.hasNext() ? ifrom.next() : null;
            } else {
                // ato in destination, but not in source.
                diffAddAttribute(request, ato, options);
                ato = ito.hasNext() ? ito.next() : null;
            }
        }

        // Additional attributes in source entry: these must be deleted.
        if (afrom != null) {
            diffDeleteAttribute(request, afrom, options);
        }
        while (ifrom.hasNext()) {
            diffDeleteAttribute(request, ifrom.next(), options);
        }

        // Additional attributes in destination entry: these must be added.
        if (ato != null) {
            diffAddAttribute(request, ato, options);
        }
        while (ito.hasNext()) {
            diffAddAttribute(request, ito.next(), options);
        }

        return request;
    }

    /**
     * Returns a new set of options which may be used to control how entries are
     * compared and changes generated using
     * {@link #diffEntries(Entry, Entry, DiffOptions)}. By default only user
     * attributes will be compared, matching rules will be used for comparisons,
     * and all generated changes will be reversible.
     *
     * @return A new set of options which may be used to control how entries are
     *         compared and changes generated.
     */
    public static DiffOptions diffOptions() {
        return new DiffOptions();
    }

    /**
     * Returns an unmodifiable set containing the object classes associated with
     * the provided entry. This method will ignore unrecognized object classes.
     * <p>
     * This method uses the default schema for decoding the object class
     * attribute values.
     *
     * @param entry
     *            The entry whose object classes are required.
     * @return An unmodifiable set containing the object classes associated with
     *         the provided entry.
     */
    public static Set<ObjectClass> getObjectClasses(final Entry entry) {
        return getObjectClasses(entry, Schema.getDefaultSchema());
    }

    /**
     * Returns an unmodifiable set containing the object classes associated with
     * the provided entry. This method will ignore unrecognized object classes.
     *
     * @param entry
     *            The entry whose object classes are required.
     * @param schema
     *            The schema which should be used for decoding the object class
     *            attribute values.
     * @return An unmodifiable set containing the object classes associated with
     *         the provided entry.
     */
    public static Set<ObjectClass> getObjectClasses(final Entry entry, final Schema schema) {
        final Attribute objectClassAttribute =
                entry.getAttribute(AttributeDescription.objectClass());
        if (objectClassAttribute == null) {
            return Collections.emptySet();
        } else {
            final Set<ObjectClass> objectClasses = new HashSet<>(objectClassAttribute.size());
            for (final ByteString v : objectClassAttribute) {
                final String objectClassName = v.toString();
                final ObjectClass objectClass;
                try {
                    objectClass = schema.getObjectClass(objectClassName);
                    objectClasses.add(objectClass);
                } catch (final UnknownSchemaElementException e) {
                    // Ignore.
                    continue;
                }
            }
            return Collections.unmodifiableSet(objectClasses);
        }
    }

    /**
     * Returns the structural object class associated with the provided entry,
     * or {@code null} if none was found. If the entry contains multiple
     * structural object classes then the first will be returned. This method
     * will ignore unrecognized object classes.
     * <p>
     * This method uses the default schema for decoding the object class
     * attribute values.
     *
     * @param entry
     *            The entry whose structural object class is required.
     * @return The structural object class associated with the provided entry,
     *         or {@code null} if none was found.
     */
    public static ObjectClass getStructuralObjectClass(final Entry entry) {
        return getStructuralObjectClass(entry, Schema.getDefaultSchema());
    }

    /**
     * Builds an entry from the provided lines of LDIF.
     * <p>
     * Sample usage:
     * <pre>
     * Entry john = makeEntry(
     *   "dn: cn=John Smith,dc=example,dc=com",
     *   "objectclass: inetorgperson",
     *   "cn: John Smith",
     *   "sn: Smith",
     *   "givenname: John");
     * </pre>
     *
     * @param ldifLines
     *          LDIF lines that contains entry definition.
     * @return an entry
     * @throws LocalizedIllegalArgumentException
     *            If {@code ldifLines} did not contain an LDIF entry, or
     *            contained multiple entries, or contained malformed LDIF, or
     *            if the entry could not be decoded using the default schema.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null}.
     */
    public static Entry makeEntry(String... ldifLines) {
        return LDIF.makeEntry(ldifLines);
    }

    /**
     * Builds a list of entries from the provided lines of LDIF.
     * <p>
     * Sample usage:
     * <pre>
     * List&lt;Entry&gt; smiths = TestCaseUtils.makeEntries(
     *   "dn: cn=John Smith,dc=example,dc=com",
     *   "objectclass: inetorgperson",
     *   "cn: John Smith",
     *   "sn: Smith",
     *   "givenname: John",
     *   "",
     *   "dn: cn=Jane Smith,dc=example,dc=com",
     *   "objectclass: inetorgperson",
     *   "cn: Jane Smith",
     *   "sn: Smith",
     *   "givenname: Jane");
     * </pre>
     * @param ldifLines
     *          LDIF lines that contains entries definition.
     *          Entries are separated by an empty string: {@code ""}.
     * @return a non empty list of entries
     * @throws LocalizedIllegalArgumentException
     *             If {@code ldifLines} did not contain LDIF entries,
     *             or contained malformed LDIF, or if the entries
     *             could not be decoded using the default schema.
     * @throws NullPointerException
     *             If {@code ldifLines} was {@code null}.
     */
    public static List<Entry> makeEntries(String... ldifLines) {
        return LDIF.makeEntries(ldifLines);
    }

    /**
     * Returns the structural object class associated with the provided entry,
     * or {@code null} if none was found. If the entry contains multiple
     * structural object classes then the first will be returned. This method
     * will ignore unrecognized object classes.
     *
     * @param entry
     *            The entry whose structural object class is required.
     * @param schema
     *            The schema which should be used for decoding the object class
     *            attribute values.
     * @return The structural object class associated with the provided entry,
     *         or {@code null} if none was found.
     */
    public static ObjectClass getStructuralObjectClass(final Entry entry, final Schema schema) {
        ObjectClass structuralObjectClass = null;
        final Attribute objectClassAttribute = entry.getAttribute(objectClass());

        if (objectClassAttribute == null) {
            return null;
        }

        for (final ByteString v : objectClassAttribute) {
            final String objectClassName = v.toString();
            final ObjectClass objectClass;
            try {
                objectClass = schema.getObjectClass(objectClassName);
            } catch (final UnknownSchemaElementException e) {
                // Ignore.
                continue;
            }

            if (objectClass.getObjectClassType() == ObjectClassType.STRUCTURAL
                    && (structuralObjectClass == null || objectClass.isDescendantOf(structuralObjectClass))) {
                structuralObjectClass = objectClass;
            }
        }

        return structuralObjectClass;
    }

    /**
     * Applies the provided modification to an entry. This method implements
     * "permissive" modify semantics, ignoring attempts to add duplicate values
     * or attempts to remove values which do not exist.
     *
     * @param entry
     *            The entry to be modified.
     * @param change
     *            The modification to be applied to the entry.
     * @return A reference to the updated entry.
     * @throws LdapException
     *             If an error occurred while performing the change such as an
     *             attempt to increment a value which is not a number. The entry
     *             will not have been modified.
     */
    public static Entry modifyEntry(final Entry entry, final Modification change) throws LdapException {
        return modifyEntry(entry, change, null);
    }

    /**
     * Applies the provided modification to an entry. This method implements
     * "permissive" modify semantics, recording attempts to add duplicate values
     * or attempts to remove values which do not exist in the provided
     * collection if provided.
     *
     * @param entry
     *            The entry to be modified.
     * @param change
     *            The modification to be applied to the entry.
     * @param conflictingValues
     *            A collection into which duplicate or missing values will be
     *            added, or {@code null} if conflicting values should not be
     *            saved.
     * @return A reference to the updated entry.
     * @throws LdapException
     *             If an error occurred while performing the change such as an
     *             attempt to increment a value which is not a number. The entry
     *             will not have been modified.
     */
    public static Entry modifyEntry(final Entry entry, final Modification change,
            final Collection<? super ByteString> conflictingValues) throws LdapException {
        return modifyEntry0(entry, change, conflictingValues, true);
    }

    /**
     * Applies the provided modification request to an entry. This method will
     * utilize "permissive" modify semantics if the request contains the
     * {@link PermissiveModifyRequestControl}.
     *
     * @param entry
     *            The entry to be modified.
     * @param changes
     *            The modification request to be applied to the entry.
     * @return A reference to the updated entry.
     * @throws LdapException
     *             If an error occurred while performing the changes such as an
     *             attempt to add duplicate values, remove values which do not
     *             exist, or increment a value which is not a number. The entry
     *             may have been modified.
     */
    public static Entry modifyEntry(final Entry entry, final ModifyRequest changes) throws LdapException {
        final boolean isPermissive = changes.containsControl(PermissiveModifyRequestControl.OID);
        return modifyEntry0(entry, changes.getModifications(), isPermissive);
    }

    /**
     * Applies the provided modifications to an entry using "permissive" modify
     * semantics.
     *
     * @param entry
     *            The entry to be modified.
     * @param changes
     *            The modification request to be applied to the entry.
     * @return A reference to the updated entry.
     * @throws LdapException
     *             If an error occurred while performing the changes such as an
     *             attempt to increment a value which is not a number. The entry
     *             may have been modified.
     */
    public static Entry modifyEntryPermissive(final Entry entry,
            final Collection<Modification> changes) throws LdapException {
        return modifyEntry0(entry, changes, true);
    }

    /**
     * Applies the provided modifications to an entry using "strict" modify
     * semantics. Attempts to add duplicate values or attempts to remove values
     * which do not exist will cause the update to fail.
     *
     * @param entry
     *            The entry to be modified.
     * @param changes
     *            The modification request to be applied to the entry.
     * @return A reference to the updated entry.
     * @throws LdapException
     *             If an error occurred while performing the changes such as an
     *             attempt to add duplicate values, remove values which do not
     *             exist, or increment a value which is not a number. The entry
     *             may have been modified.
     */
    public static Entry modifyEntryStrict(final Entry entry, final Collection<Modification> changes)
            throws LdapException {
        return modifyEntry0(entry, changes, false);
    }

    /**
     * Returns a read-only view of {@code entry} and its attributes. Query
     * operations on the returned entry and its attributes "read-through" to the
     * underlying entry or attribute, and attempts to modify the returned entry
     * and its attributes either directly or indirectly via an iterator result
     * in an {@code UnsupportedOperationException}.
     *
     * @param entry
     *            The entry for which a read-only view is to be returned.
     * @return A read-only view of {@code entry}.
     * @throws NullPointerException
     *             If {@code entry} was {@code null}.
     */
    public static Entry unmodifiableEntry(final Entry entry) {
        if (entry instanceof UnmodifiableEntry) {
            return entry;
        } else {
            return new UnmodifiableEntry(entry);
        }
    }

    private static void diffAddAttribute(final ModifyRequest request, final Attribute ato,
            final DiffOptions diffOptions) {
        if (diffOptions.useReplaceMaxValues > 0) {
            request.addModification(new Modification(ModificationType.REPLACE, ato));
        } else {
            request.addModification(new Modification(ModificationType.ADD, ato));
        }
    }

    private static void diffAddValues(final ModifyRequest request, final Attribute addedValues) {
        if (addedValues != null && !addedValues.isEmpty()) {
            request.addModification(new Modification(ModificationType.ADD, addedValues));
        }
    }

    private static boolean diffAttributeNeedsReplacing(final Attribute afrom, final Attribute ato,
            final DiffOptions options) {
        if (afrom.size() != ato.size()) {
            return true;
        } else if (afrom.size() == 1) {
            return diffFirstValuesAreDifferent(options, afrom, ato);
        } else if (options.useExactMatching) {
            /*
             * Use a hash set for membership checking rather than the attribute
             * in order to avoid matching rule based comparisons.
             */
            final Set<ByteString> oldValues = new LinkedHashSet<>(afrom);
            return !oldValues.containsAll(ato);
        } else {
            return !afrom.equals(ato);
        }
    }

    private static void diffDeleteAttribute(final ModifyRequest request, final Attribute afrom,
            final DiffOptions diffOptions) {
        if (diffOptions.useReplaceMaxValues > 0) {
            request.addModification(new Modification(ModificationType.REPLACE, Attributes
                    .emptyAttribute(afrom.getAttributeDescription())));
        } else {
            request.addModification(new Modification(ModificationType.DELETE, afrom));
        }
    }

    private static void diffDeleteValues(final ModifyRequest request, final Attribute deletedValues) {
        if (deletedValues != null && !deletedValues.isEmpty()) {
            request.addModification(new Modification(ModificationType.DELETE, deletedValues));
        }
    }

    private static boolean diffFirstValuesAreDifferent(final DiffOptions diffOptions,
            final Attribute afrom, final Attribute ato) {
        if (diffOptions.useExactMatching) {
            return !afrom.firstValue().equals(ato.firstValue());
        } else {
            return !afrom.contains(ato.firstValue());
        }
    }

    private static void incrementAttribute(final Entry entry, final Attribute change)
            throws LdapException {
        // First parse the change.
        final AttributeDescription deltaAd = change.getAttributeDescription();
        if (change.size() != 1) {
            throw newLdapException(ResultCode.CONSTRAINT_VIOLATION,
                    ERR_ENTRY_INCREMENT_INVALID_VALUE_COUNT.get(deltaAd.toString()).toString());
        }
        final long delta;
        try {
            delta = change.parse().asLong();
        } catch (final Exception e) {
            throw newLdapException(ResultCode.CONSTRAINT_VIOLATION,
                    ERR_ENTRY_INCREMENT_CANNOT_PARSE_AS_INT.get(deltaAd.toString()).toString());
        }

        // Now apply the increment to the attribute.
        final Attribute oldAttribute = entry.getAttribute(deltaAd);
        if (oldAttribute == null) {
            throw newLdapException(ResultCode.NO_SUCH_ATTRIBUTE,
                    ERR_ENTRY_INCREMENT_NO_SUCH_ATTRIBUTE.get(deltaAd.toString()).toString());
        }

        // Re-use existing attribute description in case it differs in case, etc.
        final Attribute newAttribute = new LinkedAttribute(oldAttribute.getAttributeDescription());
        try {
            for (final Long value : oldAttribute.parse().asSetOfLong()) {
                newAttribute.add(value + delta);
            }
        } catch (final Exception e) {
            throw newLdapException(ResultCode.CONSTRAINT_VIOLATION,
                    ERR_ENTRY_INCREMENT_CANNOT_PARSE_AS_INT.get(deltaAd.toString()).toString());
        }
        entry.replaceAttribute(newAttribute);
    }

    private static Entry modifyEntry0(final Entry entry, final Collection<Modification> changes,
            final boolean isPermissive) throws LdapException {
        final Collection<ByteString> conflictingValues =
                isPermissive ? null : new ArrayList<ByteString>(0);
        for (final Modification change : changes) {
            modifyEntry0(entry, change, conflictingValues, isPermissive);
        }
        return entry;
    }

    private static Entry modifyEntry0(final Entry entry, final Modification change,
            final Collection<? super ByteString> conflictingValues, final boolean isPermissive)
            throws LdapException {
        final ModificationType modType = change.getModificationType();
        if (modType.equals(ModificationType.ADD)) {
            entry.addAttribute(change.getAttribute(), conflictingValues);
            if (!isPermissive && !conflictingValues.isEmpty()) {
                // Duplicate values.
                throw newLdapException(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS,
                        ERR_ENTRY_DUPLICATE_VALUES.get(
                                change.getAttribute().getAttributeDescriptionAsString()).toString());
            }
        } else if (modType.equals(ModificationType.DELETE)) {
            final boolean hasChanged =
                    entry.removeAttribute(change.getAttribute(), conflictingValues);
            if (!isPermissive && (!hasChanged || !conflictingValues.isEmpty())) {
                // Missing attribute or values.
                throw newLdapException(ResultCode.NO_SUCH_ATTRIBUTE, ERR_ENTRY_NO_SUCH_VALUE.get(
                        change.getAttribute().getAttributeDescriptionAsString()).toString());
            }
        } else if (modType.equals(ModificationType.REPLACE)) {
            entry.replaceAttribute(change.getAttribute());
        } else if (modType.equals(ModificationType.INCREMENT)) {
            incrementAttribute(entry, change.getAttribute());
        } else {
            throw newLdapException(ResultCode.UNWILLING_TO_PERFORM,
                    ERR_ENTRY_UNKNOWN_MODIFICATION_TYPE.get(String.valueOf(modType)).toString());
        }
        return entry;
    }

    private static Entry toFilteredTreeMapEntry(final Entry entry, final DiffOptions options) {
        if (entry instanceof TreeMapEntry) {
            return options.filter(entry);
        } else {
            return new TreeMapEntry(options.filter(entry));
        }
    }

    /** Prevent instantiation. */
    private Entries() {
        // Nothing to do.
    }
}
