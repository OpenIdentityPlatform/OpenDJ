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

import java.sql.Connection;
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

		/**
		 * What the scan of what a clear left standing answered, handed in rather than looked up: the
		 * lists of {@code reportClearOutcome()} are the tables of a schema and the stamps they carry,
		 * and a stamp is a comment somebody else may write. Asked of a database they would be this
		 * backend's own tables and nothing else - which is the one input the lines are not written for.
		 */
		private ClearLeftovers leftovers = new ClearLeftovers();

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

		@Override
		ClearLeftovers leftoverTables(Connection con, TableScope scope) {
			return leftovers;
		}

		/** The outcome of a clear which dropped nothing, so that the lists below are what it has to say. */
		void reportOutcome() {
			reportClearOutcome(null, TableScope.of(this, null, false), 0, 0, 0, new SkippedRows());
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
		// every character of its own: a value of 5000 identical ones has every substring of it equal to
		// every other, so an escape keeping the last 200 - or every twenty-fifth - renders the same
		// string and passes. What an operator recognises a value by is its head, and that is the thing
		// asserted below
		final StringBuilder huge = new StringBuilder();
		for (int i = 0; i < 5000; i++) {
			huge.append((char) ('a' + i % 26));
		}
		final String escaped = JDBCStorage.forLog(huge.toString());
		assertTrue(escaped.length() < JDBCStorage.MAX_LOGGED_VALUE_LENGTH + 64,
			"the whole of a 5000 character value reached the line: " + escaped.length() + " characters");
		assertTrue(escaped.startsWith(huge.substring(0, JDBCStorage.MAX_LOGGED_VALUE_LENGTH)),
			"the line does not show the head of the value it carries: " + escaped);
		assertTrue(escaped.contains("(+" + (5000 - JDBCStorage.MAX_LOGGED_VALUE_LENGTH) + " more characters)"),
			"the line does not say how much of the value it is not showing: " + escaped);
	}

	/**
	 * And cuts between characters and not inside one. The cap counts code units, so a key decoded from
	 * a four-byte sequence - a surrogate pair - can straddle it, and a cut between the two leaves a
	 * high surrogate standing on its own: no control character, no separator, and written out as
	 * U+FFFD or "?" by whatever encoder the log has.
	 */
	@Test
	public void testForLogDoesNotCutASupplementaryCharacterInHalf() {
		final StringBuilder value = new StringBuilder();
		for (int i = 0; i < JDBCStorage.MAX_LOGGED_VALUE_LENGTH - 1; i++) {
			value.append('x');
		}
		value.appendCodePoint(0x1F600); // two units: the second of them is on the far side of the cap
		value.append("tail");

		final String escaped = JDBCStorage.forLog(value.toString());
		for (int i = 0; i < escaped.length(); i++) {
			final char c = escaped.charAt(i);
			if (Character.isHighSurrogate(c)) {
				assertTrue(i + 1 < escaped.length() && Character.isLowSurrogate(escaped.charAt(i + 1)),
					"a surrogate pair was cut in half at " + i + ": " + escaped);
			}
			assertFalse(Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(escaped.charAt(i - 1))),
				"a low surrogate reached the line on its own at " + i + ": " + escaped);
		}
		// the unit given back is counted by the tail like any other: 199 kept of 205
		assertTrue(escaped.contains("(+" + (value.length() - (JDBCStorage.MAX_LOGGED_VALUE_LENGTH - 1))
				+ " more characters)"),
			"the line does not count the unit the cut gave back: " + escaped);
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

	/**
	 * The other three lists a clear renders, and the road every one of them reaches a line by. They
	 * are the {@code opendj} tables of a schema and the stamps they carry: a table name is whatever
	 * the database was told to call it, a stamp is a comment somebody else may write, and neither is
	 * bounded by anything this backend does - a clear of a database several backends share can meet
	 * any number of them.
	 */
	@Test
	public void testTheListsOfWhatAClearLeftStandingAreBoundedAndCarryNoControlCharacter() {
		final ReportedLines storage = new ReportedLines();
		for (int i = 0; i < 100; i++) {
			storage.leftovers.ours.add("opendj_o" + i + " (/dc=x/" + SPLICED + ")");
			storage.leftovers.unattributed.add("opendj_u" + i + SPLICED);
			storage.leftovers.unreadable.add("opendj_r" + i + SPLICED);
		}
		storage.reportOutcome();

		for (final String line : storage.lines) {
			assertFalse(line.indexOf('\n') >= 0, "a line of the report can be split in two by a value it carries: " + line);
			assertFalse(line.indexOf('\r') >= 0, "a line of the report can be split in two by a value it carries: " + line);
		}
		// one line per list: the count is the whole of it, the naming is the first of it, and it says
		// how much of itself it is not showing
		assertList(storage, "hold trees of this backend that its catalog does not name", "opendj_o0", "opendj_o99");
		assertList(storage, "are named by no catalog of this backend and carry no tree stamp", "opendj_u0", "opendj_u99");
		assertList(storage, "could not be read, so this clear says nothing about whose they are", "opendj_r0", "opendj_r99");
	}

	private static void assertList(ReportedLines storage, String marker, String named, String unnamed) {
		final String line = lineHolding(storage, marker);
		assertTrue(line.contains("100 "), "the list did not count the whole of itself: " + line);
		assertTrue(line.contains(named), "the list does not name its first value: " + line);
		assertFalse(line.contains(unnamed), "the list names more values than the cap allows: " + line);
		assertTrue(line.contains("(and " + (100 - JDBCStorage.MAX_REPORTED_VALUES) + " more, not named here)"),
			"the list does not say how many of its values it is not naming: " + line);
	}

	private static String lineHolding(ReportedLines storage, String marker) {
		for (final String line : storage.lines) {
			if (line.contains(marker)) {
				return line;
			}
		}
		throw new AssertionError("no line of the report says \"" + marker + "\": " + storage.lines);
	}

	/** And says nothing at all where there is nothing to say, which is every clear of a catalog this backend wrote. */
	@Test
	public void testNoLineWhereNoRowWasPassedOver() {
		final ReportedLines storage = new ReportedLines();
		storage.reportSkippedRows(new JDBCStorage.SkippedRows());
		assertTrue(storage.lines.isEmpty(), "a clear that passed over no row reported one: " + storage.lines);
	}
}
