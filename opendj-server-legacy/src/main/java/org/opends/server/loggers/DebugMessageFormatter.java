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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for formatting messages and replacing
 * format tokens with the text value of message arguments in debug logging
 * records.
 */
class DebugMessageFormatter
{
  /**
   * Format the message format string with the provided arguments.
   *
   * @param msg the message format string to be formatted.
   * @param msgArgs the arguments to use when replacing tokens in the message.
   * @return the formatted message string.
   */
  static String format(String msg, Object[] msgArgs)
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

  private static void concatenateArgs(Object[] args, StringBuilder buffer)
  {
    if (args != null) {
      for (Object arg : args) {
        buffer.append(" ").append(arg);
      }
    }
  }

  private static Object[] decorateMessageArgs(Object[] undecoratedArgs)
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

  private static Object decorateArg(Object arg)
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

  private static String decorateArrayArg(Object[] array)
  {
    return decorateListArg(Arrays.asList(array));
  }

  private static String decorateArrayArg(boolean[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    for (int i= 0; i < array.length; i++) {
      if (i > 0)
      {
        buffer.append(", ");
      }
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private static String decorateArrayArg(byte[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    for (int i= 0; i < array.length; i++) {
      if (i > 0)
      {
        buffer.append(", ");
      }
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private static String decorateArrayArg(char[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    for (int i= 0; i < array.length; i++) {
      if (i > 0)
      {
        buffer.append(", ");
      }
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private static String decorateArrayArg(double[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    for (int i= 0; i < array.length; i++) {
      if (i > 0)
      {
        buffer.append(", ");
      }
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private static String decorateArrayArg(float[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    for (int i= 0; i < array.length; i++) {
      if (i > 0)
      {
        buffer.append(", ");
      }
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private static String decorateArrayArg(int[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    for (int i= 0; i < array.length; i++) {
      if (i > 0)
      {
        buffer.append(", ");
      }
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private static String decorateArrayArg(long[] array)
  {
    StringBuilder buffer= new StringBuilder();
    buffer.append("[ ");
    for (int i= 0; i < array.length; i++) {
      if (i > 0)
      {
        buffer.append(", ");
      }
      buffer.append(array[i]);
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private static String decorateListArg(List list)
  {
    StringBuilder buffer= new StringBuilder();
    Iterator iter= list.iterator();
    buffer.append("[ ");
    boolean firstElement= true;
    while (iter.hasNext()) {
      Object lValue= iter.next();
      if (!firstElement)
      {
        buffer.append(", ");
      }
      buffer.append(decorateArg(lValue));
      firstElement= false;
    }
    buffer.append(" ]");

    return buffer.toString();
  }

  private static String decorateMapArg(Map map)
  {
    StringBuilder buffer= new StringBuilder();
    Iterator iter= map.entrySet().iterator();
    buffer.append("{ ");
    boolean firstEntry= true;
    while (iter.hasNext()) {
      Map.Entry entry= (Map.Entry)iter.next();
      if (!firstEntry)
      {
        buffer.append(", ");
      }
      buffer.append(decorateArg(entry.getKey()));
      buffer.append("=");
      buffer.append(decorateArg(entry.getValue()));
      firstEntry= false;
    }
    buffer.append(" }");

    return buffer.toString();
  }
}
