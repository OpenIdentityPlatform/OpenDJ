/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.api;

import static org.opends.server.core.DirectoryServer.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.GeneralizedTime;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attribute.RemoveOnceSwitchingAttributes;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.PublicAPI;
import org.opends.server.types.StabilityLevel;

/**
 * This class is used to hold monitoring data, i.e. a list of attributes. It provides convenient
 * methods to easily build such data.
 * <p>
 * <strong>Note:</strong> <br>
 * Creating monitor entries may become a lot easier once we've migrated to the SDK Entry class:
 *
 * <pre>
 * Entry entry = ...;
 * entry.addAttribute("stringStat", "aString")
 *       .addAttribute("integerStat", 12345)
 *       .addAttribute("dnStat", DN.valueOf("dc=aDN");
 * </pre>
 *
 * We could also envisage an annotation based approach where we determine the monitor content from
 * annotated fields/methods in an object.
 */
@PublicAPI(stability = StabilityLevel.PRIVATE)
public final class MonitorData implements Iterable<Attribute>
{
  private final List<Attribute> attrs;

  /** Constructor to use when the number of attributes to create is unknown. */
  public MonitorData()
  {
    attrs = new ArrayList<>();
  }

  /**
   * Constructor that accepts the number of attributes to create.
   *
   * @param expectedAttributesCount
   *          number of attributes that will be added
   */
  public MonitorData(int expectedAttributesCount)
  {
    attrs = new ArrayList<>(expectedAttributesCount);
  }

  /**
   * Adds an attribute with the provided name and value.
   *
   * @param attrName
   *          the attribute name
   * @param attrValue
   *          the attribute value
   */
  public void add(String attrName, Object attrValue)
  {
    Syntax syntax;
    if (attrValue instanceof String
        || attrValue instanceof ByteString
        || attrValue instanceof Float
        || attrValue instanceof Double)
    {
      // coming first because they are the most common types
      syntax = CoreSchema.getDirectoryStringSyntax();
    }
    else if (attrValue instanceof Number)
    {
      syntax = CoreSchema.getIntegerSyntax();
    }
    else if (attrValue instanceof Boolean)
    {
      syntax = CoreSchema.getBooleanSyntax();
    }
    else if (attrValue instanceof DN)
    {
      syntax = CoreSchema.getDNSyntax();
    }
    else if (attrValue instanceof Date)
    {
      syntax = CoreSchema.getGeneralizedTimeSyntax();
      attrValue = GeneralizedTime.valueOf((Date) attrValue);
    }
    else if (attrValue instanceof Calendar)
    {
      syntax = CoreSchema.getGeneralizedTimeSyntax();
      attrValue = GeneralizedTime.valueOf((Calendar) attrValue);
    }
    else if (attrValue instanceof UUID)
    {
      syntax = CoreSchema.getUUIDSyntax();
    }
    else
    {
      syntax = CoreSchema.getDirectoryStringSyntax();
    }
    add(attrName, syntax, attrValue);
  }

  private void add(String attrName, Syntax syntax, Object attrValue)
  {
    AttributeType attrType = getSchema().getAttributeType(attrName, syntax);
    attrs.add(Attributes.create(attrType, String.valueOf(attrValue)));
  }

  /**
   * Adds an attribute with the provided name and value if the value is not null.
   *
   * @param attrName
   *          the attribute name
   * @param attrValue
   *          the attribute value
   */
  public void addIfNotNull(String attrName, Object attrValue)
  {
    if (attrValue != null)
    {
      add(attrName, attrValue);
    }
  }

  /**
   * Adds an attribute with the provided name and values.
   *
   * @param attrName
   *          the attribute name
   * @param attrValues
   *          the attribute values
   */
  @RemoveOnceSwitchingAttributes(comment = "once using the non immutable SDK's Attribute class, "
      + "we can incrementally build an attribute by using the add(String attrName, Object attrValue) method")
  public void add(String attrName, Collection<?> attrValues)
  {
    AttributeBuilder builder = new AttributeBuilder(attrName);
    builder.addAllStrings(attrValues);
    attrs.add(builder.toAttribute());
  }

  /**
   * Adds all the properties from the provided bean as attributes, prepending the provided prefix.
   *
   * @param bean
   *          the bean from which to read the properties
   * @param attributesPrefix
   *          the prefix to prepend to the attributes read from the bean
   * @throws ReflectiveOperationException
   *           if a problem occurs while reading the properties of the bean
   */
  public void addBean(Object bean, String attributesPrefix) throws ReflectiveOperationException
  {
    for (Method method : bean.getClass().getMethods())
    {
      if (method.getName().startsWith("get"))
      {
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(int.class) || returnType.equals(long.class) || returnType.equals(String.class))
        {
          addStatAttribute(attributesPrefix, bean, method, 3);
        }
      }
      else if (method.getName().startsWith("is") && method.getReturnType().equals(boolean.class))
      {
        addStatAttribute(attributesPrefix, bean, method, 2);
      }
    }
  }

  private void addStatAttribute(String attrPrefix, Object stats, Method method, int skipNameLen)
      throws ReflectiveOperationException
  {
    String attrName = attrPrefix + method.getName().substring(skipNameLen);
    add(attrName, method.invoke(stats));
  }

  @Override
  public Iterator<Attribute> iterator()
  {
    return attrs.iterator();
  }

  /**
   * Returns the number of attributes.
   *
   * @return the number of attributes
   */
  public int size()
  {
    return attrs.size();
  }

  @Override
  public String toString()
  {
    return getClass().getName() + attrs;
  }
}
