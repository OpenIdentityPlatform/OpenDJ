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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableStorage;
import org.opends.server.backends.pluggable.spi.Storage;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableStorage;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchResultReference;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.JebMessages.*;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class represents the referral database which contains URIs from referral
 * entries.  The key is the DN of the referral entry and the value is that of a
 * labeled URI in the ref attribute for that entry. Duplicate keys are permitted
 * since a referral entry can contain multiple values of the ref attribute.  Key
 * order is the same as in the DN database so that all referrals in a subtree
 * can be retrieved by cursoring through a range of the records.
 */
public class DN2URI extends DatabaseContainer
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final int prefixRDNComponents;

  /**
   * The standard attribute type that is used to specify the set of referral
   * URLs in a referral entry.
   */
  private final AttributeType referralType =
       DirectoryServer.getAttributeType(ATTR_REFERRAL_URL);

  /**
   * A flag that indicates whether there are any referrals contained in this
   * database.  It should only be set to {@code false} when it is known that
   * there are no referrals.
   */
  private volatile ConditionResult containsReferrals =
       ConditionResult.UNDEFINED;


  /**
   * Create a new object representing a referral database in a given
   * entryContainer.
   *
   * @param treeName
   *          The name of the referral database.
   * @param storage
   *          The JE environment.
   * @param entryContainer
   *          The entryContainer of the DN database.
   * @throws StorageRuntimeException
   *           If an error occurs in the JE database.
   */
  DN2URI(TreeName treeName, Storage storage, EntryContainer entryContainer)
      throws StorageRuntimeException
  {
    super(treeName, storage, entryContainer);

    prefixRDNComponents = entryContainer.getBaseDN().size();
  }

  private ByteSequence encode(Collection<String> col)
  {
    if (col != null)
    {
      ByteStringBuilder b = new ByteStringBuilder();
      b.append(col.size());
      for (String s : col)
      {
        byte[] bytes = StaticUtils.getBytes(s);
        b.append(bytes.length);
        b.append(bytes);
      }
      return b;
    }
    return ByteString.empty();
  }

  private Collection<String> decode(ByteSequence bs)
  {
    if (!bs.isEmpty())
    {
      ByteSequenceReader r = bs.asReader();
      final int nbElems = r.getInt();
      ArrayList<String> results = new ArrayList<String>(nbElems);
      for (int i = 0; i < nbElems; i++)
      {
        final int stringLength = r.getInt();
        results.add(r.getString(stringLength));
      }
      return results;
    }
    return new ArrayList<String>();
  }

  /**
   * Insert a URI value in the referral database.
   *
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param dn The DN of the referral entry.
   * @param labeledURIs The labeled URI value of the ref attribute.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  private void insert(WriteableStorage txn, DN dn, Collection<String> labeledURIs) throws StorageRuntimeException
  {
    ByteString key = toKey(dn);

    ByteString oldValue = read(txn, key, true);
    if (oldValue != null)
    {
      final Collection<String> newUris = decode(oldValue);
      if (newUris.addAll(labeledURIs))
      {
        put(txn, key, encode(newUris));
      }
    }
    else
    {
      txn.putIfAbsent(treeName, key, encode(labeledURIs));
    }
    containsReferrals = ConditionResult.TRUE;
  }

  /**
   * Delete URI values for a given referral entry from the referral database.
   *
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param dn The DN of the referral entry for which URI values are to be
   * deleted.
   * @return true if the values were deleted, false if not.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public boolean delete(WriteableStorage txn, DN dn) throws StorageRuntimeException
  {
    ByteString key = toKey(dn);

    if (delete(txn, key))
    {
      containsReferrals = containsReferrals(txn);
      return true;
    }
    return false;
  }

  /**
   * Delete a single URI value from the referral database.
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param dn The DN of the referral entry.
   * @param labeledURIs The URI value to be deleted.
   * @return true if the value was deleted, false if not.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public boolean delete(WriteableStorage txn, DN dn, Collection<String> labeledURIs)
       throws StorageRuntimeException
  {
    ByteString key = toKey(dn);

    ByteString oldValue = read(txn, key, true);
    if (oldValue != null)
    {
      final Collection<String> oldUris = decode(oldValue);
      if (oldUris.removeAll(labeledURIs))
      {
        put(txn, key, encode(oldUris));
        containsReferrals = containsReferrals(txn);
        return true;
      }
    }
    return false;
  }

  /**
   * Indicates whether the underlying database contains any referrals.
   *
   * @param  txn  The transaction to use when making the determination.
   *
   * @return  {@code true} if it is believed that the underlying database may
   *          contain at least one referral, or {@code false} if it is certain
   *          that it doesn't.
   */
  private ConditionResult containsReferrals(ReadableStorage txn)
  {
    Cursor cursor = txn.openCursor(treeName);
    try
    {
      return ConditionResult.valueOf(cursor.next());
    }
    catch (Exception e)
    {
      logger.traceException(e);

      return ConditionResult.UNDEFINED;
    }
    finally
    {
      cursor.close();
    }
  }

  /**
   * Update the referral database for an entry that has been modified.  Does
   * not do anything unless the entry before the modification or the entry after
   * the modification is a referral entry.
   *
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param before The entry before the modifications have been applied.
   * @param after The entry after the modifications have been applied.
   * @param mods The sequence of modifications made to the entry.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public void modifyEntry(WriteableStorage txn, Entry before, Entry after,
                          List<Modification> mods)
       throws StorageRuntimeException
  {
    DN entryDN = before.getName();
    for (Modification mod : mods)
    {
      Attribute modAttr = mod.getAttribute();
      AttributeType modAttrType = modAttr.getAttributeType();
      if (modAttrType.equals(referralType))
      {
        Attribute a = mod.getAttribute();
        switch (mod.getModificationType().asEnum())
        {
          case ADD:
            if (a != null)
            {
              insert(txn, entryDN, toStrings(a));
            }
            break;

          case DELETE:
            if (a == null || a.isEmpty())
            {
              delete(txn, entryDN);
            }
            else
            {
              delete(txn, entryDN, toStrings(a));
            }
            break;

          case INCREMENT:
            // Nonsensical.
            break;

          case REPLACE:
            delete(txn, entryDN);
            if (a != null)
            {
              insert(txn, entryDN, toStrings(a));
            }
            break;
        }
      }
    }
  }

  private List<String> toStrings(Attribute a)
  {
    List<String> results = new ArrayList<String>(a.size());
    for (ByteString v : a)
    {
      results.add(v.toString());
    }
    return results;
  }

  /**
   * Update the referral database for an entry that has been replaced. Does not
   * do anything unless the entry before it was replaced or the entry after it
   * was replaced is a referral entry.
   *
   * @param txn
   *          A database transaction used for the update, or null if none is
   *          required.
   * @param before
   *          The entry before it was replaced.
   * @param after
   *          The entry after it was replaced.
   * @throws StorageRuntimeException
   *           If an error occurs in the JE database.
   */
  public void replaceEntry(WriteableStorage txn, Entry before, Entry after)
       throws StorageRuntimeException
  {
    deleteEntry(txn, before);
    addEntry(txn, after);
  }

  /**
   * Update the referral database for a new entry. Does nothing if the entry
   * is not a referral entry.
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param entry The entry to be added.
   * @return True if the entry was added successfully or False otherwise.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public boolean addEntry(WriteableStorage txn, Entry entry)
       throws StorageRuntimeException
  {
    Set<String> labeledURIs = entry.getReferralURLs();
    if (labeledURIs != null)
    {
      insert(txn, entry.getName(), labeledURIs);
    }
    return true;
  }

  /**
   * Update the referral database for a deleted entry. Does nothing if the entry
   * was not a referral entry.
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param entry The entry to be deleted.
   * @throws StorageRuntimeException If an error occurs in the JE database.
   */
  public void deleteEntry(WriteableStorage txn, Entry entry)
       throws StorageRuntimeException
  {
    Set<String> labeledURIs = entry.getReferralURLs();
    if (labeledURIs != null)
    {
      delete(txn, entry.getName());
    }
  }

  /**
   * Checks whether the target of an operation is a referral entry and throws
   * a Directory referral exception if it is.
   * @param entry The target entry of the operation, or the base entry of a
   * search operation.
   * @param searchScope The scope of the search operation, or null if the
   * operation is not a search operation.
   * @throws DirectoryException If a referral is found at or above the target
   * DN.  The referral URLs will be set appropriately for the references found
   * in the referral entry.
   */
  public void checkTargetForReferral(Entry entry, SearchScope searchScope)
       throws DirectoryException
  {
    Set<String> referralURLs = entry.getReferralURLs();
    if (referralURLs != null)
    {
      throwReferralException(entry.getName(), entry.getName(), referralURLs,
                             searchScope);
    }
  }

  /**
   * Throws a Directory referral exception for the case where a referral entry
   * exists at or above the target DN of an operation.
   * @param targetDN The target DN of the operation, or the base object of a
   * search operation.
   * @param referralDN The DN of the referral entry.
   * @param labeledURIs The set of labeled URIs in the referral entry.
   * @param searchScope The scope of the search operation, or null if the
   * operation is not a search operation.
   * @throws DirectoryException If a referral is found at or above the target
   * DN.  The referral URLs will be set appropriately for the references found
   * in the referral entry.
   */
  public void throwReferralException(DN targetDN, DN referralDN, Collection<String> labeledURIs, SearchScope searchScope)
       throws DirectoryException
  {
    ArrayList<String> URIList = new ArrayList<String>(labeledURIs.size());
    for (String labeledURI : labeledURIs)
    {
      // Remove the label part of the labeled URI if there is a label.
      String uri = labeledURI;
      int i = labeledURI.indexOf(' ');
      if (i != -1)
      {
        uri = labeledURI.substring(0, i);
      }

      try
      {
        LDAPURL ldapurl = LDAPURL.decode(uri, false);

        if ("ldap".equalsIgnoreCase(ldapurl.getScheme()))
        {
          DN urlBaseDN = targetDN;
          if (!referralDN.equals(ldapurl.getBaseDN()))
          {
            urlBaseDN =
                 EntryContainer.modDN(targetDN,
                                      referralDN.size(),
                                      ldapurl.getBaseDN());
          }
          ldapurl.setBaseDN(urlBaseDN);
          if (searchScope == null)
          {
            // RFC 3296, 5.2.  Target Object Considerations:
            // In cases where the URI to be returned is a LDAP URL, the server
            // SHOULD trim any present scope, filter, or attribute list from the
            // URI before returning it.  Critical extensions MUST NOT be trimmed
            // or modified.
            StringBuilder builder = new StringBuilder(uri.length());
            ldapurl.toString(builder, true);
            uri = builder.toString();
          }
          else
          {
            // RFC 3296, 5.3.  Base Object Considerations:
            // In cases where the URI to be returned is a LDAP URL, the server
            // MUST provide an explicit scope specifier from the LDAP URL prior
            // to returning it.
            ldapurl.getAttributes().clear();
            ldapurl.setScope(searchScope);
            ldapurl.setFilter(null);
            uri = ldapurl.toString();
          }
        }
      }
      catch (DirectoryException e)
      {
        logger.traceException(e);
        // Return the non-LDAP URI as is.
      }

      URIList.add(uri);
    }

    // Throw a directory referral exception containing the URIs.
    LocalizableMessage msg = NOTE_JEB_REFERRAL_RESULT_MESSAGE.get(referralDN);
    throw new DirectoryException(
            ResultCode.REFERRAL, msg, referralDN, URIList, null);
  }

  /**
   * Process referral entries that are above the target DN of an operation.
   * @param targetDN The target DN of the operation, or the base object of a
   * search operation.
   * @param searchScope The scope of the search operation, or null if the
   * operation is not a search operation.
   * @throws DirectoryException If a referral is found at or above the target
   * DN.  The referral URLs will be set appropriately for the references found
   * in the referral entry.
   */
  public void targetEntryReferrals(ReadableStorage txn, DN targetDN, SearchScope searchScope)
       throws DirectoryException
  {
    if (containsReferrals == ConditionResult.UNDEFINED)
    {
      containsReferrals = containsReferrals(txn);
    }

    if (containsReferrals == ConditionResult.FALSE)
    {
      return;
    }

    try
    {
      final Cursor cursor = txn.openCursor(treeName);
      try
      {
        // Go up through the DIT hierarchy until we find a referral.
        for (DN dn = entryContainer.getParentWithinBase(targetDN); dn != null;
             dn = entryContainer.getParentWithinBase(dn))
        {
          // Look for a record whose key matches the current DN.
          if (cursor.positionToKey(toKey(dn)))
          {
            // Construct a set of all the labeled URIs in the referral.
            Collection<String> labeledURIs = decode(cursor.getValue());
            throwReferralException(targetDN, dn, labeledURIs, searchScope);
          }
        }
      }
      finally
      {
        cursor.close();
      }
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
    }
  }

  /**
   * Return search result references for a search operation using the referral
   * database to find all referral entries within scope of the search.
   * @param searchOp The search operation for which search result references
   * should be returned.
   * @return  <CODE>true</CODE> if the caller should continue processing the
   *          search request and sending additional entries and references, or
   *          <CODE>false</CODE> if not for some reason (e.g., the size limit
   *          has been reached or the search has been abandoned).
   * @throws DirectoryException If a Directory Server error occurs.
   */
  public boolean returnSearchReferences(ReadableStorage txn, SearchOperation searchOp)
       throws DirectoryException
  {
    if (containsReferrals == ConditionResult.UNDEFINED)
    {
      containsReferrals = containsReferrals(txn);
    }

    if (containsReferrals == ConditionResult.FALSE)
    {
      return true;
    }

    /*
     * We will iterate forwards through a range of the keys to
     * find subordinates of the base entry from the top of the tree downwards.
     */
    ByteString baseDN = toKey(searchOp.getBaseDN());
    ByteStringBuilder suffix = new ByteStringBuilder(baseDN.length() + 1);
    suffix.append(baseDN);
    ByteStringBuilder end = new ByteStringBuilder(suffix);

    /*
     * Set the ending value to a value of equal length but slightly
     * greater than the suffix. Since keys are compared in
     * reverse order we must set the first byte (the comma).
     * No possibility of overflow here.
     */
    suffix.append((byte) 0x00);
    end.append((byte) 0x01);

    ByteSequence startKey = suffix;
    try
    {
      final Cursor cursor = txn.openCursor(treeName);
      try
      {
        // Initialize the cursor very close to the starting value then
        // step forward until we pass the ending value.
        boolean success = cursor.positionToKey(startKey);
        while (success)
        {
          ByteString key = cursor.getKey();
          int cmp = ByteSequence.COMPARATOR.compare(key, end);
          if (cmp >= 0)
          {
            // We have gone past the ending value.
            break;
          }

          // We have found a subordinate referral.
          DN dn = JebFormat.dnFromDNKey(key, entryContainer.getBaseDN());

          // Make sure the referral is within scope.
          if (searchOp.getScope() == SearchScope.SINGLE_LEVEL
              && JebFormat.findDNKeyParent(key) != baseDN.length())
          {
            continue;
          }

          // Construct a list of all the URIs in the referral.
          Collection<String> labeledURIs = decode(cursor.getValue());
          SearchResultReference reference = toSearchResultReference(dn, labeledURIs, searchOp.getScope());
          if (!searchOp.returnReference(dn, reference))
          {
            return false;
          }
          success = cursor.next();
        }
      }
      finally
      {
        cursor.close();
      }
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
    }

    return true;
  }

  private SearchResultReference toSearchResultReference(DN dn, Collection<String> labeledURIs, SearchScope scope)
  {
    ArrayList<String> URIList = new ArrayList<String>(labeledURIs.size());
    for (String labeledURI : labeledURIs)
    {
      // Remove the label part of the labeled URI if there is a label.
      String uri = labeledURI;
      int i = labeledURI.indexOf(' ');
      if (i != -1)
      {
        uri = labeledURI.substring(0, i);
      }

      // From RFC 3296 section 5.4:
      // If the URI component is not a LDAP URL, it should be returned as
      // is.  If the LDAP URL's DN part is absent or empty, the DN part
      // must be modified to contain the DN of the referral object.  If
      // the URI component is a LDAP URL, the URI SHOULD be modified to
      // add an explicit scope specifier.
      try
      {
        LDAPURL ldapurl = LDAPURL.decode(uri, false);

        if ("ldap".equalsIgnoreCase(ldapurl.getScheme()))
        {
          if (ldapurl.getBaseDN().isRootDN())
          {
            ldapurl.setBaseDN(dn);
          }
          ldapurl.getAttributes().clear();
          if (scope == SearchScope.SINGLE_LEVEL)
          {
            ldapurl.setScope(SearchScope.BASE_OBJECT);
          }
          else
          {
            ldapurl.setScope(SearchScope.WHOLE_SUBTREE);
          }
          ldapurl.setFilter(null);
          uri = ldapurl.toString();
        }
      }
      catch (DirectoryException e)
      {
        logger.traceException(e);
        // Return the non-LDAP URI as is.
      }

      URIList.add(uri);
    }
    return new SearchResultReference(URIList);
  }

  private ByteString toKey(DN dn)
  {
    return JebFormat.dnToDNKey(dn, prefixRDNComponents);
  }
}
