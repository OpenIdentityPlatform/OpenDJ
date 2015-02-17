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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS
 */

package org.opends.guitools.controlpanel.event;

/**
 * Interface that must be implemented by the objects that want to receive
 * notifications when a schema element has been selected.  This is used in the
 * dialog 'Manage Schema' to notify that a new schema element is selected on the
 * tree.
 *
 */
public interface SchemaElementSelectionListener
{
  /**
   * Notification that a schema element has been selected.
   * @param ev the schema element selection event.
   */
  void schemaElementSelected(SchemaElementSelectionEvent ev);
}
