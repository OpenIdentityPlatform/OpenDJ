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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.util;

import static org.forgerock.util.Reject.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;



/**
 * This class defines a data structure for a change record entry for
 * an add operation.  It includes a DN and a set of attributes, as well as
 * methods to decode the entry.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class AddChangeRecordEntry extends ChangeRecordEntry
{

  /**
   * The entry attributes for this operation.
   */
  private final List<Attribute> attributes;



  /**
   * Creates a new entry with the provided information.
   *
   * @param dn
   *          The distinguished name for this entry.  It must not be
   *          <CODE>null</CODE>.
   * @param attributes
   *          The entry attributes for this operation.  It must not be
   *          <CODE>null</CODE>.
   */
  public AddChangeRecordEntry(DN dn,
      Map<AttributeType,List<Attribute>> attributes)
  {
    super(dn);


    ifNull(attributes);


    this.attributes = new ArrayList<>(attributes.size());
    for (List<Attribute> list : attributes.values())
    {
      this.attributes.addAll(list);
    }
  }



  /**
   * Retrieves the name of the change operation type.
   *
   * @return  The name of the change operation type.
   */
  public ChangeOperationType getChangeOperationType()
  {
    return ChangeOperationType.ADD;
  }



  /**
   * Retrieves the entire set of attributes for this entry.
   * <p>
   * The returned list is read-only.
   *
   * @return The entire unmodifiable list of attributes for this entry.
   */
  public List<Attribute> getAttributes()
  {
    return Collections.unmodifiableList(attributes);
  }



  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append("AddChangeRecordEntry(dn=\"");
    buffer.append(getDN());
    buffer.append("\", attrs={");

    Iterator<Attribute> iterator = attributes.iterator();
    while (iterator.hasNext())
    {
      buffer.append(iterator.next().getName());
      if (iterator.hasNext())
      {
        buffer.append(", ");
      }
    }
    buffer.append("})");

    return buffer.toString();
  }
}

