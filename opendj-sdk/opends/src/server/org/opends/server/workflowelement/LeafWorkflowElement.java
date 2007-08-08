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
package org.opends.server.workflowelement;



/**
 * This class gathers all the workflow elements that are encapsulating
 * physical repositories such as local database, remote LDAP servers,
 * JDBC repository or LDIF flat file.
 */
public abstract class LeafWorkflowElement
  extends WorkflowElement
{
  /**
   * Creates a new instance of the leaf workflow element.
   *
   * @param workflowElementID  the workflow element identifier as defined
   *                           in the configuration.
   */
  protected LeafWorkflowElement(String workflowElementID)
  {
    super(workflowElementID);
  }
}
