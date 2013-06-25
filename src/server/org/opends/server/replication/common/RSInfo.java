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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012 ForgeRock AS
 */
package org.opends.server.replication.common;

/**
 * This class holds information about a RS connected to the topology. This
 * information is to be exchanged through the replication protocol in
 * topology messages, to keep every member DS of the topology aware of
 * the RS topology.
 */
public class RSInfo
{
  // Server id of the RS
  private int id = -1;
  // Generation Id of the RS
  private long generationId = -1;
  // Group id of the RS
  private byte groupId = (byte) -1;
  // The weight of the RS
  // It is important to keep the default value to 1 so that it is used as
  // default value for a RS using protocol V3: this default value will be used
  // in algorithms that use weight
  private int weight = 1;
  // The server URL of the RS
  private String serverUrl = null;

  /**
   * Creates a new instance of RSInfo with every given info.
   *
   * @param id The RS id
   * @param serverUrl Url of the RS
   * @param generationId The generation id the RS is using
   * @param groupId RS group id
   * @param weight RS weight
   */
  public RSInfo(int id, String serverUrl,
    long generationId, byte groupId, int weight)
  {
    this.id = id;
    this.serverUrl = serverUrl;
    this.generationId = generationId;
    this.groupId = groupId;
    this.weight = weight;
  }

  /**
   * Get the RS id.
   * @return the RS id
   */
  public int getId()
  {
    return id;
  }

  /**
   * Get the generation id RS is using.
   * @return the generation id RS is using.
   */
  public long getGenerationId()
  {
    return generationId;
  }

  /**
   * Get the RS group id.
   * @return The RS group id
   */
  public byte getGroupId()
  {
    return groupId;
  }

  /**
   * Get the RS weight.
   * @return The RS weight
   */
  public int getWeight()
  {
    return weight;
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
      RSInfo rsInfo = (RSInfo) obj;
      return ((id == rsInfo.getId()) &&
        (generationId == rsInfo.getGenerationId()) &&
        (groupId == rsInfo.getGroupId()) &&
        (weight == rsInfo.getWeight()) &&
        (((serverUrl == null) && (rsInfo.getServerUrl() == null)) ||
        ((serverUrl != null) && (rsInfo.getServerUrl() != null) &&
        (serverUrl.equals(rsInfo.getServerUrl())))));
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
    hash = 17 * hash + this.id;
    hash = 17 * hash + (int) (this.generationId ^ (this.generationId >>> 32));
    hash = 17 * hash + this.groupId;
    hash = 17 * hash + this.weight;
    hash = 17 * hash + (this.serverUrl != null ? this.serverUrl.hashCode() : 0);
    return hash;
  }

  /**
   * Gets the server URL.
   * @return the serverUrl
   */
  public String getServerUrl()
  {
    return serverUrl;
  }

  /**
   * Returns a string representation of the DS info.
   * @return A string representation of the DS info
   */
  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("\nId: ");
    sb.append(id);
    sb.append(" ; Server URL: ");
    sb.append(serverUrl);
    sb.append(" ; Generation id: ");
    sb.append(generationId);
    sb.append(" ; Group id: ");
    sb.append(groupId);
    sb.append(" ; Weight: ");
    sb.append(weight);
    return sb.toString();
  }
}
