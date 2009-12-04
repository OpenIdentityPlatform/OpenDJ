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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package com.sun.opends.sdk.util;



/**
 * Functions transform input values of type {@code M} to output values
 * of type {@code N}.
 *
 * @param <M>
 *          The type of input values transformed by this function.
 * @param <N>
 *          The type of output values return by this function.
 * @param <P>
 *          The type of the additional parameter to this function's
 *          {@code apply} method. Use {@link java.lang.Void} for
 *          functions that do not need an additional parameter.
 */
public interface Function<M, N, P>
{
  /**
   * Applies this function to the provided input value of type {@code M}
   * , returning an output value of type {@code N}.
   *
   * @param value
   *          The value to be transformed.
   * @param p
   *          A function specified parameter.
   * @return The result of the transformation.
   */
  N apply(M value, P p);
}
