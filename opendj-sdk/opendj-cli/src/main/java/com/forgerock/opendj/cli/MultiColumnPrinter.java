/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2012-2016 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.Utils.repeat;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.forgerock.util.Reject;

/**
 * Utility class for printing columns of data.
 * <p>
 * This printer can be used to print data in formatted table or in csv format.
 * <p>
 * Regarding the formatting table feature, this class allows you to specify for each {@link Column}s:
 * <ul>
 *     <li>A unique identifier</li>
 *     <li>The column title which will be printed by a call to {@link MultiColumnPrinter#printTitleLine()}</li>
 *     <li>The size (if a cell's data is bigger than the predefined size, then the data will not be truncated,
 *         i.e. it will overflow)</li>
 *     <li>The number of digits to keep (for {@link Double} data)</li>
 * </ul>
 * <p>
 * Code to write data is independent of the {@link MultiColumnPrinter} configuration:
 * <pre>
 * void printData(final MultiColumnPrinter printer) {
 *     String[][] myData = new String[][] {
 *         new String[]{"U.S.A", "34.2", "40.8", ".us"},
 *         new String[]{"United Kingdom", "261.1", "31.6", ".uk"},
 *         new String[]{"France", "98.8", "30.1", ".fr"}
 *     };
 *
 *     int i;
 *     for (String[] countryData : myData) {
 *         i = 0;
 *         for (final MultiColumnPrinter.Column column : printer.getColumns()) {
 *             printer.printData(countryData[i++]);
 *         }
 *     }
 *  }
 * </pre>
 * <p>
 * The following code sample presents how to create a {@link MultiColumnPrinter} to write CSV data:
 * <pre>
 * final List<MultiColumnPrinter.Column> columns = new ArrayList&lt;&gt;();
 * columns.add(MultiColumnPrinter.column("CountryNameColumnId", "country_name", 0));
 * columns.add(MultiColumnPrinter.column("populationDensityId", "population_density", 1));
 * columns.add(MultiColumnPrinter.column("GiniId", "gini", 1));
 * columns.add(MultiColumnPrinter.column("internetTLDId", "internet_tld", 0));
 * MultiColumnPrinter myCsvPrinter = MultiColumnPrinter.builder(System.out, columns)
 *                                                     .columnSeparator(",")
 *                                                     .build();
 * printData(myCsvPrinter);
 * </pre>
 * <p>
 * The code above would print:
 * <pre>
 * country_name,population_density,gini,internet_tld
 * U.S.A,34.2,40.8,.us
 * United Kingdom,261.1,31.6,.uk
 * France,98.8,30.1,.fr
 * </pre>
 * <p>
 * The following code sample presents how to configure a {@link MultiColumnPrinter}
 * to print the same data on console with some title headers.
 * <pre>
 *     final List<MultiColumnPrinter.Column> columns = new ArrayList&lt;&gt;();
 *     columns.add(MultiColumnPrinter.separatorColumn());
 *     columns.add(MultiColumnPrinter.column("CountryNameColumnId", "Country Name", 15, 0));
 *     columns.add(MultiColumnPrinter.column("populationDensityId", "Density", 10, 1));
 *     columns.add(MultiColumnPrinter.separatorColumn());
 *     columns.add(MultiColumnPrinter.column("GiniID", "GINI", 5, 1));
 *     columns.add(MultiColumnPrinter.column("internetTLDID", "TLD", 5, 0));
 *     columns.add(MultiColumnPrinter.separatorColumn());
 *     MultiColumnPrinter myPrinter = MultiColumnPrinter.builder(System.out, columns)
 *                                                      .format(true)
 *                                                      .columnSeparator("  ")
 *                                                      .titleAlignment(MultiColumnPrinter.Alignment.CENTER)
 *                                                      .build();
 *     myPrinter.printDashedLine();
 *     myPrinter.printTitleSection("General Information", 2);
 *     myPrinter.printTitleSection("Data", 2);
 *     myPrinter.printTitleLine();
 *     myPrinter.printDashedLine();
 *     printData(myPrinter);
 *     myPrinter.printDashedLine();
 * </pre>
 * <p>
 * The code above would print:
 * <pre>
 * --------------------------------------------------
 * |       General Information     |       Data     |
 * |     Country Name     Density  |   GINI    TLD  |
 * --------------------------------------------------
 * |            U.S.A        34.2  |   40.8    .us  |
 * |   United Kingdom       261.1  |   31.6    .uk  |
 * |           France        98.8  |   30.1    .fr  |
 * --------------------------------------------------
 * </pre>
 */
public final class MultiColumnPrinter {

    /** The data alignment. */
    public enum Alignment {
        /** Data will be left-aligned. */
        LEFT,
        /** Data will be centered. */
        CENTER,
        /** Data will be right-aligned. */
        RIGHT
    }

    private static final String SEPARATOR_ID = "separator";
    private static int separatorIdNumber;

    /**
     * Returns a new separator {@link Column}.
     * <p>
     * This kind of {@link Column} can be used to separate data sections.
     *
     * @return A new separator {@link Column}.
     */
    public static Column separatorColumn() {
        return new Column(SEPARATOR_ID + separatorIdNumber++, "", 1, 0);
    }

    /**
     * Creates a new {@link Column} with the provided arguments.
     *
     * @param id
     *      The column identifier.
     * @param title
     *      The column title.
     * @param doublePrecision
     *      The double precision used to print {@link Double} data for this column.
     *      See {@link MultiColumnPrinter#printData(Double)}.
     * @return
     *      A new Column with the provided arguments.
     */
    public static Column column(final String id, final String title, final int doublePrecision) {
        return new Column(id, title, 1, doublePrecision);
    }

    /**
     * Creates a new Column with the provided arguments.
     *
     * @param id
     *      The column identifier.
     * @param title
     *      The column title.
     * @param width
     *      The column width.
     *      This information will only be used if the associated
     *      {@link MultiColumnPrinter} is configured to apply formatting.
     *      See {@link Builder#format(boolean)}.
     * @param doublePrecision
     *      The double precision to use to print data for this column.
     * @return
     *      A new Column with the provided arguments.
     */
    public static Column column(final String id, final String title, final int width, final int doublePrecision) {
        return new Column(id, title, Math.max(width, title.length()), doublePrecision);
    }

    /**
     * This class describes a Column of data used in the {@link MultiColumnPrinter}.
     * <p>
     * A column consists in the following fields:
     * <ul>
     *     <li>An identifier for the associated data.
     *     <li>A title which is printed when {@link MultiColumnPrinter#printTitleLine()} is called.
     *     <li>A width which is the max width for this column's data.
     *         This information will only be used if the associated {@link MultiColumnPrinter}
     *         is configure to apply formatting.See {@link Builder#format(boolean)}.
     *     <li>A double precision which is the number of decimal to print for numeric data.
     *         See {@link MultiColumnPrinter#printData(Double)}.
     * </ul>
     */
    public static final class Column {
        private final String id;
        private final String title;
        private final int width;
        private final int doublePrecision;

        private Column(final String id, final String title, final int width, final int doublePrecision) {
            this.id = id;
            this.title = title;
            this.width = Math.max(width, title.length());
            this.doublePrecision = doublePrecision;
        }

        /**
         * Returns this {@link Column} identifier.
         *
         * @return This {@link Column} identifier.
         */
        public String getId() {
            return id;
        }
    }

    /**
     * Creates a new {@link Builder} to build a {@link MultiColumnPrinter}.
     *
     * @param stream
     *      The {@link PrintStream} to use to print data.
     * @param columns
     *      The {@link List} of {@link Column} data to print.
     * @return
     *      A new {@link Builder} to build a {@link MultiColumnPrinter}.
     */
    public static Builder builder(final PrintStream stream, final List<Column> columns) {
        return new Builder(stream, columns);
    }

    /** A fluent API for incrementally constructing {@link MultiColumnPrinter}. */
    public static final class Builder {
        private final PrintStream stream;
        private final List<Column> columns;

        private Alignment titleAlignment = Alignment.RIGHT;
        private String columnSeparator = " ";
        private boolean format;

        private Builder(final PrintStream stream, final List<Column> columns) {
            Reject.ifNull(stream);
            this.stream = stream;
            this.columns = columns;
        }

        /**
         * Sets whether the {@link MultiColumnPrinter} needs to apply formatting.
         * <br>
         * Default value is {@code false}.
         *
         * @param format
         *      {@code true} if the {@link MultiColumnPrinter} needs to apply formatting.
         * @return This builder.
         */
        public Builder format(final boolean format) {
            this.format = format;
            return this;
        }

        /**
         * Sets the alignment for title elements which will be printed by the {@link MultiColumnPrinter}.
         * <p>
         * This is used only if the printer is configured to
         * apply formatting, see {@link Builder#format(boolean)}.
         * <br>
         * Default value is {@link Alignment#RIGHT}.
         *
         * @param titleAlignment
         *      The title alignment.
         * @return This builder.
         */
        public Builder titleAlignment(final Alignment titleAlignment) {
            this.titleAlignment = titleAlignment;
            return this;
        }

        /**
         * Sets the sequence to use to separate column.
         * <p>
         * Default value is {@code " "}.
         *
         * @param separator
         *      The sequence {@link String}.
         * @return This builder.
         */
        public Builder columnSeparator(final String separator) {
            this.columnSeparator = separator;
            return this;
        }

        /**
         * Creates a new {@link MultiColumnPrinter} as configured in this {@link Builder}.
         *
         * @return A new {@link MultiColumnPrinter} as configured in this {@link Builder}.
         */
        public MultiColumnPrinter build() {
            return new MultiColumnPrinter(this);
        }
    }

    private final PrintStream stream;
    private final List<Column> columns;
    private final boolean format;
    private final Alignment titleAlignment;
    private final String columnSeparator;

    private List<Column> printableColumns;
    private final int lineLength;
    private Iterator<Column> columnIterator;
    private Column currentColumn;

    private MultiColumnPrinter(final Builder builder) {
        this.stream = builder.stream;
        this.columns = Collections.unmodifiableList(builder.columns);
        this.format = builder.format;
        this.columnSeparator = builder.columnSeparator;
        this.titleAlignment = builder.titleAlignment;
        this.lineLength = computeLineLength();
        resetIterator();
        computePrintableColumns();
    }

    /** Prints a dashed line. */
    public void printDashedLine() {
        startNewLineIfNeeded();
        for (int i = 0; i < lineLength; i++) {
            stream.print('-');
        }
        stream.println();
    }

    /**
     * Formats and prints the provided text data.
     * Merge the provided text over the provided number of column.
     * <p>
     * Separator columns between merged columns will not be printed.
     *
     * @param data
     *      The section title to print.
     * @param rowSpan
     *      Specifies the number of rows a cell should span.
     */
    public void printTitleSection(final String data, final int rowSpan) {
        consumeSeparatorColumn();
        int lengthToPad = 0;
        int nbColumnMerged = 0;

        while (columnIterator.hasNext() && nbColumnMerged < rowSpan) {
            lengthToPad += currentColumn.width + columnSeparator.length();
            if (!isSeparatorColumn(currentColumn)) {
                nbColumnMerged++;
            }
            currentColumn = columnIterator.next();
        }
        stream.print(align(data, titleAlignment, lengthToPad));
        consumeSeparatorColumn();
        if (!columnIterator.hasNext()) {
            nextLine();
        }
    }

    /** Prints a line with all column title and separator. */
    public void printTitleLine() {
        startNewLineIfNeeded();
        passFirstSeparatorColumn();
        for (final Column column : this.printableColumns) {
            printCell(column.title, Alignment.RIGHT);
        }
    }

    /**
     * Prints the provided {@link Double} value on the current column.
     * <p>
     * If this {@link MultiColumnPrinter} is configured to apply formatting,
     * the provided value will be truncated according to the decimal
     * precision set in the corresponding {@link Column}.
     * <br>
     * See {@link MultiColumnPrinter#column(String, String, int, int)} for more details.
     *
     * @param value
     *      The double value to print.
     */
    public void printData(final Double value) {
        passFirstSeparatorColumn();
        printData(value.isNaN() ? "-"
                                : String.format(Locale.ENGLISH, "%." + currentColumn.doublePrecision + "f", value));
    }

    /**
     * Prints the provided text data on the current column.
     *
     * @param data
     *      The text data to print.
     */
    public void printData(final String data) {
        passFirstSeparatorColumn();
        printCell(data, Alignment.RIGHT);
    }

    /**
     * Returns the data {@link Column} list of this {@link MultiColumnPrinter}.
     * <p>
     * Separator columns are filtered out.
     *
     * @return The {@link Column} list of this {@link MultiColumnPrinter}.
     */
    public List<Column> getColumns() {
        return printableColumns;
    }

    private void printCell(final String data, final Alignment alignment) {
        String toPrint = format ? align(data, alignment, currentColumn.width) : data;
        if (columnIterator.hasNext()) {
            toPrint += columnSeparator;
        }
        stream.print(toPrint);
        nextLineOnEOLOrNextColumn();
    }

    /** Provided the provided string data according to the provided width and the provided alignment. */
    private String align(final String data, final Alignment alignment, final int width) {
        final String rawData = data.trim();
        final int padding = width - rawData.length();

        if (padding <= 0) {
            return rawData;
        }

        switch (alignment) {
        case RIGHT:
            return pad(padding, rawData, 0);
        case LEFT:
            return pad(0, rawData, padding);
        case CENTER:
            final int paddingBefore = padding / 2;
            return pad(paddingBefore, rawData, padding - paddingBefore);
        default:
            return "";
        }
    }

    private String pad(final int leftPad, final String s, final int rightPad) {
        return new StringBuilder().append(repeat(' ', leftPad))
                                  .append(s)
                                  .append(repeat(' ', rightPad))
                                  .toString();
    }

    private void passFirstSeparatorColumn() {
        if (cursorOnLineStart()) {
            consumeSeparatorColumn();
        }
    }

    private void consumeSeparatorColumn() {
        if (isSeparatorColumn(currentColumn)) {
            stream.print('|' + columnSeparator);
            nextLineOnEOLOrNextColumn();
        }
    }

    private void startNewLineIfNeeded() {
        if (!cursorOnLineStart()) {
            nextLine();
        }
    }

    private void nextLineOnEOLOrNextColumn() {
        if (columnIterator.hasNext()) {
            currentColumn = columnIterator.next();
            consumeSeparatorColumn();
        } else {
            nextLine();
        }
    }

    private void nextLine() {
        stream.println();
        resetIterator();
    }

    private void resetIterator() {
        columnIterator = columns.iterator();
        currentColumn = columnIterator.next();
    }

    private boolean cursorOnLineStart() {
        return currentColumn == columns.get(0);
    }

    private boolean isSeparatorColumn(final Column column) {
        return column.id.startsWith(SEPARATOR_ID);
    }

    private void computePrintableColumns() {
        printableColumns = new ArrayList<>(columns);
        final Iterator<Column> it = printableColumns.iterator();

        while (it.hasNext()) {
            if (isSeparatorColumn(it.next())) {
                it.remove();
            }
        }
    }

    private int computeLineLength() {
        int lineLength = 0;
        final int separatorLength = this.columnSeparator.length();
        for (final Column column : this.columns) {
            lineLength += column.width + separatorLength;
        }
        return lineLength - separatorLength;
    }
}
