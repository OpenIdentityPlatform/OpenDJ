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



import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;



/**
 * An interface for creating a tab-separated formatted table.
 * <p>
 * This table printer will replace any tab, line-feeds, or carriage
 * return control characters encountered in a cell with a single
 * space.
 */
public final class TabSeparatedTablePrinter extends TablePrinter {

  /**
   * Table serializer implementation.
   */
  private final class Serializer extends TableSerializer {

    // The current column being output.
    private int column = 0;

    // Counts the number of separators that should be output the next
    // time a non-empty cell is displayed. The tab separators are
    // not displayed immediately so that we can avoid displaying
    // unnecessary trailing separators.
    private int requiredSeparators = 0;



    // Private constructor.
    private Serializer() {
      // No implementation required.
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void addCell(String s) {
      // Avoid printing tab separators for trailing empty cells.
      if (s.length() == 0) {
        requiredSeparators++;
      } else {
        for (int i = 0; i < requiredSeparators; i++) {
          writer.print('\t');
        }
        requiredSeparators = 1;
      }

      // Replace all new-lines and tabs with a single space.
      writer.print(s.replaceAll("[\\t\\n\\r]", " "));
      column++;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeading(String s) {
      if (displayHeadings) {
        addCell(s);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void endHeader() {
      if (displayHeadings) {
        writer.println();
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void endRow() {
      writer.println();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void endTable() {
      writer.flush();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void startHeader() {
      column = 0;
      requiredSeparators = 0;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void startRow() {
      column = 0;
      requiredSeparators = 0;
    }
  }

  // Indicates whether or not the headings should be output.
  private boolean displayHeadings = false;

  // The output destination.
  private PrintWriter writer = null;



  /**
   * Creates a new tab separated table printer for the specified
   * output stream. Headings will not be displayed by default.
   *
   * @param stream
   *          The stream to output tables to.
   */
  public TabSeparatedTablePrinter(OutputStream stream) {
    this(new BufferedWriter(new OutputStreamWriter(stream)));
  }



  /**
   * Creates a new tab separated table printer for the specified
   * writer. Headings will not be displayed by default.
   *
   * @param writer
   *          The writer to output tables to.
   */
  public TabSeparatedTablePrinter(Writer writer) {
    this.writer = new PrintWriter(writer);
  }



  /**
   * Specify whether or not table headings should be displayed.
   *
   * @param displayHeadings
   *          <code>true</code> if table headings should be
   *          displayed.
   */
  public void setDisplayHeadings(boolean displayHeadings) {
    this.displayHeadings = displayHeadings;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected TableSerializer getSerializer() {
    return new Serializer();
  }

}
