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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.plugins.profiler;



import java.io.IOException;

import org.opends.server.protocols.asn1.*;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.DebugLogLevel;


/**
 * This class defines a data structure that may be used to hold information
 * about a thread stack trace.
 */
public class ProfileStack
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  /**
   * The line number that will be used for stack frames in which the line number
   * is unknown but it is not a native method.
   */
  public static final int LINE_NUMBER_UNKNOWN = -1;



  /**
   * The line number that will be used for stack frames in which the line number
   * is unknown because it is a native method.
   */
  public static final int LINE_NUMBER_NATIVE = -2;



  // The number of frames in this stack.
  private int numFrames;

  // The source file line numbers for each of the frames in this stack.
  private int[] lineNumbers;

  // The class names for each of the frames in this stack.
  private String[] classNames;

  // The method names for each of the frames in this stack.
  private String[] methodNames;



  /**
   * Creates a new profile stack with the provided information.
   *
   * @param  stackElements  The stack trace elements to use to create this
   *                        profile stack.
   */
  public ProfileStack(StackTraceElement[] stackElements)
  {
    numFrames   = stackElements.length;
    classNames  = new String[numFrames];
    methodNames = new String[numFrames];
    lineNumbers = new int[numFrames];

    for (int i=0, j=(numFrames-1); i < numFrames; i++,j--)
    {
      classNames[i]  = stackElements[j].getClassName();
      methodNames[i] = stackElements[j].getMethodName();
      lineNumbers[i] = stackElements[j].getLineNumber();

      if (lineNumbers[i] <= 0)
      {
        if (stackElements[j].isNativeMethod())
        {
          lineNumbers[i] = LINE_NUMBER_NATIVE;
        }
        else
        {
          lineNumbers[i] = LINE_NUMBER_UNKNOWN;
        }
      }
    }
  }



  /**
   * Creates a new profile stack with the provided information.
   *
   * @param  classNames   The class names for the frames in this stack.
   * @param  methodNames  The method names for the frames in this stack.
   * @param  lineNumbers  The line numbers for the frames in this stack.
   */
  private ProfileStack(String[] classNames, String[] methodNames,
                       int[] lineNumbers)
  {
    this.numFrames   = classNames.length;
    this.classNames  = classNames;
    this.methodNames = methodNames;
    this.lineNumbers = lineNumbers;
  }



  /**
   * Retrieves the number of frames in this stack.
   *
   * @return  The number of frames in this stack.
   */
  public int getNumFrames()
  {
    return numFrames;
  }



  /**
   * Retrieves the class names in this stack.
   *
   * @return  The class names in this stack.
   */
  public String[] getClassNames()
  {
    return classNames;
  }



  /**
   * Retrieves the class name from the specified frame in the stack.
   *
   * @param  depth  The depth of the frame to retrieve, with the first frame
   *                being frame zero.
   *
   * @return  The class name from the specified frame in the stack.
   */
  public String getClassName(int depth)
  {
    return classNames[depth];
  }



  /**
   * Retrieves the method names in this stack.
   *
   * @return  The method names in this stack.
   */
  public String[] getMethodNames()
  {
    return methodNames;
  }



  /**
   * Retrieves the method name from the specified frame in the stack.
   *
   * @param  depth  The depth of the frame to retrieve, with the first frame
   *                being frame zero.
   *
   * @return  The method name from the specified frame in the stack.
   */
  public String getMethodName(int depth)
  {
    return methodNames[depth];
  }



  /**
   * Retrieves the line numbers in this stack.
   *
   * @return  The line numbers in this stack.
   */
  public int[] getLineNumbers()
  {
    return lineNumbers;
  }



  /**
   * Retrieves the line number from the specified frame in the stack.
   *
   * @param  depth  The depth of the frame for which to retrieve the line
   *                number.
   *
   * @return  The line number from the specified frame in the stack.
   */
  public int getLineNumber(int depth)
  {
    return lineNumbers[depth];
  }



  /**
   * Retrieves the hash code for this profile stack.  It will be the sum of the
   * hash codes for the class and method name and line number for the first
   * frame.
   *
   * @return  The hash code for this profile stack.
   */
  public int hashCode()
  {
    if (numFrames == 0)
    {
      return 0;
    }
    else
    {
      return (classNames[0].hashCode() + methodNames[0].hashCode() +
              lineNumbers[0]);
    }
  }



  /**
   * Indicates whether to the provided object is equal to this profile stack.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is a profile stack object
   *          with the same set of class names, method names, and line numbers
   *          as this profile stack, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    if (o == null)
    {
      return false;
    }
    else if (this == o)
    {
      return true;
    }


    try
    {
      ProfileStack s = (ProfileStack) o;

      if (numFrames != s.numFrames)
      {
        return false;
      }

      for (int i=0; i < numFrames; i++)
      {
        if ((lineNumbers[i] != s.lineNumbers[i]) ||
            (! classNames[i].equals(s.classNames[i])) ||
            (! methodNames[i].equals(s.methodNames[i])))
        {
          return false;
        }
      }

      return true;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return false;
    }
  }



  /**
   * Encodes and writes this profile stack to the capture file.
   *
   * @param  writer The writer to use.
   * @throws IOException if an error occurs while writing.
   */
  public void write(ASN1Writer writer) throws IOException
  {
    writer.writeStartSequence();
    writer.writeInteger(numFrames);
    for (int i=0; i < numFrames; i++)
    {
      writer.writeOctetString(classNames[i]);
      writer.writeOctetString(methodNames[i]);
      writer.writeInteger(lineNumbers[i]);
    }
    writer.writeEndSequence();
  }



  /**
   * Decodes the contents of the provided element as a profile stack.
   *
   * @param  reader  The ASN.1 reader to read the encoded profile stack
   *                 information from.
   *
   * @return  The decoded profile stack.
   * @throws ASN1Exception If the element could not be decoded for some reason.
   *
   */
  public static ProfileStack decode(ASN1Reader reader) throws ASN1Exception
  {
    reader.readStartSequence();

    int      numFrames   = (int)reader.readInteger();
    String[] classNames  = new String[numFrames];
    String[] methodNames = new String[numFrames];
    int[]    lineNumbers = new int[numFrames];

    int i = 0;
    while(reader.hasNextElement())
    {
      classNames[i]  = reader.readOctetStringAsString();
      methodNames[i] = reader.readOctetStringAsString();
      lineNumbers[i] = (int)reader.readInteger();
      i++;
    }

    reader.readEndSequence();

    return new ProfileStack(classNames, methodNames, lineNumbers);
  }
}

