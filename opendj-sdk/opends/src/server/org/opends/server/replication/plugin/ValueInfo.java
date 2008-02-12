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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.AttributeValue;

/**
 * Allows to store historical information about specific values
 * for a given attribute.
 */
public class ValueInfo
{
  private AttributeValue value;
  private ChangeNumber valueDeleteTime;
  private ChangeNumber valueUpdateTime;

  /**
   * Build a new ValueInfo.
   * @param value value for which ValueInfo is built
   * @param CNupdate last time when value was updated last
   * @param CNdelete last time when value for deleted
   */
  public ValueInfo(AttributeValue value,
                   ChangeNumber CNupdate,
                   ChangeNumber CNdelete)
  {
    this.value = value;
    this.valueUpdateTime = CNupdate;
    this.valueDeleteTime = CNdelete;
  }

  /**
   * Compares this object with another ValueInfo object.
   * Object are said equals when their values matches.
   * @param obj object to be compared with this object
   * @return true if equal, false otherwise
   */
  @Override
  public boolean equals(Object obj)
  {
    if (obj instanceof ValueInfo)
    {
      ValueInfo objVal = (ValueInfo) obj;
      return (value.equals(objVal.getValue()));
    }
    else
    {
      return false;
    }
  }

  /**
   * calculates the hasCode for this object.
   * Only value is used when calculating the hashCode
   * @return the hashcode
   */
  @Override
  public int hashCode()
  {
    return value.hashCode();
  }

  /**
   * Get the last time when the value was deleted.
   * @return the last time when the value was deleted
   */
  public ChangeNumber getValueDeleteTime()
  {
    return valueDeleteTime;
  }

  /**
   * Get the last time when the value was updated.
   * @return the last time when the value was updated
   */
  public ChangeNumber getValueUpdateTime()
  {
    return valueUpdateTime;
  }

  /**
   * Get the value for which this ValueInfo was generated.
   * @return the value for which this ValueInfo was generated
   */
  public AttributeValue getValue()
  {
    return value;
  }

  /**
   * Check if the value associated with this ValueInfo was updated.
   * @return true if the value associated with this ValueInfo was updated
   */
  public boolean isUpdate()
  {
    if (valueUpdateTime != null)
      return true;
    else
      return false;
  }
}
