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
package org.opends.server.core.networkgroups;

import org.opends.server.core.*;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * This classes defines a list of naming contexts for a network group.
 */
public class NetworkGroupNamingContexts
{
  // List of naming contexts.
  private List<WorkflowTopologyNode> namingContexts;
  // If list of naming contexts is returned, ensure it is immutable
  private List<WorkflowTopologyNode> _namingContexts;

  // List of public naming contexts.
  private List<WorkflowTopologyNode> publicNamingContexts;
  // If list of public naming contexts is returned, ensure it is immutable
  private List<WorkflowTopologyNode> _publicNamingContexts;

  // List of private naming contexts.
  private List<WorkflowTopologyNode> privateNamingContexts;
  // If list of private naming contexts is returned, ensure it is immutable
  private List<WorkflowTopologyNode> _privateNamingContexts;

  /**
   * Create a list of naming contexts for a network group.
   */
  public NetworkGroupNamingContexts()
  {
    namingContexts  = new CopyOnWriteArrayList<WorkflowTopologyNode>();
    _namingContexts = Collections.unmodifiableList(namingContexts);

    privateNamingContexts  = new CopyOnWriteArrayList<WorkflowTopologyNode>();
    _privateNamingContexts =
                            Collections.unmodifiableList(privateNamingContexts);

    publicNamingContexts  = new CopyOnWriteArrayList<WorkflowTopologyNode>();
    _publicNamingContexts = Collections.unmodifiableList(publicNamingContexts);
  }


  /**
   * Reset the list of naming contexts.
   */
  public void resetLists()
  {
    namingContexts.clear();
    privateNamingContexts.clear();
    publicNamingContexts.clear();
  }


  /**
   * Add a workflow in the list of naming context.
   *
   * @param workflow  the workflow to add in the list of naming contexts
   */
  public void addNamingContext (
      WorkflowTopologyNode workflow
      )
  {
    // add the workflow to the list of naming context
    namingContexts.add (workflow);

    // add the workflow to the private/public list of naming contexts
    if (workflow.isPrivate())
    {
      privateNamingContexts.add (workflow);
    }
    else
    {
      publicNamingContexts.add (workflow);
    }
  }


  /**
   * Get the list of naming contexts.
   *
   * <br>Note: the returned iterable instance is immutable and attempts to
   * remove elements will throw an UnsupportedOperationException exception.
   *
   * @return the list of all the naming contexts
   */
  public Iterable<WorkflowTopologyNode> getNamingContexts()
  {
    return _namingContexts;
  }


  /**
   * Get the list of private naming contexts.
   *
   * <br>Note: the returned iterable instance is immutable and attempts to
   * remove elements will throw an UnsupportedOperationException exception.
   *
   * @return the list of private naming contexts
   */
  public Iterable<WorkflowTopologyNode> getPrivateNamingContexts()
  {
    return _privateNamingContexts;
  }


  /**
   * Get the list of public naming contexts.
   *
   * <br>Note: the returned iterable instance is immutable and attempts to
   * remove elements will throw an UnsupportedOperationException exception.
   *
   * @return the list of public naming contexts
   */
  public Iterable<WorkflowTopologyNode> getPublicNamingContexts()
  {
    return _publicNamingContexts;
  }


  /**
   * Dumps info from the current networkk group for debug purpose.
   *
   * @param  leftMargin  white spaces used to indent traces
   * @return a string buffer that contains trace information
   */
  public StringBuilder toString (String leftMargin)
  {
    StringBuilder sb = new StringBuilder();
    String newMargin = leftMargin + "   ";

    sb.append (leftMargin + "List of naming contexts:\n");
    for (WorkflowTopologyNode w: namingContexts)
    {
      sb.append (w.toString (newMargin));
    }

    sb.append (leftMargin + "List of PRIVATE naming contexts:\n");
    for (WorkflowTopologyNode w: privateNamingContexts)
    {
      sb.append (w.toString (newMargin));
    }

    sb.append (leftMargin + "List of PUBLIC naming contexts:\n");
    for (WorkflowTopologyNode w: publicNamingContexts)
    {
      sb.append (w.toString (newMargin));
    }

    return sb;
  }

}
