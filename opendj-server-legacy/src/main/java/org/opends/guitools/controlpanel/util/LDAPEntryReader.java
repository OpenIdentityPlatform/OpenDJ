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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.util;

import static org.forgerock.opendj.ldap.SearchScope.*;

import java.util.HashSet;
import java.util.Set;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.guitools.controlpanel.browser.ConnectionWithControls;
import org.opends.guitools.controlpanel.event.EntryReadErrorEvent;
import org.opends.guitools.controlpanel.event.EntryReadEvent;
import org.opends.guitools.controlpanel.event.EntryReadListener;

/**
 * A class that reads an entry on the background.  This is used in the LDAP
 * entries browser.  When the entry is read it notifies to the EntryReadListener
 * objects that have been registered.
 */
public class LDAPEntryReader extends BackgroundTask<Entry>
{
  private final DN dn;
  private final ConnectionWithControls conn;
  private final Set<EntryReadListener> listeners = new HashSet<>();
  private boolean isOver;
  private boolean notifyListeners;

  /**
   * Constructor of the entry reader.
   * @param dn the DN of the entry.
   * @param conn the connection to the server.
   */
  public LDAPEntryReader(DN dn, ConnectionWithControls conn)
  {
    this.dn = dn;
    this.conn = conn;
    this.notifyListeners = true;
  }

  @Override
  public Entry processBackgroundTask() throws Throwable
  {
    isOver = false;
    final Filter filter = Filter.valueOf("(|(objectclass=*)(objectclass=ldapsubentry))");
    SearchRequest request = Requests.newSearchRequest(dn, BASE_OBJECT, filter, "*", "+");
    SearchResultEntry sr = conn.searchSingleEntry(request);
    if (isInterrupted())
    {
      isOver = true;
    }
    return sr;
  }

  @Override
  public void backgroundTaskCompleted(Entry sr, Throwable throwable)
  {
    if (!isInterrupted() && isNotifyListeners())
    {
      if (throwable == null)
      {
        notifyListeners(sr);
      }
      else
      {
        notifyListeners(throwable);
      }
    }
    isOver = true;
  }

  /**
   * Returns whether this entry reader will notify the listeners once it is
   * over.
   * @return whether this entry reader will notify the listeners once it is
   * over.
   */
  public boolean isNotifyListeners()
  {
    return notifyListeners;
  }

  /**
   * Sets whether this entry reader will notify the listeners once it is
   * over.
   * @param notifyListeners whether this entry reader will notify the listeners
   * once it is over.
   */
  public void setNotifyListeners(boolean notifyListeners)
  {
    this.notifyListeners = notifyListeners;
  }

  /**
   * Returns <CODE>true</CODE> if the read process is over and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the read process is over and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isOver()
  {
    return isOver;
  }

  /**
   * Notifies listeners that a new entry was read.
   * @param sr the new entry in form of CustomSearchResult.
   */
  private void notifyListeners(Entry sr)
  {
    EntryReadEvent ev = new EntryReadEvent(this, sr);
    for (EntryReadListener listener : listeners)
    {
      listener.entryRead(ev);
    }
  }

  /**
   * Notifies the listeners that an error occurred reading an entry.
   * @param t the error that occurred reading an entry.
   */
  private void notifyListeners(Throwable t)
  {
    EntryReadErrorEvent ev = new EntryReadErrorEvent(this, dn, t);
    for (EntryReadListener listener : listeners)
    {
      listener.entryReadError(ev);
    }
  }

  /**
   * Adds an EntryReadListener.
   * @param listener the listener.
   */
  public void addEntryReadListener(EntryReadListener listener)
  {
    listeners.add(listener);
  }

  /**
   * Removes an EntryReadListener.
   * @param listener the listener.
   */
  public void removeEntryReadListener(EntryReadListener listener)
  {
    listeners.remove(listener);
  }
}
