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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
    /*
     * TODO Change linked list implementation as suggested below.
     *  I would strongly recommend that you change aciList to be
     *  LinkedHashMap<DN,List<Aci>> or LinkedHashMap<DN,Aci[]> rather than
     *  LinkedHashMap<String,Aci>.  It looks like there are some costly
     *  string->DN and even string->DN->string conversions.  Further, the very
     *  hackish way that the linked-list is currently maintained is very
     *  ugly and potentially error-prone.
     */
    private LinkedHashMap<DN, Aci> aciList =
            new LinkedHashMap<DN, Aci>();
    /*
     * TODO Evaluate making this class lock-free.
     *  I would definitely try to make this a lock-free class if at all
     *  possible. Read locks aren't free to acquire, since they still require
     *  an exclusive lock at some point.  If possible, you should use a
     *  copy-on-write structure so that you only incur penalties for changing
     *  the ACI list (which should be a rare event) and there is no need for
     *  any kind of locking at all for read operations.
     */
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock aciReadLock  = rwl.readLock();
    private final Lock aciWriteLock = rwl.writeLock();

    /*
     * TODO Add support global ACIs in config.ldif.
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
        try {
            aciReadLock.lock();
            while(baseDN != null) {
                Aci aci = aciList.get(baseDN);
                if (aci != null)
                {
                    while (aci != null)
                    {
                        candidates.add(aci);
                        aci = aci.next;
                    }
                }
                if(baseDN.isNullDN())
                    break;
               DN parentDN=baseDN.getParent();
               if(parentDN == null)
                    baseDN=DN.nullDN();
                else
                    baseDN=parentDN;
            }
        } finally {
            aciReadLock.unlock();
        }
        return candidates;
    }


    /**
     * Add all of an entries ACI attribute values to the ACI list. This
     * method locks/unlocks the list.
     * @param entry The entry containing the "aci" attribute values.\
     * @return The number of valid ACI attribute values added to the ACI list.
     */
    public int addAci(Entry entry) {
        int validAcis=0;
        DN dn=entry.getDN();
        List<Attribute> attributeList =
                entry.getOperationalAttribute(AciHandler.aciType);
        try {
            aciWriteLock.lock();
            validAcis=addAciAttributeListNoLock(dn, attributeList);
        } finally {
            aciWriteLock.unlock();
        }
        return validAcis;
    }

    /**
     * Add "aci" attribute type values to the ACI list. There is a chance
     * that an ACI will throw an exception if it has an invalid syntax.
     * If that happens a message will be logged and the ACI skipped.
     * @param dn The DN to use a the key in the ACI list.
     * @param attributeList List of attributes contain the "aci" attribute
     * values.
     * @return The number of valid "aci" attribute types added to the ACI list.
     */
    private int addAciAttributeListNoLock(DN dn,
                                    List<Attribute> attributeList) {
        int validAcis=0;
        for (Attribute attribute : attributeList) {
            for (AttributeValue value : attribute.getValues()) {
                try {
                    Aci aci= Aci.decode(value.getValue(),dn);
                    addAci(dn, aci);
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
        return validAcis;
    }

    /**
     * Remove all of the ACIs related to the old entry and then add all of the
     * ACIs related to the new entry. This method locks/unlocks the list.
     * @param oldEntry The old entry maybe containing old "aci" attribute
     * values.
     * @param newEntry The new entry maybe containing new "aci" attribute
     * values.
     */
    public void modAciOldNewEntry(Entry oldEntry, Entry newEntry) {
         if((oldEntry.hasOperationalAttribute(AciHandler.aciType)) ||
                 (newEntry.hasOperationalAttribute(AciHandler.aciType))) {
             try {
                 aciWriteLock.lock();
                 aciList.remove(oldEntry.getDN());
                 List<Attribute> attributeList =
                     newEntry.getOperationalAttribute(AciHandler.aciType, null);
                 addAciAttributeListNoLock(newEntry.getDN(),attributeList);
             } finally {
                 aciWriteLock.unlock();
             }
         }
     }

    /**
     * Add an ACI using the DN as a key. If the DN already
     * has ACI(s) on the list, then the new ACI is added to the
     * end of the linked list.
     * @param dn The DN to use as the key.
     * @param aci  The ACI to add to the list.
     */
    public void addAci(DN dn, Aci aci)  {
        if(aciList.containsKey(dn)) {
            Aci tmpAci = aciList.get(dn);
            while(tmpAci.next != null)
                tmpAci=tmpAci.next;
            tmpAci.next=aci;
        } else
            aciList.put(dn, aci);
    }

    /**
     * Remove ACIs related to an entry.
     * @param entry The entry to be removed.
     * @return True if the ACI set was deleted.
     */
    public boolean removeAci(Entry entry) {
        boolean deleted = false;
        try {
            aciWriteLock.lock();
            if (aciList.remove(entry.getDN()) != null)
                deleted = true;
        } finally {
            aciWriteLock.unlock();
        }
        return deleted;
    }

    /**
     * Remove all ACIs related to a backend.
     * @param backend  The backend to check if each DN is handled by that
     * backend.
     */
    public void removeAci (Backend backend) {
        try {
            aciWriteLock.lock();
            Set<DN> keys=aciList.keySet();
            for(DN dn : keys) {
                if (backend.handlesEntry(dn))
                    aciList.remove(dn);
            }
        } finally {
            aciWriteLock.unlock();
        }
    }
}
