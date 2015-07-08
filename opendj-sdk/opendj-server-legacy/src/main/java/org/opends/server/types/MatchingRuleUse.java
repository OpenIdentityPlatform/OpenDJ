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
import org.forgerock.opendj.ldap.schema.MatchingRule;

import static org.forgerock.util.Reject.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class defines a data structure for storing and interacting
 * with a matching rule use definition, which may be used to restrict
 * the set of attribute types that may be used for a given matching
 * rule.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class MatchingRuleUse
       implements SchemaFileElement
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Indicates whether this matching rule use is declared "obsolete". */
  private final boolean isObsolete;

  /**
   * The set of additional name-value pairs associated with this
   * matching rule use definition.
   */
  private final Map<String,List<String>> extraProperties;

  /**
   * The set of names that may be used to refer to this matching rule
   * use, mapped between their all-lowercase representations and the
   * user-defined representations.
   */
  private final Map<String,String> names;

  /**
   * The matching rule with which this matching rule use is associated.
   */
  private final MatchingRule matchingRule;

  /**
   * The set of attribute types with which this matching rule use is associated.
   */
  private final Set<AttributeType> attributes;

  /** The definition string used to create this matching rule use. */
  private final String definition;

  /** The description for this matching rule use. */
  private final String description;



  /**
   * Creates a new matching rule use definition with the provided
   * information.
   *
   * @param  definition       The definition string used to create
   *                          this matching rule use.  It must not be
   *                          {@code null}.
   * @param  matchingRule     The matching rule for this matching rule
   *                          use.  It must not be {@code null}.
   * @param  names            The set of names for this matching rule
   *                          use.
   * @param  description      The description for this matching rule
   *                          use.
   * @param  isObsolete       Indicates whether this matching rule use
   *                          is declared "obsolete".
   * @param  attributes       The set of attribute types for this
   *                          matching rule use.
   * @param  extraProperties  A set of "extra" properties that may be
   *                          associated with this matching rule use.
   */
  public MatchingRuleUse(String definition, MatchingRule matchingRule,
                         Map<String,String> names, String description,
                         boolean isObsolete,
                         Set<AttributeType> attributes,
                         Map<String,List<String>> extraProperties)
  {
    ifNull(definition, matchingRule);

    this.matchingRule = matchingRule;
    this.description  = description;
    this.isObsolete   = isObsolete;

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

    if ((attributes == null) || attributes.isEmpty())
    {
      this.attributes = new LinkedHashSet<>(0);
    }
    else
    {
      this.attributes = new LinkedHashSet<>(attributes);
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
   * Retrieves the matching rule for this matching rule use.
   *
   * @return  The matching rule for this matching rule use.
   */
  public MatchingRule getMatchingRule()
  {
    return matchingRule;
  }



  /**
   * Retrieves the set of names for this matching rule use.  The
   * mapping will be between the names in all lowercase form and the
   * names in the user-defined form.
   *
   * @return  The set of names for this matching rule use.
   */
  public Map<String,String> getNames()
  {
    return names;
  }



  /**
   * Retrieves the primary name to use when referencing this matching
   * rule use.
   *
   * @return  The primary name to use when referencing this matching
   *          rule use, or {@code null} if there is none.
   */
  public String getNameOrOID()
  {
    if (names.isEmpty())
    {
      return null;
    }
    else
    {
      return names.values().iterator().next();
    }
  }



  /**
   * Indicates whether this matching rule use has the specified name.
   *
   * @param  lowerName  The name for which to make the determination,
   *                    formatted in all lowercase characters.
   *
   * @return  {@code true} if this matching rule use has the specified
   *          name, or {@code false} if not.
   */
  public boolean hasName(String lowerName)
  {
    return names.containsKey(lowerName);
  }



  /**
   * Retrieves the description for this matching rule use.
   *
   * @return  The description for this matching rule use, or
   *          {@code null} if there is none.
   */
  public String getDescription()
  {
    return description;
  }



  /**
   * Indicates whether this matching rule use is declared "obsolete".
   *
   * @return  {@code true} if this matching rule use is declared
   *          "obsolete", or {@code false} if it is not.
   */
  public boolean isObsolete()
  {
    return isObsolete;
  }



  /**
   * Retrieves the set of attributes associated with this matching
   * rule use.
   *
   * @return  The set of attributes associated with this matching
   *          rule use.
   */
  public Set<AttributeType> getAttributes()
  {
    return attributes;
  }



  /**
   * Indicates whether the provided attribute type is referenced by
   * this matching rule use.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  {@code true} if the provided attribute type is
   *          referenced by this matching rule use, or {@code false}
   *          if it is not.
   */
  boolean appliesToAttribute(AttributeType attributeType)
  {
    return attributes.contains(attributeType);
  }



  /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this matching rule use and
   * the value for that property.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this matching
   *          rule use and the value for that property.
   */
  @Override
  public Map<String,List<String>> getExtraProperties()
  {
    return extraProperties;
  }



  /**
   * Indicates whether the provided object is equal to this matching
   * rule use.  The object will be considered equal if it is a
   * matching rule use with the same matching rule.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object is equal to this
   *          matching rule use, or {@code false} if not.
   */
  @Override
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }
    if (!(o instanceof MatchingRuleUse))
    {
      return false;
    }
    return matchingRule.equals(((MatchingRuleUse) o).matchingRule);
  }



  /**
   * Retrieves the hash code for this matching rule use.  It will be
   * equal to the hash code for the associated matching rule.
   *
   * @return  The hash code for this matching rule use.
   */
  @Override
  public int hashCode()
  {
    return matchingRule.hashCode();
  }



  /**
   * Retrieves the string representation of this matching rule use in
   * the form specified in RFC 2252.
   *
   * @return  The string representation of this matching rule use in
   *          the form specified in RFC 2252.
   */
  @Override
  public String toString()
  {
    return definition;
  }

}
