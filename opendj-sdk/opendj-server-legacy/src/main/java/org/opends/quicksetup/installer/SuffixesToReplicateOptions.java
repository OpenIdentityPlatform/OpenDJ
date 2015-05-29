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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS
 */
package org.opends.quicksetup.installer;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.opends.admin.ads.SuffixDescriptor;
import org.opends.server.tools.BackendTypeHelper.BackendTypeUIAdapter;

/**
 * This class is used to provide a data model for the Suffix to Replicate
 * Options panel of the installer.
 */
public class SuffixesToReplicateOptions
{
  /**
   * This enumeration is used to know what the user wants to do for the data
   * (import data or not, what use as source of the data...).
   */
  public enum Type
  {
    /** Do not replicate suffix. */
    NO_SUFFIX_TO_REPLICATE,

    /** This is a new suffix in topology.. */
    NEW_SUFFIX_IN_TOPOLOGY,

    /** Replicate Contents of the new Suffix with existings server. */
    REPLICATE_WITH_EXISTING_SUFFIXES
  }

  private Type type;
  private Set<SuffixDescriptor> availableSuffixes;
  private Set<SuffixDescriptor> suffixesToReplicate;
  private Map<String, BackendTypeUIAdapter> backendsToReplicate;

  /**
   * Constructor for the SuffixesToReplicateOptions object.
   *
   * @param type
   *          the Type of DataReplicationOptions.
   * @param availableSuffixes
   *          The set of suffixes which are available for replication.
   * @param suffixesToReplicate
   *          The set of suffixes which user wants to replicate.
   */
  public SuffixesToReplicateOptions(Type type, Set<SuffixDescriptor> availableSuffixes,
      Set<SuffixDescriptor> suffixesToReplicate)
  {
    this(type, availableSuffixes, suffixesToReplicate, new HashMap<String, BackendTypeUIAdapter>());
  }

  /**
   * Constructor for the SuffixesToReplicateOptions object.
   *
   * @param type
   *          the Type of DataReplicationOptions.
   * @param availableSuffixes
   *          The set of suffixes which are available for replication.
   * @param suffixesToReplicate
   *          The set of suffixes which user wants to replicate.
   * @param backendsToReplicate
   *          The map with backend name as keys and their associated backend type
   *          as value.
   */
  public SuffixesToReplicateOptions(Type type, Set<SuffixDescriptor> availableSuffixes,
      Set<SuffixDescriptor> suffixesToReplicate, Map<String, BackendTypeUIAdapter> backendsToReplicate)
  {
    this.type = type;
    this.availableSuffixes = new LinkedHashSet<>(availableSuffixes);
    this.suffixesToReplicate = new LinkedHashSet<>(suffixesToReplicate);
    this.backendsToReplicate = new HashMap<>(backendsToReplicate);
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
   * Returns the set of suffixes available for replication.
   *
   * @return the set of suffixes available for replication.
   */
  public Set<SuffixDescriptor> getAvailableSuffixes()
  {
    return availableSuffixes;
  }

  /**
   * The set of suffixes that we must replicate with.
   *
   * @return the set of suffixes that we must replicate with.
   */
  public Set<SuffixDescriptor> getSuffixes()
  {
    return suffixesToReplicate;
  }

  /**
   * Returns a map which associate backend names and backend types.
   *
   * @return A map which associate backend names and backend types.
   */
  public Map<String, BackendTypeUIAdapter> getSuffixBackendTypes()
  {
    return backendsToReplicate;
  }
}
