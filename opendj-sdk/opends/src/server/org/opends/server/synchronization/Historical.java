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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;


/**
 * This class is used to store historical information that is
 * used to resolve modify conflicts
 *
 * It is assumed that the common case is not to have conflict and
 * therefore is optimized (in order of importance) for :
 *  1- detecting potential conflict
 *  2- fast update of historical information for non-conflicting change
 *  3- fast and efficient purge
 *  4- compact
 *  5- solve conflict. This should also be as fast as possible but
 *     not at the cost of any of the other previous objectives
 *
 * One Historical object is created for each entry in the entry cache
 * each Historical Object contains a list of attribute historical information
 */

public class Historical
{
  static final String HISTORICALATTRIBUTENAME = "ds-sync-hist";
  static final AttributeType historicalAttrType =
    DirectoryServer.getSchema().getAttributeType(HISTORICALATTRIBUTENAME);
  static final String ENTRYUIDNAME = "entryuuid";
  static final AttributeType entryuuidAttrType =
    DirectoryServer.getSchema().getAttributeType(ENTRYUIDNAME);

  /*
   * The last update seen on this entry, allows fast conflict detection.
   */
  private ChangeNumber moreRecentChangenumber =
                              new ChangeNumber(0,0,(short)0);

  /*
   * contains Historical information for each attribute sorted by attribute type
   */
  private HashMap<AttributeType,AttrInfoWithOptions> attributesInfo
                           = new HashMap<AttributeType,AttrInfoWithOptions>();

  /**
   * Duplicates an Historical Object.
   * attributesInfo are nor duplicated but used as references.
   * @return The duplicate of the Historical Object
   */
  public Historical duplicate()
  {
    Historical dup = new Historical();

    dup.attributesInfo =
                new HashMap<AttributeType,AttrInfoWithOptions>(attributesInfo);
    dup.moreRecentChangenumber = this.moreRecentChangenumber;
    return dup;
  }

  /**
   * Process an operation.
   * This method is responsible for detecting and resolving conflict for
   * modifyOperation. This is done by using the historical information.
   *
   * @param modifyOperation the operation to be processed
   * @param modifiedEntry the entry that is being modified (before modification)
   */
  public void replayOperation(ModifyOperation modifyOperation,
                              Entry modifiedEntry)
  {
    List<Modification> mods = modifyOperation.getModifications();
    ChangeNumber changeNumber =
      OperationContext.getChangeNumber(modifyOperation);

    for (Iterator modsIterator = mods.iterator(); modsIterator.hasNext();)
    {
      Modification m = (Modification) modsIterator.next();
      Attribute modAttr = m.getAttribute();
      Set<String> options = modAttr.getOptions();
      if (options.isEmpty())
        options = null;
      AttributeType type = modAttr.getAttributeType();
      AttrInfoWithOptions attrInfoWithOptions =  attributesInfo.get(type);
      AttrInfo attrInfo = null;
      if (attrInfoWithOptions != null)
        attrInfo = attrInfoWithOptions.get(options);

      if (this.hasConflict(attrInfo, changeNumber))
      {
        // We are replaying an operation that was already done
        // on another master server and this operation has a potential
        // conflict
        // with some more recent operations on this same entry
        // we need to take the more complex path to solve them

        switch (m.getModificationType())
        {
        case DELETE:
          if (changeNumber.older(attrInfo.getDeleteTime()))
          {
            /* this delete is already obsoleted by a more recent delete
             * skip this mod
             */
            modsIterator.remove();
            break;
          }

          this.conflictDelete(changeNumber,
              type, m, modifiedEntry, attrInfo, modAttr);
          break;

        case ADD:
          this.conflictAdd(modsIterator, changeNumber, attrInfo,
              modAttr.getValues(), modAttr.getOptions());
          break;

        case REPLACE:
          if (changeNumber.older(attrInfo.getDeleteTime()))
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
          LinkedHashSet<AttributeValue> addedValues  = modAttr.getValues();
          modAttr.setValues(new LinkedHashSet<AttributeValue>());

          this.conflictDelete(changeNumber, type, m, modifiedEntry,
              attrInfo, modAttr);

          LinkedHashSet<AttributeValue> keptValues = modAttr.getValues();
          this.conflictAdd(modsIterator, changeNumber, attrInfo, addedValues,
              modAttr.getOptions());
          keptValues.addAll(addedValues);
          break;

        case INCREMENT:
          // TODO : FILL ME
          break;
        }
      }
      else
        processLocalOrNonConflictModification(changeNumber, m);
    }

    // TODO : now purge old historical information

    if (moreRecentChangenumber == null ||
        moreRecentChangenumber.older(changeNumber))
      moreRecentChangenumber = changeNumber;
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
  private void processLocalOrNonConflictModification(ChangeNumber changeNumber,
      Modification mod)
  {
    /*
     * The operation is either a non-conflicting operation or a local
     * operation so there is no need to check the historical information
     * for conflicts.
     * If this is a local operation, the this code is run during
     * the pre-operation phase (TODO : should make sure that synchronization
     * is always run after all other plugins)
     * If this is a non-conflicting replicated operation, this code is run
     * during the handleConflictResolution().
     */

    Attribute modAttr = mod.getAttribute();
    Set<String> options = modAttr.getOptions();
    if (options.isEmpty())
      options = null;
    AttributeType type = modAttr.getAttributeType();
    AttrInfoWithOptions attrInfoWithOptions =  attributesInfo.get(type);
    AttrInfo attrInfo;
    if (attrInfoWithOptions != null)
      attrInfo = attrInfoWithOptions.get(options);
    else
      attrInfo = null;

    /*
     * The following code only works for multi-valued attributes.
     * TODO : See impact of single valued attributes
     */
    if (attrInfo == null)
    {
      attrInfo = new AttrInfo();
      if (attrInfoWithOptions == null)
        attrInfoWithOptions = new AttrInfoWithOptions();
      attrInfoWithOptions.put(options, attrInfo);
      attributesInfo.put(type, attrInfoWithOptions);
    }
    switch (mod.getModificationType())
    {
    case DELETE:
      if (modAttr.getValues().isEmpty())
        attrInfo.delete(changeNumber);
      else
        attrInfo.delete(modAttr.getValues(), changeNumber);
      break;

    case ADD:
      if (type.isSingleValue())
      {
        attrInfo.delete(changeNumber);
      }
      attrInfo.add(modAttr.getValues(), changeNumber);
      break;

    case REPLACE:
      /* TODO : can we replace specific attribute values ????? */
      attrInfo.delete(changeNumber);
      attrInfo.add(modAttr.getValues(), changeNumber);
      break;

    case INCREMENT:
      /* FIXME : we should update ChangeNumber */
      break;
    }
  }


  /**
   * Append replacement of state information to a given modification.
   * @param modifyOperation the modification.
   */
  public void generateState(ModifyOperation modifyOperation)
  {
    List<Modification> mods = modifyOperation.getModifications();
    Entry modifiedEntry = modifyOperation.getModifiedEntry();
    ChangeNumber changeNumber =
      OperationContext.getChangeNumber(modifyOperation);

    /*
     * If this is a local operation we need first to update the historical
     * information, then update the entry with the historical information
     * If this is a replicated operation the historical information has
     * already been set in the resolveConflict phase and we only need
     * to update the entry
     */
    if (!modifyOperation.isSynchronizationOperation())
    {
      for (Iterator modsIterator = mods.iterator(); modsIterator.hasNext();)
      {
        Modification mod = (Modification) modsIterator.next();
        processLocalOrNonConflictModification(changeNumber, mod);
      }
      if (moreRecentChangenumber == null ||
          moreRecentChangenumber.older(changeNumber))
        moreRecentChangenumber = changeNumber;
    }

    LinkedHashSet<AttributeValue> hist = new LinkedHashSet<AttributeValue>();

    for (Map.Entry<AttributeType, AttrInfoWithOptions> entryWithOptions :
                                                   attributesInfo.entrySet())

    {
      AttributeType type = entryWithOptions.getKey();
      HashMap<Set<String> ,AttrInfo> attrwithoptions =
                                entryWithOptions.getValue().getAttributesInfo();

      for (Map.Entry<Set<String>, AttrInfo> entry : attrwithoptions.entrySet())
      {
        boolean delAttr = false;
        Set<String> options = entry.getKey();
        String optionsString = "";
        AttrInfo info = entry.getValue();
        ChangeNumber deleteTime = info.getDeleteTime();

        if (options != null)
          for (String s : options)
            optionsString.concat(";"+s);

        /* generate the historical information for deleted attributes */
        if (deleteTime != null)
        {
          delAttr = true;
        }

        /* generate the historical information for modified attribute values */
        for (ValueInfo valInfo : info.getValuesInfo())
        {
          String strValue;
          if (valInfo.getValueDeleteTime() != null)
          {
            strValue = type.getNormalizedPrimaryName() + optionsString + ":" +
            valInfo.getValueDeleteTime().toString() +
            ":del:" + valInfo.getValue().toString();
            AttributeValue val = new AttributeValue(historicalAttrType,
                                                    strValue);
            hist.add(val);
          }
          else if (valInfo.getValueUpdateTime() != null)
          {
            if (delAttr && valInfo.getValueUpdateTime() == deleteTime)
            {
              strValue = type.getNormalizedPrimaryName() + optionsString + ":" +
              valInfo.getValueUpdateTime().toString() +  ":repl:" +
              valInfo.getValue().toString();
              delAttr = false;
            }
            else
            {
              strValue = type.getNormalizedPrimaryName() + optionsString + ":" +
              valInfo.getValueUpdateTime().toString() +
              ":add:" + valInfo.getValue().toString();
            }

            AttributeValue val = new AttributeValue(historicalAttrType,
                                                    strValue);
            hist.add(val);
          }
        }

        if (delAttr)
        {
          String strValue = type.getNormalizedPrimaryName()
              + optionsString + ":" + deleteTime.toString()
              + ":attrDel";
          delAttr = false;
          AttributeValue val = new AttributeValue(historicalAttrType, strValue);
          hist.add(val);
        }
      }
    }

    Attribute attr;
    Modification mod;

    if (hist.isEmpty())
      attr = new Attribute(historicalAttrType, HISTORICALATTRIBUTENAME, null);
    else
      attr = new Attribute(historicalAttrType, HISTORICALATTRIBUTENAME, hist);
    mod = new Modification(ModificationType.REPLACE, attr);
    mods.add(mod);
    modifiedEntry.removeAttribute(historicalAttrType);
    modifiedEntry.addAttribute(attr, null);
  }

  /**
   * Detect if a new change has a potential conflict with previous changes.
   * Must be blinding fast
   * @param info the historical information for the attribute that
   * was modified
   * @param newChange the ChangeNumber of the change for which we need to check
   *        for potential conflict
   * @return true if there is a potential conflict, false otherwise
   */
  private boolean hasConflict(AttrInfo info, ChangeNumber newChange)
  {
    // if I've already seen a change that is more recetn than the one
    // that is currently being processed, then there is
    // a potential conflict
    if (ChangeNumber.compare(newChange, moreRecentChangenumber) <= 0)
    {
      if (info == null)
        return false;   // the attribute was never modified -> no conflict
      else
      if (ChangeNumber.compare(newChange, info.getLastUpdateTime()) <= 0)
        return true; // the attribute was modified after this change -> conflict
      else
        return false;// the attribute was not modified more recently
    }
    else
      return false;
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
                                AttrInfo attrInfo, Attribute modAttr )
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

      for (Iterator it = attrInfo.getValuesInfo().iterator();
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
      if (changeNumber.newer(attrInfo.getDeleteTime()))
        attrInfo.setDeleteTime(changeNumber);
      if (changeNumber.newer(attrInfo.getLastUpdateTime()))
        attrInfo.setLastUpdateTime(changeNumber);
    }
    else
    {
      /*
       * we are processing DELETE of some attribute values
       */
      ArrayList<ValueInfo> valuesInfo = attrInfo.getValuesInfo();

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
      if (changeNumber.newer(attrInfo.getLastUpdateTime()))
        attrInfo.setLastUpdateTime(changeNumber);
    }
    return true;
  }

  /**
   * Process a add attribute values that is conflicting with a previous
   * modification.
   *
   * @param modsIterator iterator on the list of modification
   * @param changeNumber  the historical info associated to the entry
   * @param attrInfo the historical info associated to the entry
   * @param addValues the values that are added
   * @param options the options that are added
   * @return false if operation becomes empty and must not be processed
   */
  private boolean conflictAdd(Iterator modsIterator, ChangeNumber changeNumber,
                          AttrInfo attrInfo,
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

    if (changeNumber.older(attrInfo.getDeleteTime()))
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
      ArrayList<ValueInfo> valuesInfo = attrInfo.getValuesInfo();
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
      modsIterator.remove();

    if (changeNumber.newer(attrInfo.getLastUpdateTime()))
      attrInfo.setLastUpdateTime(changeNumber);
    return true;
  }

  /**
   * read the historical information from the entry attribute and
   * load it into the Historical object attached to the entry.
   * @param entry The entry which historical information must be loaded
   * @return the generated Historical information
   */
  public static Historical load(Entry entry)
  {
    List<Attribute> hist = entry.getAttribute(historicalAttrType);
    Historical histObj = new Historical();
    AttributeType lastAttrType = null;
    Set<String> lastOptions = null;
    AttrInfo attrInfo = null;
    AttrInfoWithOptions attrInfoWithOptions = null;

    if (hist == null)
      return histObj;

    for (Attribute attr : hist)
    {
      for (AttributeValue val : attr.getValues())
      {
        HistVal histVal = new HistVal(val.getStringValue());
        AttributeType attrType = histVal.getAttrType();
        Set<String> options = histVal.getOptions();
        ChangeNumber cn = histVal.getCn();
        AttributeValue value = histVal.getAttributeValue();
        HistKey histKey = histVal.getHistKey();

        if (attrType == null)
        {
          /*
           * This attribute is unknown from the schema
           * Just skip it, the modification will be processed but no
           * historical information is going to be kept.
           * TODO : recovery tool should deal with this, add some logging.
           */
          continue;
        }

        /* if attribute type does not match we create new
         *   AttrInfoWithOptions and AttrInfo
         *   we also add old AttrInfoWithOptions into histObj.attributesInfo
         * if attribute type match but options does not match we create new
         *   AttrInfo that we add to AttrInfoWithOptions
         * if both match we keep everything
         */
        if (attrType != lastAttrType)
        {
          attrInfo = new AttrInfo();
          attrInfoWithOptions = new AttrInfoWithOptions();
          attrInfoWithOptions.put(options, attrInfo);
          histObj.attributesInfo.put(attrType, attrInfoWithOptions);

          lastAttrType = attrType;
          lastOptions = options;
        }
        else
        {
          attrType = lastAttrType;
          if (options != lastOptions)
          {
            attrInfo = new AttrInfo();
            attrInfoWithOptions.put(options, attrInfo);
            lastOptions = options;
          }
        }

        if (histObj.moreRecentChangenumber.older(cn))
        {
          histObj.moreRecentChangenumber = cn;
        }

        switch (histKey)
        {
        case ADD:
          if (value != null)
          {
            attrInfo.add(value, cn);
          }
        break;

        case DEL:
          if (value != null)
          {
            attrInfo.delete(value, cn);
          }
        break;

        case REPL:
          attrInfo.delete(cn);
          if (value != null)
          {
            attrInfo.add(value, cn);
          }
        break;

        case DELATTR:
          attrInfo.delete(cn);
        break;
        }
      }
    }

    /* set the reference to the historical information in the entry */
    return histObj;
  }
  /**
   * Use this historical information to generate fake operations that would
   * result in this historical information.
   * TODO : This is only implemented for modify operation, should implement ADD
   *        DELETE and MODRDN.
   * @param entry The Entry to use to generate the FakeOperation Iterable.
   *
   * @return an Iterable of FakeOperation that would result in this historical
   *         information.
   */
  public static Iterable<FakeOperation> generateFakeOperations(Entry entry)
  {
    TreeMap<ChangeNumber, FakeOperation> operations =
            new TreeMap<ChangeNumber, FakeOperation>();
    List<Attribute> attrs = entry.getOperationalAttribute(historicalAttrType);
    if (attrs != null)
    {
      for (Attribute attr : attrs)
      {
        for (AttributeValue val : attr.getValues())
        {
          HistVal histVal = new HistVal(val.getStringValue());
          ChangeNumber cn = histVal.getCn();
          Modification mod = histVal.generateMod();
          ModifyFakeOperation modifyFakeOperation;

          FakeOperation fakeOperation = operations.get(cn);

          if (fakeOperation != null)
          {
            try
            {
              fakeOperation.addModification(mod);
            } catch (Exception e)
            {
              /*
               *  TODO : REPAIR : This Exception shows that there are some
               *  inconsistency in the historical information.
               *  This method can't fix the problem.
               *  This should be logged and somehow the repair
               *  service should get called to fix the problem.
               */
            }
          }
          else
          {
            String uuidString = getEntryUuid(entry);
            if (uuidString != null)
            {
                modifyFakeOperation = new ModifyFakeOperation(entry.getDN(),
                      cn, uuidString);

                modifyFakeOperation.addModification(mod);
                operations.put(histVal.getCn(), modifyFakeOperation);
            }
          }
        }
      }
    }
    return operations.values();
  }

  /**
   * Get the entry unique Id in String form.
   *
   * @param entry The entry for which the unique id should be returned.
   *
   * @return The Unique Id of the entry if it has one. null, otherwise.
   */
  public static String getEntryUuid(Entry entry)
  {
    String uuidString = null;
    List<Attribute> uuidAttrs =
             entry.getOperationalAttribute(entryuuidAttrType);
    if (uuidAttrs != null)
    {
      Attribute uuid = uuidAttrs.get(0);
      if (uuid.hasValue())
      {
        AttributeValue uuidVal = uuid.getValues().iterator().next();
        uuidString =  uuidVal.getStringValue();
      }
    }
    return uuidString;
  }

  /**
   * Get the Entry Unique Id from an add operation.
   * This must be called after the entry uuid preop plugin (i.e no
   * sooner than the synchronization provider pre-op)
   *
   * @param op The operation
   * @return The Entry Unique Id String form.
   */
  public static String getEntryUuid(AddOperation op)
  {
    String uuidString = null;
    Map<AttributeType, List<Attribute>> attrs = op.getOperationalAttributes();
    List<Attribute> uuidAttrs = attrs.get(entryuuidAttrType);

    if (uuidAttrs != null)
    {
      Attribute uuid = uuidAttrs.get(0);
      if (uuid.hasValue())
      {
        AttributeValue uuidVal = uuid.getValues().iterator().next();
        uuidString =  uuidVal.getStringValue();
      }
    }
    return uuidString;
  }
}

