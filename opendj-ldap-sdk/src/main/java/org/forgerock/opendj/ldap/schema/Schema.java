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
 *      Portions copyright 2011-2013 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.AttributeDescription.objectClass;
import static org.forgerock.opendj.ldap.CoreMessages.*;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.AVA;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.Attributes;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.EntryNotFoundException;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.FutureResult;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultHandler;

import com.forgerock.opendj.util.FutureResultTransformer;
import com.forgerock.opendj.util.StaticUtils;
import com.forgerock.opendj.util.Validator;

/**
 * This class defines a data structure that holds information about the
 * components of the LDAP schema. It includes the following kinds of elements:
 * <UL>
 * <LI>Attribute type definitions</LI>
 * <LI>Object class definitions</LI>
 * <LI>Attribute syntax definitions</LI>
 * <LI>Matching rule definitions</LI>
 * <LI>Matching rule use definitions</LI>
 * <LI>DIT content rule definitions</LI>
 * <LI>DIT structure rule definitions</LI>
 * <LI>Name form definitions</LI>
 * </UL>
 */
public final class Schema {
    private static final class EmptyImpl implements Impl {

        private final boolean isStrict;

        private EmptyImpl(final boolean isStrict) {
            this.isStrict = isStrict;
        }

        public boolean allowMalformedNamesAndOptions() {
            return true;
        }

        public boolean allowNonStandardTelephoneNumbers() {
            return true;
        }

        public boolean allowMalformedJPEGPhotos() {
            return true;
        }

        public boolean allowZeroLengthDirectoryStrings() {
            return false;
        }

        public Syntax getDefaultSyntax() {
            return Schema.getCoreSchema().getDefaultSyntax();
        }

        public MatchingRule getDefaultMatchingRule() {
            return Schema.getCoreSchema().getDefaultMatchingRule();
        }

        @Override
        public AttributeType getAttributeType(final Schema schema, final String name) {
            if (isStrict) {
                throw new UnknownSchemaElementException(WARN_ATTR_TYPE_UNKNOWN.get(name));
            } else {
                // Return a place-holder.
                return new AttributeType(schema, name);
            }
        }

        @Override
        public Collection<AttributeType> getAttributeTypes() {
            return Collections.emptyList();
        }

        @Override
        public List<AttributeType> getAttributeTypesWithName(final String name) {
            return Collections.emptyList();
        }

        @Override
        public DITContentRule getDITContentRule(final ObjectClass structuralClass) {
            return null;
        }

        @Override
        public DITContentRule getDITContentRule(final String name) {
            throw new UnknownSchemaElementException(WARN_DCR_UNKNOWN.get(name));
        }

        @Override
        public Collection<DITContentRule> getDITContentRules() {
            return Collections.emptyList();
        }

        @Override
        public Collection<DITContentRule> getDITContentRulesWithName(final String name) {
            return Collections.emptyList();
        }

        @Override
        public DITStructureRule getDITStructureRule(final int ruleID) {
            throw new UnknownSchemaElementException(WARN_DSR_UNKNOWN.get(String.valueOf(ruleID)));
        }

        @Override
        public Collection<DITStructureRule> getDITStructureRules(final NameForm nameForm) {
            return Collections.emptyList();
        }

        @Override
        public Collection<DITStructureRule> getDITStructureRulesWithName(final String name) {
            return Collections.emptyList();
        }

        @Override
        public Collection<DITStructureRule> getDITStuctureRules() {
            return Collections.emptyList();
        }

        @Override
        public MatchingRule getMatchingRule(final String name) {
            throw new UnknownSchemaElementException(WARN_MR_UNKNOWN.get(name));
        }

        @Override
        public Collection<MatchingRule> getMatchingRules() {
            return Collections.emptyList();
        }

        @Override
        public Collection<MatchingRule> getMatchingRulesWithName(final String name) {
            return Collections.emptyList();
        }

        @Override
        public MatchingRuleUse getMatchingRuleUse(final MatchingRule matchingRule) {
            return null;
        }

        @Override
        public MatchingRuleUse getMatchingRuleUse(final String name) {
            throw new UnknownSchemaElementException(WARN_MRU_UNKNOWN.get(name));
        }

        @Override
        public Collection<MatchingRuleUse> getMatchingRuleUses() {
            return Collections.emptyList();
        }

        @Override
        public Collection<MatchingRuleUse> getMatchingRuleUsesWithName(final String name) {
            return Collections.emptyList();
        }

        @Override
        public NameForm getNameForm(final String name) {
            throw new UnknownSchemaElementException(WARN_NAMEFORM_UNKNOWN.get(name));
        }

        @Override
        public Collection<NameForm> getNameForms() {
            return Collections.emptyList();
        }

        @Override
        public Collection<NameForm> getNameForms(final ObjectClass structuralClass) {
            return Collections.emptyList();
        }

        @Override
        public Collection<NameForm> getNameFormsWithName(final String name) {
            return Collections.emptyList();
        }

        @Override
        public ObjectClass getObjectClass(final String name) {
            throw new UnknownSchemaElementException(WARN_OBJECTCLASS_UNKNOWN.get(name));
        }

        @Override
        public Collection<ObjectClass> getObjectClasses() {
            return Collections.emptyList();
        }

        @Override
        public Collection<ObjectClass> getObjectClassesWithName(final String name) {
            return Collections.emptyList();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getSchemaName() {
            return "Empty Schema";
        }

        @Override
        public Syntax getSyntax(final Schema schema, final String numericOID) {
            // Fake up a syntax substituted by the default syntax.
            return new Syntax(schema, numericOID);
        }

        @Override
        public Collection<Syntax> getSyntaxes() {
            return Collections.emptyList();
        }

        @Override
        public Collection<LocalizableMessage> getWarnings() {
            return Collections.emptyList();
        }

        @Override
        public boolean hasAttributeType(final String name) {
            // In theory a non-strict schema always contains the requested
            // attribute type, so we could always return true. However, we
            // should provide a way for callers to differentiate between a
            // real attribute type and a faked up attribute type.
            return false;
        }

        @Override
        public boolean hasDITContentRule(final String name) {
            return false;
        }

        @Override
        public boolean hasDITStructureRule(final int ruleID) {
            return false;
        }

        @Override
        public boolean hasMatchingRule(final String name) {
            return false;
        }

        @Override
        public boolean hasMatchingRuleUse(final String name) {
            return false;
        }

        @Override
        public boolean hasNameForm(final String name) {
            return false;
        }

        @Override
        public boolean hasObjectClass(final String name) {
            return false;
        }

        @Override
        public boolean hasSyntax(final String numericOID) {
            return false;
        }

        @Override
        public boolean isStrict() {
            return isStrict;
        }
    }

    private static interface Impl {
        boolean allowMalformedNamesAndOptions();

        boolean allowMalformedJPEGPhotos();

        boolean allowNonStandardTelephoneNumbers();

        boolean allowZeroLengthDirectoryStrings();

        MatchingRule getDefaultMatchingRule();

        Syntax getDefaultSyntax();

        AttributeType getAttributeType(Schema schema, String name);

        Collection<AttributeType> getAttributeTypes();

        List<AttributeType> getAttributeTypesWithName(String name);

        DITContentRule getDITContentRule(ObjectClass structuralClass);

        DITContentRule getDITContentRule(String name);

        Collection<DITContentRule> getDITContentRules();

        Collection<DITContentRule> getDITContentRulesWithName(String name);

        DITStructureRule getDITStructureRule(int ruleID);

        Collection<DITStructureRule> getDITStructureRules(NameForm nameForm);

        Collection<DITStructureRule> getDITStructureRulesWithName(String name);

        Collection<DITStructureRule> getDITStuctureRules();

        MatchingRule getMatchingRule(String name);

        Collection<MatchingRule> getMatchingRules();

        Collection<MatchingRule> getMatchingRulesWithName(String name);

        MatchingRuleUse getMatchingRuleUse(MatchingRule matchingRule);

        MatchingRuleUse getMatchingRuleUse(String name);

        Collection<MatchingRuleUse> getMatchingRuleUses();

        Collection<MatchingRuleUse> getMatchingRuleUsesWithName(String name);

        NameForm getNameForm(String name);

        Collection<NameForm> getNameForms();

        Collection<NameForm> getNameForms(ObjectClass structuralClass);

        Collection<NameForm> getNameFormsWithName(String name);

        ObjectClass getObjectClass(String name);

        Collection<ObjectClass> getObjectClasses();

        Collection<ObjectClass> getObjectClassesWithName(String name);

        String getSchemaName();

        Syntax getSyntax(Schema schema, String numericOID);

        Collection<Syntax> getSyntaxes();

        Collection<LocalizableMessage> getWarnings();

        boolean hasAttributeType(String name);

        boolean hasDITContentRule(String name);

        boolean hasDITStructureRule(int ruleID);

        boolean hasMatchingRule(String name);

        boolean hasMatchingRuleUse(String name);

        boolean hasNameForm(String name);

        boolean hasObjectClass(String name);

        boolean hasSyntax(String numericOID);

        boolean isStrict();
    }

    private static final class NonStrictImpl implements Impl {
        private final StrictImpl strictImpl;

        private NonStrictImpl(final StrictImpl strictImpl) {
            this.strictImpl = strictImpl;
        }

        public boolean allowMalformedNamesAndOptions() {
            return strictImpl.allowMalformedNamesAndOptions();
        }

        public boolean allowMalformedJPEGPhotos() {
            return strictImpl.allowMalformedJPEGPhotos();
        }

        public boolean allowNonStandardTelephoneNumbers() {
            return strictImpl.allowNonStandardTelephoneNumbers();
        }

        public boolean allowZeroLengthDirectoryStrings() {
            return strictImpl.allowZeroLengthDirectoryStrings();
        }

        public Syntax getDefaultSyntax() {
            return strictImpl.getDefaultSyntax();
        }

        public MatchingRule getDefaultMatchingRule() {
            return strictImpl.getDefaultMatchingRule();
        }

        @Override
        public AttributeType getAttributeType(final Schema schema, final String name) {
            final AttributeType type = strictImpl.getAttributeType0(name);
            return type != null ? type : new AttributeType(schema, name);
        }

        @Override
        public Collection<AttributeType> getAttributeTypes() {
            return strictImpl.getAttributeTypes();
        }

        @Override
        public List<AttributeType> getAttributeTypesWithName(final String name) {
            return strictImpl.getAttributeTypesWithName(name);
        }

        @Override
        public DITContentRule getDITContentRule(final ObjectClass structuralClass) {
            return strictImpl.getDITContentRule(structuralClass);
        }

        @Override
        public DITContentRule getDITContentRule(final String name) {
            return strictImpl.getDITContentRule(name);
        }

        @Override
        public Collection<DITContentRule> getDITContentRules() {
            return strictImpl.getDITContentRules();
        }

        @Override
        public Collection<DITContentRule> getDITContentRulesWithName(final String name) {
            return strictImpl.getDITContentRulesWithName(name);
        }

        @Override
        public DITStructureRule getDITStructureRule(final int ruleID) {
            return strictImpl.getDITStructureRule(ruleID);
        }

        @Override
        public Collection<DITStructureRule> getDITStructureRules(final NameForm nameForm) {
            return strictImpl.getDITStructureRules(nameForm);
        }

        @Override
        public Collection<DITStructureRule> getDITStructureRulesWithName(final String name) {
            return strictImpl.getDITStructureRulesWithName(name);
        }

        @Override
        public Collection<DITStructureRule> getDITStuctureRules() {
            return strictImpl.getDITStuctureRules();
        }

        @Override
        public MatchingRule getMatchingRule(final String name) {
            return strictImpl.getMatchingRule(name);
        }

        @Override
        public Collection<MatchingRule> getMatchingRules() {
            return strictImpl.getMatchingRules();
        }

        @Override
        public Collection<MatchingRule> getMatchingRulesWithName(final String name) {
            return strictImpl.getMatchingRulesWithName(name);
        }

        @Override
        public MatchingRuleUse getMatchingRuleUse(final MatchingRule matchingRule) {
            return strictImpl.getMatchingRuleUse(matchingRule);
        }

        @Override
        public MatchingRuleUse getMatchingRuleUse(final String name) {
            return strictImpl.getMatchingRuleUse(name);
        }

        @Override
        public Collection<MatchingRuleUse> getMatchingRuleUses() {
            return strictImpl.getMatchingRuleUses();
        }

        @Override
        public Collection<MatchingRuleUse> getMatchingRuleUsesWithName(final String name) {
            return strictImpl.getMatchingRuleUsesWithName(name);
        }

        @Override
        public NameForm getNameForm(final String name) {
            return strictImpl.getNameForm(name);
        }

        @Override
        public Collection<NameForm> getNameForms() {
            return strictImpl.getNameForms();
        }

        @Override
        public Collection<NameForm> getNameForms(final ObjectClass structuralClass) {
            return strictImpl.getNameForms(structuralClass);
        }

        @Override
        public Collection<NameForm> getNameFormsWithName(final String name) {
            return strictImpl.getNameFormsWithName(name);
        }

        @Override
        public ObjectClass getObjectClass(final String name) {
            return strictImpl.getObjectClass(name);
        }

        @Override
        public Collection<ObjectClass> getObjectClasses() {
            return strictImpl.getObjectClasses();
        }

        @Override
        public Collection<ObjectClass> getObjectClassesWithName(final String name) {
            return strictImpl.getObjectClassesWithName(name);
        }

        @Override
        public String getSchemaName() {
            return strictImpl.getSchemaName();
        }

        @Override
        public Syntax getSyntax(final Schema schema, final String numericOID) {
            if (!strictImpl.hasSyntax(numericOID)) {
                return new Syntax(schema, numericOID);
            }
            return strictImpl.getSyntax(schema, numericOID);
        }

        @Override
        public Collection<Syntax> getSyntaxes() {
            return strictImpl.getSyntaxes();
        }

        @Override
        public Collection<LocalizableMessage> getWarnings() {
            return strictImpl.getWarnings();
        }

        @Override
        public boolean hasAttributeType(final String name) {
            // In theory a non-strict schema always contains the requested
            // attribute type, so we could always return true. However, we
            // should provide a way for callers to differentiate between a
            // real attribute type and a faked up attribute type.
            return strictImpl.hasAttributeType(name);
        }

        @Override
        public boolean hasDITContentRule(final String name) {
            return strictImpl.hasDITContentRule(name);
        }

        @Override
        public boolean hasDITStructureRule(final int ruleID) {
            return strictImpl.hasDITStructureRule(ruleID);
        }

        @Override
        public boolean hasMatchingRule(final String name) {
            return strictImpl.hasMatchingRule(name);
        }

        @Override
        public boolean hasMatchingRuleUse(final String name) {
            return strictImpl.hasMatchingRuleUse(name);
        }

        @Override
        public boolean hasNameForm(final String name) {
            return strictImpl.hasNameForm(name);
        }

        @Override
        public boolean hasObjectClass(final String name) {
            return strictImpl.hasObjectClass(name);
        }

        @Override
        public boolean hasSyntax(final String numericOID) {
            return strictImpl.hasSyntax(numericOID);
        }

        @Override
        public boolean isStrict() {
            return false;
        }
    }

    private static final class StrictImpl implements Impl {
        private final Map<Integer, DITStructureRule> id2StructureRules;
        private final Map<String, List<AttributeType>> name2AttributeTypes;
        private final Map<String, List<DITContentRule>> name2ContentRules;
        private final Map<String, List<MatchingRule>> name2MatchingRules;
        private final Map<String, List<MatchingRuleUse>> name2MatchingRuleUses;
        private final Map<String, List<NameForm>> name2NameForms;
        private final Map<String, List<ObjectClass>> name2ObjectClasses;
        private final Map<String, List<DITStructureRule>> name2StructureRules;
        private final Map<String, List<DITStructureRule>> nameForm2StructureRules;
        private final Map<String, AttributeType> numericOID2AttributeTypes;
        private final Map<String, DITContentRule> numericOID2ContentRules;
        private final Map<String, MatchingRule> numericOID2MatchingRules;
        private final Map<String, MatchingRuleUse> numericOID2MatchingRuleUses;
        private final Map<String, NameForm> numericOID2NameForms;
        private final Map<String, ObjectClass> numericOID2ObjectClasses;
        private final Map<String, Syntax> numericOID2Syntaxes;
        private final Map<String, List<NameForm>> objectClass2NameForms;
        private final List<LocalizableMessage> warnings;
        private final String schemaName;
        private final boolean allowMalformedJPEGPhotos;
        private final boolean allowNonStandardTelephoneNumbers;
        private final boolean allowZeroLengthDirectoryStrings;
        private final boolean allowMalformedNamesAndOptions;
        private final Syntax defaultSyntax;
        private final MatchingRule defaultMatchingRule;

        StrictImpl(final String schemaName, final boolean allowMalformedNamesAndOptions,
                final boolean allowMalformedJPEGPhotos,
                final boolean allowNonStandardTelephoneNumbers,
                final boolean allowZeroLengthDirectoryStrings,
                final Syntax defaultSyntax,
                final MatchingRule defaultMatchingRule,
                final Map<String, Syntax> numericOID2Syntaxes,
                final Map<String, MatchingRule> numericOID2MatchingRules,
                final Map<String, MatchingRuleUse> numericOID2MatchingRuleUses,
                final Map<String, AttributeType> numericOID2AttributeTypes,
                final Map<String, ObjectClass> numericOID2ObjectClasses,
                final Map<String, NameForm> numericOID2NameForms,
                final Map<String, DITContentRule> numericOID2ContentRules,
                final Map<Integer, DITStructureRule> id2StructureRules,
                final Map<String, List<MatchingRule>> name2MatchingRules,
                final Map<String, List<MatchingRuleUse>> name2MatchingRuleUses,
                final Map<String, List<AttributeType>> name2AttributeTypes,
                final Map<String, List<ObjectClass>> name2ObjectClasses,
                final Map<String, List<NameForm>> name2NameForms,
                final Map<String, List<DITContentRule>> name2ContentRules,
                final Map<String, List<DITStructureRule>> name2StructureRules,
                final Map<String, List<NameForm>> objectClass2NameForms,
                final Map<String, List<DITStructureRule>> nameForm2StructureRules,
                final List<LocalizableMessage> warnings) {
            this.schemaName = schemaName;
            this.allowMalformedNamesAndOptions = allowMalformedNamesAndOptions;
            this.allowMalformedJPEGPhotos = allowMalformedJPEGPhotos;
            this.allowNonStandardTelephoneNumbers = allowNonStandardTelephoneNumbers;
            this.allowZeroLengthDirectoryStrings = allowZeroLengthDirectoryStrings;
            this.defaultSyntax = defaultSyntax;
            this.defaultMatchingRule = defaultMatchingRule;
            this.numericOID2Syntaxes = Collections.unmodifiableMap(numericOID2Syntaxes);
            this.numericOID2MatchingRules = Collections.unmodifiableMap(numericOID2MatchingRules);
            this.numericOID2MatchingRuleUses =
                    Collections.unmodifiableMap(numericOID2MatchingRuleUses);
            this.numericOID2AttributeTypes = Collections.unmodifiableMap(numericOID2AttributeTypes);
            this.numericOID2ObjectClasses = Collections.unmodifiableMap(numericOID2ObjectClasses);
            this.numericOID2NameForms = Collections.unmodifiableMap(numericOID2NameForms);
            this.numericOID2ContentRules = Collections.unmodifiableMap(numericOID2ContentRules);
            this.id2StructureRules = Collections.unmodifiableMap(id2StructureRules);
            this.name2MatchingRules = Collections.unmodifiableMap(name2MatchingRules);
            this.name2MatchingRuleUses = Collections.unmodifiableMap(name2MatchingRuleUses);
            this.name2AttributeTypes = Collections.unmodifiableMap(name2AttributeTypes);
            this.name2ObjectClasses = Collections.unmodifiableMap(name2ObjectClasses);
            this.name2NameForms = Collections.unmodifiableMap(name2NameForms);
            this.name2ContentRules = Collections.unmodifiableMap(name2ContentRules);
            this.name2StructureRules = Collections.unmodifiableMap(name2StructureRules);
            this.objectClass2NameForms = Collections.unmodifiableMap(objectClass2NameForms);
            this.nameForm2StructureRules = Collections.unmodifiableMap(nameForm2StructureRules);
            this.warnings = Collections.unmodifiableList(warnings);
        }

        public boolean allowMalformedNamesAndOptions() {
            return allowMalformedNamesAndOptions;
        }

        public boolean allowMalformedJPEGPhotos() {
            return allowMalformedJPEGPhotos;
        }

        public boolean allowNonStandardTelephoneNumbers() {
            return allowNonStandardTelephoneNumbers;
        }

        public boolean allowZeroLengthDirectoryStrings() {
            return allowZeroLengthDirectoryStrings;
        }

        public Syntax getDefaultSyntax() {
            return defaultSyntax;
        }

        public MatchingRule getDefaultMatchingRule() {
            return defaultMatchingRule;
        }

        @Override
        public AttributeType getAttributeType(final Schema schema, final String name) {
            final AttributeType type = getAttributeType0(name);
            if (type != null) {
                return type;
            } else {
                throw new UnknownSchemaElementException(WARN_ATTR_TYPE_UNKNOWN.get(name));
            }
        }

        @Override
        public Collection<AttributeType> getAttributeTypes() {
            return numericOID2AttributeTypes.values();
        }

        @Override
        public List<AttributeType> getAttributeTypesWithName(final String name) {
            final List<AttributeType> attributes =
                    name2AttributeTypes.get(StaticUtils.toLowerCase(name));
            if (attributes == null) {
                return Collections.emptyList();
            } else {
                return attributes;
            }
        }

        @Override
        public DITContentRule getDITContentRule(final ObjectClass structuralClass) {
            return numericOID2ContentRules.get(structuralClass.getOID());
        }

        @Override
        public DITContentRule getDITContentRule(final String name) {
            final DITContentRule rule = numericOID2ContentRules.get(name);
            if (rule != null) {
                return rule;
            }
            final List<DITContentRule> rules = name2ContentRules.get(StaticUtils.toLowerCase(name));
            if (rules != null) {
                if (rules.size() == 1) {
                    return rules.get(0);
                }
                throw new UnknownSchemaElementException(WARN_DCR_AMBIGIOUS.get(name));
            }
            throw new UnknownSchemaElementException(WARN_DCR_UNKNOWN.get(name));
        }

        @Override
        public Collection<DITContentRule> getDITContentRules() {
            return numericOID2ContentRules.values();
        }

        @Override
        public Collection<DITContentRule> getDITContentRulesWithName(final String name) {
            final List<DITContentRule> rules = name2ContentRules.get(StaticUtils.toLowerCase(name));
            if (rules == null) {
                return Collections.emptyList();
            } else {
                return rules;
            }
        }

        @Override
        public DITStructureRule getDITStructureRule(final int ruleID) {
            final DITStructureRule rule = id2StructureRules.get(ruleID);
            if (rule == null) {
                throw new UnknownSchemaElementException(WARN_DSR_UNKNOWN
                        .get(String.valueOf(ruleID)));
            }
            return rule;
        }

        @Override
        public Collection<DITStructureRule> getDITStructureRules(final NameForm nameForm) {
            final List<DITStructureRule> rules = nameForm2StructureRules.get(nameForm.getOID());
            if (rules == null) {
                return Collections.emptyList();
            } else {
                return rules;
            }
        }

        @Override
        public Collection<DITStructureRule> getDITStructureRulesWithName(final String name) {
            final List<DITStructureRule> rules =
                    name2StructureRules.get(StaticUtils.toLowerCase(name));
            if (rules == null) {
                return Collections.emptyList();
            } else {
                return rules;
            }
        }

        @Override
        public Collection<DITStructureRule> getDITStuctureRules() {
            return id2StructureRules.values();
        }

        @Override
        public MatchingRule getMatchingRule(final String name) {
            final MatchingRule rule = numericOID2MatchingRules.get(name);
            if (rule != null) {
                return rule;
            }
            final List<MatchingRule> rules = name2MatchingRules.get(StaticUtils.toLowerCase(name));
            if (rules != null) {
                if (rules.size() == 1) {
                    return rules.get(0);
                }
                throw new UnknownSchemaElementException(WARN_MR_AMBIGIOUS.get(name));
            }
            throw new UnknownSchemaElementException(WARN_MR_UNKNOWN.get(name));
        }

        @Override
        public Collection<MatchingRule> getMatchingRules() {
            return numericOID2MatchingRules.values();
        }

        @Override
        public Collection<MatchingRule> getMatchingRulesWithName(final String name) {
            final List<MatchingRule> rules = name2MatchingRules.get(StaticUtils.toLowerCase(name));
            if (rules == null) {
                return Collections.emptyList();
            } else {
                return rules;
            }
        }

        @Override
        public MatchingRuleUse getMatchingRuleUse(final MatchingRule matchingRule) {
            return numericOID2MatchingRuleUses.get(matchingRule.getOID());
        }

        @Override
        public MatchingRuleUse getMatchingRuleUse(final String name) {
            final MatchingRuleUse rule = numericOID2MatchingRuleUses.get(name);
            if (rule != null) {
                return rule;
            }
            final List<MatchingRuleUse> uses =
                    name2MatchingRuleUses.get(StaticUtils.toLowerCase(name));
            if (uses != null) {
                if (uses.size() == 1) {
                    return uses.get(0);
                }
                throw new UnknownSchemaElementException(WARN_MRU_AMBIGIOUS.get(name));
            }
            throw new UnknownSchemaElementException(WARN_MRU_UNKNOWN.get(name));
        }

        @Override
        public Collection<MatchingRuleUse> getMatchingRuleUses() {
            return numericOID2MatchingRuleUses.values();
        }

        @Override
        public Collection<MatchingRuleUse> getMatchingRuleUsesWithName(final String name) {
            final List<MatchingRuleUse> rules =
                    name2MatchingRuleUses.get(StaticUtils.toLowerCase(name));
            if (rules == null) {
                return Collections.emptyList();
            } else {
                return rules;
            }
        }

        @Override
        public NameForm getNameForm(final String name) {
            final NameForm form = numericOID2NameForms.get(name);
            if (form != null) {
                return form;
            }
            final List<NameForm> forms = name2NameForms.get(StaticUtils.toLowerCase(name));
            if (forms != null) {
                if (forms.size() == 1) {
                    return forms.get(0);
                }
                throw new UnknownSchemaElementException(WARN_NAMEFORM_AMBIGIOUS.get(name));
            }
            throw new UnknownSchemaElementException(WARN_NAMEFORM_UNKNOWN.get(name));
        }

        @Override
        public Collection<NameForm> getNameForms() {
            return numericOID2NameForms.values();
        }

        @Override
        public Collection<NameForm> getNameForms(final ObjectClass structuralClass) {
            final List<NameForm> forms = objectClass2NameForms.get(structuralClass.getOID());
            if (forms == null) {
                return Collections.emptyList();
            } else {
                return forms;
            }
        }

        @Override
        public Collection<NameForm> getNameFormsWithName(final String name) {
            final List<NameForm> forms = name2NameForms.get(StaticUtils.toLowerCase(name));
            if (forms == null) {
                return Collections.emptyList();
            } else {
                return forms;
            }
        }

        @Override
        public ObjectClass getObjectClass(final String name) {
            final ObjectClass oc = numericOID2ObjectClasses.get(name);
            if (oc != null) {
                return oc;
            }
            final List<ObjectClass> classes = name2ObjectClasses.get(StaticUtils.toLowerCase(name));
            if (classes != null) {
                if (classes.size() == 1) {
                    return classes.get(0);
                }
                throw new UnknownSchemaElementException(WARN_OBJECTCLASS_AMBIGIOUS.get(name));
            }
            throw new UnknownSchemaElementException(WARN_OBJECTCLASS_UNKNOWN.get(name));
        }

        @Override
        public Collection<ObjectClass> getObjectClasses() {
            return numericOID2ObjectClasses.values();
        }

        @Override
        public Collection<ObjectClass> getObjectClassesWithName(final String name) {
            final List<ObjectClass> classes = name2ObjectClasses.get(StaticUtils.toLowerCase(name));
            if (classes == null) {
                return Collections.emptyList();
            } else {
                return classes;
            }
        }

        @Override
        public String getSchemaName() {
            return schemaName;
        }

        @Override
        public Syntax getSyntax(final Schema schema, final String numericOID) {
            final Syntax syntax = numericOID2Syntaxes.get(numericOID);
            if (syntax == null) {
                throw new UnknownSchemaElementException(WARN_SYNTAX_UNKNOWN.get(numericOID));
            }
            return syntax;
        }

        @Override
        public Collection<Syntax> getSyntaxes() {
            return numericOID2Syntaxes.values();
        }

        @Override
        public Collection<LocalizableMessage> getWarnings() {
            return warnings;
        }

        @Override
        public boolean hasAttributeType(final String name) {
            if (numericOID2AttributeTypes.containsKey(name)) {
                return true;
            }
            final List<AttributeType> attributes =
                    name2AttributeTypes.get(StaticUtils.toLowerCase(name));
            return attributes != null && attributes.size() == 1;
        }

        @Override
        public boolean hasDITContentRule(final String name) {
            if (numericOID2ContentRules.containsKey(name)) {
                return true;
            }
            final List<DITContentRule> rules = name2ContentRules.get(StaticUtils.toLowerCase(name));
            return rules != null && rules.size() == 1;
        }

        @Override
        public boolean hasDITStructureRule(final int ruleID) {
            return id2StructureRules.containsKey(ruleID);
        }

        @Override
        public boolean hasMatchingRule(final String name) {
            if (numericOID2MatchingRules.containsKey(name)) {
                return true;
            }
            final List<MatchingRule> rules = name2MatchingRules.get(StaticUtils.toLowerCase(name));
            return rules != null && rules.size() == 1;
        }

        @Override
        public boolean hasMatchingRuleUse(final String name) {
            if (numericOID2MatchingRuleUses.containsKey(name)) {
                return true;
            }
            final List<MatchingRuleUse> uses =
                    name2MatchingRuleUses.get(StaticUtils.toLowerCase(name));
            return uses != null && uses.size() == 1;
        }

        @Override
        public boolean hasNameForm(final String name) {
            if (numericOID2NameForms.containsKey(name)) {
                return true;
            }
            final List<NameForm> forms = name2NameForms.get(StaticUtils.toLowerCase(name));
            return forms != null && forms.size() == 1;
        }

        @Override
        public boolean hasObjectClass(final String name) {
            if (numericOID2ObjectClasses.containsKey(name)) {
                return true;
            }
            final List<ObjectClass> classes = name2ObjectClasses.get(StaticUtils.toLowerCase(name));
            return classes != null && classes.size() == 1;
        }

        @Override
        public boolean hasSyntax(final String numericOID) {
            return numericOID2Syntaxes.containsKey(numericOID);
        }

        @Override
        public boolean isStrict() {
            return true;
        }

        AttributeType getAttributeType0(final String name) {
            final AttributeType type = numericOID2AttributeTypes.get(name);
            if (type != null) {
                return type;
            }
            final List<AttributeType> attributes =
                    name2AttributeTypes.get(StaticUtils.toLowerCase(name));
            if (attributes != null) {
                if (attributes.size() == 1) {
                    return attributes.get(0);
                }
                throw new UnknownSchemaElementException(WARN_ATTR_TYPE_AMBIGIOUS.get(name));
            }
            return null;
        }
    }

    /*
     * WARNING: do not reference the core schema in the following declarations.
     */

    private static final Schema EMPTY_STRICT_SCHEMA = new Schema(new EmptyImpl(true));
    private static final Schema EMPTY_NON_STRICT_SCHEMA = new Schema(new EmptyImpl(false));
    static final String ATTR_ATTRIBUTE_TYPES = "attributeTypes";
    static final String ATTR_DIT_CONTENT_RULES = "dITContentRules";
    static final String ATTR_DIT_STRUCTURE_RULES = "dITStructureRules";
    static final String ATTR_LDAP_SYNTAXES = "ldapSyntaxes";
    static final String ATTR_MATCHING_RULE_USE = "matchingRuleUse";
    static final String ATTR_MATCHING_RULES = "matchingRules";
    static final String ATTR_NAME_FORMS = "nameForms";
    static final String ATTR_OBJECT_CLASSES = "objectClasses";

    /**
     * Returns the core schema. The core schema is non-strict and contains the
     * following standard LDAP schema elements:
     * <ul>
     * <li><a href="http://tools.ietf.org/html/rfc4512">RFC 4512 - Lightweight
     * Directory Access Protocol (LDAP): Directory Information Models </a>
     * <li><a href="http://tools.ietf.org/html/rfc4517">RFC 4517 - Lightweight
     * Directory Access Protocol (LDAP): Syntaxes and Matching Rules </a>
     * <li><a href="http://tools.ietf.org/html/rfc4519">RFC 4519 - Lightweight
     * Directory Access Protocol (LDAP): Schema for User Applications </a>
     * <li><a href="http://tools.ietf.org/html/rfc4530">RFC 4530 - Lightweight
     * Directory Access Protocol (LDAP): entryUUID Operational Attribute </a>
     * <li><a href="http://tools.ietf.org/html/rfc3045">RFC 3045 - Storing
     * Vendor Information in the LDAP root DSE </a>
     * <li><a href="http://tools.ietf.org/html/rfc3112">RFC 3112 - LDAP
     * Authentication Password Schema </a>
     * </ul>
     *
     * @return The core schema.
     */
    public static Schema getCoreSchema() {
        return CoreSchemaImpl.getInstance();
    }

    /**
     * Returns the default schema which should be used by this application. The
     * default schema is initially set to the core schema.
     *
     * @return The default schema which should be used by this application.
     */
    public static Schema getDefaultSchema() {
        return DefaultSchema.schema;
    }

    /**
     * Returns the empty schema. The empty schema is non-strict and does not
     * contain any schema elements.
     *
     * @return The empty schema.
     */
    public static Schema getEmptySchema() {
        return EMPTY_NON_STRICT_SCHEMA;
    }

    /**
     * Reads the schema contained in the named subschema sub-entry.
     * <p>
     * If the requested schema is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, this method will never return {@code null}.
     *
     * @param connection
     *            A connection to the Directory Server whose schema is to be
     *            read.
     * @param name
     *            The distinguished name of the subschema sub-entry.
     * @return The schema from the Directory Server.
     * @throws ErrorResultException
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
    public static Schema readSchema(final Connection connection, final DN name)
            throws ErrorResultException {
        return new SchemaBuilder().addSchema(connection, name, true).toSchema();
    }

    /**
     * Asynchronously reads the schema contained in the named subschema
     * sub-entry.
     * <p>
     * If the requested schema is not returned by the Directory Server then the
     * request will fail with an {@link EntryNotFoundException}. More
     * specifically, the returned future will never return {@code null}.
     *
     * @param connection
     *            A connection to the Directory Server whose schema is to be
     *            read.
     * @param name
     *            The distinguished name of the subschema sub-entry.
     * @param handler
     *            A result handler which can be used to asynchronously process
     *            the operation result when it is received, may be {@code null}.
     * @return A future representing the retrieved schema.
     * @throws UnsupportedOperationException
     *             If the connection does not support search operations.
     * @throws IllegalStateException
     *             If the connection has already been closed, i.e. if
     *             {@code connection.isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code connection} or {@code name} was {@code null}.
     */
    public static FutureResult<Schema> readSchemaAsync(final Connection connection, final DN name,
            final ResultHandler<? super Schema> handler) {
        final FutureResultTransformer<SchemaBuilder, Schema> future =
                new FutureResultTransformer<SchemaBuilder, Schema>(handler) {

                    @Override
                    protected Schema transformResult(final SchemaBuilder builder)
                            throws ErrorResultException {
                        return builder.toSchema();
                    }

                };

        final SchemaBuilder builder = new SchemaBuilder();
        final FutureResult<SchemaBuilder> innerFuture =
                builder.addSchemaAsync(connection, name, future, true);
        future.setFutureResult(innerFuture);
        return future;
    }

    /**
     * Reads the schema contained in the subschema sub-entry which applies to
     * the named entry.
     * <p>
     * If the requested entry or its associated schema are not returned by the
     * Directory Server then the request will fail with an
     * {@link EntryNotFoundException}. More specifically, this method will never
     * return {@code null}.
     * <p>
     * This implementation first reads the {@code subschemaSubentry} attribute
     * of the entry in order to identify the schema and then invokes
     * {@link #readSchema(Connection, DN)} to read the schema.
     *
     * @param connection
     *            A connection to the Directory Server whose schema is to be
     *            read.
     * @param name
     *            The distinguished name of the entry whose schema is to be
     *            located.
     * @return The schema from the Directory Server which applies to the named
     *         entry.
     * @throws ErrorResultException
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
    public static Schema readSchemaForEntry(final Connection connection, final DN name)
            throws ErrorResultException {
        return new SchemaBuilder().addSchemaForEntry(connection, name, true).toSchema();
    }

    /**
     * Asynchronously reads the schema contained in the subschema sub-entry
     * which applies to the named entry.
     * <p>
     * If the requested entry or its associated schema are not returned by the
     * Directory Server then the request will fail with an
     * {@link EntryNotFoundException}. More specifically, the returned future
     * will never return {@code null}.
     * <p>
     * This implementation first reads the {@code subschemaSubentry} attribute
     * of the entry in order to identify the schema and then invokes
     * {@link #readSchemaAsync(Connection, DN, ResultHandler)} to read the
     * schema.
     *
     * @param connection
     *            A connection to the Directory Server whose schema is to be
     *            read.
     * @param name
     *            The distinguished name of the entry whose schema is to be
     *            located.
     * @param handler
     *            A result handler which can be used to asynchronously process
     *            the operation result when it is received, may be {@code null}.
     * @return A future representing the retrieved schema.
     * @throws UnsupportedOperationException
     *             If the connection does not support search operations.
     * @throws IllegalStateException
     *             If the connection has already been closed, i.e. if
     *             {@code connection.isClosed() == true}.
     * @throws NullPointerException
     *             If the {@code connection} or {@code name} was {@code null}.
     */
    public static FutureResult<Schema> readSchemaForEntryAsync(final Connection connection,
            final DN name, final ResultHandler<? super Schema> handler) {
        final FutureResultTransformer<SchemaBuilder, Schema> future =
                new FutureResultTransformer<SchemaBuilder, Schema>(handler) {

                    @Override
                    protected Schema transformResult(final SchemaBuilder builder)
                            throws ErrorResultException {
                        return builder.toSchema();
                    }

                };

        final SchemaBuilder builder = new SchemaBuilder();
        final FutureResult<SchemaBuilder> innerFuture =
                builder.addSchemaForEntryAsync(connection, name, future, true);
        future.setFutureResult(innerFuture);
        return future;

    }

    /**
     * Sets the default schema which should be used by this application. The
     * default schema is initially set to the core schema.
     *
     * @param schema
     *            The default schema which should be used by this application.
     */
    public static void setDefaultSchema(final Schema schema) {
        Validator.ensureNotNull(schema);
        DefaultSchema.schema = schema;
    }

    /**
     * Parses the provided entry as a subschema subentry. Any problems
     * encountered while parsing the entry can be retrieved using the returned
     * schema's {@link #getWarnings()} method.
     *
     * @param entry
     *            The subschema subentry to be parsed.
     * @return The parsed schema.
     */
    public static Schema valueOf(final Entry entry) {
        return new SchemaBuilder(entry).toSchema();
    }

    private final Impl impl;

    Schema(final String schemaName, final boolean allowMalformedNamesAndOptions,
            final boolean allowMalformedJPEGPhotos,
            final boolean allowNonStandardTelephoneNumbers,
            final boolean allowZeroLengthDirectoryStrings,
            final Syntax defaultSyntax,
            final MatchingRule defaultMatchingRule,
            final Map<String, Syntax> numericOID2Syntaxes,
            final Map<String, MatchingRule> numericOID2MatchingRules,
            final Map<String, MatchingRuleUse> numericOID2MatchingRuleUses,
            final Map<String, AttributeType> numericOID2AttributeTypes,
            final Map<String, ObjectClass> numericOID2ObjectClasses,
            final Map<String, NameForm> numericOID2NameForms,
            final Map<String, DITContentRule> numericOID2ContentRules,
            final Map<Integer, DITStructureRule> id2StructureRules,
            final Map<String, List<MatchingRule>> name2MatchingRules,
            final Map<String, List<MatchingRuleUse>> name2MatchingRuleUses,
            final Map<String, List<AttributeType>> name2AttributeTypes,
            final Map<String, List<ObjectClass>> name2ObjectClasses,
            final Map<String, List<NameForm>> name2NameForms,
            final Map<String, List<DITContentRule>> name2ContentRules,
            final Map<String, List<DITStructureRule>> name2StructureRules,
            final Map<String, List<NameForm>> objectClass2NameForms,
            final Map<String, List<DITStructureRule>> nameForm2StructureRules,
            final List<LocalizableMessage> warnings) {
        impl =
                new StrictImpl(schemaName, allowMalformedNamesAndOptions, allowMalformedJPEGPhotos,
                        allowNonStandardTelephoneNumbers, allowZeroLengthDirectoryStrings,
                        defaultSyntax, defaultMatchingRule, numericOID2Syntaxes,
                        numericOID2MatchingRules, numericOID2MatchingRuleUses,
                        numericOID2AttributeTypes, numericOID2ObjectClasses, numericOID2NameForms,
                        numericOID2ContentRules, id2StructureRules, name2MatchingRules,
                        name2MatchingRuleUses, name2AttributeTypes, name2ObjectClasses,
                        name2NameForms, name2ContentRules, name2StructureRules,
                        objectClass2NameForms, nameForm2StructureRules, warnings);
    }

    private Schema(final Impl impl) {
        this.impl = impl;
    }

    /**
     * Returns {@code true} if this schema allows certain illegal characters in
     * OIDs and attribute options. When this compatibility option is set to
     * {@code true} the following illegal characters will be permitted in
     * addition to those permitted in section 1.4 of RFC 4512:
     *
     * <pre>
     * USCORE  = %x5F ; underscore ("_")
     * DOT     = %x2E ; period (".")
     * </pre>
     *
     * By default this compatibility option is set to {@code true} because these
     * characters are often used for naming purposes (such as collation rules).
     *
     * @return {@code true} if this schema allows certain illegal characters in
     *         OIDs and attribute options.
     * @see <a href="http://tools.ietf.org/html/rfc4512">RFC 4512 - Lightweight
     *      Directory Access Protocol (LDAP): Directory Information Models </a>
     */
    public boolean allowMalformedNamesAndOptions() {
        return impl.allowMalformedNamesAndOptions();
    }

    /**
     * Returns {@code true} if the JPEG Photo syntax defined for this
     * schema allows values which do not conform to the JFIF or Exif
     * specifications.
     * <p>
     * By default this compatibility option is set to {@code true}.
     *
     * @return {@code true} if the JPEG Photo syntax defined for this
     *         schema allows values which do not conform to the JFIF
     *         of Exit specifications.
     */
    public boolean allowMalformedJPEGPhotos() {
        return impl.allowMalformedJPEGPhotos();
    }

    /**
     * Returns {@code true} if the Telephone Number syntax defined for this
     * schema allows values which do not conform to the E.123 international
     * telephone number format.
     * <p>
     * By default this compatibility option is set to {@code true}.
     *
     * @return {@code true} if the Telephone Number syntax defined for this
     *         schema allows values which do not conform to the E.123
     *         international telephone number format.
     */
    public boolean allowNonStandardTelephoneNumbers() {
        return impl.allowNonStandardTelephoneNumbers();
    }

    /**
     * Returns {@code true} if zero-length values will be allowed by the
     * Directory String syntax defined for this schema. This is technically
     * forbidden by the LDAP specification, but it was allowed in earlier
     * versions of the server, and the discussion of the directory string syntax
     * in RFC 2252 does not explicitly state that they are not allowed.
     * <p>
     * By default this compatibility option is set to {@code false}.
     *
     * @return {@code true} if zero-length values will be allowed by the
     *         Directory String syntax defined for this schema, or {@code false}
     *         if not.
     */
    public boolean allowZeroLengthDirectoryStrings() {
        return impl.allowZeroLengthDirectoryStrings();
    }

    /**
     * Returns a non-strict view of this schema.
     * <p>
     * See the description of {@link #isStrict()} for more details.
     *
     * @return A non-strict view of this schema.
     * @see Schema#isStrict()
     */
    public Schema asNonStrictSchema() {
        if (!impl.isStrict()) {
            return this;
        } else if (impl instanceof StrictImpl) {
            return new Schema(new NonStrictImpl((StrictImpl) impl));
        } else {
            // EmptyImpl
            return EMPTY_NON_STRICT_SCHEMA;
        }
    }

    /**
     * Returns a strict view of this schema.
     * <p>
     * See the description of {@link #isStrict()} for more details.
     *
     * @return A strict view of this schema.
     * @see Schema#isStrict()
     */
    public Schema asStrictSchema() {
        if (impl.isStrict()) {
            return this;
        } else if (impl instanceof NonStrictImpl) {
            return new Schema(((NonStrictImpl) impl).strictImpl);
        } else {
            // EmptyImpl
            return EMPTY_STRICT_SCHEMA;
        }
    }

    /**
     * Returns the default matching rule which will be used when parsing
     * unrecognized attributes.
     *
     * @return The default matching rule which will be used when parsing
     *         unrecognized attributes.
     */
    public MatchingRule getDefaultMatchingRule() {
        return impl.getDefaultMatchingRule();
    }

    /**
     * Returns the default syntax which will be used when parsing unrecognized
     * attributes.
     *
     * @return The default syntax which will be used when parsing unrecognized
     *         attributes.
     */
    public Syntax getDefaultSyntax() {
        return impl.getDefaultSyntax();
    }

    /**
     * Returns the attribute type with the specified name or numeric OID.
     * <p>
     * If the requested attribute type is not registered in this schema and this
     * schema is non-strict then a temporary "place-holder" attribute type will
     * be created and returned. Place holder attribute types have an OID which
     * is the normalized attribute name with the string {@code -oid} appended.
     * In addition, they will use the directory string syntax and case ignore
     * matching rule.
     *
     * @param name
     *            The name or OID of the attribute type to retrieve.
     * @return The requested attribute type.
     * @throws UnknownSchemaElementException
     *             If this is a strict schema and the requested attribute type
     *             was not found or if the provided name is ambiguous.
     * @see AttributeType#isPlaceHolder()
     */
    public AttributeType getAttributeType(final String name) {
        return impl.getAttributeType(this, name);
    }

    /**
     * Returns an unmodifiable collection containing all of the attribute types
     * contained in this schema.
     *
     * @return An unmodifiable collection containing all of the attribute types
     *         contained in this schema.
     */
    public Collection<AttributeType> getAttributeTypes() {
        return impl.getAttributeTypes();
    }

    /**
     * Returns an unmodifiable collection containing all of the attribute types
     * having the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the attribute types to retrieve.
     * @return An unmodifiable collection containing all of the attribute types
     *         having the specified name or numeric OID.
     */
    public List<AttributeType> getAttributeTypesWithName(final String name) {
        return impl.getAttributeTypesWithName(name);
    }

    /**
     * Returns the DIT content rule associated with the provided structural
     * object class, or {@code null} if no rule is defined.
     *
     * @param structuralClass
     *            The structural object class .
     * @return The DIT content rule associated with the provided structural
     *         object class, or {@code null} if no rule is defined.
     */
    public DITContentRule getDITContentRule(final ObjectClass structuralClass) {
        return impl.getDITContentRule(structuralClass);
    }

    /**
     * Returns the DIT content rule with the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the DIT content rule to retrieve.
     * @return The requested DIT content rule.
     * @throws UnknownSchemaElementException
     *             If this is a strict schema and the requested DIT content rule
     *             was not found or if the provided name is ambiguous.
     */
    public DITContentRule getDITContentRule(final String name) {
        return impl.getDITContentRule(name);
    }

    /**
     * Returns an unmodifiable collection containing all of the DIT content
     * rules contained in this schema.
     *
     * @return An unmodifiable collection containing all of the DIT content
     *         rules contained in this schema.
     */
    public Collection<DITContentRule> getDITContentRules() {
        return impl.getDITContentRules();
    }

    /**
     * Returns an unmodifiable collection containing all of the DIT content
     * rules having the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the DIT content rules to retrieve.
     * @return An unmodifiable collection containing all of the DIT content
     *         rules having the specified name or numeric OID.
     */
    public Collection<DITContentRule> getDITContentRulesWithName(final String name) {
        return impl.getDITContentRulesWithName(name);
    }

    /**
     * Returns the DIT structure rule with the specified name or numeric OID.
     *
     * @param ruleID
     *            The ID of the DIT structure rule to retrieve.
     * @return The requested DIT structure rule.
     * @throws UnknownSchemaElementException
     *             If this is a strict schema and the requested DIT structure
     *             rule was not found.
     */
    public DITStructureRule getDITStructureRule(final int ruleID) {
        return impl.getDITStructureRule(ruleID);
    }

    /**
     * Returns an unmodifiable collection containing all of the DIT structure
     * rules associated with the provided name form.
     *
     * @param nameForm
     *            The name form.
     * @return An unmodifiable collection containing all of the DIT structure
     *         rules associated with the provided name form.
     */
    public Collection<DITStructureRule> getDITStructureRules(final NameForm nameForm) {
        return impl.getDITStructureRules(nameForm);
    }

    /**
     * Returns an unmodifiable collection containing all of the DIT structure
     * rules having the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the DIT structure rules to retrieve.
     * @return An unmodifiable collection containing all of the DIT structure
     *         rules having the specified name or numeric OID.
     */
    public Collection<DITStructureRule> getDITStructureRulesWithName(final String name) {
        return impl.getDITStructureRulesWithName(name);
    }

    /**
     * Returns an unmodifiable collection containing all of the DIT structure
     * rules contained in this schema.
     *
     * @return An unmodifiable collection containing all of the DIT structure
     *         rules contained in this schema.
     */
    public Collection<DITStructureRule> getDITStuctureRules() {
        return impl.getDITStuctureRules();
    }

    /**
     * Returns the matching rule with the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the matching rule to retrieve.
     * @return The requested matching rule.
     * @throws UnknownSchemaElementException
     *             If this is a strict schema and the requested matching rule
     *             was not found or if the provided name is ambiguous.
     */
    public MatchingRule getMatchingRule(final String name) {
        return impl.getMatchingRule(name);
    }

    /**
     * Returns an unmodifiable collection containing all of the matching rules
     * contained in this schema.
     *
     * @return An unmodifiable collection containing all of the matching rules
     *         contained in this schema.
     */
    public Collection<MatchingRule> getMatchingRules() {
        return impl.getMatchingRules();
    }

    /**
     * Returns an unmodifiable collection containing all of the matching rules
     * having the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the matching rules to retrieve.
     * @return An unmodifiable collection containing all of the matching rules
     *         having the specified name or numeric OID.
     */
    public Collection<MatchingRule> getMatchingRulesWithName(final String name) {
        return impl.getMatchingRulesWithName(name);
    }

    /**
     * Returns the matching rule use associated with the provided matching rule,
     * or {@code null} if no use is defined.
     *
     * @param matchingRule
     *            The matching rule whose matching rule use is to be retrieved.
     * @return The matching rule use associated with the provided matching rule,
     *         or {@code null} if no use is defined.
     */
    public MatchingRuleUse getMatchingRuleUse(final MatchingRule matchingRule) {
        return getMatchingRuleUse(matchingRule.getOID());
    }

    /**
     * Returns the matching rule use with the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the matching rule use to retrieve.
     * @return The requested matching rule use.
     * @throws UnknownSchemaElementException
     *             If this is a strict schema and the requested matching rule
     *             use was not found or if the provided name is ambiguous.
     */
    public MatchingRuleUse getMatchingRuleUse(final String name) {
        return impl.getMatchingRuleUse(name);
    }

    /**
     * Returns an unmodifiable collection containing all of the matching rule
     * uses contained in this schema.
     *
     * @return An unmodifiable collection containing all of the matching rule
     *         uses contained in this schema.
     */
    public Collection<MatchingRuleUse> getMatchingRuleUses() {
        return impl.getMatchingRuleUses();
    }

    /**
     * Returns an unmodifiable collection containing all of the matching rule
     * uses having the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the matching rule uses to retrieve.
     * @return An unmodifiable collection containing all of the matching rule
     *         uses having the specified name or numeric OID.
     */
    public Collection<MatchingRuleUse> getMatchingRuleUsesWithName(final String name) {
        return impl.getMatchingRuleUsesWithName(name);
    }

    /**
     * Returns the name form with the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the name form to retrieve.
     * @return The requested name form.
     * @throws UnknownSchemaElementException
     *             If this is a strict schema and the requested name form was
     *             not found or if the provided name is ambiguous.
     */
    public NameForm getNameForm(final String name) {
        return impl.getNameForm(name);
    }

    /**
     * Returns an unmodifiable collection containing all of the name forms
     * contained in this schema.
     *
     * @return An unmodifiable collection containing all of the name forms
     *         contained in this schema.
     */
    public Collection<NameForm> getNameForms() {
        return impl.getNameForms();
    }

    /**
     * Returns an unmodifiable collection containing all of the name forms
     * associated with the provided structural object class.
     *
     * @param structuralClass
     *            The structural object class whose name forms are to be
     *            retrieved.
     * @return An unmodifiable collection containing all of the name forms
     *         associated with the provided structural object class.
     */
    public Collection<NameForm> getNameForms(final ObjectClass structuralClass) {
        return impl.getNameForms(structuralClass);
    }

    /**
     * Returns an unmodifiable collection containing all of the name forms
     * having the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the name forms to retrieve.
     * @return An unmodifiable collection containing all of the name forms
     *         having the specified name or numeric OID.
     */
    public Collection<NameForm> getNameFormsWithName(final String name) {
        return impl.getNameFormsWithName(name);
    }

    /**
     * Returns the object class with the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the object class to retrieve.
     * @return The requested object class.
     * @throws UnknownSchemaElementException
     *             If this is a strict schema and the requested object class was
     *             not found or if the provided name is ambiguous.
     */
    public ObjectClass getObjectClass(final String name) {
        return impl.getObjectClass(name);
    }

    /**
     * Returns an unmodifiable collection containing all of the object classes
     * contained in this schema.
     *
     * @return An unmodifiable collection containing all of the object classes
     *         contained in this schema.
     */
    public Collection<ObjectClass> getObjectClasses() {
        return impl.getObjectClasses();
    }

    /**
     * Returns an unmodifiable collection containing all of the object classes
     * having the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the object classes to retrieve.
     * @return An unmodifiable collection containing all of the object classes
     *         having the specified name or numeric OID.
     */
    public Collection<ObjectClass> getObjectClassesWithName(final String name) {
        return impl.getObjectClassesWithName(name);
    }

    /**
     * Returns the user-friendly name of this schema which may be used for
     * debugging purposes. The format of the schema name is not defined but
     * should contain the distinguished name of the subschema sub-entry for
     * those schemas retrieved from a Directory Server.
     *
     * @return The user-friendly name of this schema which may be used for
     *         debugging purposes.
     */
    public String getSchemaName() {
        return impl.getSchemaName();
    }

    /**
     * Returns the syntax with the specified numeric OID.
     *
     * @param numericOID
     *            The OID of the syntax to retrieve.
     * @return The requested syntax.
     * @throws UnknownSchemaElementException
     *             If this is a strict schema and the requested syntax was not
     *             found or if the provided name is ambiguous.
     */
    public Syntax getSyntax(final String numericOID) {
        return impl.getSyntax(this, numericOID);
    }

    /**
     * Returns an unmodifiable collection containing all of the syntaxes
     * contained in this schema.
     *
     * @return An unmodifiable collection containing all of the syntaxes
     *         contained in this schema.
     */
    public Collection<Syntax> getSyntaxes() {
        return impl.getSyntaxes();
    }

    /**
     * Returns an unmodifiable collection containing all of the warnings that
     * were detected when this schema was constructed.
     *
     * @return An unmodifiable collection containing all of the warnings that
     *         were detected when this schema was constructed.
     */
    public Collection<LocalizableMessage> getWarnings() {
        return impl.getWarnings();
    }

    /**
     * Indicates whether or not this schema contains an attribute type with the
     * specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the attribute type.
     * @return {@code true} if this schema contains an attribute type with the
     *         specified name or numeric OID, otherwise {@code false}.
     */
    public boolean hasAttributeType(final String name) {
        return impl.hasAttributeType(name);
    }

    /**
     * Indicates whether or not this schema contains a DIT content rule with the
     * specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the DIT content rule.
     * @return {@code true} if this schema contains a DIT content rule with the
     *         specified name or numeric OID, otherwise {@code false}.
     */
    public boolean hasDITContentRule(final String name) {
        return impl.hasDITContentRule(name);
    }

    /**
     * Indicates whether or not this schema contains a DIT structure rule with
     * the specified rule ID.
     *
     * @param ruleID
     *            The ID of the DIT structure rule.
     * @return {@code true} if this schema contains a DIT structure rule with
     *         the specified rule ID, otherwise {@code false}.
     */
    public boolean hasDITStructureRule(final int ruleID) {
        return impl.hasDITStructureRule(ruleID);
    }

    /**
     * Indicates whether or not this schema contains a matching rule with the
     * specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the matching rule.
     * @return {@code true} if this schema contains a matching rule with the
     *         specified name or numeric OID, otherwise {@code false}.
     */
    public boolean hasMatchingRule(final String name) {
        return impl.hasMatchingRule(name);
    }

    /**
     * Indicates whether or not this schema contains a matching rule use with
     * the specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the matching rule use.
     * @return {@code true} if this schema contains a matching rule use with the
     *         specified name or numeric OID, otherwise {@code false}.
     */
    public boolean hasMatchingRuleUse(final String name) {
        return impl.hasMatchingRuleUse(name);
    }

    /**
     * Indicates whether or not this schema contains a name form with the
     * specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the name form.
     * @return {@code true} if this schema contains a name form with the
     *         specified name or numeric OID, otherwise {@code false}.
     */
    public boolean hasNameForm(final String name) {
        return impl.hasNameForm(name);
    }

    /**
     * Indicates whether or not this schema contains an object class with the
     * specified name or numeric OID.
     *
     * @param name
     *            The name or OID of the object class.
     * @return {@code true} if this schema contains an object class with the
     *         specified name or numeric OID, otherwise {@code false}.
     */
    public boolean hasObjectClass(final String name) {
        return impl.hasObjectClass(name);
    }

    /**
     * Indicates whether or not this schema contains a syntax with the specified
     * numeric OID.
     *
     * @param numericOID
     *            The OID of the syntax.
     * @return {@code true} if this schema contains a syntax with the specified
     *         numeric OID, otherwise {@code false}.
     */
    public boolean hasSyntax(final String numericOID) {
        return impl.hasSyntax(numericOID);
    }

    /**
     * Indicates whether or not this schema is strict.
     * <p>
     * Attribute type queries against non-strict schema always succeed: if the
     * requested attribute type is not found then a temporary attribute type is
     * created automatically having the Octet String syntax and associated
     * matching rules.
     * <p>
     * Strict schema, on the other hand, throw an
     * {@link UnknownSchemaElementException} whenever an attempt is made to
     * retrieve a non-existent attribute type.
     *
     * @return {@code true} if this schema is strict.
     */
    public boolean isStrict() {
        return impl.isStrict();
    }

    /**
     * Adds the definitions of all the schema elements contained in this schema
     * to the provided subschema subentry. Any existing attributes (including
     * schema definitions) contained in the provided entry will be preserved.
     *
     * @param entry
     *            The subschema subentry to which all schema definitions should
     *            be added.
     * @return The updated subschema subentry.
     * @throws NullPointerException
     *             If {@code entry} was {@code null}.
     */
    public Entry toEntry(final Entry entry) {
        Attribute attr = new LinkedAttribute(Schema.ATTR_LDAP_SYNTAXES);
        for (final Syntax syntax : getSyntaxes()) {
            attr.add(syntax.toString());
        }
        if (!attr.isEmpty()) {
            entry.addAttribute(attr);
        }

        attr = new LinkedAttribute(Schema.ATTR_ATTRIBUTE_TYPES);
        for (final AttributeType attributeType : getAttributeTypes()) {
            attr.add(attributeType.toString());
        }
        if (!attr.isEmpty()) {
            entry.addAttribute(attr);
        }

        attr = new LinkedAttribute(Schema.ATTR_OBJECT_CLASSES);
        for (final ObjectClass objectClass : getObjectClasses()) {
            attr.add(objectClass.toString());
        }
        if (!attr.isEmpty()) {
            entry.addAttribute(attr);
        }

        attr = new LinkedAttribute(Schema.ATTR_MATCHING_RULE_USE);
        for (final MatchingRuleUse matchingRuleUse : getMatchingRuleUses()) {
            attr.add(matchingRuleUse.toString());
        }
        if (!attr.isEmpty()) {
            entry.addAttribute(attr);
        }

        attr = new LinkedAttribute(Schema.ATTR_MATCHING_RULES);
        for (final MatchingRule matchingRule : getMatchingRules()) {
            attr.add(matchingRule.toString());
        }
        if (!attr.isEmpty()) {
            entry.addAttribute(attr);
        }

        attr = new LinkedAttribute(Schema.ATTR_DIT_CONTENT_RULES);
        for (final DITContentRule ditContentRule : getDITContentRules()) {
            attr.add(ditContentRule.toString());
        }
        if (!attr.isEmpty()) {
            entry.addAttribute(attr);
        }

        attr = new LinkedAttribute(Schema.ATTR_DIT_STRUCTURE_RULES);
        for (final DITStructureRule ditStructureRule : getDITStuctureRules()) {
            attr.add(ditStructureRule.toString());
        }
        if (!attr.isEmpty()) {
            entry.addAttribute(attr);
        }

        attr = new LinkedAttribute(Schema.ATTR_NAME_FORMS);
        for (final NameForm nameForm : getNameForms()) {
            attr.add(nameForm.toString());
        }
        if (!attr.isEmpty()) {
            entry.addAttribute(attr);
        }

        return entry;
    }

    /**
     * Returns {@code true} if the provided entry is valid according to this
     * schema and the specified schema validation policy.
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
     * @return {@code true} if an entry conforms to this schema based on the
     *         provided schema validation policy.
     */
    public boolean validateEntry(final Entry entry, final SchemaValidationPolicy policy,
            final Collection<LocalizableMessage> errorMessages) {
        // First check that the object classes are recognized and that there is
        // one
        // structural object class.
        ObjectClass structuralObjectClass = null;
        final Attribute objectClassAttribute = entry.getAttribute(objectClass());
        final List<ObjectClass> objectClasses = new LinkedList<ObjectClass>();
        if (objectClassAttribute != null) {
            for (final ByteString v : objectClassAttribute) {
                final String objectClassName = v.toString();
                final ObjectClass objectClass;
                try {
                    objectClass = getObjectClass(objectClassName);
                    objectClasses.add(objectClass);
                } catch (final UnknownSchemaElementException e) {
                    if (policy.checkAttributesAndObjectClasses().needsChecking()) {
                        if (errorMessages != null) {
                            final LocalizableMessage message =
                                    ERR_ENTRY_SCHEMA_UNKNOWN_OBJECT_CLASS.get(entry.getName()
                                            .toString(), objectClassName);
                            errorMessages.add(message);
                        }
                        if (policy.checkAttributesAndObjectClasses().isReject()) {
                            return false;
                        }
                    }
                    continue;
                }

                if (objectClass.getObjectClassType() == ObjectClassType.STRUCTURAL) {
                    if (structuralObjectClass == null
                            || objectClass.isDescendantOf(structuralObjectClass)) {
                        structuralObjectClass = objectClass;
                    } else if (!structuralObjectClass.isDescendantOf(objectClass)) {
                        if (policy.requireSingleStructuralObjectClass().needsChecking()) {
                            if (errorMessages != null) {
                                final LocalizableMessage message =
                                        ERR_ENTRY_SCHEMA_MULTIPLE_STRUCTURAL_CLASSES.get(entry
                                                .getName().toString(), structuralObjectClass
                                                .getNameOrOID(), objectClassName);
                                errorMessages.add(message);
                            }
                            if (policy.requireSingleStructuralObjectClass().isReject()) {
                                return false;
                            }
                        }
                    }
                }
            }
        }

        Collection<DITStructureRule> ditStructureRules = Collections.emptyList();
        DITContentRule ditContentRule = null;

        if (structuralObjectClass == null) {
            if (policy.requireSingleStructuralObjectClass().needsChecking()) {
                if (errorMessages != null) {
                    final LocalizableMessage message =
                            ERR_ENTRY_SCHEMA_NO_STRUCTURAL_CLASS.get(entry.getName().toString());
                    errorMessages.add(message);
                }
                if (policy.requireSingleStructuralObjectClass().isReject()) {
                    return false;
                }
            }
        } else {
            ditContentRule = getDITContentRule(structuralObjectClass);
            if (ditContentRule != null && ditContentRule.isObsolete()) {
                ditContentRule = null;
            }
        }

        // Check entry conforms to object classes and optional content rule.
        if (!checkAttributesAndObjectClasses(entry, policy, errorMessages, objectClasses,
                ditContentRule)) {
            return false;
        }

        // Check that the name of the entry conforms to at least one applicable
        // name
        // form.
        if (policy.checkNameForms().needsChecking() && structuralObjectClass != null) {
            /**
             * There may be multiple name forms registered with this structural
             * object class. However, we need to select only one of the name
             * forms and its corresponding DIT structure rule(s). We will
             * iterate over all the name forms and see if at least one is
             * acceptable before rejecting the entry. DIT structure rules
             * corresponding to other non-acceptable name forms are not applied.
             */
            boolean foundMatchingNameForms = false;
            NameForm nameForm = null;
            final List<LocalizableMessage> nameFormWarnings =
                    (errorMessages != null) ? new LinkedList<LocalizableMessage>() : null;
            for (final NameForm nf : getNameForms(structuralObjectClass)) {
                if (nf.isObsolete()) {
                    continue;
                }

                // If there are any candidate name forms then at least one
                // should be
                // valid.
                foundMatchingNameForms = true;

                if (checkNameForm(entry, policy, nameFormWarnings, nf)) {
                    nameForm = nf;
                    break;
                }
            }

            if (foundMatchingNameForms) {
                if (nameForm != null) {
                    ditStructureRules = getDITStructureRules(nameForm);
                } else {
                    // We couldn't match this entry against any of the name
                    // forms, so
                    // append the reasons why they didn't match and reject if
                    // required.
                    if (errorMessages != null) {
                        errorMessages.addAll(nameFormWarnings);
                    }
                    if (policy.checkNameForms().isReject()) {
                        return false;
                    }
                }
            }
        }

        // Check DIT structure rules - this needs the parent entry.
        if (policy.checkDITStructureRules().needsChecking() && !entry.getName().isRootDN()) {
            boolean foundMatchingRules = false;
            boolean foundValidRule = false;
            final List<LocalizableMessage> ruleWarnings =
                    (errorMessages != null) ? new LinkedList<LocalizableMessage>() : null;
            ObjectClass parentStructuralObjectClass = null;
            boolean parentEntryHasBeenRead = false;
            for (final DITStructureRule rule : ditStructureRules) {
                if (rule.isObsolete()) {
                    continue;
                }

                foundMatchingRules = true;

                // A DIT structure rule with no superiors is automatically
                // valid, so
                // avoid reading the parent.
                if (rule.getSuperiorRules().isEmpty()) {
                    foundValidRule = true;
                    break;
                }

                if (!parentEntryHasBeenRead) {
                    // Don't drop out immediately on failure because there may
                    // be some
                    // applicable rules which do not require the parent entry.
                    parentStructuralObjectClass =
                            getParentStructuralObjectClass(entry, policy, ruleWarnings);
                    parentEntryHasBeenRead = true;
                }

                if (parentStructuralObjectClass != null) {
                    if (checkDITStructureRule(entry, policy, ruleWarnings, rule,
                            structuralObjectClass, parentStructuralObjectClass)) {
                        foundValidRule = true;
                        break;
                    }
                }
            }

            if (foundMatchingRules) {
                if (!foundValidRule) {
                    // We couldn't match this entry against any of the rules, so
                    // append the reasons why they didn't match and reject if
                    // required.
                    if (errorMessages != null) {
                        errorMessages.addAll(ruleWarnings);
                    }
                    if (policy.checkDITStructureRules().isReject()) {
                        return false;
                    }
                }
            } else {
                // There is no DIT structure rule for this entry, but there may
                // be one for the parent entry. If there is such a rule for the
                // parent entry, then this entry will not be valid.

                // The parent won't have been read yet.
                parentStructuralObjectClass =
                        getParentStructuralObjectClass(entry, policy, ruleWarnings);
                if (parentStructuralObjectClass == null) {
                    if (errorMessages != null) {
                        errorMessages.addAll(ruleWarnings);
                    }
                    if (policy.checkDITStructureRules().isReject()) {
                        return false;
                    }
                } else {
                    for (final NameForm nf : getNameForms(parentStructuralObjectClass)) {
                        if (!nf.isObsolete()) {
                            for (final DITStructureRule rule : getDITStructureRules(nf)) {
                                if (!rule.isObsolete()) {
                                    if (errorMessages != null) {
                                        final LocalizableMessage message =
                                                ERR_ENTRY_SCHEMA_DSR_MISSING_DSR.get(entry
                                                        .getName().toString(), rule
                                                        .getNameOrRuleID());
                                        errorMessages.add(message);
                                    }
                                    if (policy.checkDITStructureRules().isReject()) {
                                        return false;
                                    }

                                    // We could break out of the loop here in
                                    // warn mode but
                                    // continuing allows us to collect all
                                    // conflicts.
                                }
                            }
                        }
                    }
                }
            }
        }

        // If we've gotten here, then the entry is acceptable.
        return true;
    }

    private boolean checkAttributesAndObjectClasses(final Entry entry,
            final SchemaValidationPolicy policy,
            final Collection<LocalizableMessage> errorMessages,
            final List<ObjectClass> objectClasses, final DITContentRule ditContentRule) {
        // Check object classes.
        final boolean checkDITContentRule =
                policy.checkDITContentRules().needsChecking() && ditContentRule != null;
        final boolean checkObjectClasses = policy.checkAttributesAndObjectClasses().needsChecking();
        final boolean checkAttributeValues = policy.checkAttributeValues().needsChecking();

        if (checkObjectClasses || checkDITContentRule) {
            for (final ObjectClass objectClass : objectClasses) {
                // Make sure that any auxiliary object classes are permitted by
                // the
                // content rule.
                if (checkDITContentRule) {
                    if (objectClass.getObjectClassType() == ObjectClassType.AUXILIARY
                            && !ditContentRule.getAuxiliaryClasses().contains(objectClass)) {
                        if (errorMessages != null) {
                            final LocalizableMessage message =
                                    ERR_ENTRY_SCHEMA_DCR_PROHIBITED_AUXILIARY_OC.get(entry
                                            .getName().toString(), objectClass.getNameOrOID(),
                                            ditContentRule.getNameOrOID());
                            errorMessages.add(message);
                        }
                        if (policy.checkDITContentRules().isReject()) {
                            return false;
                        }
                    }
                }

                // Make sure that all of the attributes required by the object
                // class are
                // present.
                if (checkObjectClasses) {
                    for (final AttributeType t : objectClass.getDeclaredRequiredAttributes()) {
                        final Attribute a =
                                Attributes.emptyAttribute(AttributeDescription.create(t));
                        if (!entry.containsAttribute(a, null)) {
                            if (errorMessages != null) {
                                final LocalizableMessage message =
                                        ERR_ENTRY_SCHEMA_OC_MISSING_MUST_ATTRIBUTES.get(entry
                                                .getName().toString(), t.getNameOrOID(),
                                                objectClass.getNameOrOID());
                                errorMessages.add(message);
                            }
                            if (policy.checkAttributesAndObjectClasses().isReject()) {
                                return false;
                            }
                        }
                    }
                }
            }

            // Make sure that all of the attributes required by the content rule
            // are
            // present.
            if (checkDITContentRule) {
                for (final AttributeType t : ditContentRule.getRequiredAttributes()) {
                    final Attribute a = Attributes.emptyAttribute(AttributeDescription.create(t));
                    if (!entry.containsAttribute(a, null)) {
                        if (errorMessages != null) {
                            final LocalizableMessage message =
                                    ERR_ENTRY_SCHEMA_DCR_MISSING_MUST_ATTRIBUTES.get(entry
                                            .getName().toString(), t.getNameOrOID(), ditContentRule
                                            .getNameOrOID());
                            errorMessages.add(message);
                        }
                        if (policy.checkDITContentRules().isReject()) {
                            return false;
                        }
                    }
                }

                // Make sure that attributes prohibited by the content rule are
                // not
                // present.
                for (final AttributeType t : ditContentRule.getProhibitedAttributes()) {
                    final Attribute a = Attributes.emptyAttribute(AttributeDescription.create(t));
                    if (entry.containsAttribute(a, null)) {
                        if (errorMessages != null) {
                            final LocalizableMessage message =
                                    ERR_ENTRY_SCHEMA_DCR_PROHIBITED_ATTRIBUTES.get(entry.getName()
                                            .toString(), t.getNameOrOID(), ditContentRule
                                            .getNameOrOID());
                            errorMessages.add(message);
                        }
                        if (policy.checkDITContentRules().isReject()) {
                            return false;
                        }
                    }
                }
            }
        }

        // Check attributes.
        if (checkObjectClasses || checkDITContentRule || checkAttributeValues) {
            for (final Attribute attribute : entry.getAllAttributes()) {
                final AttributeType t = attribute.getAttributeDescription().getAttributeType();

                if (!t.isOperational()) {
                    if (checkObjectClasses || checkDITContentRule) {
                        boolean isAllowed = false;
                        for (final ObjectClass objectClass : objectClasses) {
                            if (objectClass.isRequiredOrOptional(t)) {
                                isAllowed = true;
                                break;
                            }
                        }
                        if (!isAllowed && ditContentRule != null) {
                            if (ditContentRule.isRequiredOrOptional(t)) {
                                isAllowed = true;
                            }
                        }
                        if (!isAllowed) {
                            if (errorMessages != null) {
                                final LocalizableMessage message;
                                if (ditContentRule == null) {
                                    message =
                                            ERR_ENTRY_SCHEMA_OC_DISALLOWED_ATTRIBUTES.get(entry
                                                    .getName().toString(), t.getNameOrOID());
                                } else {
                                    message =
                                            ERR_ENTRY_SCHEMA_DCR_DISALLOWED_ATTRIBUTES.get(entry
                                                    .getName().toString(), t.getNameOrOID(),
                                                    ditContentRule.getNameOrOID());
                                }
                                errorMessages.add(message);
                            }
                            if (policy.checkAttributesAndObjectClasses().isReject()
                                    || policy.checkDITContentRules().isReject()) {
                                return false;
                            }
                        }
                    }
                }

                // Check all attributes contain an appropriate number of values.
                if (checkAttributeValues) {
                    final int sz = attribute.size();

                    if (sz == 0) {
                        if (errorMessages != null) {
                            final LocalizableMessage message =
                                    ERR_ENTRY_SCHEMA_AT_EMPTY_ATTRIBUTE.get(entry.getName()
                                            .toString(), t.getNameOrOID());
                            errorMessages.add(message);
                        }
                        if (policy.checkAttributeValues().isReject()) {
                            return false;
                        }
                    } else if (sz > 1 && t.isSingleValue()) {
                        if (errorMessages != null) {
                            final LocalizableMessage message =
                                    ERR_ENTRY_SCHEMA_AT_SINGLE_VALUED_ATTRIBUTE.get(entry.getName()
                                            .toString(), t.getNameOrOID());
                            errorMessages.add(message);
                        }
                        if (policy.checkAttributeValues().isReject()) {
                            return false;
                        }
                    }
                }
            }
        }

        // If we've gotten here, then things are OK.
        return true;
    }

    private boolean checkDITStructureRule(final Entry entry, final SchemaValidationPolicy policy,
            final List<LocalizableMessage> ruleWarnings, final DITStructureRule rule,
            final ObjectClass structuralObjectClass, final ObjectClass parentStructuralObjectClass) {
        boolean matchFound = false;
        for (final DITStructureRule parentRule : rule.getSuperiorRules()) {
            if (parentRule.getNameForm().getStructuralClass().equals(parentStructuralObjectClass)) {
                matchFound = true;
            }
        }

        if (!matchFound) {
            if (ruleWarnings != null) {
                final LocalizableMessage message =
                        ERR_ENTRY_SCHEMA_DSR_ILLEGAL_OC.get(entry.getName().toString(), rule
                                .getNameOrRuleID(), structuralObjectClass.getNameOrOID(),
                                parentStructuralObjectClass.getNameOrOID());
                ruleWarnings.add(message);
            }
            return false;
        }

        return true;
    }

    private boolean checkNameForm(final Entry entry, final SchemaValidationPolicy policy,
            final List<LocalizableMessage> nameFormWarnings, final NameForm nameForm) {
        final RDN rdn = entry.getName().rdn();
        if (rdn != null) {
            // Make sure that all the required AVAs are present.
            for (final AttributeType t : nameForm.getRequiredAttributes()) {
                if (rdn.getAttributeValue(t) == null) {
                    if (nameFormWarnings != null) {
                        final LocalizableMessage message =
                                ERR_ENTRY_SCHEMA_NF_MISSING_MUST_ATTRIBUTES.get(entry.getName()
                                        .toString(), t.getNameOrOID(), nameForm.getNameOrOID());
                        nameFormWarnings.add(message);
                    }
                    return false;
                }
            }

            // Make sure that all AVAs in the RDN are allowed.
            for (final AVA ava : rdn) {
                final AttributeType t = ava.getAttributeType();
                if (!nameForm.isRequiredOrOptional(t)) {
                    if (nameFormWarnings != null) {
                        final LocalizableMessage message =
                                ERR_ENTRY_SCHEMA_NF_DISALLOWED_ATTRIBUTES.get(entry.getName()
                                        .toString(), t.getNameOrOID(), nameForm.getNameOrOID());
                        nameFormWarnings.add(message);
                    }
                    return false;
                }
            }
        }

        // If we've gotten here, then things are OK.
        return true;
    }

    private ObjectClass getParentStructuralObjectClass(final Entry entry,
            final SchemaValidationPolicy policy, final List<LocalizableMessage> ruleWarnings) {
        final Entry parentEntry;
        try {
            parentEntry =
                    policy.checkDITStructureRulesEntryResolver().getEntry(entry.getName().parent());
        } catch (final ErrorResultException e) {
            if (ruleWarnings != null) {
                final LocalizableMessage message =
                        ERR_ENTRY_SCHEMA_DSR_PARENT_NOT_FOUND.get(entry.getName().toString(), e
                                .getResult().getDiagnosticMessage());
                ruleWarnings.add(message);
            }
            return null;
        }

        final ObjectClass parentStructuralObjectClass =
                Entries.getStructuralObjectClass(parentEntry, this);
        if (parentStructuralObjectClass == null) {
            if (ruleWarnings != null) {
                final LocalizableMessage message =
                        ERR_ENTRY_SCHEMA_DSR_NO_PARENT_OC.get(entry.getName().toString());
                ruleWarnings.add(message);
            }
            return null;
        }
        return parentStructuralObjectClass;
    }
}
