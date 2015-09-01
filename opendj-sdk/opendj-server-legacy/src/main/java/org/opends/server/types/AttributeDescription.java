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
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.Set;

/** Temporary class until we move to {@link org.forgerock.opendj.ldap.AttributeDescription}. */
public final class AttributeDescription
{
  final AttributeType attributeType;
  final Set<String> options;

  AttributeDescription(Attribute attr)
  {
    this(attr.getAttributeType(), attr.getOptions());
  }

  AttributeDescription(AttributeType attributeType, Set<String> options)
  {
    this.attributeType = attributeType;
    this.options = options;
  }

  String toOptionsString()
  {
    if (options != null)
    {
      StringBuilder optionsBuilder = new StringBuilder();
      for (String s : options)
      {
        optionsBuilder.append(';').append(s);
      }
      return optionsBuilder.toString();
    }
    return "";
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == this)
    {
      return true;
    }
    if (!(obj instanceof AttributeDescription))
    {
      return false;
    }
    final AttributeDescription other = (AttributeDescription) obj;
    return attributeType.equals(other.attributeType) && options.equals(other.options);
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((attributeType == null) ? 0 : attributeType.hashCode());
    result = prime * result + ((options == null) ? 0 : options.hashCode());
    return result;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "(" + "attributeType=" + attributeType + ", options=" + options + ")";
  }
}