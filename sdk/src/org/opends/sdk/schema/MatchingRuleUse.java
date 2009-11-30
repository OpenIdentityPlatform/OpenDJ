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



import static org.opends.messages.SchemaMessages.ERR_ATTR_SYNTAX_MRUSE_UNKNOWN_ATTR;
import static org.opends.messages.SchemaMessages.ERR_ATTR_SYNTAX_MRUSE_UNKNOWN_MATCHING_RULE;

import java.util.*;

import org.opends.messages.Message;
import org.opends.sdk.util.Validator;



/**
 * This class defines a data structure for storing and interacting with
 * a matching rule use definition, which may be used to restrict the set
 * of attribute types that may be used for a given matching rule.
 */
public final class MatchingRuleUse extends SchemaElement
{
  // The OID of the matching rule associated with this matching rule
  // use definition.
  private final String oid;

  // The set of user defined names for this definition.
  private final List<String> names;

  // Indicates whether this definition is declared "obsolete".
  private final boolean isObsolete;

  // The set of attribute types with which this matching rule use is
  // associated.
  private final Set<String> attributeOIDs;

  // The definition string used to create this objectclass.
  private final String definition;

  private MatchingRule matchingRule;
  private Set<AttributeType> attributes = Collections.emptySet();



  MatchingRuleUse(String oid, List<String> names, String description,
      boolean obsolete, Set<String> attributeOIDs,
      Map<String, List<String>> extraProperties, String definition)
  {
    super(description, extraProperties);

    Validator.ensureNotNull(oid, names, attributeOIDs);
    this.oid = oid;
    this.names = names;
    this.isObsolete = obsolete;
    this.attributeOIDs = attributeOIDs;

    if (definition != null)
    {
      this.definition = definition;
    }
    else
    {
      this.definition = buildDefinition();
    }
  }



  /**
   * Retrieves the set of attributes associated with this matching rule
   * use.
   * 
   * @return The set of attributes associated with this matching rule
   *         use.
   */
  public Iterable<AttributeType> getAttributes()
  {
    return attributes;
  }



  /**
   * Retrieves the matching rule for this matching rule use.
   * 
   * @return The matching rule for this matching rule use.
   */
  public MatchingRule getMatchingRule()
  {
    return matchingRule;
  }



  /**
   * Retrieves the matching rule OID for this schema definition.
   * 
   * @return The OID for this schema definition.
   */
  public String getMatchingRuleOID()
  {
    return oid;
  }



  /**
   * Retrieves the name or matching rule OID for this schema definition.
   * If it has one or more names, then the primary name will be
   * returned. If it does not have any names, then the OID will be
   * returned.
   * 
   * @return The name or OID for this schema definition.
   */
  public String getNameOrOID()
  {
    if (names.isEmpty())
    {
      return oid;
    }
    return names.get(0);
  }



  /**
   * Retrieves an iterable over the set of user-defined names that may
   * be used to reference this schema definition.
   * 
   * @return Returns an iterable over the set of user-defined names that
   *         may be used to reference this schema definition.
   */
  public Iterable<String> getNames()
  {
    return names;
  }



  /**
   * Indicates whether the provided attribute type is referenced by this
   * matching rule use.
   * 
   * @param attributeType
   *          The attribute type for which to make the determination.
   * @return {@code true} if the provided attribute type is referenced
   *         by this matching rule use, or {@code false} if it is not.
   */
  public boolean hasAttribute(AttributeType attributeType)
  {
    return attributes.contains(attributeType);
  }



  @Override
  public int hashCode()
  {
    return oid.hashCode();
  }



  /**
   * Indicates whether this schema definition has the specified name.
   * 
   * @param name
   *          The name for which to make the determination.
   * @return <code>true</code> if the specified name is assigned to this
   *         schema definition, or <code>false</code> if not.
   */
  public boolean hasName(String name)
  {
    for (final String n : names)
    {
      if (n.equalsIgnoreCase(name))
      {
        return true;
      }
    }
    return false;
  }



  /**
   * Indicates whether this schema definition has the specified name or
   * matching rule OID.
   * 
   * @param value
   *          The value for which to make the determination.
   * @return <code>true</code> if the provided value matches the OID or
   *         one of the names assigned to this schema definition, or
   *         <code>false</code> if not.
   */
  public boolean hasNameOrOID(String value)
  {
    return hasName(value) || oid.equals(value);
  }



  /**
   * Indicates whether this schema definition is declared "obsolete".
   * 
   * @return <code>true</code> if this schema definition is declared
   *         "obsolete", or <code>false</code> if not.
   */
  public boolean isObsolete()
  {
    return isObsolete;
  }



  /**
   * Retrieves the string representation of this schema definition in
   * the form specified in RFC 2252.
   * 
   * @return The string representation of this schema definition in the
   *         form specified in RFC 2252.
   */
  @Override
  public String toString()
  {
    return definition;
  }



  MatchingRuleUse duplicate()
  {
    return new MatchingRuleUse(oid, names, description, isObsolete,
        attributeOIDs, extraProperties, definition);
  }



  @Override
  void toStringContent(StringBuilder buffer)
  {
    buffer.append(oid);

    if (!names.isEmpty())
    {
      final Iterator<String> iterator = names.iterator();

      final String firstName = iterator.next();
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

    if (description != null && description.length() > 0)
    {
      buffer.append(" DESC '");
      buffer.append(description);
      buffer.append("'");
    }

    if (isObsolete)
    {
      buffer.append(" OBSOLETE");
    }

    if (!attributeOIDs.isEmpty())
    {
      final Iterator<String> iterator = attributeOIDs.iterator();

      final String firstName = iterator.next();
      if (iterator.hasNext())
      {
        buffer.append(" APPLIES ( ");
        buffer.append(firstName);

        while (iterator.hasNext())
        {
          buffer.append(" $ ");
          buffer.append(iterator.next());
        }

        buffer.append(" )");
      }
      else
      {
        buffer.append(" APPLIES ");
        buffer.append(firstName);
      }
    }
  }



  @Override
  void validate(List<Message> warnings, Schema schema)
      throws SchemaException
  {
    try
    {
      matchingRule = schema.getMatchingRule(oid);
    }
    catch (final UnknownSchemaElementException e)
    {
      // This is bad because the matching rule use is associated with a
      // matching rule that we don't know anything about.
      final Message message =
          ERR_ATTR_SYNTAX_MRUSE_UNKNOWN_MATCHING_RULE.get(definition,
              oid);
      throw new SchemaException(message, e);
    }

    attributes = new HashSet<AttributeType>(attributeOIDs.size());
    AttributeType attributeType;
    for (final String attribute : attributeOIDs)
    {
      try
      {
        attributeType = schema.getAttributeType(attribute);
      }
      catch (final UnknownSchemaElementException e)
      {
        final Message message =
            ERR_ATTR_SYNTAX_MRUSE_UNKNOWN_ATTR.get(oid, attribute);
        throw new SchemaException(message, e);
      }
      attributes.add(attributeType);
    }
  }
}
