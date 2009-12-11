/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.sdk.schema;



import static com.sun.opends.sdk.messages.Messages.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.opends.sdk.*;
import org.opends.sdk.requests.Requests;
import org.opends.sdk.requests.SearchRequest;
import org.opends.sdk.responses.Responses;
import org.opends.sdk.responses.Result;
import org.opends.sdk.responses.SearchResultEntry;

import com.sun.opends.sdk.util.ResultChain;
import com.sun.opends.sdk.util.ResultTransformer;
import com.sun.opends.sdk.util.StaticUtils;



/**
 * This class defines a data structure that holds information about the
 * components of the LDAP schema. It includes the following kinds of
 * elements:
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
public final class Schema
{
  private static class EmptyImpl implements Impl
  {
    private final SchemaCompatOptions options;



    private EmptyImpl()
    {
      this.options = SchemaCompatOptions.defaultOptions();
    }



    public AttributeType getAttributeType(String name)
    {
      // Construct an placeholder attribute type with the given name,
      // the default matching rule, and the default syntax. The OID of
      // the attribute will be the normalized OID alias with "-oid"
      // appended to the given name.
      final StringBuilder builder = new StringBuilder(name.length() + 4);
      StaticUtils.toLowerCase(name, builder);
      builder.append("-oid");
      final String noid = builder.toString();

      return new AttributeType(noid, Collections.singletonList(name),
          "", Schema.getDefaultMatchingRule(), Schema
              .getDefaultSyntax());
    }



    public Collection<AttributeType> getAttributeTypes()
    {
      return Collections.emptyList();
    }



    public List<AttributeType> getAttributeTypesByName(String name)
    {
      return Collections.emptyList();
    }



    public DITContentRule getDITContentRule(String name)
        throws UnknownSchemaElementException
    {
      throw new UnknownSchemaElementException(WARN_DCR_UNKNOWN
          .get(name));
    }



    public Collection<DITContentRule> getDITContentRules()
    {
      return Collections.emptyList();
    }



    public Collection<DITContentRule> getDITContentRulesByName(
        String name)
    {
      return Collections.emptyList();
    }



    public DITStructureRule getDITStructureRule(int ruleID)
        throws UnknownSchemaElementException
    {
      throw new UnknownSchemaElementException(WARN_DSR_UNKNOWN
          .get(String.valueOf(ruleID)));
    }



    public Collection<DITStructureRule> getDITStructureRulesByName(
        String name)
    {
      return Collections.emptyList();
    }



    public Collection<DITStructureRule> getDITStructureRulesByNameForm(
        NameForm nameForm)
    {
      return Collections.emptyList();
    }



    public Collection<DITStructureRule> getDITStuctureRules()
    {
      return Collections.emptyList();
    }



    public MatchingRule getMatchingRule(String name)
        throws UnknownSchemaElementException
    {
      throw new UnknownSchemaElementException(WARN_MR_UNKNOWN.get(name));
    }



    public Collection<MatchingRule> getMatchingRules()
    {
      return Collections.emptyList();
    }



    public Collection<MatchingRule> getMatchingRulesByName(String name)
    {
      return Collections.emptyList();
    }



    public MatchingRuleUse getMatchingRuleUse(MatchingRule matchingRule)
        throws UnknownSchemaElementException
    {
      return getMatchingRuleUse(matchingRule.getOID());
    }



    public MatchingRuleUse getMatchingRuleUse(String name)
        throws UnknownSchemaElementException
    {
      throw new UnknownSchemaElementException(WARN_MRU_UNKNOWN
          .get(name));
    }



    public Collection<MatchingRuleUse> getMatchingRuleUses()
    {
      return Collections.emptyList();
    }



    public Collection<MatchingRuleUse> getMatchingRuleUsesByName(
        String name)
    {
      return Collections.emptyList();
    }



    public NameForm getNameForm(String name)
        throws UnknownSchemaElementException
    {
      throw new UnknownSchemaElementException(WARN_NAMEFORM_UNKNOWN
          .get(name));
    }



    public Collection<NameForm> getNameFormByObjectClass(
        ObjectClass structuralClass)
    {
      return Collections.emptyList();
    }



    public Collection<NameForm> getNameForms()
    {
      return Collections.emptyList();
    }



    public Collection<NameForm> getNameFormsByName(String name)
    {
      return Collections.emptyList();
    }



    public ObjectClass getObjectClass(String name)
        throws UnknownSchemaElementException
    {
      throw new UnknownSchemaElementException(WARN_OBJECTCLASS_UNKNOWN
          .get(name));
    }



    public Collection<ObjectClass> getObjectClasses()
    {
      return Collections.emptyList();
    }



    public Collection<ObjectClass> getObjectClassesByName(String name)
    {
      return Collections.emptyList();
    }



    public SchemaCompatOptions getSchemaCompatOptions()
    {
      return options;
    }



    public Syntax getSyntax(String numericOID)
    {
      // Fake up a syntax substituted by the default syntax.
      return new Syntax(numericOID);
    }



    public Collection<Syntax> getSyntaxes()
    {
      return Collections.emptyList();
    }



    public Collection<LocalizableMessage> getWarnings()
    {
      return Collections.emptyList();
    }



    public boolean hasAttributeType(String name)
    {
      // In theory a non-strict schema always contains the requested
      // attribute type, so we could always return true. However, we
      // should provide a way for callers to differentiate between a
      // real attribute type and a faked up attribute type.
      return false;
    }



    public boolean hasDITContentRule(String name)
    {
      return false;
    }



    public boolean hasDITStructureRule(int ruleID)
    {
      return false;
    }



    public boolean hasMatchingRule(String name)
    {
      return false;
    }



    public boolean hasMatchingRuleUse(String name)
    {
      return false;
    }



    public boolean hasNameForm(String name)
    {
      return false;
    }



    public boolean hasObjectClass(String name)
    {
      return false;
    }



    public boolean hasSyntax(String numericOID)
    {
      return false;
    }



    public boolean isStrict()
    {
      return false;
    }
  }



  private static interface Impl
  {
    AttributeType getAttributeType(String name)
        throws UnknownSchemaElementException;



    Collection<AttributeType> getAttributeTypes();



    List<AttributeType> getAttributeTypesByName(String name);



    DITContentRule getDITContentRule(String name)
        throws UnknownSchemaElementException;



    Collection<DITContentRule> getDITContentRules();



    Collection<DITContentRule> getDITContentRulesByName(String name);



    DITStructureRule getDITStructureRule(int ruleID)
        throws UnknownSchemaElementException;



    Collection<DITStructureRule> getDITStructureRulesByName(String name);



    Collection<DITStructureRule> getDITStructureRulesByNameForm(
        NameForm nameForm);



    Collection<DITStructureRule> getDITStuctureRules();



    MatchingRule getMatchingRule(String name)
        throws UnknownSchemaElementException;



    Collection<MatchingRule> getMatchingRules();



    Collection<MatchingRule> getMatchingRulesByName(String name);



    MatchingRuleUse getMatchingRuleUse(MatchingRule matchingRule)
        throws UnknownSchemaElementException;



    MatchingRuleUse getMatchingRuleUse(String name)
        throws UnknownSchemaElementException;



    Collection<MatchingRuleUse> getMatchingRuleUses();



    Collection<MatchingRuleUse> getMatchingRuleUsesByName(String name);



    NameForm getNameForm(String name)
        throws UnknownSchemaElementException;



    Collection<NameForm> getNameFormByObjectClass(
        ObjectClass structuralClass);



    Collection<NameForm> getNameForms();



    Collection<NameForm> getNameFormsByName(String name);



    ObjectClass getObjectClass(String name)
        throws UnknownSchemaElementException;



    Collection<ObjectClass> getObjectClasses();



    Collection<ObjectClass> getObjectClassesByName(String name);



    SchemaCompatOptions getSchemaCompatOptions();



    Syntax getSyntax(String numericOID)
        throws UnknownSchemaElementException;



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



  private static class NonStrictImpl implements Impl
  {
    private final Impl strictImpl;



    private NonStrictImpl(Impl strictImpl)
    {
      this.strictImpl = strictImpl;
    }



    public AttributeType getAttributeType(String name)
        throws UnknownSchemaElementException
    {
      if (!strictImpl.hasAttributeType(name))
      {
        // Construct an placeholder attribute type with the given name,
        // the default matching rule, and the default syntax. The OID of
        // the attribute will be the normalized OID alias with "-oid"
        // appended to the given name.
        final StringBuilder builder = new StringBuilder(
            name.length() + 4);
        StaticUtils.toLowerCase(name, builder);
        builder.append("-oid");
        final String noid = builder.toString();

        return new AttributeType(noid, Collections.singletonList(name),
            "", Schema.getDefaultMatchingRule(), Schema
                .getDefaultSyntax());
      }
      return strictImpl.getAttributeType(name);
    }



    public Collection<AttributeType> getAttributeTypes()
    {
      return strictImpl.getAttributeTypes();
    }



    public List<AttributeType> getAttributeTypesByName(String name)
    {
      return strictImpl.getAttributeTypesByName(name);
    }



    public DITContentRule getDITContentRule(String name)
        throws UnknownSchemaElementException
    {
      return strictImpl.getDITContentRule(name);
    }



    public Collection<DITContentRule> getDITContentRules()
    {
      return strictImpl.getDITContentRules();
    }



    public Collection<DITContentRule> getDITContentRulesByName(
        String name)
    {
      return strictImpl.getDITContentRulesByName(name);
    }



    public DITStructureRule getDITStructureRule(int ruleID)
        throws UnknownSchemaElementException
    {
      return strictImpl.getDITStructureRule(ruleID);
    }



    public Collection<DITStructureRule> getDITStructureRulesByName(
        String name)
    {
      return strictImpl.getDITStructureRulesByName(name);
    }



    public Collection<DITStructureRule> getDITStructureRulesByNameForm(
        NameForm nameForm)
    {
      return strictImpl.getDITStructureRulesByNameForm(nameForm);
    }



    public Collection<DITStructureRule> getDITStuctureRules()
    {
      return strictImpl.getDITStuctureRules();
    }



    public MatchingRule getMatchingRule(String name)
        throws UnknownSchemaElementException
    {
      return strictImpl.getMatchingRule(name);
    }



    public Collection<MatchingRule> getMatchingRules()
    {
      return strictImpl.getMatchingRules();
    }



    public Collection<MatchingRule> getMatchingRulesByName(String name)
    {
      return strictImpl.getMatchingRulesByName(name);
    }



    public MatchingRuleUse getMatchingRuleUse(MatchingRule matchingRule)
        throws UnknownSchemaElementException
    {
      return strictImpl.getMatchingRuleUse(matchingRule);
    }



    public MatchingRuleUse getMatchingRuleUse(String name)
        throws UnknownSchemaElementException
    {
      return strictImpl.getMatchingRuleUse(name);
    }



    public Collection<MatchingRuleUse> getMatchingRuleUses()
    {
      return strictImpl.getMatchingRuleUses();
    }



    public Collection<MatchingRuleUse> getMatchingRuleUsesByName(
        String name)
    {
      return strictImpl.getMatchingRuleUsesByName(name);
    }



    public NameForm getNameForm(String name)
        throws UnknownSchemaElementException
    {
      return strictImpl.getNameForm(name);
    }



    public Collection<NameForm> getNameFormByObjectClass(
        ObjectClass structuralClass)
    {
      return strictImpl.getNameFormByObjectClass(structuralClass);
    }



    public Collection<NameForm> getNameForms()
    {
      return strictImpl.getNameForms();
    }



    public Collection<NameForm> getNameFormsByName(String name)
    {
      return strictImpl.getNameFormsByName(name);
    }



    public ObjectClass getObjectClass(String name)
        throws UnknownSchemaElementException
    {
      return strictImpl.getObjectClass(name);
    }



    public Collection<ObjectClass> getObjectClasses()
    {
      return strictImpl.getObjectClasses();
    }



    public Collection<ObjectClass> getObjectClassesByName(String name)
    {
      return strictImpl.getObjectClassesByName(name);
    }



    public SchemaCompatOptions getSchemaCompatOptions()
    {
      return strictImpl.getSchemaCompatOptions();
    }



    public Syntax getSyntax(String numericOID)
    {
      if (!strictImpl.hasSyntax(numericOID))
      {
        return new Syntax(numericOID);
      }
      return strictImpl.getSyntax(numericOID);
    }



    public Collection<Syntax> getSyntaxes()
    {
      return strictImpl.getSyntaxes();
    }



    public Collection<LocalizableMessage> getWarnings()
    {
      return strictImpl.getWarnings();
    }



    public boolean hasAttributeType(String name)
    {
      // In theory a non-strict schema always contains the requested
      // attribute type, so we could always return true. However, we
      // should provide a way for callers to differentiate between a
      // real attribute type and a faked up attribute type.
      return strictImpl.hasAttributeType(name);
    }



    public boolean hasDITContentRule(String name)
    {
      return strictImpl.hasDITContentRule(name);
    }



    public boolean hasDITStructureRule(int ruleID)
    {
      return strictImpl.hasDITStructureRule(ruleID);
    }



    public boolean hasMatchingRule(String name)
    {
      return strictImpl.hasMatchingRule(name);
    }



    public boolean hasMatchingRuleUse(String name)
    {
      return strictImpl.hasMatchingRuleUse(name);
    }



    public boolean hasNameForm(String name)
    {
      return strictImpl.hasNameForm(name);
    }



    public boolean hasObjectClass(String name)
    {
      return strictImpl.hasObjectClass(name);
    }



    public boolean hasSyntax(String numericOID)
    {
      return strictImpl.hasSyntax(numericOID);
    }



    public boolean isStrict()
    {
      return false;
    }
  }



  private static class StrictImpl implements Impl
  {
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

    private final SchemaCompatOptions options;

    private final List<LocalizableMessage> warnings;



    private StrictImpl(Map<String, Syntax> numericOID2Syntaxes,
        Map<String, MatchingRule> numericOID2MatchingRules,
        Map<String, MatchingRuleUse> numericOID2MatchingRuleUses,
        Map<String, AttributeType> numericOID2AttributeTypes,
        Map<String, ObjectClass> numericOID2ObjectClasses,
        Map<String, NameForm> numericOID2NameForms,
        Map<String, DITContentRule> numericOID2ContentRules,
        Map<Integer, DITStructureRule> id2StructureRules,
        Map<String, List<MatchingRule>> name2MatchingRules,
        Map<String, List<MatchingRuleUse>> name2MatchingRuleUses,
        Map<String, List<AttributeType>> name2AttributeTypes,
        Map<String, List<ObjectClass>> name2ObjectClasses,
        Map<String, List<NameForm>> name2NameForms,
        Map<String, List<DITContentRule>> name2ContentRules,
        Map<String, List<DITStructureRule>> name2StructureRules,
        Map<String, List<NameForm>> objectClass2NameForms,
        Map<String, List<DITStructureRule>> nameForm2StructureRules,
        SchemaCompatOptions options, List<LocalizableMessage> warnings)
    {
      this.numericOID2Syntaxes = Collections
          .unmodifiableMap(numericOID2Syntaxes);
      this.numericOID2MatchingRules = Collections
          .unmodifiableMap(numericOID2MatchingRules);
      this.numericOID2MatchingRuleUses = Collections
          .unmodifiableMap(numericOID2MatchingRuleUses);
      this.numericOID2AttributeTypes = Collections
          .unmodifiableMap(numericOID2AttributeTypes);
      this.numericOID2ObjectClasses = Collections
          .unmodifiableMap(numericOID2ObjectClasses);
      this.numericOID2NameForms = Collections
          .unmodifiableMap(numericOID2NameForms);
      this.numericOID2ContentRules = Collections
          .unmodifiableMap(numericOID2ContentRules);
      this.id2StructureRules = Collections
          .unmodifiableMap(id2StructureRules);
      this.name2MatchingRules = Collections
          .unmodifiableMap(name2MatchingRules);
      this.name2MatchingRuleUses = Collections
          .unmodifiableMap(name2MatchingRuleUses);
      this.name2AttributeTypes = Collections
          .unmodifiableMap(name2AttributeTypes);
      this.name2ObjectClasses = Collections
          .unmodifiableMap(name2ObjectClasses);
      this.name2NameForms = Collections.unmodifiableMap(name2NameForms);
      this.name2ContentRules = Collections
          .unmodifiableMap(name2ContentRules);
      this.name2StructureRules = Collections
          .unmodifiableMap(name2StructureRules);
      this.objectClass2NameForms = Collections
          .unmodifiableMap(objectClass2NameForms);
      this.nameForm2StructureRules = Collections
          .unmodifiableMap(nameForm2StructureRules);
      this.options = options;
      this.warnings = Collections.unmodifiableList(warnings);
    }



    public AttributeType getAttributeType(String name)
        throws UnknownSchemaElementException
    {
      final AttributeType type = numericOID2AttributeTypes.get(name);
      if (type != null)
      {
        return type;
      }
      final List<AttributeType> attributes = name2AttributeTypes
          .get(StaticUtils.toLowerCase(name));
      if (attributes != null)
      {
        if (attributes.size() == 1)
        {
          return attributes.get(0);
        }
        throw new UnknownSchemaElementException(
            WARN_ATTR_TYPE_AMBIGIOUS.get(name));
      }
      throw new UnknownSchemaElementException(WARN_ATTR_TYPE_UNKNOWN
          .get(name));
    }



    public Collection<AttributeType> getAttributeTypes()
    {
      return numericOID2AttributeTypes.values();
    }



    public List<AttributeType> getAttributeTypesByName(String name)
    {
      final List<AttributeType> attributes = name2AttributeTypes
          .get(StaticUtils.toLowerCase(name));
      if (attributes == null)
      {
        return Collections.emptyList();
      }
      else
      {
        return attributes;
      }
    }



    public DITContentRule getDITContentRule(String name)
        throws UnknownSchemaElementException
    {
      final DITContentRule rule = numericOID2ContentRules.get(name);
      if (rule != null)
      {
        return rule;
      }
      final List<DITContentRule> rules = name2ContentRules
          .get(StaticUtils.toLowerCase(name));
      if (rules != null)
      {
        if (rules.size() == 1)
        {
          return rules.get(0);
        }
        throw new UnknownSchemaElementException(WARN_DCR_AMBIGIOUS
            .get(name));
      }
      throw new UnknownSchemaElementException(WARN_DCR_UNKNOWN
          .get(name));
    }



    public Collection<DITContentRule> getDITContentRules()
    {
      return numericOID2ContentRules.values();
    }



    public Collection<DITContentRule> getDITContentRulesByName(
        String name)
    {
      final List<DITContentRule> rules = name2ContentRules
          .get(StaticUtils.toLowerCase(name));
      if (rules == null)
      {
        return Collections.emptyList();
      }
      else
      {
        return rules;
      }
    }



    public DITStructureRule getDITStructureRule(int ruleID)
        throws UnknownSchemaElementException
    {
      final DITStructureRule rule = id2StructureRules.get(ruleID);
      if (rule == null)
      {
        throw new UnknownSchemaElementException(WARN_DSR_UNKNOWN
            .get(String.valueOf(ruleID)));
      }
      return rule;
    }



    public Collection<DITStructureRule> getDITStructureRulesByName(
        String name)
    {
      final List<DITStructureRule> rules = name2StructureRules
          .get(StaticUtils.toLowerCase(name));
      if (rules == null)
      {
        return Collections.emptyList();
      }
      else
      {
        return rules;
      }
    }



    public Collection<DITStructureRule> getDITStructureRulesByNameForm(
        NameForm nameForm)
    {
      final List<DITStructureRule> rules = nameForm2StructureRules
          .get(nameForm.getOID());
      if (rules == null)
      {
        return Collections.emptyList();
      }
      else
      {
        return rules;
      }
    }



    public Collection<DITStructureRule> getDITStuctureRules()
    {
      return id2StructureRules.values();
    }



    public MatchingRule getMatchingRule(String name)
        throws UnknownSchemaElementException
    {
      final MatchingRule rule = numericOID2MatchingRules.get(name);
      if (rule != null)
      {
        return rule;
      }
      final List<MatchingRule> rules = name2MatchingRules
          .get(StaticUtils.toLowerCase(name));
      if (rules != null)
      {
        if (rules.size() == 1)
        {
          return rules.get(0);
        }
        throw new UnknownSchemaElementException(WARN_MR_AMBIGIOUS
            .get(name));
      }
      throw new UnknownSchemaElementException(WARN_MR_UNKNOWN.get(name));
    }



    public Collection<MatchingRule> getMatchingRules()
    {
      return numericOID2MatchingRules.values();
    }



    public Collection<MatchingRule> getMatchingRulesByName(String name)
    {
      final List<MatchingRule> rules = name2MatchingRules
          .get(StaticUtils.toLowerCase(name));
      if (rules == null)
      {
        return Collections.emptyList();
      }
      else
      {
        return rules;
      }
    }



    public MatchingRuleUse getMatchingRuleUse(MatchingRule matchingRule)
        throws UnknownSchemaElementException
    {
      return getMatchingRuleUse(matchingRule.getOID());
    }



    public MatchingRuleUse getMatchingRuleUse(String name)
        throws UnknownSchemaElementException
    {
      final MatchingRuleUse rule = numericOID2MatchingRuleUses
          .get(name);
      if (rule != null)
      {
        return rule;
      }
      final List<MatchingRuleUse> uses = name2MatchingRuleUses
          .get(StaticUtils.toLowerCase(name));
      if (uses != null)
      {
        if (uses.size() == 1)
        {
          return uses.get(0);
        }
        throw new UnknownSchemaElementException(WARN_MRU_AMBIGIOUS
            .get(name));
      }
      throw new UnknownSchemaElementException(WARN_MRU_UNKNOWN
          .get(name));
    }



    public Collection<MatchingRuleUse> getMatchingRuleUses()
    {
      return numericOID2MatchingRuleUses.values();
    }



    public Collection<MatchingRuleUse> getMatchingRuleUsesByName(
        String name)
    {
      final List<MatchingRuleUse> rules = name2MatchingRuleUses
          .get(StaticUtils.toLowerCase(name));
      if (rules == null)
      {
        return Collections.emptyList();
      }
      else
      {
        return rules;
      }
    }



    public NameForm getNameForm(String name)
        throws UnknownSchemaElementException
    {
      final NameForm form = numericOID2NameForms.get(name);
      if (form != null)
      {
        return form;
      }
      final List<NameForm> forms = name2NameForms.get(StaticUtils
          .toLowerCase(name));
      if (forms != null)
      {
        if (forms.size() == 1)
        {
          return forms.get(0);
        }
        throw new UnknownSchemaElementException(WARN_NAMEFORM_AMBIGIOUS
            .get(name));
      }
      throw new UnknownSchemaElementException(WARN_NAMEFORM_UNKNOWN
          .get(name));
    }



    public Collection<NameForm> getNameFormByObjectClass(
        ObjectClass structuralClass)
    {
      final List<NameForm> forms = objectClass2NameForms
          .get(structuralClass.getOID());
      if (forms == null)
      {
        return Collections.emptyList();
      }
      else
      {
        return forms;
      }
    }



    public Collection<NameForm> getNameForms()
    {
      return numericOID2NameForms.values();
    }



    public Collection<NameForm> getNameFormsByName(String name)
    {
      final List<NameForm> forms = name2NameForms.get(StaticUtils
          .toLowerCase(name));
      if (forms == null)
      {
        return Collections.emptyList();
      }
      else
      {
        return forms;
      }
    }



    public ObjectClass getObjectClass(String name)
        throws UnknownSchemaElementException
    {
      final ObjectClass oc = numericOID2ObjectClasses.get(name);
      if (oc != null)
      {
        return oc;
      }
      final List<ObjectClass> classes = name2ObjectClasses
          .get(StaticUtils.toLowerCase(name));
      if (classes != null)
      {
        if (classes.size() == 1)
        {
          return classes.get(0);
        }
        throw new UnknownSchemaElementException(
            WARN_OBJECTCLASS_AMBIGIOUS.get(name));
      }
      throw new UnknownSchemaElementException(WARN_OBJECTCLASS_UNKNOWN
          .get(name));
    }



    public Collection<ObjectClass> getObjectClasses()
    {
      return numericOID2ObjectClasses.values();
    }



    public Collection<ObjectClass> getObjectClassesByName(String name)
    {
      final List<ObjectClass> classes = name2ObjectClasses
          .get(StaticUtils.toLowerCase(name));
      if (classes == null)
      {
        return Collections.emptyList();
      }
      else
      {
        return classes;
      }
    }



    public SchemaCompatOptions getSchemaCompatOptions()
    {
      return options;
    }



    public Syntax getSyntax(String numericOID)
        throws UnknownSchemaElementException
    {
      final Syntax syntax = numericOID2Syntaxes.get(numericOID);
      if (syntax == null)
      {
        throw new UnknownSchemaElementException(WARN_SYNTAX_UNKNOWN
            .get(numericOID));
      }
      return syntax;
    }



    public Collection<Syntax> getSyntaxes()
    {
      return numericOID2Syntaxes.values();
    }



    public Collection<LocalizableMessage> getWarnings()
    {
      return warnings;
    }



    public boolean hasAttributeType(String name)
    {
      if (numericOID2AttributeTypes.containsKey(name))
      {
        return true;
      }
      final List<AttributeType> attributes = name2AttributeTypes
          .get(StaticUtils.toLowerCase(name));
      return attributes != null && attributes.size() == 1;
    }



    public boolean hasDITContentRule(String name)
    {
      if (numericOID2ContentRules.containsKey(name))
      {
        return true;
      }
      final List<DITContentRule> rules = name2ContentRules
          .get(StaticUtils.toLowerCase(name));
      return rules != null && rules.size() == 1;
    }



    public boolean hasDITStructureRule(int ruleID)
    {
      return id2StructureRules.containsKey(ruleID);
    }



    public boolean hasMatchingRule(String name)
    {
      if (numericOID2MatchingRules.containsKey(name))
      {
        return true;
      }
      final List<MatchingRule> rules = name2MatchingRules
          .get(StaticUtils.toLowerCase(name));
      return rules != null && rules.size() == 1;
    }



    public boolean hasMatchingRuleUse(String name)
    {
      if (numericOID2MatchingRuleUses.containsKey(name))
      {
        return true;
      }
      final List<MatchingRuleUse> uses = name2MatchingRuleUses
          .get(StaticUtils.toLowerCase(name));
      return uses != null && uses.size() == 1;
    }



    public boolean hasNameForm(String name)
    {
      if (numericOID2NameForms.containsKey(name))
      {
        return true;
      }
      final List<NameForm> forms = name2NameForms.get(StaticUtils
          .toLowerCase(name));
      return forms != null && forms.size() == 1;
    }



    public boolean hasObjectClass(String name)
    {
      if (numericOID2ObjectClasses.containsKey(name))
      {
        return true;
      }
      final List<ObjectClass> classes = name2ObjectClasses
          .get(StaticUtils.toLowerCase(name));
      return classes != null && classes.size() == 1;
    }



    public boolean hasSyntax(String numericOID)
    {
      return numericOID2Syntaxes.containsKey(numericOID);
    }



    public boolean isStrict()
    {
      return true;
    }
  }



  private static final Schema CORE_SCHEMA = CoreSchemaImpl
      .getInstance();

  private static final Schema EMPTY_SCHEMA = new Schema(new EmptyImpl());

  private static volatile Schema DEFAULT_SCHEMA = CoreSchemaImpl
      .getInstance();

  private static final AttributeDescription ATTR_ATTRIBUTE_TYPES = AttributeDescription
      .valueOf("attributeTypes");

  private static final AttributeDescription ATTR_DIT_CONTENT_RULES = AttributeDescription
      .valueOf("dITContentRules");

  private static final AttributeDescription ATTR_DIT_STRUCTURE_RULES = AttributeDescription
      .valueOf("dITStructureRules");

  private static final AttributeDescription ATTR_LDAP_SYNTAXES = AttributeDescription
      .valueOf("ldapSyntaxes");

  private static final AttributeDescription ATTR_MATCHING_RULE_USE = AttributeDescription
      .valueOf("matchingRuleUse");

  private static final AttributeDescription ATTR_MATCHING_RULES = AttributeDescription
      .valueOf("matchingRules");

  private static final AttributeDescription ATTR_NAME_FORMS = AttributeDescription
      .valueOf("nameForms");

  private static final AttributeDescription ATTR_OBJECT_CLASSES = AttributeDescription
      .valueOf("objectClasses");

  private static final AttributeDescription ATTR_SUBSCHEMA_SUBENTRY = AttributeDescription
      .valueOf("subschemaSubentry");

  private static final String[] SUBSCHEMA_ATTRS = new String[] {
      ATTR_LDAP_SYNTAXES.toString(), ATTR_ATTRIBUTE_TYPES.toString(),
      ATTR_DIT_CONTENT_RULES.toString(),
      ATTR_DIT_STRUCTURE_RULES.toString(),
      ATTR_MATCHING_RULE_USE.toString(),
      ATTR_MATCHING_RULES.toString(), ATTR_NAME_FORMS.toString(),
      ATTR_OBJECT_CLASSES.toString() };

  private static final Filter SUBSCHEMA_FILTER = Filter
      .newEqualityMatchFilter(CoreSchema.getObjectClassAttributeType()
          .getNameOrOID(), CoreSchema.getSubschemaObjectClass()
          .getNameOrOID());

  private static final String[] SUBSCHEMA_SUBENTRY_ATTRS = new String[] { ATTR_SUBSCHEMA_SUBENTRY
      .toString() };



  /**
   * Reads the schema from the Directory Server contained in the named
   * subschema sub-entry using the provided connection.
   * <p>
   * If the requested schema is not returned by the Directory Server
   * then the request will fail with an {@link EntryNotFoundException}.
   * More specifically, this method will never return {@code null}.
   *
   * @param connection
   *          A connection to the Directory Server whose schema is to be
   *          read.
   * @param name
   *          The distinguished name of the subschema sub-entry.
   * @return The schema from the Directory Server.
   * @throws ErrorResultException
   *           If the result code indicates that the request failed for
   *           some reason.
   * @throws InterruptedException
   *           If the current thread was interrupted while waiting.
   * @throws UnsupportedOperationException
   *           If the connection does not support search operations.
   * @throws IllegalStateException
   *           If the connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If the {@code connection} or {@code name} was {@code
   *           null}.
   */
  public static Schema readSchema(Connection connection, DN name)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final SearchRequest request = getReadSchemaSearchRequest(name);
    final Entry entry = connection.searchSingleEntry(request);
    return valueOf(entry);
  }



  /**
   * Reads the schema from the Directory Server which applies to the
   * named entry using the provided connection.
   * <p>
   * If the requested entry or its associated schema are not returned by
   * the Directory Server then the request will fail with an
   * {@link EntryNotFoundException}. More specifically, this method will
   * never return {@code null}.
   * <p>
   * A typical implementation will first read the {@code
   * subschemaSubentry} attribute of the entry in order to locate the
   * schema. However, implementations may choose to perform other
   * optimizations, such as caching.
   *
   * @param connection
   *          A connection to the Directory Server whose schema is to be
   *          read.
   * @param name
   *          The distinguished name of the entry whose schema is to be
   *          located.
   * @return The schema from the Directory Server which applies to the
   *         named entry.
   * @throws ErrorResultException
   *           If the result code indicates that the request failed for
   *           some reason.
   * @throws InterruptedException
   *           If the current thread was interrupted while waiting.
   * @throws UnsupportedOperationException
   *           If the connection does not support search operations.
   * @throws IllegalStateException
   *           If the connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If the {@code connection} or {@code name} was {@code
   *           null}.
   */
  public static Schema readSchemaForEntry(Connection connection, DN name)
      throws ErrorResultException, InterruptedException,
      UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final SearchRequest request = getReadSchemaForEntrySearchRequest(name);
    final Entry entry = connection.searchSingleEntry(request);
    final DN subschemaDN = getSubschemaSubentryDN(name, entry);

    return readSchema(connection, subschemaDN);
  }



  private static DN getSubschemaSubentryDN(DN name, final Entry entry)
      throws ErrorResultException
  {
    final Attribute subentryAttr = entry
        .getAttribute(ATTR_SUBSCHEMA_SUBENTRY);

    if (subentryAttr == null || subentryAttr.isEmpty())
    {
      // Did not get the subschema sub-entry attribute.
      Result result = Responses.newResult(
          ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED)
          .setDiagnosticMessage(
              ERR_NO_SUBSCHEMA_SUBENTRY_ATTR.get(name.toString())
                  .toString());
      throw ErrorResultException.wrap(result);
    }

    String dnString = subentryAttr.iterator().next().toString();
    DN subschemaDN;
    try
    {
      subschemaDN = DN.valueOf(dnString);
    }
    catch (LocalizedIllegalArgumentException e)
    {
      Result result = Responses.newResult(
          ResultCode.CLIENT_SIDE_NO_RESULTS_RETURNED)
          .setDiagnosticMessage(
              ERR_INVALID_SUBSCHEMA_SUBENTRY_ATTR.get(name.toString(),
                  dnString, e.getMessageObject()).toString());
      throw ErrorResultException.wrap(result);
    }
    return subschemaDN;
  }



  /**
   * Reads the schema from the Directory Server contained in the named
   * subschema sub-entry.
   * <p>
   * If the requested schema is not returned by the Directory Server
   * then the request will fail with an {@link EntryNotFoundException}.
   * More specifically, the returned future will never return {@code
   * null}.
   * <p>
   * Implementations may choose to perform optimizations such as
   * caching.
   *
   * @param <P>
   *          The type of the additional parameter to the handler's
   *          methods.
   * @param connection
   *          A connection to the Directory Server whose schema is to be
   *          read.
   * @param name
   *          The distinguished name of the subschema sub-entry.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @param p
   *          Optional additional handler parameter.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support search operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If the {@code connection} or {@code name} was {@code
   *           null}.
   */
  public static <P> ResultFuture<Schema> readSchema(
      AsynchronousConnection connection, DN name,
      ResultHandler<? super Schema, P> handler, P p)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final SearchRequest request = getReadSchemaSearchRequest(name);

    final ResultTransformer<SearchResultEntry, Schema, P> future = new ResultTransformer<SearchResultEntry, Schema, P>(
        handler)
    {

      protected Schema transformResult(SearchResultEntry result)
          throws ErrorResultException
      {
        return valueOf(result);
      }

    };

    ResultFuture<SearchResultEntry> innerFuture = connection
        .searchSingleEntry(request, future, p);
    future.setResultFuture(innerFuture);
    return future;
  }



  /**
   * Reads the schema from the Directory Server which applies to the
   * named entry.
   * <p>
   * If the requested entry or its associated schema are not returned by
   * the Directory Server then the request will fail with an
   * {@link EntryNotFoundException}. More specifically, the returned
   * future will never return {@code null}.
   * <p>
   * A typical implementation will first read the {@code
   * subschemaSubentry} attribute of the entry in order to locate the
   * schema. However, implementations may choose to perform other
   * optimizations, such as caching.
   *
   * @param <P>
   *          The type of the additional parameter to the handler's
   *          methods.
   * @param connection
   *          A connection to the Directory Server whose schema is to be
   *          read.
   * @param name
   *          The distinguished name of the entry whose schema is to be
   *          located.
   * @param handler
   *          A result handler which can be used to asynchronously
   *          process the operation result when it is received, may be
   *          {@code null}.
   * @param p
   *          Optional additional handler parameter.
   * @return A future representing the result of the operation.
   * @throws UnsupportedOperationException
   *           If this connection does not support search operations.
   * @throws IllegalStateException
   *           If this connection has already been closed, i.e. if
   *           {@code isClosed() == true}.
   * @throws NullPointerException
   *           If the {@code connection} or {@code name} was {@code
   *           null}.
   */
  public static <P> ResultFuture<Schema> readSchemaForEntry(
      final AsynchronousConnection connection, final DN name,
      ResultHandler<Schema, P> handler, final P p)
      throws UnsupportedOperationException, IllegalStateException,
      NullPointerException
  {
    final ResultChain<SearchResultEntry, Schema, P> future = new ResultChain<SearchResultEntry, Schema, P>(
        handler)
    {

      protected ResultFuture<Schema> chainResult(
          SearchResultEntry innerResult,
          ResultHandler<? super Schema, P> handler)
          throws ErrorResultException
      {
        final DN subschemaDN = getSubschemaSubentryDN(name, innerResult);
        return readSchema(connection, subschemaDN, handler, p);
      }

    };

    final SearchRequest request = getReadSchemaForEntrySearchRequest(name);
    ResultFuture<SearchResultEntry> innerFuture = connection
        .searchSingleEntry(request, future, p);
    future.setInnerResultFuture(innerFuture);
    return future;
  }



  // Constructs a search request for retrieving the named subschema
  // sub-entry.
  private static SearchRequest getReadSchemaSearchRequest(DN dn)
  {
    return Requests.newSearchRequest(dn, SearchScope.BASE_OBJECT,
        SUBSCHEMA_FILTER, SUBSCHEMA_ATTRS);
  }



  // Constructs a search request for retrieving the subschemaSubentry
  // attribute from the named entry.
  private static SearchRequest getReadSchemaForEntrySearchRequest(DN dn)
  {
    return Requests.newSearchRequest(dn, SearchScope.BASE_OBJECT,
        SUBSCHEMA_FILTER, SUBSCHEMA_SUBENTRY_ATTRS);
  }



  /**
   * Returns the core schema. The core schema is non-strict and contains
   * the following standard LDAP schema elements:
   * <ul>
   * <li><a href="http://tools.ietf.org/html/rfc4512">RFC 4512 -
   * Lightweight Directory Access Protocol (LDAP): Directory Information
   * Models </a>
   * <li><a href="http://tools.ietf.org/html/rfc4517">RFC 4517 -
   * Lightweight Directory Access Protocol (LDAP): Syntaxes and Matching
   * Rules </a>
   * <li><a href="http://tools.ietf.org/html/rfc4519">RFC 4519 -
   * Lightweight Directory Access Protocol (LDAP): Schema for User
   * Applications </a>
   * <li><a href="http://tools.ietf.org/html/rfc4530">RFC 4530 -
   * Lightweight Directory Access Protocol (LDAP): entryUUID Operational
   * Attribute </a>
   * <li><a href="http://tools.ietf.org/html/rfc3045">RFC 3045 - Storing
   * Vendor Information in the LDAP root DSE </a>
   * <li><a href="http://tools.ietf.org/html/rfc3112">RFC 3112 - LDAP
   * Authentication Password Schema </a>
   * </ul>
   *
   * @return The core schema.
   */
  public static Schema getCoreSchema()
  {
    return CORE_SCHEMA;
  }



  /**
   * Returns the default schema which should be used by this
   * application. The default schema is initially set to the core
   * schema.
   *
   * @return The default schema which should be used by this
   *         application.
   */
  public static Schema getDefaultSchema()
  {
    return DEFAULT_SCHEMA;
  }



  /**
   * Returns the empty schema. The empty schema is non-strict and does
   * not contain any schema elements.
   *
   * @return The empty schema.
   */
  public static Schema getEmptySchema()
  {
    return EMPTY_SCHEMA;
  }



  /**
   * Parses the provided entry as a subschema subentry. Any problems
   * encountered while parsing the entry can be retrieved using the
   * returned schema's {@link #getWarnings()} method.
   *
   * @param entry
   *          The subschema subentry to be parsed.
   * @return The parsed schema.
   */
  public static Schema valueOf(Entry entry)
  {
    final SchemaBuilder builder = new SchemaBuilder();

    Attribute attr = entry.getAttribute(ATTR_LDAP_SYNTAXES);
    if (attr != null)
    {
      for (final ByteString def : attr)
      {
        try
        {
          builder.addSyntax(def.toString(), true);
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          builder.addWarning(e.getMessageObject());
        }
      }
    }

    attr = entry.getAttribute(ATTR_ATTRIBUTE_TYPES);
    if (attr != null)
    {
      for (final ByteString def : attr)
      {
        try
        {
          builder.addAttributeType(def.toString(), true);
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          builder.addWarning(e.getMessageObject());
        }
      }
    }

    attr = entry.getAttribute(ATTR_OBJECT_CLASSES);
    if (attr != null)
    {
      for (final ByteString def : attr)
      {
        try
        {
          builder.addObjectClass(def.toString(), true);
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          builder.addWarning(e.getMessageObject());
        }
      }
    }

    attr = entry.getAttribute(ATTR_MATCHING_RULE_USE);
    if (attr != null)
    {
      for (final ByteString def : attr)
      {
        try
        {
          builder.addMatchingRuleUse(def.toString(), true);
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          builder.addWarning(e.getMessageObject());
        }
      }
    }

    attr = entry.getAttribute(ATTR_MATCHING_RULES);
    if (attr != null)
    {
      for (final ByteString def : attr)
      {
        try
        {
          builder.addMatchingRule(def.toString(), true);
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          builder.addWarning(e.getMessageObject());
        }
      }
    }

    attr = entry.getAttribute(ATTR_DIT_CONTENT_RULES);
    if (attr != null)
    {
      for (final ByteString def : attr)
      {
        try
        {
          builder.addDITContentRule(def.toString(), true);
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          builder.addWarning(e.getMessageObject());
        }
      }
    }

    attr = entry.getAttribute(ATTR_DIT_STRUCTURE_RULES);
    if (attr != null)
    {
      for (final ByteString def : attr)
      {
        try
        {
          builder.addDITStructureRule(def.toString(), true);
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          builder.addWarning(e.getMessageObject());
        }
      }
    }

    attr = entry.getAttribute(ATTR_NAME_FORMS);
    if (attr != null)
    {
      for (final ByteString def : attr)
      {
        try
        {
          builder.addNameForm(def.toString(), true);
        }
        catch (final LocalizedIllegalArgumentException e)
        {
          builder.addWarning(e.getMessageObject());
        }
      }
    }

    return builder.toSchema();
  }



  /**
   * Sets the default schema which should be used by this application.
   * The default schema is initially set to the core schema.
   *
   * @param schema
   *          The default schema which should be used by this
   *          application.
   */
  public static void setDefaultSchema(Schema schema)
  {
    DEFAULT_SCHEMA = schema;
  }



  static MatchingRule getDefaultMatchingRule()
  {
    return CoreSchema.getOctetStringMatchingRule();
  }



  static Syntax getDefaultSyntax()
  {
    return CoreSchema.getOctetStringSyntax();
  }



  private final Impl impl;



  Schema(Map<String, Syntax> numericOID2Syntaxes,
      Map<String, MatchingRule> numericOID2MatchingRules,
      Map<String, MatchingRuleUse> numericOID2MatchingRuleUses,
      Map<String, AttributeType> numericOID2AttributeTypes,
      Map<String, ObjectClass> numericOID2ObjectClasses,
      Map<String, NameForm> numericOID2NameForms,
      Map<String, DITContentRule> numericOID2ContentRules,
      Map<Integer, DITStructureRule> id2StructureRules,
      Map<String, List<MatchingRule>> name2MatchingRules,
      Map<String, List<MatchingRuleUse>> name2MatchingRuleUses,
      Map<String, List<AttributeType>> name2AttributeTypes,
      Map<String, List<ObjectClass>> name2ObjectClasses,
      Map<String, List<NameForm>> name2NameForms,
      Map<String, List<DITContentRule>> name2ContentRules,
      Map<String, List<DITStructureRule>> name2StructureRules,
      Map<String, List<NameForm>> objectClass2NameForms,
      Map<String, List<DITStructureRule>> nameForm2StructureRules,
      SchemaCompatOptions options, List<LocalizableMessage> warnings)
  {
    impl = new StrictImpl(numericOID2Syntaxes,
        numericOID2MatchingRules, numericOID2MatchingRuleUses,
        numericOID2AttributeTypes, numericOID2ObjectClasses,
        numericOID2NameForms, numericOID2ContentRules,
        id2StructureRules, name2MatchingRules, name2MatchingRuleUses,
        name2AttributeTypes, name2ObjectClasses, name2NameForms,
        name2ContentRules, name2StructureRules, objectClass2NameForms,
        nameForm2StructureRules, options, warnings);
  }



  private Schema(Impl impl)
  {
    this.impl = impl;
  }



  /**
   * Returns the attribute type with the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the attribute type to retrieve.
   * @return The requested attribute type.
   * @throws UnknownSchemaElementException
   *           If this is a strict schema and the requested attribute
   *           type was not found or if the provided name is ambiguous.
   */
  public AttributeType getAttributeType(String name)
      throws UnknownSchemaElementException
  {
    return impl.getAttributeType(name);
  }



  /**
   * Returns an unmodifiable collection containing all of the attribute
   * types contained in this schema.
   *
   * @return An unmodifiable collection containing all of the attribute
   *         types contained in this schema.
   */
  public Collection<AttributeType> getAttributeTypes()
  {
    return impl.getAttributeTypes();
  }



  /**
   * Returns an unmodifiable collection containing all of the attribute
   * types having the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the attribute types to retrieve.
   * @return An unmodifiable collection containing all of the attribute
   *         types having the specified name or numeric OID.
   */
  public List<AttributeType> getAttributeTypesByName(String name)
  {
    return impl.getAttributeTypesByName(name);
  }



  /**
   * Returns the DIT content rule with the specified name or numeric
   * OID.
   *
   * @param name
   *          The name or OID of the DIT content rule to retrieve.
   * @return The requested DIT content rule.
   * @throws UnknownSchemaElementException
   *           If this is a strict schema and the requested DIT content
   *           rule was not found or if the provided name is ambiguous.
   */
  public DITContentRule getDITContentRule(String name)
      throws UnknownSchemaElementException
  {
    return impl.getDITContentRule(name);
  }



  /**
   * Returns an unmodifiable collection containing all of the DIT
   * content rules contained in this schema.
   *
   * @return An unmodifiable collection containing all of the DIT
   *         content rules contained in this schema.
   */
  public Collection<DITContentRule> getDITContentRules()
  {
    return impl.getDITContentRules();
  }



  /**
   * Returns an unmodifiable collection containing all of the DIT
   * content rules having the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the DIT content rules to retrieve.
   * @return An unmodifiable collection containing all of the DIT
   *         content rules having the specified name or numeric OID.
   */
  public Collection<DITContentRule> getDITContentRulesByName(String name)
  {
    return impl.getDITContentRulesByName(name);
  }



  /**
   * Returns the DIT structure rule with the specified name or numeric
   * OID.
   *
   * @param ruleID
   *          The ID of the DIT structure rule to retrieve.
   * @return The requested DIT structure rule.
   * @throws UnknownSchemaElementException
   *           If this is a strict schema and the requested DIT
   *           structure rule was not found.
   */
  public DITStructureRule getDITStructureRule(int ruleID)
      throws UnknownSchemaElementException
  {
    return impl.getDITStructureRule(ruleID);
  }



  /**
   * Returns an unmodifiable collection containing all of the DIT
   * structure rules having the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the DIT structure rules to retrieve.
   * @return An unmodifiable collection containing all of the DIT
   *         structure rules having the specified name or numeric OID.
   */
  public Collection<DITStructureRule> getDITStructureRulesByName(
      String name)
  {
    return impl.getDITStructureRulesByName(name);
  }



  /**
   * Retrieves the DIT structure rules for the provided name form.
   *
   * @param nameForm
   *          The name form.
   * @return The requested DIT structure rules.
   */
  public Collection<DITStructureRule> getDITStructureRulesByNameForm(
      NameForm nameForm)
  {
    return impl.getDITStructureRulesByNameForm(nameForm);
  }



  /**
   * Returns an unmodifiable collection containing all of the DIT
   * structure rules contained in this schema.
   *
   * @return An unmodifiable collection containing all of the DIT
   *         structure rules contained in this schema.
   */
  public Collection<DITStructureRule> getDITStuctureRules()
  {
    return impl.getDITStuctureRules();
  }



  /**
   * Returns the matching rule with the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the matching rule to retrieve.
   * @return The requested matching rule.
   * @throws UnknownSchemaElementException
   *           If this is a strict schema and the requested matching
   *           rule was not found or if the provided name is ambiguous.
   */
  public MatchingRule getMatchingRule(String name)
      throws UnknownSchemaElementException
  {
    return impl.getMatchingRule(name);
  }



  /**
   * Returns an unmodifiable collection containing all of the matching
   * rules contained in this schema.
   *
   * @return An unmodifiable collection containing all of the matching
   *         rules contained in this schema.
   */
  public Collection<MatchingRule> getMatchingRules()
  {
    return impl.getMatchingRules();
  }



  /**
   * Returns an unmodifiable collection containing all of the matching
   * rules having the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the matching rules to retrieve.
   * @return An unmodifiable collection containing all of the matching
   *         rules having the specified name or numeric OID.
   */
  public Collection<MatchingRule> getMatchingRulesByName(String name)
  {
    return impl.getMatchingRulesByName(name);
  }



  /**
   * Returns the matching rule use associated with the provided matching
   * rule.
   *
   * @param matchingRule
   *          The matching rule whose matching rule use is to be
   *          retrieved.
   * @return The requested matching rule use.
   * @throws UnknownSchemaElementException
   *           If this is a strict schema and the requested matching
   *           rule use was not found or if the provided name is
   *           ambiguous.
   */
  public MatchingRuleUse getMatchingRuleUse(MatchingRule matchingRule)
      throws UnknownSchemaElementException
  {
    return getMatchingRuleUse(matchingRule.getOID());
  }



  /**
   * Returns the matching rule use with the specified name or numeric
   * OID.
   *
   * @param name
   *          The name or OID of the matching rule use to retrieve.
   * @return The requested matching rule use.
   * @throws UnknownSchemaElementException
   *           If this is a strict schema and the requested matching
   *           rule use was not found or if the provided name is
   *           ambiguous.
   */
  public MatchingRuleUse getMatchingRuleUse(String name)
      throws UnknownSchemaElementException
  {
    return impl.getMatchingRuleUse(name);
  }



  /**
   * Returns an unmodifiable collection containing all of the matching
   * rule uses contained in this schema.
   *
   * @return An unmodifiable collection containing all of the matching
   *         rule uses contained in this schema.
   */
  public Collection<MatchingRuleUse> getMatchingRuleUses()
  {
    return impl.getMatchingRuleUses();
  }



  /**
   * Returns an unmodifiable collection containing all of the matching
   * rule uses having the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the matching rule uses to retrieve.
   * @return An unmodifiable collection containing all of the matching
   *         rule uses having the specified name or numeric OID.
   */
  public Collection<MatchingRuleUse> getMatchingRuleUsesByName(
      String name)
  {
    return impl.getMatchingRuleUsesByName(name);
  }



  /**
   * Returns the name form with the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the name form to retrieve.
   * @return The requested name form.
   * @throws UnknownSchemaElementException
   *           If this is a strict schema and the requested name form
   *           was not found or if the provided name is ambiguous.
   */
  public NameForm getNameForm(String name)
      throws UnknownSchemaElementException
  {
    return impl.getNameForm(name);
  }



  /**
   * Retrieves the name forms for the specified structural objectclass.
   *
   * @param structuralClass
   *          The structural objectclass for the name form to retrieve.
   * @return The requested name forms
   */
  public Collection<NameForm> getNameFormByObjectClass(
      ObjectClass structuralClass)
  {
    return impl.getNameFormByObjectClass(structuralClass);
  }



  /**
   * Returns an unmodifiable collection containing all of the name forms
   * contained in this schema.
   *
   * @return An unmodifiable collection containing all of the name forms
   *         contained in this schema.
   */
  public Collection<NameForm> getNameForms()
  {
    return impl.getNameForms();
  }



  /**
   * Returns an unmodifiable collection containing all of the name forms
   * having the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the name forms to retrieve.
   * @return An unmodifiable collection containing all of the name forms
   *         having the specified name or numeric OID.
   */
  public Collection<NameForm> getNameFormsByName(String name)
  {
    return impl.getNameFormsByName(name);
  }



  /**
   * Returns the object class with the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the object class to retrieve.
   * @return The requested object class.
   * @throws UnknownSchemaElementException
   *           If this is a strict schema and the requested object class
   *           was not found or if the provided name is ambiguous.
   */
  public ObjectClass getObjectClass(String name)
      throws UnknownSchemaElementException
  {
    return impl.getObjectClass(name);
  }



  /**
   * Returns an unmodifiable collection containing all of the object
   * classes contained in this schema.
   *
   * @return An unmodifiable collection containing all of the object
   *         classes contained in this schema.
   */
  public Collection<ObjectClass> getObjectClasses()
  {
    return impl.getObjectClasses();
  }



  /**
   * Returns an unmodifiable collection containing all of the object
   * classes having the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the object classes to retrieve.
   * @return An unmodifiable collection containing all of the object
   *         classes having the specified name or numeric OID.
   */
  public Collection<ObjectClass> getObjectClassesByName(String name)
  {
    return impl.getObjectClassesByName(name);
  }



  /**
   * Returns the syntax with the specified numeric OID.
   *
   * @param numericOID
   *          The OID of the syntax to retrieve.
   * @return The requested syntax.
   * @throws UnknownSchemaElementException
   *           If this is a strict schema and the requested syntax was
   *           not found or if the provided name is ambiguous.
   */
  public Syntax getSyntax(String numericOID)
      throws UnknownSchemaElementException
  {
    return impl.getSyntax(numericOID);
  }



  /**
   * Returns an unmodifiable collection containing all of the syntaxes
   * contained in this schema.
   *
   * @return An unmodifiable collection containing all of the syntaxes
   *         contained in this schema.
   */
  public Collection<Syntax> getSyntaxes()
  {
    return impl.getSyntaxes();
  }



  /**
   * Returns an unmodifiable collection containing all of the warnings
   * that were detected when this schema was constructed.
   *
   * @return An unmodifiable collection containing all of the warnings
   *         that were detected when this schema was constructed.
   */
  public Collection<LocalizableMessage> getWarnings()
  {
    return impl.getWarnings();
  }



  /**
   * Indicates whether or not this schema contains an attribute type
   * with the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the attribute type.
   * @return {@code true} if this schema contains an attribute type with
   *         the specified name or numeric OID, otherwise {@code false}.
   */
  public boolean hasAttributeType(String name)
  {
    return impl.hasAttributeType(name);
  }



  /**
   * Indicates whether or not this schema contains a DIT content rule
   * with the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the DIT content rule.
   * @return {@code true} if this schema contains a DIT content rule
   *         with the specified name or numeric OID, otherwise {@code
   *         false}.
   */
  public boolean hasDITContentRule(String name)
  {
    return impl.hasDITContentRule(name);
  }



  /**
   * Indicates whether or not this schema contains a DIT structure rule
   * with the specified rule ID.
   *
   * @param ruleID
   *          The ID of the DIT structure rule.
   * @return {@code true} if this schema contains a DIT structure rule
   *         with the specified rule ID, otherwise {@code false}.
   */
  public boolean hasDITStructureRule(int ruleID)
  {
    return impl.hasDITStructureRule(ruleID);
  }



  /**
   * Indicates whether or not this schema contains a matching rule with
   * the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the matching rule.
   * @return {@code true} if this schema contains a matching rule with
   *         the specified name or numeric OID, otherwise {@code false}.
   */
  public boolean hasMatchingRule(String name)
  {
    return impl.hasMatchingRule(name);
  }



  /**
   * Indicates whether or not this schema contains a matching rule use
   * with the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the matching rule use.
   * @return {@code true} if this schema contains a matching rule use
   *         with the specified name or numeric OID, otherwise {@code
   *         false}.
   */
  public boolean hasMatchingRuleUse(String name)
  {
    return impl.hasMatchingRuleUse(name);
  }



  /**
   * Indicates whether or not this schema contains a name form with the
   * specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the name form.
   * @return {@code true} if this schema contains a name form with the
   *         specified name or numeric OID, otherwise {@code false}.
   */
  public boolean hasNameForm(String name)
  {
    return impl.hasNameForm(name);
  }



  /**
   * Indicates whether or not this schema contains an object class with
   * the specified name or numeric OID.
   *
   * @param name
   *          The name or OID of the object class.
   * @return {@code true} if this schema contains an object class with
   *         the specified name or numeric OID, otherwise {@code false}.
   */
  public boolean hasObjectClass(String name)
  {
    return impl.hasObjectClass(name);
  }



  /**
   * Indicates whether or not this schema contains a syntax with the
   * specified numeric OID.
   *
   * @param numericOID
   *          The OID of the syntax.
   * @return {@code true} if this schema contains a syntax with the
   *         specified numeric OID, otherwise {@code false}.
   */
  public boolean hasSyntax(String numericOID)
  {
    return impl.hasSyntax(numericOID);
  }



  /**
   * Indicates whether or not this schema is strict. Attribute type
   * queries in non-strict schema always succeed: if the requested
   * attribute type is not found then a temporary attribute type is
   * created automatically having the Octet String syntax and associated
   * matching rules. Strict schema, on the other hand, throw an
   * {@link UnknownSchemaElementException} whenever an attempt is made
   * to retrieve a non-existent attribute type.
   *
   * @return {@code true} if this schema is strict.
   */
  public boolean isStrict()
  {
    return impl.isStrict();
  }



  /**
   * Returns a non-strict view of this schema. Attribute type queries in
   * non-strict schema always succeed: if the requested attribute type
   * is not found then a temporary attribute type is created
   * automatically having the Octet String syntax and associated
   * matching rules. Strict schema, on the other hand, throw an
   * {@link UnknownSchemaElementException} whenever an attempt is made
   * to retrieve a non-existent attribute type.
   *
   * @return A non-strict view of this schema.
   */
  public Schema nonStrict()
  {
    if (impl.isStrict())
    {
      return new Schema(new NonStrictImpl(impl));
    }
    else
    {
      return this;
    }
  }



  SchemaCompatOptions getSchemaCompatOptions()
  {
    return impl.getSchemaCompatOptions();
  }
}
