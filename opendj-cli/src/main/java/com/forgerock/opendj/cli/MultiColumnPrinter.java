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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2012-2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 * Utility class for printing aligned columns of text.
 * <P>
 * This class allows you to specify:
 * <UL>
 * <LI>The number of columns in the output. This will determine the dimension of
 * the string arrays passed to add(String[]) or addTitle(String[]).
 * <LI>spacing/gap between columns
 * <LI>character to use for title border (null means no border)
 * <LI>column alignment. Only LEFT/CENTER is supported for now.
 * </UL>
 * <P>
 * Example usage:
 *
 * <PRE>
 * MyPrinter mp = new MyPrinter(3, 2, &quot;-&quot;);
 * String oneRow[] = new String[3];
 * oneRow[0] = &quot;User Name&quot;;
 * oneRow[1] = &quot;Email Address&quot;;
 * oneRow[2] = &quot;Phone Number&quot;;
 * mp.addTitle(oneRow);
 * oneRow[0] = &quot;Bob&quot;;
 * oneRow[1] = &quot;bob@foo.com&quot;;
 * oneRow[2] = &quot;123-4567&quot;;
 * mp.add(oneRow);
 * oneRow[0] = &quot;John&quot;;
 * oneRow[1] = &quot;john@foo.com&quot;;
 * oneRow[2] = &quot;456-7890&quot;;
 * mp.add(oneRow);
 * mp.print();
 * </PRE>
 * <P>
 * The above would print:
 * <P>
 *
 * <PRE>
 *  --------------------------------------
 *  User Name  Email Address  Phone Number
 *  --------------------------------------
 *  Bob        bob@foo.com    123-4567
 *  John       john@foo.com   456-7890
 * </PRE>
 * <P>
 * This class also supports multi-row titles and having title strings spanning
 * multiple columns. Example usage:
 *
 * <PRE>
 * TestPrinter tp = new TestPrinter(4, 2, &quot;-&quot;);
 * String oneRow[] = new String[4];
 * int[] span = new int[4];
 * span[0] = 2; // spans 2 columns
 * span[1] = 0; // spans 0 columns
 * span[2] = 2; // spans 2 columns
 * span[3] = 0; // spans 0 columns
 * tp.setTitleAlign(CENTER);
 * oneRow[0] = &quot;Name&quot;;
 * oneRow[1] = &quot;&quot;;
 * oneRow[2] = &quot;Contact&quot;;
 * oneRow[3] = &quot;&quot;;
 * tp.addTitle(oneRow, span);
 * oneRow[0] = &quot;First&quot;;
 * oneRow[1] = &quot;Last&quot;;
 * oneRow[2] = &quot;Email&quot;;
 * oneRow[3] = &quot;Phone&quot;;
 * tp.addTitle(oneRow);
 * oneRow[0] = &quot;Bob&quot;;
 * oneRow[1] = &quot;Jones&quot;;
 * oneRow[2] = &quot;bob@foo.com&quot;;
 * oneRow[3] = &quot;123-4567&quot;;
 * tp.add(oneRow);
 * oneRow[0] = &quot;John&quot;;
 * oneRow[1] = &quot;Doe&quot;;
 * oneRow[2] = &quot;john@foo.com&quot;;
 * oneRow[3] = &quot;456-7890&quot;;
 * tp.add(oneRow);
 * tp.println();
 * </PRE>
 * <P>
 * The above would print:
 * <P>
 *
 * <PRE>
 *      ------------------------------------
 *          Name             Contact
 *      First  Last      Email       Phone
 *      ------------------------------------
 *      Bob    Jones  bob@foo.com   123-4567
 *      John   Doe    john@foo.com  456-7890
 * </PRE>
 */
public final class MultiColumnPrinter {

    /** Left ID. */
    public static final int LEFT = 0;
    /** Center ID. */
    public static final int CENTER = 1;
    /** Right ID. */
    public static final int RIGHT = 2;

    private int numCol = 2;
    private int gap = 4;

    private int align = CENTER;
    private int titleAlign = CENTER;

    private String border;
    private final List<String[]> titleTable = new Vector<>();
    private final List<int[]> titleSpanTable = new Vector<>();
    private final int[] curLength;

    private final ConsoleApplication app;

    /**
     * Creates a sorted new MultiColumnPrinter class using LEFT alignment and
     * with no title border.
     *
     * @param numCol
     *            number of columns
     * @param gap
     *            gap between each column
     * @param app
     *            the console application to use for outputting data
     */
    public MultiColumnPrinter(final int numCol, final int gap, final ConsoleApplication app) {
        this(numCol, gap, null, LEFT, app);
    }

    /**
     * Creates a sorted new MultiColumnPrinter class using LEFT alignment.
     *
     * @param numCol
     *            number of columns
     * @param gap
     *            gap between each column
     * @param border
     *            character used to frame the titles
     * @param app
     *            the console application to use for outputting data
     */
    public MultiColumnPrinter(final int numCol, final int gap, final String border,
            final ConsoleApplication app) {
        this(numCol, gap, border, LEFT, app);
    }

    /**
     * Creates a new MultiColumnPrinter class.
     *
     * @param numCol
     *            number of columns
     * @param gap
     *            gap between each column
     * @param border
     *            character used to frame the titles
     * @param align
     *            type of alignment within columns
     * @param app
     *            the console application to use for outputting data
     */
    public MultiColumnPrinter(final int numCol, final int gap, final String border, final int align,
            final ConsoleApplication app) {
        curLength = new int[numCol];

        this.numCol = numCol;
        this.gap = gap;
        this.border = border;
        this.align = align;
        this.titleAlign = LEFT;

        this.app = app;
    }

    /**
     * Adds to the row of strings to be used as the title for the table.
     *
     * @param row
     *            Array of strings to print in one row of title.
     */
    public void addTitle(final String[] row) {
        if (row == null) {
            return;
        }

        final int[] span = new int[row.length];
        for (int i = 0; i < row.length; i++) {
            span[i] = 1;
        }

        addTitle(row, span);
    }

    /**
     * Adds to the row of strings to be used as the title for the table. Also
     * allows for certain title strings to span multiple columns The span
     * parameter is an array of integers which indicate how many columns the
     * corresponding title string will occupy. For a row that is 4 columns
     * wide, it is possible to have some title strings in a row to 'span'
     * multiple columns:
     * <P>
     *
     * <PRE>
     * ------------------------------------
     *     Name             Contact
     * First  Last      Email       Phone
     * ------------------------------------
     * Bob    Jones  bob@foo.com   123-4567
     * John   Doe    john@foo.com  456-7890
     * </PRE>
     *
     * In the example above, the title row has a string 'Name' that spans 2
     * columns. The string 'Contact' also spans 2 columns. The above is done
     * by passing in to addTitle() an array that contains:
     *
     * <PRE>
     * span[0] = 2; // spans 2 columns
     * span[1] = 0; // spans 0 columns, ignore
     * span[2] = 2; // spans 2 columns
     * span[3] = 0; // spans 0 columns, ignore
     * </PRE>
     * <P>
     * A span value of 1 is the default. The method addTitle(String[] row)
     * basically does:
     *
     * <PRE>
     * int[] span = new int[row.length];
     * for (int i = 0; i &lt; row.length; i++) {
     *     span[i] = 1;
     * }
     * addTitle(row, span);
     * </PRE>
     *
     * @param row
     *            Array of strings to print in one row of title.
     * @param span
     *            Array of integers that reflect the number of columns the
     *            corresponding title string will occupy.
     */
    public void addTitle(final String[] row, final int[] span) {
        // Need to create a new instance of it, otherwise the new values
        // will always overwrite the old values.
        titleTable.add(Arrays.copyOf(row, row.length));
        titleSpanTable.add(span);
    }

    /**
     * Clears title strings.
     */
    public void clearTitle() {
        titleTable.clear();
        titleSpanTable.clear();
    }

    /**
     * Adds one row of text to output.
     *
     * @param row
     *            Array of strings to print in one row.
     */
    public void printRow(final String... row) {
        for (int i = 0; i < numCol; i++) {
            if (titleAlign == RIGHT) {
                final int spaceBefore = curLength[i] - row[i].length();
                printSpaces(spaceBefore);
                app.getOutputStream().print(row[i]);
                if (i < numCol - 1) {
                    printSpaces(gap);
                }
            } else if (align == CENTER) {
                int space1, space2;
                space1 = (curLength[i] - row[i].length()) / 2;
                space2 = curLength[i] - row[i].length() - space1;

                printSpaces(space1);
                app.getOutputStream().print(row[i]);
                printSpaces(space2);
                if (i < numCol - 1) {
                    printSpaces(gap);
                }
            } else {
                app.getOutputStream().print(row[i]);
                if (i < numCol - 1) {
                    printSpaces(curLength[i] - row[i].length() + gap);
                }
            }
        }
        app.getOutputStream().println("");
    }

    /**
     * Prints the table title.
     */
    public void printTitle() {
        // Get the longest string for each column and store in curLength[]

        // Scan through title rows
        Iterator<int[]> spanEnum = titleSpanTable.iterator();
        for (String[] row : titleTable) {
            final int[] curSpan = spanEnum.next();

            for (int i = 0; i < numCol; i++) {
                // None of the fields should be null, but if it
                // happens to be so, replace it with "-".
                if (row[i] == null) {
                    row[i] = "-";
                }

                int len = row[i].length();

                /*
                 * If a title string spans multiple columns, then the space it
                 * occupies in each column is at most len/span (since we have
                 * gap to take into account as well).
                 */
                final int span = curSpan[i];
                int rem = 0;
                if (span > 1) {
                    rem = len % span;
                    len = len / span;
                }

                if (curLength[i] < len) {
                    curLength[i] = len;

                    if ((span > 1) && ((i + span) <= numCol)) {
                        for (int j = i + 1; j < (i + span); ++j) {
                            curLength[j] = len;
                        }

                        /*
                         * Add remainder to last column in span to avoid
                         * round-off errors.
                         */
                        curLength[(i + span) - 1] += rem;
                    }
                }
            }
        }

        printBorder();

        spanEnum = titleSpanTable.iterator();
        for (String[] row : titleTable) {
            final int[] curSpan = spanEnum.next();

            for (int i = 0; i < numCol; i++) {
                int availableSpace = 0;
                final int span = curSpan[i];

                if (span == 0) {
                    continue;
                }

                availableSpace = curLength[i];

                if ((span > 1) && ((i + span) <= numCol)) {
                    for (int j = i + 1; j < (i + span); ++j) {
                        availableSpace += gap;
                        availableSpace += curLength[j];
                    }
                }

                if (titleAlign == RIGHT) {
                    final int spaceBefore = availableSpace - row[i].length();
                    printSpaces(spaceBefore);
                    app.getOutputStream().print(row[i]);
                    if (i < numCol - 1) {
                        printSpaces(gap);
                    }
                } else if (titleAlign == CENTER) {
                    int spaceBefore, spaceAfter;
                    spaceBefore = (availableSpace - row[i].length()) / 2;
                    spaceAfter = availableSpace - row[i].length() - spaceBefore;

                    printSpaces(spaceBefore);
                    app.getOutputStream().print(row[i]);
                    printSpaces(spaceAfter);
                    if (i < numCol - 1) {
                        printSpaces(gap);
                    }
                } else {
                    app.getOutputStream().print(row[i]);
                    if (i < numCol - 1) {
                        printSpaces(availableSpace - row[i].length() + gap);
                    }
                }

            }
            app.getOutputStream().println("");
        }
        printBorder();
    }

    /**
     * Set alignment for title strings.
     *
     * @param titleAlign
     *            The alignment which should be one of {@code LEFT},
     *            {@code RIGHT}, or {@code CENTER}.
     */
    public void setTitleAlign(final int titleAlign) {
        this.titleAlign = titleAlign;
    }

    private void printBorder() {
        if (border == null) {
            return;
        }

        // For the value in each column
        for (int i = 0; i < numCol; i++) {
            for (int j = 0; j < curLength[i]; j++) {
                app.getOutputStream().print(border);
            }
        }

        // For the gap between each column
        for (int i = 0; i < numCol - 1; i++) {
            for (int j = 0; j < gap; j++) {
                app.getOutputStream().print(border);
            }
        }
        app.getOutputStream().println("");
    }

    private void printSpaces(final int count) {
        for (int i = 0; i < count; ++i) {
            app.getOutputStream().print(" ");
        }
    }
}
