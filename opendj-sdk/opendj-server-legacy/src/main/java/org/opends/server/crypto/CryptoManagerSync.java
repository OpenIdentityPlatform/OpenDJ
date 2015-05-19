/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.crypto;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.api.plugin.PluginType.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.admin.ads.ADSContext;
import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.plugin.InternalDirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult.PostResponse;
import org.opends.server.config.ConfigConstants;
import org.opends.server.controls.EntryChangeNotificationControl;
import org.opends.server.controls.PersistentSearchChangeType;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Control;
import org.opends.server.types.CryptoManagerException;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RDN;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.operation.PostResponseAddOperation;
import org.opends.server.types.operation.PostResponseDeleteOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;

/**
 * This class defines an object that synchronizes certificates from the admin
 * data branch into the trust store backend, and synchronizes secret-key entries
 * from the admin data branch to the crypto manager secret-key cache.
 */
public class CryptoManagerSync extends InternalDirectoryServerPlugin
     implements BackendInitializationListener
{
  /** The debug log tracer for this object. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The DN of the administration suffix. */
  private DN adminSuffixDN;

  /** The DN of the instance keys container within the admin suffix. */
  private DN instanceKeysDN;

  /** The DN of the secret keys container within the admin suffix. */
  private DN secretKeysDN;

  /** The DN of the trust store root. */
  private DN trustStoreRootDN;

  /** The attribute type that is used to specify a server instance certificate. */
  private final AttributeType attrCert;

  /** The attribute type that holds a server certificate identifier. */
  private final AttributeType attrAlias;

  /** The attribute type that holds the time a key was compromised. */
  private final AttributeType attrCompromisedTime;

  /** A filter on object class to select key entries. */
  private SearchFilter keySearchFilter;

  /** The instance key objectclass. */
  private final ObjectClass ocInstanceKey;

  /** The cipher key objectclass. */
  private final ObjectClass ocCipherKey;

  /** The mac key objectclass. */
  private final ObjectClass ocMacKey;

  /** Dummy configuration DN. */
  private static final String CONFIG_DN = "cn=Crypto Manager Sync,cn=config";

  /**
   * Creates a new instance of this trust store synchronization thread.
   *
   * @throws InitializationException in case an exception occurs during
   * initialization, such as a failure to publish the instance-key-pair
   * public-key-certificate in ADS.
   */
  public CryptoManagerSync() throws InitializationException
  {
    super(toDN(CONFIG_DN), EnumSet.of(
        // No implementation required for modify_dn operations
        // FIXME: Technically it is possible to perform a subtree modDN
        // in this case however such subtree modDN would essentially be
        // moving configuration branches which should not happen.
        POST_RESPONSE_ADD, POST_RESPONSE_MODIFY, POST_RESPONSE_DELETE),
        true);
    try {
      CryptoManagerImpl.publishInstanceKeyEntryInADS();
    }
    catch (CryptoManagerException ex) {
      throw new InitializationException(ex.getMessageObject());
    }
    DirectoryServer.registerBackendInitializationListener(this);

    try
    {
      adminSuffixDN = DN.valueOf(ADSContext.getAdministrationSuffixDN());
      instanceKeysDN = adminSuffixDN.child(DN.valueOf("cn=instance keys"));
      secretKeysDN = adminSuffixDN.child(DN.valueOf("cn=secret keys"));
      trustStoreRootDN = DN.valueOf(ConfigConstants.DN_TRUST_STORE_ROOT);
      keySearchFilter =
           SearchFilter.createFilterFromString("(|" +
                "(objectclass=" + OC_CRYPTO_INSTANCE_KEY + ")" +
                "(objectclass=" + OC_CRYPTO_CIPHER_KEY + ")" +
                "(objectclass=" + OC_CRYPTO_MAC_KEY + ")" +
                ")");
    }
    catch (DirectoryException e)
    {
    }

    ocInstanceKey = DirectoryServer.getObjectClass(OC_CRYPTO_INSTANCE_KEY, true);
    ocCipherKey = DirectoryServer.getObjectClass(OC_CRYPTO_CIPHER_KEY, true);
    ocMacKey = DirectoryServer.getObjectClass(OC_CRYPTO_MAC_KEY, true);

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

    DirectoryServer.registerInternalPlugin(this);
  }

  private static DN toDN(final String dn) throws InitializationException
  {
    try
    {
      return DN.valueOf(dn);
    }
    catch (DirectoryException e)
    {
      throw new RuntimeException(e);
    }
  }


  private void searchAdminSuffix()
  {
    SearchRequest request = newSearchRequest(adminSuffixDN, SearchScope.WHOLE_SUBTREE, keySearchFilter);
    InternalSearchOperation searchOperation = getRootConnection().processSearch(request);
    ResultCode resultCode = searchOperation.getResultCode();
    if (resultCode != ResultCode.SUCCESS)
    {
      logger.debug(INFO_TRUSTSTORESYNC_ADMIN_SUFFIX_SEARCH_FAILED, adminSuffixDN,
                searchOperation.getErrorMessage());
    }

    for (SearchResultEntry searchEntry : searchOperation.getSearchEntries())
    {
      try
      {
        handleInternalSearchEntry(searchEntry);
      }
      catch (DirectoryException e)
      {
        logger.traceException(e);

        logger.error(ERR_TRUSTSTORESYNC_EXCEPTION, stackTraceToSingleLineString(e));
      }
    }
  }


  /** {@inheritDoc} */
  @Override
  public void performBackendInitializationProcessing(Backend<?> backend)
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

  /** {@inheritDoc} */
  @Override
  public void performBackendFinalizationProcessing(Backend<?> backend)
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
    RDN srcRDN = searchEntry.getName().rdn();

    // Only process the entry if it has the expected form of RDN.
    if (!srcRDN.isMultiValued() &&
         srcRDN.getAttributeType(0).equals(attrAlias))
    {
      DN dstDN = trustStoreRootDN.child(srcRDN);

      // Extract any change notification control.
      EntryChangeNotificationControl ecn = null;
      List<Control> controls = searchEntry.getControls();
      try
      {
        for (Control c : controls)
        {
          if (OID_ENTRY_CHANGE_NOTIFICATION.equals(c.getOID()))
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
        // entry was deleted so remove it from the local trust store
        if (dstEntry != null)
        {
          deleteEntry(dstDN);
        }
      }
      else if (searchEntry.hasAttribute(attrCompromisedTime))
      {
        // key was compromised so remove it from the local trust store
        if (dstEntry != null)
        {
          deleteEntry(dstDN);
        }
      }
      else if (dstEntry == null)
      {
        // The entry was added
        addEntry(searchEntry, dstDN);
      }
      else
      {
        // The entry was modified
        modifyEntry(searchEntry, dstEntry);
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
    List<Attribute> srcList = srcEntry.getAttribute(attrCert);
    List<Attribute> dstList = dstEntry.getAttribute(attrCert);

    // Check for changes to the certificate value.
    boolean differ = false;
    if (srcList == null)
    {
      if (dstList != null)
      {
        differ = true;
      }
    }
    else if (dstList == null
        || srcList.size() != dstList.size()
        || !srcList.equals(dstList))
    {
      differ = true;
    }

    if (differ)
    {
      // The trust store backend does not implement modify so we need to
      // delete then add.
      DN dstDN = dstEntry.getName();
      deleteEntry(dstDN);
      addEntry(srcEntry, dstDN);
    }
  }


  /**
   * Delete an entry from the local trust store.
   * @param dstDN The DN of the entry to be deleted in the local trust store.
   */
  private static void deleteEntry(DN dstDN)
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation delOperation = conn.processDelete(dstDN);

    if (delOperation.getResultCode() != ResultCode.SUCCESS)
    {
      logger.debug(INFO_TRUSTSTORESYNC_DELETE_FAILED, dstDN, delOperation.getErrorMessage());
    }
  }


  /**
   * Add an entry to the local trust store.
   * @param srcEntry The instance key entry in the ADS branch.
   * @param dstDN The DN of the entry to be added in the local trust store.
   */
  private void addEntry(Entry srcEntry, DN dstDN)
  {
    Map<ObjectClass, String> ocMap = new LinkedHashMap<>(2);
    ocMap.put(DirectoryServer.getTopObjectClass(), OC_TOP);
    ocMap.put(ocInstanceKey, OC_CRYPTO_INSTANCE_KEY);

    Map<AttributeType, List<Attribute>> userAttrs = new HashMap<>();

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
      logger.debug(INFO_TRUSTSTORESYNC_ADD_FAILED, dstDN, addOperation.getErrorMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public PostResponse doPostResponse(PostResponseAddOperation op)
  {
    if (op.getResultCode() != ResultCode.SUCCESS)
    {
      return PostResponse.continueOperationProcessing();
    }

    final Entry entry = op.getEntryToAdd();
    final DN entryDN = op.getEntryDN();
    if (entryDN.isDescendantOf(instanceKeysDN))
    {
      handleInstanceKeyAddOperation(entry);
    }
    else if (entryDN.isDescendantOf(secretKeysDN))
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
        logger.error(LocalizableMessage.raw(
            "Failed to import key entry: %s", e.getMessage()));
      }
    }
    return PostResponse.continueOperationProcessing();
  }


  private void handleInstanceKeyAddOperation(Entry entry)
  {
    RDN srcRDN = entry.getName().rdn();

    // Only process the entry if it has the expected form of RDN.
    if (!srcRDN.isMultiValued() &&
         srcRDN.getAttributeType(0).equals(attrAlias))
    {
      DN dstDN = trustStoreRootDN.child(srcRDN);

      if (!entry.hasAttribute(attrCompromisedTime))
      {
        addEntry(entry, dstDN);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public PostResponse doPostResponse(PostResponseDeleteOperation op)
  {
    if (op.getResultCode() != ResultCode.SUCCESS
        || !op.getEntryDN().isDescendantOf(instanceKeysDN))
    {
      return PostResponse.continueOperationProcessing();
    }

    RDN srcRDN = op.getEntryToDelete().getName().rdn();

    // Only process the entry if it has the expected form of RDN.
    // FIXME: Technically it is possible to perform a subtree in
    // this case however such subtree delete would essentially be
    // removing configuration branches which should not happen.
    if (!srcRDN.isMultiValued() &&
         srcRDN.getAttributeType(0).equals(attrAlias))
    {
      DN destDN = trustStoreRootDN.child(srcRDN);
      deleteEntry(destDN);
    }
    return PostResponse.continueOperationProcessing();
  }

  /** {@inheritDoc} */
  @Override
  public PostResponse doPostResponse(PostResponseModifyOperation op)
  {
    if (op.getResultCode() != ResultCode.SUCCESS)
    {
      return PostResponse.continueOperationProcessing();
    }

    final Entry newEntry = op.getModifiedEntry();
    final DN entryDN = op.getEntryDN();
    if (entryDN.isDescendantOf(instanceKeysDN))
    {
      handleInstanceKeyModifyOperation(newEntry);
    }
    else if (entryDN.isDescendantOf(secretKeysDN))
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
        logger.error(LocalizableMessage.raw(
            "Failed to import modified key entry: %s", e.getMessage()));
      }
    }
    return PostResponse.continueOperationProcessing();
  }

  private void handleInstanceKeyModifyOperation(Entry newEntry)
  {
    RDN srcRDN = newEntry.getName().rdn();

    // Only process the entry if it has the expected form of RDN.
    if (!srcRDN.isMultiValued() &&
         srcRDN.getAttributeType(0).equals(attrAlias))
    {
      DN dstDN = trustStoreRootDN.child(srcRDN);

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
      else if (dstEntry == null)
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
