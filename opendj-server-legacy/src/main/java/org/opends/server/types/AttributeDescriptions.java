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
 *      Copyright 2015-2016 ForgeRock AS
 */
package org.opends.server.types;

import org.forgerock.opendj.ldap.AttributeDescription;

/** Temporary class until we move fully to {@link AttributeDescription}. */
public class AttributeDescriptions
{
  private AttributeDescriptions()
  {
    // private for utility class
  }

  /**
   * Creates an attribute description with the attribute type and options of the provided
   * {@link Attribute}.
   *
   * @param attr
   *          The attribute.
   * @return The attribute description.
   * @throws NullPointerException
   *           If {@code attributeType} or {@code options} was {@code null}.
   */
  public static AttributeDescription create(Attribute attr)
  {
    return AttributeDescription.create(attr.getAttributeType(), attr.getOptions());
  }
}
