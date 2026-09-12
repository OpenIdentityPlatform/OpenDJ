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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends.jdbc;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.config.server.JDBCBackendCfg;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.forgerock.opendj.config.ConfigurationMock.mockCfg;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * What the account a clear gives of itself may carry (#931): every value in those lines was read
 * out of the database, and the lines carrying them exist for a database written into by something
 * other than this backend - so a value is bounded before it reaches one, and nothing in it may end
 * a log record.
 * <p>
 * None of it needs a database. The escape and the caps are pure functions, and the line itself is
 * built by {@code reportSkippedRows()} out of an accumulator a case can fill by hand - which is why
 * these are kept out of the container suites: those skip themselves whole where no docker is
 * reachable, and a bound nothing exercises is a bound that can be deleted without a single test
 * going red.
 */
@SuppressWarnings("javadoc")
public class ClearReportTestCase extends DirectoryServerTestCase {

	/** A storage with no database behind it, collecting the lines of a clear's report. */
	private static final class ReportedLines extends JDBCStorage {
		private final List<String> lines = new ArrayList<>();

		ReportedLines() {
			super(backendCfg(), null);
		}

		private static JDBCBackendCfg backendCfg() {
			final JDBCBackendCfg cfg = mockCfg(JDBCBackendCfg.class);
			when(cfg.getBackendId()).thenReturn("clearReport");
			return cfg;
		}

		@Override
		void reportClearLine(LocalizableMessage line) {
			lines.add(line.toString());
		}
	}

	/**
	 * The characters a value must not reach a log record as: the ones that end one, and the two
	 * unicode separators a reader may fold the same way. Built rather than written out - a source
	 * file holding them as they stand is one nobody can review.
	 */
	private static final String SPLICED = "a_table\nSEVERE: a record of somebody else's\r\ttail"
		+ (char) 0x00 + (char) 0x85 + (char) 0x2028 + (char) 0x2029;

	/** The one thing the escape exists for: a value that would end the record it is written into. */
	@Test
	public void testForLogEscapesWhatWouldEndALogRecord() {
		final String escaped = JDBCStorage.forLog(SPLICED);
		for (int i = 0; i < escaped.length(); i++) {
			assertFalse(Character.isISOControl(escaped.charAt(i)),
				"a control character reached the line as it stood, at " + i + ": " + escaped);
		}
		assertFalse(escaped.indexOf(0x2028) >= 0, "a line separator reached the line as it stood: " + escaped);
		assertFalse(escaped.indexOf(0x2029) >= 0, "a paragraph separator reached the line as it stood: " + escaped);
		assertTrue(escaped.contains("\\n"), "the newline is not rendered at all: " + escaped);
		assertTrue(escaped.contains("\\r"), "the carriage return is not rendered at all: " + escaped);
		assertTrue(escaped.contains("\\t"), "the tab is not rendered at all: " + escaped);
		assertTrue(escaped.contains("\\u0000"), "the nul is not rendered at all: " + escaped);
		assertTrue(escaped.contains("\\u0085"), "the next-line control is not rendered at all: " + escaped);
		assertTrue(escaped.contains("\\u2028"), "the line separator is not rendered at all: " + escaped);
		assertTrue(escaped.contains("\\u2029"), "the paragraph separator is not rendered at all: " + escaped);
		// escaped and not dropped: what such a row holds is the whole of what the line has to say about
		// it, and an operator who cannot read it is being told a row was passed over and nothing else
		assertTrue(escaped.contains("SEVERE: a record of somebody else's"),
			"the text of the value was dropped rather than escaped: " + escaped);
	}

	/**
	 * A value is bounded as well as escaped: the key of a catalog row is {@code bytea} on postgresql
	 * and {@code varbinary(max)} on sql server, so one row is enough for the multi-megabyte record
	 * that the cap on the number of rows does not catch.
	 */
	@Test
	public void testForLogBoundsHowMuchOfAValueALineCarries() {
		final StringBuilder huge = new StringBuilder();
		for (int i = 0; i < 5000; i++) {
			huge.append('x');
		}
		final String escaped = JDBCStorage.forLog(huge.toString());
		assertTrue(escaped.length() < JDBCStorage.MAX_LOGGED_VALUE_LENGTH + 64,
			"the whole of a 5000 character value reached the line: " + escaped.length() + " characters");
		assertTrue(escaped.contains("(+" + (5000 - JDBCStorage.MAX_LOGGED_VALUE_LENGTH) + " more characters)"),
			"the line does not say how much of the value it is not showing: " + escaped);
	}

	/**
	 * And leaves alone what an operator has to read. A backslash is not escaped: it ends no record,
	 * and escaping it would spell every escaped comma of a normalized DN twice over.
	 */
	@Test
	public void testForLogLeavesAnOrdinaryNameAsItIs() {
		final String treeName = "/dc\\=example\\,inc,dc\\=com/id2entry";
		assertEquals(JDBCStorage.forLog(treeName), treeName, "an ordinary tree name was rewritten");
	}

	/** A list is bounded in its turn, and says how many of its values the line is not naming. */
	@Test
	public void testForLogNamesOnlyTheFirstOfALongList() {
		final List<String> tables = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			tables.add(String.format("opendj_t%02d", i));
		}
		final String rendered = JDBCStorage.forLog(tables);
		assertTrue(rendered.contains("opendj_t00"), "the first value of the list is not named: " + rendered);
		assertTrue(rendered.contains(String.format("opendj_t%02d", JDBCStorage.MAX_REPORTED_VALUES - 1)),
			"the list names fewer values than the cap allows: " + rendered);
		assertFalse(rendered.contains(String.format("opendj_t%02d", JDBCStorage.MAX_REPORTED_VALUES)),
			"the list names more values than the cap allows: " + rendered);
		assertTrue(rendered.contains("(and " + (100 - JDBCStorage.MAX_REPORTED_VALUES) + " more, not named here)"),
			"the list does not say how many values it is not naming: " + rendered);
	}

	/**
	 * The accumulator counts every row and describes the first of them, and says which is which: what
	 * {@code add()} answers is whether the row it took is one of the described, which is what bounds
	 * the per-row warns of {@code readCatalogRows()} as well.
	 */
	@Test
	public void testSkippedRowsCountsEveryRowAndDescribesTheFirst() {
		final JDBCStorage.SkippedRows skipped = new JDBCStorage.SkippedRows();
		assertTrue(skipped.isEmpty(), "a fresh accumulator has rows in it");
		for (int i = 0; i < 100; i++) {
			assertEquals(skipped.add("row " + i), i < JDBCStorage.MAX_REPORTED_VALUES,
				"row " + i + " was described on the wrong side of the cap");
		}
		assertEquals(skipped.size(), 100, "the accumulator counted fewer rows than it was given");
		assertEquals(skipped.descriptions().size(), JDBCStorage.MAX_REPORTED_VALUES,
			"the accumulator kept more descriptions than the cap allows: " + skipped.descriptions());
		assertTrue(skipped.reported().contains("(and " + (100 - JDBCStorage.MAX_REPORTED_VALUES)
				+ " more, not described here)"),
			"the rendering does not say how many rows it is not describing: " + skipped.reported());
	}

	/**
	 * The line itself, which is where all of it lands: the count is every row passed over, the naming
	 * is bounded, and nothing in it ends the record. The values arrive escaped, {@code
	 * readCatalogRows()} having put every one of them through {@code forLog()} where it built the
	 * description - which this case does for itself, exactly as that read does.
	 */
	@Test
	public void testTheLineOfPassedOverRowsIsBoundedAndCarriesNoControlCharacter() {
		final ReportedLines storage = new ReportedLines();
		final JDBCStorage.SkippedRows skipped = new JDBCStorage.SkippedRows();
		for (int i = 0; i < 100; i++) {
			skipped.add(JDBCStorage.forLog("/dc=x/tree" + i + " at \"" + SPLICED + "\""));
		}
		storage.reportSkippedRows(skipped);

		assertEquals(storage.lines.size(), 1, "the rows passed over were reported in " + storage.lines.size() + " lines");
		final String line = storage.lines.get(0);
		assertTrue(line.contains("100 row(s)"), "the line does not count every row passed over: " + line);
		assertFalse(line.indexOf('\n') >= 0, "the line can be split in two by a value it carries: " + line);
		assertFalse(line.indexOf('\r') >= 0, "the line can be split in two by a value it carries: " + line);
		assertTrue(line.contains("(and " + (100 - JDBCStorage.MAX_REPORTED_VALUES) + " more, not described here)"),
			"the line describes every row it counted, or says nothing about the ones it left out: " + line);
	}

	/** And says nothing at all where there is nothing to say, which is every clear of a catalog this backend wrote. */
	@Test
	public void testNoLineWhereNoRowWasPassedOver() {
		final ReportedLines storage = new ReportedLines();
		storage.reportSkippedRows(new JDBCStorage.SkippedRows());
		assertTrue(storage.lines.isEmpty(), "a clear that passed over no row reported one: " + storage.lines);
	}
}
