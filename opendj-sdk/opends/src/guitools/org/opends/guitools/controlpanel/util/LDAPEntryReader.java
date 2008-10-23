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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.util;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;

import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.event.EntryReadErrorEvent;
import org.opends.guitools.controlpanel.event.EntryReadEvent;
import org.opends.guitools.controlpanel.event.EntryReadListener;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Schema;

/**
 * A class that reads an entry on the background.  This is used in the LDAP
 * entries browser.  When the entry is read it notifies to the EntryReadListener
 * objects that have been registered.
 *
 */
public class LDAPEntryReader extends BackgroundTask<CustomSearchResult>
{
  private String dn;
  private InitialLdapContext ctx;
  private Schema schema;
  private Set<EntryReadListener> listeners = new HashSet<EntryReadListener>();
  private boolean isOver;

  /**
   * Constructor of the entry reader.
   * @param dn the DN of the entry.
   * @param ctx the connection to the server.
   * @param schema the schema of the server.
   */
  public LDAPEntryReader(String dn, InitialLdapContext ctx, Schema schema)
  {
    this.dn = dn;
    this.ctx = ctx;
    this.schema = schema;
  }

  /**
   * {@inheritDoc}
   */
  public CustomSearchResult processBackgroundTask() throws Throwable
  {
    isOver = false;
    try
    {
      SearchControls controls = new SearchControls();
      controls.setCountLimit(1);
      Set<String> operational = getAllOperationalAttributes();

      String[] attrs = new String[operational.size()+1];
      Iterator<String> it = operational.iterator();
      int i = 0;
      while (it.hasNext())
      {
        attrs[i] = it.next();
        i++;
      }
      attrs[attrs.length - 1] = "*";
      controls.setReturningAttributes(attrs);
      controls.setSearchScope(SearchControls.OBJECT_SCOPE);
      final String filter = "(|(objectclass=*)(objectclass=ldapsubentry))";

      NamingEnumeration en = ctx.search(Utilities.getJNDIName(dn), filter,
          controls);

      SearchResult sr = (SearchResult)en.next();

      return new CustomSearchResult(sr, dn);
    }
    finally
    {
      if (isInterrupted())
      {
        isOver = true;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void backgroundTaskCompleted(CustomSearchResult sr,
      Throwable throwable)
  {
    if (!isInterrupted())
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
  private void notifyListeners(CustomSearchResult sr)
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

  private Set<String> getAllOperationalAttributes()
  {
    HashSet<String> attrs = new HashSet<String>();
    // Do a best effort if schema could not be retrieved when creating
    // this object.
    if (schema != null)
    {
      for (AttributeType attr : schema.getAttributeTypes().values())
      {
        if (attr.isOperational())
        {
          attrs.add(attr.getNameOrOID());
        }
      }
    }
    return attrs;
  }
}
