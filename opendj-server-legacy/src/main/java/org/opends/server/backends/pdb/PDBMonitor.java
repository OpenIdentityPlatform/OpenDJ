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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.server.backends.pdb;

import static org.opends.server.util.StaticUtils.*;

import java.rmi.RemoteException;

import org.opends.server.api.MonitorData;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.opends.server.api.MonitorProvider;

import com.persistit.Management.BufferPoolInfo;
import com.persistit.Management.TreeInfo;
import com.persistit.Management.VolumeInfo;
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
  public MonitorData getMonitorData()
  {
    try
    {
      MonitorData monitorAttrs = new MonitorData();
      monitorAttrs.add("PDBVersion", db.getManagement().getVersion());

      for(BufferPoolInfo bufferInfo : db.getManagement().getBufferPoolInfoArray())
      {
        monitorAttrs.addBean(bufferInfo, "PDBBuffer");
      }
      monitorAttrs.addBean(db.getManagement().getJournalInfo(), "PDBJournal");
      monitorAttrs.addBean(db.getManagement().getTransactionInfo(), "PDBTransaction");
      for (VolumeInfo vol : db.getManagement().getVolumeInfoArray())
      {
        monitorAttrs.addBean(vol, "PDBVolume");
        for (TreeInfo tree : db.getManagement().getTreeInfoArray(vol.getName()))
        {
          // For the time being, depth is not reported.
          monitorAttrs.add("PDBVolumeTree", vol.getName() + tree.getName()
              + ", traverse=" + tree.getTraverseCounter()
              + ", fetch=" + tree.getFetchCounter()
              + ", store=" + tree.getStoreCounter()
              + ", remove=" + tree.getRemoveCounter());
        }
      }
      return monitorAttrs;
    }
    catch (ReflectiveOperationException | RemoteException e)
    {
      MonitorData monitorAttrs = new MonitorData(1);
      monitorAttrs.add("PDBInfo", stackTraceToSingleLineString(e));
      return monitorAttrs;
    }
  }
}
