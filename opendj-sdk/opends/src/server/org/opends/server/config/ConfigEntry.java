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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.config;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a configuration entry, which can hold zero or more
 * attributes that may control the configuration of various components of the
 * Directory Server.
 */
public class ConfigEntry
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The set of immediate children for this configuration entry.
  private ConcurrentHashMap<DN,ConfigEntry> children;

  // The immediate parent for this configuration entry.
  private ConfigEntry parent;

  // The set of add listeners that have been registered with this entry.
  private CopyOnWriteArrayList<ConfigAddListener> addListeners;

  // The set of change listeners that have been registered with this entry.
  private CopyOnWriteArrayList<ConfigChangeListener> changeListeners;

  // The set of delete listeners that have been registered with this entry.
  private CopyOnWriteArrayList<ConfigDeleteListener> deleteListeners;

  // The actual entry wrapped by this configuration entry.
  private Entry entry;

  // The lock used to provide threadsafe access to this configuration entry.
  private ReentrantLock entryLock;



  /**
   * Creates a new config entry with the provided information.
   *
   * @param  entry   The entry that will be encapsulated by this config entry.
   * @param  parent  The configuration entry that is the immediate parent for
   *                 this configuration entry.  It may be <CODE>null</CODE> if
   *                 this entry is the configuration root.
   */
  public ConfigEntry(Entry entry, ConfigEntry parent)
  {
    this.entry  = entry;
    this.parent = parent;

    children        = new ConcurrentHashMap<DN,ConfigEntry>();
    addListeners    = new CopyOnWriteArrayList<ConfigAddListener>();
    changeListeners = new CopyOnWriteArrayList<ConfigChangeListener>();
    deleteListeners = new CopyOnWriteArrayList<ConfigDeleteListener>();
    entryLock       = new ReentrantLock();
  }



  /**
   * Retrieves the actual entry wrapped by this configuration entry.
   *
   * @return  The actual entry wrapped by this configuration entry.
   */
  public Entry getEntry()
  {
    return entry;
  }



  /**
   * Replaces the actual entry wrapped by this configuration entry with the
   * provided entry.  The given entry must be non-null and must have the same DN
   * as the current entry.  No validation will be performed on the target entry.
   * All add/delete/change listeners that have been registered will be
   * maintained, it will keep the same parent and set of children, and all other
   * settings will remain the same.
   *
   * @param  entry   The new entry to store in this config entry.
   */
  public void setEntry(Entry entry)
  {
    entryLock.lock();

    try
    {
      this.entry = entry;
    }
    finally
    {
      entryLock.unlock();
    }
  }



  /**
   * Retrieves the DN for this configuration entry.
   *
   * @return  The DN for this configuration entry.
   */
  public DN getDN()
  {
    return entry.getDN();
  }



  /**
   * Indicates whether this configuration entry contains the specified
   * objectclass.
   *
   * @param  name  The name of the objectclass for which to make the
   *               determination.
   *
   * @return  <CODE>true</CODE> if this configuration entry contains the
   *          specified objectclass, or <CODE>false</CODE> if not.
   */
  public boolean hasObjectClass(String name)
  {
    ObjectClass oc = DirectoryServer.getObjectClass(name.toLowerCase());
    if (oc == null)
    {
      oc = DirectoryServer.getDefaultObjectClass(name);
    }

    return entry.hasObjectClass(oc);
  }



  /**
   * Retrieves the specified configuration attribute from this configuration
   * entry.
   *
   * @param  stub  The stub to use to format the returned configuration
   *               attribute.
   *
   * @return  The requested configuration attribute from this configuration
   *          entry, or <CODE>null</CODE> if no such attribute is present in
   *          this entry.
   *
   * @throws  ConfigException  If the specified attribute exists but cannot be
   *                           interpreted as the specified type of
   *                           configuration attribute.
   */
  public ConfigAttribute getConfigAttribute(ConfigAttribute stub)
         throws ConfigException
  {
    String attrName = stub.getName();
    AttributeType attrType =
         DirectoryServer.getAttributeType(attrName.toLowerCase());
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(attrName);
    }

    List<Attribute> attrList = entry.getAttribute(attrType);
    if ((attrList == null) || (attrList.isEmpty()))
    {
      return null;
    }

    return stub.getConfigAttribute(attrList);
  }



  /**
   * Puts the provided configuration attribute in this entry (adding a new
   * attribute if one doesn't exist, or replacing it if one does).  This must
   * only be performed on a duplicate of a configuration entry and never on a
   * configuration entry itself.
   *
   * @param  attribute  The configuration attribute to use.
   */
  public void putConfigAttribute(ConfigAttribute attribute)
  {
    String name = attribute.getName();
    AttributeType attrType =
         DirectoryServer.getAttributeType(name.toLowerCase());
    if (attrType == null)
    {
      attrType =
           DirectoryServer.getDefaultAttributeType(name, attribute.getSyntax());
    }

    ArrayList<Attribute> attrs = new ArrayList<Attribute>(2);
    attrs.add(new Attribute(attrType, name, attribute.getActiveValues()));
    if (attribute.hasPendingValues())
    {
      LinkedHashSet<String> options = new LinkedHashSet<String>(1);
      options.add(OPTION_PENDING_VALUES);
      attrs.add(new Attribute(attrType, name, options,
                              attribute.getPendingValues()));
    }

    entry.putAttribute(attrType, attrs);
  }



  /**
   * Removes the specified configuration attribute from the entry.  This will
   * have no impact if the specified attribute is not contained in the entry.
   *
   * @param  lowerName  The name of the configuration attribute to remove from
   *                    the entry, formatted in all lowercase characters.
   *
   * @return  <CODE>true</CODE> if the requested attribute was found and
   *          removed, or <CODE>false</CODE> if not.
   */
  public boolean removeConfigAttribute(String lowerName)
  {
    for (AttributeType t : entry.getUserAttributes().keySet())
    {
      if (t.hasNameOrOID(lowerName))
      {
        entry.getUserAttributes().remove(t);
        return true;
      }
    }

    for (AttributeType t : entry.getOperationalAttributes().keySet())
    {
      if (t.hasNameOrOID(lowerName))
      {
        entry.getOperationalAttributes().remove(t);
        return true;
      }
    }

    return false;
  }



  /**
   * Retrieves the configuration entry that is the immediate parent for this
   * configuration entry.
   *
   * @return  The configuration entry that is the immediate parent for this
   *          configuration entry.  It may be <CODE>null</CODE> if this entry is
   *          the configuration root.
   */
  public ConfigEntry getParent()
  {
    return parent;
  }



  /**
   * Retrieves the set of children associated with this configuration entry.
   * This list should not be altered by the caller.
   *
   * @return  The set of children associated with this configuration entry.
   */
  public ConcurrentHashMap<DN,ConfigEntry> getChildren()
  {
    return children;
  }



  /**
   * Indicates whether this entry has any children.
   *
   * @return  <CODE>true</CODE> if this entry has one or more children, or
   *          <CODE>false</CODE> if not.
   */
  public boolean hasChildren()
  {
    return (! children.isEmpty());
  }



  /**
   * Adds the specified entry as a child of this configuration entry.  No check
   * will be made to determine whether the specified entry actually should be a
   * child of this entry, and this method will not notify any add listeners that
   * might be registered with this configuration entry.
   *
   * @param  childEntry  The entry to add as a child of this configuration
   *                     entry.
   *
   * @throws  ConfigException  If the provided entry could not be added as a
   *                           child of this configuration entry (e.g., because
   *                           another entry already exists with the same DN).
   */
  public void addChild(ConfigEntry childEntry)
         throws ConfigException
  {
    ConfigEntry conflictingChild;

    entryLock.lock();

    try
    {
      conflictingChild = children.putIfAbsent(childEntry.getDN(), childEntry);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      conflictingChild = null;
    }
    finally
    {
      entryLock.unlock();
    }

    if (conflictingChild != null)
    {
      Message message = ERR_CONFIG_ENTRY_CONFLICTING_CHILD.get(
          conflictingChild.getDN().toString(), entry.getDN().toString());
      throw new ConfigException(message);
    }
  }



  /**
   * Attempts to remove the child entry with the specified DN.  This method will
   * not notify any delete listeners that might be registered with this
   * configuration entry.
   *
   * @param  childDN  The DN of the child entry to remove from this config
   *                  entry.
   *
   * @return  The configuration entry that was removed as a child of this
   *          entry.
   *
   * @throws  ConfigException  If the specified child entry did not exist or if
   *                           it had children of its own.
   */
  public ConfigEntry removeChild(DN childDN)
         throws ConfigException
  {
    entryLock.lock();

    try
    {
      ConfigEntry childEntry = children.get(childDN);
      if (childEntry == null)
      {
        Message message = ERR_CONFIG_ENTRY_NO_SUCH_CHILD.get(
            childDN.toString(), entry.getDN().toString());
        throw new ConfigException(message);
      }

      if (childEntry.hasChildren())
      {
        Message message = ERR_CONFIG_ENTRY_CANNOT_REMOVE_NONLEAF.get(
            childDN.toString(), entry.getDN().toString());
        throw new ConfigException(message);
      }

      children.remove(childDN);
      return childEntry;
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CONFIG_ENTRY_CANNOT_REMOVE_CHILD.
          get(String.valueOf(childDN), String.valueOf(entry.getDN()),
              stackTraceToSingleLineString(e));
      throw new ConfigException(message, e);
    }
    finally
    {
      entryLock.unlock();
    }
  }



  /**
   * Creates a duplicate of this configuration entry that should be used when
   * making changes to this entry.  Changes should only be made to the duplicate
   * (never the original) and then applied to the original.  Note that this
   * method and the other methods used to make changes to the entry contents are
   * not threadsafe and therefore must be externally synchronized to ensure that
   * only one change may be in progress at any given time.
   *
   * @return  A duplicate of this configuration entry that should be used when
   *          making changes to this entry.
   */
  public ConfigEntry duplicate()
  {
    return new ConfigEntry(entry.duplicate(false), parent);
  }



  /**
   * Retrieves the set of change listeners that have been registered with this
   * configuration entry.
   *
   * @return  The set of change listeners that have been registered with this
   *          configuration entry.
   */
  public CopyOnWriteArrayList<ConfigChangeListener> getChangeListeners()
  {
    return changeListeners;
  }



  /**
   * Registers the provided change listener so that it will be notified of any
   * changes to this configuration entry.  No check will be made to determine
   * whether the provided listener is already registered.
   *
   * @param  listener  The change listener to register with this config entry.
   */
  public void registerChangeListener(ConfigChangeListener listener)
  {
    changeListeners.add(listener);
  }



  /**
   * Attempts to deregister the provided change listener with this configuration
   * entry.
   *
   * @param  listener  The change listener to deregister with this config entry.
   *
   * @return  <CODE>true</CODE> if the specified listener was deregistered, or
   *          <CODE>false</CODE> if it was not.
   */
  public boolean deregisterChangeListener(ConfigChangeListener listener)
  {
    return changeListeners.remove(listener);
  }



  /**
   * Retrieves the set of config add listeners that have been registered for
   * this entry.
   *
   * @return  The set of config add listeners that have been registered for this
   *          entry.
   */
  public CopyOnWriteArrayList<ConfigAddListener> getAddListeners()
  {
    return addListeners;
  }



  /**
   * Registers the provided add listener so that it will be notified if any new
   * entries are added immediately below this configuration entry.
   *
   * @param  listener  The add listener that should be registered.
   */
  public void registerAddListener(ConfigAddListener listener)
  {
    addListeners.addIfAbsent(listener);
  }



  /**
   * Deregisters the provided add listener so that it will no longer be
   * notified if any new entries are added immediately below this configuration
   * entry.
   *
   * @param  listener  The add listener that should be deregistered.
   */
  public void deregisterAddListener(ConfigAddListener listener)
  {
    addListeners.remove(listener);
  }



  /**
   * Retrieves the set of config delete listeners that have been registered for
   * this entry.
   *
   * @return  The set of config delete listeners that have been registered for
   *          this entry.
   */
  public CopyOnWriteArrayList<ConfigDeleteListener> getDeleteListeners()
  {
    return deleteListeners;
  }



  /**
   * Registers the provided delete listener so that it will be notified if any
   * entries are deleted immediately below this configuration entry.
   *
   * @param  listener  The delete listener that should be registered.
   */
  public void registerDeleteListener(ConfigDeleteListener listener)
  {
    deleteListeners.addIfAbsent(listener);
  }



  /**
   * Deregisters the provided delete listener so that it will no longer be
   * notified if any new are removed immediately below this configuration entry.
   *
   * @param  listener  The delete listener that should be deregistered.
   */
  public void deregisterDeleteListener(ConfigDeleteListener listener)
  {
    deleteListeners.remove(listener);
  }
}

