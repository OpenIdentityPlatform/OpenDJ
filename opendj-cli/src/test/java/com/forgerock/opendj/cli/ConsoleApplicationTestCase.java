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
 *      Copyright 2014 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import org.forgerock.i18n.LocalizableMessage;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for the console application class.
 */
@SuppressWarnings("javadoc")
public class ConsoleApplicationTestCase extends CliTestCase {

    final LocalizableMessage msg = LocalizableMessage.raw("Language is the source of misunderstandings.");
    final LocalizableMessage msg2 = LocalizableMessage
            .raw("If somebody wants a sheep, that is a proof that one exists.");

    /**
     * For test purposes only.
     */
    private static class MockConsoleApplication extends ConsoleApplication {
        private static ByteArrayOutputStream out;
        private static ByteArrayOutputStream err;
        private boolean verbose;
        private boolean interactive;
        private boolean quiet;

        private MockConsoleApplication(PrintStream out, PrintStream err) {
            super(out, err);
        }

        static MockConsoleApplication getDefault() {
            out = new ByteArrayOutputStream();
            final PrintStream psOut = new PrintStream(out);
            err = new ByteArrayOutputStream();
            final PrintStream psErr = new PrintStream(err);
            return new MockConsoleApplication(psOut, psErr);
        }

        public String getOut() throws UnsupportedEncodingException {
            return out.toString("UTF-8");
        }

        public String getErr() throws UnsupportedEncodingException {
            return err.toString("UTF-8");
        }

        /** {@inheritDoc} */
        @Override
        public boolean isVerbose() {
            return verbose;
        }

        /** {@inheritDoc} */
        @Override
        public boolean isInteractive() {
            return interactive;
        }

        /** {@inheritDoc} */
        @Override
        public boolean isQuiet() {
            return quiet;
        }

        public void setVerbose(boolean v) {
            verbose = v;
        }

        public void setInteractive(boolean inter) {
            interactive = inter;
        }

        public void setQuiet(boolean q) {
            quiet = q;
        }

        @Override
        public boolean isMenuDrivenMode() {
            return false;
        }
    }

    @Test
    public void testWriteLineInOutputStream() throws UnsupportedEncodingException {
        final MockConsoleApplication ca = MockConsoleApplication.getDefault();
        ca.print(msg);
        assertThat(ca.getOut()).contains(msg.toString());
        assertThat(ca.getErr()).isEmpty();
    }

    @Test
    public void testWriteLineInErrorStream() throws UnsupportedEncodingException {
        final MockConsoleApplication ca = MockConsoleApplication.getDefault();
        ca.errPrintln(msg);
        assertThat(ca.getOut()).isEmpty();
        assertThat(ca.getErr()).contains(msg.toString());
    }

    @Test
    public void testWriteOutputStreamVerbose() throws UnsupportedEncodingException {
        final MockConsoleApplication ca = MockConsoleApplication.getDefault();
        ca.printVerboseMessage(msg);
        assertThat(ca.isVerbose()).isFalse();
        assertThat(ca.getOut()).isEmpty();
        assertThat(ca.getErr()).isEmpty();
        ca.setVerbose(true);
        ca.printVerboseMessage(msg);
        assertThat(ca.isVerbose()).isTrue();
        assertThat(ca.getOut()).contains(msg.toString());
        assertThat(ca.getErr()).isEmpty();
    }

    @Test
    public void testWriteErrorStreamVerbose() throws UnsupportedEncodingException {
        final MockConsoleApplication ca = MockConsoleApplication.getDefault();
        ca.errPrintVerboseMessage(msg);
        assertThat(ca.isVerbose()).isFalse();
        assertThat(ca.getOut()).isEmpty();
        assertThat(ca.getErr()).isEmpty();
        ca.setVerbose(true);
        ca.errPrintVerboseMessage(msg);
        assertThat(ca.isVerbose()).isTrue();
        assertThat(ca.getOut()).isEmpty();
        assertThat(ca.getErr()).contains(msg.toString());
    }

    /**
     * In non interactive applications, standard messages should be displayed in the stdout(info) and errors to the
     * stderr (warnings, errors).
     *
     * @throws UnsupportedEncodingException
     */
    @Test
    public void testNonInteractiveApplicationShouldNotStdoutErrors() throws UnsupportedEncodingException {
        final MockConsoleApplication ca = MockConsoleApplication.getDefault();

        assertFalse(ca.isInteractive());
        ca.errPrintln(msg);
        assertThat(ca.getOut()).isEmpty();
        assertThat(ca.getErr()).contains(msg.toString());
        ca.println(msg2);
        assertThat(ca.getOut()).contains(msg2.toString());
        assertThat(ca.getErr()).doesNotContain(msg2.toString());
    }

    /**
     * If an application is interactive, all messages should be redirect to the stdout. (info, warnings, errors).
     *
     * @throws UnsupportedEncodingException
     */
    @Test
    public void testInteractiveApplicationShouldStdoutErrors() throws UnsupportedEncodingException {
        final MockConsoleApplication ca = MockConsoleApplication.getDefault();

        assertFalse(ca.isInteractive());
        ca.setInteractive(true);
        assertTrue(ca.isInteractive());
        ca.errPrintln(msg);
        assertThat(ca.getOut()).contains(msg.toString());
        assertThat(ca.getErr()).isEmpty();
        ca.println(msg2);
        assertThat(ca.getOut()).contains(msg2.toString());
        assertThat(ca.getErr()).isEmpty();
    }

    /**
     * In quiet mode, only the stderr should contain lines.
     * @throws UnsupportedEncodingException
     */
    @Test
    public void testQuietMode() throws UnsupportedEncodingException {
        final MockConsoleApplication ca = MockConsoleApplication.getDefault();
        ca.setQuiet(true);
        assertTrue(ca.isQuiet());
        ca.println(msg);
        ca.errPrintln(msg2);
        assertThat(ca.getOut()).isEmpty();
        assertThat(ca.getErr()).contains(msg2.toString());
        assertThat(ca.getErr()).doesNotContain(msg.toString());
    }
}
