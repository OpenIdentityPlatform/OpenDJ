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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.server.core.networkgroups;



import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.RequestFilteringQOSPolicyCfgDefn.AllowedOperations;
import org.opends.server.admin.std.meta.RequestFilteringQOSPolicyCfgDefn.AllowedSearchScopes;
import org.opends.server.admin.std.server.QOSPolicyCfg;
import org.opends.server.admin.std.server.RequestFilteringQOSPolicyCfg;
import org.opends.server.types.DN;



/**
 * Stub configuration used in tests.
 */
public abstract class MockRequestFilteringQOSPolicyCfg implements
    RequestFilteringQOSPolicyCfg
{

  /**
   * {@inheritDoc}
   */
  public final void addRequestFilteringChangeListener(
      ConfigurationChangeListener<RequestFilteringQOSPolicyCfg> listener)
  {
    // Stub.
  }



  /**
   * {@inheritDoc}
   */
  public final Class<? extends RequestFilteringQOSPolicyCfg> configurationClass()
  {
    // Stub.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public final String getJavaClass()
  {
    // Stub.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public final void removeRequestFilteringChangeListener(
      ConfigurationChangeListener<RequestFilteringQOSPolicyCfg> listener)
  {
    // Stub.
  }



  /**
   * {@inheritDoc}
   */
  public final void addChangeListener(
      ConfigurationChangeListener<QOSPolicyCfg> listener)
  {
    // Stub.
  }



  /**
   * {@inheritDoc}
   */
  public final void removeChangeListener(
      ConfigurationChangeListener<QOSPolicyCfg> listener)
  {
    // Stub.
  }



  /**
   * {@inheritDoc}
   */
  public final DN dn()
  {
    // Stub.
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public SortedSet<String> getAllowedAttributes()
  {
    return Collections.unmodifiableSortedSet(new TreeSet<String>());
  }



  /**
   * {@inheritDoc}
   */
  public SortedSet<AllowedOperations> getAllowedOperations()
  {
    return Collections
        .unmodifiableSortedSet(new TreeSet<AllowedOperations>());
  }



  /**
   * {@inheritDoc}
   */
  public SortedSet<AllowedSearchScopes> getAllowedSearchScopes()
  {
    return Collections
        .unmodifiableSortedSet(new TreeSet<AllowedSearchScopes>());
  }



  /**
   * {@inheritDoc}
   */
  public SortedSet<DN> getAllowedSubtrees()
  {
    return Collections.unmodifiableSortedSet(new TreeSet<DN>());
  }



  /**
   * {@inheritDoc}
   */
  public SortedSet<String> getProhibitedAttributes()
  {
    return Collections.unmodifiableSortedSet(new TreeSet<String>());
  }



  /**
   * {@inheritDoc}
   */
  public SortedSet<DN> getProhibitedSubtrees()
  {
    return Collections.unmodifiableSortedSet(new TreeSet<DN>());
  }

}
