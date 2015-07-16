/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.common;

import java.util.*;

/**
 * This class holds information about a DS connected to the topology. This
 * information is to be exchanged through the replication protocol in topology
 * messages, to keep every member (RS or DS) of the topology aware of the DS
 * topology.
 * <p>
 * @Immutable
 */
public final class DSInfo
{
  /** DS server id. */
  private final int dsId;
  /** DS server url. */
  private final String dsUrl;
  /** Server id of the RS that the DS is connected to. */
  private final int rsId;
  /** DS Generation Id. */
  private final long generationId;
  /** DS Status. */
  private final ServerStatus status;
  /** Assured replication enabled on DS or not. */
  private final boolean assuredFlag;
  /** DS assured mode (relevant if assured replication enabled). */
  private final AssuredMode assuredMode;
  /** DS safe data level (relevant if assured mode is safe data). */
  private final byte safeDataLevel;
  /** List of referrals URLs exported by the DS. */
  private final List<String> refUrls;
  /** Group id. */
  private final byte groupId;
  /** Protocol version. */
  private final short protocolVersion;

  private final Set<String> eclIncludes;
  private final Set<String> eclIncludesForDeletes;


  /**
   * Creates a new instance of DSInfo with every given info.
   *
   * @param dsId
   *          The DS id
   * @param dsUrl Url of the DS
   * @param rsId
   *          The RS id the DS is connected to
   * @param generationId
   *          The generation id the DS is using
   * @param status
   *          The DS status
   * @param assuredFlag
   *          DS assured replication enabled or not
   * @param assuredMode
   *          DS assured mode
   * @param safeDataLevel
   *          DS safe data level
   * @param groupId
   *          DS group id
   * @param refUrls
   *          DS exported referrals URLs
   * @param eclIncludes
   *          The list of entry attributes to include in the ECL.
   * @param eclIncludesForDeletes
   *          The list of entry attributes to include in the ECL for deletes.
   * @param protocolVersion
   *          Protocol version supported by this server.
   */
  public DSInfo(int dsId, String dsUrl, int rsId, long generationId,
      ServerStatus status, boolean assuredFlag,
      AssuredMode assuredMode, byte safeDataLevel, byte groupId,
      Collection<String> refUrls, Collection<String> eclIncludes,
      Collection<String> eclIncludesForDeletes, short protocolVersion)
  {
    this.dsId = dsId;
    this.dsUrl = dsUrl;
    this.rsId = rsId;
    this.generationId = generationId;
    this.status = status;
    this.assuredFlag = assuredFlag;
    this.assuredMode = assuredMode;
    this.safeDataLevel = safeDataLevel;
    this.groupId = groupId;
    this.refUrls = Collections.unmodifiableList(new ArrayList<String>(refUrls));
    this.eclIncludes =
        Collections.unmodifiableSet(new HashSet<String>(eclIncludes));
    this.eclIncludesForDeletes =
        Collections.unmodifiableSet(new HashSet<String>(eclIncludesForDeletes));
    this.protocolVersion = protocolVersion;
  }

  /**
   * Get the DS id.
   * @return the DS id
   */
  public int getDsId()
  {
    return dsId;
  }

  /**
   * Get the DS URL.
   * @return the DS URL
   */
  public String getDsUrl()
  {
    return dsUrl;
  }

  /**
   * Get the RS id the DS is connected to.
   * @return the RS id the DS is connected to
   */
  public int getRsId()
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
   * Get the entry attributes to be included in the ECL.
   * @return The entry attributes to be included in the ECL.
   */
  public Set<String> getEclIncludes()
  {
    return eclIncludes;
  }

  /**
   * Get the entry attributes to be included in the ECL for delete operations.
   * @return The entry attributes to be included in the ECL.
   */
  public Set<String> getEclIncludesForDeletes()
  {
    return eclIncludesForDeletes;
  }

  /**
   * Get the protocol version supported by this server.
   * Returns -1 when the protocol version is not known (too old version).
   * @return The protocol version.
   */
  public short getProtocolVersion()
  {
    return protocolVersion;
  }

  /**
   * Returns a new instance of {@link DSInfo} with the specified replication
   * server Id.
   *
   * @param rsId
   *          the replication server Id to set on the new DSInfo object.
   * @return a new instance of {@link DSInfo} with the specified replication
   *         server Id.
   */
  public DSInfo cloneWithReplicationServerId(int rsId)
  {
    return new DSInfo(dsId, dsUrl, rsId, generationId, status, assuredFlag,
        assuredMode, safeDataLevel, groupId, refUrls, eclIncludes,
        eclIncludesForDeletes, protocolVersion);
  }

  /**
   * Test if the passed object is equal to this one.
   * @param obj The object to test
   * @return True if both objects are equal
   */
  @Override
  public boolean equals(Object obj)
  {
    if (obj == null)
    {
      return false;
    }
    if (obj.getClass() != getClass())
    {
      return false;
    }
    final DSInfo dsInfo = (DSInfo) obj;
    return dsId == dsInfo.getDsId()
        && rsId == dsInfo.getRsId()
        && generationId == dsInfo.getGenerationId()
        && status == dsInfo.getStatus()
        && assuredFlag == dsInfo.isAssured()
        && assuredMode == dsInfo.getAssuredMode()
        && safeDataLevel == dsInfo.getSafeDataLevel()
        && groupId == dsInfo.getGroupId()
        && protocolVersion == dsInfo.getProtocolVersion()
        && refUrls.equals(dsInfo.getRefUrls())
        && Objects.equals(eclIncludes, dsInfo.getEclIncludes())
        && Objects.equals(eclIncludesForDeletes, dsInfo.getEclIncludesForDeletes());
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
    hash = 73 * hash + (this.eclIncludes != null ? eclIncludes.hashCode() : 0);
    hash = 73 * hash + (this.eclIncludesForDeletes != null ?
        eclIncludesForDeletes.hashCode() : 0);
    hash = 73 * hash + this.groupId;
    hash = 73 * hash + this.protocolVersion;
    return hash;
  }

  /**
   * Returns a string representation of the DS info.
   * @return A string representation of the DS info
   */
  @Override
  public String toString()
  {
    final StringBuilder sb = new StringBuilder();
    sb.append("DS id: ").append(dsId);
    sb.append(" ; DS url: ").append(dsUrl);
    sb.append(" ; RS id: ").append(rsId);
    sb.append(" ; Generation id: ").append(generationId);
    sb.append(" ; Status: ").append(status);
    sb.append(" ; Assured replication: ").append(assuredFlag);
    if (assuredFlag)
    {
      sb.append(" ; Assured mode: ").append(assuredMode);
      sb.append(" ; Safe data level: ").append(safeDataLevel);
    }
    sb.append(" ; Group id: ").append(groupId);
    sb.append(" ; Protocol version: ").append(protocolVersion);
    sb.append(" ; Referral URLs: ").append(refUrls);
    sb.append(" ; ECL Include: ").append(eclIncludes);
    sb.append(" ; ECL Include for Deletes: ").append(eclIncludesForDeletes);
    return sb.toString();
  }

}
