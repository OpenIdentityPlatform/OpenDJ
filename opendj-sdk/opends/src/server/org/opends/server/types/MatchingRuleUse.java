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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.opends.server.api.MatchingRule;

import static org.opends.server.loggers.Debug.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a data structure for storing and interacting
 * with a matching rule use definition, which may be used to restrict
 * the set of attribute types that may be used for a given matching
 * rule.
 */
public class MatchingRuleUse
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.types.MatchingRuleUse";



  // Indicates whether this matching rule use is declared "obsolete".
  private boolean isObsolete;

  // The set of names that may be used to refer to this matching rule
  // use, mapped between their all-lowercase representations and the
  // user-defined representations.
  private ConcurrentHashMap<String,String> names;

  // The set of attribute types with which this matching rule use is
  // associated.
  private CopyOnWriteArraySet<AttributeType> attributes;

  // The set of additional name-value pairs associated with this
  // matching rule use definition.
  private ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
               extraProperties;

  // The matching rule with which this matching rule use is
  // associated.
  private MatchingRule matchingRule;

  // The description for this matching rule use.
  private String description;

  // The path to the schema file that contains this matching rule use
  // definition.
  private String schemaFile;



  /**
   * Creates a new matching rule use definition with the provided
   * information.
   *
   * @param  matchingRule     The matching rule for this matching rule
   *                          use.
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
  public MatchingRuleUse(MatchingRule matchingRule,
              ConcurrentHashMap<String,String> names,
              String description, boolean isObsolete,
              CopyOnWriteArraySet<AttributeType> attributes,
              ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
                   extraProperties)
  {
    assert debugConstructor(CLASS_NAME,
                            new String[]
                            {
                              String.valueOf(matchingRule),
                              String.valueOf(names),
                              String.valueOf(description),
                              String.valueOf(isObsolete),
                              String.valueOf(attributes),
                              String.valueOf(extraProperties)
                            });


    this.matchingRule    = matchingRule;
    this.names           = names;
    this.description     = description;
    this.isObsolete      = isObsolete;
    this.attributes      = attributes;
    this.schemaFile      = null;
    this.extraProperties = extraProperties;
  }



  /**
   * Retrieves the matching rule for this matching rule use.
   *
   * @return  The matching rule for this matching rule use.
   */
  public MatchingRule getMatchingRule()
  {
    assert debugEnter(CLASS_NAME, "getMatchingRule");

    return matchingRule;
  }



  /**
   * Specifies the matching rule for this matching rule use.
   *
   * @param  matchingRule  The matching rule for this matching rule
   *                       use.
   */
  public void setMatchingRule(MatchingRule matchingRule)
  {
    assert debugEnter(CLASS_NAME, "setMatchingRule",
                      String.valueOf(matchingRule));

    this.matchingRule = matchingRule;
  }



  /**
   * Retrieves the set of names for this matching rule use.  The
   * mapping will be between the names in all lowercase form and the
   * names in the user-defined form.
   *
   * @return  The set of names for this matching rule use.
   */
  public ConcurrentHashMap<String,String> getNames()
  {
    assert debugEnter(CLASS_NAME, "getNames");

    return names;
  }



  /**
   * Retrieves the primary name to use when referencing this matching
   * rule use.
   *
   * @return  The primary name to use when referencing this matching
   *          rule use, or <CODE>null</CODE> if there is none.
   */
  public String getName()
  {
    assert debugEnter(CLASS_NAME, "getName");

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
   * @return  <CODE>true</CODE> if this matching rule use has the
   *          specified name, or <CODE>false</CODE> if not.
   */
  public boolean hasName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "hasName",
                      String.valueOf(lowerName));

    return names.containsKey(lowerName);
  }



  /**
   * Specifies the set of names for this matching rule use as a
   * mapping between the names in all lowercase form and the names in
   * the user-defined form.
   *
   * @param  names  The set of names for this matching rule use.
   */
  public void setNames(ConcurrentHashMap<String,String> names)
  {
    assert debugEnter(CLASS_NAME, "setNames", String.valueOf(names));

    this.names = names;
  }



  /**
   * Adds the provided name to the set of names for this matching rule
   * use.
   *
   * @param  name  The name to add to the set of names for this
   *               matching rule use.
   */
  public void addName(String name)
  {
    assert debugEnter(CLASS_NAME, "addName", String.valueOf(name));

    names.put(toLowerCase(name), name);
  }



  /**
   * Removes the provided name from the set of names for this matching
   * rule use.  This will have no effect if the specified name is not
   * associated with this matching rule use.
   *
   * @param  lowerName  The name to remove from the set of names for
   *                    this matching rule use, in all lowercase
   *                    characters.
   */
  public void removeName(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "removeName",
                      String.valueOf(lowerName));

    names.remove(lowerName);
  }



  /**
   * Retrieves the path to the schema file that contains the
   * definition for this matching rule use.
   *
   * @return  The path to the schema file that contains the definition
   *          for this matching rule use, or <CODE>null</CODE> if it
   *          is not known or if it is not stored in any schema file.
   */
  public String getSchemaFile()
  {
    assert debugEnter(CLASS_NAME, "getSchemaFile");

    return schemaFile;
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
    assert debugEnter(CLASS_NAME, "setSchemaFile",
                      String.valueOf(schemaFile));

    this.schemaFile = schemaFile;
  }



  /**
   * Retrieves the description for this matching rule use.
   *
   * @return  The description for this matching rule use, or
   *          <CODE>null</CODE> if there is none.
   */
  public String getDescription()
  {
    assert debugEnter(CLASS_NAME, "getDescription");

    return description;
  }



  /**
   * Specifies the description for this matching rule use.
   *
   * @param  description  The description for this matching rule use.
   */
  public void setDescription(String description)
  {
    assert debugEnter(CLASS_NAME, "setDescription",
                      String.valueOf(description));

    this.description = description;
  }



  /**
   * Indicates whether this matching rule use is declared "obsolete".
   *
   * @return  <CODE>true</CODE> if this matching rule use is declared
   *          "obsolete", or <CODE>false</CODE> if it is not.
   */
  public boolean isObsolete()
  {
    assert debugEnter(CLASS_NAME, "isObsolete");

    return isObsolete;
  }



  /**
   * Specifies whether this matching rule use is declared "obsolete".
   *
   * @param  isObsolete  Specifies whether this matching rule use is
   *                     declared "obsolete".
   */
  public void setObsolete(boolean isObsolete)
  {
    assert debugEnter(CLASS_NAME, "setObsolete",
                      String.valueOf(isObsolete));

    this.isObsolete = isObsolete;
  }



  /**
   * Retrieves the set of attributes associated with this matching
   * rule use.
   *
   * @return  The set of attributes associated with this matching
   *          rule use.
   */
  public CopyOnWriteArraySet<AttributeType> getAttributes()
  {
    assert debugEnter(CLASS_NAME, "getAttributes");

    return attributes;
  }



  /**
   * Indicates whether the provided attribute type is referenced by
   * this matching rule use.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   *
   * @return  <CODE>true</CODE> if the provided attribute type is
   *          referenced by this matching rule use, or
   *          <CODE>false</CODE> if it is not.
   */
  public boolean appliesToAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "appliesToAttribute",
                      String.valueOf(attributeType));

    return attributes.contains(attributeType);
  }



  /**
   * Specifies the set of attributes for this matching rule use.
   *
   * @param  attributes  The set of attributes for this matching rule
   *                     use.
   */
  public void setAttributes(
                   CopyOnWriteArraySet<AttributeType> attributes)
  {
    assert debugEnter(CLASS_NAME, "setAttributes",
                      String.valueOf(attributes));

    this.attributes = attributes;
  }



  /**
   * Adds the provided attribute type to the set of attributes for
   * this matching rule use.  This will have no effect if the provided
   * attribute type is already associated with this matching rule use.
   *
   * @param  attributeType  The attribute type to add to the set of
   *                        attributes for this matching rule use.
   */
  public void addAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "addAttribute",
                      String.valueOf(attributeType));

    attributes.add(attributeType);
  }



  /**
   * Removes the provided attribute type from the set of attributes
   * for this matching rule use.  This will have no effect if the
   * provided attribute type is not associated with this matching rule
   * use.
   *
   * @param  attributeType  The attribute type to remove from the set
   *                        of attributes for this matching rule use.
   */
  public void removeAttribute(AttributeType attributeType)
  {
    assert debugEnter(CLASS_NAME, "addAttribute",
                      String.valueOf(attributeType));

    attributes.remove(attributeType);
  }



  /**
   * Retrieves a mapping between the names of any extra non-standard
   * properties that may be associated with this matching rule use and
   * the value for that property.  The caller may alter the contents
   * of this mapping.
   *
   * @return  A mapping between the names of any extra non-standard
   *          properties that may be associated with this matching
   *          rule use and the value for that property.
   */
  public ConcurrentHashMap<String,CopyOnWriteArrayList<String>>
              getExtraProperties()
  {
    assert debugEnter(CLASS_NAME, "getExtraProperties");

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
   *          matching rule use, or <CODE>null</CODE> if no such
   *          property is defined.
   */
  public CopyOnWriteArrayList<String>
              getExtraProperty(String propertyName)
  {
    assert debugEnter(CLASS_NAME, "getExtraProperty",
                      String.valueOf(propertyName));

    return extraProperties.get(propertyName);
  }



  /**
   * Indicates whether the provided object is equal to this matching
   * rule use.  The object will be considered equal if it is a
   * matching rule use with the same matching rule.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is equal to
   *          this matching rule use, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    assert debugEnter(CLASS_NAME, "equals");

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
    assert debugEnter(CLASS_NAME, "hashCode");

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
    assert debugEnter(CLASS_NAME, "toString");

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
    assert debugEnter(CLASS_NAME, "toString",
                      "java.lang.StringBuilder",
                      String.valueOf(includeFileElement));

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
        CopyOnWriteArrayList<String> valueList =
             extraProperties.get(property);

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

    if (includeFileElement && (schemaFile != null) &&
        (! extraProperties.containsKey(SCHEMA_PROPERTY_FILENAME)))
    {
      buffer.append(" ");
      buffer.append(SCHEMA_PROPERTY_FILENAME);
      buffer.append(" '");
      buffer.append(schemaFile);
      buffer.append("'");
    }

    buffer.append(" )");
  }
}

