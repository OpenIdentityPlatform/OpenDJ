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
package org.opends.server.util;



import java.lang.reflect.Method;
import java.util.Arrays;

import org.opends.server.api.DirectoryThread;



/**
 * This class provides a means of interactively reading a password from the
 * command-line without echoing it to the console.  If it is running on a Java 6
 * or higher VM, then it will use the System.console() method.  If it is running
 * on Java 5, then it will use an ugly hack in which one thread will be used to
 * repeatedly send backspace characters to the console while another reads the
 * password.  Reflection is used to determine whether the Java 6 method is
 * available and to invoke it if it is so that the code will still compile
 * cleanly on Java 5 systems.
 */
public class PasswordReader
       extends DirectoryThread
{
  // Indicates whether the backspace thread should keep looping, sending
  // backspace characters to the console.
  private volatile boolean keepLooping;



  /**
   * Creates a new instance of this password reader.  A new instance should only
   * be created from within this class.
   */
  private PasswordReader()
  {
    super("Password Reader Thread");

    // No implementation is required.  However, this constructor is private to
    // help prevent it being used for external purposes.
  }



  /**
   * Operates in a loop, sending backspace characters to the console to attempt
   * to prevent exposing what the user entered.  It sets the priority to the
   * maximum allowed value to reduce the chance of one or more characters being
   * displayed temporarily before they can be erased.
   */
  public void run()
  {
    Thread currentThread   = Thread.currentThread();
    int    initialPriority = currentThread.getPriority();

    try
    {
      try
      {
        currentThread.setPriority(Thread.MAX_PRIORITY);
      } catch (Exception e) {}

      keepLooping = true;
      while (keepLooping)
      {
        System.out.print("\u0008 ");

        try
        {
          currentThread.sleep(1);
        }
        catch (InterruptedException ie)
        {
          currentThread.interrupt();
          return;
        }
      }
    }
    finally
    {
      try
      {
        currentThread.setPriority(initialPriority);
      } catch (Exception e) {}
    }
  }



  /**
   * Indicates that the backspace thread should stop looping as the complete
   * password has been entered.
   */
  private void stopLooping()
  {
    keepLooping = false;
  }



  /**
   * Reads a password from the console without echoing it to the client.
   *
   * @return  The password as an array of characters.
   */
  public static char[] readPassword()
  {
    // First, use reflection to determine whether the System.console() method
    // is available.
    try
    {
      Method consoleMethod = System.class.getDeclaredMethod("console",
                                                            new Class[0]);
      if (consoleMethod != null)
      {
        char[] password = readPasswordUsingConsole(consoleMethod);
        if (password != null)
        {
          return password;
        }
      }
    }
    catch (Exception e)
    {
      // This must mean that we're running on a JVM that doesn't have the
      // System.console() method, or that the call to Console.readPassword()
      // isn't working.  Fall back to using backspaces.
      return readPasswordUsingBackspaces();
    }


    // If we've gotten here, then the System.console() method must not exist.
    // Fall back on using backspaces.
    return readPasswordUsingBackspaces();
  }



  /**
   * Uses reflection to invoke the <CODE>java.io.Console.readPassword()</CODE>
   * method in order to retrieve the password from the user.
   *
   * @param  consoleMethod  The <CODE>Method</CODE> object that may be used to
   *                        obtain a <CODE>Console</CODE> instance.
   *
   * @return  The password as an array of characters.
   *
   * @throws  Exception  If any problem occurs while attempting to read the
   *                     password.
   */
  private static char[] readPasswordUsingConsole(Method consoleMethod)
          throws Exception
  {
    Object consoleObject  = consoleMethod.invoke(null);
    Method passwordMethod =
         consoleObject.getClass().getDeclaredMethod("readPassword",
                                                    new Class[0]);
    return (char[]) passwordMethod.invoke(consoleObject);
  }



  /**
   * Attempts to read a password from the console by repeatedly sending
   * backspace characters to mask whatever the user may have entered.  This will
   * be used if the <CODE>java.io.Console</CODE> class is not available.
   *
   * @return  The password read from the console.
   */
  private static char[] readPasswordUsingBackspaces()
  {
    char[] pwChars;
    char[] pwBuffer = new char[100];
    int    pos      = 0;

    PasswordReader backspaceThread = new PasswordReader();
    backspaceThread.start();

    try
    {
      while (true)
      {
        int charRead = System.in.read();
        if ((charRead == -1) || (charRead == '\n'))
        {
          // This is the end of the value.
          if (pos == 0)
          {
            return null;
          }
          else
          {
            pwChars = new char[pos];
            System.arraycopy(pwBuffer, 0, pwChars, 0, pos);
            Arrays.fill(pwBuffer, '\u0000');
            return pwChars;
          }
        }
        else if (charRead == '\r')
        {
          int char2 = System.in.read();
          if (char2 == '\n')
          {
            // This is the end of the value.
            if (pos == 0)
            {
              return null;
            }
            else
            {
              pwChars = new char[pos];
              System.arraycopy(pwBuffer, 0, pwChars, 0, pos);
              Arrays.fill(pwBuffer, '\u0000');
              return pwChars;
            }
          }
          else
          {
            // Append the characters to the buffer and continue.
            pwBuffer[pos++] = (char) charRead;
            if (pos >= pwBuffer.length)
            {
              char[] newBuffer = new char[pwBuffer.length+100];
              System.arraycopy(pwBuffer, 0, newBuffer, 0, pwBuffer.length);
              Arrays.fill(pwBuffer, '\u0000');
              pwBuffer = newBuffer;
            }

            pwBuffer[pos++] = (char) char2;
            if (pos >= pwBuffer.length)
            {
              char[] newBuffer = new char[pwBuffer.length+100];
              System.arraycopy(pwBuffer, 0, newBuffer, 0, pwBuffer.length);
              Arrays.fill(pwBuffer, '\u0000');
              pwBuffer = newBuffer;
            }
          }
        }
        else
        {
          // Append the value to the buffer and continue.
          pwBuffer[pos++] = (char) charRead;

          if (pos >= pwBuffer.length)
          {
            char[] newBuffer = new char[pwBuffer.length+100];
            System.arraycopy(pwBuffer, 0, newBuffer, 0, pwBuffer.length);
            Arrays.fill(pwBuffer, '\u0000');
            pwBuffer = newBuffer;
          }
        }
      }
    }
    catch (Exception e)
    {
      // We must have encountered an error while attempting to read.  The only
      // thing we can do is to dump a stack trace and return null.
      e.printStackTrace();
      return null;
    }
    finally
    {
      backspaceThread.stopLooping();
    }
  }
}

