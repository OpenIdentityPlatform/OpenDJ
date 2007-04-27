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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.admin.ads;

/**
 * The object of this class represent a Replica (i.e. a suffix in a given
 * server).
 */
public class ReplicaDescriptor
{
  private SuffixDescriptor suffix;
  private int entries = -1;
  private ServerDescriptor server;

  /**
   * Returns the number of entries contained in the replica.
   * @return the number of entries contained in the replica.
   */
  public int getEntries()
  {
    return entries;
  }

  /**
   * Sets the number of entries contained in the replica.
   * @param entries the number of entries contained in the replica.
   */
  public void setEntries(int entries)
  {
    this.entries = entries;
  }

  /**
   * Returns the ServerDescriptor object associated with the server where this
   * replica is located.
   * @return the ServerDescriptor object associated with the server where this
   * replica is located.
   */
  public ServerDescriptor getServer()
  {
    return server;
  }

  /**
   * Sets the server where this replica is located.
   * @param server the ServerDescriptor object associated with the server where
   * this replica is located.
   */
  public void setServer(ServerDescriptor server)
  {
    this.server = server;
  }

  /**
   * Returns the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   * @return the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   */
  public SuffixDescriptor getSuffix()
  {
    return suffix;
  }

  /**
   * Sets the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   * @param suffix the SuffixDescriptor object representing the suffix topology
   * across servers to which this replica belongs.
   */
  public void setSuffix(SuffixDescriptor suffix)
  {
    this.suffix = suffix;
  }
}
