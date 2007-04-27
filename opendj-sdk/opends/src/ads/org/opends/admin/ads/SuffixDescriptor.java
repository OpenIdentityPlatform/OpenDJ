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

import java.util.HashSet;
import java.util.Set;

/**
 * The object of this class represent a topology of replicas across servers
 * that have the same suffix DN.  They may or might not be replicated.
 */
public class SuffixDescriptor
{
  private String suffixDN;
  private Set<ReplicaDescriptor> replicas = new HashSet<ReplicaDescriptor>();

  /**
   * Returns the DN associated with this suffix descriptor.
   * @return the DN associated with this suffix descriptor.
   */
  public String getDN()
  {
    return suffixDN;
  }

  /**
   * Sets the DN associated with this suffix descriptor.
   * @param suffixDN the DN associated with this suffix descriptor.
   */
  public void setDN(String suffixDN)
  {
    this.suffixDN = suffixDN;
  }

  /**
   * Returns the replicas associated with this SuffixDescriptor.
   * @return a Set containing the replicas associated with this
   * SuffixDescriptor.
   */
  public Set<ReplicaDescriptor> getReplicas()
  {
    return replicas;
  }

  /**
   * Sets the replicas associated with this SuffixDescriptor.
   * @param replicas a Set containing the replicas associated with this
   * SuffixDescriptor.
   */
  public void setReplicas(Set<ReplicaDescriptor> replicas)
  {
    this.replicas = replicas;
  }



  /**
   * {@inheritDoc}
   */
  public boolean equals(Object v)
  {
    // TODO: this is buggy code
    boolean equals = false;
    if (this != v)
    {
      if (v instanceof SuffixDescriptor)
      {
        SuffixDescriptor desc = (SuffixDescriptor)v;
        equals = getDN().equals(desc.getDN());

        if (equals)
        {
          equals = getReplicas().size() == desc.getReplicas().size();
        }

        if (equals)
        {
          for (ReplicaDescriptor repl : getReplicas())
          {
            boolean serverFound = false;
            ServerDescriptor server = repl.getServer();
            for (ReplicaDescriptor repl1 : desc.getReplicas())
            {
              serverFound = serversEqual(server, repl1.getServer());
              if (serverFound)
              {
                break;
              }
            }
            if (!serverFound)
            {
              equals = false;
              break;
            }
          }
        }

      }
    }
    else
    {
      equals = true;
    }
    return equals;
  }

  /**
   * Tells whether the two provided objects represent the same server or not.
   * @param serv1 the first ServerDescriptor to compare.
   * @param serv2 the second ServerDescriptor to compare.
   * @return <CODE>true</CODE> if the two objects represent the same server
   * and <CODE>false</CODE> otherwise.
   */
  private boolean serversEqual(ServerDescriptor serv1, ServerDescriptor serv2)
  {
    return serv1.getAdsProperties().equals(serv2.getAdsProperties());
  }
  /**
   * {@inheritDoc}
   */
  public int hashCode()
  {
//  FIXME: this is buggy code
    return getDN().hashCode();
  }
}
