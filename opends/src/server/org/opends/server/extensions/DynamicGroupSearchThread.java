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
package org.opends.server.extensions;



import java.util.LinkedHashSet;

import org.opends.server.api.DirectoryThread;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchListener;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.MembershipException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;
import org.opends.server.types.SearchScope;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class implements a Directory Server thread that will be used to perform
 * a background search to retrieve all of the members of a dynamic group.
 * <BR><BR>
 */
public class DynamicGroupSearchThread
// FIXME -- Would it be better to implement this class using an Executor
//          rather than always creating a custom thread?
       extends DirectoryThread
       implements InternalSearchListener
{
  // The set of base DNs for the search requests.
  private final DN[] baseDNs;

  // The member list with which this search thread is associated.
  private final DynamicGroupMemberList memberList;

  // A counter used to keep track of which search is currently in progress.
  private int searchCounter;

  // The set of member URLs for determining whether entries match the criteria.
  private final LDAPURL[][] memberURLs;

  // The set of search filters for the search requests.
  private final SearchFilter[] searchFilters;



  /**
   * Creates a new dynamic group search thread that is associated with the
   * provided member list and that will perform the search using the provided
   * information.
   *
   * @param  memberList  The dynamic group member list with which this thread is
   *                     associated.
   * @param  baseDNs     The set of base DNs to use for the search requests.
   * @param  filters     The set of search filters to use for the search
   *                     requests.
   * @param  memberURLs  The set of member URLs to use when determining if
   *                     entries match the necessary group criteria.
   */
  public DynamicGroupSearchThread(DynamicGroupMemberList memberList,
                                  DN[] baseDNs, SearchFilter[] filters,
                                  LDAPURL[][] memberURLs)
  {
    super("Dynamic Group Search Thread " + memberList.getDynamicGroupDN());

    this.memberList    = memberList;
    this.baseDNs       = baseDNs;
    this.searchFilters = filters;
    this.memberURLs    = memberURLs;

    searchCounter = 0;
  }



  /**
   * Performs the set of searches and provides the results to the associated
   * member list.
   */
  public void run()
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    LinkedHashSet<String> attributes = new LinkedHashSet<String>(0);

    for (searchCounter = 0; searchCounter < baseDNs.length; searchCounter++)
    {
      InternalSearchOperation searchOperation =
           conn.processSearch(baseDNs[searchCounter], SearchScope.WHOLE_SUBTREE,
                              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0,
                              false, searchFilters[searchCounter], attributes,
                              this);

      ResultCode resultCode = searchOperation.getResultCode();
      if (resultCode != ResultCode.SUCCESS)
      {
        if (resultCode == ResultCode.NO_SUCH_OBJECT)
        {
          int    msgID   = MSGID_DYNAMICGROUP_NONEXISTENT_BASE_DN;
          String message =
               getMessage(msgID, String.valueOf(baseDNs[searchCounter]),
                          String.valueOf(memberList.getDynamicGroupDN()));
          logError(ErrorLogCategory.EXTENSIONS, ErrorLogSeverity.MILD_WARNING,
                   message, msgID);
          continue;
        }
        else
        {
          int    msgID   = MSGID_DYNAMICGROUP_INTERNAL_SEARCH_FAILED;
          String message =
               getMessage(msgID, String.valueOf(baseDNs[searchCounter]),
                          String.valueOf(searchFilters[searchCounter]),
                          String.valueOf(memberList.getDynamicGroupDN()),
                          String.valueOf(resultCode),
                          String.valueOf(searchOperation.getErrorMessage()));
          if (! memberList.addResult(
                     new MembershipException(msgID, message, true)))
          {
            memberList.setSearchesCompleted();
            return;
          }
        }
      }
    }

    memberList.setSearchesCompleted();
  }



  /**
   * {@inheritDoc}
   */
  public void handleInternalSearchEntry(InternalSearchOperation searchOperation,
                                        SearchResultEntry searchEntry)
         throws DirectoryException
  {
    for (LDAPURL url : memberURLs[searchCounter])
    {
      if (url.matchesEntry(searchEntry))
      {
        if (! memberList.addResult(searchEntry))
        {
          int msgID = MSGID_DYNAMICGROUP_CANNOT_RETURN_ENTRY;
          String message = getMessage(msgID,
                                String.valueOf(searchEntry.getDN()),
                                String.valueOf(memberList.getDynamicGroupDN()));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(), message,
                         msgID);
        }

        return;
      }
    }
  }



  /**
   * {@inheritDoc}
   */
  public void handleInternalSearchReference(
                   InternalSearchOperation searchOperation,
                   SearchResultReference searchReference)
  {
    // No implementation required.
  }
}

