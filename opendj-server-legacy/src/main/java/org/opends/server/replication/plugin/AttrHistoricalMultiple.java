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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.Iterator;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;

import com.forgerock.opendj.util.SmallSet;

/**
 * This class is used to store historical information for multiple valued attributes.
 * One object of this type is created for each attribute that was changed in the entry.
 * It allows to record the last time a given value was added,the last
 * time a given value was deleted and the last time the whole attribute was deleted.
 */
public class AttrHistoricalMultiple extends AttrHistorical
{
  /** Last time when the attribute was deleted. */
  private CSN deleteTime;
  /** Last time the attribute was modified. */
  private CSN lastUpdateTime;
  /** Change history for the values of this attribute. */
  private final SmallSet<AttrValueHistorical> valuesHist = new SmallSet<>();

   /**
    * Create a new object from the provided information.
    * @param deleteTime the last time this attribute was deleted
    * @param updateTime the last time this attribute was updated
    * @param valuesHist the new attribute values when updated.
    */
   AttrHistoricalMultiple(CSN deleteTime, CSN updateTime, Set<AttrValueHistorical> valuesHist)
   {
     this.deleteTime = deleteTime;
     this.lastUpdateTime = updateTime;
     if (valuesHist != null)
     {
      this.valuesHist.addAll(valuesHist);
     }
   }

   /** Creates a new object. */
   public AttrHistoricalMultiple()
   {
     this.deleteTime = null;
     this.lastUpdateTime = null;
   }

   /**
    * Returns the last time when the attribute was updated.
    * @return the last time when the attribute was updated
    */
   CSN getLastUpdateTime()
   {
     return lastUpdateTime;
   }

   @Override
   public CSN getDeleteTime()
   {
     return deleteTime;
   }

   /**
    * Delete all historical information that is older than the provided CSN for
    * this attribute type.
    * Add the delete attribute state information
    * @param csn time when the delete was done
    */
   void delete(CSN csn)
   {
     // iterate through the values in the valuesInfo and suppress all the values
     // that have not been added after the date of this delete.
     for (Iterator<AttrValueHistorical> it = valuesHist.iterator(); it.hasNext();)
     {
       AttrValueHistorical info = it.next();
       if (csn.isNewerThanOrEqualTo(info.getValueUpdateTime()) &&
           csn.isNewerThanOrEqualTo(info.getValueDeleteTime()))
      {
        it.remove();
      }
     }

     if (csn.isNewerThan(deleteTime))
     {
       deleteTime = csn;
     }

     if (csn.isNewerThan(lastUpdateTime))
     {
       lastUpdateTime = csn;
     }
   }

  /**
   * Update the historical of this attribute after deleting a set of values.
   *
   * @param attr
   *          the attribute containing the set of values that were deleted
   * @param csn
   *          time when the delete was done
   */
  void delete(Attribute attr, CSN csn)
  {
    AttributeType attrType = attr.getAttributeDescription().getAttributeType();
    for (ByteString val : attr)
    {
      delete(val, attrType, csn);
    }
  }

   /**
   * Update the historical of this attribute after a delete value.
   *
   * @param val
   *          value that was deleted
   * @param attrType
   * @param csn
   *          time when the delete was done
   */
  void delete(ByteString val, AttributeType attrType, CSN csn)
   {
     update(csn, new AttrValueHistorical(val, attrType, null, csn));
   }

  /**
   * Update the historical information when values are added.
   *
   * @param attr
   *          the attribute containing the set of added values
   * @param csn
   *          time when the add is done
   */
  private void add(Attribute attr, CSN csn)
  {
    AttributeType attrType = attr.getAttributeDescription().getAttributeType();
    for (ByteString val : attr)
    {
      add(val, attrType, csn);
    }
  }

  /**
   * Update the historical information when a value is added.
   *
   * @param addedValue
   *          the added value
   * @param attrType
   *          the attribute type of the added value
   * @param csn
   *          time when the value was added
   */
  void add(ByteString addedValue, AttributeType attrType, CSN csn)
  {
    update(csn, new AttrValueHistorical(addedValue, attrType, csn, null));
  }

  private void update(CSN csn, AttrValueHistorical valInfo)
  {
    valuesHist.addOrReplace(valInfo);
    if (csn.isNewerThan(lastUpdateTime))
    {
      lastUpdateTime = csn;
    }
  }

  @Override
  public Set<AttrValueHistorical> getValuesHistorical()
  {
    return valuesHist;
  }

  @Override
  public boolean replayOperation(Iterator<Modification> modsIterator, CSN csn,
      Entry modifiedEntry, Modification m)
  {
    if (csn.isNewerThanOrEqualTo(getLastUpdateTime())
        && m.getModificationType() == ModificationType.REPLACE)
    {
      processLocalOrNonConflictModification(csn, m);
      return false;// the attribute was not modified more recently
    }
    // We are replaying an operation that was already done
    // on another master server and this operation has a potential
    // conflict with some more recent operations on this same entry
    // we need to take the more complex path to solve them
    return replayPotentialConflictModification(modsIterator, csn, modifiedEntry, m);
  }

  private boolean replayPotentialConflictModification(Iterator<Modification> modsIterator, CSN csn,
      Entry modifiedEntry, Modification m)
  {
    // the attribute was modified after this change -> conflict
    switch (m.getModificationType().asEnum())
    {
    case DELETE:
      if (csn.isOlderThan(getDeleteTime()))
      {
        /* this delete is already obsoleted by a more recent delete
         * skip this mod
         */
        modsIterator.remove();
        return true;
      }

      if (!processDeleteConflict(csn, m, modifiedEntry))
      {
        modsIterator.remove();
        return true;
      }
      return false;

    case ADD:
      if (!processAddConflict(csn, m))
      {
        modsIterator.remove();
        return true;
      }
      return false;

    case REPLACE:
      if (csn.isOlderThan(getDeleteTime()))
      {
        /* this replace is already obsoleted by a more recent delete
         * skip this mod
         */
        modsIterator.remove();
        return true;
      }

      /* save the values that are added by the replace operation into addedValues
       * first process the replace as a delete operation
       * -> this generates a list of values that should be kept
       * then process the addedValues as if they were coming from an add
       * -> this generates the list of values that needs to be added
       * concatenate the 2 generated lists into a replace
       */
      boolean conflict = false;
      Attribute addedValues = m.getAttribute();
      m.setAttribute(new AttributeBuilder(addedValues.getAttributeDescription()).toAttribute());

      processDeleteConflict(csn, m, modifiedEntry);
      Attribute keptValues = m.getAttribute();

      m.setAttribute(addedValues);
      if (!processAddConflict(csn, m))
      {
        modsIterator.remove();
        conflict = true;
      }

      AttributeBuilder builder = new AttributeBuilder(keptValues);
      builder.addAll(m.getAttribute());
      m.setAttribute(builder.toAttribute());
      return conflict;

    case INCREMENT:
      // TODO : FILL ME
      return false;

    default:
      return false;
    }
  }

  @Override
  public void processLocalOrNonConflictModification(CSN csn, Modification mod)
  {
    /*
     * The operation is either a non-conflicting operation or a local operation
     * so there is no need to check the historical information for conflicts.
     * If this is a local operation, then this code is run after
     * the pre-operation phase.
     * If this is a non-conflicting replicated operation, this code is run
     * during the handleConflictResolution().
     */

    Attribute modAttr = mod.getAttribute();
    AttributeType type = modAttr.getAttributeDescription().getAttributeType();

    switch (mod.getModificationType().asEnum())
    {
    case DELETE:
      if (modAttr.isEmpty())
      {
        delete(csn);
      }
      else
      {
        delete(modAttr, csn);
      }
      break;

    case ADD:
      if (type.isSingleValue())
      {
        delete(csn);
      }
      add(modAttr, csn);
      break;

    case REPLACE:
      /* TODO : can we replace specific attribute values ????? */
      delete(csn);
      add(modAttr, csn);
      break;

    case INCREMENT:
      /* FIXME : we should update CSN */
      break;
    }
  }

  /**
   * Process a delete attribute values that is conflicting with a previous modification.
   *
   * @param csn The CSN of the currently processed change
   * @param m the modification that is being processed
   * @param modifiedEntry the entry that is modified (before current mod)
   * @return {@code true} if no conflict was detected, {@code false} otherwise.
   */
  private boolean processDeleteConflict(CSN csn, Modification m, Entry modifiedEntry)
  {
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

    Attribute modAttr = m.getAttribute();
    if (modAttr.isEmpty())
    {
      // We are processing a DELETE attribute modification
      m.setModificationType(ModificationType.REPLACE);
      AttributeBuilder builder = new AttributeBuilder(modAttr.getAttributeDescription());

      for (Iterator<AttrValueHistorical> it = valuesHist.iterator(); it.hasNext();)
      {
        AttrValueHistorical valInfo = it.next();

        if (csn.isOlderThan(valInfo.getValueUpdateTime()))
        {
          // this value has been updated after this delete,
          // therefore this value must be kept
          builder.add(valInfo.getAttributeValue());
        }
        else if (csn.isNewerThanOrEqualTo(valInfo.getValueDeleteTime()))
        {
          /*
           * this value is going to be deleted, remove it from historical
           * information unless it is a Deleted attribute value that is
           * more recent than this DELETE
           */
          it.remove();
        }
      }

      m.setAttribute(builder.toAttribute());

      if (csn.isNewerThan(getDeleteTime()))
      {
        deleteTime = csn;
      }
      if (csn.isNewerThan(getLastUpdateTime()))
      {
        lastUpdateTime = csn;
      }
    }
    else
    {
      // we are processing DELETE of some attribute values
      AttributeBuilder builder = new AttributeBuilder(modAttr);

      AttributeType attrType = modAttr.getAttributeDescription().getAttributeType();
      for (ByteString val : modAttr)
      {
        boolean deleteIt = true;  // true if the delete must be done
        boolean addedInCurrentOp = false;

        // update historical information
        AttrValueHistorical valInfo = new AttrValueHistorical(val, attrType, null, csn);
        AttrValueHistorical oldValInfo = valuesHist.get(valInfo);
        if (oldValInfo == null)
        {
          valuesHist.add(valInfo);
        }
        else
        {
          // this value already exist in the historical information
          if (csn.equals(oldValInfo.getValueUpdateTime()))
          {
            // This value was added earlier in the same operation
            // we need to keep the delete.
            addedInCurrentOp = true;
          }
          if (csn.isNewerThanOrEqualTo(oldValInfo.getValueDeleteTime()) &&
              csn.isNewerThanOrEqualTo(oldValInfo.getValueUpdateTime()))
          {
            valuesHist.addOrReplace(valInfo);
          }
          else if (oldValInfo.isUpdate())
          {
            deleteIt = false;
          }
        }

        /* if the attribute value is not to be deleted
         * or if attribute value is not present suppress it from the
         * MOD to make sure the delete is going to succeed
         */
        if (!deleteIt
            || (!modifiedEntry.hasValue(modAttr.getAttributeDescription(), val) && ! addedInCurrentOp))
        {
          // this value was already deleted before and therefore
          // this should not be replayed.
          builder.remove(val);
          if (builder.isEmpty())
          {
            // This was the last values in the set of values to be deleted.
            // this MOD must therefore be skipped.
            return false;
          }
        }
      }

      m.setAttribute(builder.toAttribute());

      if (csn.isNewerThan(getLastUpdateTime()))
      {
        lastUpdateTime = csn;
      }
    }

    return true;
  }

  /**
   * Process a add attribute values that is conflicting with a previous modification.
   *
   * @param csn
   *          the historical info associated to the entry
   * @param m
   *          the modification that is being processed
   * @return {@code true} if no conflict was detected, {@code false} otherwise.
   */
  private boolean processAddConflict(CSN csn, Modification m)
  {
    /*
     * if historicalattributedelete is newer forget this mod else find
     * attr value if does not exist add historicalvalueadded timestamp
     * add real value in entry else if timestamp older and already was
     * historicalvalueadded update historicalvalueadded else if
     * timestamp older and was historicalvaluedeleted change
     * historicalvaluedeleted into historicalvalueadded add value in
     * real entry
     */

    if (csn.isOlderThan(getDeleteTime()))
    {
      /* A delete has been done more recently than this add
       * forget this MOD ADD
       */
      return false;
    }

    Attribute attribute = m.getAttribute();
    AttributeBuilder builder = new AttributeBuilder(attribute);
    AttributeType attrType = attribute.getAttributeDescription().getAttributeType();
    for (ByteString addVal : attribute)
    {
      AttrValueHistorical valInfo = new AttrValueHistorical(addVal, attrType, csn, null);
      AttrValueHistorical oldValInfo = valuesHist.get(valInfo);
      if (oldValInfo == null)
      {
        /* this value does not exist yet
         * add it in the historical information
         * let the operation process normally
         */
        valuesHist.add(valInfo);
      }
      else
      {
        if  (oldValInfo.isUpdate())
        {
          /* if the value is already present
           * check if the updateTime must be updated
           * in all cases suppress this value from the value list
           * as it is already present in the entry
           */
          if (csn.isNewerThan(oldValInfo.getValueUpdateTime()))
          {
            valuesHist.addOrReplace(valInfo);
          }
          builder.remove(addVal);
        }
        else
        { // it is a delete
          /* this value is marked as a deleted value
           * check if this mod is more recent the this delete
           */
          if (csn.isNewerThanOrEqualTo(oldValInfo.getValueDeleteTime()))
          {
            valuesHist.addOrReplace(valInfo);
          }
          else
          {
            /* the delete that is present in the historical information
             * is more recent so it must win,
             * remove this value from the list of values to add
             * don't update the historical information
             */
            builder.remove(addVal);
          }
        }
      }
    }

    Attribute attr = builder.toAttribute();
    m.setAttribute(attr);

    if (attr.isEmpty())
    {
      return false;
    }

    if (csn.isNewerThan(getLastUpdateTime()))
    {
      lastUpdateTime = csn;
    }
    return true;
  }

  @Override
  public void assign(HistAttrModificationKey histKey, AttributeType attrType, ByteString value, CSN csn)
  {
    switch (histKey)
    {
    case ADD:
      if (value != null)
      {
        add(value, attrType, csn);
      }
      break;

    case DEL:
      if (value != null)
      {
        delete(value, attrType, csn);
      }
      break;

    case REPL:
      delete(csn);
      if (value != null)
      {
        add(value, attrType, csn);
      }
      break;

    case ATTRDEL:
      delete(csn);
      break;
    }
  }

  @Override
  public String toString()
  {
    final StringBuilder sb = new StringBuilder();
    sb.append(getClass().getSimpleName()).append("(");
    boolean deleteAppended = false;
    if (deleteTime != null)
    {
      deleteAppended = true;
      sb.append("deleteTime=").append(deleteTime);
    }
    if (lastUpdateTime != null)
    {
      if (deleteAppended)
      {
        sb.append(", ");
      }
      sb.append("lastUpdateTime=").append(lastUpdateTime);
    }
    sb.append(", valuesHist=").append(valuesHist);
    sb.append(")");
    return sb.toString();
  }
}
