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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.getBytes;

import java.util.*;

import org.opends.messages.Message;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.types.*;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.util.TimeThread;

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

public class EntryHistorical
{
  /**
   * Name of the attribute used to store historical information.
   */
  public static final String HISTORICAL_ATTRIBUTE_NAME = "ds-sync-hist";

  /**
   * Name used to store attachment of historical information in the
   * operation. This attachement allows to use in several different places
   * the historical while reading/writing ONCE it from/to the entry.
   */
  public static final String HISTORICAL = "ds-synch-historical";

  /**
   * Name of the entryuuid attribute.
   */
  public static final String ENTRYUUID_ATTRIBUTE_NAME = "entryuuid";

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /* The delay to purge the historical informations
   * This delay indicates the time the domain keeps the historical
   * information necessary to solve conflicts.When a change stored in the
   * historical part of the user entry has a date (from its replication
   * ChangeNumber) older than this delay, it is candidate to be purged.
   * The purge is triggered on 2 events: modify of the entry, dedicated purge
   * task.
   *
   * The purge is done when the historical is encoded.
   */
  private long purgeDelayInMillisec = -1;

  /*
   * The oldest ChangeNumber stored in this entry historical attribute.
   * null when this historical object has been created from
   * an entry that has no historical attribute and after the last
   * historical has been purged.
   */
  private ChangeNumber oldestChangeNumber = null;

  /**
   * For stats/monitoring purpose, the number of historical values
   * purged the last time a purge has been applied on this entry historical.
   */
  private int lastPurgedValuesCount = 0;


  /**
   * The in-memory historical information is made of.
   *
   * EntryHistorical ::= ADDDate MODDNDate attributesInfo
   * ADDDate       ::= ChangeNumber  // the date the entry was added
   * MODDNDate     ::= ChangeNumber  // the date the entry was last renamed
   *
   * attributesInfo      ::= (AttrInfoWithOptions)*
   *                         one AttrInfoWithOptions by attributeType
   *
   * AttrInfoWithOptions ::= (AttributeInfo)*
   *                         one AttributeInfo by attributeType and option
   *
   * AttributeInfo       ::= AttrInfoSingle | AttrInfoMultiple
   *
   * AttrInfoSingle      ::= AddTime DeleteTime ValueInfo
   *
   * AttrInfoMultiple    ::= AddTime DeleteTime ValuesInfo
   *
   * ValuesInfo          ::= (AttrValueHistorical)*
   *                         AttrValueHistorical is the historical of the
   *                         the modification of one value
   *
   * AddTime             ::= ChangeNumber // last time the attribute was added
   *                                      // to the entry
   * DeleteTime          ::= ChangeNumber // last time the attribute was deleted
   *                                      // from the entry
   *
   * AttrValueHistorical ::= AttributeValue valueDeleteTime valueUpdateTime
   * valueDeleteTime     ::= ChangeNumber
   * valueUpdateTime     ::= ChangeNumber
   *
   * - a list indexed on AttributeType of AttrInfoWithOptions :
   *     each value is the historical for this attribute
   *     an AttrInfoWithOptions is a set indexed on the optionValue(string) of
   *     AttributeInfo
   *
   */

  // The date when the entry was added.
  private ChangeNumber entryADDDate = null;

  // The date when the entry was last renamed.
  private ChangeNumber entryMODDNDate = null;

  // contains Historical information for each attribute sorted by attribute type
  // key:AttributeType  value:AttrInfoWithOptions
  private HashMap<AttributeType,AttrHistoricalWithOptions> attributesHistorical
    = new HashMap<AttributeType,AttrHistoricalWithOptions>();

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    builder.append(encodeAndPurge());
    return builder.toString();
  }

  /**
   * Process an operation.
   * This method is responsible for detecting and resolving conflict for
   * modifyOperation. This is done by using the historical information.
   *
   * @param modifyOperation the operation to be processed
   * @param modifiedEntry the entry that is being modified (before modification)
   * @return true if the replayed operation was in conflict
   */
  public boolean replayOperation(PreOperationModifyOperation modifyOperation,
                                 Entry modifiedEntry)
  {
    boolean bConflict = false;
    List<Modification> mods = modifyOperation.getModifications();
    ChangeNumber modOpChangeNumber =
      OperationContext.getChangeNumber(modifyOperation);

    for (Iterator<Modification> modsIterator = mods.iterator();
         modsIterator.hasNext(); )
    {
      // Traverse the mods of this MOD operation
      Modification m = modsIterator.next();

      // Read or create the attr historical for the attribute type and option
      // contained in the mod
      AttrHistorical attrHist = getOrCreateAttrHistorical(m);

      if (attrHist.replayOperation(modsIterator, modOpChangeNumber,
                                   modifiedEntry, m))
      {
        bConflict = true;
      }
    }

    return bConflict;
  }

  /**
   * Update the historical information for the provided operation.
   * Steps :
   * - compute the historical attribute
   * - update the mods in the provided operation by adding the update of the
   *   historical attribute
   * - update the modifiedEntry, already computed by core since we are in the
   *   preOperation plugin, that is called just before commiting into the DB.
   *
   * @param modifyOperation the modification.
   */
  public void setHistoricalAttrToOperation(
      PreOperationModifyOperation modifyOperation)
  {
    List<Modification> mods = modifyOperation.getModifications();
    Entry modifiedEntry = modifyOperation.getModifiedEntry();
    ChangeNumber changeNumber =
      OperationContext.getChangeNumber(modifyOperation);

    /*
     * If this is a local operation we need :
     * - first to update the historical information,
     * - then update the entry with the historical information
     * If this is a replicated operation the historical information has
     * already been set in the resolveConflict phase and we only need
     * to update the entry
     */
    if (!modifyOperation.isSynchronizationOperation())
    {
      for (Modification mod : mods)
      {
        // Get the current historical for this attributeType/options
        // (eventually read from the provided modification)
        AttrHistorical attrHist = getOrCreateAttrHistorical(mod);
        if (attrHist != null)
          attrHist.processLocalOrNonConflictModification(changeNumber, mod);
      }
    }

    // Now do the 2 updates required by the core to be consistent:
    //
    // - add the modification of the ds-sync-hist attribute,
    // to the current modifications of the MOD operation
    Attribute attr = encodeAndPurge();
    Modification mod = new Modification(ModificationType.REPLACE, attr);
    mods.add(mod);
    // - update the already modified entry
    modifiedEntry.replaceAttribute(attr);
  }

  /**
     * For a MODDN operation, add new or update existing historical information.
     *
     * This method is NOT static because it relies on this Historical object
     * created in the HandleConflictResolution phase.
     *
     * @param modifyDNOperation the modification for which the historical
     *                          information should be created.
     */
  public void setHistoricalAttrToOperation(
      PreOperationModifyDNOperation modifyDNOperation)
  {
    // Update this historical information with the operation ChangeNumber.
    this.entryMODDNDate = OperationContext.getChangeNumber(modifyDNOperation);

    // Update the operations mods and the modified entry so that the
    // historical information gets stored in the DB and indexed accordingly.
    Entry modifiedEntry = modifyDNOperation.getUpdatedEntry();
    List<Modification> mods = modifyDNOperation.getModifications();

    Attribute attr = encodeAndPurge();

    // Now do the 2 updates required by the core to be consistent:
    //
    // - add the modification of the ds-sync-hist attribute,
    // to the current modifications of the operation
    Modification mod;
    mod = new Modification(ModificationType.REPLACE, attr);
    mods.add(mod);
    // - update the already modified entry
    modifiedEntry.removeAttribute(attr.getAttributeType());
    modifiedEntry.addAttribute(attr, null);
  }

  /**
   * Generate an attribute containing the historical information
   * from the replication context attached to the provided operation
   * and set this attribute in the operation.
   *
   *   For ADD, the historical is made of the changeNumber read from the
   *   synchronization context attached to the operation.
   *
   *   Called for both local and synchronization ADD preOperation.
   *
   *   This historical information will be used to generate fake operation
   *   in case a Directory Server can not find a Replication Server with
   *   all its changes at connection time.
   *   This should only happen if a Directory Server or a Replication Server
   *   crashes.
   *
   *   This method is static because there is no Historical object creation
   *   required here or before(in the HandleConflictResolution phase)
   *
   * @param addOperation The Operation to which the historical attribute will
   *                     be added.
   */
  public static void setHistoricalAttrToOperation(
      PreOperationAddOperation addOperation)
  {
    AttributeType historicalAttrType =
      DirectoryServer.getSchema().getAttributeType(HISTORICAL_ATTRIBUTE_NAME);

    // Get the changeNumber from the attached synchronization context
    // Create the attribute (encoded)
    Attribute attr =
      Attributes.create(historicalAttrType,
          encodeAddHistorical(OperationContext.getChangeNumber(addOperation)));

    // Set the created attribute to the operation
    List<Attribute> attrList = new LinkedList<Attribute>();
    attrList.add(attr);
    addOperation.setAttribute(historicalAttrType, attrList);
  }

  /**
   * Encode in the returned attributeValue, this historical information for
   * an ADD operation.
   * For ADD Operation : "dn:changeNumber:add"
   *
   * @param cn     The date when the ADD Operation happened.
   * @return       The encoded attribute value containing the historical
   *               information for the Operation.
   */
  private static AttributeValue encodeAddHistorical(ChangeNumber cn)
  {
    AttributeType historicalAttrType =
      DirectoryServer.getSchema().getAttributeType(HISTORICAL_ATTRIBUTE_NAME);

    String strValue = "dn:" + cn.toString() +":add";
    AttributeValue val = AttributeValues.create(historicalAttrType, strValue);
    return val;
  }

  /**
   * Encode in the returned attributeValue, the historical information for
   * a MODDN operation.
   * For MODDN Operation : "dn:changeNumber:moddn"
   *
   * @param cn     The date when the MODDN Operation happened.
   * @return       The encoded attribute value containing the historical
   *               information for the Operation.
   */
  private static AttributeValue encodeMODDNHistorical(ChangeNumber cn)
  {
    AttributeType historicalAttrType =
      DirectoryServer.getSchema().getAttributeType(HISTORICAL_ATTRIBUTE_NAME);

    String strValue = "dn:" + cn.toString() +":moddn";
    AttributeValue val = AttributeValues.create(historicalAttrType, strValue);
    return val;
  }

  /**
   * Return an AttributeHistorical corresponding to the attribute type
   * and options contained in the provided mod,
   * The attributeHistorical is :
   * - either read from this EntryHistorical object if one exist,
   * - or created empty.
   * Should never return null.
   *
   * @param  mod the provided mod from which we'll use attributeType
   *             and options to retrieve/create the attribute historical
   * @return the attribute historical retrieved or created empty.
   */
  private AttrHistorical getOrCreateAttrHistorical(Modification mod)
  {

    // Read the provided mod
    Attribute modAttr = mod.getAttribute();
    if (isHistoricalAttribute(modAttr))
    {
      // Don't keep historical information for the attribute that is
      // used to store the historical information.
      return null;
    }
    Set<String> modOptions = modAttr.getOptions();
    AttributeType modAttrType = modAttr.getAttributeType();

    // Read from this entryHistorical,
    // Create one empty if none was existing in this entryHistorical.
    AttrHistoricalWithOptions attrHistWithOptions =
      attributesHistorical.get(modAttrType);
    AttrHistorical attrHist;
    if (attrHistWithOptions != null)
    {
      attrHist = attrHistWithOptions.get(modOptions);
    }
    else
    {
      attrHistWithOptions = new AttrHistoricalWithOptions();
      attributesHistorical.put(modAttrType, attrHistWithOptions);
      attrHist = null;
    }

    if (attrHist == null)
    {
      attrHist = AttrHistorical.createAttributeHistorical(modAttrType);
      attrHistWithOptions.put(modOptions, attrHist);
    }
    return attrHist;
  }

  /**
   * For stats/monitoring purpose, returns the number of historical values
   * purged the last time a purge has been applied on this entry historical.
   *
   * @return the purged values count.
   */
  public int getLastPurgedValuesCount()
  {
    return this.lastPurgedValuesCount;
  }

  /**
   * Encode this historical information object in an operational attribute
   * and purge it from the values older than the purge delay.
   *
   * @return The historical information encoded in an operational attribute.
   */
  public Attribute encodeAndPurge()
  {
    long purgeDate = 0;

    // Set the stats counter to 0 and compute the purgeDate to now minus
    // the potentially set purge delay.
    this.lastPurgedValuesCount = 0;
    if (purgeDelayInMillisec>0)
      purgeDate = TimeThread.getTime() - purgeDelayInMillisec;

    AttributeType historicalAttrType =
      DirectoryServer.getSchema().getAttributeType(HISTORICAL_ATTRIBUTE_NAME);
    AttributeBuilder builder = new AttributeBuilder(historicalAttrType);

    for (Map.Entry<AttributeType, AttrHistoricalWithOptions> entryWithOptions :
          attributesHistorical.entrySet())
    {
      // Encode an attribute type
      AttributeType type = entryWithOptions.getKey();
      HashMap<Set<String> , AttrHistorical> attrwithoptions =
                                entryWithOptions.getValue().getAttributesInfo();

      for (Map.Entry<Set<String>, AttrHistorical> entry :
           attrwithoptions.entrySet())
      {
        // Encode an (attribute type/option)
        boolean delAttr = false;
        Set<String> options = entry.getKey();
        String optionsString = "";
        AttrHistorical attrHist = entry.getValue();


        if (options != null)
        {
          StringBuilder optionsBuilder = new StringBuilder();
          for (String s : options)
          {
            optionsBuilder.append(';');
            optionsBuilder.append(s);
          }
          optionsString = optionsBuilder.toString();
        }

        ChangeNumber deleteTime = attrHist.getDeleteTime();
        /* generate the historical information for deleted attributes */
        if (deleteTime != null)
        {
          delAttr = true;
        }

        for (AttrValueHistorical attrValHist : attrHist.getValuesHistorical()
            .keySet())
        {
          // Encode an attribute value
          String strValue;
          if (attrValHist.getValueDeleteTime() != null)
          {
            // this hist must be purged now, so skip its encoding
            if ((purgeDelayInMillisec>0) &&
                (attrValHist.getValueDeleteTime().getTime()<=purgeDate))
            {
              this.lastPurgedValuesCount++;
              continue;
            }
            strValue = type.getNormalizedPrimaryName() + optionsString + ":" +
            attrValHist.getValueDeleteTime().toString() +
            ":del:" + attrValHist.getAttributeValue().toString();
            AttributeValue val = AttributeValues.create(historicalAttrType,
                                                    strValue);
            builder.add(val);
          }
          else if (attrValHist.getValueUpdateTime() != null)
          {
            if ((purgeDelayInMillisec>0) &&
                (attrValHist.getValueUpdateTime().getTime()<=purgeDate))
            {
              // this hist must be purged now, so skip its encoding
              this.lastPurgedValuesCount++;
              continue;
            }
            if ((delAttr && attrValHist.getValueUpdateTime() == deleteTime)
               && (attrValHist.getAttributeValue() != null))
            {
              strValue = type.getNormalizedPrimaryName() + optionsString + ":" +
              attrValHist.getValueUpdateTime().toString() +  ":repl:" +
              attrValHist.getAttributeValue().toString();
              delAttr = false;
            }
            else
            {
              if (attrValHist.getAttributeValue() == null)
              {
                strValue = type.getNormalizedPrimaryName() + optionsString
                           + ":" + attrValHist.getValueUpdateTime().toString() +
                           ":add";
              }
              else
              {
                strValue = type.getNormalizedPrimaryName() + optionsString
                           + ":" + attrValHist.getValueUpdateTime().toString() +
                           ":add:" + attrValHist.getAttributeValue().toString();
              }
            }

            AttributeValue val = AttributeValues.create(historicalAttrType,
                                                    strValue);
            builder.add(val);
          }
        }

        if (delAttr)
        {
          // Stores the attr deletion hist when not older than the purge delay
          if ((purgeDelayInMillisec>0) &&
              (deleteTime.getTime()<=purgeDate))
          {
            // this hist must be purged now, so skip its encoding
            this.lastPurgedValuesCount++;
            continue;
          }
          String strValue = type.getNormalizedPrimaryName()
              + optionsString + ":" + deleteTime.toString()
              + ":attrDel";
          AttributeValue val =
              AttributeValues.create(historicalAttrType, strValue);
          builder.add(val);
        }
      }
    }

    // Encode the historical information for the ADD Operation.
    if (entryADDDate != null)
    {
      // Stores the ADDDate when not older than the purge delay
      if ((purgeDelayInMillisec>0) &&
          (entryADDDate.getTime()<=purgeDate))
      {
        this.lastPurgedValuesCount++;
      }
      else
      {
        builder.add(encodeAddHistorical(entryADDDate));
      }
    }

    // Encode the historical information for the MODDN Operation.
    if (entryMODDNDate != null)
    {
      // Stores the MODDNDate when not older than the purge delay
      if ((purgeDelayInMillisec>0) &&
          (entryMODDNDate.getTime()<=purgeDate))
      {
        this.lastPurgedValuesCount++;
      }
      else
      {
        builder.add(encodeMODDNHistorical(entryMODDNDate));
      }
    }

    return builder.toAttribute();
  }

  /**
   * Set the delay to purge the historical informations. The purge is applied
   * only when historical attribute is updated (write operations).
   *
   * @param purgeDelay the purge delay in ms
   */
  public void setPurgeDelay(long purgeDelay)
  {
    this.purgeDelayInMillisec = purgeDelay;
  }

  /**
   * Indicates if the Entry was renamed or added after the ChangeNumber
   * that is given as a parameter.
   *
   * @param cn The ChangeNumber with which the ADD or Rename date must be
   *           compared.
   *
   * @return A boolean indicating if the Entry was renamed or added after
   *                   the ChangeNumber that is given as a parameter.
   */
  public boolean addedOrRenamedAfter(ChangeNumber cn)
  {
    if (cn.older(entryADDDate) || cn.older(entryMODDNDate))
    {
      return true;
    }
    else
      return false;
  }


  /**
   * Returns the lastChangeNumber when the entry DN was modified.
   *
   * @return The lastChangeNumber when the entry DN was modified.
   */
  public ChangeNumber getDNDate()
  {
    if (entryADDDate == null)
      return entryMODDNDate;

    if (entryMODDNDate == null)
      return entryADDDate;

    if (entryMODDNDate.older(entryADDDate))
      return entryMODDNDate;
    else
      return entryADDDate;
  }

  /**
   * Construct an Historical object from the provided entry by reading the
   * historical attribute.
   * Return an empty object when the entry does not contain any
   * historical attribute.
   *
   * @param entry The entry which historical information must be loaded
   * @return The constructed Historical information object
   *
   */
  public static EntryHistorical newInstanceFromEntry(Entry entry)
  {
    AttributeType lastAttrType = null;
    Set<String> lastOptions = new HashSet<String>();
    AttrHistorical attrInfo = null;
    AttrHistoricalWithOptions attrInfoWithOptions = null;

    // Read the DB historical attribute from the entry
    List<Attribute> histAttrWithOptionsFromEntry = getHistoricalAttr(entry);

    // Now we'll build the Historical object we want to construct
    EntryHistorical newHistorical = new EntryHistorical();

    if (histAttrWithOptionsFromEntry == null)
    {
      // No historical attribute in the entry, return empty object
      return newHistorical;
    }

    try
    {
      // For each value of the historical attr read (mod. on a user attribute)
      //   build an AttrInfo subobject

      // Traverse the Attributes (when several options for the hist attr)
      // of the historical attribute read from the entry
      for (Attribute histAttrFromEntry : histAttrWithOptionsFromEntry)
      {
        // For each Attribute (option), traverse the values
        for (AttributeValue histAttrValueFromEntry : histAttrFromEntry)
        {
          // From each value of the hist attr, create an object
          HistoricalAttributeValue histVal = new HistoricalAttributeValue(
              histAttrValueFromEntry.getValue().toString());

          AttributeType attrType = histVal.getAttrType();
          Set<String> options = histVal.getOptions();
          ChangeNumber cn = histVal.getCn();
          AttributeValue value = histVal.getAttributeValue();
          HistAttrModificationKey histKey = histVal.getHistKey();

          // update the oldest ChangeNumber stored in the new entry historical
          newHistorical.updateOldestCN(cn);

          if (histVal.isADDOperation())
          {
            newHistorical.entryADDDate = cn;
          }
          else if (histVal.isMODDNOperation())
          {
            newHistorical.entryMODDNDate = cn;
          }
          else
          {
            if (attrType == null)
            {
              /*
               * This attribute is unknown from the schema
               * Just skip it, the modification will be processed but no
               * historical information is going to be kept.
               * Log information for the repair tool.
               */
              Message message = ERR_UNKNOWN_ATTRIBUTE_IN_HISTORICAL.get(
                  entry.getDN().toNormalizedString(), histVal.getAttrString());
              logError(message);
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
              attrInfo = AttrHistorical.createAttributeHistorical(attrType);

              // Create attrInfoWithOptions and store inside the attrInfo
              attrInfoWithOptions = new AttrHistoricalWithOptions();
              attrInfoWithOptions.put(options, attrInfo);

              // Store this attrInfoWithOptions in the newHistorical object
              newHistorical.attributesHistorical.
                put(attrType, attrInfoWithOptions);

              lastAttrType = attrType;
              lastOptions = options;
            }
            else
            {
              if (!options.equals(lastOptions))
              {
                attrInfo = AttrHistorical.createAttributeHistorical(attrType);
                attrInfoWithOptions.put(options, attrInfo);
                lastOptions = options;
              }
            }

            attrInfo.assign(histKey, value, cn);
          }
        }
      }
    } catch (Exception e)
    {
      // Any exception happening here means that the coding of the historical
      // information was wrong.
      // Log an error and continue with an empty historical.
      Message message = ERR_BAD_HISTORICAL.get(entry.getDN().toString());
      logError(message);
    }

    /* set the reference to the historical information in the entry */
    return newHistorical;
  }


  /**
   * Use this historical information to generate fake operations that would
   * result in this historical information.
   * TODO : This is only implemented for MODIFY, MODRDN and  ADD
   *        need to complete with DELETE.
   * @param entry The Entry to use to generate the FakeOperation Iterable.
   *
   * @return an Iterable of FakeOperation that would result in this historical
   *         information.
   */
  public static Iterable<FakeOperation> generateFakeOperations(Entry entry)
  {
    TreeMap<ChangeNumber, FakeOperation> operations =
            new TreeMap<ChangeNumber, FakeOperation>();
    List<Attribute> attrs = getHistoricalAttr(entry);
    if (attrs != null)
    {
      for (Attribute attr : attrs)
      {
        for (AttributeValue val : attr)
        {
          HistoricalAttributeValue histVal =
            new HistoricalAttributeValue(val.getValue().toString());
          if (histVal.isADDOperation())
          {
            // Found some historical information indicating that this
            // entry was just added.
            // Create the corresponding ADD operation.
            operations.put(histVal.getCn(),
                new FakeAddOperation(histVal.getCn(), entry));
          }
          else if (histVal.isMODDNOperation())
          {
            // Found some historical information indicating that this
            // entry was just renamed.
            // Create the corresponding ADD operation.
            operations.put(histVal.getCn(),
                new FakeModdnOperation(histVal.getCn(), entry));
          }
          else
          {
            // Found some historical information for modify operation.
            // Generate the corresponding ModifyOperation or update
            // the already generated Operation if it can be found.
            ChangeNumber cn = histVal.getCn();
            Modification mod = histVal.generateMod();
            FakeModifyOperation modifyFakeOperation;
            FakeOperation fakeOperation = operations.get(cn);

            if ((fakeOperation != null) &&
                      (fakeOperation instanceof FakeModifyOperation))
            {
              modifyFakeOperation = (FakeModifyOperation) fakeOperation;
              modifyFakeOperation.addModification(mod);
            }
            else
            {
              String uuidString = getEntryUUID(entry);
              modifyFakeOperation = new FakeModifyOperation(entry.getDN(), cn,
                  uuidString);
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
   * Get the attribute used to store the historical information from
   * the provided Entry.
   *
   * @param   entry  The entry containing the historical information.
   *
   * @return  The Attribute used to store the historical information.
   *          Several values on the list if several options for this attribute.
   *          Null if not present.
   */
  public static List<Attribute> getHistoricalAttr(Entry entry)
  {
    return entry.getAttribute(HISTORICAL_ATTRIBUTE_NAME);
  }

  /**
   * Get the entry unique Id in String form.
   *
   * @param entry The entry for which the unique id should be returned.
   *
   * @return The Unique Id of the entry, or a fake one if none is found.
   */
  public static String getEntryUUID(Entry entry)
  {
    AttributeType entryuuidAttrType =
      DirectoryServer.getSchema().getAttributeType(ENTRYUUID_ATTRIBUTE_NAME);
    List<Attribute> uuidAttrs =
             entry.getOperationalAttribute(entryuuidAttrType);
    return extractEntryUUID(uuidAttrs, entry.getDN());
  }

  /**
   * Get the Entry Unique Id from an add operation.
   * This must be called after the entry uuid preop plugin (i.e no
   * sooner than the replication provider pre-op)
   *
   * @param op The operation
   * @return The Entry Unique Id String form.
   */
  public static String getEntryUUID(PreOperationAddOperation op)
  {
    Map<AttributeType, List<Attribute>> attrs = op.getOperationalAttributes();
    AttributeType entryuuidAttrType =
      DirectoryServer.getSchema().getAttributeType(ENTRYUUID_ATTRIBUTE_NAME);
    List<Attribute> uuidAttrs = attrs.get(entryuuidAttrType);
    return extractEntryUUID(uuidAttrs, op.getEntryDN());
  }

  /**
   * Check if a given attribute is an attribute used to store historical
   * information.
   *
   * @param   attr The attribute that needs to be checked.
   *
   * @return  a boolean indicating if the given attribute is
   *          used to store historical information.
   */
  public static boolean isHistoricalAttribute(Attribute attr)
  {
    AttributeType attrType = attr.getAttributeType();
    return
      attrType.getNameOrOID().equals(EntryHistorical.HISTORICAL_ATTRIBUTE_NAME);
  }

  /**
   * Potentially update the oldest ChangeNumber stored in this entry historical
   * with the provided ChangeNumber when its older than the current oldest.
   *
   * @param cn the provided ChangeNumber.
   */
  private void updateOldestCN(ChangeNumber cn)
  {
    if (cn != null)
    {
      if (this.oldestChangeNumber == null)
        this.oldestChangeNumber = cn;
      else
        if (cn.older(this.oldestChangeNumber))
            this.oldestChangeNumber = cn;
    }
  }

  /**
   * Returns the oldest ChangeNumber stored in this entry historical attribute.
   *
   * @return the oldest ChangeNumber stored in this entry historical attribute.
   *         Returns null when this historical object has been created from
   *         an entry that has no historical attribute and after the last
   *         historical has been purged.
   */
  public ChangeNumber getOldestCN()
  {
    return this.oldestChangeNumber;
  }



  // Extracts the entryUUID attribute value from the provided list of
  // attributes. If the attribute is not present one is generated from the DN
  // using the same algorithm as the entryUUID virtual attribute provider.
  private static String extractEntryUUID(List<Attribute> entryUUIDAttributes,
      DN entryDN)
  {
    if (entryUUIDAttributes != null)
    {
      Attribute uuid = entryUUIDAttributes.get(0);
      if (!uuid.isEmpty())
      {
        AttributeValue uuidVal = uuid.iterator().next();
        return uuidVal.getValue().toString();
      }
    }

    // Generate a fake entryUUID: see OPENDJ-181. In rare pathological cases
    // an entryUUID attribute may not be present and this causes severe side
    // effects for replication which requires the attribute to always be
    // present.
    if (debugEnabled())
    {
      TRACER.debugWarning(
          "Replication requires an entryUUID attribute in order "
              + "to perform conflict resolution, but none was "
              + "found in entry \"%s\": generating virtual entryUUID instead",
          entryDN);
    }

    String normDNString = entryDN.toNormalizedString();
    return UUID.nameUUIDFromBytes(getBytes(normDNString)).toString();
  }
}

