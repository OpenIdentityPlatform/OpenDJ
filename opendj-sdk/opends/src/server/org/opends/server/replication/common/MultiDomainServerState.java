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
 */
package org.opends.server.replication.common;

import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;


/**
 * This object is used to store a list of ServerState object, one by
 * replication domain. Globally, it is the generalization of ServerState
 * (that applies to one domain) to a list of domains.
 */
public class MultiDomainServerState implements Iterable<String>
{
  /**
   * The list of (domain service id, ServerState).
   */
  private TreeMap<String, ServerState> list;

  /**
   * Creates a new empty object.
   */
  public MultiDomainServerState()
  {
    list = new TreeMap<String, ServerState>();
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
   * Update the ServerState of the provided serviceId with the
   * replication change number provided.
   *
   * @param serviceId    The provided serviceId.
   * @param changeNumber The provided ChangeNumber.
   *
   * @return a boolean indicating if the update was meaningful.
   */
  public boolean update(String serviceId, ChangeNumber changeNumber)
  {
    if (changeNumber == null)
      return false;

    synchronized(this)
    {
      Short serverId =  changeNumber.getServerId();
      ServerState oldServerState = list.get(serviceId);
      if (oldServerState == null)
        oldServerState = new ServerState();

      if (changeNumber.newer(oldServerState.getMaxChangeNumber(serverId)))
      {
        oldServerState.update(changeNumber);
        list.put(serviceId,oldServerState);
        return true;
      }
      else
      {
        return false;
      }
    }
  }

  /**
   * Update the ServerState of the provided serviceId with the
   * provided server state.
   *
   * @param serviceId    The provided serviceId.
   * @param serverState  The provided serverState.
   */
  public void update(String serviceId, ServerState serverState)
  {
    list.put(serviceId,serverState.duplicate());
  }

  /**
   * Create an object from a string representation.
   * @param mdss The provided string representation of the state.
   */
  public MultiDomainServerState(String mdss)
  {
    list = new TreeMap<String, ServerState>();
    if ((mdss != null) &&
        (mdss.length()>0))
    {
      String[] domains = mdss.split(";");
      for (String domain : domains)
      {
        String[] fields = domain.split(":");
        String name = fields[0];
        ServerState ss = new ServerState();
        if (fields.length>1)
        {
          String strState = fields[1].trim();
          String[] strCN = strState.split(" ");
          for (String sr : strCN)
          {
            ChangeNumber fromChangeNumber = new ChangeNumber(sr);
            ss.update(fromChangeNumber);
          }
        }
        this.list.put(name, ss);
      }
    }
  }

  /**
   * Returns a string representation of this object.
   * @return The string representation.
   */
  public String toString()
  {
    String res = "";
    if ((list != null) && (!list.isEmpty()))
    {
      for (String serviceId  : list.keySet())
      {
        ServerState ss = list.get(serviceId);
        res += serviceId + ":" + ss.toString();
        res += ";";
      }
    }
    return res;
  }

  /**
   * Dump a string representation in the provided buffer.
   * @param buffer The provided buffer.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append(this.toString());
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

  /**
   * {@inheritDoc}
   */
  public Iterator<String> iterator()
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
    return ((this.cover(other)) && (other.cover(this)));
  }

  /**
   * Test if this object covers the provided covered object.
   * @param  covered The provided object.
   * @return true when this covers the provided object.
   */
  public boolean cover(MultiDomainServerState covered)
  {
    for (String serviceId : covered.list.keySet())
    {
      ServerState state = list.get(serviceId);
      ServerState coveredState = covered.list.get(serviceId);
      if ((state==null)||(coveredState == null) || (!state.cover(coveredState)))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Splits the provided generalizedServerState being a String with the
   * following syntax: "domain1:state1;domain2:state2;..."
   * to a hashmap of (domain DN, domain ServerState).
   * @param multidomainserverstate the provided state
   * @exception DirectoryException when an error occurs
   * @return the splited state.
   */
  public static HashMap<String,ServerState> splitGenStateToServerStates(
      String multidomainserverstate)
      throws DirectoryException
  {
    HashMap<String,ServerState> startStates = new HashMap<String,ServerState>();
    try
    {
      // Split the provided multidomainserverstate into domains
      String[] domains = multidomainserverstate.split(";");
      for (String domain : domains)
      {
        // For each domain, split the changenumbers by server
        // and build a server state (SHOULD BE OPTIMIZED)
        ServerState serverStateByDomain = new ServerState();

        String[] fields = domain.split(":");
        String domainBaseDN = fields[0];
        if (fields.length>1)
        {
          String strState = fields[1];
          String[] strCN = strState.split(" ");
          for (String sr : strCN)
          {
            ChangeNumber fromChangeNumber = new ChangeNumber(sr);
            serverStateByDomain.update(fromChangeNumber);
          }
        }
        startStates.put(domainBaseDN, serverStateByDomain);
      }
    }
    catch(Exception e)
    {
      throw new DirectoryException(
          ResultCode.OPERATIONS_ERROR,
          Message.raw(Category.SYNC, Severity.INFORMATION,"Exception raised: "),
          e);
    }
    return startStates;
  }
}
