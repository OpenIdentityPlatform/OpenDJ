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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;


import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.Modification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;
import org.opends.server.util.StaticUtils;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.opends.server.util.ServerConstants.ATTR_REFERRAL_URL;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.JebMessages.
     MSGID_JEB_REFERRAL_RESULT_MESSAGE;
import static org.opends.server.messages.MessageHandler.getMessage;

/**
 * This class represents the referral database which contains URIs from referral
 * entries.  The key is the DN of the referral entry and the value is that of a
 * labeled URI in the ref attribute for that entry. Duplicate keys are permitted
 * since a referral entry can contain multiple values of the ref attribute.  Key
 * order is the same as in the DN database so that all referrals in a subtree
 * can be retrieved by cursoring through a range of the records.
 */
public class DN2URI
{

  /**
   * The standard attribute type that is used to specify the set of referral
   * URLs in a referral entry.
   */
  public AttributeType referralType =
       DirectoryServer.getAttributeType(ATTR_REFERRAL_URL);

  /**
   * The database entryContainer.
   */
  private EntryContainer entryContainer;

  /**
   * The JE database configuration.
   */
  private DatabaseConfig dbConfig;

  /**
   * The name of the database within the entryContainer.
   */
  private String name;

  /**
   * A custom btree key comparator for the JE database.
   */
  Comparator<byte[]> comparator = new EntryContainer.KeyReverseComparator();

  /**
   * A cached per-thread JE database handle.
   */
  private ThreadLocal<Database> threadLocalDatabase =
       new ThreadLocal<Database>();

  /**
   * Create a new object representing a referral database in a given
   * entryContainer.
   *
   * @param entryContainer The entryContainer of the referral database.
   * @param dbConfig The JE database configuration which will be used to
   * open the database.
   * @param name The name of the referral database.
   */
  public DN2URI(EntryContainer entryContainer, DatabaseConfig dbConfig,
                String name)
  {
    this.entryContainer = entryContainer;
    this.dbConfig = dbConfig;
    this.name = name;
  }

  /**
   * Open the referral database.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void open() throws DatabaseException
  {
    getDatabase();
  }

  /**
   * Get a handle to the database. It returns a per-thread handle to avoid
   * any thread contention on the database handle. The entryContainer is
   * responsible for closing all handles.
   *
   * @return A database handle.
   *
   * @throws DatabaseException If an error occurs in the JE database.
   */
  private Database getDatabase() throws DatabaseException
  {
    Database database = threadLocalDatabase.get();
    if (database == null)
    {
      database = entryContainer.openDatabase(dbConfig, name);
      threadLocalDatabase.set(database);
    }
    return database;
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
  public boolean insert(Transaction txn, DN dn, String labeledURI)
       throws DatabaseException
  {
    byte[] normDN = StaticUtils.getBytes(dn.toNormalizedString());
    byte[] URIBytes = StaticUtils.getBytes(labeledURI);
    DatabaseEntry key = new DatabaseEntry(normDN);
    DatabaseEntry data = new DatabaseEntry(URIBytes);
    OperationStatus status;

    // The JE insert method does not permit duplicate keys so we must use the
    // put method.
    status = EntryContainer.put(getDatabase(), txn, key, data);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
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
  public boolean delete(Transaction txn, DN dn)
       throws DatabaseException
  {
    byte[] normDN = StaticUtils.getBytes(dn.toNormalizedString());
    DatabaseEntry key = new DatabaseEntry(normDN);
    OperationStatus status;

    status = EntryContainer.delete(getDatabase(), txn, key);
    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
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
  public boolean delete(Transaction txn, DN dn, String labeledURI)
       throws DatabaseException
  {
    CursorConfig cursorConfig = null;
    byte[] normDN = StaticUtils.getBytes(dn.toNormalizedString());
    byte[] URIBytes = StaticUtils.getBytes(labeledURI);
    DatabaseEntry key = new DatabaseEntry(normDN);
    DatabaseEntry data = new DatabaseEntry(URIBytes);
    OperationStatus status;

    Cursor cursor = getDatabase().openCursor(txn, cursorConfig);
    try
    {
      status = cursor.getSearchBoth(key, data, null);
      if (status == OperationStatus.SUCCESS)
      {
        status = cursor.delete();
      }
    }
    finally
    {
      cursor.close();
    }

    if (status != OperationStatus.SUCCESS)
    {
      return false;
    }
    return true;
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
  public void modifyEntry(Transaction txn, Entry before, Entry after,
                          List<Modification> mods)
       throws DatabaseException
  {
    DN entryDN = before.getDN();
    for (Modification mod : mods)
    {
      Attribute modAttr = mod.getAttribute();
      AttributeType modAttrType = modAttr.getAttributeType();
      if (modAttrType.equals(referralType))
      {
        Attribute a = mod.getAttribute();
        switch (mod.getModificationType())
        {
          case ADD:
            if (a != null)
            {
              for (AttributeValue v : a.getValues())
              {
                insert(txn, entryDN, v.getStringValue());
              }
            }
            break;

          case DELETE:
            if (a == null || !a.hasValue())
            {
              delete(txn, entryDN);
            }
            else
            {
              for (AttributeValue v : a.getValues())
              {
                delete(txn, entryDN, v.getStringValue());
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
              for (AttributeValue v : a.getValues())
              {
                insert(txn, entryDN, v.getStringValue());
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
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void addEntry(Transaction txn, Entry entry)
       throws DatabaseException
  {
    Set<String> labeledURIs = entry.getReferralURLs();
    if (labeledURIs != null)
    {
      DN dn = entry.getDN();
      for (String labeledURI : labeledURIs)
      {
        insert(txn, dn, labeledURI);
      }
    }
  }

  /**
   * Update the referral database for a deleted entry. Does nothing if the entry
   * was not a referral entry.
   * @param txn A database transaction used for the update, or null if none is
   * required.
   * @param entry The entry to be deleted.
   * @throws DatabaseException If an error occurs in the JE database.
   */
  public void deleteEntry(Transaction txn, Entry entry)
       throws DatabaseException
  {
    Set<String> labeledURIs = entry.getReferralURLs();
    if (labeledURIs != null)
    {
      delete(txn, entry.getDN());
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
      throwReferralException(entry.getDN(), entry.getDN(), referralURLs,
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
  public void throwReferralException(DN targetDN, DN referralDN,
                                     Set<String> labeledURIs,
                                     SearchScope searchScope)
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

        if (ldapurl.getScheme().equalsIgnoreCase("ldap"))
        {
          DN urlBaseDN = targetDN;
          if (!referralDN.equals(ldapurl.getBaseDN()))
          {
            urlBaseDN =
                 EntryContainer.modDN(targetDN,
                                      referralDN.getNumComponents(),
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
        if (debugEnabled())
        {
          debugCought(DebugLogLevel.ERROR, e);
        }
        // Return the non-LDAP URI as is.
      }

      URIList.add(uri);
    }

    // Throw a directory referral exception containing the URIs.
    int msgID = MSGID_JEB_REFERRAL_RESULT_MESSAGE;
    String msg = getMessage(msgID, referralDN);
    throw new DirectoryException(ResultCode.REFERRAL, msg, msgID,
                                 referralDN, URIList, null);
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
  public void targetEntryReferrals(DN targetDN, SearchScope searchScope)
       throws DirectoryException
  {
    Transaction txn = null;
    CursorConfig cursorConfig = null;

    try
    {
      Cursor cursor = getDatabase().openCursor(txn, cursorConfig);
      try
      {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();

        // Go up through the DIT hierarchy until we find a referral.
        for (DN dn = entryContainer.getParentWithinBase(targetDN); dn != null;
             dn = entryContainer.getParentWithinBase(dn))
        {
          // Look for a record whose key matches the current DN.
          String normDN = dn.toNormalizedString();
          key.setData(StaticUtils.getBytes(normDN));
          OperationStatus status =
             cursor.getSearchKey(key, data, LockMode.DEFAULT);
          if (status == OperationStatus.SUCCESS)
          {
            // Construct a set of all the labeled URIs in the referral.
            Set<String> labeledURIs =
                 new LinkedHashSet<String>(cursor.count());
            do
            {
              String labeledURI = new String(data.getData(), "UTF-8");
              labeledURIs.add(labeledURI);
              status = cursor.getNextDup(key, data, LockMode.DEFAULT);
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
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
    }
    catch (UnsupportedEncodingException e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
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
  public boolean returnSearchReferences(SearchOperation searchOp)
       throws DirectoryException
  {
    Transaction txn = null;
    CursorConfig cursorConfig = null;

    /*
     * We will iterate forwards through a range of the keys to
     * find subordinates of the base entry from the top of the tree
     * downwards.
     */
    DN baseDN = searchOp.getBaseDN();
    String normBaseDN = baseDN.toNormalizedString();
    byte[] suffix = StaticUtils.getBytes("," + normBaseDN);

    /*
     * Set the ending value to a value of equal length but slightly
     * greater than the suffix. Since keys are compared in
     * reverse order we must set the first byte (the comma).
     * No possibility of overflow here.
     */
    byte[] end = suffix.clone();
    end[0] = (byte) (end[0] + 1);

    DatabaseEntry data = new DatabaseEntry();
    DatabaseEntry key = new DatabaseEntry(suffix);

    try
    {
      Cursor cursor = getDatabase().openCursor(txn, cursorConfig);
      try
      {
        // Initialize the cursor very close to the starting value then
        // step forward until we pass the ending value.
        for (OperationStatus status =
             cursor.getSearchKeyRange(key, data, LockMode.DEFAULT);
             status == OperationStatus.SUCCESS;
             status = cursor.getNextNoDup(key, data, LockMode.DEFAULT))
        {
          int cmp = comparator.compare(key.getData(), end);
          if (cmp >= 0)
          {
            // We have gone past the ending value.
            break;
          }

          // We have found a subordinate referral.
          DN dn = DN.decode(new ASN1OctetString(key.getData()));

          // Make sure the referral is within scope.
          if (searchOp.getScope() == SearchScope.SINGLE_LEVEL)
          {
            if ((dn.getNumComponents() !=
                 baseDN.getNumComponents() + 1))
            {
              continue;
            }
          }

          // Construct a list of all the URIs in the referral.
          ArrayList<String> URIList = new ArrayList<String>(cursor.count());
          do
          {
            // Remove the label part of the labeled URI if there is a label.
            String labeledURI = new String(data.getData(), "UTF-8");
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

              if (ldapurl.getScheme().equalsIgnoreCase("ldap"))
              {
                if (ldapurl.getBaseDN().isNullDN())
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
              if (debugEnabled())
              {
                debugCought(DebugLogLevel.ERROR, e);
              }
              // Return the non-LDAP URI as is.
            }

            URIList.add(uri);
            status = cursor.getNextDup(key, data, LockMode.DEFAULT);
          } while (status == OperationStatus.SUCCESS);

          SearchResultReference reference = new SearchResultReference(URIList);
          if (!searchOp.returnReference(reference))
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
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
    }
    catch (UnsupportedEncodingException e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }
    }

    return true;
  }
}
