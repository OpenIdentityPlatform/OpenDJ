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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.plugins.profiler;



import java.util.Arrays;
import java.util.HashMap;

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;


/**
 * This class defines a data structure for holding information about a stack
 * frame captured by the Directory Server profiler.  It will contain the class
 * and method name for this frame, the set of line numbers within that method
 * that were captured along with the number of times they were seen, as well as
 * references to subordinate frames that were encountered.
 */
public class ProfileStackFrame
       implements Comparable
{



  // The mapping between the line numbers for this stack frame and the
  // number of times that they were encountered.
  private HashMap<Integer,Long> lineNumbers;

  // The mapping for subordinate frames.  It is mapped to itself because we
  // use a fuzzy equality comparison and sets do not have a get method that
  // can be used to retrieve a specified object.
  private HashMap<ProfileStackFrame,ProfileStackFrame> subordinateFrames;

  // The class name for this stack frame.
  private String className;

  // The method name for this stack frame.
  private String methodName;



  /**
   * Creates a new profile stack frame with the provided information.
   *
   * @param  className   The class name for use in this stack frame.
   * @param  methodName  The method name for use in this stack frame.
   */
  public ProfileStackFrame(String className, String methodName)
  {

    this.className  = className;
    this.methodName = methodName;

    lineNumbers       = new HashMap<Integer,Long>();
    subordinateFrames = new HashMap<ProfileStackFrame,ProfileStackFrame>();
  }



  /**
   * Retrieves the class name for this stack frame.
   *
   * @return  The class name for this stack frame.
   */
  public String getClassName()
  {

    return className;
  }



  /**
   * Retrieves the method name for this stack frame.
   *
   * @return  The method name for this stack frame.
   */
  public String getMethodName()
  {

    return methodName;
  }



  /**
   * Retrieves the method name for this stack frame in a manner that will be
   * safe for use in an HTML context.  Currently, this simply replaces angle
   * brackets with the appropriate HTML equivalent.
   *
   * @return  The generated safe name.
   */
  public String getHTMLSafeMethodName()
  {

    int length = methodName.length();
    StringBuilder buffer = new StringBuilder(length + 6);

    for (int i=0; i < length; i++)
    {
      char c = methodName.charAt(i);
      if (c == '<')
      {
        buffer.append("&lt;");
      }
      else if (c == '>')
      {
        buffer.append("&gt;");
      }
      else
      {
        buffer.append(c);
      }
    }

    return buffer.toString();
  }



  /**
   * Retrieves the mapping between the line numbers associated with this method
   * and the number of occurrences for each of those line numbers.
   *
   * @return  The mapping between the line numbers associated with this method
   *          and the number of occurrences for each of those line numbers.
   */
  public HashMap<Integer,Long> getLineNumbers()
  {

    return lineNumbers;
  }



  /**
   * Updates the count for the number of occurrences of a given stack frame
   * for the specified line number.
   *
   * @param  lineNumber      The line number for which to update the count.
   * @param  numOccurrences  The number of times the specified line was
   *                         encountered for this stack frame.
   */
  public void updateLineNumberCount(int lineNumber, long numOccurrences)
  {

    Long existingCount = lineNumbers.get(lineNumber);
    if (existingCount == null)
    {
      lineNumbers.put(lineNumber, numOccurrences);
    }
    else
    {
      lineNumbers.put(lineNumber, existingCount+numOccurrences);
    }
  }



  /**
   * Retrieves the total number of times that a frame with this class and
   * method name was seen by the profiler thread.
   *
   * @return  The total number of times that a frame with this class and method
   *          name was seen by the profiler thread.
   */
  public long getTotalCount()
  {

    long totalCount = 0;

    for (Long l : lineNumbers.values())
    {
      totalCount += l;
    }

    return totalCount;
  }



  /**
   * Retrieves an array containing the subordinate frames that were seen below
   * this frame in stack traces.  The elements of the array will be sorted in
   * descending order of the number of occurrences.
   *
   * @return  An array containing the subordinate frames that were seen below
   *          this frame in stack traces.
   */
  public ProfileStackFrame[] getSubordinateFrames()
  {

    ProfileStackFrame[] subFrames = new ProfileStackFrame[0];
    subFrames = subordinateFrames.values().toArray(subFrames);

    Arrays.sort(subFrames);

    return subFrames;
  }



  /**
   * Indicates whether this stack frame has one or more subordinate frames.
   *
   * @return  <CODE>true</CODE> if this stack frame has one or more subordinate
   *          frames, or <CODE>false</CODE> if not.
   */
  public boolean hasSubFrames()
  {

    return (! subordinateFrames.isEmpty());
  }



  /**
   * Recursively processes the frames of the provided stack, adding them as
   * nested subordinate frames of this stack frame.
   *
   * @param  stack           The stack trace to use to obtain the frames.
   * @param  depth           The slot of the next frame to process in the
   *                         provided array.
   * @param  count           The number of occurrences for the provided stack.
   * @param  stacksByMethod  The set of stack traces mapped from method name to
   *                         their corresponding stack traces.
   */
  public void recurseSubFrames(ProfileStack stack, int depth, long count,
                   HashMap<String,HashMap<ProfileStack,Long>> stacksByMethod)
  {

    if (depth < 0)
    {
      return;
    }

    String cName = stack.getClassName(depth);
    String mName = stack.getMethodName(depth);
    ProfileStackFrame f = new ProfileStackFrame(cName, mName);

    int lineNumber = stack.getLineNumber(depth);

    ProfileStackFrame subFrame = subordinateFrames.get(f);
    if (subFrame == null)
    {
      subFrame = f;
      subordinateFrames.put(subFrame, subFrame);
    }

    subFrame.updateLineNumberCount(lineNumber, count);


    String classAndMethod = cName + "." + mName;
    HashMap<ProfileStack,Long> stackMap = stacksByMethod.get(classAndMethod);
    if (stackMap == null)
    {
      stackMap = new HashMap<ProfileStack,Long>();
      stacksByMethod.put(classAndMethod, stackMap);
    }
    stackMap.put(stack, count);

    subFrame.recurseSubFrames(stack, (depth-1), count, stacksByMethod);
  }



  /**
   * Retrieves the hash code for this stack frame.  It will be the sum of the
   * hash codes for the class and method name.
   *
   * @return  The hash code for this stack frame.
   */
  public int hashCode()
  {

    return (className.hashCode() + methodName.hashCode());
  }



  /**
   * Indicates whether the provided object is equal to this stack frame.  It
   * will be considered equal if it is a profile stack frame with the same class
   * and method name.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object may be considered equal
   *          to this stack frame, or <CODE>false</CODE> if not.
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
      ProfileStackFrame f = (ProfileStackFrame) o;
      return (className.equals(f.className) && methodName.equals(f.methodName));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      return false;
    }
  }



  /**
   * Indicates the order of this profile stack frame relative to the provided
   * object in a sorted list.  The order will be primarily based on number of
   * occurrences, with an equivalent number of occurrences falling back on
   * alphabetical by class and method names.
   *
   * @param  o  The objectfor which to make the comparison.
   *
   * @return  A negative integer if this stack frame should come before the
   *          provided object in a sorted list, a positive integer if it should
   *          come after the provided object, or zero if they should have
   *          equivalent order.
   *
   * @throws  ClassCastException  If the provided object is not a profile stack
   *                              frame.
   */
  public int compareTo(Object o)
         throws ClassCastException
  {

    ProfileStackFrame f;
    try
    {
      f = (ProfileStackFrame) o;
    }
    catch (ClassCastException cce)
    {
      throw cce;
    }

    long thisCount = getTotalCount();
    long thatCount = f.getTotalCount();
    if (thisCount > thatCount)
    {
      return -1;
    }
    else if (thisCount < thatCount)
    {
      return 1;
    }

    int value = className.compareTo(f.className);
    if (value == 0)
    {
      value = methodName.compareTo(f.methodName);
    }

    return value;
  }



  /**
   * Retrieves a string representation of this stack frame.  It will contain the
   * total number of matching frames, the class name, and the method name.
   *
   * @return  A string representation of this stack frame.
   */
  public String toString()
  {

    StringBuilder buffer = new StringBuilder();
    buffer.append(getTotalCount());
    buffer.append("    ");
    buffer.append(className);
    buffer.append('.');
    buffer.append(methodName);

    return buffer.toString();
  }
}

