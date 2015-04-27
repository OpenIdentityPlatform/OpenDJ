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
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package com.forgerock.opendj.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;

/**
 * A class which can be used to construct tables of information to be displayed in a terminal.
 * Once built the table can be output using a {@link TableSerializer}.
 */
public final class TableBuilder {

    /**
     * The current column number in the current row where 0 represents
     * the left-most column in the table.
     */
    private int column;

    /** The current with of each column. */
    private List<Integer> columnWidths = new ArrayList<>();

    /** The list of column headings. */
    private List<LocalizableMessage> header = new ArrayList<>();

    /** The current number of rows in the table. */
    private int height;

    /** The list of table rows. */
    private List<List<String>> rows = new ArrayList<>();

    /** The linked list of sort keys comparators. */
    private List<Comparator<String>> sortComparators = new ArrayList<>();

    /** The linked list of sort keys. */
    private List<Integer> sortKeys = new ArrayList<>();

    /** The current number of columns in the table. */
    private int width;

    /**
     * Creates a new table printer.
     */
    public TableBuilder() {
        // No implementation required.
    }

    /**
     * Adds a table sort key. The table will be sorted according to the case-insensitive string ordering of the cells in
     * the specified column.
     *
     * @param column
     *            The column which will be used as a sort key.
     */
    public void addSortKey(int column) {
        addSortKey(column, String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * Adds a table sort key. The table will be sorted according to the provided string comparator.
     *
     * @param column
     *            The column which will be used as a sort key.
     * @param comparator
     *            The string comparator.
     */
    public void addSortKey(int column, Comparator<String> comparator) {
        sortKeys.add(column);
        sortComparators.add(comparator);
    }

    /**
     * Appends a new blank cell to the current row.
     */
    public void appendCell() {
        appendCell("");
    }

    /**
     * Appends a new cell to the current row containing the provided boolean value.
     *
     * @param value
     *            The boolean value.
     */
    public void appendCell(boolean value) {
        appendCell(String.valueOf(value));
    }

    /**
     * Appends a new cell to the current row containing the provided byte value.
     *
     * @param value
     *            The byte value.
     */
    public void appendCell(byte value) {
        appendCell(String.valueOf(value));
    }

    /**
     * Appends a new cell to the current row containing the provided char value.
     *
     * @param value
     *            The char value.
     */
    public void appendCell(char value) {
        appendCell(String.valueOf(value));
    }

    /**
     * Appends a new cell to the current row containing the provided double value.
     *
     * @param value
     *            The double value.
     */
    public void appendCell(double value) {
        appendCell(String.valueOf(value));
    }

    /**
     * Appends a new cell to the current row containing the provided float value.
     *
     * @param value
     *            The float value.
     */
    public void appendCell(float value) {
        appendCell(String.valueOf(value));
    }

    /**
     * Appends a new cell to the current row containing the provided integer value.
     *
     * @param value
     *            The boolean value.
     */
    public void appendCell(int value) {
        appendCell(String.valueOf(value));
    }

    /**
     * Appends a new cell to the current row containing the provided long value.
     *
     * @param value
     *            The long value.
     */
    public void appendCell(long value) {
        appendCell(String.valueOf(value));
    }

    /**
     * Appends a new cell to the current row containing the provided object value.
     *
     * @param value
     *            The object value.
     */
    public void appendCell(Object value) {
        // Make sure that the first row has been created.
        if (height == 0) {
            startRow();
        }

        // Create the cell.
        String s = String.valueOf(value);
        rows.get(height - 1).add(s);
        column++;

        // Update statistics.
        if (column > width) {
            width = column;
            columnWidths.add(s.length());
        } else if (columnWidths.get(column - 1) < s.length()) {
            columnWidths.set(column - 1, s.length());
        }
    }

    /**
     * Appends a new blank column heading to the header row.
     */
    public void appendHeading() {
        appendHeading(LocalizableMessage.EMPTY);
    }

    /**
     * Appends a new column heading to the header row.
     *
     * @param value
     *            The column heading value.
     */
    public void appendHeading(LocalizableMessage value) {
        header.add(value);

        // Update statistics.
        if (header.size() > width) {
            width = header.size();
            columnWidths.add(value.length());
        } else if (columnWidths.get(header.size() - 1) < value.length()) {
            columnWidths.set(header.size() - 1, value.length());
        }
    }

    /**
     * Gets the width of the current row.
     *
     * @return Returns the width of the current row.
     */
    public int getRowWidth() {
        return column;
    }

    /**
     * Gets the number of rows in table.
     *
     * @return Returns the number of rows in table.
     */
    public int getTableHeight() {
        return height;
    }

    /**
     * Gets the number of columns in table.
     *
     * @return Returns the number of columns in table.
     */
    public int getTableWidth() {
        return width;
    }

    /**
     * Prints the table in its current state using the provided table printer.
     *
     * @param printer
     *            The table printer.
     */
    public void print(TablePrinter printer) {
        // Create a new printer instance.
        TableSerializer serializer = printer.getSerializer();

        // First sort the table.
        List<List<String>> sortedRows = new ArrayList<>(rows);

        Comparator<List<String>> comparator = new Comparator<List<String>>() {

            public int compare(List<String> row1, List<String> row2) {
                for (int i = 0; i < sortKeys.size(); i++) {
                    String cell1 = row1.get(sortKeys.get(i));
                    String cell2 = row2.get(sortKeys.get(i));

                    int rc = sortComparators.get(i).compare(cell1, cell2);
                    if (rc != 0) {
                        return rc;
                    }
                }

                // Both rows are equal.
                return 0;
            }

        };

        Collections.sort(sortedRows, comparator);

        // Now output the table.
        serializer.startTable(height, width);
        for (int i = 0; i < width; i++) {
            serializer.addColumn(columnWidths.get(i));
        }

        // Column headings.
        serializer.startHeader();
        for (LocalizableMessage s : header) {
            serializer.addHeading(s.toString());
        }
        serializer.endHeader();

        // Table contents.
        serializer.startContent();
        for (List<String> row : sortedRows) {
            serializer.startRow();

            // Print each cell in the row, padding missing trailing cells.
            for (int i = 0; i < width; i++) {
                if (i < row.size()) {
                    serializer.addCell(row.get(i));
                } else {
                    serializer.addCell("");
                }
            }

            serializer.endRow();
        }
        serializer.endContent();
        serializer.endTable();
    }

    /**
     * Appends a new row to the table.
     */
    public void startRow() {
        rows.add(new ArrayList<String>());
        height++;
        column = 0;
    }
}
