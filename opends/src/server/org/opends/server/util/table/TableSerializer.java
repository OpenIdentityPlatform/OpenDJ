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
 * An interface for serializing tables.
 * <p>
 * The default implementation for each method is to do nothing.
 * Implementations must override methods as required.
 */
public abstract class TableSerializer {

  /**
   * Create a new table serializer.
   */
  protected TableSerializer() {
    // No implementation required.
  }



  /**
   * Prints a table cell.
   *
   * @param s
   *          The cell contents.
   */
  public void addCell(String s) {
    // Default implementation.
  }



  /**
   * Defines a column in the table.
   *
   * @param width
   *          The width of the column in characters.
   */
  public void addColumn(int width) {
    // Default implementation.
  }



  /**
   * Prints a column heading.
   *
   * @param s
   *          The column heading.
   */
  public void addHeading(String s) {
    // Default implementation.
  }



  /**
   * Finish printing the table contents.
   */
  public void endContent() {
    // Default implementation.
  }



  /**
   * Finish printing the column headings.
   */
  public void endHeader() {
    // Default implementation.
  }



  /**
   * Finish printing the current row of the table.
   */
  public void endRow() {
    // Default implementation.
  }



  /**
   * Finish printing the table.
   */
  public void endTable() {
    // Default implementation.
  }



  /**
   * Prepare to start printing the table contents.
   */
  public void startContent() {
    // Default implementation.
  }



  /**
   * Prepare to start printing the column headings.
   */
  public void startHeader() {
    // Default implementation.
  }



  /**
   * Prepare to start printing a new row of the table.
   */
  public void startRow() {
    // Default implementation.
  }



  /**
   * Start a new table having the specified number of rows and
   * columns.
   *
   * @param height
   *          The number of rows in the table.
   * @param width
   *          The number of columns in the table.
   */
  public void startTable(int height, int width) {
    // Default implementation.
  }

}
