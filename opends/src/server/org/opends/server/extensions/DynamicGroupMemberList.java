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



import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPURL;
import org.opends.server.types.MemberList;
import org.opends.server.types.MembershipException;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;



/**
 * This class defines a mechanism that may be used to iterate over the
 * members of a dynamic group, optionally using an additional set of
 * criteria to further filter the results.
 */
public class DynamicGroupMemberList
       extends MemberList
{
  // Indicates whether the search thread has completed its processing.
  private boolean searchesCompleted;

  // The base DN to use when filtering the set of group members.
  private final DN baseDN;

  // The DN of the entry containing the group definition.
  private final DN groupDN;

  // The queue into which results will be placed while they are waiting to be
  // returned.  The types of objects that may be placed in this queue are Entry
  // objects to return or MembershipException objects to throw.
  private final LinkedBlockingQueue<Object> resultQueue;

  // The search filter to use when filtering the set of group members.
  private final SearchFilter filter;

  // The search scope to use when filtering the set of group members.
  private final SearchScope scope;

  // The set of LDAP URLs that define the membership criteria.
  private final Set<LDAPURL> memberURLs;



  /**
   * Creates a new dynamic group member list with the provided information.
   *
   * @param  groupDN     The DN of the entry containing the group definition.
   * @param  memberURLs  The set of LDAP URLs that define the membership
   *                     criteria for the associated group.
   *
   * @throws  DirectoryException  If a problem occurs while creating the member
   *                              list.
   */
  public DynamicGroupMemberList(DN groupDN, Set<LDAPURL> memberURLs)
         throws DirectoryException
  {
    this(groupDN, memberURLs, null, null, null);
  }



  /**
   * Creates a new dynamic group member list with the provided information.
   *
   * @param  groupDN     The DN of the entry containing the group definition.
   * @param  memberURLs  The set of LDAP URLs that define the membership
   *                     criteria for the associated group.
   * @param  baseDN      The base DN that should be enforced for all entries to
   *                     return.
   * @param  scope       The scope that should be enforced for all entries to
   *                     return.
   * @param  filter      The filter that should be enforced for all entries to
   *                     return.
   *
   * @throws  DirectoryException  If a problem occurs while creating the member
   *                              list.
   */
  public DynamicGroupMemberList(DN groupDN, Set<LDAPURL> memberURLs,
                                DN baseDN, SearchScope scope,
                                SearchFilter filter)
         throws DirectoryException
  {
    this.groupDN    = groupDN;
    this.memberURLs = memberURLs;
    this.baseDN     = baseDN;
    this.filter     = filter;

    if (scope == null)
    {
      this.scope = SearchScope.WHOLE_SUBTREE;
    }
    else
    {
      this.scope = scope;
    }

    searchesCompleted = false;
    resultQueue = new LinkedBlockingQueue<Object>(10);


    // We're going to have to perform one or more internal searches in order to
    // get the results.  We need to be careful about the way that we construct
    // them in order to avoid the possibility of getting duplicate results, so
    // searches with overlapping bases will need to be combined.
    LinkedHashMap<DN,LinkedList<LDAPURL>> baseDNs =
         new LinkedHashMap<DN,LinkedList<LDAPURL>>();
    for (LDAPURL memberURL : memberURLs)
    {
      // First, determine the base DN for the search.  It needs to be evaluated
      // as relative to both the overall base DN specified in the set of
      // criteria, as well as any other existing base DNs in the same hierarchy.
      DN urlBaseDN = memberURL.getBaseDN();
      if (baseDN != null)
      {
        if (baseDN.isDescendantOf(urlBaseDN))
        {
          // The base DN requested by the user is below the base DN for this
          // URL, so we'll use the base DN requested by the user.
          urlBaseDN = baseDN;
        }
        else if (! urlBaseDN.isDescendantOf(baseDN))
        {
          // The base DN from the URL is outside the base requested by the user,
          // so we can skip this URL altogether.
          continue;
        }
      }

      // If this is the first URL, then we can just add it with the base DN.
      // Otherwise, we need to see if it needs to be merged with other URLs in
      // the same hierarchy.
      if (baseDNs.isEmpty())
      {
        LinkedList<LDAPURL> urlList = new LinkedList<LDAPURL>();
        urlList.add(memberURL);
        baseDNs.put(urlBaseDN, urlList);
      }
      else
      {
        // See if the specified base DN is already in the map.  If so, then
        // just add the new URL to the existing list.
        LinkedList<LDAPURL> urlList = baseDNs.get(urlBaseDN);
        if (urlList == null)
        {
          // There's no existing list for the same base DN, but there might be
          // DNs in an overlapping hierarchy.  If so, then use the base DN that
          // is closest to the naming context.  If not, then add a new list with
          // the current base DN.
          boolean found = false;
          Iterator<DN> iterator = baseDNs.keySet().iterator();
          while (iterator.hasNext())
          {
            DN existingBaseDN = iterator.next();
            if (urlBaseDN.isDescendantOf(existingBaseDN))
            {
              // The base DN for the current URL is below an existing base DN,
              // so we can just add this URL to the existing list and be done.
              urlList = baseDNs.get(existingBaseDN);
              urlList.add(memberURL);
              found = true;
              break;
            }
            else if (existingBaseDN.isDescendantOf(urlBaseDN))
            {
              // The base DN for the current URL is above the existing base DN,
              // so we should use the base DN for the current URL instead of the
              // existing one.
              urlList = baseDNs.get(existingBaseDN);
              urlList.add(memberURL);
              iterator.remove();
              baseDNs.put(urlBaseDN, urlList);
              found = true;
              break;
            }
          }

          if (! found)
          {
            urlList = new LinkedList<LDAPURL>();
            urlList.add(memberURL);
            baseDNs.put(urlBaseDN, urlList);
          }
        }
        else
        {
          // There was already a list with the same base DN, so just add the
          // URL.
          urlList.add(memberURL);
        }
      }
    }


    // At this point, we should know what base DN(s) we need to use, so we can
    // create the filter to use with that base DN.  There are some special-case
    // optimizations that we can do here, but in general the filter will look
    // like "(&(filter)(|(urlFilters)))".
    LinkedHashMap<DN,SearchFilter> searchMap =
         new LinkedHashMap<DN,SearchFilter>();
    for (DN urlBaseDN : baseDNs.keySet())
    {
      LinkedList<LDAPURL> urlList = baseDNs.get(urlBaseDN);
      LinkedHashSet<SearchFilter> urlFilters =
           new LinkedHashSet<SearchFilter>();
      for (LDAPURL url : urlList)
      {
        urlFilters.add(url.getFilter());
      }

      SearchFilter combinedFilter;
      if (filter == null)
      {
        if (urlFilters.size() == 1)
        {
          combinedFilter = urlFilters.iterator().next();
        }
        else
        {
          combinedFilter = SearchFilter.createORFilter(urlFilters);
        }
      }
      else
      {
        if (urlFilters.size() == 1)
        {
          SearchFilter urlFilter = urlFilters.iterator().next();
          if (urlFilter.equals(filter))
          {
            combinedFilter = filter;
          }
          else
          {
            LinkedHashSet<SearchFilter> filterSet =
                 new LinkedHashSet<SearchFilter>();
            filterSet.add(filter);
            filterSet.add(urlFilter);
            combinedFilter = SearchFilter.createANDFilter(filterSet);
          }
        }
        else
        {
          if (urlFilters.contains(filter))
          {
            combinedFilter = filter;
          }
          else
          {
            LinkedHashSet<SearchFilter> filterSet =
                 new LinkedHashSet<SearchFilter>();
            filterSet.add(filter);
            filterSet.add(SearchFilter.createORFilter(urlFilters));
            combinedFilter = SearchFilter.createANDFilter(filterSet);
          }
        }
      }

      searchMap.put(urlBaseDN, combinedFilter);
    }


    // At this point, we should have all the information we need to perform the
    // searches.  Create arrays of the elements for each.
    DN[]           baseDNArray = new DN[baseDNs.size()];
    SearchFilter[] filterArray = new SearchFilter[baseDNArray.length];
    LDAPURL[][]    urlArray    = new LDAPURL[baseDNArray.length][];
    Iterator<DN> iterator = baseDNs.keySet().iterator();
    for (int i=0; i < baseDNArray.length; i++)
    {
      baseDNArray[i] = iterator.next();
      filterArray[i] = searchMap.get(baseDNArray[i]);

      LinkedList<LDAPURL> urlList = baseDNs.get(baseDNArray[i]);
      urlArray[i] = new LDAPURL[urlList.size()];
      int j=0;
      for (LDAPURL url : urlList)
      {
        urlArray[i][j++] = url;
      }
    }


    DynamicGroupSearchThread searchThread =
         new DynamicGroupSearchThread(this, baseDNArray, filterArray, urlArray);
    searchThread.start();
  }



  /**
   * Retrieves the DN of the dynamic group with which this dynamic group member
   * list is associated.
   *
   * @return  The DN of the dynamic group with which this dynamic group member
   *          list is associated.
   */
  public final DN getDynamicGroupDN()
  {
    return groupDN;
  }



  /**
   * Indicates that all of the searches needed to iterate across the member list
   * have completed and there will not be any more results provided.
   */
  final void setSearchesCompleted()
  {
    searchesCompleted = true;
  }



  /**
   * Adds the provided entry to the set of results that should be returned for
   * this member list.
   *
   * @param  entry  The entry to add to the set of results that should be
   *                returned for this member list.
   *
   * @return  {@code true} if the entry was added to the result set, or
   *          {@code false} if it was not (either because a timeout expired or
   *          the attempt was interrupted).  If this method returns
   *          {@code false}, then the search thread should terminate
   *          immediately.
   */
  final boolean addResult(Entry entry)
  {
    try
    {
      return resultQueue.offer(entry, 10, TimeUnit.SECONDS);
    }
    catch (InterruptedException ie)
    {
      return false;
    }
  }



  /**
   * Adds the provided membership exception so that it will be thrown along with
   * the set of results for this member list.
   *
   * @param  membershipException  The membership exception to be thrown.
   *
   * @return  {@code true} if the exception was added to the result set, or
   *          {@code false} if it was not (either because a timeout expired or
   *          the attempt was interrupted).  If this method returns
   *          {@code false}, then the search thread should terminate
   *          immediately.
   */
  final boolean addResult(MembershipException membershipException)
  {
    try
    {
      return resultQueue.offer(membershipException, 10, TimeUnit.SECONDS);
    }
    catch (InterruptedException ie)
    {
      return false;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean hasMoreMembers()
  {
    while (! searchesCompleted)
    {
      if (resultQueue.peek() != null)
      {
        return true;
      }

      try
      {
        Thread.sleep(0, 1000);
      } catch (Exception e) {}
    }

    return (resultQueue.peek() != null);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Entry nextMemberEntry()
         throws MembershipException
  {
    if (! hasMoreMembers())
    {
      return null;
    }

    Object result = resultQueue.poll();
    if (result == null)
    {
      close();
      return null;
    }
    else if (result instanceof Entry)
    {
      return (Entry) result;
    }
    else if (result instanceof MembershipException)
    {
      MembershipException me = (MembershipException) result;
      if (! me.continueIterating())
      {
        close();
      }

      throw me;
    }

    // We should never get here.
    close();
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void close()
  {
    searchesCompleted = true;
    resultQueue.clear();
  }
}

