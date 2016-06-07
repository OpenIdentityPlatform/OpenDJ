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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.backends.pluggable;

import static org.forgerock.opendj.ldap.SearchScope.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.pluggable.DnKeyFormat.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.backends.pluggable.spi.Cursor;
import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.UpdateFunction;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.Modification;
import org.opends.server.types.SearchResultReference;
import org.opends.server.util.StaticUtils;

/**
 * This class represents the referral tree which contains URIs from referral
 * entries.
 * <p>
 * The key is the DN of the referral entry and the value is that of a pair
 * (DN, list of labeled URI in the ref attribute for that entry). The DN must be
 * duplicated in the value because the key is suitable for comparisons but is
 * not reversible to a valid DN. Duplicate keys are permitted since a referral
 * entry can contain multiple values of the ref attribute. Key order is the same
 * as in dn2id so that all referrals in a subtree can be retrieved by cursoring
 * through a range of the records.
 */
class DN2URI extends AbstractTree
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private final int prefixRDNComponents;

  /**
   * The standard attribute type that is used to specify the set of referral
   * URLs in a referral entry.
   */
  private final AttributeType referralType = DirectoryServer.getSchema().getAttributeType(ATTR_REFERRAL_URL);

  /**
   * A flag that indicates whether there are any referrals contained in this
   * tree.  It should only be set to {@code false} when it is known that
   * there are no referrals.
   */
  private volatile ConditionResult containsReferrals = ConditionResult.UNDEFINED;

  /**
   * Create a new object representing a referral tree in a given
   * entryContainer.
   *
   * @param treeName
   *          The name of the referral tree.
   * @param entryContainer
   *          The entryContainer of the DN tree.
   * @throws StorageRuntimeException
   *           If an error occurs in the storage.
   */
  DN2URI(TreeName treeName, EntryContainer entryContainer) throws StorageRuntimeException
  {
    super(treeName);
    prefixRDNComponents = entryContainer.getBaseDN().size();
  }

  /** Encodes the value. */
  private ByteSequence encode(DN dn, Collection<String> col)
  {
    if (col != null && !col.isEmpty())
    {
      ByteStringBuilder b = new ByteStringBuilder();
      // encode the dn inside the value
      // because the dn is encoded in a non reversible way in the key
      byte[] dnBytes = StaticUtils.getBytes(dn.toString());
      b.appendInt(dnBytes.length);
      b.appendBytes(dnBytes);
      b.appendInt(col.size());
      for (String s : col)
      {
        byte[] bytes = StaticUtils.getBytes(s);
        b.appendInt(bytes.length);
        b.appendBytes(bytes);
      }
      return b;
    }
    return null;
  }

  /** Decodes the value as a pair where the first element is the DN key and the second is the actual value. */
  private Map.Entry<DN, List<String>> decode(ByteSequence bs) throws StorageRuntimeException
  {
    return decode0(bs, true);
  }

  private Collection<String> decodeUrisOnly(ByteSequence oldValue)
  {
    return decode0(oldValue, false).getValue();
  }

  private Map.Entry<DN, List<String>> decode0(ByteSequence bs, boolean decodeDN)
  {
    if (!bs.isEmpty())
    {
      ByteSequenceReader r = bs.asReader();
      DN dn = decodeDN(r, decodeDN);
      List<String> uris = decodeUris(r);
      return new SimpleImmutableEntry<>(dn, uris);
    }
    return new SimpleImmutableEntry<>(null, null);
  }

  private DN decodeDN(ByteSequenceReader r, boolean decodeDN)
  {
    final int dnLength = r.readInt();
    if (decodeDN)
    {
      try
      {
        return DN.valueOf(r.readStringUtf8(dnLength));
      }
      catch (LocalizedIllegalArgumentException e)
      {
        throw new StorageRuntimeException("Unable to decode DN from binary value", e);
      }
    }

    r.skip(dnLength);
    return null;
  }

  private List<String> decodeUris(ByteSequenceReader r)
  {
    final int nbElems = r.readInt();
    List<String> results = new ArrayList<>(nbElems);
    for (int i = 0; i < nbElems; i++)
    {
      final int stringLength = r.readInt();
      results.add(r.readStringUtf8(stringLength));
    }
    return results;
  }

  /**
   * Puts a URI value in the referral tree.
   *
   * @param txn a non null transaction
   * @param dn The DN of the referral entry.
   * @param labeledURIs The labeled URI value of the ref attribute.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  private void update(final WriteableTransaction txn, final DN dn, final Collection<String> labeledURIs)
      throws StorageRuntimeException
  {
    final ByteString key = toKey(dn);
    txn.update(getName(), key, new UpdateFunction()
    {
      @Override
      public ByteSequence computeNewValue(ByteSequence oldValue)
      {
        if (oldValue != null)
        {
          final Collection<String> newUris = decodeUrisOnly(oldValue);
          if (newUris.addAll(labeledURIs))
          {
            return encode(dn, newUris);
          }
          return oldValue;
        }
        else
        {
          return encode(dn, labeledURIs);
        }
      }
    });
    containsReferrals = ConditionResult.TRUE;
  }

  /**
   * Delete URI values for a given referral entry from the referral tree.
   *
   * @param txn a non null transaction
   * @param dn The DN of the referral entry for which URI values are to be
   * deleted.
   * @return true if the values were deleted, false if not.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  private boolean delete(WriteableTransaction txn, DN dn) throws StorageRuntimeException
  {
    ByteString key = toKey(dn);

    if (txn.delete(getName(), key))
    {
      containsReferrals = containsReferrals(txn);
      return true;
    }
    return false;
  }

  /**
   * Delete a single URI value from the referral tree.
   * @param txn a non null transaction
   * @param dn The DN of the referral entry.
   * @param labeledURIs The URI value to be deleted.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  private void delete(final WriteableTransaction txn, final DN dn, final Collection<String> labeledURIs)
      throws StorageRuntimeException
  {
    ByteString key = toKey(dn);
    txn.update(getName(), key, new UpdateFunction()
    {
      @Override
      public ByteSequence computeNewValue(ByteSequence oldValue)
      {
        if (oldValue != null)
        {
          final Collection<String> oldUris = decodeUrisOnly(oldValue);
          if (oldUris.removeAll(labeledURIs))
          {
            return encode(dn, oldUris);
          }
        }
        return oldValue;
      }
    });
    containsReferrals = containsReferrals(txn);
  }

  /**
   * Indicates whether the underlying tree contains any referrals.
   *
   * @param  txn  The transaction to use when making the determination.
   * @return  {@code true} if it is believed that the underlying tree may
   *          contain at least one referral, or {@code false} if it is certain
   *          that it doesn't.
   */
  private ConditionResult containsReferrals(ReadableTransaction txn)
  {
    try (Cursor<?, ?> cursor = txn.openCursor(getName()))
    {
      return ConditionResult.valueOf(cursor.next());
    }
    catch (Exception e)
    {
      logger.traceException(e);

      return ConditionResult.UNDEFINED;
    }
  }

  /**
   * Update the referral tree for an entry that has been modified.  Does
   * not do anything unless the entry before the modification or the entry after
   * the modification is a referral entry.
   *
   * @param txn a non null transaction
   * @param before The entry before the modifications have been applied.
   * @param after The entry after the modifications have been applied.
   * @param mods The sequence of modifications made to the entry.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  void modifyEntry(WriteableTransaction txn, Entry before, Entry after, List<Modification> mods)
      throws StorageRuntimeException
  {
    DN entryDN = before.getName();
    for (Modification mod : mods)
    {
      Attribute modAttr = mod.getAttribute();
      AttributeType modAttrType = modAttr.getAttributeDescription().getAttributeType();
      if (modAttrType.equals(referralType))
      {
        Attribute a = mod.getAttribute();
        switch (mod.getModificationType().asEnum())
        {
          case ADD:
            if (a != null)
            {
              update(txn, entryDN, toStrings(a));
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
              update(txn, entryDN, toStrings(a));
            }
            break;
        }
      }
    }
  }

  private List<String> toStrings(Attribute a)
  {
    List<String> results = new ArrayList<>(a.size());
    for (ByteString v : a)
    {
      results.add(v.toString());
    }
    return results;
  }

  /**
   * Update the referral tree for an entry that has been replaced. Does not
   * do anything unless the entry before it was replaced or the entry after it
   * was replaced is a referral entry.
   *
   * @param txn
   *          A transaction used for the update, or null if none is
   *          required.
   * @param before
   *          The entry before it was replaced.
   * @param after
   *          The entry after it was replaced.
   * @throws StorageRuntimeException
   *           If an error occurs in the storage.
   */
  void replaceEntry(WriteableTransaction txn, Entry before, Entry after)
       throws StorageRuntimeException
  {
    deleteEntry(txn, before);
    addEntry(txn, after);
  }

  /**
   * Update the referral tree for a new entry. Does nothing if the entry
   * is not a referral entry.
   * @param txn a non null transaction
   * @param entry The entry to be added.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  void addEntry(WriteableTransaction txn, Entry entry)
       throws StorageRuntimeException
  {
    Set<String> labeledURIs = entry.getReferralURLs();
    if (labeledURIs != null)
    {
      update(txn, entry.getName(), labeledURIs);
    }
  }

  /**
   * Update the referral tree for a deleted entry. Does nothing if the entry
   * was not a referral entry.
   * @param txn a non null transaction
   * @param entry The entry to be deleted.
   * @throws StorageRuntimeException If an error occurs in the storage.
   */
  void deleteEntry(WriteableTransaction txn, Entry entry) throws StorageRuntimeException
  {
    Set<String> labeledURIs = entry.getReferralURLs();
    if (labeledURIs != null)
    {
      delete(txn, entry.getName());
    }
  }

  @Override
  public String keyToString(ByteString key)
  {
    return key.toString();
  }

  @Override public String valueToString(ByteString value)
  {
    final Map.Entry<DN, List<String>> dnAndUris = decode(value);
    return dnAndUris.getValue().toString();
  }

  @Override
  public ByteString generateKey(String key)
  {
    try
    {
      return toKey(DN.valueOf(key));
    }
    catch (LocalizedIllegalArgumentException e)
    {
      return ByteString.empty();
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
  void checkTargetForReferral(Entry entry, SearchScope searchScope) throws DirectoryException
  {
    Set<String> referralURLs = entry.getReferralURLs();
    if (referralURLs != null)
    {
      throwReferralException(entry.getName(), entry.getName(), referralURLs, searchScope);
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
  private void throwReferralException(DN targetDN, DN referralDN, Collection<String> labeledURIs,
      SearchScope searchScope) throws DirectoryException
  {
    ArrayList<String> URIList = new ArrayList<>(labeledURIs.size());
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
            urlBaseDN = targetDN.rename(referralDN, ldapurl.getBaseDN());
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
      catch (LocalizedIllegalArgumentException | DirectoryException e)
      {
        logger.traceException(e);
        // Return the non-LDAP URI as is.
      }

      URIList.add(uri);
    }

    // Throw a directory referral exception containing the URIs.
    throw new DirectoryException(
        ResultCode.REFERRAL, NOTE_REFERRAL_RESULT_MESSAGE.get(referralDN), referralDN, URIList, null);
  }

  /**
   * Process referral entries that are above the target DN of an operation.
   * @param txn a non null transaction
   * @param targetDN The target DN of the operation, or the base object of a
   * search operation.
   * @param searchScope The scope of the search operation, or null if the
   * operation is not a search operation.
   * @throws DirectoryException If a referral is found at or above the target
   * DN.  The referral URLs will be set appropriately for the references found
   * in the referral entry.
   */
  void targetEntryReferrals(ReadableTransaction txn, DN targetDN, SearchScope searchScope) throws DirectoryException
  {
    if (containsReferrals == ConditionResult.UNDEFINED)
    {
      containsReferrals = containsReferrals(txn);
    }

    if (containsReferrals == ConditionResult.FALSE)
    {
      return;
    }

    try (Cursor<ByteString, ByteString> cursor = txn.openCursor(getName()))
    {
      // Go up through the DIT hierarchy until we find a referral.
      for (DN dn = getParentWithinBase(targetDN); dn != null; dn = getParentWithinBase(dn))
      {
        // Look for a record whose key matches the current DN.
        if (cursor.positionToKey(toKey(dn)))
        {
          // Construct a set of all the labeled URIs in the referral.
          Collection<String> labeledURIs = decodeUrisOnly(cursor.getValue());
          throwReferralException(targetDN, dn, labeledURIs, searchScope);
        }
      }
    }
    catch (StorageRuntimeException e)
    {
      logger.traceException(e);
    }
  }

  private DN getParentWithinBase(DN targetDN)
  {
    return targetDN.size() == prefixRDNComponents ? null: targetDN.parent();
  }

  /**
   * Return search result references for a search operation using the referral
   * tree to find all referral entries within scope of the search.
   * @param txn a non null transaction
   * @param searchOp The search operation for which search result references
   * should be returned.
   * @return  <CODE>true</CODE> if the caller should continue processing the
   *          search request and sending additional entries and references, or
   *          <CODE>false</CODE> if not for some reason (e.g., the size limit
   *          has been reached or the search has been abandoned).
   * @throws DirectoryException If a Directory Server error occurs.
   */
  boolean returnSearchReferences(ReadableTransaction txn, SearchOperation searchOp) throws DirectoryException
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
    ByteStringBuilder beforeFirstChild = beforeFirstChildOf(baseDN);
    ByteStringBuilder afterLastChild = afterLastChildOf(baseDN);

    try (Cursor<ByteString, ByteString> cursor = txn.openCursor(getName()))
    {
      // Initialize the cursor very close to the starting value then
      // step forward until we pass the ending value.
      boolean success = cursor.positionToKeyOrNext(beforeFirstChild);
      while (success && cursor.getKey().compareTo(afterLastChild) < 0)
      {
        // We have found a subordinate referral.
        // Make sure the referral is within scope.
        if (searchOp.getScope() == SINGLE_LEVEL
            && DnKeyFormat.findDNKeyParent(cursor.getKey()) != baseDN.length())
        {
          success = cursor.next();
          continue;
        }

        // Construct a list of all the URIs in the referral.
        final Map.Entry<DN, List<String>> dnAndUris = decode(cursor.getValue());
        final DN dn = dnAndUris.getKey();
        final Collection<String> labeledURIs = dnAndUris.getValue();
        SearchResultReference reference = toSearchResultReference(dn, labeledURIs, searchOp.getScope());
        if (!searchOp.returnReference(dn, reference))
        {
          return false;
        }
        success = cursor.next();
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
    ArrayList<String> URIList = new ArrayList<>(labeledURIs.size());
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
          ldapurl.setScope(scope == SINGLE_LEVEL ? BASE_OBJECT : WHOLE_SUBTREE);
          ldapurl.setFilter(null);
          uri = ldapurl.toString();
        }
      }
      catch (LocalizedIllegalArgumentException | DirectoryException e)
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
    return DnKeyFormat.dnToDNKey(dn, prefixRDNComponents);
  }
}
