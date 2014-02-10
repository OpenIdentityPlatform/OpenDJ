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
package org.opends.server.loggers;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedMarker;
import org.opends.messages.Severity;
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * OpenDJ implementation of a SLF4J Logger.
 * <p>
 * Log calls at trace level are redirected to {@code DebugLogger}, while calls
 * at other levels are redirected to {@code ErrorLogger}.
 * <p>
 * Trace level calls are accepted with no Marker argument, while calls at other
 * level must be done with a Marker expected to be an instance of
 * {@code LocalizedMarker}.
 */
final class OpenDJLoggerAdapter implements Logger {
    /** Name of logger, used as the category. */
    private final String name;

    /** The tracer associated to this logger. */
    private final DebugTracer tracer;

    /**
     * Creates a new logger with the provided name.
     *
     * @param name
     *            The name of logger.
     */
    public OpenDJLoggerAdapter(final String name) {
        // Tracer always use the provided name
        // which should be a classname
        this.tracer = DebugLogger.getTracer(name);
        // Name is simplified if possible
        this.name = LoggingCategoryNames.getCategoryName(name);
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /** Trace with message only. */
    private void logTraceMessage(String msg) {
        tracer.trace(msg);
    }

    /** Trace with message and exception. */
    private void logTraceException(String message, Throwable t) {
        tracer.traceException(message, t);
    }

    /**
     * Log a message to {@code ErrorLogger} with the provided severity,
     * extracting {@code LocalizableMessage} from the provided
     * {@code Marker marker} argument.
     *
     * @param marker
     *            The marker, expected to be an instance of
     *            {@code LocalizedMarker} class, from which message to log is
     *            extracted.
     * @param severity
     *            The severity to use when logging message.
     * @param throwable
     *            Exception to log. May be {@code null}.
     */
    private void logError(Marker marker, Severity severity, Throwable throwable) {
        if (marker instanceof LocalizedMarker) {
            LocalizableMessage message = ((LocalizedMarker) marker).getMessage();
            ErrorLogger.log(name, severity, message, throwable);
        } else {
            throw new IllegalStateException("Expecting the marker to be an instance of LocalizedMarker");
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTraceEnabled() {
        return DebugLogger.debugEnabled() && tracer.enabled();
    }

    /** {@inheritDoc} */
    @Override
    public void trace(String msg) {
        logTraceMessage(msg);
    }

    /** {@inheritDoc} */
    @Override
    public void trace(Marker marker, String msg) {
        logTraceMessage(msg);
    }

    /** {@inheritDoc} */
    @Override
    public void trace(String msg, Throwable t) {
        logTraceException(msg, t);
    }

    /** {@inheritDoc} */
    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        logTraceException(msg, t);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDebugEnabled() {
        return ErrorLogger.isEnabledFor(name, Severity.INFORMATION);
    }

    /** {@inheritDoc} */
    @Override
    public void debug(Marker marker, String msg) {
        logError(marker, Severity.INFORMATION, null);
    }

    /** {@inheritDoc} */
    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        logError(marker, Severity.INFORMATION, t);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isInfoEnabled() {
        return ErrorLogger.isEnabledFor(name, Severity.NOTICE);
    }

    /** {@inheritDoc} */
    @Override
    public void info(Marker marker, String msg) {
        logError(marker, Severity.NOTICE, null);
    }

    /** {@inheritDoc} */
    @Override
    public void info(Marker marker, String msg, Throwable t) {
        logError(marker, Severity.NOTICE, t);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isWarnEnabled() {
        return ErrorLogger.isEnabledFor(name, Severity.WARNING);
    }

    /** {@inheritDoc} */
    @Override
    public void warn(Marker marker, String msg) {
        logError(marker, Severity.WARNING, null);
    }

    /** {@inheritDoc} */
    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        logError(marker, Severity.WARNING, t);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isErrorEnabled() {
        return ErrorLogger.isEnabledFor(name, Severity.ERROR);
    }

    /** {@inheritDoc} */
    @Override
    public void error(Marker marker, String msg) {
        logError(marker, Severity.ERROR, null);
    }

    /** {@inheritDoc} */
    @Override
    public void error(Marker marker, String msg, Throwable t) {
        logError(marker, Severity.ERROR, t);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTraceEnabled(Marker marker) {
        return isTraceEnabled();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isDebugEnabled(Marker marker) {
        return isDebugEnabled();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isInfoEnabled(Marker marker) {
        return isInfoEnabled();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isWarnEnabled(Marker marker) {
        return isWarnEnabled();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isErrorEnabled(Marker marker) {
        return isErrorEnabled();
    }

    /** {@inheritDoc} */
    @Override
    public void trace(String format, Object arg) {
        throw new UnsupportedOperationException("Use #trace(String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void trace(String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException("Use #trace(String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void trace(String format, Object... argArray) {
        throw new UnsupportedOperationException("Use #trace(String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void trace(Marker marker, String format, Object arg) {
        throw new UnsupportedOperationException("Use #trace(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException("Use #trace(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void trace(Marker marker, String format, Object... argArray) {
        throw new UnsupportedOperationException("Use #trace(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void debug(String msg) {
        throw new UnsupportedOperationException("Use #debug(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void debug(String format, Object arg) {
        throw new UnsupportedOperationException("Use #debug(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void debug(String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException("Use #debug(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void debug(String format, Object... argArray) {
        throw new UnsupportedOperationException("Use #debug(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void debug(String msg, Throwable t) {
        throw new UnsupportedOperationException("Use #debug(Marker, String, Throwable) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void debug(Marker marker, String format, Object arg) {
        throw new UnsupportedOperationException("Use #debug(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException("Use #debug(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        throw new UnsupportedOperationException("Use #debug(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void info(String msg) {
        throw new UnsupportedOperationException("Use #info(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void info(String format, Object arg) {
        throw new UnsupportedOperationException("Use #info(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void info(String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException("Use #info(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void info(String format, Object... argArray) {
        throw new UnsupportedOperationException("Use #info(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void info(String msg, Throwable t) {
        throw new UnsupportedOperationException("Use #info(Marker, String, Throwable) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void info(Marker marker, String format, Object arg) {
        throw new UnsupportedOperationException("Use #info(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException("Use #info(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void info(Marker marker, String format, Object... arguments) {
        throw new UnsupportedOperationException("Use #info(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void warn(String msg) {
        throw new UnsupportedOperationException("Use #warn(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void warn(String format, Object arg) {
        throw new UnsupportedOperationException("Use #warn(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void warn(String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException("Use #warn(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void warn(String format, Object... argArray) {
        throw new UnsupportedOperationException("Use #warn(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void warn(String msg, Throwable t) {
        throw new UnsupportedOperationException("Use #warn(Marker, String, Throwable) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void warn(Marker marker, String format, Object arg) {
        throw new UnsupportedOperationException("Use #warn(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException("Use #warn(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        throw new UnsupportedOperationException("Use #warn(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void error(String msg) {
        throw new UnsupportedOperationException("Use #error(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void error(String format, Object arg) {
        throw new UnsupportedOperationException("Use #error(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void error(String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException("Use #error(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void error(String format, Object... arguments) {
        throw new UnsupportedOperationException("Use #error(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void error(String msg, Throwable t) {
        throw new UnsupportedOperationException("Use #error(Marker, String, Throwable) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void error(Marker marker, String format, Object arg) {
        throw new UnsupportedOperationException("Use #error(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        throw new UnsupportedOperationException("Use #error(Marker, String) instead.");
    }

    /** {@inheritDoc} */
    @Override
    public void error(Marker marker, String format, Object... arguments) {
        throw new UnsupportedOperationException("Use #error(Marker, String) instead.");
    }
}
