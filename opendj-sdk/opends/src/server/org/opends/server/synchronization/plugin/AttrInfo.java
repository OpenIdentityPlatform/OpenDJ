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
package org.opends.server.synchronization.plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.types.AttributeValue;


/**
 * This classes is used to store historical information.
 * One object of this type is created for each attribute that was changed in
 * the entry.
 * It allows to record the last time a given value was added, the last
 * time a given value was deleted and the last time the whole attribute was
 * deleted.
 */
public class AttrInfo
{
   private ChangeNumber deleteTime, // last time when the attribute was deleted
                        lastUpdateTime; // last time the attribute was modified
   private ArrayList<ValueInfo> valuesInfo; // creation or deletion time for
                                            // given values
  /**
    * create a new AttrInfo object.
    * @param deleteTime the deletion time
    * @param updateTime the update time
    * @param valuesInfo of Value Info
    */
   public AttrInfo(ChangeNumber deleteTime, ChangeNumber updateTime,
       ArrayList<ValueInfo> valuesInfo)
   {
     this.deleteTime = deleteTime;
     this.lastUpdateTime = updateTime;
     if (valuesInfo == null)
       this.valuesInfo = new ArrayList<ValueInfo>();
     else
       this.valuesInfo = valuesInfo;
   }

   /**
    * create a new empty AttrInfo object.
    */
   public AttrInfo()
   {
     this.deleteTime = null;
     this.lastUpdateTime = null;
     this.valuesInfo = new ArrayList<ValueInfo>();
   }

   /**
    * Returns the last time when the entry was updated.
    * @return the last time when the entry was updated
    */
   ChangeNumber getLastUpdateTime()
   {
     return lastUpdateTime;
   }

   /**
    * Returns the last time when the entry was deleted.
    * @return the last time when the entry was deleted
    */
   ChangeNumber getDeleteTime()
   {
     return deleteTime;
   }

   /**
    * set the last time when the entry was deleted.
    * @param time the last time when the entry was deleted
    */
   void setDeleteTime(ChangeNumber time)
   {
     deleteTime = time;
   }

   /**
    * set the last time when the entry was updated.
    * @param time the last time when the entry was updated
    */
   void setLastUpdateTime(ChangeNumber time)
   {
     lastUpdateTime = time;
   }

   /**
    * Duplicate an AttrInfo.
    * ChangeNumber are duplicated by references
    * @return the duplicated AttrInfo
    */
   AttrInfo duplicate()
   {
     AttrInfo dup = new AttrInfo(this.deleteTime, this.lastUpdateTime,
                                                            this.valuesInfo);
     return dup;
   }

   /**
    * Delete all historical information that is older than
    * the provided ChangeNumber for this attribute type.
    * Add the delete attribute state information
    * @param CN time when the delete was done
    */
   void delete(ChangeNumber CN)
   {
     // iterate through the values in the valuesInfo
     // and suppress all the values that have not been added
     // after the date of this delete.
     Iterator<ValueInfo> it = this.valuesInfo.iterator();
     while (it.hasNext())
     {
       ValueInfo info = it.next();
       if (CN.newerOrEquals(info.getValueUpdateTime()))
         it.remove();
     }

     if (CN.newer(deleteTime))
     {
       deleteTime = CN;
     }

     if (CN.newer(lastUpdateTime))
     {
       lastUpdateTime = CN;
     }
   }

   /**
    * Change historical information after a delete value.
    * @param val value that was deleted
    * @param CN time when the delete was done
    */
   void delete(AttributeValue val, ChangeNumber CN)
   {
     ValueInfo info = new ValueInfo(val, null, CN);
     this.valuesInfo.remove(info);
     this.valuesInfo.add(info);
     if (CN.newer(lastUpdateTime))
     {
       lastUpdateTime = CN;
     }
   }

   /**
    * Change historical information after a delete of a set of values.
    *
    * @param values values that were deleted
   * @param CN time when the delete was done
    */
   void delete(LinkedHashSet<AttributeValue> values, ChangeNumber CN)
   {
     for (AttributeValue val : values)
     {
       ValueInfo info = new ValueInfo(val, null, CN);
       this.valuesInfo.remove(info);
       this.valuesInfo.add(info);
       if (CN.newer(lastUpdateTime))
       {
         lastUpdateTime = CN;
       }
     }
   }

   /**
    * Update the historical information when a value is added.
    *
    * @param val values that was added
    * @param CN time when the value was added
    */
   void add(AttributeValue val, ChangeNumber CN)
   {
     ValueInfo info = new ValueInfo(val, CN, null);
     this.valuesInfo.remove(info);
     valuesInfo.add(info);
     if (CN.newer(lastUpdateTime))
     {
       lastUpdateTime = CN;
     }
   }

   /**
    * Update the historical information when values are added.
    *
    * @param values the set of added values
    * @param CN time when the add is done
    */
   void add(LinkedHashSet<AttributeValue> values,
            ChangeNumber CN)
   {
     for (AttributeValue val : values)
     {
       ValueInfo info = new ValueInfo(val, CN, null);
       this.valuesInfo.remove(info);
       valuesInfo.add(info);
       if (CN.newer(lastUpdateTime))
       {
         lastUpdateTime = CN;
       }
     }
   }

  /**
   * Get the List of ValueInfo for this attribute Info.
   * @return the List of ValueInfo
   */
  public ArrayList<ValueInfo> getValuesInfo()
  {
    return valuesInfo;
  }
}


