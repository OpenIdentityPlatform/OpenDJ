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

package org.opends.guitools.controlpanel.event;

/**
 * The event that describes that an element of the schema has been selected.
 * This is used in the dialog 'Manage Schema' to notify that a new schema
 * element is selected on the tree.
 *
 */
public class SchemaElementSelectionEvent
{
  private Object source;
  private Object schemaElement;

  /**
   * Constructor of the event.
   * @param source the source of the event.
   * @param schemaElement the schema element that has been selected.
   */
  public SchemaElementSelectionEvent(Object source, Object schemaElement)
  {
    this.source = source;
    this.schemaElement = schemaElement;
  }

  /**
   * Returns the schema element that has been selected.
   * @return the schema element that has been selected.
   */
  public Object getSchemaElement()
  {
    return schemaElement;
  }

  /**
   * Returns the source of the event.
   * @return the source of the event.
   */
  public Object getSource()
  {
    return source;
  }
}
