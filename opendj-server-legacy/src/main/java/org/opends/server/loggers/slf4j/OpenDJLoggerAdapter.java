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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.loggers.slf4j;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedMarker;
import org.opends.messages.Severity;
import org.opends.server.loggers.DebugLogger;
import org.opends.server.loggers.DebugTracer;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.LoggingCategoryNames;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

/**
 * OpenDJ implementation of a SLF4J Logger.
 * <p>
 * Log calls at trace level are redirected to {@code DebugLogger}, while calls
 * at other levels are redirected to {@code ErrorLogger}.
 * <p>
 * Trace-level calls do no expect a Marker argument. The marker argument is ignored if provided.
 * <pre>
 * Example of expected trace-level call:
 *   logger.trace("This is an error message");
 * </pre>
 * <p>
 * Non trace-level calls expect a Marker argument that is an instance of
 * {@code LocalizedMarker}. This is the standard way for OpenDJ code.
 * <pre>
 * Example of expected non trace-level call:
 *   LocalizableMessage message = ...
 *   logger.error(new LocalizedMarker(message), message.toString(locale), t);
 * </pre>
 * <br>
 * However, to support logger calls from external libraries, calls without a Marker are supported,
 * by creating a raw LocalizableMessage on the fly.
 * <p>
 * Note that these methods are never called directly. Instead, OpenDJ code instantiates a LocalizedLogger
 * which then delegates to the underlying SLF4J logger.
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

    @Override
    public String getName() {
        return name;
    }

    /** Format a message containing '{}' as arguments placeholder. */
    private String formatMessage(String message, Object...args)
    {
      return MessageFormatter.arrayFormat(message, args).getMessage();
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
            logError(message, severity, throwable);
        } else {
            throw new IllegalStateException("Expecting the marker to be an instance of LocalizedMarker");
        }
    }

    /**
     * Log a message to {@code ErrorLogger} with the provided message and severity.
     * <p>
     * This should be avoided, but when using an external library there can be calls
     * with a String.
     *
     * @param message
     *            The message as string.
     * @param severity
     *            The severity to use when logging message.
     * @param throwable
     *            Exception to log. May be {@code null}.
     */
    private void logError(String message, Severity severity, Throwable throwable) {
      logError(LocalizableMessage.raw(message), severity, throwable);
    }

    /** Performs the actual logging to {@code ErrorLogger}. */
    private void logError(LocalizableMessage message, Severity severity, Throwable throwable) {
      ErrorLogger.log(name, severity, message, throwable);
    }


    @Override
    public boolean isTraceEnabled() {
        return DebugLogger.debugEnabled() && tracer.enabled();
    }

    @Override
    public void trace(String msg) {
        logTraceMessage(msg);
    }

    @Override
    public void trace(Marker marker, String msg) {
        logTraceMessage(msg);
    }

    @Override
    public void trace(String msg, Throwable t) {
        logTraceException(msg, t);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        logTraceException(msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return ErrorLogger.isEnabledFor(name, Severity.INFORMATION);
    }

    @Override
    public void debug(Marker marker, String msg) {
        logError(marker, Severity.INFORMATION, null);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        logError(marker, Severity.INFORMATION, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return ErrorLogger.isEnabledFor(name, Severity.NOTICE);
    }

    @Override
    public void info(Marker marker, String msg) {
        logError(marker, Severity.NOTICE, null);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        logError(marker, Severity.NOTICE, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return ErrorLogger.isEnabledFor(name, Severity.WARNING);
    }

    @Override
    public void warn(Marker marker, String msg) {
        logError(marker, Severity.WARNING, null);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        logError(marker, Severity.WARNING, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return ErrorLogger.isEnabledFor(name, Severity.ERROR);
    }

    @Override
    public void error(Marker marker, String msg) {
        logError(marker, Severity.ERROR, null);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        logError(marker, Severity.ERROR, t);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return isTraceEnabled();
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return isErrorEnabled();
    }

    @Override
    public void trace(String message, Object arg) {
      logTraceMessage(formatMessage(message, arg));
    }

    @Override
    public void trace(String message, Object arg1, Object arg2) {
      logTraceMessage(formatMessage(message, arg1, arg2));
    }

    @Override
    public void trace(String message, Object... argArray) {
      logTraceMessage(formatMessage(message, argArray));
    }

    @Override
    public void trace(Marker marker, String message, Object arg) {
      logTraceMessage(formatMessage(message, arg));
    }

    @Override
    public void trace(Marker marker, String message, Object arg1, Object arg2) {
      logTraceMessage(formatMessage(message, arg1, arg2));
    }

    @Override
    public void trace(Marker marker, String message, Object... argArray) {
      logTraceMessage(formatMessage(message, argArray));
    }

    @Override
    public void debug(String msg) {
      logError(msg, Severity.INFORMATION, null);
    }

    @Override
    public void debug(String message, Object arg) {
      logError(formatMessage(message, arg), Severity.INFORMATION, null);
    }

    @Override
    public void debug(String message, Object arg1, Object arg2) {
      logError(formatMessage(message, arg1, arg2), Severity.INFORMATION, null);
    }

    @Override
    public void debug(String message, Object... argArray) {
      logError(formatMessage(message, argArray), Severity.INFORMATION, null);
    }

    @Override
    public void debug(String msg, Throwable t) {
      logError(msg, Severity.INFORMATION, t);
    }

    @Override
    public void debug(Marker marker, String message, Object arg) {
      logError(formatMessage(message, arg), Severity.INFORMATION, null);
    }

    @Override
    public void debug(Marker marker, String message, Object arg1, Object arg2) {
      logError(formatMessage(message, arg1, arg2), Severity.INFORMATION, null);
    }

    @Override
    public void debug(Marker marker, String message, Object... arguments) {
      logError(formatMessage(message, arguments), Severity.INFORMATION, null);
    }

    @Override
    public void info(String msg) {
      logError(msg, Severity.NOTICE, null);
    }

    @Override
    public void info(String message, Object arg) {
      logError(formatMessage(message, arg), Severity.NOTICE, null);
    }

    @Override
    public void info(String message, Object arg1, Object arg2) {
      logError(formatMessage(message, arg1, arg2), Severity.NOTICE, null);
    }

    @Override
    public void info(String message, Object... argArray) {
      logError(formatMessage(message, argArray), Severity.NOTICE, null);
    }

    @Override
    public void info(String msg, Throwable t) {
      logError(msg, Severity.NOTICE, t);
    }

    @Override
    public void info(Marker marker, String message, Object arg) {
      logError(formatMessage(message, arg), Severity.NOTICE, null);
    }

    @Override
    public void info(Marker marker, String message, Object arg1, Object arg2) {
      logError(formatMessage(message, arg1, arg2), Severity.NOTICE, null);
    }

    @Override
    public void info(Marker marker, String message, Object... arguments) {
      logError(formatMessage(message, arguments), Severity.NOTICE, null);
    }

    @Override
    public void warn(String msg) {
      logError(msg, Severity.WARNING, null);
    }

    @Override
    public void warn(String message, Object arg) {
      logError(formatMessage(message, arg), Severity.WARNING, null);
    }

    @Override
    public void warn(String message, Object arg1, Object arg2) {
      logError(formatMessage(message, arg1, arg2), Severity.WARNING, null);
    }

    @Override
    public void warn(String message, Object... argArray) {
      logError(formatMessage(message, argArray), Severity.WARNING, null);
    }

    @Override
    public void warn(String msg, Throwable t) {
      logError(msg, Severity.WARNING, t);
    }

    @Override
    public void warn(Marker marker, String message, Object arg) {
      logError(formatMessage(message, arg), Severity.WARNING, null);
    }

    @Override
    public void warn(Marker marker, String message, Object arg1, Object arg2) {
      logError(formatMessage(message, arg1, arg2), Severity.WARNING, null);
    }

    @Override
    public void warn(Marker marker, String message, Object... arguments) {
      logError(formatMessage(message, arguments), Severity.WARNING, null);
    }

    @Override
    public void error(String msg) {
      logError(msg, Severity.ERROR, null);
    }

    @Override
    public void error(String message, Object arg) {
      logError(formatMessage(message, arg), Severity.ERROR, null);
    }

    @Override
    public void error(String message, Object arg1, Object arg2) {
      logError(formatMessage(message, arg1, arg2), Severity.ERROR, null);
    }

    @Override
    public void error(String message, Object... arguments) {
      logError(formatMessage(message, arguments), Severity.ERROR, null);
    }

    @Override
    public void error(String msg, Throwable t) {
      logError(msg, Severity.ERROR, t);
    }

    @Override
    public void error(Marker marker, String message, Object arg) {
      logError(formatMessage(message, arg), Severity.ERROR, null);
    }

    @Override
    public void error(Marker marker, String message, Object arg1, Object arg2) {
      logError(formatMessage(message, arg1, arg2), Severity.ERROR, null);
    }

    @Override
    public void error(Marker marker, String message, Object... arguments) {
      logError(formatMessage(message, arguments), Severity.ERROR, null);
    }
}