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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;

import static org.forgerock.util.Utils.*;
import static org.opends.messages.QuickSetupMessages.*;

/** A class used to describe the java arguments for a given command-line. */
public class JavaArguments
{
  private int maxMemory = -1;
  private int initialMemory = -1;
  private String[] additionalArguments = {};

  /**
   * Returns the maximum memory allowed to execute the command-line.
   * @return the maximum memory allowed to execute the command-line.
   */
  public int getMaxMemory()
  {
    return maxMemory;
  }

  /**
   * Sets the maximum memory allowed to execute the command-line.
   * @param maxMemory the maximum memory allowed to execute the command-line.
   */
  public void setMaxMemory(int maxMemory)
  {
    this.maxMemory = maxMemory;
  }

  /**
   * Returns the initial memory allowed to execute the command-line.
   * @return the initial memory allowed to execute the command-line.
   */
  public int getInitialMemory()
  {
    return initialMemory;
  }

  /**
   * Sets the initial memory allowed to execute the command-line.
   * @param initialMemory the initial memory allowed to execute the
   * command-line.
   */
  public void setInitialMemory(int initialMemory)
  {
    this.initialMemory = initialMemory;
  }

  /**
   * Returns the additional arguments to be used when executing the
   * command-line.
   * @return the additional arguments to be used when executing the
   * command-line.
   */
  public String[] getAdditionalArguments()
  {
    return additionalArguments;
  }

  /**
   * Sets the additional arguments to be used when executing the
   * command-line.
   * @param additionalArguments the additional arguments to be used when
   * executing the command-line.  It cannot be null.
   */
  public void setAdditionalArguments(String[] additionalArguments)
  {
    if (additionalArguments == null)
    {
      throw new IllegalArgumentException("additionalArguments cannot be null.");
    }
    this.additionalArguments = additionalArguments;
  }

  @Override
  public boolean equals(Object o)
  {
    if (o == this)
    {
      return true;
    }
    if (o instanceof JavaArguments)
    {
      final JavaArguments that = (JavaArguments) o;
      return initialMemory == that.initialMemory
          && maxMemory == that.maxMemory
          && Arrays.equals(additionalArguments, that.additionalArguments);
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    int hashCode = 44 + initialMemory + maxMemory;
    for (String arg : additionalArguments)
    {
      hashCode += arg.hashCode();
    }
    return hashCode;
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("Initial Memory: ").append(initialMemory)
        .append("  Max Memory: ").append(maxMemory);
    int i=1;
    for (String arg : additionalArguments)
    {
      sb.append(" arg ").append(i).append(": ").append(arg);
      i++;
    }
    return sb.toString();
  }

  /**
   * Returns the message in HTML format to be used in a JLabel representing a
   * java arguments object.
   * @param javaArguments the java arguments to be represented.
   * @param defaultJavaArguments the default values for the java arguments.
   * @param font the font to be used.
   * @return the message representing a java arguments object.
   */
  public static LocalizableMessage getMessageForJLabel(JavaArguments javaArguments,
      JavaArguments defaultJavaArguments, Font font)
  {
    LocalizableMessage msg = getMessage(javaArguments, defaultJavaArguments);
    String s = msg.toString();
    if (s.contains("<br>"))
    {
      msg = LocalizableMessage.raw("<html>"+UIFactory.applyFontToHtml(s, font));
    }
    return msg;
  }

  /**
   * Returns the message in HTML format to be used in a representing a
   * java arguments object.  Note that no formatting of font is done.
   * @param javaArguments the java arguments to be represented.
   * @param defaultJavaArguments the default values for the java arguments.
   * @return the message representing a java arguments object.
   */
  private static LocalizableMessage getMessage(JavaArguments javaArguments,
      JavaArguments defaultJavaArguments)
  {
    LocalizableMessage msg;
    if (javaArguments.equals(defaultJavaArguments))
    {
      msg = INFO_DEFAULT_JAVA_ARGUMENTS.get();
    }
    else
    {
      ArrayList<LocalizableMessage> lines = new ArrayList<>();
      if (javaArguments.getInitialMemory() != -1)
      {
        lines.add(INFO_INITIAL_MEMORY.get(javaArguments.getInitialMemory()));
      }
      if (javaArguments.getMaxMemory() != -1)
      {
        lines.add(INFO_MAXIMUM_MEMORY.get(javaArguments.getMaxMemory()));
      }
      if (javaArguments.getAdditionalArguments().length > 0)
      {
        StringBuilder sb = new StringBuilder();
        for (String arg : javaArguments.getAdditionalArguments())
        {
          if (sb.length() > 0)
          {
            sb.append(" ");
          }
          sb.append(arg);
        }
        lines.add(INFO_ADDITIONAL_ARGUMENTS.get(sb));
      }
      if (lines.isEmpty())
      {
        msg = INFO_USE_JVM_DEFAULT_SETTINGS.get();
      }
      else if (lines.size() == 1)
      {
        msg = lines.get(0);
      }
      else
      {
        msg = LocalizableMessage.raw(joinAsString("<br>", lines));
      }
    }
    return msg;
  }

  /**
   * Returns a String representation of the arguments (the String that must
   * be passed when invoking java).
   * @return a String representation of the arguments (the String that must
   * be passed when invoking java).
   */
  public String getStringArguments()
  {
    ArrayList<String> l = new ArrayList<>();
    if (initialMemory != -1)
    {
      l.add(Utils.escapeCommandLineValue(
          getInitialMemoryArgument(initialMemory)));
    }
    if (maxMemory != -1)
    {
      l.add(Utils.escapeCommandLineValue(getMaxMemoryArgument(maxMemory)));
    }
    for (String arg : additionalArguments)
    {
      l.add(Utils.escapeCommandLineValue(arg));
    }
    return joinAsString(" ", l);
  }

  /**
   * Returns the java argument to specify the initial memory to be used.
   * @param value the value in megabytes to be specified.
   * @return the java argument to specify the initial memory to be used.
   */
  public static String getInitialMemoryArgument(int value)
  {
    return "-Xms"+value+"m";
  }

  /**
   * Returns a generic initial memory argument (to be used in messages).
   * @return a generic initial memory argument (to be used in messages).
   */
  public static String getInitialMemoryGenericArgument()
  {
    return "-Xms<"+INFO_MEMORY_PLACEHOLDER.get()+">";
  }

  /**
   * Returns the java argument to specify the maximum memory that can be used.
   * @param value the value in megabytes to be specified.
   * @return the java argument to specify the maximum memory that can be used.
   */
  public static String getMaxMemoryArgument(int value)
  {
    return "-Xmx"+value+"m";
  }

  /**
   * Returns a generic maximum memory argument (to be used in messages).
   * @return a generic maximum memory argument (to be used in messages).
   */
  public static String getMaxMemoryGenericArgument()
  {
    return "-Xmx<"+INFO_MEMORY_PLACEHOLDER.get()+">";
  }
}
