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
package org.opends.server.replication.common;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds information about a DS connected to the topology. This
 * information is to be exchanged through the replication protocol in
 * topology messages, to keep every member (RS or DS) of the topology aware of
 * the DS topology.
 */
public class DSInfo
{

  // DS server id
  private short dsId = -1;
  // Server id of the RS the DS is connected to
  private short rsId = -1;
  // DS Generation Id
  private long generationId = -1;
  // DS Status
  private ServerStatus status = ServerStatus.INVALID_STATUS;
  // Assured replication enabled on DS or not
  private boolean assuredFlag = false;
  // DS assured mode (relevant if assured replication enabled)
  private AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;
  // DS safe data level (relevant if assured mode is safe data)
  private byte safeDataLevel = (byte) -1;
  // List of referrals URLs exported by the DS
  private List<String> refUrls = new ArrayList<String>(0);
  // Group id
  private byte groupId = (byte) -1;

  /**
   * Creates a new instance of DSInfo with every given info.
   *
   * @param dsId The DS id
   * @param rsId The RS id the DS is connected to
   * @param generationId The generation id the DS is using
   * @param status The DS status
   * @param assuredFlag DS assured replication enabled or not
   * @param assuredMode DS assured mode
   * @param safeDataLevel DS safe data level
   * @param groupId DS group id
   * @param refUrls DS exported referrals URLs
   */
  public DSInfo(short dsId, short rsId, long generationId, ServerStatus status,
    boolean assuredFlag, AssuredMode assuredMode, byte safeDataLevel,
    byte groupId, List<String> refUrls)
  {

    this.dsId = dsId;
    this.rsId = rsId;
    this.generationId = generationId;
    this.status = status;
    this.assuredFlag = assuredFlag;
    this.assuredMode = assuredMode;
    this.safeDataLevel = safeDataLevel;
    this.groupId = groupId;
    this.refUrls = refUrls;
  }

  /**
   * Get the DS id.
   * @return the DS id
   */
  public short getDsId()
  {
    return dsId;
  }

  /**
   * Get the RS id the DS is connected to.
   * @return the RS id the DS is connected to
   */
  public short getRsId()
  {
    return rsId;
  }

  /**
   * Get the generation id DS is using.
   * @return the generation id DS is using.
   */
  public long getGenerationId()
  {
    return generationId;
  }

  /**
   * Get the DS status.
   * @return the DS status
   */
  public ServerStatus getStatus()
  {
    return status;
  }

  /**
   * Tells if the DS has assured replication enabled.
   * @return True if the DS has assured replication enabled
   */
  public boolean isAssured()
  {
    return assuredFlag;
  }

  /**
   * Get the DS assured mode (relevant if DS has assured replication enabled).
   * @return The DS assured mode
   */
  public AssuredMode getAssuredMode()
  {
    return assuredMode;
  }

  /**
   * Get the DS safe data level (relevant if assured mode is safe data).
   * @return The DS safe data level
   */
  public byte getSafeDataLevel()
  {
    return safeDataLevel;
  }

  /**
   * Get the DS group id.
   * @return The DS group id
   */
  public byte getGroupId()
  {
    return groupId;
  }

  /**
   * Get the DS exported URLs for referrals.
   * @return The DS exported URLs for referrals
   */
  public List<String> getRefUrls()
  {
    return refUrls;
  }

  /**
   * Test if the passed object is equal to this one.
   * @param obj The object to test
   * @return True if both objects are equal
   */
  @Override
  public boolean equals(Object obj)
  {
    if (obj != null)
    {
      if (obj.getClass() != this.getClass())
      {
        return false;
      }
      DSInfo dsInfo = (DSInfo) obj;
      return ((dsId == dsInfo.getDsId()) &&
        (rsId == dsInfo.getRsId()) &&
        (generationId == dsInfo.getGenerationId()) &&
        (status == dsInfo.getStatus()) &&
        (assuredFlag == dsInfo.isAssured()) &&
        (assuredMode == dsInfo.getAssuredMode()) &&
        (safeDataLevel == dsInfo.getSafeDataLevel()) &&
        (groupId == dsInfo.getGroupId()) &&
        (refUrls.equals(dsInfo.getRefUrls())));
    } else
    {
      return false;
    }
  }

  /**
   * Computes hash code for this object instance.
   * @return Hash code for this object instance.
   */
  @Override
  public int hashCode()
  {
    int hash = 7;
    hash = 73 * hash + this.dsId;
    hash = 73 * hash + this.rsId;
    hash = 73 * hash + (int) (this.generationId ^ (this.generationId >>> 32));
    hash = 73 * hash + (this.status != null ? this.status.hashCode() : 0);
    hash = 73 * hash + (this.assuredFlag ? 1 : 0);
    hash =
      73 * hash + (this.assuredMode != null ? this.assuredMode.hashCode() : 0);
    hash = 73 * hash + this.safeDataLevel;
    hash = 73 * hash + (this.refUrls != null ? this.refUrls.hashCode() : 0);
    hash = 73 * hash + this.groupId;
    return hash;
  }

  /**
   * Returns a string representation of the DS info.
   * @return A string representation of the DS info
   */
  @Override
  public String toString()
  {
    StringBuffer sb = new StringBuffer();
    sb.append("DS id: ");
    sb.append(dsId);
    sb.append("\nRS id: ");
    sb.append(rsId);
    sb.append("\nGeneration id: ");
    sb.append(generationId);
    sb.append("\nStatus: ");
    sb.append(status);
    sb.append("\nAssured replication: ");
    sb.append(assuredFlag);
    sb.append("\nAssured mode: ");
    sb.append(assuredMode);
    sb.append("\nSafe data level: ");
    sb.append(safeDataLevel);
    sb.append("\nGroup id: ");
    sb.append(groupId);
    sb.append("\nReferral URLs: ");
    sb.append(refUrls);
    return sb.toString();
  }

}
