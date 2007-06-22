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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

/**
 * Provides support for construction and display of tables in text based
 * applications. Applications construct tables using the {@link TableBuilder}
 * class and display them using on of the {@link TablePrinter}
 * implementations. At the moment two types of table output are supported:
 * <ul>
 * <li>{@link CSVTablePrinter} - displays a table in comma-separated
 *     value format
 * <li>{@link TabSeparatedTablePrinter} - displays a table in tab separated
 *     format
 * <li>{@link TextTablePrinter} - displays a table in a human-readable
 *     format. Using this implementation it is possible to configure
 *     constraints on column widths. The implementation will take care of
 *     wrapping table cells where required.
 * </ul>
 * The following code illustrates the construction of a text-based table:
 * <pre>
 * TableBuilder builder = new TableBuilder();
 *
 * builder.appendHeading("Name");
 * builder.appendHeading("Age");
 * builder.addSortKey(0);
 *
 * builder.startRow();
 * builder.appendCell("Bob");
 * builder.appendCell(11);
 *
 * builder.startRow();
 * builder.appendCell("Alice");
 * builder.appendCell(22);
 *
 * builder.startRow();
 * builder.appendCell("Charlie");
 * builder.appendCell(33);
 *
 * TextTablePrinter printer = new TextTablePrinter(System.out);
 * printer.setColumnSeparator(":");
 * builder.print(printer);
 * </pre>
 *
 * Which will display the following table:
 * <pre>
 * Name    : Age
 * --------:----
 * Alice   : 22
 * Bob     : 11
 * Charlie : 33
 * </pre>
 */
package org.opends.server.util.table;



