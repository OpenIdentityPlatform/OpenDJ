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
 * Copyright 2015 ForgeRock AS.
 */
package org.opends.server.backends.pdb;

import static org.opends.server.util.StaticUtils.*;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opends.server.admin.std.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;

import com.persistit.Management.BufferPoolInfo;
import com.persistit.Management.TreeInfo;
import com.persistit.Management.VolumeInfo;
import com.persistit.Management.WrappedRemoteException;
import com.persistit.Persistit;

/** Monitoring class for PDB, populating cn=monitor statistics using reflection on objects methods. */
class PDBMonitor extends MonitorProvider<MonitorProviderCfg>
{
  private final String name;
  private final Persistit db;

  PDBMonitor(String name, Persistit db)
  {
    this.name = name;
    this.db = db;
  }

  @Override
  public String getMonitorInstanceName()
  {
    return name;
  }

  @Override
  public List<Attribute> getMonitorData()
  {
    try
    {
      List<Attribute> monitorAttrs = new ArrayList<>();
      monitorAttrs.add(Attributes.create("PDBVersion", db.getManagement().getVersion()));

      for(BufferPoolInfo bufferInfo : db.getManagement().getBufferPoolInfoArray())
      {
        addAttributesForStatsObject(monitorAttrs, "PDBBuffer", bufferInfo);
      }
      addAttributesForStatsObject(monitorAttrs, "PDBJournal", db.getManagement().getJournalInfo());
      addAttributesForStatsObject(monitorAttrs, "PDBTransaction", db.getManagement().getTransactionInfo());
      for (VolumeInfo vol : db.getManagement().getVolumeInfoArray())
      {
        addAttributesForStatsObject(monitorAttrs, "PDBVolume", vol);
        for (TreeInfo tree : db.getManagement().getTreeInfoArray(vol.getName()))
        {
          // For the time being, depth is not reported.
          monitorAttrs.add(Attributes.create("PDBVolumeTree", vol.getName() + tree.getName()
              + ", traverse=" + tree.getTraverseCounter()
              + ", fetch=" + tree.getFetchCounter()
              + ", store=" + tree.getStoreCounter()
              + ", remove=" + tree.getRemoveCounter()));
        }
      }
      return monitorAttrs;
    }
    catch (RemoteException e)
    {
      return Collections.singletonList(Attributes.create("PDBInfo", stackTraceToSingleLineString(e)));
    }
  }

  private void addAttributesForStatsObject(List<Attribute> monitorAttrs, String attrPrefix, Object stats)
    throws RemoteException
  {
    for (Method method : stats.getClass().getMethods())
    {
      if (method.getName().startsWith("get"))
      {
        Class<?> returnType = method.getReturnType();
        if (returnType.equals(int.class) || returnType.equals(long.class) || returnType.equals(String.class))
        {
          addStatAttribute(monitorAttrs, attrPrefix, stats, method, 3);
        }
      }
      else if (method.getName().startsWith("is") && method.getReturnType().equals(boolean.class))
      {
        addStatAttribute(monitorAttrs, attrPrefix, stats, method, 2);
      }
    }
  }

  private void addStatAttribute(List<Attribute> monitorAttrs, String attrPrefix, Object stats,
      Method method, int skipNameLen) throws WrappedRemoteException
  {
    try
    {
      String attrName = attrPrefix + method.getName().substring(skipNameLen);
      monitorAttrs.add(Attributes.create(attrName, String.valueOf(method.invoke(stats))));
    }
    catch (Exception e)
    {
      throw new WrappedRemoteException(e);
    }
  }
}
