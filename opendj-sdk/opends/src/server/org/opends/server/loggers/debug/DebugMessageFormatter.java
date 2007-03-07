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
package org.opends.server.loggers.debug;

import java.util.*;

/**
 * This class is responsible for formatting messages and replacing
 * format tokens with the text value of message arguments in debug logging
 * records.
 */
public class DebugMessageFormatter
{
  /**
   * Construct a new debug message formatter.
   */
  public DebugMessageFormatter() {
    //no implementation needed.
  }

  /**
   * Format the message format string with the provided arguments.
   *
   * @param msg the message format string to be formatted.
   * @param msgArgs the arguments to use when replacing tokens in the message.
   * @return the formatted message string.
   */
  public String format(String msg, Object[] msgArgs)
  {
    StringBuilder buffer= new StringBuilder();
    Object[] decoratedArgs = decorateMessageArgs(msgArgs);

    if (msg == null)
    {
      concatenateArgs(decoratedArgs, buffer);
      return buffer.toString();
    }

    try
    {
      return String.format(msg, decoratedArgs);
    }
    catch (IllegalFormatException e)
    {
      // Make a more useful message than a stack trace.
      buffer.append(msg);
      concatenateArgs(decoratedArgs, buffer);

      return buffer.toString();
    }
  }

  private void concatenateArgs(Object[] args, StringBuilder buffer)
  {
    for (int i = 0; (args != null) && (i < args.length); i++) {
      buffer.append(" ").append(args[i]);
    }
  }

  private Object[] decorateMessageArgs(Object[] undecoratedArgs)
  {
    Object[] args= null;
    if (undecoratedArgs != null) {
      args= new Object[undecoratedArgs.length];
      for (int i= 0; i < args.length; i++) {
        args[i]= decorateArg(undecoratedArgs[i]);
      }
    }

    return args;
  }

  private Object decorateArg(Object arg)
  {
    Object decoratedArg= arg;

    if (arg instanceof Map) {
      decoratedArg= decorateMapArg((Map)arg);
    }
    else if (arg instanceof List) {
      decoratedArg= decorateListArg((List)arg);
    }
    else if (arg instanceof Object[]) {
      decoratedArg= decorateArrayArg((Object[])arg);
    }
    else if (arg instanceof boolean[]) {
      decoratedArg = decorateArrayArg((boolean[])arg);
    }
    else if (arg instanceof byte[]) {
      decoratedArg = decorateArrayArg((byte[])arg);
    }
    else if (arg instanceof char[]) {
      decoratedArg = decorateArrayArg((char[])arg);
    }
    else if (arg instanceof double[]) {
      decoratedArg = decorateArrayArg((double[])arg);
    }
    else if (arg instanceof float[]) {
      decoratedArg = decorateArrayArg((float[])arg);
    }
    else if (arg instanceof int[]) {
      decoratedArg = decorateArrayArg((int[])arg);
    }
    else if (arg instanceof long[]) {
      decoratedArg = decorateArrayArg((long[])arg);
    }

    return decoratedArg;
  }

  private String decorateArrayArg(Object[] array)
  {
    return decorateListArg(Arrays.asList(array));
  }

  private String decorateArrayArg(boolean[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    boolean firstElement= true;
    for (int i= 0; i < array.length; i++) {
      if (i > 0) buffer.append(", ");
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private String decorateArrayArg(byte[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    boolean firstElement= true;
    for (int i= 0; i < array.length; i++) {
      if (i > 0) buffer.append(", ");
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private String decorateArrayArg(char[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    boolean firstElement= true;
    for (int i= 0; i < array.length; i++) {
      if (i > 0) buffer.append(", ");
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private String decorateArrayArg(double[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    boolean firstElement= true;
    for (int i= 0; i < array.length; i++) {
      if (i > 0) buffer.append(", ");
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private String decorateArrayArg(float[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    boolean firstElement= true;
    for (int i= 0; i < array.length; i++) {
      if (i > 0) buffer.append(", ");
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private String decorateArrayArg(int[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    boolean firstElement= true;
    for (int i= 0; i < array.length; i++) {
      if (i > 0) buffer.append(", ");
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private String decorateArrayArg(long[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    boolean firstElement= true;
    for (int i= 0; i < array.length; i++) {
      if (i > 0) buffer.append(", ");
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private String decorateListArg(List list)
  {
    StringBuilder buffer= new StringBuilder();
    Iterator iter= list.iterator();
    buffer.append("[ ");
    boolean firstElement= true;
    while (iter.hasNext()) {
      Object lValue= iter.next();
      if (!firstElement) buffer.append(", ");
      buffer.append(decorateArg(lValue));
      firstElement= false;
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private String decorateMapArg(Map map)
  {
    StringBuilder buffer= new StringBuilder();
    Iterator iter= map.entrySet().iterator();
    buffer.append("{ ");
    boolean firstEntry= true;
    while (iter.hasNext()) {
      Map.Entry entry= (Map.Entry)iter.next();
      if (!firstEntry) buffer.append(", ");
      buffer.append(decorateArg(entry.getKey()));
      buffer.append("=");
      buffer.append(decorateArg(entry.getValue()));
      firstEntry= false;
    }
    buffer.append(" }");

    return buffer.toString();
  }
}
