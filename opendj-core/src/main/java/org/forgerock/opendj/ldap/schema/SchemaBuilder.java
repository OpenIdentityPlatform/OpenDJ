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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014 Manuel Gaupp
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import static java.util.Collections.*;

import static org.forgerock.opendj.ldap.LdapException.*;
import static org.forgerock.opendj.ldap.schema.ObjectClass.*;
import static org.forgerock.opendj.ldap.schema.ObjectClassType.*;
import static org.forgerock.opendj.ldap.schema.Schema.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.forgerock.opendj.ldap.schema.SchemaUtils.*;

import static com.forgerock.opendj.ldap.CoreMessages.*;
import static com.forgerock.opendj.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapPromise;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.schema.DITContentRule.Builder;
import org.forgerock.util.Options;
import org.forgerock.util.Reject;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.Function;
import org.forgerock.util.Option;
import org.forgerock.util.promise.Promise;

import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.SubstringReader;

/**
 * Schema builders should be used for incremental construction of new schemas.
 */
public final class SchemaBuilder {

    /** Constant used for name to oid mapping when one name actually maps to multiple numerical OID. */
    public static final String AMBIGUOUS_OID = "<ambiguous-oid>";

    private static final String ATTR_SUBSCHEMA_SUBENTRY = "subschemaSubentry";

    private static final String[] SUBSCHEMA_ATTRS = { ATTR_LDAP_SYNTAXES,
        ATTR_ATTRIBUTE_TYPES, ATTR_DIT_CONTENT_RULES, ATTR_DIT_STRUCTURE_RULES,
        ATTR_MATCHING_RULE_USE, ATTR_MATCHING_RULES, ATTR_NAME_FORMS, ATTR_OBJECT_CLASSES };

    private static final Filter SUBSCHEMA_FILTER = Filter.valueOf("(objectClass=subschema)");

    private static final String[] SUBSCHEMA_SUBENTRY_ATTRS = { ATTR_SUBSCHEMA_SUBENTRY };

    /**
     * Constructs a search request for retrieving the subschemaSubentry
     * attribute from the named entry.
     */
    private static SearchRequest getReadSchemaForEntrySearchRequest(final DN dn) {
        return Requests.newSearchRequest(dn, SearchScope.BASE_OBJECT, Filter.objectClassPresent(),
                SUBSCHEMA_SUBENTRY_ATTRS);
    }

    /**
     * Constructs a search request for retrieving the named subschema
     * sub-entry.
     */
    private static SearchRequest getReadSchemaSearchRequest(final DN dn) {
        return Requests.newSearchRequest(dn, SearchScope.BASE_OBJECT, SUBSCHEMA_FILTER,
                SUBSCHEMA_ATTRS);
    }

    private static DN getSubschemaSubentryDN(final DN name, final Entry entry) throws LdapException {
        final Attribute subentryAttr = entry.getAttribute(ATTR_SUBSCHEMA_SUBENTRY);

        if (subentryAttr == null || subentryAttr.isEmpty()) {
            // Did not get the subschema sub-entry attribute.
            throw newLdapException(ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED,
                    ERR_NO_SUBSCHEMA_SUBENTRY_ATTR.get(name.toString()).toString());
        }

        final String dnString = subentryAttr.iterator().next().toString();
        DN subschemaDN;
        try {
            subschemaDN = DN.valueOf(dnString);
        } catch (final LocalizedIllegalArgumentException e) {
            throw newLdapException(ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED,
                    ERR_INVALID_SUBSCHEMA_SUBENTRY_ATTR.get(name.toString(), dnString,
                            e.getMessageObject()).toString());
        }
        return subschemaDN;
    }

    private Map<Integer, DITStructureRule> id2StructureRules;
    private Map<String, List<AttributeType>> name2AttributeTypes;
    private Map<String, List<DITContentRule>> name2ContentRules;
    private Map<String, List<MatchingRule>> name2MatchingRules;
    private Map<String, List<MatchingRuleUse>> name2MatchingRuleUses;
    private Map<String, List<NameForm>> name2NameForms;
    private Map<String, List<ObjectClass>> name2ObjectClasses;
    private Map<String, List<DITStructureRule>> name2StructureRules;
    private Map<String, List<DITStructureRule>> nameForm2StructureRules;
    private Map<String, AttributeType> numericOID2AttributeTypes;
    private Map<String, DITContentRule> numericOID2ContentRules;
    private Map<String, MatchingRule> numericOID2MatchingRules;
    private Map<String, MatchingRuleUse> numericOID2MatchingRuleUses;
    private Map<String, NameForm> numericOID2NameForms;
    private Map<String, ObjectClass> numericOID2ObjectClasses;
    private Map<String, Syntax> numericOID2Syntaxes;
    private Map<String, List<NameForm>> objectClass2NameForms;
    private Map<String, String> name2OIDs;
    private String schemaName;
    private List<LocalizableMessage> warnings;
    private Options options;


    /** A schema which should be copied into this builder on any mutation. */
    private Schema copyOnWriteSchema;

    /**
     * A unique ID which can be used to uniquely identify schemas
     * constructed without a name.
     */
    private static final AtomicInteger NEXT_SCHEMA_ID = new AtomicInteger();

    /**
     * Creates a new schema builder with no schema elements and default
     * compatibility options.
     */
    public SchemaBuilder() {
        preLazyInitBuilder(null, null);
    }

    /**
     * Creates a new schema builder containing all of the schema elements
     * contained in the provided subschema subentry. Any problems encountered
     * while parsing the entry can be retrieved using the returned schema's
     * {@link Schema#getWarnings()} method.
     *
     * @param entry
     *            The subschema subentry to be parsed.
     * @throws NullPointerException
     *             If {@code entry} was {@code null}.
     */
    public SchemaBuilder(final Entry entry) {
        preLazyInitBuilder(entry.getName().toString(), null);
        addSchema(entry, true);
    }

    /**
     * Creates a new schema builder containing all of the schema elements from
     * the provided schema and its compatibility options.
     *
     * @param schema
     *            The initial contents of the schema builder.
     * @throws NullPointerException
     *             If {@code schema} was {@code null}.
     */
    public SchemaBuilder(final Schema schema) {
        preLazyInitBuilder(schema.getSchemaName(), schema);
    }

    /**
     * Creates a new schema builder with no schema elements and default
     * compatibility options.
     *
     * @param schemaName
     *            The user-friendly name of this schema which may be used for
     *            debugging purposes.
     */
    public SchemaBuilder(final String schemaName) {
        preLazyInitBuilder(schemaName, null);
    }

    private Boolean allowsMalformedNamesAndOptions() {
        return options.get(ALLOW_MALFORMED_NAMES_AND_OPTIONS);
    }

    /**
     * Adds the provided attribute type definition to this schema builder.
     *
     * @param definition
     *            The attribute type definition.
     * @param overwrite
     *            {@code true} if any existing attribute type with the same OID
     *            should be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     * @throws LocalizedIllegalArgumentException
     *             If the provided attribute type definition could not be
     *             parsed.
     * @throws NullPointerException
     *             If {@code definition} was {@code null}.
     */
    public SchemaBuilder addAttributeType(final String definition, final boolean overwrite) {
        Reject.ifNull(definition);

        lazyInitBuilder();

        try {
            final SubstringReader reader = new SubstringReader(definition);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // This means that the definition was empty or contained only
                // whitespace. That is illegal.
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_ATTRTYPE_EMPTY_VALUE1.get(definition));
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                final LocalizableMessage message = ERR_ATTR_SYNTAX_ATTRTYPE_EXPECTED_OPEN_PARENTHESIS.get(
                    definition, reader.pos() - 1, String.valueOf(c));
                throw new LocalizedIllegalArgumentException(message);
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters must be the OID.
            final String oid = readOID(reader, allowsMalformedNamesAndOptions());
            AttributeType.Builder atBuilder = new AttributeType.Builder(oid, this);
            atBuilder.definition(definition);
            String superiorType = null;
            String syntax = null;
            // At this point, we should have a pretty specific syntax that
            // describes what may come next, but some of the components are
            // optional and it would be pretty easy to put something in the
            // wrong order, so we will be very flexible about what we can
            // accept. Just look at the next token, figure out what it is and
            // how to treat what comes after it, then repeat until we get to
            // the end of the definition. But before we start, set default
            // values for everything else we might need to know.
            while (true) {
                final String tokenName = readTokenName(reader);

                if (tokenName == null) {
                    // No more tokens.
                    break;
                } else if ("name".equalsIgnoreCase(tokenName)) {
                    atBuilder.names(readNameDescriptors(reader, allowsMalformedNamesAndOptions()));
                } else if ("desc".equalsIgnoreCase(tokenName)) {
                    // This specifies the description for the attribute type. It
                    // is an arbitrary string of characters enclosed in single
                    // quotes.
                    atBuilder.description(readQuotedString(reader));
                } else if ("obsolete".equalsIgnoreCase(tokenName)) {
                    // This indicates whether the attribute type should be
                    // considered obsolete.
                    atBuilder.obsolete(true);
                } else if ("sup".equalsIgnoreCase(tokenName)) {
                    // This specifies the name or OID of the superior attribute
                    // type from which this attribute type should inherit its
                    // properties.
                    superiorType = readOID(reader, allowsMalformedNamesAndOptions());
                } else if ("equality".equalsIgnoreCase(tokenName)) {
                    // This specifies the name or OID of the equality matching
                    // rule to use for this attribute type.
                    atBuilder.equalityMatchingRule(readOID(reader, allowsMalformedNamesAndOptions()));
                } else if ("ordering".equalsIgnoreCase(tokenName)) {
                    // This specifies the name or OID of the ordering matching
                    // rule to use for this attribute type.
                    atBuilder.orderingMatchingRule(readOID(reader, allowsMalformedNamesAndOptions()));
                } else if ("substr".equalsIgnoreCase(tokenName)) {
                    // This specifies the name or OID of the substring matching
                    // rule to use for this attribute type.
                    atBuilder.substringMatchingRule(readOID(reader, allowsMalformedNamesAndOptions()));
                } else if ("syntax".equalsIgnoreCase(tokenName)) {
                    // This specifies the numeric OID of the syntax for this
                    // matching rule. It may optionally be immediately followed
                    // by an open curly brace, an integer definition, and a close
                    // curly brace to suggest the minimum number of characters
                    // that should be allowed in values of that type. This
                    // implementation will ignore any such length because it
                    // does not impose any practical limit on the length of attribute
                    // values.
                    syntax = readOIDLen(reader, allowsMalformedNamesAndOptions());
                } else if ("single-value".equalsIgnoreCase(tokenName)) {
                    // This indicates that attributes of this type are allowed
                    // to have at most one value.
                    atBuilder.singleValue(true);
                } else if ("collective".equalsIgnoreCase(tokenName)) {
                    // This indicates that attributes of this type are collective
                    // (i.e., have their values generated dynamically in some way).
                    atBuilder.collective(true);
                } else if ("no-user-modification".equalsIgnoreCase(tokenName)) {
                    // This indicates that the values of attributes of this type
                    // are not to be modified by end users.
                    atBuilder.noUserModification(true);
                } else if ("usage".equalsIgnoreCase(tokenName)) {
                    // This specifies the usage string for this attribute type.
                    // It should be followed by one of the strings
                    // "userApplications", "directoryOperation",
                    // "distributedOperation", or "dSAOperation".
                    int length = 0;

                    reader.skipWhitespaces();
                    reader.mark();

                    while (" )".indexOf(reader.read()) == -1) {
                        length++;
                    }
                    reader.reset();
                    final String usageStr = reader.read(length);
                    if ("userapplications".equalsIgnoreCase(usageStr)) {
                        atBuilder.usage(AttributeUsage.USER_APPLICATIONS);
                    } else if ("directoryoperation".equalsIgnoreCase(usageStr)) {
                        atBuilder.usage(AttributeUsage.DIRECTORY_OPERATION);
                    } else if ("distributedoperation".equalsIgnoreCase(usageStr)) {
                        atBuilder.usage(AttributeUsage.DISTRIBUTED_OPERATION);
                    } else if ("dsaoperation".equalsIgnoreCase(usageStr)) {
                        atBuilder.usage(AttributeUsage.DSA_OPERATION);
                    } else {
                        throw new LocalizedIllegalArgumentException(
                            WARN_ATTR_SYNTAX_ATTRTYPE_INVALID_ATTRIBUTE_USAGE1.get(definition, usageStr));
                    }
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    atBuilder.extraProperties(tokenName, readExtensions(reader));
                } else {
                    throw new LocalizedIllegalArgumentException(
                        ERR_ATTR_SYNTAX_ATTRTYPE_ILLEGAL_TOKEN1.get(definition, tokenName));
                }
            }

            final List<String> approxRules = atBuilder.getExtraProperties().get(SCHEMA_PROPERTY_APPROX_RULE);
            if (approxRules != null && !approxRules.isEmpty()) {
                atBuilder.approximateMatchingRule(approxRules.get(0));
            }

            if (superiorType == null && syntax == null) {
                throw new LocalizedIllegalArgumentException(
                    WARN_ATTR_SYNTAX_ATTRTYPE_MISSING_SYNTAX_AND_SUPERIOR.get(definition));
            }

            atBuilder.superiorType(superiorType)
                     .syntax(syntax);

            return overwrite ? atBuilder.addToSchemaOverwrite() : atBuilder.addToSchema();
        } catch (final DecodeException e) {
            final LocalizableMessage msg = ERR_ATTR_SYNTAX_ATTRTYPE_INVALID1.get(definition, e.getMessageObject());
            throw new LocalizedIllegalArgumentException(msg, e.getCause());
        }
    }

    /**
     * Adds the provided DIT content rule definition to this schema builder.
     *
     * @param definition
     *            The DIT content rule definition.
     * @param overwrite
     *            {@code true} if any existing DIT content rule with the same
     *            OID should be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     * @throws LocalizedIllegalArgumentException
     *             If the provided DIT content rule definition could not be
     *             parsed.
     * @throws NullPointerException
     *             If {@code definition} was {@code null}.
     */
    public SchemaBuilder addDITContentRule(final String definition, final boolean overwrite) {
        Reject.ifNull(definition);

        lazyInitBuilder();

        try {
            final SubstringReader reader = new SubstringReader(definition);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // This means that the value was empty or contained only
                // whitespace. That is illegal.
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_DCR_EMPTY_VALUE1.get(definition));
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                final LocalizableMessage message = ERR_ATTR_SYNTAX_DCR_EXPECTED_OPEN_PARENTHESIS.get(
                    definition, reader.pos() - 1, String.valueOf(c));
                throw new LocalizedIllegalArgumentException(message);
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters must be the OID.
            final DITContentRule.Builder contentRuleBuilder =
                    buildDITContentRule(readOID(reader, allowsMalformedNamesAndOptions()));
            contentRuleBuilder.definition(definition);

            // At this point, we should have a pretty specific syntax that
            // describes what may come next, but some of the components are
            // optional and it would be pretty easy to put something in the
            // wrong order, so we will be very flexible about what we can
            // accept. Just look at the next token, figure out what it is and
            // how to treat what comes after it, then repeat until we get to
            // the end of the value. But before we start, set default values
            // for everything else we might need to know.
            while (true) {
                final String tokenName = readTokenName(reader);

                if (tokenName == null) {
                    // No more tokens.
                    break;
                } else if ("name".equalsIgnoreCase(tokenName)) {
                    contentRuleBuilder.names(readNameDescriptors(reader, allowsMalformedNamesAndOptions()));
                } else if ("desc".equalsIgnoreCase(tokenName)) {
                    // This specifies the description for the attribute type. It
                    // is an arbitrary string of characters enclosed in single
                    // quotes.
                    contentRuleBuilder.description(readQuotedString(reader));
                } else if ("obsolete".equalsIgnoreCase(tokenName)) {
                    // This indicates whether the attribute type should be
                    // considered obsolete.
                    contentRuleBuilder.obsolete(true);
                } else if ("aux".equalsIgnoreCase(tokenName)) {
                    contentRuleBuilder.auxiliaryObjectClasses(readOIDs(reader, allowsMalformedNamesAndOptions()));
                } else if ("must".equalsIgnoreCase(tokenName)) {
                    contentRuleBuilder.requiredAttributes(readOIDs(reader, allowsMalformedNamesAndOptions()));
                } else if ("may".equalsIgnoreCase(tokenName)) {
                    contentRuleBuilder.optionalAttributes(readOIDs(reader, allowsMalformedNamesAndOptions()));
                } else if ("not".equalsIgnoreCase(tokenName)) {
                    contentRuleBuilder.prohibitedAttributes(readOIDs(reader, allowsMalformedNamesAndOptions()));
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    contentRuleBuilder.extraProperties(tokenName, readExtensions(reader));
                } else {
                    throw new LocalizedIllegalArgumentException(
                        ERR_ATTR_SYNTAX_DCR_ILLEGAL_TOKEN1.get(definition, tokenName));
                }
            }

            return overwrite ? contentRuleBuilder.addToSchemaOverwrite() : contentRuleBuilder.addToSchema();
        } catch (final DecodeException e) {
            final LocalizableMessage msg = ERR_ATTR_SYNTAX_DCR_INVALID1.get(definition, e.getMessageObject());
            throw new LocalizedIllegalArgumentException(msg, e.getCause());
        }
    }

    /**
     * Adds the provided DIT structure rule definition to this schema builder.
     *
     * @param definition
     *            The DIT structure rule definition.
     * @param overwrite
     *            {@code true} if any existing DIT structure rule with the same
     *            OID should be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     * @throws LocalizedIllegalArgumentException
     *             If the provided DIT structure rule definition could not be
     *             parsed.
     * @throws NullPointerException
     *             If {@code definition} was {@code null}.
     */
    public SchemaBuilder addDITStructureRule(final String definition, final boolean overwrite) {
        Reject.ifNull(definition);

        lazyInitBuilder();

        try {
            final SubstringReader reader = new SubstringReader(definition);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // This means that the value was empty or contained only
                // whitespace. That is illegal.
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_DSR_EMPTY_VALUE1.get(definition));
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                final LocalizableMessage message = ERR_ATTR_SYNTAX_DSR_EXPECTED_OPEN_PARENTHESIS.get(
                    definition, reader.pos() - 1, String.valueOf(c));
                throw new LocalizedIllegalArgumentException(message);
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters must be the OID.
            final DITStructureRule.Builder ruleBuilder = new DITStructureRule.Builder(readRuleID(reader), this);
            String nameForm = null;

            // At this point, we should have a pretty specific syntax that
            // describes what may come next, but some of the components are
            // optional and it would be pretty easy to put something in the
            // wrong order, so we will be very flexible about what we can
            // accept. Just look at the next token, figure out what it is and
            // how to treat what comes after it, then repeat until we get to
            // the end of the value. But before we start, set default values
            // for everything else we might need to know.
            while (true) {
                final String tokenName = readTokenName(reader);

                if (tokenName == null) {
                    // No more tokens.
                    break;
                } else if ("name".equalsIgnoreCase(tokenName)) {
                    ruleBuilder.names(readNameDescriptors(reader, allowsMalformedNamesAndOptions()));
                } else if ("desc".equalsIgnoreCase(tokenName)) {
                    // This specifies the description for the attribute type. It
                    // is an arbitrary string of characters enclosed in single
                    // quotes.
                    ruleBuilder.description(readQuotedString(reader));
                } else if ("obsolete".equalsIgnoreCase(tokenName)) {
                    // This indicates whether the attribute type should be
                    // considered obsolete.
                    ruleBuilder.obsolete(true);
                } else if ("form".equalsIgnoreCase(tokenName)) {
                    nameForm = readOID(reader, allowsMalformedNamesAndOptions());
                } else if ("sup".equalsIgnoreCase(tokenName)) {
                    ruleBuilder.superiorRules(readRuleIDs(reader));
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    ruleBuilder.extraProperties(tokenName, readExtensions(reader));
                } else {
                    throw new LocalizedIllegalArgumentException(
                            ERR_ATTR_SYNTAX_DSR_ILLEGAL_TOKEN1.get(definition, tokenName));
                }
            }

            if (nameForm == null) {
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_DSR_NO_NAME_FORM.get(definition));
            }
            ruleBuilder.nameForm(nameForm);

            return overwrite ? ruleBuilder.addToSchemaOverwrite() : ruleBuilder.addToSchema();
        } catch (final DecodeException e) {
            final LocalizableMessage msg = ERR_ATTR_SYNTAX_DSR_INVALID1.get(definition, e.getMessageObject());
            throw new LocalizedIllegalArgumentException(msg, e.getCause());
        }
    }

    /**
     * Adds the provided enumeration syntax definition to this schema builder.
     *
     * @param oid
     *            The OID of the enumeration syntax definition.
     * @param description
     *            The description of the enumeration syntax definition.
     * @param overwrite
     *            {@code true} if any existing syntax with the same OID should
     *            be overwritten.
     * @param enumerations
     *            The range of values which attribute values must match in order
     *            to be valid.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     */
    public SchemaBuilder addEnumerationSyntax(final String oid, final String description,
            final boolean overwrite, final String... enumerations) {
        Reject.ifNull((Object) enumerations);

        lazyInitBuilder();

        final EnumSyntaxImpl enumImpl = new EnumSyntaxImpl(oid, Arrays.asList(enumerations));

        final Syntax.Builder syntaxBuilder = buildSyntax(oid).description(description)
                .extraProperties(Collections.singletonMap("X-ENUM", Arrays.asList(enumerations)))
                .implementation(enumImpl);

        syntaxBuilder.addToSchema(overwrite);

        try {
            buildMatchingRule(enumImpl.getOrderingMatchingRule())
                    .names(OMR_GENERIC_ENUM_NAME + oid)
                    .syntaxOID(oid)
                    .extraProperties(CoreSchemaImpl.OPENDS_ORIGIN)
                    .implementation(new EnumOrderingMatchingRule(enumImpl))
                    .addToSchemaOverwrite();
        } catch (final ConflictingSchemaElementException e) {
            removeSyntax(oid);
        }
        return this;
    }

    /**
     * Adds the provided matching rule definition to this schema builder.
     *
     * @param definition
     *            The matching rule definition.
     * @param overwrite
     *            {@code true} if any existing matching rule with the same OID
     *            should be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     * @throws LocalizedIllegalArgumentException
     *             If the provided matching rule definition could not be parsed.
     * @throws NullPointerException
     *             If {@code definition} was {@code null}.
     */
    public SchemaBuilder addMatchingRule(final String definition, final boolean overwrite) {
        Reject.ifNull(definition);

        lazyInitBuilder();

        try {
            final SubstringReader reader = new SubstringReader(definition);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // This means that the value was empty or contained only
                // whitespace. That is illegal.
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_MR_EMPTY_VALUE1.get(definition));
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                final LocalizableMessage message = ERR_ATTR_SYNTAX_MR_EXPECTED_OPEN_PARENTHESIS.get(
                    definition, reader.pos() - 1, String.valueOf(c));
                throw new LocalizedIllegalArgumentException(message);
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters must be the OID.
            final MatchingRule.Builder matchingRuleBuilder = new MatchingRule.Builder(
                readOID(reader, allowsMalformedNamesAndOptions()), this);
            matchingRuleBuilder.definition(definition);

            String syntax = null;
            // At this point, we should have a pretty specific syntax that
            // describes what may come next, but some of the components are
            // optional and it would be pretty easy to put something in the
            // wrong order, so we will be very flexible about what we can
            // accept. Just look at the next token, figure out what it is and
            // how to treat what comes after it, then repeat until we get to
            // the end of the value. But before we start, set default values
            // for everything else we might need to know.
            while (true) {
                final String tokenName = readTokenName(reader);

                if (tokenName == null) {
                    // No more tokens.
                    break;
                } else if ("name".equalsIgnoreCase(tokenName)) {
                    matchingRuleBuilder.names(readNameDescriptors(reader, allowsMalformedNamesAndOptions()));
                } else if ("desc".equalsIgnoreCase(tokenName)) {
                    // This specifies the description for the matching rule. It
                    // is an arbitrary string of characters enclosed in single
                    // quotes.
                    matchingRuleBuilder.description(readQuotedString(reader));
                } else if ("obsolete".equalsIgnoreCase(tokenName)) {
                    // This indicates whether the matching rule should be
                    // considered obsolete. We do not need to do any more
                    // parsing for this token.
                    matchingRuleBuilder.obsolete(true);
                } else if ("syntax".equalsIgnoreCase(tokenName)) {
                    syntax = readOID(reader, allowsMalformedNamesAndOptions());
                    matchingRuleBuilder.syntaxOID(syntax);
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    final List<String> extensions = readExtensions(reader);
                    matchingRuleBuilder.extraProperties(tokenName, extensions.toArray(new String[extensions.size()]));
                } else {
                    throw new LocalizedIllegalArgumentException(
                        ERR_ATTR_SYNTAX_MR_ILLEGAL_TOKEN1.get(definition, tokenName));
                }
            }

            // Make sure that a syntax was specified.
            if (syntax == null) {
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_MR_NO_SYNTAX.get(definition));
            }
            if (overwrite) {
                matchingRuleBuilder.addToSchemaOverwrite();
            } else {
                matchingRuleBuilder.addToSchema();
            }
        } catch (final DecodeException e) {
            final LocalizableMessage msg =
                    ERR_ATTR_SYNTAX_MR_INVALID1.get(definition, e.getMessageObject());
            throw new LocalizedIllegalArgumentException(msg, e.getCause());
        }
        return this;
    }

    /**
     * Adds the provided matching rule use definition to this schema builder.
     *
     * @param definition
     *            The matching rule use definition.
     * @param overwrite
     *            {@code true} if any existing matching rule use with the same
     *            OID should be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     * @throws LocalizedIllegalArgumentException
     *             If the provided matching rule use definition could not be
     *             parsed.
     * @throws NullPointerException
     *             If {@code definition} was {@code null}.
     */
    public SchemaBuilder addMatchingRuleUse(final String definition, final boolean overwrite) {
        Reject.ifNull(definition);

        lazyInitBuilder();

        try {
            final SubstringReader reader = new SubstringReader(definition);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // This means that the value was empty or contained only
                // whitespace. That is illegal.
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_MRUSE_EMPTY_VALUE1.get(definition));
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                final LocalizableMessage message = ERR_ATTR_SYNTAX_MRUSE_EXPECTED_OPEN_PARENTHESIS.get(
                    definition, reader.pos() - 1, String.valueOf(c));
                throw new LocalizedIllegalArgumentException(message);
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters must be the OID.
            final MatchingRuleUse.Builder useBuilder =
                    buildMatchingRuleUse(readOID(reader, allowsMalformedNamesAndOptions()));
            Set<String> attributes = null;

            // At this point, we should have a pretty specific syntax that
            // describes what may come next, but some of the components are
            // optional and it would be pretty easy to put something in the
            // wrong order, so we will be very flexible about what we can
            // accept. Just look at the next token, figure out what it is and
            // how to treat what comes after it, then repeat until we get to
            // the end of the value. But before we start, set default values
            // for everything else we might need to know.
            while (true) {
                final String tokenName = readTokenName(reader);

                if (tokenName == null) {
                    // No more tokens.
                    break;
                } else if ("name".equalsIgnoreCase(tokenName)) {
                    useBuilder.names(readNameDescriptors(reader, allowsMalformedNamesAndOptions()));
                } else if ("desc".equalsIgnoreCase(tokenName)) {
                    // This specifies the description for the attribute type. It
                    // is an arbitrary string of characters enclosed in single
                    // quotes.
                    useBuilder.description(readQuotedString(reader));
                } else if ("obsolete".equalsIgnoreCase(tokenName)) {
                    // This indicates whether the attribute type should be
                    // considered obsolete.
                    useBuilder.obsolete(true);
                } else if ("applies".equalsIgnoreCase(tokenName)) {
                    attributes = readOIDs(reader, allowsMalformedNamesAndOptions());
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    useBuilder.extraProperties(tokenName, readExtensions(reader));
                } else {
                    throw new LocalizedIllegalArgumentException(
                        ERR_ATTR_SYNTAX_MRUSE_ILLEGAL_TOKEN1.get(definition, tokenName));
                }
            }

            // Make sure that the set of attributes was defined.
            if (attributes == null || attributes.size() == 0) {
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_MRUSE_NO_ATTR.get(definition));
            }
            useBuilder.attributes(attributes);

            return overwrite ? useBuilder.addToSchemaOverwrite() : useBuilder.addToSchema();
        } catch (final DecodeException e) {
            final LocalizableMessage msg = ERR_ATTR_SYNTAX_MRUSE_INVALID1.get(definition, e.getMessageObject());
            throw new LocalizedIllegalArgumentException(msg, e.getCause());
        }
    }

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * attribute type before adding it to the schema. Example usage:
     *
     * <pre>
     * SchemaBuilder builder = ...;
     * builder.buildAttributeType("attributetype-oid").name("attribute type name").addToSchema();
     * </pre>
     *
     * @param oid
     *            The OID of the attribute type definition.
     * @return A builder to continue building the AttributeType.
     */
    public AttributeType.Builder buildAttributeType(final String oid) {
        lazyInitBuilder();
        return new AttributeType.Builder(oid, this);
    }

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * DIT structure rule before adding it to the schema. Example usage:
     *
     * <pre>
     * SchemaBuilder builder = ...;
     * final int myRuleID = ...;
     * builder.buildDITStructureRule(myRuleID).name("DIT structure rule name").addToSchema();
     * </pre>
     *
     * @param ruleID
     *            The ID of the DIT structure rule.
     * @return A builder to continue building the DITStructureRule.
     */
    public DITStructureRule.Builder buildDITStructureRule(final int ruleID) {
        lazyInitBuilder();
        return new DITStructureRule.Builder(ruleID, this);
    }

    /**
     * Returns a builder which can be used for incrementally constructing a new matching rule before adding it to the
     * schema. Example usage:
     *
     * <pre>
     * SchemaBuilder builder = ...;
     * builder.buildMatchingRule("matchingrule-oid").name("matching rule name").addToSchema();
     * </pre>
     *
     * @param oid
     *            The OID of the matching rule definition.
     * @return A builder to continue building the MatchingRule.
     */
    public MatchingRule.Builder buildMatchingRule(final String oid) {
        lazyInitBuilder();
        return new MatchingRule.Builder(oid, this);
    }

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * matching rule use before adding it to the schema. Example usage:
     *
     * <pre>
     * SchemaBuilder builder = ...;
     * builder.buildMatchingRuleUse("matchingrule-oid")
     *        .name("matching rule use name")
     *        .addToSchema();
     * </pre>
     *
     * @param oid
     *            The OID of the matching rule definition.
     * @return A builder to continue building the MatchingRuleUse.
     */
    public MatchingRuleUse.Builder buildMatchingRuleUse(final String oid) {
        lazyInitBuilder();
        return new MatchingRuleUse.Builder(oid, this);
    }

    /**
     * Adds the provided name form definition to this schema builder.
     *
     * @param definition
     *            The name form definition.
     * @param overwrite
     *            {@code true} if any existing name form with the same OID
     *            should be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     * @throws LocalizedIllegalArgumentException
     *             If the provided name form definition could not be parsed.
     * @throws NullPointerException
     *             If {@code definition} was {@code null}.
     */
    public SchemaBuilder addNameForm(final String definition, final boolean overwrite) {
        Reject.ifNull(definition);

        lazyInitBuilder();

        try {
            final SubstringReader reader = new SubstringReader(definition);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // This means that the value was empty or contained only
                // whitespace. That is illegal.
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_NAME_FORM_EMPTY_VALUE1.get(definition));
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                final LocalizableMessage message = ERR_ATTR_SYNTAX_NAME_FORM_EXPECTED_OPEN_PARENTHESIS.get(
                    definition, reader.pos() - 1, c);
                throw new LocalizedIllegalArgumentException(message);
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters must be the OID.
            final NameForm.Builder nameFormBuilder = new NameForm.Builder(
                readOID(reader, allowsMalformedNamesAndOptions()), this);
            nameFormBuilder.definition(definition);

            // Required properties :
            String structuralOID = null;
            Collection<String> requiredAttributes = Collections.emptyList();

            // At this point, we should have a pretty specific syntax that
            // describes what may come next, but some of the components are
            // optional and it would be pretty easy to put something in the
            // wrong order, so we will be very flexible about what we can
            // accept. Just look at the next token, figure out what it is and
            // how to treat what comes after it, then repeat until we get to
            // the end of the value. But before we start, set default values
            // for everything else we might need to know.
            while (true) {
                final String tokenName = readTokenName(reader);

                if (tokenName == null) {
                    // No more tokens.
                    break;
                } else if ("name".equalsIgnoreCase(tokenName)) {
                    nameFormBuilder.names(readNameDescriptors(reader, allowsMalformedNamesAndOptions()));
                } else if ("desc".equalsIgnoreCase(tokenName)) {
                    // This specifies the description for the attribute type. It
                    // is an arbitrary string of characters enclosed in single
                    // quotes.
                    nameFormBuilder.description(readQuotedString(reader));
                } else if ("obsolete".equalsIgnoreCase(tokenName)) {
                    // This indicates whether the attribute type should be
                    // considered obsolete. We do not need to do any more
                    // parsing for this token.
                    nameFormBuilder.obsolete(true);
                } else if ("oc".equalsIgnoreCase(tokenName)) {
                    structuralOID = readOID(reader, allowsMalformedNamesAndOptions());
                    nameFormBuilder.structuralObjectClassOID(structuralOID);
                } else if ("must".equalsIgnoreCase(tokenName)) {
                    requiredAttributes = readOIDs(reader, allowsMalformedNamesAndOptions());
                    nameFormBuilder.requiredAttributes(requiredAttributes);
                } else if ("may".equalsIgnoreCase(tokenName)) {
                    nameFormBuilder.optionalAttributes(readOIDs(reader, allowsMalformedNamesAndOptions()));
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    final List<String> extensions = readExtensions(reader);
                    nameFormBuilder.extraProperties(tokenName, extensions.toArray(new String[extensions.size()]));
                } else {
                    throw new LocalizedIllegalArgumentException(
                        ERR_ATTR_SYNTAX_NAME_FORM_ILLEGAL_TOKEN1.get(definition, tokenName));
                }
            }

            // Make sure that a structural class was specified. If not, then
            // it cannot be valid and the name form cannot be build.
            if (structuralOID == null) {
                throw new LocalizedIllegalArgumentException(
                    ERR_ATTR_SYNTAX_NAME_FORM_NO_STRUCTURAL_CLASS1.get(definition));
            }

            if (requiredAttributes.isEmpty()) {
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_NAME_FORM_NO_REQUIRED_ATTR.get(definition));
            }

            if (overwrite) {
                nameFormBuilder.addToSchemaOverwrite();
            } else {
                nameFormBuilder.addToSchema();
            }
        } catch (final DecodeException e) {
            final LocalizableMessage msg =
                    ERR_ATTR_SYNTAX_NAME_FORM_INVALID1.get(definition, e.getMessageObject());
            throw new LocalizedIllegalArgumentException(msg, e.getCause());
        }
        return this;
    }

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * DIT content rule before adding it to the schema. Example usage:
     *
     * <pre>
     * SchemaBuilder builder = ...;
     * builder.buildDITContentRule("structuralobjectclass-oid").name("DIT content rule name").addToSchema();
     * </pre>
     *
     * @param structuralClassOID
     *            The OID of the structural objectclass for the DIT content rule to build.
     * @return A builder to continue building the DITContentRule.
     */
    public Builder buildDITContentRule(String structuralClassOID) {
        lazyInitBuilder();
        return new DITContentRule.Builder(structuralClassOID, this);
    }

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * name form before adding it to the schema. Example usage:
     *
     * <pre>
     * SchemaBuilder builder = ...;
     * builder.buildNameForm("1.2.3.4").name("myNameform").addToSchema();
     * </pre>
     *
     * @param oid
     *            The OID of the name form definition.
     * @return A builder to continue building the NameForm.
     */
    public NameForm.Builder buildNameForm(final String oid) {
        lazyInitBuilder();
        return new NameForm.Builder(oid, this);
    }

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * object class before adding it to the schema. Example usage:
     *
     * <pre>
     * SchemaBuilder builder = ...;
     * builder.buildObjectClass("objectclass-oid").name("object class name").addToSchema();
     * </pre>
     *
     * @param oid
     *            The OID of the object class definition.
     * @return A builder to continue building the ObjectClass.
     */
    public ObjectClass.Builder buildObjectClass(final String oid) {
        lazyInitBuilder();
        return new ObjectClass.Builder(oid, this);
    }

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * syntax before adding it to the schema. Example usage:
     *
     * <pre>
     * SchemaBuilder builder = ...;
     * builder.buildSyntax("1.2.3.4").addToSchema();
     * </pre>
     *
     * @param oid
     *            The OID of the syntax definition.
     * @return A builder to continue building the syntax.
     */
    public Syntax.Builder buildSyntax(final String oid) {
        lazyInitBuilder();
        return new Syntax.Builder(oid, this);
    }

    /**
     * Returns an attribute type builder whose fields are initialized to the
     * values of the provided attribute type. This method should be used when
     * duplicating attribute types from external schemas or when modifying
     * existing attribute types.
     *
     * @param attributeType
     *            The attribute type source.
     * @return A builder to continue building the AttributeType.
     */
    public AttributeType.Builder buildAttributeType(final AttributeType attributeType) {
        lazyInitBuilder();
        return new AttributeType.Builder(attributeType, this);
    }

    /**
     * Returns a DIT content rule builder whose fields are initialized to the
     * values of the provided DIT content rule. This method should be used when
     * duplicating DIT content rules from external schemas or when modifying
     * existing DIT content rules.
     *
     * @param contentRule
     *            The DIT content rule source.
     * @return A builder to continue building the DITContentRule.
     */
    public DITContentRule.Builder buildDITContentRule(DITContentRule contentRule) {
        lazyInitBuilder();
        return new DITContentRule.Builder(contentRule, this);
    }

    /**
     * Returns an DIT structure rule builder whose fields are initialized to the
     * values of the provided rule. This method should be used when duplicating
     * structure rules from external schemas or when modifying existing
     * structure rules.
     *
     * @param structureRule
     *            The DIT structure rule source.
     * @return A builder to continue building the DITStructureRule.
     */
    public DITStructureRule.Builder buildDITStructureRule(final DITStructureRule structureRule) {
        lazyInitBuilder();
        return new DITStructureRule.Builder(structureRule, this);
    }

    /**
     * Returns a matching rule builder whose fields are initialized to the
     * values of the provided matching rule. This method should be used when
     * duplicating matching rules from external schemas or when modifying
     * existing matching rules.
     *
     * @param matchingRule
     *            The matching rule source.
     * @return A builder to continue building the MatchingRule.
     */
    public MatchingRule.Builder buildMatchingRule(final MatchingRule matchingRule) {
        return buildMatchingRule(matchingRule, true);
    }

    private MatchingRule.Builder buildMatchingRule(final MatchingRule matchingRule, final boolean initialize) {
        if (initialize) {
            lazyInitBuilder();
        }
        return new MatchingRule.Builder(matchingRule, this);
    }

    /**
     * Returns a matching rule use builder whose fields are initialized to the
     * values of the provided matching rule use object. This method should be used when
     * duplicating matching rule uses from external schemas or when modifying
     * existing matching rule uses.
     *
     * @param matchingRuleUse
     *            The matching rule use source.
     * @return A builder to continue building the MatchingRuleUse.
     */
    public MatchingRuleUse.Builder buildMatchingRuleUse(final MatchingRuleUse matchingRuleUse) {
        lazyInitBuilder();
        return new MatchingRuleUse.Builder(matchingRuleUse, this);
    }

    /**
     * Returns a name form builder whose fields are initialized to the
     * values of the provided name form. This method should be used when
     * duplicating name forms from external schemas or when modifying
     * existing names forms.
     *
     * @param nameForm
     *            The name form source.
     * @return A builder to continue building the NameForm.
     */
    public NameForm.Builder buildNameForm(final NameForm nameForm) {
        lazyInitBuilder();
        return new NameForm.Builder(nameForm, this);
    }

    /**
     * Returns an object class builder whose fields are initialized to the
     * values of the provided object class. This method should be used when
     * duplicating object classes from external schemas or when modifying
     * existing object classes.
     *
     * @param objectClass
     *            The object class source.
     * @return A builder to continue building the ObjectClass.
     */
    public ObjectClass.Builder buildObjectClass(final ObjectClass objectClass) {
        lazyInitBuilder();
        return new ObjectClass.Builder(objectClass, this);
    }

    /**
     * Returns a syntax builder whose fields are initialized to the
     * values of the provided syntax. This method should be used when
     * duplicating syntaxes from external schemas or when modifying
     * existing syntaxes.
     *
     * @param syntax
     *            The syntax source.
     * @return A builder to continue building the Syntax.
     */
    public Syntax.Builder buildSyntax(final Syntax syntax) {
        return buildSyntax(syntax, true);
    }

    private Syntax.Builder buildSyntax(final Syntax syntax, final boolean initialize) {
        if (initialize) {
            lazyInitBuilder();
        }
        return new Syntax.Builder(syntax, this);
    }

    /**
     * Adds the provided object class definition to this schema builder.
     *
     * @param definition
     *            The object class definition.
     * @param overwrite
     *            {@code true} if any existing object class with the same OID
     *            should be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     * @throws LocalizedIllegalArgumentException
     *             If the provided object class definition could not be parsed.
     * @throws NullPointerException
     *             If {@code definition} was {@code null}.
     */
    public SchemaBuilder addObjectClass(final String definition, final boolean overwrite) {
        Reject.ifNull(definition);

        lazyInitBuilder();

        try {
            final SubstringReader reader = new SubstringReader(definition);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // This means that the value was empty or contained only
                // whitespace. That is illegal.
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_OBJECTCLASS_EMPTY_VALUE1.get(definition));
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                final LocalizableMessage message =  ERR_ATTR_SYNTAX_OBJECTCLASS_EXPECTED_OPEN_PARENTHESIS1.get(
                            definition, reader.pos() - 1, String.valueOf(c));
                throw new LocalizedIllegalArgumentException(message);
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters is the OID.
            final String oid = readOID(reader, allowsMalformedNamesAndOptions());
            Set<String> superiorClasses = emptySet();
            ObjectClassType ocType = null;
            ObjectClass.Builder ocBuilder = new ObjectClass.Builder(oid, this).definition(definition);

            // At this point, we should have a pretty specific syntax that
            // describes what may come next, but some of the components are
            // optional and it would be pretty easy to put something in the
            // wrong order, so we will be very flexible about what we can
            // accept. Just look at the next token, figure out what it is and
            // how to treat what comes after it, then repeat until we get to
            // the end of the value. But before we start, set default values
            // for everything else we might need to know.
            while (true) {
                final String tokenName = readTokenName(reader);

                if (tokenName == null) {
                    // No more tokens.
                    break;
                } else if ("name".equalsIgnoreCase(tokenName)) {
                    ocBuilder.names(readNameDescriptors(reader, allowsMalformedNamesAndOptions()));
                } else if ("desc".equalsIgnoreCase(tokenName)) {
                    // This specifies the description for the attribute type. It
                    // is an arbitrary string of characters enclosed in single
                    // quotes.
                    ocBuilder.description(readQuotedString(reader));
                } else if ("obsolete".equalsIgnoreCase(tokenName)) {
                    // This indicates whether the attribute type should be
                    // considered obsolete.
                    ocBuilder.obsolete(true);
                } else if ("sup".equalsIgnoreCase(tokenName)) {
                    superiorClasses = readOIDs(reader, allowsMalformedNamesAndOptions());
                } else if ("abstract".equalsIgnoreCase(tokenName)) {
                    // This indicates that entries must not include this
                    // objectclass unless they also include a non-abstract
                    // objectclass that inherits from this class.
                    ocType = ABSTRACT;
                } else if ("structural".equalsIgnoreCase(tokenName)) {
                    ocType = STRUCTURAL;
                } else if ("auxiliary".equalsIgnoreCase(tokenName)) {
                    ocType = AUXILIARY;
                } else if ("must".equalsIgnoreCase(tokenName)) {
                    ocBuilder.requiredAttributes(readOIDs(reader, allowsMalformedNamesAndOptions()));
                } else if ("may".equalsIgnoreCase(tokenName)) {
                    ocBuilder.optionalAttributes(readOIDs(reader, allowsMalformedNamesAndOptions()));
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    final List<String> extensions = readExtensions(reader);
                    ocBuilder.extraProperties(tokenName, extensions.toArray(new String[extensions.size()]));
                } else {
                    throw new LocalizedIllegalArgumentException(
                        ERR_ATTR_SYNTAX_OBJECTCLASS_ILLEGAL_TOKEN1.get(definition, tokenName));
                }
            }

            if (EXTENSIBLE_OBJECT_OBJECTCLASS_OID.equals(oid)) {
                addObjectClass(newExtensibleObjectObjectClass(
                    ocBuilder.getDescription(), ocBuilder.getExtraProperties(), this), overwrite);
                return this;
            } else {
                if (ocType == STRUCTURAL && superiorClasses.isEmpty()) {
                    superiorClasses = singleton(TOP_OBJECTCLASS_NAME);
                }
                ocBuilder.superiorObjectClasses(superiorClasses)
                         .type(ocType);
                return overwrite ? ocBuilder.addToSchemaOverwrite() : ocBuilder.addToSchema();
            }
        } catch (final DecodeException e) {
            throw new LocalizedIllegalArgumentException(
                ERR_ATTR_SYNTAX_OBJECTCLASS_INVALID1.get(definition, e.getMessageObject()), e.getCause());
        }
    }

    /**
     * Adds the provided pattern syntax definition to this schema builder.
     *
     * @param oid
     *            The OID of the pattern syntax definition.
     * @param description
     *            The description of the pattern syntax definition.
     * @param pattern
     *            The regular expression pattern which attribute values must
     *            match in order to be valid.
     * @param overwrite
     *            {@code true} if any existing syntax with the same OID should
     *            be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     */
    public SchemaBuilder addPatternSyntax(final String oid, final String description,
            final Pattern pattern, final boolean overwrite) {
        Reject.ifNull(pattern);

        lazyInitBuilder();

        final Syntax.Builder syntaxBuilder = buildSyntax(oid).description(description).extraProperties(
                Collections.singletonMap("X-PATTERN", Collections.singletonList(pattern.toString())));

        syntaxBuilder.addToSchema(overwrite);

        return this;
    }

    /**
     * Reads the schema elements contained in the named subschema sub-entry and
     * adds them to this schema builder.
     * <p>
     * If the requested schema is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}.
     *
     * @param connection
     *            A connection to the Directory Server whose schema is to be
     *            read.
     * @param name
     *            The distinguished name of the subschema sub-entry.
     * @param overwrite
     *            {@code true} if existing schema elements with the same
     *            conflicting OIDs should be overwritten.
     * @return A reference to this schema builder.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If the connection does not support search operations.
     * @throws IllegalStateException
     *             If the connection has already been closed, i.e. if
     *             {@code isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code connection} or {@code name} was {@code null}.
     */
    public SchemaBuilder addSchema(final Connection connection, final DN name,
            final boolean overwrite) throws LdapException {
        // The call to addSchema will perform copyOnWrite.
        final SearchRequest request = getReadSchemaSearchRequest(name);
        final Entry entry = connection.searchSingleEntry(request);
        return addSchema(entry, overwrite);
    }

    /**
     * Adds all of the schema elements contained in the provided subschema
     * subentry to this schema builder. Any problems encountered while parsing
     * the entry can be retrieved using the returned schema's
     * {@link Schema#getWarnings()} method.
     *
     * @param entry
     *            The subschema subentry to be parsed.
     * @param overwrite
     *            {@code true} if existing schema elements with the same
     *            conflicting OIDs should be overwritten.
     * @return A reference to this schema builder.
     * @throws NullPointerException
     *             If {@code entry} was {@code null}.
     */
    public SchemaBuilder addSchema(final Entry entry, final boolean overwrite) {
        Reject.ifNull(entry);

        lazyInitBuilder();

        Attribute attr = entry.getAttribute(Schema.ATTR_LDAP_SYNTAXES);
        if (attr != null) {
            for (final ByteString def : attr) {
                try {
                    addSyntax(def.toString(), overwrite);
                } catch (final LocalizedIllegalArgumentException e) {
                    warnings.add(e.getMessageObject());
                }
            }
        }

        attr = entry.getAttribute(Schema.ATTR_ATTRIBUTE_TYPES);
        if (attr != null) {
            for (final ByteString def : attr) {
                try {
                    addAttributeType(def.toString(), overwrite);
                } catch (final LocalizedIllegalArgumentException e) {
                    warnings.add(e.getMessageObject());
                }
            }
        }

        attr = entry.getAttribute(Schema.ATTR_OBJECT_CLASSES);
        if (attr != null) {
            for (final ByteString def : attr) {
                try {
                    addObjectClass(def.toString(), overwrite);
                } catch (final LocalizedIllegalArgumentException e) {
                    warnings.add(e.getMessageObject());
                }
            }
        }

        attr = entry.getAttribute(Schema.ATTR_MATCHING_RULE_USE);
        if (attr != null) {
            for (final ByteString def : attr) {
                try {
                    addMatchingRuleUse(def.toString(), overwrite);
                } catch (final LocalizedIllegalArgumentException e) {
                    warnings.add(e.getMessageObject());
                }
            }
        }

        attr = entry.getAttribute(Schema.ATTR_MATCHING_RULES);
        if (attr != null) {
            for (final ByteString def : attr) {
                try {
                    addMatchingRule(def.toString(), overwrite);
                } catch (final LocalizedIllegalArgumentException e) {
                    warnings.add(e.getMessageObject());
                }
            }
        }

        attr = entry.getAttribute(Schema.ATTR_DIT_CONTENT_RULES);
        if (attr != null) {
            for (final ByteString def : attr) {
                try {
                    addDITContentRule(def.toString(), overwrite);
                } catch (final LocalizedIllegalArgumentException e) {
                    warnings.add(e.getMessageObject());
                }
            }
        }

        attr = entry.getAttribute(Schema.ATTR_DIT_STRUCTURE_RULES);
        if (attr != null) {
            for (final ByteString def : attr) {
                try {
                    addDITStructureRule(def.toString(), overwrite);
                } catch (final LocalizedIllegalArgumentException e) {
                    warnings.add(e.getMessageObject());
                }
            }
        }

        attr = entry.getAttribute(Schema.ATTR_NAME_FORMS);
        if (attr != null) {
            for (final ByteString def : attr) {
                try {
                    addNameForm(def.toString(), overwrite);
                } catch (final LocalizedIllegalArgumentException e) {
                    warnings.add(e.getMessageObject());
                }
            }
        }

        return this;
    }

    /**
     * Adds all of the schema elements in the provided schema to this schema
     * builder.
     *
     * @param schema
     *            The schema to be copied into this schema builder.
     * @param overwrite
     *            {@code true} if existing schema elements with the same
     *            conflicting OIDs should be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and conflicting schema
     *             elements were found.
     * @throws NullPointerException
     *             If {@code schema} was {@code null}.
     */
    public SchemaBuilder addSchema(final Schema schema, final boolean overwrite) {
        Reject.ifNull(schema);

        lazyInitBuilder();

        addSchema0(schema, overwrite);
        return this;
    }

    /**
     * Asynchronously reads the schema elements contained in the named subschema
     * sub-entry and adds them to this schema builder.
     * <p>
     * If the requested schema is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}.
     *
     * @param connection
     *            A connection to the Directory Server whose schema is to be
     *            read.
     * @param name
     *            The distinguished name of the subschema sub-entry.
     *            the operation result when it is received, may be {@code null}.
     * @param overwrite
     *            {@code true} if existing schema elements with the same
     *            conflicting OIDs should be overwritten.
     * @return A promise representing the updated schema builder.
     * @throws UnsupportedOperationException
     *             If the connection does not support search operations.
     * @throws IllegalStateException
     *             If the connection has already been closed, i.e. if
     *             {@code connection.isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code connection} or {@code name} was {@code null}.
     */
    public LdapPromise<SchemaBuilder> addSchemaAsync(final Connection connection, final DN name,
        final boolean overwrite) {
        // The call to addSchema will perform copyOnWrite.
        return connection.searchSingleEntryAsync(getReadSchemaSearchRequest(name)).then(
                new Function<SearchResultEntry, SchemaBuilder, LdapException>() {
                    @Override
                    public SchemaBuilder apply(SearchResultEntry result) throws LdapException {
                        addSchema(result, overwrite);
                        return SchemaBuilder.this;
                    }
                });
    }

    /**
     * Reads the schema elements contained in the subschema sub-entry which
     * applies to the named entry and adds them to this schema builder.
     * <p>
     * If the requested entry or its associated schema are not returned by the
     * Directory Server then the request will fail with an
     * {@link EntryNotFoundException}.
     * <p>
     * This implementation first reads the {@code subschemaSubentry} attribute
     * of the entry in order to identify the schema and then invokes
     * {@link #addSchemaForEntry(Connection, DN, boolean)} to read the schema.
     *
     * @param connection
     *            A connection to the Directory Server whose schema is to be
     *            read.
     * @param name
     *            The distinguished name of the entry whose schema is to be
     *            located.
     * @param overwrite
     *            {@code true} if existing schema elements with the same
     *            conflicting OIDs should be overwritten.
     * @return A reference to this schema builder.
     * @throws LdapException
     *             If the result code indicates that the request failed for some
     *             reason.
     * @throws UnsupportedOperationException
     *             If the connection does not support search operations.
     * @throws IllegalStateException
     *             If the connection has already been closed, i.e. if
     *             {@code connection.isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code connection} or {@code name} was {@code null}.
     */
    public SchemaBuilder addSchemaForEntry(final Connection connection, final DN name,
            final boolean overwrite) throws LdapException {
        // The call to addSchema will perform copyOnWrite.
        final SearchRequest request = getReadSchemaForEntrySearchRequest(name);
        final Entry entry = connection.searchSingleEntry(request);
        final DN subschemaDN = getSubschemaSubentryDN(name, entry);
        return addSchema(connection, subschemaDN, overwrite);
    }

    /**
     * Asynchronously reads the schema elements contained in the subschema
     * sub-entry which applies to the named entry and adds them to this schema
     * builder.
     * <p>
     * If the requested entry or its associated schema are not returned by the
     * Directory Server then the request will fail with an
     * {@link EntryNotFoundException}.
     * <p>
     * This implementation first reads the {@code subschemaSubentry} attribute
     * of the entry in order to identify the schema and then invokes
     * {@link #addSchemaAsync(Connection, DN, boolean)} to read the schema.
     *
     * @param connection
     *            A connection to the Directory Server whose schema is to be
     *            read.
     * @param name
     *            The distinguished name of the entry whose schema is to be
     *            located.
     * @param overwrite
     *            {@code true} if existing schema elements with the same
     *            conflicting OIDs should be overwritten.
     * @return A promise representing the updated schema builder.
     * @throws UnsupportedOperationException
     *             If the connection does not support search operations.
     * @throws IllegalStateException
     *             If the connection has already been closed, i.e. if
     *             {@code connection.isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code connection} or {@code name} was {@code null}.
     */
    public LdapPromise<SchemaBuilder> addSchemaForEntryAsync(final Connection connection, final DN name,
            final boolean overwrite) {
        return connection.searchSingleEntryAsync(getReadSchemaForEntrySearchRequest(name)).thenAsync(
                new AsyncFunction<SearchResultEntry, SchemaBuilder, LdapException>() {
                    @Override
                    public Promise<SchemaBuilder, LdapException> apply(SearchResultEntry result) throws LdapException {
                        final DN subschemaDN = getSubschemaSubentryDN(name, result);
                        return addSchemaAsync(connection, subschemaDN, overwrite);
                    }
                });
    }

    /**
     * Adds the provided substitution syntax definition to this schema builder.
     *
     * @param oid
     *            The OID of the substitution syntax definition.
     * @param description
     *            The description of the substitution syntax definition.
     * @param substituteSyntax
     *            The OID of the syntax whose implementation should be
     *            substituted.
     * @param overwrite
     *            {@code true} if any existing syntax with the same OID should
     *            be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     */
    public SchemaBuilder addSubstitutionSyntax(final String oid, final String description,
            final String substituteSyntax, final boolean overwrite) {
        Reject.ifNull(substituteSyntax);

        lazyInitBuilder();

        final Syntax.Builder syntaxBuilder = buildSyntax(oid).description(description).extraProperties(
                Collections.singletonMap("X-SUBST", Collections.singletonList(substituteSyntax)));

        syntaxBuilder.addToSchema(overwrite);

        return this;
    }

    /**
     * Adds the provided syntax definition to this schema builder.
     *
     * @param definition
     *            The syntax definition.
     * @param overwrite
     *            {@code true} if any existing syntax with the same OID should
     *            be overwritten.
     * @return A reference to this schema builder.
     * @throws ConflictingSchemaElementException
     *             If {@code overwrite} was {@code false} and a conflicting
     *             schema element was found.
     * @throws LocalizedIllegalArgumentException
     *             If the provided syntax definition could not be parsed.
     * @throws NullPointerException
     *             If {@code definition} was {@code null}.
     */
    public SchemaBuilder addSyntax(final String definition, final boolean overwrite) {
        Reject.ifNull(definition);

        lazyInitBuilder();

        try {
            final SubstringReader reader = new SubstringReader(definition);

            // We'll do this a character at a time. First, skip over any
            // leading whitespace.
            reader.skipWhitespaces();

            if (reader.remaining() <= 0) {
                // This means that the value was empty or contained only
                // whitespace. That is illegal.
                throw new LocalizedIllegalArgumentException(ERR_ATTR_SYNTAX_ATTRSYNTAX_EMPTY_VALUE1.get(definition));
            }

            // The next character must be an open parenthesis. If it is not,
            // then that is an error.
            final char c = reader.read();
            if (c != '(') {
                final LocalizableMessage message = ERR_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_OPEN_PARENTHESIS.get(
                    definition, reader.pos() - 1, String.valueOf(c));
                throw new LocalizedIllegalArgumentException(message);
            }

            // Skip over any spaces immediately following the opening
            // parenthesis.
            reader.skipWhitespaces();

            // The next set of characters must be the OID.
            final String oid = readOID(reader, allowsMalformedNamesAndOptions());
            final Syntax.Builder syntaxBuilder = new Syntax.Builder(oid, this).definition(definition);

            // At this point, we should have a pretty specific syntax that
            // describes what may come next, but some of the components are
            // optional and it would be pretty easy to put something in the
            // wrong order, so we will be very flexible about what we can
            // accept. Just look at the next token, figure out what it is and
            // how to treat what comes after it, then repeat until we get to
            // the end of the value. But before we start, set default values
            // for everything else we might need to know.
            while (true) {
                final String tokenName = readTokenName(reader);

                if (tokenName == null) {
                    // No more tokens.
                    break;
                } else if ("desc".equalsIgnoreCase(tokenName)) {
                    // This specifies the description for the syntax. It is an
                    // arbitrary string of characters enclosed in single quotes.
                    syntaxBuilder.description(readQuotedString(reader));
                } else if (tokenName.matches("^X-[A-Za-z_-]+$")) {
                    // This must be a non-standard property and it must be
                    // followed by either a single definition in single quotes
                    // or an open parenthesis followed by one or more values in
                    // single quotes separated by spaces followed by a close
                    // parenthesis.
                    final List<String> extensions = readExtensions(reader);
                    syntaxBuilder.extraProperties(tokenName, extensions.toArray(new String[extensions.size()]));
                } else {
                    throw new LocalizedIllegalArgumentException(
                        ERR_ATTR_SYNTAX_ATTRSYNTAX_ILLEGAL_TOKEN1.get(definition, tokenName));
                }
            }

            // See if it is a enum syntax
            for (final Map.Entry<String, List<String>> property : syntaxBuilder.getExtraProperties().entrySet()) {
                if ("x-enum".equalsIgnoreCase(property.getKey())) {
                    final EnumSyntaxImpl enumImpl = new EnumSyntaxImpl(oid, property.getValue());
                    syntaxBuilder.implementation(enumImpl);
                    syntaxBuilder.addToSchema(overwrite);

                    buildMatchingRule(enumImpl.getOrderingMatchingRule())
                        .names(OMR_GENERIC_ENUM_NAME + oid)
                        .syntaxOID(oid)
                        .extraProperties(CoreSchemaImpl.OPENDS_ORIGIN)
                        .implementation(new EnumOrderingMatchingRule(enumImpl))
                        .addToSchemaOverwrite();
                    return this;
                }
            }

            syntaxBuilder.addToSchema(overwrite);
        } catch (final DecodeException e) {
            final LocalizableMessage msg =
                    ERR_ATTR_SYNTAX_ATTRSYNTAX_INVALID1.get(definition, e.getMessageObject());
            throw new LocalizedIllegalArgumentException(msg, e.getCause());
        }
        return this;
    }

    Options getOptions() {
        lazyInitBuilder();

        return options;
    }

    /**
     * Removes the named attribute type from this schema builder.
     *
     * @param name
     *            The name or OID of the attribute type to be removed.
     * @return {@code true} if the attribute type was found.
     */
    public boolean removeAttributeType(final String name) {
        lazyInitBuilder();

        final AttributeType element = numericOID2AttributeTypes.get(name);
        if (element != null) {
            removeAttributeType(element);
            return true;
        }
        final List<AttributeType> elements = name2AttributeTypes.get(toLowerCase(name));
        if (elements != null) {
            for (final AttributeType e : elements) {
                removeAttributeType(e);
            }
            return true;
        }
        return false;
    }

    /**
     * Removes the named DIT content rule from this schema builder.
     *
     * @param name
     *            The name or OID of the DIT content rule to be removed.
     * @return {@code true} if the DIT content rule was found.
     */
    public boolean removeDITContentRule(final String name) {
        lazyInitBuilder();

        final DITContentRule element = numericOID2ContentRules.get(name);
        if (element != null) {
            removeDITContentRule(element);
            return true;
        }
        final List<DITContentRule> elements = name2ContentRules.get(toLowerCase(name));
        if (elements != null) {
            for (final DITContentRule e : elements) {
                removeDITContentRule(e);
            }
            return true;
        }
        return false;
    }

    /**
     * Removes the specified DIT structure rule from this schema builder.
     *
     * @param ruleID
     *            The ID of the DIT structure rule to be removed.
     * @return {@code true} if the DIT structure rule was found.
     */
    public boolean removeDITStructureRule(final int ruleID) {
        lazyInitBuilder();

        final DITStructureRule element = id2StructureRules.get(ruleID);
        if (element != null) {
            removeDITStructureRule(element);
            return true;
        }
        return false;
    }

    /**
     * Removes the named matching rule from this schema builder.
     *
     * @param name
     *            The name or OID of the matching rule to be removed.
     * @return {@code true} if the matching rule was found.
     */
    public boolean removeMatchingRule(final String name) {
        lazyInitBuilder();

        final MatchingRule element = numericOID2MatchingRules.get(name);
        if (element != null) {
            removeMatchingRule(element);
            return true;
        }
        final List<MatchingRule> elements = name2MatchingRules.get(toLowerCase(name));
        if (elements != null) {
            for (final MatchingRule e : elements) {
                removeMatchingRule(e);
            }
            return true;
        }
        return false;
    }

    /**
     * Removes the named matching rule use from this schema builder.
     *
     * @param name
     *            The name or OID of the matching rule use to be removed.
     * @return {@code true} if the matching rule use was found.
     */
    public boolean removeMatchingRuleUse(final String name) {
        lazyInitBuilder();

        final MatchingRuleUse element = numericOID2MatchingRuleUses.get(name);
        if (element != null) {
            removeMatchingRuleUse(element);
            return true;
        }
        final List<MatchingRuleUse> elements = name2MatchingRuleUses.get(toLowerCase(name));
        if (elements != null) {
            for (final MatchingRuleUse e : elements) {
                removeMatchingRuleUse(e);
            }
            return true;
        }
        return false;
    }

    /**
     * Removes the named name form from this schema builder.
     *
     * @param name
     *            The name or OID of the name form to be removed.
     * @return {@code true} if the name form was found.
     */
    public boolean removeNameForm(final String name) {
        lazyInitBuilder();

        final NameForm element = numericOID2NameForms.get(name);
        if (element != null) {
            removeNameForm(element);
            return true;
        }
        final List<NameForm> elements = name2NameForms.get(toLowerCase(name));
        if (elements != null) {
            for (final NameForm e : elements) {
                removeNameForm(e);
            }
            return true;
        }
        return false;
    }

    /**
     * Removes the named object class from this schema builder.
     *
     * @param name
     *            The name or OID of the object class to be removed.
     * @return {@code true} if the object class was found.
     */
    public boolean removeObjectClass(final String name) {
        lazyInitBuilder();

        final ObjectClass element = numericOID2ObjectClasses.get(name);
        if (element != null) {
            removeObjectClass(element);
            return true;
        }
        final List<ObjectClass> elements = name2ObjectClasses.get(toLowerCase(name));
        if (elements != null) {
            for (final ObjectClass e : elements) {
                removeObjectClass(e);
            }
            return true;
        }
        return false;
    }

    /**
     * Removes the named syntax from this schema builder.
     *
     * @param numericOID
     *            The name of the syntax to be removed.
     * @return {@code true} if the syntax was found.
     */
    public boolean removeSyntax(final String numericOID) {
        lazyInitBuilder();

        final Syntax element = numericOID2Syntaxes.get(numericOID);
        if (element != null) {
            removeSyntax(element);
            return true;
        }
        return false;
    }

    /**
     * Sets a schema option overriding any previous values for the option.
     *
     * @param <T>
     *            The option type.
     * @param option
     *            Option with which the specified value is to be associated.
     * @param value
     *            Value to be associated with the specified option.
     * @return A reference to this schema builder.
     * @throws UnsupportedOperationException
     *             If the schema builder options are read only.
     */
    public <T> SchemaBuilder setOption(final Option<T> option, T value) {
        getOptions().set(option, value);
        return this;
    }

    /**
     * Returns a strict {@code Schema} containing all of the schema elements
     * contained in this schema builder as well as the same set of schema
     * compatibility options.
     * <p>
     * This method does not alter the contents of this schema builder.
     *
     * @return A {@code Schema} containing all of the schema elements contained
     *         in this schema builder as well as the same set of schema
     *         compatibility options
     */
    public Schema toSchema() {
        // If this schema builder was initialized from another schema and no
        // modifications have been made since then we can simply return the
        // original schema.
        if (copyOnWriteSchema != null) {
            return copyOnWriteSchema;
        }

        // We still need to ensure that this builder has been initialized
        // (otherwise some fields may still be null).
        lazyInitBuilder();

        final String localSchemaName;
        if (schemaName != null) {
            localSchemaName = schemaName;
        } else {
            localSchemaName = String.format("Schema#%d", NEXT_SCHEMA_ID.getAndIncrement());
        }

        Syntax defaultSyntax = numericOID2Syntaxes.get(options.get(DEFAULT_SYNTAX_OID));
        if (defaultSyntax == null) {
            defaultSyntax = Schema.getCoreSchema().getDefaultSyntax();
        }

        MatchingRule defaultMatchingRule =  numericOID2MatchingRules.get(options.get(DEFAULT_MATCHING_RULE_OID));
        if (defaultMatchingRule == null) {
            defaultMatchingRule = Schema.getCoreSchema().getDefaultMatchingRule();
        }

        final Schema schema =
                new Schema.StrictImpl(localSchemaName, options,
                        defaultSyntax, defaultMatchingRule, numericOID2Syntaxes,
                        numericOID2MatchingRules, numericOID2MatchingRuleUses,
                        numericOID2AttributeTypes, numericOID2ObjectClasses, numericOID2NameForms,
                        numericOID2ContentRules, id2StructureRules, name2MatchingRules,
                        name2MatchingRuleUses, name2AttributeTypes, name2ObjectClasses,
                        name2NameForms, name2ContentRules, name2StructureRules,
                        objectClass2NameForms, nameForm2StructureRules, name2OIDs, warnings).asStrictSchema();
        validate(schema);

        // Re-init this builder so that it can continue to be used afterwards.
        preLazyInitBuilder(schemaName, schema);

        return schema;
    }

    private void registerNameToOIDMapping(String name, String anOID) {
        if (name2OIDs.put(name, anOID) != null) {
            name2OIDs.put(name, AMBIGUOUS_OID);
        }
    }

    SchemaBuilder addAttributeType(final AttributeType attribute, final boolean overwrite) {
        AttributeType conflictingAttribute;
        if (numericOID2AttributeTypes.containsKey(attribute.getOID())) {
            conflictingAttribute = numericOID2AttributeTypes.get(attribute.getOID());
            if (!overwrite) {
                final LocalizableMessage message =
                        ERR_SCHEMA_CONFLICTING_ATTRIBUTE_OID.get(attribute.getNameOrOID(),
                                attribute.getOID(), conflictingAttribute.getNameOrOID());
                throw new ConflictingSchemaElementException(message);
            }
            removeAttributeType(conflictingAttribute);
        }

        numericOID2AttributeTypes.put(attribute.getOID(), attribute);
        for (final String name : attribute.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            List<AttributeType> attrs = name2AttributeTypes.get(lowerName);
            if (attrs == null) {
                name2AttributeTypes.put(lowerName, Collections.singletonList(attribute));
            } else if (attrs.size() == 1) {
                attrs = new ArrayList<>(attrs);
                attrs.add(attribute);
                name2AttributeTypes.put(lowerName, attrs);
            } else {
                attrs.add(attribute);
            }
        }

        return this;
    }

    SchemaBuilder addDITContentRule(final DITContentRule rule, final boolean overwrite) {
        DITContentRule conflictingRule;
        if (numericOID2ContentRules.containsKey(rule.getStructuralClassOID())) {
            conflictingRule = numericOID2ContentRules.get(rule.getStructuralClassOID());
            if (!overwrite) {
                final LocalizableMessage message =
                        ERR_SCHEMA_CONFLICTING_DIT_CONTENT_RULE1.get(rule.getNameOrOID(), rule
                                .getStructuralClassOID(), conflictingRule.getNameOrOID());
                throw new ConflictingSchemaElementException(message);
            }
            removeDITContentRule(conflictingRule);
        }

        numericOID2ContentRules.put(rule.getStructuralClassOID(), rule);
        for (final String name : rule.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            List<DITContentRule> rules = name2ContentRules.get(lowerName);
            if (rules == null) {
                name2ContentRules.put(lowerName, Collections.singletonList(rule));
            } else if (rules.size() == 1) {
                rules = new ArrayList<>(rules);
                rules.add(rule);
                name2ContentRules.put(lowerName, rules);
            } else {
                rules.add(rule);
            }
        }

        return this;
    }

    SchemaBuilder addDITStructureRule(final DITStructureRule rule, final boolean overwrite) {
        DITStructureRule conflictingRule;
        if (id2StructureRules.containsKey(rule.getRuleID())) {
            conflictingRule = id2StructureRules.get(rule.getRuleID());
            if (!overwrite) {
                final LocalizableMessage message =
                        ERR_SCHEMA_CONFLICTING_DIT_STRUCTURE_RULE_ID.get(rule.getNameOrRuleID(),
                                rule.getRuleID(), conflictingRule.getNameOrRuleID());
                throw new ConflictingSchemaElementException(message);
            }
            removeDITStructureRule(conflictingRule);
        }

        id2StructureRules.put(rule.getRuleID(), rule);
        for (final String name : rule.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            List<DITStructureRule> rules = name2StructureRules.get(lowerName);
            if (rules == null) {
                name2StructureRules.put(lowerName, Collections.singletonList(rule));
            } else if (rules.size() == 1) {
                rules = new ArrayList<>(rules);
                rules.add(rule);
                name2StructureRules.put(lowerName, rules);
            } else {
                rules.add(rule);
            }
        }

        return this;
    }

    SchemaBuilder addMatchingRuleUse(final MatchingRuleUse use, final boolean overwrite) {
        MatchingRuleUse conflictingUse;
        if (numericOID2MatchingRuleUses.containsKey(use.getMatchingRuleOID())) {
            conflictingUse = numericOID2MatchingRuleUses.get(use.getMatchingRuleOID());
            if (!overwrite) {
                final LocalizableMessage message =
                        ERR_SCHEMA_CONFLICTING_MATCHING_RULE_USE.get(use.getNameOrOID(), use
                                .getMatchingRuleOID(), conflictingUse.getNameOrOID());
                throw new ConflictingSchemaElementException(message);
            }
            removeMatchingRuleUse(conflictingUse);
        }

        numericOID2MatchingRuleUses.put(use.getMatchingRuleOID(), use);
        for (final String name : use.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            List<MatchingRuleUse> uses = name2MatchingRuleUses.get(lowerName);
            if (uses == null) {
                name2MatchingRuleUses.put(lowerName, Collections.singletonList(use));
            } else if (uses.size() == 1) {
                uses = new ArrayList<>(uses);
                uses.add(use);
                name2MatchingRuleUses.put(lowerName, uses);
            } else {
                uses.add(use);
            }
        }

        return this;
    }

    SchemaBuilder addMatchingRule(final MatchingRule rule, final boolean overwrite) {
        Reject.ifTrue(rule.isValidated(),
                "Matching rule has already been validated, it can't be added");
        MatchingRule conflictingRule;
        if (numericOID2MatchingRules.containsKey(rule.getOID())) {
            conflictingRule = numericOID2MatchingRules.get(rule.getOID());
            if (!overwrite) {
                final LocalizableMessage message =
                        ERR_SCHEMA_CONFLICTING_MR_OID.get(rule.getNameOrOID(), rule.getOID(),
                                conflictingRule.getNameOrOID());
                throw new ConflictingSchemaElementException(message);
            }
            removeMatchingRule(conflictingRule);
        }

        numericOID2MatchingRules.put(rule.getOID(), rule);
        for (final String name : rule.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            List<MatchingRule> rules = name2MatchingRules.get(lowerName);
            if (rules == null) {
                name2MatchingRules.put(lowerName, Collections.singletonList(rule));
            } else if (rules.size() == 1) {
                rules = new ArrayList<>(rules);
                rules.add(rule);
                name2MatchingRules.put(lowerName, rules);
            } else {
                rules.add(rule);
            }
        }
        return this;
    }

    SchemaBuilder addNameForm(final NameForm form, final boolean overwrite) {
        NameForm conflictingForm;
        if (numericOID2NameForms.containsKey(form.getOID())) {
            conflictingForm = numericOID2NameForms.get(form.getOID());
            if (!overwrite) {
                final LocalizableMessage message =
                        ERR_SCHEMA_CONFLICTING_NAME_FORM_OID.get(form.getNameOrOID(),
                                form.getOID(), conflictingForm.getNameOrOID());
                throw new ConflictingSchemaElementException(message);
            }
            removeNameForm(conflictingForm);
        }

        numericOID2NameForms.put(form.getOID(), form);
        for (final String name : form.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            List<NameForm> forms = name2NameForms.get(lowerName);
            if (forms == null) {
                name2NameForms.put(lowerName, Collections.singletonList(form));
            } else if (forms.size() == 1) {
                forms = new ArrayList<>(forms);
                forms.add(form);
                name2NameForms.put(lowerName, forms);
            } else {
                forms.add(form);
            }
        }
        return this;
    }

    SchemaBuilder addObjectClass(final ObjectClass oc, final boolean overwrite) {
        ObjectClass conflictingOC;
        if (numericOID2ObjectClasses.containsKey(oc.getOID())) {
            conflictingOC = numericOID2ObjectClasses.get(oc.getOID());
            if (!overwrite) {
                final LocalizableMessage message =
                        ERR_SCHEMA_CONFLICTING_OBJECTCLASS_OID1.get(oc.getNameOrOID(), oc.getOID(),
                                conflictingOC.getNameOrOID());
                throw new ConflictingSchemaElementException(message);
            }
            removeObjectClass(conflictingOC);
        }

        numericOID2ObjectClasses.put(oc.getOID(), oc);
        for (final String name : oc.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            List<ObjectClass> classes = name2ObjectClasses.get(lowerName);
            if (classes == null) {
                name2ObjectClasses.put(lowerName, Collections.singletonList(oc));
            } else if (classes.size() == 1) {
                classes = new ArrayList<>(classes);
                classes.add(oc);
                name2ObjectClasses.put(lowerName, classes);
            } else {
                classes.add(oc);
            }
        }

        return this;
    }

    private void addSchema0(final Schema schema, final boolean overwrite) {
        // All of the schema elements must be duplicated because validation will
        // cause them to update all their internal references which, although
        // unlikely, may be different in the new schema.

        for (final Syntax syntax : schema.getSyntaxes()) {
            if (overwrite) {
                buildSyntax(syntax, false).addToSchemaOverwrite();
            } else {
                buildSyntax(syntax, false).addToSchema();
            }
        }

        for (final MatchingRule matchingRule : schema.getMatchingRules()) {
            if (overwrite) {
                buildMatchingRule(matchingRule, false).addToSchemaOverwrite();
            } else {
                buildMatchingRule(matchingRule, false).addToSchema();
            }
        }

        for (final MatchingRuleUse matchingRuleUse : schema.getMatchingRuleUses()) {
            addMatchingRuleUse(matchingRuleUse, overwrite);
        }

        for (final AttributeType attributeType : schema.getAttributeTypes()) {
            addAttributeType(attributeType, overwrite);
        }

        for (final ObjectClass objectClass : schema.getObjectClasses()) {
            addObjectClass(objectClass, overwrite);
        }

        for (final NameForm nameForm : schema.getNameForms()) {
            addNameForm(nameForm, overwrite);
        }

        for (final DITContentRule contentRule : schema.getDITContentRules()) {
            addDITContentRule(contentRule, overwrite);
        }

        for (final DITStructureRule structureRule : schema.getDITStuctureRules()) {
            addDITStructureRule(structureRule, overwrite);
        }
    }

    SchemaBuilder addSyntax(final Syntax syntax, final boolean overwrite) {
        Reject.ifTrue(syntax.isValidated(), "Syntax has already been validated, it can't be added");
        Syntax conflictingSyntax;
        if (numericOID2Syntaxes.containsKey(syntax.getOID())) {
            conflictingSyntax = numericOID2Syntaxes.get(syntax.getOID());
            if (!overwrite) {
                final LocalizableMessage message = ERR_SCHEMA_CONFLICTING_SYNTAX_OID.get(syntax.toString(),
                        syntax.getOID(), conflictingSyntax.getOID());
                throw new ConflictingSchemaElementException(message);
            }
            removeSyntax(conflictingSyntax);
        }

        numericOID2Syntaxes.put(syntax.getOID(), syntax);
        return this;
    }

    private void lazyInitBuilder() {
        // Lazy initialization.
        if (numericOID2Syntaxes == null) {
            options = Options.defaultOptions();

            numericOID2Syntaxes = new LinkedHashMap<>();
            numericOID2MatchingRules = new LinkedHashMap<>();
            numericOID2MatchingRuleUses = new LinkedHashMap<>();
            numericOID2AttributeTypes = new LinkedHashMap<>();
            numericOID2ObjectClasses = new LinkedHashMap<>();
            numericOID2NameForms = new LinkedHashMap<>();
            numericOID2ContentRules = new LinkedHashMap<>();
            id2StructureRules = new LinkedHashMap<>();

            name2MatchingRules = new LinkedHashMap<>();
            name2MatchingRuleUses = new LinkedHashMap<>();
            name2AttributeTypes = new LinkedHashMap<>();
            name2ObjectClasses = new LinkedHashMap<>();
            name2NameForms = new LinkedHashMap<>();
            name2ContentRules = new LinkedHashMap<>();
            name2StructureRules = new LinkedHashMap<>();

            objectClass2NameForms = new HashMap<>();
            nameForm2StructureRules = new HashMap<>();
            name2OIDs = new HashMap<>();
            warnings = new LinkedList<>();
        }

        if (copyOnWriteSchema != null) {
            // Copy the schema.
            addSchema0(copyOnWriteSchema, true);
            options = Options.copyOf(copyOnWriteSchema.getOptions());
            copyOnWriteSchema = null;
        }
    }

    private void preLazyInitBuilder(final String schemaName, final Schema copyOnWriteSchema) {
        this.schemaName = schemaName;
        this.copyOnWriteSchema = copyOnWriteSchema;

        this.options = null;

        this.numericOID2Syntaxes = null;
        this.numericOID2MatchingRules = null;
        this.numericOID2MatchingRuleUses = null;
        this.numericOID2AttributeTypes = null;
        this.numericOID2ObjectClasses = null;
        this.numericOID2NameForms = null;
        this.numericOID2ContentRules = null;
        this.id2StructureRules = null;

        this.name2MatchingRules = null;
        this.name2MatchingRuleUses = null;
        this.name2AttributeTypes = null;
        this.name2ObjectClasses = null;
        this.name2NameForms = null;
        this.name2ContentRules = null;
        this.name2StructureRules = null;

        this.objectClass2NameForms = null;
        this.nameForm2StructureRules = null;
        this.warnings = null;
    }

    private void removeAttributeType(final AttributeType attributeType) {
        numericOID2AttributeTypes.remove(attributeType.getOID());
        for (final String name : attributeType.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            final List<AttributeType> attributes = name2AttributeTypes.get(lowerName);
            if (attributes != null && attributes.contains(attributeType)) {
                if (attributes.size() <= 1) {
                    name2AttributeTypes.remove(lowerName);
                } else {
                    attributes.remove(attributeType);
                }
            }
        }
    }

    private void removeDITContentRule(final DITContentRule rule) {
        numericOID2ContentRules.remove(rule.getStructuralClassOID());
        for (final String name : rule.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            final List<DITContentRule> rules = name2ContentRules.get(lowerName);
            if (rules != null && rules.contains(rule)) {
                if (rules.size() <= 1) {
                    name2ContentRules.remove(lowerName);
                } else {
                    rules.remove(rule);
                }
            }
        }
    }

    private void removeDITStructureRule(final DITStructureRule rule) {
        id2StructureRules.remove(rule.getRuleID());
        for (final String name : rule.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            final List<DITStructureRule> rules = name2StructureRules.get(lowerName);
            if (rules != null && rules.contains(rule)) {
                if (rules.size() <= 1) {
                    name2StructureRules.remove(lowerName);
                } else {
                    rules.remove(rule);
                }
            }
        }
    }

    private void removeMatchingRule(final MatchingRule rule) {
        numericOID2MatchingRules.remove(rule.getOID());
        for (final String name : rule.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            final List<MatchingRule> rules = name2MatchingRules.get(lowerName);
            if (rules != null && rules.contains(rule)) {
                if (rules.size() <= 1) {
                    name2MatchingRules.remove(lowerName);
                } else {
                    rules.remove(rule);
                }
            }
        }
    }

    private void removeMatchingRuleUse(final MatchingRuleUse use) {
        numericOID2MatchingRuleUses.remove(use.getMatchingRuleOID());
        for (final String name : use.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            final List<MatchingRuleUse> uses = name2MatchingRuleUses.get(lowerName);
            if (uses != null && uses.contains(use)) {
                if (uses.size() <= 1) {
                    name2MatchingRuleUses.remove(lowerName);
                } else {
                    uses.remove(use);
                }
            }
        }
    }

    private void removeNameForm(final NameForm form) {
        numericOID2NameForms.remove(form.getOID());
        name2NameForms.remove(form.getOID());
        for (final String name : form.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            final List<NameForm> forms = name2NameForms.get(lowerName);
            if (forms != null && forms.contains(form)) {
                if (forms.size() <= 1) {
                    name2NameForms.remove(lowerName);
                } else {
                    forms.remove(form);
                }
            }
        }
    }

    private void removeObjectClass(final ObjectClass oc) {
        numericOID2ObjectClasses.remove(oc.getOID());
        name2ObjectClasses.remove(oc.getOID());
        for (final String name : oc.getNames()) {
            final String lowerName = StaticUtils.toLowerCase(name);
            final List<ObjectClass> classes = name2ObjectClasses.get(lowerName);
            if (classes != null && classes.contains(oc)) {
                if (classes.size() <= 1) {
                    name2ObjectClasses.remove(lowerName);
                } else {
                    classes.remove(oc);
                }
            }
        }
    }

    private void removeSyntax(final Syntax syntax) {
        numericOID2Syntaxes.remove(syntax.getOID());
    }

    private void validate(final Schema schema) {
        // Verify all references in all elements
        for (final Syntax syntax : numericOID2Syntaxes.values().toArray(
                new Syntax[numericOID2Syntaxes.values().size()])) {
            try {
                syntax.validate(schema, warnings);
                registerNameToOIDMapping(syntax.getName(), syntax.getOID());
            } catch (final SchemaException e) {
                removeSyntax(syntax);
                warnings.add(ERR_SYNTAX_VALIDATION_FAIL
                        .get(syntax.toString(), e.getMessageObject()));
            }
        }

        for (final MatchingRule rule : numericOID2MatchingRules.values().toArray(
                new MatchingRule[numericOID2MatchingRules.values().size()])) {
            try {
                rule.validate(schema, warnings);
                for (final String name : rule.getNames()) {
                    registerNameToOIDMapping(StaticUtils.toLowerCase(name), rule.getOID());
                }
            } catch (final SchemaException e) {
                removeMatchingRule(rule);
                warnings.add(ERR_MR_VALIDATION_FAIL.get(rule.toString(), e.getMessageObject()));
            }
        }

        // Attribute types need special processing because they have
        // hierarchical dependencies.
        final List<AttributeType> invalidAttributeTypes = new LinkedList<>();
        for (final AttributeType attributeType : numericOID2AttributeTypes.values()) {
            attributeType.validate(schema, invalidAttributeTypes, warnings);
        }

        for (final AttributeType attributeType : invalidAttributeTypes) {
            removeAttributeType(attributeType);
        }

        for (final AttributeType attributeType : numericOID2AttributeTypes.values()) {
            for (final String name : attributeType.getNames()) {
                registerNameToOIDMapping(StaticUtils.toLowerCase(name), attributeType.getOID());
            }
        }

        // Object classes need special processing because they have hierarchical
        // dependencies.
        final List<ObjectClass> invalidObjectClasses = new LinkedList<>();
        for (final ObjectClass objectClass : numericOID2ObjectClasses.values()) {
            objectClass.validate(schema, invalidObjectClasses, warnings);
        }

        for (final ObjectClass objectClass : invalidObjectClasses) {
            removeObjectClass(objectClass);
        }

        for (final ObjectClass objectClass : numericOID2ObjectClasses.values()) {
            for (final String name : objectClass.getNames()) {
                registerNameToOIDMapping(StaticUtils.toLowerCase(name), objectClass.getOID());
            }
        }
        for (final MatchingRuleUse use : numericOID2MatchingRuleUses.values().toArray(
                new MatchingRuleUse[numericOID2MatchingRuleUses.values().size()])) {
            try {
                use.validate(schema, warnings);
                for (final String name : use.getNames()) {
                    registerNameToOIDMapping(StaticUtils.toLowerCase(name), use.getMatchingRuleOID());
                }
            } catch (final SchemaException e) {
                removeMatchingRuleUse(use);
                warnings.add(ERR_MRU_VALIDATION_FAIL.get(use.toString(), e.getMessageObject()));
            }
        }

        for (final NameForm form : numericOID2NameForms.values().toArray(
                new NameForm[numericOID2NameForms.values().size()])) {
            try {
                form.validate(schema, warnings);

                // build the objectClass2NameForms map
                final String ocOID = form.getStructuralClass().getOID();
                List<NameForm> forms = objectClass2NameForms.get(ocOID);
                if (forms == null) {
                    objectClass2NameForms.put(ocOID, Collections.singletonList(form));
                } else if (forms.size() == 1) {
                    forms = new ArrayList<>(forms);
                    forms.add(form);
                    objectClass2NameForms.put(ocOID, forms);
                } else {
                    forms.add(form);
                }
                for (final String name : form.getNames()) {
                    registerNameToOIDMapping(StaticUtils.toLowerCase(name), form.getOID());
                }
            } catch (final SchemaException e) {
                removeNameForm(form);
                warnings.add(ERR_NAMEFORM_VALIDATION_FAIL
                        .get(form.toString(), e.getMessageObject()));
            }
        }

        for (final DITContentRule rule : numericOID2ContentRules.values().toArray(
                new DITContentRule[numericOID2ContentRules.values().size()])) {
            try {
                rule.validate(schema, warnings);
                for (final String name : rule.getNames()) {
                    registerNameToOIDMapping(StaticUtils.toLowerCase(name), rule.getStructuralClassOID());
                }
            } catch (final SchemaException e) {
                removeDITContentRule(rule);
                warnings.add(ERR_DCR_VALIDATION_FAIL.get(rule.toString(), e.getMessageObject()));
            }
        }

        // DIT structure rules need special processing because they have
        // hierarchical dependencies.
        final List<DITStructureRule> invalidStructureRules = new LinkedList<>();
        for (final DITStructureRule rule : id2StructureRules.values()) {
            rule.validate(schema, invalidStructureRules, warnings);
        }

        for (final DITStructureRule rule : invalidStructureRules) {
            removeDITStructureRule(rule);
        }

        for (final DITStructureRule rule : id2StructureRules.values()) {
            // build the nameForm2StructureRules map
            final String ocOID = rule.getNameForm().getOID();
            List<DITStructureRule> rules = nameForm2StructureRules.get(ocOID);
            if (rules == null) {
                nameForm2StructureRules.put(ocOID, Collections.singletonList(rule));
            } else if (rules.size() == 1) {
                rules = new ArrayList<>(rules);
                rules.add(rule);
                nameForm2StructureRules.put(ocOID, rules);
            } else {
                rules.add(rule);
            }
        }
    }
}
