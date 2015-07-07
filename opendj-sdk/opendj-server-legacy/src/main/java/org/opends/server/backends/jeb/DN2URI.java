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
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import static com.sleepycat.je.LockMode.*;
import static com.sleepycat.je.OperationStatus.*;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.backends.jeb.JebFormat.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.util.Pair;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.types.*;
import org.opends.server.util.StaticUtils;

import com.sleepycat.je.*;

/**
 * This class represents the referral database which contains URIs from referral
 * entries.
 * <p>
 * The key is the DN of the referral entry and the value is that of a pair
 * (labeled URI in the ref attribute for that entry, DN). The DN must be
 * duplicated in the value because the key is suitable for comparisons but is
 * not reversible to a valid DN. Duplicate keys are permitted since a referral
 * entry can contain multiple values of the ref attribute. Key order is the same
 * as in the DN database so that all referrals in a subtree can be retrieved by
 * cursoring through a range of the records.
 */
public class DN2URI extends DatabaseContainer
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final byte STRING_SEPARATOR = 0x00;

  /**
   * The key comparator used for the DN database.
   */
  private final Comparator<byte[]> dn2uriComparator;


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
   * @param name The name of the referral database.
   * @param env The JE environment.
   * @param entryContainer The entryContainer of the DN database.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  @SuppressWarnings("unchecked")
  DN2URI(String name, Environment env,
        EntryContainer entryContainer)
      throws DatabaseException
  {
    super(name, env, entryContainer);

    dn2uriComparator = new AttributeIndex.KeyComparator();
    prefixRDNComponents = entryContainer.getBaseDN().size();

    this.dbConfig = JEBUtils.toDatabaseConfigAllowDuplicates(env);
    this.dbConfig.setBtreeComparator((Class<? extends Comparator<byte[]>>)
                                  dn2uriComparator.getClass());
  }

  /**
   * Insert a URI value in the referral database.
   *
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param dn The DN of the referral entry.
   * @param labeledURI The labeled URI value of the ref attribute.
   * @return true if the record was inserted, false if it was not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private boolean insert(Transaction txn, DN dn, String labeledURI)
       throws DatabaseException
  {
    byte[] normDN = JebFormat.dnToDNKey(dn, prefixRDNComponents);
    DatabaseEntry key = new DatabaseEntry(normDN);
    DatabaseEntry data = new DatabaseEntry(encodeURIAndDN(labeledURI, dn));

    // The JE insert method does not permit duplicate keys so we must use the
    // put method.
    if (put(txn, key, data) == SUCCESS)
    {
      containsReferrals = ConditionResult.TRUE;
      return true;
    }
    return false;
  }

  private byte[] encodeURIAndDN(String labeledURI, DN dn)
  {
    return new ByteStringBuilder()
      .append(labeledURI)
      .append(STRING_SEPARATOR)
      .append(dn.toString())
      .toByteArray();
  }

  private Pair<String, DN> decodeURIAndDN(byte[] data) throws DirectoryException {
    try {
      final ByteSequenceReader reader = ByteString.valueOf(data).asReader();
      final String labeledURI = reader.getString(getNextStringLength(reader));
      // skip the string separator
      reader.skip(1);
      final DN dn = DN.valueOf(reader.getString(reader.remaining()));
      return Pair.of(labeledURI, dn);
    }
    catch (Exception e) {
       throw new DirectoryException(ResultCode.OPERATIONS_ERROR, ERR_DATABASE_EXCEPTION.get(e));
    }
  }

  /** Returns the length of next string by looking for the zero byte used as separator. */
  private int getNextStringLength(ByteSequenceReader reader)
  {
    int length = 0;
    while (reader.peek(length) != STRING_SEPARATOR)
    {
      length++;
    }
    return length;
  }

  /**
   * Delete URI values for a given referral entry from the referral database.
   *
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param dn The DN of the referral entry for which URI values are to be
   * deleted.
   * @return true if the values were deleted, false if not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  boolean delete(Transaction txn, DN dn) throws DatabaseException
  {
    byte[] normDN = JebFormat.dnToDNKey(dn, prefixRDNComponents);
    DatabaseEntry key = new DatabaseEntry(normDN);

    if (delete(txn, key) == SUCCESS)
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
   * @param labeledURI The URI value to be deleted.
   * @return true if the value was deleted, false if not.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private boolean delete(Transaction txn, DN dn, String labeledURI) throws DatabaseException
  {
    CursorConfig cursorConfig = null;
    byte[] normDN = JebFormat.dnToDNKey(dn, prefixRDNComponents);
    byte[] URIBytes = StaticUtils.getBytes(labeledURI);
    DatabaseEntry key = new DatabaseEntry(normDN);
    DatabaseEntry data = new DatabaseEntry(URIBytes);

    Cursor cursor = openCursor(txn, cursorConfig);
    try
    {
      OperationStatus status = cursor.getSearchBoth(key, data, null);
      if (status == OperationStatus.SUCCESS)
      {
        status = cursor.delete();
      }

      if (status == OperationStatus.SUCCESS)
      {
        containsReferrals = containsReferrals(txn);
        return true;
      }
      return false;
    }
    finally
    {
      cursor.close();
    }
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
  private ConditionResult containsReferrals(Transaction txn)
  {
    try
    {
      Cursor cursor = openCursor(txn, null);
      DatabaseEntry key  = new DatabaseEntry();
      DatabaseEntry data = new DatabaseEntry();

      OperationStatus status = cursor.getFirst(key, data, null);
      cursor.close();

      if (status == OperationStatus.SUCCESS)
      {
        return ConditionResult.TRUE;
      }
      else if (status == OperationStatus.NOTFOUND)
      {
        return ConditionResult.FALSE;
      }
      else
      {
        return ConditionResult.UNDEFINED;
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      return ConditionResult.UNDEFINED;
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
   * @throws DatabaseException If an error occurs in the JE database.
   */
  void modifyEntry(Transaction txn, Entry before, Entry after, List<Modification> mods)
       throws DatabaseException
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
              for (ByteString v : a)
              {
                insert(txn, entryDN, v.toString());
              }
            }
            break;

          case DELETE:
            if (a == null || a.isEmpty())
            {
              delete(txn, entryDN);
            }
            else
            {
              for (ByteString v : a)
              {
                delete(txn, entryDN, v.toString());
              }
            }
            break;

          case INCREMENT:
            // Nonsensical.
            break;

          case REPLACE:
            delete(txn, entryDN);
            if (a != null)
            {
              for (ByteString v : a)
              {
                insert(txn, entryDN, v.toString());
              }
            }
            break;
        }
      }
    }
  }

  /**
   * Update the referral database for an entry that has been replaced.  Does
   * not do anything unless the entry before it was replaced or the entry after
   * it was replaced is a referral entry.
   *
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param before The entry before it was replaced.
   * @param after The entry after it was replaced.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void replaceEntry(Transaction txn, Entry before, Entry after)
       throws DatabaseException
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
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public boolean addEntry(Transaction txn, Entry entry)
       throws DatabaseException
  {
    boolean success = true;
    Set<String> labeledURIs = entry.getReferralURLs();
    if (labeledURIs != null)
    {
      DN dn = entry.getName();
      for (String labeledURI : labeledURIs)
      {
        if(!insert(txn, dn, labeledURI))
        {
          success = false;
        }
      }
    }
    return success;
  }

  /**
   * Update the referral database for a deleted entry. Does nothing if the entry
   * was not a referral entry.
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param entry The entry to be deleted.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  void deleteEntry(Transaction txn, Entry entry) throws DatabaseException
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
  void checkTargetForReferral(Entry entry, SearchScope searchScope) throws DirectoryException
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
  private void throwReferralException(DN targetDN, DN referralDN, Set<String> labeledURIs, SearchScope searchScope)
       throws DirectoryException
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
    LocalizableMessage msg = NOTE_REFERRAL_RESULT_MESSAGE.get(referralDN);
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
  void targetEntryReferrals(DN targetDN, SearchScope searchScope) throws DirectoryException
  {
    if (containsReferrals == ConditionResult.UNDEFINED)
    {
      containsReferrals = containsReferrals(null);
    }

    if (containsReferrals == ConditionResult.FALSE)
    {
      return;
    }

    Transaction txn = null;
    CursorConfig cursorConfig = null;

    try
    {
      Cursor cursor = openCursor(txn, cursorConfig);
      try
      {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        // Go up through the DIT hierarchy until we find a referral.
        for (DN dn = entryContainer.getParentWithinBase(targetDN); dn != null;
             dn = entryContainer.getParentWithinBase(dn))
        {
          // Look for a record whose key matches the current DN.
          key.setData(JebFormat.dnToDNKey(dn, prefixRDNComponents));
          OperationStatus status = cursor.getSearchKey(key, data, DEFAULT);
          if (status == OperationStatus.SUCCESS)
          {
            // Construct a set of all the labeled URIs in the referral.
            Set<String> labeledURIs = new LinkedHashSet<>(cursor.count());
            do
            {
              final Pair<String, DN> uriAndDN = decodeURIAndDN(data.getData());
              final String labeledURI = uriAndDN.getFirst();
              labeledURIs.add(labeledURI);
              status = cursor.getNextDup(key, data, DEFAULT);
            } while (status == OperationStatus.SUCCESS);

            throwReferralException(targetDN, dn, labeledURIs, searchScope);
          }
        }
      }
      finally
      {
        cursor.close();
      }
    }
    catch (DatabaseException e)
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
  boolean returnSearchReferences(SearchOperation searchOp) throws DirectoryException
  {
    if (containsReferrals == ConditionResult.UNDEFINED)
    {
      containsReferrals = containsReferrals(null);
    }

    if (containsReferrals == ConditionResult.FALSE)
    {
      return true;
    }

    Transaction txn = null;
    CursorConfig cursorConfig = null;

    /*
     * We will iterate forwards through a range of the keys to
     * find subordinates of the base entry from the top of the tree
     * downwards.
     */
    byte[] baseDN = JebFormat.dnToDNKey(searchOp.getBaseDN(), prefixRDNComponents);
    final byte special = 0x00;
    byte[] suffix = Arrays.copyOf(baseDN, baseDN.length+1);
    suffix[suffix.length - 1] = special;
    byte[] end = Arrays.copyOf(suffix, suffix.length);
    end[end.length - 1] = special + 1;

    /*
     * Set the ending value to a value of equal length but slightly
     * greater than the suffix. Since keys are compared in
     * reverse order we must set the first byte (the comma).
     * No possibility of overflow here.
     */

    DatabaseEntry data = new DatabaseEntry();
    DatabaseEntry key = new DatabaseEntry(suffix);

    try
    {
      Cursor cursor = openCursor(txn, cursorConfig);
      try
      {
        // Initialize the cursor very close to the starting value then
        // step forward until we pass the ending value.
        for (OperationStatus status =
             cursor.getSearchKeyRange(key, data, DEFAULT);
             status == OperationStatus.SUCCESS;
             status = cursor.getNextNoDup(key, data, DEFAULT))
        {
          int cmp = dn2uriComparator.compare(key.getData(), end);
          if (cmp >= 0)
          {
            // We have gone past the ending value.
            break;
          }

          // We have found a subordinate referral.
          final Pair<String, DN> uriAndDN = decodeURIAndDN(data.getData());
          final String labeledURI = uriAndDN.getFirst();
          final DN dn = uriAndDN.getSecond();

          // Make sure the referral is within scope.
          if (searchOp.getScope() == SearchScope.SINGLE_LEVEL
              && findDNKeyParent(key.getData()) != baseDN.length)
          {
            continue;
          }

          // Construct a list of all the URIs in the referral.
          ArrayList<String> URIList = new ArrayList<>(cursor.count());
          do
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
                if (searchOp.getScope() == SearchScope.SINGLE_LEVEL)
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
            status = cursor.getNextDup(key, data, DEFAULT);
          } while (status == OperationStatus.SUCCESS);

          SearchResultReference reference = new SearchResultReference(URIList);
          if (!searchOp.returnReference(dn, reference))
          {
            return false;
          }
        }
      }
      finally
      {
        cursor.close();
      }
    }
    catch (DatabaseException e)
    {
      logger.traceException(e);
    }

    return true;
  }
}
