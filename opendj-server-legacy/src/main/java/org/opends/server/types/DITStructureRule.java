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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.types;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.slf4j.LocalizedLogger;

import static org.forgerock.util.Reject.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines a DIT structure rule, which is used to indicate
 * the types of children that entries may have.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class DITStructureRule
       implements SchemaFileElement
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Indicates whether this DIT structure rule is declared "obsolete". */
  private final boolean isObsolete;

  /** The rule ID for this DIT structure rule. */
  private final int ruleID;

  /** The name form for this DIT structure rule. */
  private final NameForm nameForm;

  /**
   * The set of additional name-value pairs associated with this DIT
   * structure rule.
   */
  private final Map<String,List<String>> extraProperties;

  /**
   * The set of names for this DIT structure rule, in a mapping
   * between the all-lowercase form and the user-defined form.
   */
  private final Map<String,String> names;

  /** The set of superior DIT structure rules. */
  private final Set<DITStructureRule> superiorRules;

  /** The definition string for this DIT structure rule. */
  private final String definition;

  /** The description for this DIT structure rule. */
  private final String description;



  /**
   * Creates a new DIT structure rule with the provided information.
   *
   * @param  definition       The definition string used to create
   *                          this DIT structure rule.  It must not be
   *                          {@code null}.
   * @param  names            The set of names for this DIT structure
   *                          rule, mapping the lowercase names to the
   *                          user-defined values.
   * @param  ruleID           The rule ID for this DIT structure rule.
   * @param  description      The description for this DIT structure
   *                          rule.
   * @param  isObsolete       Indicates whether this DIT structure
   *                          rule is declared "obsolete".
   * @param  nameForm         The name form for this DIT structure
   *                          rule.
   * @param  superiorRules    References to the superior rules for
   *                          this DIT structure rule.
   * @param  extraProperties  The set of "extra" properties associated
   *                          with this DIT structure rules.
   */
  public DITStructureRule(String definition, Map<String,String> names,
                          int ruleID, String description,
                          boolean isObsolete, NameForm nameForm,
                          Set<DITStructureRule> superiorRules,
                          Map<String,List<String>> extraProperties)
  {
    ifNull(definition);

    this.ruleID      = ruleID;
    this.description = description;
    this.isObsolete  = isObsolete;
    this.nameForm    = nameForm;

    int schemaFilePos = definition.indexOf(SCHEMA_PROPERTY_FILENAME);
    if (schemaFilePos > 0)
    {
      String defStr;
      try
      {
        int firstQuotePos = definition.indexOf('\'', schemaFilePos);
        int secondQuotePos = definition.indexOf('\'',
                                                firstQuotePos+1);

        defStr = definition.substring(0, schemaFilePos).trim() + " " +
                 definition.substring(secondQuotePos+1).trim();
      }
      catch (Exception e)
      {
        logger.traceException(e);

        defStr = definition;
      }

      this.definition = defStr;
    }
    else
    {
      this.definition = definition;
    }

    if ((names == null) || names.isEmpty())
    {
      this.names = new LinkedHashMap<>(0);
    }
    else
    {
      this.names = new LinkedHashMap<>(names);
    }

    if ((superiorRules == null) || superiorRules.isEmpty())
    {
      this.superiorRules = new LinkedHashSet<>(0);
    }
    else
    {
      this.superiorRules = new LinkedHashSet<>(superiorRules);
    }

    if ((extraProperties == null) || extraProperties.isEmpty())
    {
      this.extraProperties = new LinkedHashMap<>(0);
    }
    else
    {
      this.extraProperties = new LinkedHashMap<>(extraProperties);
    }
  }



  /**
   * Retrieves the set of names that may be used to reference this DIT
   * structure rule.  The returned mapping will be between an all
   * lower-case form of the name and a name in the user-defined form
   * (which may include mixed capitalization).
   *
   * @return  The set of names that may be used to reference this DIT
   *          structure rule.
   */
  public Map<String,String> getNames()
  {
    return names;
  }



  /**
   * Indicates whether this DIT structure rule has the specified name.
   *
   * @param  lowerName  The lowercase name for which to make the
   *                    determination.
   *
   * @return  {@code true} if the specified name is assigned to this
   *          DIT structure rule, or {@code false} if not.
   */
  public boolean hasName(String lowerName)
  {
    return names.containsKey(lowerName);
  }



  /**
   * Retrieves the rule ID for this DIT structure rule.
   *
   * @return  The rule ID for this DIT structure rule.
   */
  public int getRuleID()
  {
    return ruleID;
  }



  /**
   * Retrieves the name or rule ID for this DIT structure rule.  If it
   * has one or more names, then the primary name will be returned.
   * If it does not have any names, then the rule ID will be returned.
   *
   * @return  The name or rule ID for this DIT structure rule.
   */
  public String getNameOrRuleID()
  {
    if (names.isEmpty())
    {
      return String.valueOf(ruleID);
    }
    else
    {
      return names.values().iterator().next();
    }
  }



  /**
   * Retrieves the description for this DIT structure rule.
   *
   * @return  The description for this DIT structure rule.
   */
  public String getDescription()
  {
    return description;
  }



  /**
   * Retrieves the name form for this DIT structure rule.
   *
   * @return  The name form for this DIT structure rule.
   */
  public NameForm getNameForm()
  {
    return nameForm;
  }



  /**
   * Retrieves the structural objectclass for the name form with which
   * this DIT structure rule is associated.
   *
   * @return  The structural objectclass for the name form with which
   *          this DIT structure rule is associated.
   */
  public ObjectClass getStructuralClass()
  {
    return nameForm.getStructuralClass();
  }



  /**
   * Retrieves the set of superior rules for this DIT structure rule.
   *
   * @return  The set of superior rules for this DIT structure rule.
   */
  public Set<DITStructureRule> getSuperiorRules()
  {
    return superiorRules;
  }



  /**
   * Indicates whether this DIT structure rule has one or more
   * superior rules.
   *
   * @return  {@code true} if this DIT structure rule has one or more
   *          superior rules, or {@code false} if not.
   */
  boolean hasSuperiorRules()
  {
    return superiorRules != null && !superiorRules.isEmpty();
  }



  /**
   * Indicates whether this DIT structure rule is declared "obsolete".
   *
   * @return  {@code true} if this DIT structure rule is declared
   *          "obsolete", or {@code false} if not.
   */
  public boolean isObsolete()
  {
    return isObsolete;
  }



  /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this DIT structure rule
   * and the value for that property.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this DIT
   *          structure rule and the value for that property.
   */
  @Override
  public Map<String,List<String>> getExtraProperties()
  {
    return extraProperties;
  }



  /**
   * Indicates whether the provided object is equal to this DIT
   * structure rule.  The object will be considered equal if it is a
   * DIT structure rule with the same OID as the current type.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object is equal to this
   *          attribute, or {@code false} if not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (!(o instanceof DITStructureRule))
    {
      return false;
    }
    return ruleID == ((DITStructureRule) o).ruleID;
  }



  /**
   * Retrieves the hash code for this DIT structure rule.  It will be
   * equal to the rule ID.
   *
   * @return  The hash code for this DIT structure rule.
   */
  @Override
  public int hashCode()
  {
    return ruleID;
  }



  /**
   * Retrieves the string representation of this attribute type in the
   * form specified in RFC 2252.
   *
   * @return  The string representation of this attribute type in the
   *          form specified in RFC 2252.
   */
  @Override
  public String toString()
  {
    return definition;
  }

}
