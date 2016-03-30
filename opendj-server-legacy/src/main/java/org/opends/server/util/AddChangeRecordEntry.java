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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.util;

import static org.forgerock.util.Reject.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.types.Attribute;



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

  @Override
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
      buffer.append(iterator.next().getAttributeDescription());
      if (iterator.hasNext())
      {
        buffer.append(", ");
      }
    }
    buffer.append("})");

    return buffer.toString();
  }
}
