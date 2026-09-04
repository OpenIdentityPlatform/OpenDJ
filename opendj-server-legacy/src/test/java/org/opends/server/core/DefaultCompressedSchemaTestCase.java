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
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.core;

import static org.testng.Assert.*;

import org.testng.annotations.Test;

/**
 * Tests the counter {@link DefaultCompressedSchema} records with the content it saves. The counter
 * is read as "No longer used" by both ends of that file, but a release old enough to seed from it
 * must not be handed a token that is live.
 */
@SuppressWarnings("javadoc")
public class DefaultCompressedSchemaTestCase extends CoreTestCase
{
  /** A content with no gaps records what counting the records recorded: ids 0..N-1 give N+1. */
  @Test
  public void theCounterOfADenseContentIsTheTokenAfterTheLastOne()
  {
    assertEquals(counterAfterWriting(), 1, "an empty content is left at the counter it starts at");
    assertEquals(counterAfterWriting(1), 2);
    assertEquals(counterAfterWriting(1, 2, 3), 4);
  }

  /**
   * A gap inside the content emits fewer records than the ids it spans, so a counter taken from
   * the number of records would name a token that is live.
   */
  @Test
  public void theCounterOfAContentWithAGapIsTheTokenAfterTheHighestOne()
  {
    // The definition of the id 1 was lost: two records, spanning the tokens 1 and 3.
    assertEquals(counterAfterWriting(1, 3), 4, "the counter names a token that is live");
    // The same across a token of more than one byte.
    assertEquals(counterAfterWriting(1, 2, 300), 301);
  }

  /** The counter never walks back, whatever order the content is written in. */
  @Test
  public void theCounterIsNotLoweredByALaterToken()
  {
    assertEquals(counterAfterWriting(3, 1), 4);
  }

  /**
   * The gap at the end is the one this cannot cover, and it is the one the withdrawal of an
   * unstored registration exists to prevent: the id whose definition was lost is not in the
   * content at all, so nothing names it and the counter stops at the token before it.
   */
  @Test
  public void aGapAtTheEndOfTheContentIsNotCoveredByTheCounter()
  {
    // The ids 0 and 1 survived and the definition of the id 2 was lost: the content spans the
    // tokens 1 and 2, and the counter reads as though the id 2 were free.
    assertEquals(counterAfterWriting(1, 2), 3, "the residual a file written by an older server can carry");
  }

  /** The counter left by writing the provided tokens, in the order they are given. */
  private static int counterAfterWriting(final int... tokens)
  {
    int counter = 1;
    for (final int token : tokens)
    {
      counter = DefaultCompressedSchema.counterAfter(counter, encodedToken(token));
    }
    return counter;
  }

  /** Encodes a token the way a compressed schema writes it: as many bytes as it needs. */
  private static byte[] encodedToken(final int token)
  {
    if (token <= 0xFF)
    {
      return new byte[] { (byte) token };
    }
    if (token <= 0xFFFF)
    {
      return new byte[] { (byte) (token >> 8), (byte) token };
    }
    return new byte[] { (byte) (token >> 16), (byte) (token >> 8), (byte) token };
  }
}
