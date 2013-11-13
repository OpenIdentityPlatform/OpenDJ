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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.common;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;

import static org.opends.messages.ReplicationMessages.*;

/**
 * This object is used to store a list of ServerState object, one by replication
 * domain. Globally, it is the generalization of ServerState (that applies to
 * one domain) to a list of domains.
 * <p>
 * MultiDomainServerState is also known as "cookie" and is used with the
 * cookie-based changelog.
 */
public class MultiDomainServerState implements Iterable<DN>
{
  /**
   * The list of (domain service id, ServerState).
   */
  private Map<DN, ServerState> list;

  /**
   * Creates a new empty object.
   */
  public MultiDomainServerState()
  {
    list = new TreeMap<DN, ServerState>();
  }

  /**
   * Create an object from a string representation.
   * @param mdss The provided string representation of the state.
   * @throws DirectoryException when the string has an invalid format
   */
  public MultiDomainServerState(String mdss)
          throws DirectoryException
  {
    list = splitGenStateToServerStates(mdss);
  }


  /**
   * Empty the object..
   * After this call the object will be in the same state as if it
   * was just created.
   */
  public void clear()
  {
    synchronized (this)
    {
      list.clear();
    }
  }

  /**
   * Update the ServerState of the provided baseDN with the replication
   * {@link CSN} provided.
   *
   * @param baseDN       The provided baseDN.
   * @param csn          The provided CSN.
   *
   * @return a boolean indicating if the update was meaningful.
   */
  public boolean update(DN baseDN, CSN csn)
  {
    if (csn == null)
      return false;

    synchronized(this)
    {
      int serverId =  csn.getServerId();
      ServerState oldServerState = list.get(baseDN);
      if (oldServerState == null)
        oldServerState = new ServerState();

      if (csn.isNewerThan(oldServerState.getCSN(serverId)))
      {
        oldServerState.update(csn);
        list.put(baseDN, oldServerState);
        return true;
      }
      return false;
    }
  }

  /**
   * Update the ServerState of the provided baseDN with the provided server
   * state. The provided server state will be owned by this instance, so care
   * must be taken by calling code to duplicate it if needed.
   *
   * @param baseDN
   *          The provided baseDN.
   * @param serverState
   *          The provided serverState.
   */
  public void update(DN baseDN, ServerState serverState)
  {
    list.put(baseDN, serverState);
  }

  /**
   * Returns a string representation of this object.
   * @return The string representation.
   */
  @Override
  public String toString()
  {
    StringBuilder res = new StringBuilder();
    if (list != null && !list.isEmpty())
    {
      for (Entry<DN, ServerState> entry : list.entrySet())
      {
        res.append(entry.getKey()).append(":")
           .append(entry.getValue()).append(";");
      }
    }
    return res.toString();
  }

  /**
   * Dump a string representation in the provided buffer.
   * @param buffer The provided buffer.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append(this);
  }


  /**
   * Tests if the state is empty.
   *
   * @return True if the state is empty.
   */
  public boolean isEmpty()
  {
    return list.isEmpty();
  }

  /** {@inheritDoc} */
  @Override
  public Iterator<DN> iterator()
  {
    return list.keySet().iterator();
  }

  /**
   * Test if this object equals the provided other object.
   * @param other The other object with which we want to test equality.
   * @return      Returns True if this equals other, else return false.
   */
  public boolean equalsTo(MultiDomainServerState other)
  {
    return cover(other) && other.cover(this);
  }

  /**
   * Test if this object covers the provided covered object.
   * @param  covered The provided object.
   * @return true when this covers the provided object.
   */
  public boolean cover(MultiDomainServerState covered)
  {
    for (DN baseDN : covered.list.keySet())
    {
      ServerState state = list.get(baseDN);
      ServerState coveredState = covered.list.get(baseDN);
      if (state == null || coveredState == null || !state.cover(coveredState))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Splits the provided generalizedServerState being a String with the
   * following syntax: "domain1:state1;domain2:state2;..." to a Map of (domain
   * DN, domain ServerState).
   *
   * @param multiDomainServerState
   *          the provided multi domain server state also known as cookie
   * @exception DirectoryException
   *              when an error occurs
   * @return the split state.
   */
  public static Map<DN, ServerState> splitGenStateToServerStates(
      String multiDomainServerState) throws DirectoryException
  {
    Map<DN, ServerState> startStates = new TreeMap<DN, ServerState>();
    if (multiDomainServerState != null && multiDomainServerState.length() > 0)
    {
      try
      {
        // Split the provided multiDomainServerState into domains
        String[] domains = multiDomainServerState.split(";");
        for (String domain : domains)
        {
          // For each domain, split the CSNs by server
          // and build a server state (SHOULD BE OPTIMIZED)
          final ServerState serverStateByDomain = new ServerState();

          final String[] fields = domain.split(":");
          if (fields.length == 0)
          {
            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                ERR_INVALID_COOKIE_SYNTAX.get(multiDomainServerState));
          }
          final String domainBaseDN = fields[0];
          if (fields.length > 1)
          {
            final String serverStateStr = fields[1];
            for (String csnStr : serverStateStr.split(" "))
            {
              final CSN csn = new CSN(csnStr);
              serverStateByDomain.update(csn);
            }
          }
          startStates.put(DN.decode(domainBaseDN), serverStateByDomain);
        }
      }
      catch (DirectoryException de)
      {
        throw de;
      }
      catch (Exception e)
      {
        throw new DirectoryException(
            ResultCode.PROTOCOL_ERROR,
            Message.raw(Category.SYNC, Severity.INFORMATION,
            "Exception raised: " + e),
            e);
      }
    }
    return startStates;
  }
}
