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


package org.opends.quicksetup.installer;

import java.util.HashSet;
import java.util.Set;

import org.opends.admin.ads.SuffixDescriptor;

/**
 * This class is used to provide a data model for the Suffix to Replicate
 * Options panel of the installer.
 *
 */
public class SuffixesToReplicateOptions
{
  /**
   * This enumeration is used to know what the user wants to do for the data
   * (import data or not, what use as source of the data...).
   *
   */
  public enum Type
  {
    /**
     * Do not replicate suffix.
     */
    NO_SUFFIX_TO_REPLICATE,
    /**
     * This is a new suffix in topology..
     */
    NEW_SUFFIX_IN_TOPOLOGY,
    /**
     * Replicate Contents of the new Suffix with existings server.
     */
    REPLICATE_WITH_EXISTING_SUFFIXES
  }

  private Type type;
  private Set<SuffixDescriptor> availableSuffixes;
  private Set<SuffixDescriptor> suffixesToReplicate;

  /**
   * Constructor for the SuffixesToReplicateOptions object.
   *
   * If the Data Replicate Options is NO_SUFFIX_TO_REPLICATE or
   * NEW_SUFFIX_IN_TOPOLOGY no args are considered.
   *
   * If the Data Options is REPLICATE_WITH_EXISTING_SUFFIXES a Set of
   * SuffixDescriptor is passed as argument.
   *
   * @param type the Type of DataReplicationOptions.
   * @param args the different argument objects (depending on the Type
   * specified)
   */
  public SuffixesToReplicateOptions(Type type, Object... args)
  {
    this.type = type;

    switch (type)
    {
    case REPLICATE_WITH_EXISTING_SUFFIXES:
      Set s = (Set)args[0];
      availableSuffixes = new HashSet<SuffixDescriptor>();
      for (Object o: s)
      {
        availableSuffixes.add((SuffixDescriptor)o);
      }
      s = (Set)args[1];
      suffixesToReplicate = new HashSet<SuffixDescriptor>();
      for (Object o: s)
      {
        suffixesToReplicate.add((SuffixDescriptor)o);
      }
      break;

    default:
      // If there is something put it.
      if ((args != null) && (args.length > 0))
      {
        s = (Set)args[0];
        availableSuffixes = new HashSet<SuffixDescriptor>();
        for (Object o: s)
        {
          availableSuffixes.add((SuffixDescriptor)o);
        }
        s = (Set)args[1];
        suffixesToReplicate = new HashSet<SuffixDescriptor>();
        for (Object o: s)
        {
          suffixesToReplicate.add((SuffixDescriptor)o);
        }
      }
    }
  }

  /**
   * Returns the type of SuffixesToReplicateOptions represented by this object
   * (replicate or not).
   *
   * @return the type of SuffixesToReplicateOptions.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Returns the set of suffixes that we must replicate with.
   * If there are no suffixes to replicate with returns null.
   *
   * @return the set of suffixes that we must replicate with.
   */
  public Set<SuffixDescriptor> getAvailableSuffixes()
  {
    return availableSuffixes;
  }

  /**
   * Returns the set of suffixes that we must replicate with.
   * If there are no suffixes to replicate with returns null.
   *
   * @return the set of suffixes that we must replicate with.
   */
  public Set<SuffixDescriptor> getSuffixes()
  {
    return suffixesToReplicate;
  }
}

