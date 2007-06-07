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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import java.util.Arrays;



/**
 * This class provides a data structure that holds a byte array but
 * also includes the necessary {@code equals} and {@code hashCode}
 * methods to make it suitable for use in maps.
 */
public class ByteArray
{
  // The array that will be wrapped by this object.
  private final byte[] array;



  /**
   * Creates a new {@code ByteArray} object that wraps the provided
   * array.
   *
   * @param  array  The array to be wrapped with this
   *                {@code ByteArray}.
   */
  public ByteArray(byte[] array)
  {
    this.array = array;
  }



  /**
   * Retrieves the array wrapped by this {@code ByteArray} object.
   *
   * @return  The array wrapped by this {@code ByteArray} object.
   */
  public byte[] array()
  {
    return array;
  }



  /**
   * Retrieves a hash code for this {@code ByteArray}.  It will be the
   * sum of all of the bytes contained in the wrapped array.
   *
   * @return  A hash code for this {@code ByteArray}.
   */
  public int hashCode()
  {
    int hashCode = 0;
    for (int i=0; i < array.length; i++)
    {
      hashCode += array[i];
    }

    return hashCode;
  }



  /**
   * Indicates whether the provided object is equal to this
   * {@code ByteArray}.  In order for it to be considered equal, the
   * provided object must be a non-null {@code ByteArray} object with
   * a wrapped array containing the same bytes in the same order.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  {@code true} if the provided object is a
   *          {@code ByteArray} whose content is equal to that of this
   *          {@code ByteArray}, or {@code false} if not.
   */
  public boolean equals(Object o)
  {
    if (o == this)
    {
      return true;
    }

    if (o == null)
    {
      return false;
    }

    if (o instanceof ByteArray)
    {
      ByteArray ba = (ByteArray) o;
      return Arrays.equals(array, ba.array);
    }
    else
    {
      return false;
    }
  }
}

