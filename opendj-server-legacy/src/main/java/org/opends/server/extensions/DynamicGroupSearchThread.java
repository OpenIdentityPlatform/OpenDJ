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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.api.DirectoryThread;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchListener;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import static org.opends.server.protocols.internal.Requests.*;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.MembershipException;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchResultReference;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;

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
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The set of base DNs for the search requests. */
  private final DN[] baseDNs;

  /** The member list with which this search thread is associated. */
  private final DynamicGroupMemberList memberList;

  /** A counter used to keep track of which search is currently in progress. */
  private int searchCounter;

  /** The set of member URLs for determining whether entries match the criteria. */
  private final LDAPURL[][] memberURLs;

  /** The set of search filters for the search requests. */
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

  /** Performs the set of searches and provides the results to the associated member list. */
  @Override
  public void run()
  {
    InternalClientConnection conn = getRootConnection();
    for (searchCounter = 0; searchCounter < baseDNs.length; searchCounter++)
    {
      DN baseDN = baseDNs[searchCounter];
      SearchFilter filter = searchFilters[searchCounter];
      // Include all the user attributes along with the ismemberof.
      final SearchRequest request = newSearchRequest(baseDN, SearchScope.WHOLE_SUBTREE, filter)
          .addAttribute("*", "ismemberof");
      InternalSearchOperation searchOperation = conn.processSearch(request, this);

      ResultCode resultCode = searchOperation.getResultCode();
      if (resultCode != ResultCode.SUCCESS)
      {
        if (resultCode == ResultCode.NO_SUCH_OBJECT)
        {
          logger.warn(WARN_DYNAMICGROUP_NONEXISTENT_BASE_DN, baseDN,
                  memberList.getDynamicGroupDN());
          continue;
        }
        else
        {
          LocalizableMessage message =
               ERR_DYNAMICGROUP_INTERNAL_SEARCH_FAILED.get(
                       baseDN,
                       filter,
                       memberList.getDynamicGroupDN(),
                       resultCode,
                       searchOperation.getErrorMessage());
          if (! memberList.addResult(
                     new MembershipException(message, true)))
          {
            memberList.setSearchesCompleted();
            return;
          }
        }
      }
    }

    memberList.setSearchesCompleted();
  }

  @Override
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
          LocalizableMessage message = ERR_DYNAMICGROUP_CANNOT_RETURN_ENTRY.
              get(searchEntry.getName(), memberList.getDynamicGroupDN());
          throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message);
        }

        return;
      }
    }
  }

  @Override
  public void handleInternalSearchReference(
                   InternalSearchOperation searchOperation,
                   SearchResultReference searchReference)
  {
    // No implementation required.
  }
}
