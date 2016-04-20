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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.opends.server.util.ServerConstants.*;

/**
 * A DebugStackTraceFormatter converts an exception's stack trace into a String
 * appropriate for tracing, optionally performing filtering of stack frames.
 */
class DebugStackTraceFormatter
{
  /** The stack depth value to indicate the entire stack should be printed. */
  public static final int COMPLETE_STACK = Integer.MAX_VALUE;
  /** A nested frame filter that removes debug and trailing no OpenDJ frames. */
  public static final FrameFilter SMART_FRAME_FILTER = new SmartFrameFilter();

  /** A FrameFilter provides stack frame filtering used during formatting. */
  interface FrameFilter
  {
    /**
     * Filters out all undesired stack frames from the given Throwable's stack
     * trace.
     *
     * @param frames
     *          the frames to filter
     * @return an array of StackTraceElements to be used in formatting.
     */
    StackTraceElement[] getFilteredStackTrace(StackTraceElement[] frames);
  }

  /** A basic FrameFilter that filters out frames from the debug logging and non OpenDJ classes. */
  private static class SmartFrameFilter implements FrameFilter
  {
    private boolean isFrameForPackage(StackTraceElement frame, String packageName)
    {
      return frame != null ? startsWith(frame.getClassName(), packageName) : false;
    }

    private boolean startsWith(String className, String packageName)
    {
      return className != null && className.startsWith(packageName);
    }

    /**
     * Return the stack trace of an exception with debug and trailing non OpenDJ
     * frames filtered out.
     *
     * @param frames
     *          the frames to filter
     * @return the filtered stack trace.
     */
    @Override
    public StackTraceElement[] getFilteredStackTrace(StackTraceElement[] frames)
    {
      StackTraceElement[] trimmedStack = null;
      if (frames != null && frames.length > 0)
      {
        int firstFrame = 0;

        // Skip leading frames debug logging classes
        while (firstFrame < frames.length
            && DebugTracer.isLoggingStackTraceElement(frames[firstFrame]))
        {
          firstFrame++;
        }

        // Skip trailing frames not in OpenDJ classes
        int lastFrame = frames.length - 1;
        while (lastFrame > firstFrame
            && !isFrameForPackage(frames[lastFrame], "org.opends"))
        {
          lastFrame--;
        }

        trimmedStack = new StackTraceElement[lastFrame - firstFrame + 1];
        for (int i = firstFrame; i <= lastFrame; i++)
        {
          trimmedStack[i - firstFrame] = frames[i];
        }
      }

      return trimmedStack;
    }
  }

  /**
   * Generate a String representation of the entire stack trace of the given
   * Throwable.
   *
   * @param t
   *          - the Throwable for which to generate the stack trace.
   * @return the stack trace.
   */
  public static String formatStackTrace(Throwable t)
  {
    return formatStackTrace(t, COMPLETE_STACK, true);
  }

  /**
   * Generate a String representation of the possibly filtered stack trace of
   * the given Throwable.
   *
   * @param t
   *          - the Throwable for which to generate the stack trace.
   * @param maxDepth
   *          - the maximum number of stack frames to include in the trace.
   * @param includeCause
   *          - also include the stack trace for the cause Throwable.
   * @return the stack trace.
   */
  static String formatStackTrace(Throwable t, int maxDepth, boolean includeCause)
  {
    StringBuilder buffer = new StringBuilder();

    StackTraceElement[] trace = t.getStackTrace();
    int frameLimit = Math.min(maxDepth, trace.length);
    for (int i = 0; i < frameLimit; i++)
    {
      buffer.append("  at ");
      buffer.append(trace[i]);
      buffer.append(EOL);
    }
    if (frameLimit < trace.length)
    {
      buffer.append("  ... ");
      buffer.append(trace.length - frameLimit);
      buffer.append(" more");
      buffer.append(EOL);
    }

    if (includeCause)
    {
      Throwable ourCause = t.getCause();
      if (ourCause != null)
      {
        formatStackTraceForCause(ourCause, maxDepth, buffer, trace);
      }
    }

    return buffer.toString();
  }

  private static void formatStackTraceForCause(Throwable t, int maxDepth,
      StringBuilder buffer, StackTraceElement[] causedTrace)
  {
    StackTraceElement[] trace = t.getStackTrace();
    int framesToSkip = Math.max(trace.length - maxDepth, 0);

    // Compute number of frames in common between this and caused
    int m = trace.length - 1 - framesToSkip;
    int n = causedTrace.length - 1 - framesToSkip;
    while (m >= 0 && n >= 0 && trace[m].equals(causedTrace[n]))
    {
      m--;
      n--;
    }
    framesToSkip = trace.length - 1 - m;

    buffer.append("Caused by: ");
    buffer.append(t);
    buffer.append(EOL);
    for (int i = 0; i <= m; i++)
    {
      buffer.append("  at ");
      buffer.append(trace[i]);
      buffer.append(EOL);
    }
    if (framesToSkip != 0)
    {
      buffer.append("  ... ");
      buffer.append(framesToSkip);
      buffer.append(" more");
      buffer.append(EOL);
    }

    // Recurse if we have a cause
    Throwable ourCause = t.getCause();
    if (ourCause != null)
    {
      formatStackTraceForCause(ourCause, maxDepth, buffer, trace);
    }
  }

  /**
   * Generate a String representation of the possibly filtered stack trace from
   * the current position in execution.
   *
   * @param stackTrace
   *          - The stack trace elements to format.
   * @param maxDepth
   *          - the maximum number of stack frames to include in the trace.
   * @return the stack trace.
   */
  static String formatStackTrace(StackTraceElement[] stackTrace, int maxDepth)
  {
    StringBuilder buffer = new StringBuilder();

    if (stackTrace != null)
    {
      int frameLimit = Math.min(maxDepth, stackTrace.length);
      if (frameLimit > 0)
      {
        for (int i = 0; i < frameLimit; i++)
        {
          buffer.append("  ");
          buffer.append(stackTrace[i]);
          buffer.append(EOL);
        }

        if (frameLimit < stackTrace.length)
        {
          buffer.append("  ...(");
          buffer.append(stackTrace.length - frameLimit);
          buffer.append(" more)");
          buffer.append(EOL);
        }
      }
    }

    return buffer.toString();
  }
}
