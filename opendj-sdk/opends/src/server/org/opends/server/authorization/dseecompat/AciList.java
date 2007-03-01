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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.authorization.dseecompat;

import static org.opends.server.authorization.dseecompat.AciMessages.*;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.messages.MessageHandler.getMessage;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.api.Backend;

/**
 * The AciList class performs caching of the ACI attribute values
 * using the entry DN as the key.
 */
public class AciList {
  /**
   * A map containing all the ACIs.
   * We use the copy-on-write technique to avoid locking when reading.
   */
  private volatile LinkedHashMap<DN, List<Aci>> aciList =
       new LinkedHashMap<DN, List<Aci>>();

  /**
   * Accessor to the ACI list intended to be called from within unsynchronized
   * read-only methods.
   */
  private LinkedHashMap<DN, List<Aci>> getList() {
    return aciList;
  }

  /**
   * Used by synchronized write methods to make a copy of the ACI list.
   * @return A copy of the ACI list.
   */
  private LinkedHashMap<DN,List<Aci>> copyList() {
    return new LinkedHashMap<DN, List<Aci>>(aciList);
  }

  /*
  * TODO Add support for global ACIs in config.ldif.
  *
  */
  /**
   * Using the base DN, return a list of ACIs that are candidates for
   * evaluation by walking up from the base DN towards the root of the
   * DIT gathering ACIs on parents.
   *
   * @param baseDN  The DN to check.
   * @return A list of candidate ACIs that might be applicable.
   */
  public LinkedList<Aci> getCandidateAcis(DN baseDN) {
    LinkedList<Aci> candidates = new LinkedList<Aci>();
    if(baseDN == null)
      return candidates;

    // Save a reference to the current ACI list, in case it gets changed.
    LinkedHashMap<DN, List<Aci>> aciList = getList();

    while(baseDN != null) {
      List<Aci> acis = aciList.get(baseDN);
      if (acis != null)
      {
        candidates.addAll(acis);
      }
      if(baseDN.isNullDN())
        break;
      DN parentDN=baseDN.getParent();
      if(parentDN == null)
        baseDN=DN.nullDN();
      else
        baseDN=parentDN;
    }
    return candidates;
  }

  /**
   * Add all the ACI from a set of entries to the ACI list.
   * @param entries The set of entries containing the "aci" attribute values.
   * @return The number of valid ACI attribute values added to the ACI list.
   */
  public synchronized int addAci(List<? extends Entry> entries)
  {
    // Copy the ACI list.
    LinkedHashMap<DN,List<Aci>> aciCopy = copyList();

    int validAcis=0;
    for (Entry entry : entries) {
      DN dn=entry.getDN();
      List<Attribute> attributeList =
           entry.getOperationalAttribute(AciHandler.aciType);
      validAcis += addAciAttributeList(aciCopy, dn, attributeList);
    }

    // Replace the ACI list with the copy.
    aciList = aciCopy;
    return validAcis;
  }

  /**
   * Add all of an entry's ACI attribute values to the ACI list.
   * @param entry The entry containing the "aci" attribute values.
   * @return The number of valid ACI attribute values added to the ACI list.
   */
  public synchronized int addAci(Entry entry) {
    int validAcis;
    DN dn=entry.getDN();
    List<Attribute> attributeList =
         entry.getOperationalAttribute(AciHandler.aciType);

    // Copy the ACI list.
    LinkedHashMap<DN,List<Aci>> aciCopy = copyList();

    validAcis=addAciAttributeList(aciCopy, dn, attributeList);

    // Replace the ACI list with the copy.
    aciList = aciCopy;
    return validAcis;
  }

  /**
   * Add "aci" attribute type values to the ACI list. There is a chance
   * that an ACI will throw an exception if it has an invalid syntax.
   * If that happens a message will be logged and the ACI skipped.
   * @param aciList The ACI list to which the ACI is to be added.
   * @param dn The DN to use as the key in the ACI list.
   * @param attributeList List of attributes containing the "aci" attribute
   * values.
   * @return The number of valid "aci" attribute values added to the ACI list.
   */
  private static int addAciAttributeList(
       LinkedHashMap<DN,List<Aci>> aciList, DN dn,
       List<Attribute> attributeList) {
    int validAcis=0;
    ArrayList<Aci> acis = new ArrayList<Aci>();
    for (Attribute attribute : attributeList) {
      for (AttributeValue value : attribute.getValues()) {
        try {
          Aci aci= Aci.decode(value.getValue(),dn);
          acis.add(aci);
          validAcis++;
        } catch (AciException ex) {
          /* An illegal ACI might have been loaded
           * during import and is failing at ACI handler
           * initialization time. Log a message and continue
           * processing. ACIs added via LDAP add have their
           * syntax checked before adding and should never
           * hit this code.
           */
          int    msgID  = MSGID_ACI_ADD_LIST_FAILED_DECODE;
          String message = getMessage(msgID,
                                      ex.getMessage());
          logError(ErrorLogCategory.ACCESS_CONTROL,
                   ErrorLogSeverity.SEVERE_WARNING,
                   message, msgID);
        }
      }
    }
    addAci(aciList, dn, acis);
    return validAcis;
  }

  /**
   * Remove all of the ACIs related to the old entry and then add all of the
   * ACIs related to the new entry. This method locks/unlocks the list.
   * @param oldEntry The old entry possibly containing old "aci" attribute
   * values.
   * @param newEntry The new entry possibly containing new "aci" attribute
   * values.
   */
  public synchronized void modAciOldNewEntry(Entry oldEntry, Entry newEntry) {
    if((oldEntry.hasOperationalAttribute(AciHandler.aciType)) ||
         (newEntry.hasOperationalAttribute(AciHandler.aciType))) {

      // Copy the ACI list.
      LinkedHashMap<DN,List<Aci>> aciCopy = copyList();

      aciCopy.remove(oldEntry.getDN());
      List<Attribute> attributeList =
           newEntry.getOperationalAttribute(AciHandler.aciType, null);
      addAciAttributeList(aciCopy,newEntry.getDN(),attributeList);

      // Replace the ACI list with the copy.
      aciList = aciCopy;
    }
  }

  /**
   * Add ACI using the DN as a key. If the DN already
   * has ACI(s) on the list, then the new ACI is added to the
   * end of the array.
   * @param aciList The set of ACIs to which ACI is to be added.
   * @param dn The DN to use as the key.
   * @param acis The ACI to be added.
   */
  private static void addAci(LinkedHashMap<DN,List<Aci>> aciList, DN dn,
                             List<Aci> acis)
  {
    if(aciList.containsKey(dn)) {
      List<Aci> tmpAci = aciList.get(dn);
      tmpAci.addAll(acis);
    } else {
      aciList.put(dn, acis);
    }
  }

  /**
   * Remove ACIs related to an entry.
   * @param entry The entry to be removed.
   * @return True if the ACI set was deleted.
   */
  public synchronized boolean removeAci(Entry entry) {
    // Copy the ACI list.
    LinkedHashMap<DN,List<Aci>> aciCopy = copyList();

    boolean deleted = false;
    if (aciCopy.remove(entry.getDN()) != null)
      deleted = true;

    // Replace the ACI list with the copy.
    aciList = aciCopy;
    return deleted;
  }

  /**
   * Remove all ACIs related to a backend.
   * @param backend  The backend to check if each DN is handled by that
   * backend.
   */
  public synchronized void removeAci(Backend backend) {
    // Copy the ACI list.
    LinkedHashMap<DN,List<Aci>> aciCopy = copyList();

    Set<DN> keys=aciCopy.keySet();
    for(DN dn : keys) {
      if (backend.handlesEntry(dn))
        aciCopy.remove(dn);
    }

    // Replace the ACI list with the copy.
    aciList = aciCopy;
  }
}
