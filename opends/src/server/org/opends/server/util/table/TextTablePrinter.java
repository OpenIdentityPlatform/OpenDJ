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



import static org.opends.server.util.ServerConstants.*;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * An interface for creating a text based table. Tables have
 * configurable column widths, padding, and column separators.
 */
public final class TextTablePrinter extends TablePrinter {

  /**
   * Table serializer implementation.
   */
  private static final class Serializer extends TableSerializer {

    // The current column being output.
    private int column = 0;

    // The string which should be used to separate one column
    // from the next (not including padding).
    private final String columnSeparator;

    // The real column widths taking into account size constraints but
    // not including padding or separators.
    private final List<Integer> columnWidths = new ArrayList<Integer>();

    // The cells in the current row.
    private final List<String> currentRow = new ArrayList<String>();

    // Indicates whether or not the headings should be output.
    private final boolean displayHeadings;

    // Table indicating whether or not a column is fixed width.
    private final Map<Integer, Integer> fixedColumns;

    // The character which should be used to separate the table
    // heading row from the rows beneath.
    private final char headingSeparator;

    // The padding which will be used to separate a cell's
    // contents from its adjacent column separators.
    private final int padding;

    // Width of the table in columns.
    private int totalColumns = 0;

    // Total permitted width for the table which expandable columns
    // can use up.
    private final int totalWidth;

    // The output writer.
    private final PrintWriter writer;



    // Private constructor.
    private Serializer(PrintWriter writer, String columnSeparator,
        Map<Integer, Integer> fixedColumns, boolean displayHeadings,
        char headingSeparator, int padding, int totalWidth) {
      this.writer = writer;
      this.columnSeparator = columnSeparator;
      this.fixedColumns = new HashMap<Integer, Integer>(fixedColumns);
      this.displayHeadings = displayHeadings;
      this.headingSeparator = headingSeparator;
      this.padding = padding;
      this.totalWidth = totalWidth;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void addCell(String s) {
      currentRow.add(s);
      column++;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void addColumn(int width) {
      columnWidths.add(width);
      totalColumns++;
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
        endRow();

        // Print the header separator.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < totalColumns; i++) {
          int width = columnWidths.get(i);
          if (totalColumns > 1) {
            if (i == 0 || i == (totalColumns - 1)) {
              // Only one lot of padding for first and last columns.
              width += padding;
            } else {
              width += padding * 2;
            }
          }

          for (int j = 0; j < width; j++) {
            builder.append(headingSeparator);
          }

          if (i < (totalColumns - 1)) {
            builder.append(columnSeparator);
          }
        }
        writer.println(builder.toString());
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void endRow() {
      boolean isRemainingText;
      do {
        isRemainingText = false;
        for (int i = 0; i < currentRow.size(); i++) {
          int width = columnWidths.get(i);
          String contents = currentRow.get(i);

          // Determine what parts of contents can be displayed on this
          // line.
          String head;
          String tail = null;

          if (contents == null) {
            // This cell has been displayed fully.
            head = "";
          } else if (contents.length() > width) {
            // We're going to have to split the cell on next word
            // boundary.
            int endIndex = contents.lastIndexOf(' ', width);
            if (endIndex == -1) {
              // Problem - we have a word which is too big to fit in
              // the
              // cell.
              head = contents.substring(0, width);
              tail = contents.substring(width);
            } else {
              head = contents.substring(0, endIndex);
              tail = contents.substring(endIndex + 1);
            }
          } else {
            // The contents fits ok.
            head = contents;
          }

          // Display this line.
          StringBuilder builder = new StringBuilder();
          if (i > 0) {
            // Add right padding for previous cell.
            for (int j = 0; j < padding; j++) {
              builder.append(' ');
            }

            // Add separator.
            builder.append(columnSeparator);

            // Add left padding for this cell.
            for (int j = 0; j < padding; j++) {
              builder.append(' ');
            }
          }

          // Add cell contents.
          builder.append(head);

          // Now pad with extra space to make up the width.
          for (int j = head.length(); j < width; j++) {
            builder.append(' ');
          }

          writer.print(builder.toString());

          // Update the row contents.
          currentRow.set(i, tail);
          if (tail != null) {
            isRemainingText = true;
          }
        }
        writer.println();
      } while (isRemainingText);
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
      determineColumnWidths();

      column = 0;
      currentRow.clear();
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public void startRow() {
      column = 0;
      currentRow.clear();
    }



    // We need to calculate the effective width of each column.
    private void determineColumnWidths() {
      // First calculate the minimum width so that we know how much
      // expandable columns can expand.
      int minWidth = 0;
      int expandableColumnSize = 0;

      for (int i = 0; i < totalColumns; i++) {
        int actualSize = columnWidths.get(i);

        if (fixedColumns.containsKey(i)) {
          int requestedSize = fixedColumns.get(i);

          if (requestedSize == 0) {
            expandableColumnSize += actualSize;
          } else {
            columnWidths.set(i, requestedSize);
            minWidth += requestedSize;
          }
        } else {
          minWidth += actualSize;
        }

        // Must also include padding and separators.
        if (i > 0) {
          minWidth += padding * 2 + columnSeparator.length();
        }
      }

      if (minWidth > totalWidth) {
        // The table is too big: leave expandable columns at their
        // requested width, as there's not much else that can be done.
      } else {
        int available = totalWidth - minWidth;

        if (expandableColumnSize > available) {
          // Only modify column sizes if necessary.
          for (int i = 0; i < totalColumns; i++) {
            int actualSize = columnWidths.get(i);

            if (fixedColumns.containsKey(i)) {
              int requestedSize = fixedColumns.get(i);
              if (requestedSize == 0) {
                // Calculate size based on requested actual size as a
                // proportion of the total.
                requestedSize =
                  ((actualSize * available) / expandableColumnSize);
                columnWidths.set(i, requestedSize);
              }
            }
          }
        }
      }
    }
  }

  /**
   * The default string which should be used to separate one column
   * from the next (not including padding).
   */
  private static final String DEFAULT_COLUMN_SEPARATOR = "";

  /**
   * The default character which should be used to separate the table
   * heading row from the rows beneath.
   */
  private static final char DEFAULT_HEADING_SEPARATOR = '-';

  /**
   * The default padding which will be used to separate a cell's
   * contents from its adjacent column separators.
   */
  private static final int DEFAULT_PADDING = 1;

  // The string which should be used to separate one column
  // from the next (not including padding).
  private String columnSeparator = DEFAULT_COLUMN_SEPARATOR;

  // Indicates whether or not the headings should be output.
  private boolean displayHeadings = true;

  // Table indicating whether or not a column is fixed width.
  private final Map<Integer, Integer> fixedColumns =
    new HashMap<Integer, Integer>();

  // The character which should be used to separate the table
  // heading row from the rows beneath.
  private char headingSeparator = DEFAULT_HEADING_SEPARATOR;

  // The padding which will be used to separate a cell's
  // contents from its adjacent column separators.
  private int padding = DEFAULT_PADDING;

  // Total permitted width for the table which expandable columns
  // can use up.
  private int totalWidth = MAX_LINE_WIDTH;

  // The output destination.
  private PrintWriter writer = null;



  /**
   * Creates a new text table printer for the specified output stream.
   * The text table printer will have the following initial settings:
   * <ul>
   * <li>headings will be displayed
   * <li>no separators between columns
   * <li>columns are padded by one character
   * </ul>
   *
   * @param stream
   *          The stream to output tables to.
   */
  public TextTablePrinter(OutputStream stream) {
    this(new BufferedWriter(new OutputStreamWriter(stream)));
  }



  /**
   * Creates a new text table printer for the specified writer. The
   * text table printer will have the following initial settings:
   * <ul>
   * <li>headings will be displayed
   * <li>no separators between columns
   * <li>columns are padded by one character
   * </ul>
   *
   * @param writer
   *          The writer to output tables to.
   */
  public TextTablePrinter(Writer writer) {
    this.writer = new PrintWriter(writer);
  }



  /**
   * Sets the column separator which should be used to separate one
   * column from the next (not including padding).
   *
   * @param columnSeparator
   *          The column separator.
   */
  public final void setColumnSeparator(String columnSeparator) {
    this.columnSeparator = columnSeparator;
  }



  /**
   * Set the maximum width for a column. If a cell is too big to fit
   * in its column then it will be wrapped.
   *
   * @param column
   *          The column to make fixed width (0 is the first column).
   * @param width
   *          The width of the column (this should not include column
   *          separators or padding), or <code>0</code> to indicate
   *          that this column should be expandable.
   * @throws IllegalArgumentException
   *           If column is less than 0 .
   */
  public final void setColumnWidth(int column, int width)
      throws IllegalArgumentException {
    if (column < 0) {
      throw new IllegalArgumentException("Negative column " + column);
    }

    if (width < 0) {
      throw new IllegalArgumentException("Negative width " + width);
    }

    fixedColumns.put(column, width);
  }



  /**
   * Specify whether the column headings should be displayed or not.
   *
   * @param displayHeadings
   *          <code>true</code> if column headings should be
   *          displayed.
   */
  public final void setDisplayHeadings(boolean displayHeadings) {
    this.displayHeadings = displayHeadings;
  }



  /**
   * Sets the heading separator which should be used to separate the
   * table heading row from the rows beneath.
   *
   * @param headingSeparator
   *          The heading separator.
   */
  public final void setHeadingSeparator(char headingSeparator) {
    this.headingSeparator = headingSeparator;
  }



  /**
   * Sets the padding which will be used to separate a cell's contents
   * from its adjacent column separators.
   *
   * @param padding
   *          The padding.
   */
  public final void setPadding(int padding) {
    this.padding = padding;
  }



  /**
   * Sets the total permitted width for the table which expandable
   * columns can use up.
   *
   * @param totalWidth
   *          The total width.
   */
  public final void setTotalWidth(int totalWidth) {
    this.totalWidth = totalWidth;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  protected TableSerializer getSerializer() {
    return new Serializer(writer, columnSeparator, fixedColumns,
        displayHeadings, headingSeparator, padding, totalWidth);
  }
}
