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
package org.opends.server.util.table;



/**
 * An interface for incrementally configuring a table serializer. Once
 * configured, the table printer can be used to create a new
 * {@link TableSerializer} instance using the {@link #getSerializer()}
 * method.
 */
public abstract class TablePrinter {

  /**
   * Creates a new abstract table printer.
   */
  protected TablePrinter() {
    // No implementation required.
  }



  /**
   * Creates a new table serializer based on the configuration of this
   * table printer.
   *
   * @return Returns a new table serializer based on the configuration
   *         of this table printer.
   */
  protected abstract TableSerializer getSerializer();
}
