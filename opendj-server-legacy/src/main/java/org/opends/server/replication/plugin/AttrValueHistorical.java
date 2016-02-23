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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.replication.common.CSN;

/** AttrValueHistorical is the historical information of the modification of one attribute value. */
public class AttrValueHistorical
{
  private ByteString value;
  private CSN valueDeleteTime;
  private CSN valueUpdateTime;

  /**
   * Build an AttrValueHistorical for a provided attribute value, providing
   * the last time the provided value is either updated or deleted.
   * @param value    the provided attributeValue
   * @param csnUpdate last time when this value was updated
   * @param csnDelete last time when this value for deleted
   */
  public AttrValueHistorical(ByteString value, CSN csnUpdate, CSN csnDelete)
  {
    this.value = value;
    this.valueUpdateTime = csnUpdate;
    this.valueDeleteTime = csnDelete;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj instanceof AttrValueHistorical)
    {
      AttrValueHistorical objVal = (AttrValueHistorical) obj;
      return value.equals(objVal.getAttributeValue());
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    return value.hashCode();
  }

  /**
   * Get the last time when the value was deleted.
   * @return the last time when the value was deleted
   */
  public CSN getValueDeleteTime()
  {
    return valueDeleteTime;
  }

  /**
   * Get the last time when the value was updated.
   * @return the last time when the value was updated
   */
  public CSN getValueUpdateTime()
  {
    return valueUpdateTime;
  }

  /**
   * Get the attributeValue for which this object was generated.
   * @return the value for which this object was generated
   */
  public ByteString getAttributeValue()
  {
    return value;
  }

  /**
   * Check if the value associated with this object was updated.
   * @return true if the value associated with this object was updated
   */
  public boolean isUpdate()
  {
    return valueUpdateTime != null;
  }

  @Override
  public String toString()
  {
    if (valueUpdateTime != null)
    {
      return valueDeleteTime != null
          // valueUpdateTime and valueDeleteTime should have the same value
          ? valueUpdateTime + ":replace:" + value
          : valueUpdateTime + ":add:" + value;
    }
    else
    {
      return valueDeleteTime != null
          ? valueDeleteTime + ":delete:" + value
          : "????:" + value;
    }
  }
}
