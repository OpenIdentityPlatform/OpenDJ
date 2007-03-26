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
package org.opends.server.util;



import static org.opends.server.util.Validator.*;



/**
 * This class provides an implementation of the Levenshtein distance algorithm,
 * which may be used to determine the minimum number of changes required to
 * transform one string into another.  For the purpose of this algorithm, a
 * change is defined as replacing one character with another, inserting a new
 * character, or removing an existing character.
 * <BR><BR>
 * The basic algorithm works as follows for a source string S of length X and
 * a target string T of length Y:
 * <OL>
 *   <LI>Create a matrix M with dimensions of X+1, Y+1.</LI>
 *   <LI>Fill the first row with sequentially-increasing values 0 through
 *       X.</LI>
 *   <LI>Fill the first column with sequentially-increasing values 0 through
 *       Y.</LI>
 *   <LI>Create a nested loop iterating over the characters in the strings.  In
 *       the outer loop, iterate through characters in S from 0 to X-1 using an
 *       iteration counter I.  In the inner loop, iterate through the characters
 *       in T from 0 to Y-1 using an iterator counter J.  Calculate the
 *       following three things and place the smallest value in the matrix in
 *       row I+1 column J+1:
 *     <UL>
 *       <LI>One more than the value in the matrix position immediately to the
 *           left (i.e., 1 + M[I][J+1]).</LI>
 *       <LI>One more than the value in the matrix position immediately above
 *           (i.e., 1 + M[I+1][J]).</LI>
 *       <LI>Define a value V to be zero if S[I] is the same as T[J], or one if
 *           they are different.  Add that value to the value in the matrix
 *           position immediately above and to the left (i.e.,
 *           V + M[I][J]).</LI>
 *     </UL>
 *   </LI>
 *   <LI>The Levenshtein distance value, which is the least number of changes
 *       needed to transform the source string into the target string, will be
 *       the value in the bottom right corner of the matrix (i.e.,
 *       M[X][Y]).</LI>
 * </OL>
 * <BR><BR>
 * Note that this is a completely "clean room" implementation, developed from a
 * description of the algorithm, rather than copying an existing implementation.
 * Doing it in this way eliminates copyright and licensing concerns associated
 * with using an existing implementation.
 */
public final class LevenshteinDistance
{
  /**
   * Calculates the Levenshtein distance between the provided string values.
   *
   * @param  source  The source string to compare.  It must not be {@code null}.
   * @param  target  The target string to compare.  It must not be {@code null}.
   *
   * @return  The minimum number of changes required to turn the source string
   *          into the target string.
   */
  public static int calculate(String source, String target)
  {
    ensureNotNull(source, target);

    // sl == source length; tl == target length
    int sl = source.length();
    int tl = target.length();


    // If either of the lengths is zero, then the distance is the length of the
    // other string.
    if (sl == 0)
    {
      return tl;
    }
    else if (tl == 0)
    {
      return sl;
    }


    // w == matrix width; h == matrix height
    int w = sl+1;
    int h = tl+1;


    // m == matrix array
    // Create the array and fill it with values 0..sl in the first dimension and
    // 0..tl in the second dimension.
    int[][] m = new int[w][h];
    for (int i=0; i < w; i++)
    {
      m[i][0] = i;
    }

    for (int i=1; i < h; i++)
    {
      m[0][i] = i;
    }

    for (int i=0,x=1; i < sl; i++,x++)
    {
      char s = source.charAt(i);

      for (int j=0,y=1; j < tl; j++,y++)
      {
        char t = target.charAt(j);


        // Figure out what to put in the appropriate matrix slot.  It should be
        // the lowest of:
        // - One more than the value to the left
        // - One more than the value to the top
        // - If the characters are equal, the value to the upper left, otherwise
        //   one more than the value to the upper left.
        m[x][y] = Math.min(Math.min((m[i][y] + 1), (m[x][j] + 1)),
                           (m[i][j] + ((s == t) ? 0 : 1)));
      }
    }

    // The Levenshtein distance should now be the value in the lower right
    // corner of the matrix.
    return m[sl][tl];
  }
}

