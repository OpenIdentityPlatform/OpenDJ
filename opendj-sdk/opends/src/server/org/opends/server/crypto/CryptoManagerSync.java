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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.server.crypto;

import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostResponseModifyDNOperation;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.util.ServerConstants.OC_TOP;
import static org.opends.server.util.ServerConstants.
     OID_ENTRY_CHANGE_NOTIFICATION;
import org.opends.server.config.ConfigConstants;
import static org.opends.server.config.ConfigConstants.OC_CRYPTO_INSTANCE_KEY;
import static org.opends.server.config.ConfigConstants.OC_CRYPTO_CIPHER_KEY;
import static org.opends.server.config.ConfigConstants.OC_CRYPTO_MAC_KEY;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.controls.EntryChangeNotificationControl;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.AddOperation;
import static org.opends.messages.CoreMessages.*;
import org.opends.messages.Message;
import org.opends.admin.ads.ADSContext;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;

/**
 * This class defines an object that synchronizes certificates from the admin
 * data branch into the trust store backend, and synchronizes secret-key entries
 * from the admin data branch to the crypto manager secret-key cache.
 */
public class CryptoManagerSync
     implements BackendInitializationListener, ChangeNotificationListener
{
  /**
   * The debug log tracer for this object.
   */
  private static final DebugTracer TRACER = getTracer();



  // The DN of the administration suffix.
  private DN adminSuffixDN;

  // The DN of the instance keys container within the admin suffix.
  private DN instanceKeysDN;

  // The DN of the secret keys container within the admin suffix.
  private DN secretKeysDN;

  // The DN of the trust store root.
  private DN trustStoreRootDN;

  // The attribute type that is used to specify a server instance certificate.
  AttributeType attrCert;

  // The attribute type that holds a server certificate identifier.
  AttributeType attrAlias;

  // The attribute type that holds the time a key was compromised.
  AttributeType attrCompromisedTime;

  // A filter on object class to select key entries.
  private SearchFilter keySearchFilter;

  // The instance key objectclass.
  private ObjectClass ocInstanceKey;

  // The cipher key objectclass.
  private ObjectClass ocCipherKey;

  // The mac key objectclass.
  private ObjectClass ocMacKey;

  /**
   * Creates a new instance of this trust store synchronization thread.
   *
   * @throws InitializationException in case an exception occurs during
   * initialization, such as a failure to publish the instance-key-pair
   * public-key-certificate in ADS.
   */
  public CryptoManagerSync()
          throws InitializationException
  {
    try {
      CryptoManagerImpl.publishInstanceKeyEntryInADS();
    }
    catch (CryptoManagerException ex) {
      throw new InitializationException(ex.getMessageObject());
    }
    DirectoryServer.registerBackendInitializationListener(this);

    try
    {
      adminSuffixDN = DN.decode(ADSContext.getAdministrationSuffixDN());
      instanceKeysDN = adminSuffixDN.concat(DN.decode("cn=instance keys"));
      secretKeysDN = adminSuffixDN.concat(DN.decode("cn=secret keys"));
      trustStoreRootDN = DN.decode(ConfigConstants.DN_TRUST_STORE_ROOT);
      keySearchFilter =
           SearchFilter.createFilterFromString("(|" +
                "(objectclass=" + OC_CRYPTO_INSTANCE_KEY + ")" +
                "(objectclass=" + OC_CRYPTO_CIPHER_KEY + ")" +
                "(objectclass=" + OC_CRYPTO_MAC_KEY + ")" +
                ")");
    }
    catch (DirectoryException e)
    {
      //
    }

    ocInstanceKey = DirectoryServer.getObjectClass(
         OC_CRYPTO_INSTANCE_KEY, true);
    ocCipherKey = DirectoryServer.getObjectClass(
         OC_CRYPTO_CIPHER_KEY, true);
    ocMacKey = DirectoryServer.getObjectClass(
         OC_CRYPTO_MAC_KEY, true);

    attrCert = DirectoryServer.getAttributeType(
         ConfigConstants.ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE, true);
    attrAlias = DirectoryServer.getAttributeType(
         ConfigConstants.ATTR_CRYPTO_KEY_ID, true);
    attrCompromisedTime = DirectoryServer.getAttributeType(
         ConfigConstants.ATTR_CRYPTO_KEY_COMPROMISED_TIME, true);

    if (DirectoryServer.getBackendWithBaseDN(adminSuffixDN) != null)
    {
      searchAdminSuffix();
    }

    DirectoryServer.registerChangeNotificationListener(this);
  }


  private void searchAdminSuffix()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    LinkedHashSet<String> attributes = new LinkedHashSet<String>(0);

    ArrayList<Control> controls = new ArrayList<Control>(0);

    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn,
                                     InternalClientConnection.nextOperationID(),
                                     InternalClientConnection.nextMessageID(),
                                     controls,
                                     adminSuffixDN, SearchScope.WHOLE_SUBTREE,
                                     DereferencePolicy.NEVER_DEREF_ALIASES,
                                     0, 0,
                                     false, keySearchFilter, attributes,
                                     null);

    searchOperation.run();

    ResultCode resultCode = searchOperation.getResultCode();
    if (resultCode != ResultCode.SUCCESS)
    {
      Message message =
           INFO_TRUSTSTORESYNC_ADMIN_SUFFIX_SEARCH_FAILED.get(
                String.valueOf(adminSuffixDN),
                searchOperation.getErrorMessage().toString());
      ErrorLogger.logError(message);
    }

    for (SearchResultEntry searchEntry : searchOperation.getSearchEntries())
    {
      try
      {
        handleInternalSearchEntry(searchEntry);
      }
      catch (DirectoryException e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_TRUSTSTORESYNC_EXCEPTION.get(
             stackTraceToSingleLineString(e));
        ErrorLogger.logError(message);
      }
    }

  }


  /**
   * {@inheritDoc}
   */
  public void performBackendInitializationProcessing(Backend backend)
  {
    DN[] baseDNs = backend.getBaseDNs();
    if (baseDNs != null)
    {
      for (DN baseDN : baseDNs)
      {
        if (baseDN.equals(adminSuffixDN))
        {
          searchAdminSuffix();
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void performBackendFinalizationProcessing(Backend backend)
  {
    // No implementation required.
  }

  private void handleInternalSearchEntry(SearchResultEntry searchEntry)
       throws DirectoryException
  {
    if (searchEntry.hasObjectClass(ocInstanceKey))
    {
      handleInstanceKeySearchEntry(searchEntry);
    }
    else
    {
      try
      {
        if (searchEntry.hasObjectClass(ocCipherKey))
        {
          DirectoryServer.getCryptoManager().importCipherKeyEntry(searchEntry);
        }
        else if (searchEntry.hasObjectClass(ocMacKey))
        {
          DirectoryServer.getCryptoManager().importMacKeyEntry(searchEntry);
        }
      }
      catch (CryptoManagerException e)
      {
        throw new DirectoryException(
             DirectoryServer.getServerErrorResultCode(), e);
      }
    }
  }


  private void handleInstanceKeySearchEntry(SearchResultEntry searchEntry)
       throws DirectoryException
  {
    RDN srcRDN = searchEntry.getDN().getRDN();

    // Only process the entry if it has the expected form of RDN.
    if (!srcRDN.isMultiValued() &&
         srcRDN.getAttributeType(0).equals(attrAlias))
    {
      DN dstDN = trustStoreRootDN.concat(srcRDN);

      // Extract any change notification control.
      EntryChangeNotificationControl ecn = null;
      List<Control> controls = searchEntry.getControls();
      try
      {
        for (Control c : controls)
        {
          if (c.getOID().equals(OID_ENTRY_CHANGE_NOTIFICATION))
          {
            if (c instanceof LDAPControl)
            {
              ecn = EntryChangeNotificationControl.DECODER.decode(c
                  .isCritical(), ((LDAPControl) c).getValue());
            }
            else
            {
              ecn = (EntryChangeNotificationControl)c;
            }
          }
        }
      }
      catch (DirectoryException e)
      {
        // ignore
      }

      // Get any existing local trust store entry.
      Entry dstEntry = DirectoryServer.getEntry(dstDN);

      if (ecn != null &&
           ecn.getChangeType() == PersistentSearchChangeType.DELETE)
      {
        // The entry was deleted so we should remove it from the local trust
        // store.
        if (dstEntry != null)
        {
          deleteEntry(dstDN);
        }
      }
      else if (searchEntry.hasAttribute(attrCompromisedTime))
      {
        // The key was compromised so we should remove it from the local
        // trust store.
        if (dstEntry != null)
        {
          deleteEntry(dstDN);
        }
      }
      else
      {
        // The entry was added or modified.
        if (dstEntry == null)
        {
          addEntry(searchEntry, dstDN);
        }
        else
        {
          modifyEntry(searchEntry, dstEntry);
        }
      }
    }
  }


  /**
   * Modify an entry in the local trust store if it differs from an entry in
   * the ADS branch.
   * @param srcEntry The instance key entry in the ADS branch.
   * @param dstEntry The local trust store entry.
   */
  private void modifyEntry(Entry srcEntry, Entry dstEntry)
  {
    List<Attribute> srcList;
    srcList = srcEntry.getAttribute(attrCert);

    List<Attribute> dstList;
    dstList = dstEntry.getAttribute(attrCert);

    // Check for changes to the certificate value.
    boolean differ = false;
    if (srcList == null)
    {
      if (dstList != null)
      {
        differ = true;
      }
    }
    else if (dstList == null)
    {
      differ = true;
    }
    else if (srcList.size() != dstList.size())
    {
      differ = true;
    }
    else
    {
      if (!srcList.equals(dstList))
      {
        differ = true;
      }
    }

    if (differ)
    {
      // The trust store backend does not implement modify so we need to
      // delete then add.
      DN dstDN = dstEntry.getDN();
      deleteEntry(dstDN);
      addEntry(srcEntry, dstDN);
    }
  }


  /**
   * Delete an entry from the local trust store.
   * @param dstDN The DN of the entry to be deleted in the local trust store.
   */
  private void deleteEntry(DN dstDN)
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation delOperation = conn.processDelete(dstDN);

    if (delOperation.getResultCode() != ResultCode.SUCCESS)
    {
      Message message = INFO_TRUSTSTORESYNC_DELETE_FAILED.get(
           String.valueOf(dstDN),
           String.valueOf(delOperation.getErrorMessage()));
      ErrorLogger.logError(message);
    }
  }


  /**
   * Add an entry to the local trust store.
   * @param srcEntry The instance key entry in the ADS branch.
   * @param dstDN The DN of the entry to be added in the local trust store.
   */
  private void addEntry(Entry srcEntry, DN dstDN)
  {
    LinkedHashMap<ObjectClass,String> ocMap =
         new LinkedHashMap<ObjectClass,String>(2);
    ocMap.put(DirectoryServer.getTopObjectClass(), OC_TOP);
    ocMap.put(ocInstanceKey, OC_CRYPTO_INSTANCE_KEY);

    HashMap<AttributeType, List<Attribute>> userAttrs =
         new HashMap<AttributeType, List<Attribute>>();

    List<Attribute> attrList;
    attrList = srcEntry.getAttribute(attrAlias);
    if (attrList != null)
    {
      userAttrs.put(attrAlias, attrList);
    }
    attrList = srcEntry.getAttribute(attrCert);
    if (attrList != null)
    {
      userAttrs.put(attrCert, attrList);
    }

    Entry addEntry = new Entry(dstDN, ocMap, userAttrs, null);

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    AddOperation addOperation = conn.processAdd(addEntry);
    if (addOperation.getResultCode() != ResultCode.SUCCESS)
    {
      Message message = INFO_TRUSTSTORESYNC_ADD_FAILED.get(
           String.valueOf(dstDN),
           String.valueOf(addOperation.getErrorMessage()));
      ErrorLogger.logError(message);
    }
  }


  /**
   * {@inheritDoc}
   */
  public void handleAddOperation(PostResponseAddOperation addOperation,
                                 Entry entry)
  {
    if (addOperation.getEntryDN().isDescendantOf(instanceKeysDN))
    {
      handleInstanceKeyAddOperation(entry);
    }
    else if (addOperation.getEntryDN().isDescendantOf(secretKeysDN))
    {
      try
      {
        if (entry.hasObjectClass(ocCipherKey))
        {
          DirectoryServer.getCryptoManager().importCipherKeyEntry(entry);
        }
        else if (entry.hasObjectClass(ocMacKey))
        {
          DirectoryServer.getCryptoManager().importMacKeyEntry(entry);
        }
      }
      catch (CryptoManagerException e)
      {
        Message message = Message.raw("Failed to import key entry: %s",
                                      e.getMessage());
        ErrorLogger.logError(message);
      }
    }
  }


  private void handleInstanceKeyAddOperation(Entry entry)
  {
    RDN srcRDN = entry.getDN().getRDN();

    // Only process the entry if it has the expected form of RDN.
    if (!srcRDN.isMultiValued() &&
         srcRDN.getAttributeType(0).equals(attrAlias))
    {
      DN dstDN = trustStoreRootDN.concat(srcRDN);

      if (!entry.hasAttribute(attrCompromisedTime))
      {
        addEntry(entry, dstDN);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void handleDeleteOperation(PostResponseDeleteOperation deleteOperation,
                                    Entry entry)
  {
    if (!deleteOperation.getEntryDN().isDescendantOf(instanceKeysDN))
    {
      return;
    }

    RDN srcRDN = entry.getDN().getRDN();

    // Only process the entry if it has the expected form of RDN.
    if (!srcRDN.isMultiValued() &&
         srcRDN.getAttributeType(0).equals(attrAlias))
    {
      DN dstDN = trustStoreRootDN.concat(srcRDN);

      deleteEntry(dstDN);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void handleModifyOperation(PostResponseModifyOperation modifyOperation,
                                    Entry oldEntry, Entry newEntry)
  {
    if (modifyOperation.getEntryDN().isDescendantOf(instanceKeysDN))
    {
      handleInstanceKeyModifyOperation(newEntry);
    }
    else if (modifyOperation.getEntryDN().isDescendantOf(secretKeysDN))
    {
      try
      {
        if (newEntry.hasObjectClass(ocCipherKey))
        {
          DirectoryServer.getCryptoManager().importCipherKeyEntry(newEntry);
        }
        else if (newEntry.hasObjectClass(ocMacKey))
        {
          DirectoryServer.getCryptoManager().importMacKeyEntry(newEntry);
        }
      }
      catch (CryptoManagerException e)
      {
        Message message = Message.raw("Failed to import modified key entry: %s",
                                      e.getMessage());
        ErrorLogger.logError(message);
      }
    }
  }

  private void handleInstanceKeyModifyOperation(Entry newEntry)
  {
    RDN srcRDN = newEntry.getDN().getRDN();

    // Only process the entry if it has the expected form of RDN.
    if (!srcRDN.isMultiValued() &&
         srcRDN.getAttributeType(0).equals(attrAlias))
    {
      DN dstDN = trustStoreRootDN.concat(srcRDN);

      // Get any existing local trust store entry.
      Entry dstEntry = null;
      try
      {
        dstEntry = DirectoryServer.getEntry(dstDN);
      }
      catch (DirectoryException e)
      {
        // ignore
      }

      if (newEntry.hasAttribute(attrCompromisedTime))
      {
        // The key was compromised so we should remove it from the local
        // trust store.
        if (dstEntry != null)
        {
          deleteEntry(dstDN);
        }
      }
      else
      {
        if (dstEntry == null)
        {
          addEntry(newEntry, dstDN);
        }
        else
        {
          modifyEntry(newEntry, dstEntry);
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void handleModifyDNOperation(
       PostResponseModifyDNOperation modifyDNOperation, Entry oldEntry,
       Entry newEntry)
  {
    // No implementation required.
  }
}
