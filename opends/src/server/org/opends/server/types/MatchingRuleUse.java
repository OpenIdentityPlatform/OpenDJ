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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.server.api.MatchingRule;
import org.opends.server.schema.MatchingRuleUseSyntax;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.*;



/**
 * This class defines a data structure for storing and interacting
 * with a matching rule use definition, which may be used to restrict
 * the set of attribute types that may be used for a given matching
 * rule.
 */
public final class MatchingRuleUse
       implements SchemaFileElement
{
  // Indicates whether this matching rule use is declared "obsolete".
  private final boolean isObsolete;

  // The set of additional name-value pairs associated with this
  // matching rule use definition.
  private final Map<String,List<String>> extraProperties;

  // The set of names that may be used to refer to this matching rule
  // use, mapped between their all-lowercase representations and the
  // user-defined representations.
  private final Map<String,String> names;

  // The matching rule with which this matching rule use is
  // associated.
  private final MatchingRule matchingRule;

  // The set of attribute types with which this matching rule use is
  // associated.
  private final Set<AttributeType> attributes;

  // The definition string used to create this matching rule use.
  private final String definition;

  // The description for this matching rule use.
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
    ensureNotNull(definition, matchingRule);

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
        if (debugEnabled())
        {
          debugCaught(DebugLogLevel.ERROR, e);
        }

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
      this.names = new LinkedHashMap<String,String>(0);
    }
    else
    {
      this.names = new LinkedHashMap<String,String>(names);
    }

    if ((attributes == null) || attributes.isEmpty())
    {
      this.attributes = new LinkedHashSet<AttributeType>(0);
    }
    else
    {
      this.attributes = new LinkedHashSet<AttributeType>(attributes);
    }

    if ((extraProperties == null) || extraProperties.isEmpty())
    {
      this.extraProperties =
           new LinkedHashMap<String,List<String>>(0);
    }
    else
    {
      this.extraProperties =
           new LinkedHashMap<String,List<String>>(extraProperties);
    }
  }



  /**
   * Retrieves the definition string used to create this matching rule
   * use.
   *
   * @return  The definition string used to create this matching rule
   *          use.
   */
  public String getDefinition()
  {
    return definition;
  }



  /**
   * Creates a new instance of this matching rule use based on the
   * definition string.  It will also preserve other state information
   * associated with this matching rule use that is not included in
   * the definition string (e.g., the name of the schema file with
   * which it is associated).
   *
   * @return  The new instance of this matching rule use based on the
   *          definition string.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to create a new matching rule use
   *                              instance from the definition string.
   */
  public MatchingRuleUse recreateFromDefinition()
         throws DirectoryException
  {
    ByteString value  = ByteStringFactory.create(definition);
    Schema     schema = DirectoryConfig.getSchema();

    MatchingRuleUse mru =
         MatchingRuleUseSyntax.decodeMatchingRuleUse(value, schema,
                                                     false);
    mru.setSchemaFile(getSchemaFile());

    return mru;
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
  public String getName()
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
   * Retrieves the path to the schema file that contains the
   * definition for this matching rule use.
   *
   * @return  The path to the schema file that contains the definition
   *          for this matching rule use, or {@code null} if it is not
   *          known or if it is not stored in any schema file.
   */
  public String getSchemaFile()
  {
    List<String> values =
         extraProperties.get(SCHEMA_PROPERTY_FILENAME);
    if ((values == null) || values.isEmpty())
    {
      return null;
    }

    return values.get(0);
  }



  /**
   * Specifies the path to the schema file that contains the
   * definition for this matching rule use.
   *
   * @param  schemaFile  The path to the schema file that contains the
   *                     definition for this matching rule use.
   */
  public void setSchemaFile(String schemaFile)
  {
    setExtraProperty(SCHEMA_PROPERTY_FILENAME, schemaFile);
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
  public boolean appliesToAttribute(AttributeType attributeType)
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
  public Map<String,List<String>> getExtraProperties()
  {
    return extraProperties;
  }



  /**
   * Retrieves the value of the specified "extra" property for this
   * matching rule use.
   *
   * @param  propertyName  The name of the "extra" property for which
   *                       to retrieve the value.
   *
   * @return  The value of the specified "extra" property for this
   *          matching rule use, or {@code null} if no such property
   *          is defined.
   */
  public List<String> getExtraProperty(String propertyName)
  {
    return extraProperties.get(propertyName);
  }



  /**
   * Specifies the provided "extra" property for this matching rule
   * use.
   *
   * @param  name   The name for the "extra" property.  It must not be
   *                {@code null}.
   * @param  value  The value for the "extra" property, or
   *                {@code null} if the property is to be removed.
   */
  public void setExtraProperty(String name, String value)
  {
    ensureNotNull(name);

    if (value == null)
    {
      extraProperties.remove(name);
    }
    else
    {
      LinkedList<String> values = new LinkedList<String>();
      values.add(value);

      extraProperties.put(name, values);
    }
  }



  /**
   * Specifies the provided "extra" property for this matching rule
   * use.
   *
   * @param  name    The name for the "extra" property.  It must not
   *                 be {@code null}.
   * @param  values  The set of value for the "extra" property, or
   *                 {@code null} if the property is to be removed.
   */
  public void setExtraProperty(String name, List<String> values)
  {
    ensureNotNull(name);

    if ((values == null) || values.isEmpty())
    {
      extraProperties.remove(name);
    }
    else
    {
      LinkedList<String> valuesCopy = new LinkedList<String>(values);
      extraProperties.put(name, valuesCopy);
    }
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
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof MatchingRuleUse)))
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
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer, true);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this matching rule use in the
   * form specified in RFC 2252 to the provided buffer.
   *
   * @param  buffer              The buffer to which the information
   *                             should be appended.
   * @param  includeFileElement  Indicates whether to include an
   *                             "extra" property that specifies the
   *                             path to the schema file from which
   *                             this matching rule use was loaded.
   */
  public void toString(StringBuilder buffer,
                       boolean includeFileElement)
  {
    buffer.append("( ");
    buffer.append(matchingRule.getOID());

    if (! names.isEmpty())
    {
      Iterator<String> iterator = names.values().iterator();

      String firstName = iterator.next();
      if (iterator.hasNext())
      {
        buffer.append(" NAME ( '");
        buffer.append(firstName);

        while (iterator.hasNext())
        {
          buffer.append("' '");
          buffer.append(iterator.next());
        }

        buffer.append("' )");
      }
      else
      {
        buffer.append(" NAME '");
        buffer.append(firstName);
        buffer.append("'");
      }
    }

    if ((description != null) && (description.length() > 0))
    {
      buffer.append(" DESC '");
      buffer.append(description);
      buffer.append("'");
    }

    if (isObsolete)
    {
      buffer.append(" OBSOLETE");
    }

    buffer.append(" APPLIES ");
    Iterator<AttributeType> iterator = attributes.iterator();
    String firstName = iterator.next().getNameOrOID();
    if (iterator.hasNext())
    {
      buffer.append("( ");
      buffer.append(firstName);

      while (iterator.hasNext())
      {
        buffer.append(" $ ");
        buffer.append(iterator.next().getNameOrOID());
      }

      buffer.append(" )");
    }
    else
    {
      buffer.append(firstName);
    }

    if (! extraProperties.isEmpty())
    {
      for (String property : extraProperties.keySet())
      {
        if ((! includeFileElement) &&
            property.equals(SCHEMA_PROPERTY_FILENAME))
        {
          continue;
        }

        List<String> valueList = extraProperties.get(property);

        buffer.append(" ");
        buffer.append(property);

        if (valueList.size() == 1)
        {
          buffer.append(" '");
          buffer.append(valueList.get(0));
          buffer.append("'");
        }
        else
        {
          buffer.append(" ( ");

          for (String value : valueList)
          {
            buffer.append("'");
            buffer.append(value);
            buffer.append("' ");
          }

          buffer.append(")");
        }
      }
    }

    buffer.append(" )");
  }
}

