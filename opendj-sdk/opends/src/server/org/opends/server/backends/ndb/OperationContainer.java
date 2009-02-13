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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.backends.ndb;

import com.mysql.cluster.ndbj.Ndb;
import com.mysql.cluster.ndbj.NdbApiException;
import com.mysql.cluster.ndbj.NdbBlob;
import com.mysql.cluster.ndbj.NdbIndexScanOperation;
import com.mysql.cluster.ndbj.NdbOperation;
import com.mysql.cluster.ndbj.NdbOperation.AbortOption;
import com.mysql.cluster.ndbj.NdbResultSet;
import com.mysql.cluster.ndbj.NdbTransaction;
import com.mysql.cluster.ndbj.NdbTransaction.ExecType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.AttributeValues;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ObjectClassType;

import static org.opends.server.util.ServerConstants.ATTR_REFERRAL_URL;
import static org.opends.server.loggers.debug.DebugLogger.*;

/**
 * This class represents the DN database, which has one record for each entry.
 */
public class OperationContainer extends DatabaseContainer
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The default name of the ordered index for the primary key.
   */
  private static final String PRIMARY_INDEX_NAME = "PRIMARY";

  /**
   * The name of extensible object objectclass.
   */
  private static final String OC_EXTENSIBLEOBJECT = "extensibleObject";

  /**
   * Lowest multivalued attribute id.
   */
  private static final int MIN_MID = 1;

  /**
   * Create a OperationContainer instance for the database in a
   * given entryContainer.
   *
   * @param name The name of the database.
   * @param entryContainer The entryContainer of the database.
   * @throws NdbApiException If an error occurs.
   */
  OperationContainer(String name, EntryContainer entryContainer)
      throws NdbApiException
  {
    super(name, entryContainer);
  }

  /**
   * Insert a new entry into the database.
   * @param txn Abstract transaction to be used for the database operation.
   * @param dn The entry DN.
   * @param id The entry ID.
   * @param entry The entry.
   * @return true if the entry was inserted, false if a entry already exists.
   * @throws NdbApiException If an error occurred while attempting to insert
   * the new entry.
   */
  public boolean insert(AbstractTransaction txn, DN dn, long id, Entry entry)
       throws NdbApiException
  {
    return writeNDBEntry(txn, dn, id, entry, false);
  }

  /**
   * Put an entry to the database.  If an entry already exists, the entry
   * will be replaced, otherwise a new entry will be inserted.
   * @param txn Abstract transaction to be used for the database operation.
   * @param dn The entry DN.
   * @param id The entry ID.
   * @param entry The new entry.
   * @param originalEntry The old entry.
   * @return true if the entry was written, false if it was not written.
   * @throws NdbApiException If an error occurred while attempting to write
   * the entry.
   */
  public boolean put(AbstractTransaction txn, DN dn, long id, Entry entry,
                     Entry originalEntry)
       throws NdbApiException
  {
    // Delete first.
    deleteNDBEntry(txn, originalEntry, id);

    return writeNDBEntry(txn, dn, id, entry, true);
  }

  /**
   * Write an entry to the database.
   * @param txn Abstract transaction to be used for the database operation.
   * @param dn The entry DN.
   * @param id The entry ID.
   * @param entry The entry.
   * @param overwrite Whether or not the entry should be overwritten.
   * @return true if the entry was written, false if it was not written.
   * @throws com.mysql.cluster.ndbj.NdbApiException If an error occurred
   * while attempting to write the entry.
   */
  private boolean writeNDBEntry(AbstractTransaction txn, DN dn,
    long id, Entry entry, boolean overwrite)
    throws NdbApiException
  {
    int nClasses = 0;
    NdbOperation op = null;
    NdbOperation tagOp = null;
    StringBuilder ocBuffer = new StringBuilder();
    StringBuilder xocBuffer = new StringBuilder();
    Map<ObjectClass, String> ocMap = entry.getObjectClasses();
    Map<AttributeType, List<Attribute>> userAttrMap =
      entry.getUserAttributes();
    ArrayList<AttributeType> userAttributes =
      new ArrayList<AttributeType>();

    boolean extensibleObject = false;

    // Update ocs tables.
    NdbTransaction ndbDATxn = null;
    for (Map.Entry<ObjectClass, String> ocEntry : ocMap.entrySet()) {
      ObjectClass oc = ocEntry.getKey();
      String ocName = oc.getNameOrOID();

      Map<Integer, NdbOperation> mvOpMap =
        new HashMap<Integer, NdbOperation>();

      if (nClasses > 0) {
        ocBuffer.append(" ");
      }
      ocBuffer.append(ocName);
      nClasses++;

      if (oc.getObjectClassType() == ObjectClassType.ABSTRACT) {
        continue;
      }

      if (ocName.equalsIgnoreCase(OC_EXTENSIBLEOBJECT)) {
        extensibleObject = true;
      }

      if (ndbDATxn == null) {
        ndbDATxn = txn.getNdbDATransaction(ocName, id);
      }
      if (overwrite) {
        op = ndbDATxn.getWriteOperation(ocName);
      } else {
        op = ndbDATxn.getInsertOperation(ocName);
      }
      op.equalLong(BackendImpl.EID, id);
      op.equalInt(BackendImpl.MID, MIN_MID);
      mvOpMap.put(MIN_MID, op);

      for (AttributeType reqAttr : oc.getRequiredAttributes()) {
        if (userAttributes.contains(reqAttr)) {
          continue;
        }
        if (reqAttr.isOperational()) {
          userAttrMap.put(reqAttr, entry.getOperationalAttribute(reqAttr));
        }
        String attrName = reqAttr.getNameOrOID();
        if (entry.hasAttribute(reqAttr)) {
          boolean indexed = BackendImpl.indexes.contains(attrName);
          List<Attribute> attrList = userAttrMap.get(reqAttr);
          int mid = MIN_MID;
          for (Attribute attr : attrList) {
            if (attr.isVirtual() || attr.isEmpty()) {
              continue;
            }
            // Attribute options.
            Set<String> attrOptionsSet = attr.getOptions();
            if (!attrOptionsSet.isEmpty()) {
              if (overwrite) {
                tagOp =
                  ndbDATxn.getWriteOperation(BackendImpl.TAGS_TABLE);
              } else {
                tagOp =
                  ndbDATxn.getInsertOperation(BackendImpl.TAGS_TABLE);
              }
              tagOp.equalLong(BackendImpl.EID, id);
              tagOp.equalString(BackendImpl.TAG_ATTR, attrName);
              tagOp.equalInt(BackendImpl.MID, mid);
              StringBuilder buffer = new StringBuilder();
              for (String option : attrOptionsSet) {
                buffer.append(';');
                buffer.append(option);
              }
              tagOp.setString(BackendImpl.TAG_TAGS, buffer.toString());
            }
            for (AttributeValue attrVal : attr) {
              String attrStringVal = attrVal.toString();
              NdbOperation attrOp = mvOpMap.get(mid);
              if (attrOp == null) {
                if (overwrite) {
                  attrOp = ndbDATxn.getWriteOperation(ocName);
                } else {
                  attrOp = ndbDATxn.getInsertOperation(ocName);
                }
                attrOp.equalLong(BackendImpl.EID, id);
                attrOp.equalInt(BackendImpl.MID, mid);
                mvOpMap.put(mid, attrOp);
              }
              if (BackendImpl.blobAttributes.contains(attrName)) {
                NdbBlob blob = attrOp.getBlobHandle(attrName);
                blob.setValue(attrVal.getValue().toByteArray());
              } else {
                attrOp.setString(attrName, attrStringVal);
              }
              // Update Indexes.
              if (indexed) {
                NdbOperation idxOp = null;
                if (overwrite) {
                  idxOp = ndbDATxn.getWriteOperation(
                    BackendImpl.IDX_TABLE_PREFIX + attrName);
                } else {
                  idxOp = ndbDATxn.getInsertOperation(
                    BackendImpl.IDX_TABLE_PREFIX + attrName);
                }
                idxOp.equalLong(BackendImpl.EID, id);
                idxOp.equalInt(BackendImpl.MID, mid);
                idxOp.setString(BackendImpl.IDX_VAL, attrStringVal);
              }
              mid++;
            }
          }
          userAttributes.add(reqAttr);
        }
      }

      for (AttributeType optAttr : oc.getOptionalAttributes()) {
        if (userAttributes.contains(optAttr)) {
          continue;
        }
        if (optAttr.isOperational()) {
          userAttrMap.put(optAttr, entry.getOperationalAttribute(optAttr));
        }
        String attrName = optAttr.getNameOrOID();
        if (entry.hasAttribute(optAttr)) {
          boolean indexed = BackendImpl.indexes.contains(attrName);
          List<Attribute> attrList = userAttrMap.get(optAttr);
          int mid = MIN_MID;
          for (Attribute attr : attrList) {
            if (attr.isVirtual() || attr.isEmpty()) {
              continue;
            }
            // Attribute options.
            Set<String> attrOptionsSet = attr.getOptions();
            if (!attrOptionsSet.isEmpty()) {
              if (overwrite) {
                tagOp =
                  ndbDATxn.getWriteOperation(BackendImpl.TAGS_TABLE);
              } else {
                tagOp =
                  ndbDATxn.getInsertOperation(BackendImpl.TAGS_TABLE);
              }
              tagOp.equalLong(BackendImpl.EID, id);
              tagOp.equalString(BackendImpl.TAG_ATTR, attrName);
              tagOp.equalInt(BackendImpl.MID, mid);
              StringBuilder buffer = new StringBuilder();
              for (String option : attrOptionsSet) {
                buffer.append(';');
                buffer.append(option);
              }
              tagOp.setString(BackendImpl.TAG_TAGS, buffer.toString());
            }
            for (AttributeValue attrVal : attr) {
              String attrStringVal = attrVal.toString();
              NdbOperation attrOp = mvOpMap.get(mid);
              if (attrOp == null) {
                if (overwrite) {
                  attrOp = ndbDATxn.getWriteOperation(ocName);
                } else {
                  attrOp = ndbDATxn.getInsertOperation(ocName);
                }
                attrOp.equalLong(BackendImpl.EID, id);
                attrOp.equalInt(BackendImpl.MID, mid);
                mvOpMap.put(mid, attrOp);
              }
              if (BackendImpl.blobAttributes.contains(attrName)) {
                NdbBlob blob = attrOp.getBlobHandle(attrName);
                blob.setValue(attrVal.getValue().toByteArray());
              } else {
                attrOp.setString(attrName, attrStringVal);
              }
              // Update Indexes.
              if (indexed) {
                NdbOperation idxOp = null;
                if (overwrite) {
                  idxOp = ndbDATxn.getWriteOperation(
                    BackendImpl.IDX_TABLE_PREFIX + attrName);
                } else {
                  idxOp = ndbDATxn.getInsertOperation(
                    BackendImpl.IDX_TABLE_PREFIX + attrName);
                }
                idxOp.equalLong(BackendImpl.EID, id);
                idxOp.equalInt(BackendImpl.MID, mid);
                idxOp.setString(BackendImpl.IDX_VAL, attrStringVal);
              }
              mid++;
            }
          }
          userAttributes.add(optAttr);
        }
      }
    }

    // Extensible object.
    if (extensibleObject) {
      int xnClasses = 0;
      for (Map.Entry<AttributeType, List<Attribute>> attrEntry :
           userAttrMap.entrySet())
      {
        AttributeType attrType = attrEntry.getKey();
        if (!userAttributes.contains(attrType)) {
          String attrName = attrType.getNameOrOID();
          String ocName = BackendImpl.attr2Oc.get(attrName);
          Map<Integer, NdbOperation> mvOpMap =
            new HashMap<Integer, NdbOperation>();
          boolean indexed = BackendImpl.indexes.contains(attrName);

          if (ndbDATxn == null) {
            ndbDATxn = txn.getNdbDATransaction(ocName, id);
          }
          if (overwrite) {
            op = ndbDATxn.getWriteOperation(ocName);
          } else {
            op = ndbDATxn.getInsertOperation(ocName);
          }
          op.equalLong(BackendImpl.EID, id);
          op.equalInt(BackendImpl.MID, MIN_MID);
          mvOpMap.put(MIN_MID, op);

          List<Attribute> attrList = userAttrMap.get(attrType);
          int mid = MIN_MID;
          for (Attribute attr : attrList) {
            if (attr.isVirtual() || attr.isEmpty()) {
              continue;
            }
            // Attribute options.
            Set<String> attrOptionsSet = attr.getOptions();
            if (!attrOptionsSet.isEmpty()) {
              if (overwrite) {
                tagOp =
                  ndbDATxn.getWriteOperation(BackendImpl.TAGS_TABLE);
              } else {
                tagOp =
                  ndbDATxn.getInsertOperation(BackendImpl.TAGS_TABLE);
              }
              tagOp.equalLong(BackendImpl.EID, id);
              tagOp.equalString(BackendImpl.TAG_ATTR, attrName);
              tagOp.equalInt(BackendImpl.MID, mid);
              StringBuilder buffer = new StringBuilder();
              for (String option : attrOptionsSet) {
                buffer.append(';');
                buffer.append(option);
              }
              tagOp.setString(BackendImpl.TAG_TAGS, buffer.toString());
            }
            for (AttributeValue attrVal : attr) {
              String attrStringVal = attrVal.toString();
              NdbOperation attrOp = mvOpMap.get(mid);
              if (attrOp == null) {
                if (overwrite) {
                  attrOp = ndbDATxn.getWriteOperation(ocName);
                } else {
                  attrOp = ndbDATxn.getInsertOperation(ocName);
                }
                attrOp.equalLong(BackendImpl.EID, id);
                attrOp.equalInt(BackendImpl.MID, mid);
                mvOpMap.put(mid, attrOp);
              }
              if (BackendImpl.blobAttributes.contains(attrName)) {
                NdbBlob blob = attrOp.getBlobHandle(attrName);
                blob.setValue(attrVal.getValue().toByteArray());
              } else {
                attrOp.setString(attrName, attrStringVal);
              }
              // Update Indexes.
              if (indexed) {
                NdbOperation idxOp = null;
                if (overwrite) {
                  idxOp = ndbDATxn.getWriteOperation(
                    BackendImpl.IDX_TABLE_PREFIX + attrName);
                } else {
                  idxOp = ndbDATxn.getInsertOperation(
                    BackendImpl.IDX_TABLE_PREFIX + attrName);
                }
                idxOp.equalLong(BackendImpl.EID, id);
                idxOp.equalInt(BackendImpl.MID, mid);
                idxOp.setString(BackendImpl.IDX_VAL, attrStringVal);
              }
              mid++;
            }
          }
          userAttributes.add(attrType);

          if (xnClasses > 0) {
            xocBuffer.append(" ");
          }
          xocBuffer.append(ocName);
          xnClasses++;
        }
      }
    }

    // Update operational attributes table.
    if (overwrite) {
      op = ndbDATxn.getWriteOperation(BackendImpl.OPATTRS_TABLE);
    } else {
      op = ndbDATxn.getInsertOperation(BackendImpl.OPATTRS_TABLE);
    }
    op.equalLong(BackendImpl.EID, id);
    for (List<Attribute> attrList :
         entry.getOperationalAttributes().values())
    {
      for (Attribute attr : attrList) {
        if (attr.isVirtual() || attr.isEmpty()) {
          continue;
        }
        if (userAttrMap.containsKey(attr.getAttributeType())) {
          continue;
        }
        String attrName = attr.getAttributeType().getNameOrOID();
        for (AttributeValue attrVal : attr) {
          op.setString(attrName, attrVal.toString());
        }
      }
    }

    // Update dn2id table.
    NdbTransaction ndbTxn = txn.getNdbTransaction();
    if (overwrite) {
      op = ndbTxn.getWriteOperation(name);
    } else {
      op = ndbTxn.getInsertOperation(name);
    }

    int componentIndex = dn.getNumComponents() - 1;
    for (int i=0; i < BackendImpl.DN2ID_DN_NC; i++) {
      while (componentIndex >= 0) {
        op.equalString(BackendImpl.DN2ID_DN + Integer.toString(i),
          dn.getRDN(componentIndex).toNormalizedString());
        componentIndex--;
        i++;
      }
      op.equalString(BackendImpl.DN2ID_DN +
        Integer.toString(i), "");
    }

    op.setLong(BackendImpl.EID, id);

    op.setString(BackendImpl.DN2ID_OC, ocBuffer.toString());

    op.setString(BackendImpl.DN2ID_XOC, xocBuffer.toString());

    return true;
  }

  /**
   * Delete an entry from the database.
   * @param txn Abstract transaction to be used for the database operation.
   * @param originalEntry The original entry.
   * @param id The entry ID.
   * @throws com.mysql.cluster.ndbj.NdbApiException If an error occurred
   * while attempting to write the entry.
   */
  private void deleteNDBEntry(AbstractTransaction txn,
    Entry originalEntry, long id) throws NdbApiException
  {
    NdbOperation op = null;
    NdbOperation tagOp = null;
    NdbTransaction ndbDATxn = null;
    boolean extensibleObject = false;

    // Delete attributes.
    Map<ObjectClass, String> originalOcMap =
      originalEntry.getObjectClasses();
    ArrayList<AttributeType> originalUserAttributes =
      new ArrayList<AttributeType>();
    Map<AttributeType, List<Attribute>> originalUserAttrMap =
      originalEntry.getUserAttributes();

    for (Map.Entry<ObjectClass, String> ocEntry : originalOcMap.entrySet()) {
      ObjectClass oc = ocEntry.getKey();
      String ocName = oc.getNameOrOID();
      Map<Integer, NdbOperation> mvOpMap =
        new HashMap<Integer, NdbOperation>();

      if (oc.getObjectClassType() == ObjectClassType.ABSTRACT) {
        continue;
      }

      if (ocName.equalsIgnoreCase(OC_EXTENSIBLEOBJECT)) {
        extensibleObject = true;
      }

      if (ndbDATxn == null) {
        ndbDATxn = txn.getNdbDATransaction(ocName, id);
      }
      op = ndbDATxn.getDeleteOperation(ocName);
      op.equalLong(BackendImpl.EID, id);
      op.equalInt(BackendImpl.MID, MIN_MID);
      mvOpMap.put(MIN_MID, op);

      for (AttributeType reqAttr : oc.getRequiredAttributes()) {
        String attrName = reqAttr.getNameOrOID();
        if (originalUserAttributes.contains(reqAttr)) {
          continue;
        }
        if (originalEntry.hasUserAttribute(reqAttr)) {
          boolean indexed = BackendImpl.indexes.contains(attrName);
          List<Attribute> attrList = originalUserAttrMap.get(reqAttr);
          int mid = MIN_MID;
          for (Attribute attr : attrList) {
            if (attr.isVirtual() || attr.isEmpty()) {
              continue;
            }
            // Attribute options.
            Set<String> attrOptionsSet = attr.getOptions();
            if (!attrOptionsSet.isEmpty()) {
              tagOp =
                ndbDATxn.getDeleteOperation(BackendImpl.TAGS_TABLE);
              tagOp.equalLong(BackendImpl.EID, id);
              tagOp.equalString(BackendImpl.TAG_ATTR, attrName);
              tagOp.equalInt(BackendImpl.MID, mid);
            }
            for (AttributeValue attrVal : attr) {
              NdbOperation attrOp = mvOpMap.get(mid);
              if (attrOp == null) {
                attrOp = ndbDATxn.getDeleteOperation(ocName);
                attrOp.equalLong(BackendImpl.EID, id);
                attrOp.equalInt(BackendImpl.MID, mid);
                mvOpMap.put(mid, attrOp);
              }
              // Update Indexes.
              if (indexed) {
                NdbOperation idxOp = ndbDATxn.getDeleteOperation(
                  BackendImpl.IDX_TABLE_PREFIX + attrName);
                idxOp.equalLong(BackendImpl.EID, id);
                idxOp.equalInt(BackendImpl.MID, mid);
              }
              mid++;
            }
          }
          originalUserAttributes.add(reqAttr);
        }
      }

      for (AttributeType optAttr : oc.getOptionalAttributes()) {
        String attrName = optAttr.getNameOrOID();
        if (originalUserAttributes.contains(optAttr)) {
          continue;
        }
        if (originalEntry.hasUserAttribute(optAttr)) {
          boolean indexed = BackendImpl.indexes.contains(attrName);
          List<Attribute> attrList = originalUserAttrMap.get(optAttr);
          int mid = MIN_MID;
          for (Attribute attr : attrList) {
            if (attr.isVirtual() || attr.isEmpty()) {
              continue;
            }
            // Attribute options.
            Set<String> attrOptionsSet = attr.getOptions();
            if (!attrOptionsSet.isEmpty()) {
              tagOp =
                ndbDATxn.getDeleteOperation(BackendImpl.TAGS_TABLE);
              tagOp.equalLong(BackendImpl.EID, id);
              tagOp.equalString(BackendImpl.TAG_ATTR, attrName);
              tagOp.equalInt(BackendImpl.MID, mid);
            }
            for (AttributeValue attrVal : attr) {
              NdbOperation attrOp = mvOpMap.get(mid);
              if (attrOp == null) {
                attrOp = ndbDATxn.getDeleteOperation(ocName);
                attrOp.equalLong(BackendImpl.EID, id);
                attrOp.equalInt(BackendImpl.MID, mid);
                mvOpMap.put(mid, attrOp);
              }
              // Update Indexes.
              if (indexed) {
                NdbOperation idxOp = ndbDATxn.getDeleteOperation(
                  BackendImpl.IDX_TABLE_PREFIX + attrName);
                idxOp.equalLong(BackendImpl.EID, id);
                idxOp.equalInt(BackendImpl.MID, mid);
              }
              mid++;
            }
          }
          originalUserAttributes.add(optAttr);
        }
      }
    }

    // Extensible object.
    if (extensibleObject) {
      for (Map.Entry<AttributeType, List<Attribute>> attrEntry :
           originalUserAttrMap.entrySet())
      {
        AttributeType attrType = attrEntry.getKey();
        if (!originalUserAttributes.contains(attrType)) {
          String attrName = attrType.getNameOrOID();
          String ocName = BackendImpl.attr2Oc.get(attrName);
          Map<Integer, NdbOperation> mvOpMap =
            new HashMap<Integer, NdbOperation>();
          boolean indexed = BackendImpl.indexes.contains(attrName);

          if (ndbDATxn == null) {
            ndbDATxn = txn.getNdbDATransaction(ocName, id);
          }
          op = ndbDATxn.getDeleteOperation(ocName);
          op.equalLong(BackendImpl.EID, id);
          op.equalInt(BackendImpl.MID, MIN_MID);
          mvOpMap.put(MIN_MID, op);

          List<Attribute> attrList = originalUserAttrMap.get(attrType);
          int mid = MIN_MID;
          for (Attribute attr : attrList) {
            if (attr.isVirtual() || attr.isEmpty()) {
              continue;
            }
            // Attribute options.
            Set<String> attrOptionsSet = attr.getOptions();
            if (!attrOptionsSet.isEmpty()) {
              tagOp =
                ndbDATxn.getDeleteOperation(BackendImpl.TAGS_TABLE);
              tagOp.equalLong(BackendImpl.EID, id);
              tagOp.equalString(BackendImpl.TAG_ATTR, attrName);
              tagOp.equalInt(BackendImpl.MID, mid);
            }
            for (AttributeValue attrVal : attr) {
              NdbOperation attrOp = mvOpMap.get(mid);
              if (attrOp == null) {
                attrOp = ndbDATxn.getDeleteOperation(ocName);
                attrOp.equalLong(BackendImpl.EID, id);
                attrOp.equalInt(BackendImpl.MID, mid);
                mvOpMap.put(mid, attrOp);
              }
              // Update Indexes.
              if (indexed) {
                NdbOperation idxOp = ndbDATxn.getDeleteOperation(
                  BackendImpl.IDX_TABLE_PREFIX + attrName);
                idxOp.equalLong(BackendImpl.EID, id);
                idxOp.equalInt(BackendImpl.MID, mid);
              }
              mid++;
            }
          }
          originalUserAttributes.add(attrType);
        }
      }
    }
  }

  /**
   * Remove an entry from the database.
   * @param txn Abstract transaction to be used for the database operation.
   * @param entry The entry.
   * @return true if the entry was removed, false if it was not removed.
   * @throws NdbApiException If an error occurred while attempting to remove
   * the entry.
   */
  public boolean remove(AbstractTransaction txn, Entry entry)
       throws NdbApiException
  {
    DN dn = entry.getDN();

    NdbResultSet rs = null;

    NdbTransaction ndbTxn = txn.getNdbTransaction();

    NdbOperation op = ndbTxn.getSelectOperation(name,
      NdbOperation.LockMode.LM_CommittedRead);

    boolean extensibleObject = false;

    int componentIndex = dn.getNumComponents() - 1;
    for (int i=0; i < BackendImpl.DN2ID_DN_NC; i++) {
      while (componentIndex >= 0) {
        op.equalString(BackendImpl.DN2ID_DN + Integer.toString(i),
          dn.getRDN(componentIndex).toNormalizedString());
        componentIndex--;
        i++;
      }
      op.equalString(BackendImpl.DN2ID_DN +
        Integer.toString(i), "");
    }
    op.getValue(BackendImpl.EID);
    op.getValue(BackendImpl.DN2ID_OC);
    op.getValue(BackendImpl.DN2ID_XOC);

    rs = op.resultData();
    ndbTxn.execute(ExecType.NoCommit, AbortOption.AO_IgnoreError, true);

    long eid = 0;
    NdbTransaction ndbDATxn = null;
    String[] ocsStringArray = null;
    String[] xocsStringArray = null;
    List<NdbResultSet> ocRsList = new ArrayList<NdbResultSet>();
    NdbIndexScanOperation indexScanOp = null;

    if (rs.next()) {
      eid = rs.getLong(BackendImpl.EID);
      String ocsString = rs.getString(BackendImpl.DN2ID_OC);
      ocsStringArray = ocsString.split(" ");

      String xocsString = rs.getString(BackendImpl.DN2ID_XOC);
      xocsStringArray = xocsString.split(" ");
      if (xocsString.length() > 0) {
        extensibleObject = true;
      }

      for (String ocName : ocsStringArray) {
        ObjectClass oc =
          DirectoryServer.getObjectClass(ocName, true);
        if (oc.getObjectClassType() == ObjectClassType.ABSTRACT) {
          continue;
        }
        if (ndbDATxn == null) {
          ndbDATxn = txn.getNdbDATransaction(ocName, eid);
        }
        indexScanOp =
          ndbDATxn.getSelectIndexScanOperation(PRIMARY_INDEX_NAME, ocName);
        indexScanOp.setBoundLong(BackendImpl.EID,
            NdbIndexScanOperation.BoundType.BoundEQ, eid);
        indexScanOp.getValue(BackendImpl.MID);
        ocRsList.add(indexScanOp.resultData());
      }

      // Extensible object.
      if (extensibleObject) {
        for (String xocName : xocsStringArray) {
          ObjectClass xoc =
            DirectoryServer.getObjectClass(xocName, true);
          if (xoc.getObjectClassType() == ObjectClassType.ABSTRACT) {
            continue;
          }
          if (ndbDATxn == null) {
            ndbDATxn = txn.getNdbDATransaction(xocName, eid);
          }
          indexScanOp =
            ndbDATxn.getSelectIndexScanOperation(PRIMARY_INDEX_NAME, xocName);
          indexScanOp.setBoundLong(BackendImpl.EID,
            NdbIndexScanOperation.BoundType.BoundEQ, eid);
          indexScanOp.getValue(BackendImpl.MID);
          ocRsList.add(indexScanOp.resultData());
        }
      }
    }

    // Attribute options.
    if (ndbDATxn == null) {
      ndbDATxn = txn.getNdbDATransaction(BackendImpl.TAGS_TABLE, eid);
    }
    indexScanOp = ndbDATxn.getSelectIndexScanOperation(PRIMARY_INDEX_NAME,
      BackendImpl.TAGS_TABLE);
    indexScanOp.setBoundLong(BackendImpl.EID,
      NdbIndexScanOperation.BoundType.BoundEQ, eid);
    indexScanOp.getValue(BackendImpl.TAG_ATTR);
    indexScanOp.getValue(BackendImpl.MID);
    NdbResultSet tagRs = indexScanOp.resultData();

    ndbDATxn.execute(ExecType.NoCommit, AbortOption.AO_IgnoreError, true);

    Iterator<NdbResultSet> rsIterator = ocRsList.iterator();
    for (String ocName : ocsStringArray) {
      ObjectClass oc =
        DirectoryServer.getObjectClass(ocName, true);
      if (oc.getObjectClassType() == ObjectClassType.ABSTRACT) {
        continue;
      }
      NdbResultSet ocRs = rsIterator.next();
      while (ocRs.next()) {
        int mid = ocRs.getInt(BackendImpl.MID);
        op = ndbDATxn.getDeleteOperation(ocName);
        op.equalLong(BackendImpl.EID, eid);
        op.equalInt(BackendImpl.MID, mid);
      }
    }

    // Extensible object.
    if (extensibleObject) {
      for (String xocName : xocsStringArray) {
        ObjectClass xoc =
          DirectoryServer.getObjectClass(xocName, true);
        if (xoc.getObjectClassType() == ObjectClassType.ABSTRACT) {
          continue;
        }
        NdbResultSet ocRs = rsIterator.next();
        while (ocRs.next()) {
          int mid = ocRs.getInt(BackendImpl.MID);
          op = ndbDATxn.getDeleteOperation(xocName);
          op.equalLong(BackendImpl.EID, eid);
          op.equalInt(BackendImpl.MID, mid);
        }
      }
    }

    // Operational attributes.
    op = ndbDATxn.getDeleteOperation(BackendImpl.OPATTRS_TABLE);
    op.equalLong(BackendImpl.EID, eid);

    // Attribute options.
    while (tagRs.next()) {
      String attrName = tagRs.getString(BackendImpl.TAG_ATTR);
      int mid = tagRs.getInt(BackendImpl.MID);
      op = ndbDATxn.getDeleteOperation(BackendImpl.TAGS_TABLE);
      op.equalLong(BackendImpl.EID, eid);
      op.equalString(BackendImpl.TAG_ATTR, attrName);
      op.equalInt(BackendImpl.MID, mid);
    }

    // Indexes.
    for (String attrName : BackendImpl.indexes) {
      AttributeType attributeType =
              DirectoryServer.getAttributeType(
              attrName.toLowerCase(), true);
      if (entry.hasAttribute(attributeType)) {
        List<Attribute> attrList =
          entry.getAttribute(attributeType);
        int mid = MIN_MID;
        for (Attribute attr : attrList) {
          for (AttributeValue attrVal : attr) {
            NdbOperation idxOp = ndbDATxn.getDeleteOperation(
              BackendImpl.IDX_TABLE_PREFIX + attrName);
            idxOp.equalLong(BackendImpl.EID, eid);
            idxOp.equalInt(BackendImpl.MID, mid);
            mid++;
          }
        }
      }
    }

    // dn2id.
    op = ndbTxn.getDeleteOperation(name);
    componentIndex = dn.getNumComponents() - 1;
    for (int i=0; i < BackendImpl.DN2ID_DN_NC; i++) {
      while (componentIndex >= 0) {
        op.equalString(BackendImpl.DN2ID_DN + Integer.toString(i),
          dn.getRDN(componentIndex).toNormalizedString());
        componentIndex--;
        i++;
      }
      op.equalString(BackendImpl.DN2ID_DN +
        Integer.toString(i), "");
    }

    return true;
  }

  /**
   * Fetch the entry for a given ID.
   * @param txn Abstract transaction to be used for the database read.
   * @param eid The ID for which the entry is desired.
   * @param lockMode NDB locking mode for this operation.
   * @return The entry, or null if the given ID is not in the database.
   * @throws NdbApiException If an error occurs in the database.
   * @throws DirectoryException If a problem occurs while trying to
   *         retrieve the entry.
   */
  public Entry get(AbstractTransaction txn, long eid,
    NdbOperation.LockMode lockMode)
       throws NdbApiException, DirectoryException
  {
    NdbIndexScanOperation indexScanOp = null;
    NdbResultSet rs = null;
    DN dn = null;

    NdbTransaction ndbTxn = txn.getNdbTransaction();

    indexScanOp = ndbTxn.getSelectIndexScanOperation(
      BackendImpl.EID, name, lockMode);
    indexScanOp.setBoundLong(BackendImpl.EID,
            NdbIndexScanOperation.BoundType.BoundEQ, eid);
    for (int i = 0; i < BackendImpl.DN2ID_DN_NC; i++) {
      indexScanOp.getValue(BackendImpl.DN2ID_DN +
        Integer.toString(i));
    }
    indexScanOp.getValue(BackendImpl.DN2ID_OC);
    indexScanOp.getValue(BackendImpl.DN2ID_XOC);

    rs = indexScanOp.resultData();
    ndbTxn.execute(ExecType.NoCommit, AbortOption.AO_IgnoreError, true);

    if (rs.next()) {
      StringBuilder dnBuffer = new StringBuilder();
      int dnColumnIndex = BackendImpl.DN2ID_DN_NC - 1;
      while (dnColumnIndex >= 0) {
        String rdnString = rs.getString(BackendImpl.DN2ID_DN +
          Integer.toString(dnColumnIndex));
        if (rdnString.length() > 0) {
          dnBuffer.append(rdnString);
          if (dnColumnIndex > 0) {
            dnBuffer.append(",");
          }
        }
        dnColumnIndex--;
      }
      String dnString = dnBuffer.toString();
      if (dnString.length() == 0) {
        return null;
      }
      dn = DN.decode(dnString);
      return getNDBEntry(txn, rs, dn, eid);
    } else {
      return null;
    }
  }


  /**
   * Fetch the entry for a given DN.
   * @param txn Abstract transaction to be used for the database read.
   * @param dn The DN for which the entry is desired.
   * @param lockMode NDB locking mode for this operation.
   * @return The entry, or null if the given DN is not in the database.
   * @throws NdbApiException If an error occurs in the database.
   */
  public Entry get(AbstractTransaction txn, DN dn,
    NdbOperation.LockMode lockMode) throws NdbApiException
  {
    NdbOperation op = null;
    NdbResultSet rs = null;

    NdbTransaction ndbTxn = txn.getNdbTransaction();

    op = ndbTxn.getSelectOperation(name, lockMode);

    int componentIndex = dn.getNumComponents() - 1;
    for (int i=0; i < BackendImpl.DN2ID_DN_NC; i++) {
      while (componentIndex >= 0) {
        op.equalString(BackendImpl.DN2ID_DN + Integer.toString(i),
          dn.getRDN(componentIndex).toNormalizedString());
        componentIndex--;
        i++;
      }
      op.equalString(BackendImpl.DN2ID_DN +
        Integer.toString(i), "");
    }
    op.getValue(BackendImpl.EID);
    op.getValue(BackendImpl.DN2ID_OC);
    op.getValue(BackendImpl.DN2ID_XOC);

    rs = op.resultData();
    ndbTxn.execute(ExecType.NoCommit, AbortOption.AO_IgnoreError, true);

    if (rs.next()) {
      long eid = rs.getLong(BackendImpl.EID);
      if (eid == 0) {
        return null;
      }
      Entry entry = getNDBEntry(txn, rs, dn, eid);
      if (entry != null) {
        entry.setAttachment(eid);
      }
      return entry;
    } else {
      return null;
    }
  }

  /**
   * Get the entry from the database.
   * @param txn Abstract transaction to be used for the database read.
   * @param rs NDB results set from the initial get entry operation.
   * @param dn The entry DN.
   * @param id The entry ID.
   * @return The entry.
   * @throws NdbApiException If an error occurs in the database.
   */
  private Entry getNDBEntry(
    AbstractTransaction txn,
    NdbResultSet rs,
    DN dn,
    long eid) throws NdbApiException
  {
    NdbOperation op = null;
    NdbIndexScanOperation indexScanOp = null;
    boolean extensibleObject = false;

    String ocsString = rs.getString(BackendImpl.DN2ID_OC);
    String[] ocsStringArray = ocsString.split(" ");

    String xocsString = rs.getString(BackendImpl.DN2ID_XOC);
    String[] xocsStringArray = xocsString.split(" ");
    if (xocsString.length() > 0) {
      extensibleObject = true;
    }
    LinkedHashMap<ObjectClass, String> xObjectClasses =
      new LinkedHashMap<ObjectClass, String>();

    List<NdbResultSet> ocRsList = new ArrayList<NdbResultSet>();
    Map<String, NdbBlob> blobMap = new HashMap<String, NdbBlob>();
    LinkedHashMap<ObjectClass, String> objectClasses =
      new LinkedHashMap<ObjectClass, String>(ocsStringArray.length);

    NdbTransaction ndbDATxn = null;
    NdbIndexScanOperation tagScanOp = null;

    for (String ocName : ocsStringArray) {
      ObjectClass oc =
        DirectoryServer.getObjectClass(ocName, true);
      objectClasses.put(oc, ocName);
      if (oc.getObjectClassType() == ObjectClassType.ABSTRACT) {
        continue;
      }

      if (ndbDATxn == null) {
        ndbDATxn = txn.getNdbDATransaction(ocName, eid);
      }

      indexScanOp =
        ndbDATxn.getSelectIndexScanOperation(
        PRIMARY_INDEX_NAME, ocName,
        NdbOperation.LockMode.LM_CommittedRead);
      indexScanOp.setBoundLong(BackendImpl.EID,
        NdbIndexScanOperation.BoundType.BoundEQ, eid);
      indexScanOp.getValue(BackendImpl.MID);

      for (AttributeType reqAttr : oc.getRequiredAttributes()) {
        String attrName = reqAttr.getNameOrOID();
        if (BackendImpl.blobAttributes.contains(attrName)) {
          NdbBlob blob = indexScanOp.getBlobHandle(attrName);
          blobMap.put(attrName, blob);
        } else {
          indexScanOp.getValue(attrName);
        }
      }
      for (AttributeType optAttr : oc.getOptionalAttributes()) {
        String attrName = optAttr.getNameOrOID();
        if (BackendImpl.blobAttributes.contains(attrName)) {
          NdbBlob blob = indexScanOp.getBlobHandle(attrName);
          blobMap.put(attrName, blob);
        } else {
          indexScanOp.getValue(attrName);
        }
      }
      ocRsList.add(indexScanOp.resultData());
    }

    // Extensible object.
    if (extensibleObject) {
      for (String xocName : xocsStringArray) {
        ObjectClass xoc =
          DirectoryServer.getObjectClass(xocName, true);
        objectClasses.put(xoc, xocName);
        xObjectClasses.put(xoc, xocName);
        if (xoc.getObjectClassType() == ObjectClassType.ABSTRACT) {
          continue;
        }

        if (ndbDATxn == null) {
          ndbDATxn = txn.getNdbDATransaction(xocName, eid);
        }

        indexScanOp =
          ndbDATxn.getSelectIndexScanOperation(
          PRIMARY_INDEX_NAME, xocName,
          NdbOperation.LockMode.LM_CommittedRead);
        indexScanOp.setBoundLong(BackendImpl.EID,
          NdbIndexScanOperation.BoundType.BoundEQ, eid);
        indexScanOp.getValue(BackendImpl.MID);

        for (AttributeType reqAttr : xoc.getRequiredAttributes()) {
          String attrName = reqAttr.getNameOrOID();
          if (BackendImpl.blobAttributes.contains(attrName)) {
            NdbBlob blob = indexScanOp.getBlobHandle(attrName);
            blobMap.put(attrName, blob);
          } else {
            indexScanOp.getValue(attrName);
          }
        }
        for (AttributeType optAttr : xoc.getOptionalAttributes()) {
          String attrName = optAttr.getNameOrOID();
          if (BackendImpl.blobAttributes.contains(attrName)) {
            NdbBlob blob = indexScanOp.getBlobHandle(attrName);
            blobMap.put(attrName, blob);
          } else {
            indexScanOp.getValue(attrName);
          }
        }
        ocRsList.add(indexScanOp.resultData());
      }
    }

    // Operational attributes.
    op = ndbDATxn.getSelectOperation(BackendImpl.OPATTRS_TABLE,
      NdbOperation.LockMode.LM_CommittedRead);
    op.equalLong(BackendImpl.EID, eid);

    for (String attrName : BackendImpl.operationalAttributes) {
      op.getValue(attrName);
    }
    ocRsList.add(op.resultData());

    // Attribute options.
    tagScanOp = ndbDATxn.getSelectIndexScanOperation(
      PRIMARY_INDEX_NAME,
      BackendImpl.TAGS_TABLE,
      NdbOperation.LockMode.LM_CommittedRead);
    tagScanOp.setBoundLong(BackendImpl.EID,
      NdbIndexScanOperation.BoundType.BoundEQ, eid);
    tagScanOp.getValue(BackendImpl.TAG_ATTR);
    tagScanOp.getValue(BackendImpl.MID);
    tagScanOp.getValue(BackendImpl.TAG_TAGS);
    NdbResultSet tagRs = tagScanOp.resultData();

    ndbDATxn.execute(ExecType.NoCommit, AbortOption.AO_IgnoreError, true);

    return decodeNDBEntry(dn, ocRsList, tagRs, objectClasses,
      xObjectClasses, blobMap, extensibleObject);
  }

  /**
   * Decode the entry from NDB results.
   * @param dn The entry DN.
   * @param ocRsList ObjectClass result sets list.
   * @param tagRs Attribute tags result set.
   * @param objectClasses ObjectClasses map.
   * @param xObjectClasses Extensible ObjectClasses map.
   * @param blobMap Blob attributes map.
   * @param extensibleObject true if the entry is Extensible Object,
   * false otherwise.
   * @return The entry.
   * @throws com.mysql.cluster.ndbj.NdbApiException
   */
  private Entry decodeNDBEntry(
    DN dn,
    List<NdbResultSet> ocRsList,
    NdbResultSet tagRs,
    Map<ObjectClass, String> objectClasses,
    Map<ObjectClass, String> xObjectClasses,
    Map<String, NdbBlob> blobMap,
    boolean extensibleObject) throws NdbApiException
  {
    LinkedHashMap<AttributeType, List<Attribute>> userAttributes =
      new LinkedHashMap<AttributeType, List<Attribute>>();
    LinkedHashMap<AttributeType, List<Attribute>> opAttributes =
      new LinkedHashMap<AttributeType, List<Attribute>>();

    // Attribute options.
    Map<String, Map<Integer, LinkedHashSet<String>>> attr2tagMap =
      new HashMap<String, Map<Integer, LinkedHashSet<String>>>();
    while (tagRs.next()) {
      String attrName = tagRs.getString(BackendImpl.TAG_ATTR);
      int mid = tagRs.getInt(BackendImpl.MID);
      String attrOptions = tagRs.getString(BackendImpl.TAG_TAGS);
      if (!tagRs.wasNull()) {
        int currentIndex = attrOptions.indexOf(';');
        int nextIndex = attrOptions.indexOf(';', currentIndex + 1);
        String option = null;
        Map<Integer, LinkedHashSet<String>> mid2tagMap =
          attr2tagMap.get(attrName);
        if (mid2tagMap == null) {
          mid2tagMap = new HashMap<Integer, LinkedHashSet<String>>();
        }
        LinkedHashSet<String> options = new LinkedHashSet<String>();
        while (nextIndex > 0) {
          option =
            attrOptions.substring(currentIndex + 1, nextIndex);
          if (option.length() > 0) {
            options.add(option);
          }
          currentIndex = nextIndex;
          nextIndex = attrOptions.indexOf(';', currentIndex + 1);
        }
        option = attrOptions.substring(currentIndex + 1);
        if (option.length() > 0) {
          options.add(option);
        }
        mid2tagMap.put(mid, options);
        attr2tagMap.put(attrName, mid2tagMap);
      }
    }

    // Object classes and user atributes.
    Iterator<NdbResultSet> ocRsIterator = ocRsList.iterator();
    NdbResultSet ocRs = ocRsIterator.next();
    AttributeBuilder attrBuilder = new AttributeBuilder();
    for (ObjectClass oc : objectClasses.keySet()) {
      if (oc.getObjectClassType() == ObjectClassType.ABSTRACT) {
        continue;
      }
      while (ocRs.next()) {
        int mid = ocRs.getInt(BackendImpl.MID);
        for (AttributeType reqAttr : oc.getRequiredAttributes()) {
          String attrName = reqAttr.getNameOrOID();
          byte[] attrValBytes = null;
          NdbBlob blob = null;
          if (BackendImpl.blobAttributes.contains(attrName)) {
            blob = blobMap.get(attrName);
          } else {
            attrValBytes = ocRs.getStringBytes(attrName);
            if (ocRs.wasNull()) {
              continue;
            }
          }
          AttributeType attributeType =
            DirectoryServer.getAttributeType(
            BackendImpl.attrName2LC.get(attrName), true);
          List<Attribute> attrList = userAttributes.get(attributeType);
          if (attrList == null) {
            attrList = new ArrayList<Attribute>();
          }
          Attribute attr = null;
          LinkedHashSet<String> options = null;
          Map<Integer, LinkedHashSet<String>> mid2tagMap =
            attr2tagMap.get(attrName);
          if (mid2tagMap != null) {
            options = mid2tagMap.get(mid);
          }
          if ((options == null) && !attrList.isEmpty()) {
            attr = attrList.get(attrList.size() - 1);
          }
          if (attr == null) {
            attrBuilder.setAttributeType(attributeType, attrName);
          } else {
            attrBuilder = new AttributeBuilder(attr);
          }
          if (blob != null) {
            if (blob.getNull()) {
              continue;
            }
            int len = blob.getLength().intValue();
            byte[] buf = new byte[len];
            blob.readData(buf, len);
            attrBuilder.add(AttributeValues.create(attributeType,
              ByteString.wrap(buf)));
          } else {
            attrBuilder.add(AttributeValues.create(attributeType,
              ByteString.wrap(attrValBytes)));
          }

          // Create or update an attribute.
          if (options != null) {
            attrBuilder.setOptions(options);
          }
          attr = attrBuilder.toAttribute();
          if (attrList.isEmpty()) {
            attrList.add(attr);
          } else {
            attrList.set(attrList.size() - 1, attr);
          }

          userAttributes.put(attributeType, attrList);
        }
        for (AttributeType optAttr : oc.getOptionalAttributes()) {
          String attrName = optAttr.getNameOrOID();
          byte[] attrValBytes = null;
          NdbBlob blob = null;
          if (BackendImpl.blobAttributes.contains(attrName)) {
            blob = blobMap.get(attrName);
          } else {
            attrValBytes = ocRs.getStringBytes(attrName);
            if (ocRs.wasNull()) {
              continue;
            }
          }
          AttributeType attributeType =
            DirectoryServer.getAttributeType(
            BackendImpl.attrName2LC.get(attrName), true);
          List<Attribute> attrList = userAttributes.get(attributeType);
          if (attrList == null) {
            attrList = new ArrayList<Attribute>();
          }
          Attribute attr = null;
          LinkedHashSet<String> options = null;
          Map<Integer, LinkedHashSet<String>> mid2tagMap =
            attr2tagMap.get(attrName);
          if (mid2tagMap != null) {
            options = mid2tagMap.get(mid);
          }
          if ((options == null) && !attrList.isEmpty()) {
            attr = attrList.get(attrList.size() - 1);
          }
          if (attr == null) {
            attrBuilder.setAttributeType(attributeType, attrName);
          } else {
            attrBuilder = new AttributeBuilder(attr);
          }
          if (blob != null) {
            if (blob.getNull()) {
              continue;
            }
            int len = blob.getLength().intValue();
            byte[] buf = new byte[len];
            blob.readData(buf, len);
            attrBuilder.add(AttributeValues.create(attributeType,
              ByteString.wrap(buf)));
          } else {
            attrBuilder.add(AttributeValues.create(attributeType,
              ByteString.wrap(attrValBytes)));
          }

          // Create or update an attribute.
          if (options != null) {
            attrBuilder.setOptions(options);
          }
          attr = attrBuilder.toAttribute();
          if (attrList.isEmpty()) {
            attrList.add(attr);
          } else {
            attrList.set(attrList.size() - 1, attr);
          }

          userAttributes.put(attributeType, attrList);
        }
      }
      if (ocRsIterator.hasNext()) {
        ocRs = ocRsIterator.next();
      }
    }

    // Operational attributes.
    if (ocRs.next()) {
      for (String attrName : BackendImpl.operationalAttributes) {
        byte[] attrValBytes = ocRs.getStringBytes(attrName);
        if (ocRs.wasNull()) {
          continue;
        }
        AttributeType attributeType =
          DirectoryServer.getAttributeType(
          BackendImpl.attrName2LC.get(attrName), true);
        attrBuilder.setAttributeType(attributeType, attrName);
        attrBuilder.add(AttributeValues.create(attributeType,
          ByteString.wrap(attrValBytes)));
        Attribute attr = attrBuilder.toAttribute();
        List<Attribute> attrList = opAttributes.get(attributeType);
        if (attrList == null) {
          attrList = new ArrayList<Attribute>();
          attrList.add(attr);
          opAttributes.put(attributeType, attrList);
        } else {
          attrList.add(attr);
        }
      }
    }

    // Extensible object.
    if (extensibleObject) {
      for (ObjectClass oc : xObjectClasses.keySet()) {
        objectClasses.remove(oc);
      }
    }

    Entry entry = new Entry(dn, objectClasses, userAttributes, opAttributes);
    if (entry != null) {
      entry.processVirtualAttributes();
    }
    return entry;
  }

  /**
   * Fetch the entry ID for a given DN.
   * @param txn Abstract transaction to be used for the database read.
   * @param dn The DN for which the entry ID is desired.
   * @param lockMode The lock mode for this operation.
   * @return The entry ID, or zero if the given DN is not in the database.
   * @throws NdbApiException If an error occurs in the database.
   */
  public long getID(AbstractTransaction txn, DN dn,
    NdbOperation.LockMode lockMode)
       throws NdbApiException
  {
    NdbOperation op = null;
    NdbResultSet rs = null;
    long eid = 0;

    NdbTransaction ndbTxn = txn.getNdbTransaction();

    op = ndbTxn.getSelectOperation(name, lockMode);

    int componentIndex = dn.getNumComponents() - 1;
    for (int i=0; i < BackendImpl.DN2ID_DN_NC; i++) {
      while (componentIndex >= 0) {
        op.equalString(BackendImpl.DN2ID_DN + Integer.toString(i),
          dn.getRDN(componentIndex).toNormalizedString());
        componentIndex--;
        i++;
      }
      op.equalString(BackendImpl.DN2ID_DN +
        Integer.toString(i), "");
    }

    op.getValue(BackendImpl.EID);

    rs = op.resultData();
    ndbTxn.execute(ExecType.NoCommit, AbortOption.AO_IgnoreError, true);

    if (rs.next()) {
      eid = rs.getLong(BackendImpl.EID);
    }

    return eid;
  }

  /**
   * Get referrals for a given entry ID.
   * @param txn Abstract transaction to be used for the operation.
   * @param id The ID for which the referral is desired.
   * @return The referral set, or empty set if the entry has no referrals.
   * @throws NdbApiException If an error occurs in the database.
   */
  public Set<String> getReferrals(AbstractTransaction txn, long id)
       throws NdbApiException
  {
    NdbIndexScanOperation op = null;
    NdbResultSet rs = null;
    Set<String> referrals = new HashSet<String>();

    NdbTransaction ndbDATxn =
      txn.getNdbDATransaction(BackendImpl.REFERRALS_TABLE, id);

    op = ndbDATxn.getSelectIndexScanOperation(
      PRIMARY_INDEX_NAME, BackendImpl.REFERRALS_TABLE,
      NdbOperation.LockMode.LM_CommittedRead);
    op.setBoundLong(BackendImpl.EID,
            NdbIndexScanOperation.BoundType.BoundEQ, id);

    op.getValue(ATTR_REFERRAL_URL);
    rs = op.resultData();

    ndbDATxn.execute(ExecType.NoCommit, AbortOption.AO_IgnoreError, true);

    while (rs.next()) {
      String referral = rs.getString(ATTR_REFERRAL_URL);
      if (rs.wasNull() || (referral.length() == 0)) {
        break;
      }
      referrals.add(referral);
    }

    return referrals;
  }

  /**
   * Get the count of rows in the database.
   * @return The number of rows in the database.
   * @throws NdbApiException If an error occurs in the database.
   */
  public long getRecordCount() throws NdbApiException
  {
    Ndb ndb = entryContainer.getRootContainer().getNDB();

    try {
      return ndb.selectCount(name);
    } finally {
      if (ndb != null) {
        entryContainer.getRootContainer().releaseNDB(ndb);
      }
    }
  }

  /**
   * Determine if the entry has subordinate entries.
   * @param txn Abstract transaction to be used for the operation.
   * @param dn The entry DN.
   * @return true if the entry has subordinates, false otherwise.
   * @throws com.mysql.cluster.ndbj.NdbApiException If an error
   * occurs in the database.
   */
  public boolean hasSubordinates(AbstractTransaction txn, DN dn)
    throws NdbApiException
  {
    // NdbInterpretedOperation op;
    NdbIndexScanOperation op;
    NdbResultSet rs;

    NdbTransaction ndbTxn = txn.getNdbTransaction();

    op = ndbTxn.getSelectIndexScanOperation(
      PRIMARY_INDEX_NAME, name,
      NdbOperation.LockMode.LM_Read);

    int numComponents = dn.getNumComponents();
    int componentIndex = numComponents - 1;
    for (int i=0; i < numComponents; i++) {
      op.setBoundString(BackendImpl.DN2ID_DN +
        Integer.toString(i),
        NdbIndexScanOperation.BoundType.BoundEQ,
        dn.getRDN(componentIndex).toNormalizedString());
      componentIndex--;
    }

    if (dn.getNumComponents() < BackendImpl.DN2ID_DN_NC) {
      String nextRDNColumn =
        BackendImpl.DN2ID_DN + Integer.toString(numComponents);
      op.setBoundString(nextRDNColumn,
        NdbIndexScanOperation.BoundType.BoundLT, "");
    }

    // FIXME: This is extremely inefficient, need NDB/J API
    // like interpretExitLastRow to count result rows node-
    // side without returning them here for check/count.
    // op.interpretExitLastRow();
    op.getValue(BackendImpl.EID);

    rs = op.resultData();
    ndbTxn.execute(ExecType.NoCommit, AbortOption.AO_IgnoreError, true);

    if (rs.next()) {
      return true;
    }

    return false;
  }

  /**
   * Get a new instance of the Search Cursor object.
   * @param txn Abstract Transaction to be used for the operation.
   * @param baseDN Search Cursor base DN.
   * @return New instance of the Search Cursor object.
   */
  public DN2IDSearchCursor getSearchCursor(
    AbstractTransaction txn, DN baseDN) {
    return new DN2IDSearchCursor(txn, baseDN);
  }

  /**
   * This inner class represents the Search Cursor which can be
   * used to cursor entries in the database starting from some
   * arbitrary base DN.
   */
  protected class DN2IDSearchCursor
  {
    private NdbIndexScanOperation op;
    private NdbResultSet rs;
    private NdbTransaction ndbTxn;
    private AbstractTransaction txn;
    private DN baseDN;

    /**
     * Object constructor.
     * @param txn Abstract Transaction to be used for the operation.
     * @param baseDN Search Cursor base DN.
     */
    public DN2IDSearchCursor(
      AbstractTransaction txn,
      DN baseDN)
    {
      this.txn = txn;
      this.baseDN = baseDN;
    }

    /**
     * Open the cursor.
     * @throws com.mysql.cluster.ndbj.NdbApiException If an error
     * occurs in the database.
     */
    public void open() throws NdbApiException
    {
      ndbTxn = txn.getNdbTransaction();

      op = ndbTxn.getSelectIndexScanOperation(
        PRIMARY_INDEX_NAME, name, NdbOperation.LockMode.LM_CommittedRead);

      int numComponents = baseDN.getNumComponents();
      int componentIndex = numComponents - 1;
      for (int i = 0; i < numComponents; i++) {
        op.setBoundString(BackendImpl.DN2ID_DN +
          Integer.toString(i),
          NdbIndexScanOperation.BoundType.BoundEQ,
          baseDN.getRDN(componentIndex).toNormalizedString());
        componentIndex--;
      }

      if (baseDN.getNumComponents() < BackendImpl.DN2ID_DN_NC) {
        String nextRDNColumn =
          BackendImpl.DN2ID_DN + Integer.toString(numComponents);
        op.setBoundString(nextRDNColumn,
          NdbIndexScanOperation.BoundType.BoundLT, "");
      }

      op.getValue(BackendImpl.EID);

      for (int i = 0; i < BackendImpl.DN2ID_DN_NC; i++) {
        op.getValue(BackendImpl.DN2ID_DN +
          Integer.toString(i));
      }

      rs = op.resultData();
      ndbTxn.execute(ExecType.NoCommit, AbortOption.AbortOnError, true);
    }

    /**
     * Advance one position and return the result.
     * @return An instance of Search Cursor Result.
     * @throws com.mysql.cluster.ndbj.NdbApiException If an error
     * occurs in the database.
     */
    public SearchCursorResult getNext() throws NdbApiException
    {
      if (rs.next()) {
        long eid = rs.getLong(BackendImpl.EID);

        StringBuilder dnBuffer = new StringBuilder();
        int dnColumnIndex = BackendImpl.DN2ID_DN_NC - 1;
        while (dnColumnIndex >= 0) {
          String rdnString = rs.getString(BackendImpl.DN2ID_DN +
            Integer.toString(dnColumnIndex));
          if (rdnString.length() > 0) {
            dnBuffer.append(rdnString);
            if (dnColumnIndex > 0) {
              dnBuffer.append(",");
            }
          }
          dnColumnIndex--;
        }
        String dnString = dnBuffer.toString();

        if ((eid == 0) || (dnString.length() == 0)) {
          return null;
        }

        SearchCursorResult result =
          new SearchCursorResult(dnString, eid);
        return result;
      }
      return null;
    }

    /**
     * Close the cursor.
     */
    public void close()
    {
      ndbTxn = null;
      txn = null;
    }
  }

  /**
   * This inner class represents a Search Cursor Result
   * as returned by the Search Cursor operations.
   */
  protected class SearchCursorResult
  {
    /**
     * Entry DN.
     */
    public String dn;

    /**
     * Entry ID.
     */
    public long id;

    /**
     * Object constructor.
     * @param dn The entry DN.
     * @param id The entry ID.
     */
    public SearchCursorResult(String dn, long id)
    {
      this.dn = dn;
      this.id = id;
    }
  }
}
