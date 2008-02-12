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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;


/**
 * This classes is used to store historical information for multiple valued
 * attributes.
 * One object of this type is created for each attribute that was changed in
 * the entry.
 * It allows to record the last time a given value was added, the last
 * time a given value was deleted and the last time the whole attribute was
 * deleted.
 */
public class AttrInfoMultiple extends AttributeInfo
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
   public AttrInfoMultiple(ChangeNumber deleteTime, ChangeNumber updateTime,
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
   public AttrInfoMultiple()
   {
     this.deleteTime = null;
     this.lastUpdateTime = null;
     this.valuesInfo = new ArrayList<ValueInfo>();
   }

   /**
    * Returns the last time when the entry was updated.
    * @return the last time when the entry was updated
    */
   private ChangeNumber getLastUpdateTime()
   {
     return lastUpdateTime;
   }

   /**
    * Returns the last time when the attribute was deleted.
    *
    * @return the last time when the attribute was deleted
    */
   public ChangeNumber getDeleteTime()
   {
     return deleteTime;
   }

   /**
    * Duplicate an AttrInfo.
    * ChangeNumber are duplicated by references
    * @return the duplicated AttrInfo
    */
   AttrInfoMultiple duplicate()
   {
     AttrInfoMultiple dup =
       new AttrInfoMultiple(this.deleteTime, this.lastUpdateTime,
           this.valuesInfo);
     return dup;
   }

   /**
    * Delete all historical information that is older than
    * the provided ChangeNumber for this attribute type.
    * Add the delete attribute state information
    * @param CN time when the delete was done
    */
   protected void delete(ChangeNumber CN)
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
   protected void delete(AttributeValue val, ChangeNumber CN)
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
   protected void delete(LinkedHashSet<AttributeValue> values, ChangeNumber CN)
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
   protected void add(AttributeValue val, ChangeNumber CN)
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
   private void add(LinkedHashSet<AttributeValue> values,
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
   *
   * @return the List of ValueInfo
   */
  public ArrayList<ValueInfo> getValuesInfo()
  {
    return valuesInfo;
  }

  /**
   * {@inheritDoc}
   */
  public boolean replayOperation(
      Iterator<Modification> modsIterator, ChangeNumber changeNumber,
      Entry modifiedEntry, Modification m)
  {
    // We are replaying an operation that was already done
    // on another master server and this operation has a potential
    // conflict
    // with some more recent operations on this same entry
    // we need to take the more complex path to solve them
    Attribute modAttr = m.getAttribute();
    AttributeType type = modAttr.getAttributeType();

    if (ChangeNumber.compare(changeNumber, getLastUpdateTime()) <= 0)
    {
      // the attribute was modified after this change -> conflict

      switch (m.getModificationType())
      {
      case DELETE:
        if (changeNumber.older(getDeleteTime()))
        {
          /* this delete is already obsoleted by a more recent delete
           * skip this mod
           */
          modsIterator.remove();
          break;
        }

        conflictDelete(changeNumber, type, m, modifiedEntry, modAttr);
        break;

      case ADD:
        conflictAdd(modsIterator, changeNumber,
                    modAttr.getValues(), modAttr.getOptions());
        break;

      case REPLACE:
        if (changeNumber.older(getDeleteTime()))
        {
          /* this replace is already obsoleted by a more recent delete
           * skip this mod
           */
          modsIterator.remove();
          break;
        }
        /* save the values that are added by the replace operation
         * into addedValues
         * first process the replace as a delete operation -> this generate
         * a list of values that should be kept
         * then process the addedValues as if they were coming from a add
         * -> this generate the list of values that needs to be added
         * concatenate the 2 generated lists into a replace
         */
        LinkedHashSet<AttributeValue> addedValues = modAttr.getValues();
        modAttr.setValues(new LinkedHashSet<AttributeValue>());

        this.conflictDelete(changeNumber, type, m, modifiedEntry, modAttr);

        LinkedHashSet<AttributeValue> keptValues = modAttr.getValues();
        this.conflictAdd(modsIterator, changeNumber, addedValues,
            modAttr.getOptions());
        keptValues.addAll(addedValues);
        break;

      case INCREMENT:
        // TODO : FILL ME
        break;
      }
      return true;
    }
    else
    {
      processLocalOrNonConflictModification(changeNumber, m);
      return false;// the attribute was not modified more recently
    }
  }

  /**
   * This method calculate the historical information and update the hist
   * attribute to store the historical information for modify operation that
   * does not conflict with previous operation.
   * This is the usual path and should therefore be optimized.
   *
   * It does not check if the operation to process is conflicting or not with
   * previous operations. The caller is responsible for this.
   *
   * @param changeNumber The changeNumber of the operation to process
   * @param mod The modify operation to process.
   */
  public void processLocalOrNonConflictModification(ChangeNumber changeNumber,
      Modification mod)
  {
    /*
     * The operation is either a non-conflicting operation or a local
     * operation so there is no need to check the historical information
     * for conflicts.
     * If this is a local operation, then this code is run after
     * the pre-operation phase.
     * If this is a non-conflicting replicated operation, this code is run
     * during the handleConflictResolution().
     */

    Attribute modAttr = mod.getAttribute();
    AttributeType type = modAttr.getAttributeType();

    switch (mod.getModificationType())
    {
    case DELETE:
      if (modAttr.getValues().isEmpty())
      {
        delete(changeNumber);
      }
      else
      {
        delete(modAttr.getValues(), changeNumber);
      }
      break;

    case ADD:
      if (type.isSingleValue())
      {
        delete(changeNumber);
      }
      add(modAttr.getValues(), changeNumber);
      break;

    case REPLACE:
      /* TODO : can we replace specific attribute values ????? */
      delete(changeNumber);
      add(modAttr.getValues(), changeNumber);
      break;

    case INCREMENT:
      /* FIXME : we should update ChangeNumber */
      break;
    }
  }

  /**
   * Process a delete attribute values that is conflicting with a previous
   * modification.
   *
   * @param changeNumber The changeNumber of the currently processed change
   * @param type attribute type
   * @param m the modification that is being processed
   * @param modifiedEntry the entry that is modified (before current mod)
   * @param attrInfo the historical info associated to the entry
   * @param modAttr the attribute modification
   * @return false if there is nothing to do
   */
  private boolean conflictDelete(ChangeNumber changeNumber,
                                AttributeType type, Modification m,
                                Entry modifiedEntry,
                                Attribute modAttr )
  {
    LinkedHashSet<AttributeValue> delValues;
    LinkedHashSet<AttributeValue> replValues;

    /*
     * We are processing a conflicting DELETE modification
     *
     * This code is written on the assumption that conflict are
     * rare. We therefore don't care much about the performance
     * However since it is rarely executed this code needs to be
     * as simple as possible to make sure that all paths are tested.
     * In this case the most simple seem to change the DELETE
     * in a REPLACE modification that keeps all values
     * more recent that the DELETE.
     * we are therefore going to change m into a REPLACE that will keep
     * all the values that have been updated after the DELETE time
     * If a value is present in the entry without any state information
     * it must be removed so we simply ignore them
     */



    delValues = modAttr.getValues();
    if ((delValues == null) || (delValues.isEmpty()))
    {
      /*
       * We are processing a DELETE attribute modification
       */
      m.setModificationType(ModificationType.REPLACE);
      replValues = new LinkedHashSet<AttributeValue>();

      for (Iterator it = getValuesInfo().iterator();
           it.hasNext();)
      {
        ValueInfo valInfo; valInfo = (ValueInfo) it.next();

        if (changeNumber.older(valInfo.getValueUpdateTime()))
        {
          /*
           * this value has been updated after this delete, therefore
           * this value must be kept
           */
          replValues.add(valInfo.getValue());
        }
        else
        {
          /*
           * this value is going to be deleted, remove it from historical
           * information unless it is a Deleted attribute value that is
           * more recent than this DELETE
           */
          if (changeNumber.newerOrEquals(valInfo.getValueDeleteTime()))
          {
            it.remove();
          }
        }
      }

      modAttr.setValues(replValues);
      if (changeNumber.newer(getDeleteTime()))
      {
        deleteTime = changeNumber;
      }
      if (changeNumber.newer(getLastUpdateTime()))
      {
        lastUpdateTime = changeNumber;
      }
    }
    else
    {
      /*
       * we are processing DELETE of some attribute values
       */
      ArrayList<ValueInfo> valuesInfo = getValuesInfo();

      for (Iterator<AttributeValue> delValIterator = delValues.iterator();
           delValIterator.hasNext();)
      {
        AttributeValue val = delValIterator.next();
        Boolean deleteIt = true;  // true if the delete must be done

        /* update historical information */
        ValueInfo valInfo = new ValueInfo(val, null, changeNumber);
        int index = valuesInfo.indexOf(valInfo);
        if (index != -1)
        {
          /* this value already exist in the historical information */
          ValueInfo oldValInfo  = valuesInfo.get(index);
          if (changeNumber.newer(oldValInfo.getValueDeleteTime()) &&
              changeNumber.newer(oldValInfo.getValueUpdateTime()))
          {
            valuesInfo.remove(index);
            valuesInfo.add(valInfo);
          }
          else if (oldValInfo.isUpdate())
          {
            deleteIt = false;
          }
        }
        else
        {
          valuesInfo.add(valInfo);
        }
        /* if the attribute value is not to be deleted
         * or if attribute value is not present suppress it from the
         * mod to make sure the delete is going to process again
         */
        modifiedEntry.getAttribute(type);
        if (!deleteIt
            || !modifiedEntry.hasValue(type, modAttr.getOptions(), val))
        {
          delValIterator.remove();
        }
      }
      if (changeNumber.newer(getLastUpdateTime()))
      {
        lastUpdateTime = changeNumber;
      }
    }
    return true;
  }

  /**
   * Process a add attribute values that is conflicting with a previous
   * modification.
   *
   * @param modsIterator iterator on the list of modification
   * @param changeNumber  the historical info associated to the entry
   * @param addValues the values that are added
   * @param options the options that are added
   * @return false if operation becomes empty and must not be processed
   */
  private boolean conflictAdd(Iterator modsIterator, ChangeNumber changeNumber,
                          LinkedHashSet<AttributeValue> addValues,
                          Set<String> options)
  {
    /* if historicalattributedelete is newer forget this mod
     *   else find attr value
     *     if does not exist
     *           add historicalvalueadded timestamp
     *           add real value in entry
     *     else if timestamp older and already was historicalvalueadded
     *        update historicalvalueadded
     *     else if timestamp older and was historicalvaluedeleted
     *        change historicalvaluedeleted into historicalvalueadded
     *        add value in real entry
     */

    if (changeNumber.older(getDeleteTime()))
    {
      /* A delete has been done more recently than this add
       * forget this MOD ADD
       */
      modsIterator.remove();
      return false;
    }

    for (Iterator<AttributeValue> valIterator = addValues.iterator();
         valIterator.hasNext();)
    {
      AttributeValue addVal= valIterator.next();
      ArrayList<ValueInfo> valuesInfo = getValuesInfo();
      ValueInfo valInfo = new ValueInfo(addVal, changeNumber, null);
      int index = valuesInfo.indexOf(valInfo);
      if (index == -1)
      {
        /* this values does not exist yet
         * add it in the historical information
         * let the operation process normally
         */
        valuesInfo.add(valInfo);
      }
      else
      {
        ValueInfo oldValueInfo = valuesInfo.get(index);
        if  (oldValueInfo.isUpdate())
        {
          /* if the value is already present
           * check if the updateTime must be updated
           * in all cases suppress this value from the value list
           * as it is already present in the entry
           */
          if (changeNumber.newer(oldValueInfo.getValueUpdateTime()))
          {
            valuesInfo.remove(index);
            valuesInfo.add(valInfo);
          }
          valIterator.remove();
        }
        else
        {
          /* this value is marked as a deleted value
           * check if this mod is more recent the this delete
           */
          if (changeNumber.newer(oldValueInfo.getValueDeleteTime()))
          {
            /* this add is more recent,
             * remove the old delete historical information
             * and add our more recent one
             * let the operation process
             */
            valuesInfo.remove(index);
            valuesInfo.add(valInfo);
          }
          else
          {
            /* the delete that is present in the historical information
             * is more recent so it must win,
             * remove this value from the list of values to add
             * don't update the historical information
             */
            valIterator.remove();
          }
        }
      }
    }
    if (addValues.isEmpty())
    {
      modsIterator.remove();
    }

    if (changeNumber.newer(getLastUpdateTime()))
    {
      lastUpdateTime = changeNumber;
    }
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void load(HistKey histKey, AttributeValue value, ChangeNumber cn)
  {
    switch (histKey)
    {
    case ADD:
      if (value != null)
      {
        add(value, cn);
      }
      break;

    case DEL:
      if (value != null)
      {
        delete(value, cn);
      }
      break;

    case REPL:
      delete(cn);
      if (value != null)
      {
        add(value, cn);
      }
      break;

    case DELATTR:
        delete(cn);
      break;
    }
  }
}


