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
package org.forgerock.opendj.io;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.SdkTestCase;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Regression test for GHSA-rv4q-c6mr-wxp7 (OPENDJ-001), SDK decoder twin.
 *
 * <p>{@link LDAP#readFilter(ASN1Reader)} (AND/OR/NOT) recursed once per nesting
 * level with no depth bound, so a maliciously over-nested search filter could
 * overflow the JVM stack with a {@link StackOverflowError} during decode. The
 * fix threads a depth counter and rejects over-nested filters with a
 * {@link DecodeException} before the stack is exhausted.
 *
 * @see org.opends.server.types.RawFilterDecodeStackOverflowTestCase the matching
 *      test for the legacy server decoder ({@code RawFilter.decode}).
 */
@SuppressWarnings("javadoc")
public class LDAPFilterDecodeNestingTest extends SdkTestCase
{
  private static final int OVERFLOW_DEPTH = 100_000;
  private static final long DECODE_STACK_BYTES = 256L * 1024L;

  @Test
  public void shallowNestedFilterDecodes() throws Exception
  {
    Throwable result = decodeOnBoundedStack(buildNestedAndFilter(50));
    assertNull(result, "A 50-level filter must decode cleanly, but threw: " + result);
  }

  @Test
  public void deeplyNestedFilterIsRejectedWithoutStackOverflow() throws Exception
  {
    Throwable result = decodeOnBoundedStack(buildNestedAndFilter(OVERFLOW_DEPTH));

    assertNotNull(result,
        "Decoding a " + OVERFLOW_DEPTH + "-level filter must be rejected, but it succeeded");
    assertFalse(result instanceof StackOverflowError,
        "VULNERABLE: LDAP.readFilter overflowed the stack instead of rejecting: " + result);
    assertTrue(result instanceof DecodeException,
        "Expected a controlled DecodeException, but got: " + result);
  }

  private Throwable decodeOnBoundedStack(final byte[] payload) throws InterruptedException
  {
    final Throwable[] escaped = new Throwable[1];
    Runnable decode = new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          LDAP.readFilter(ASN1.getReader(payload));
        }
        catch (Throwable t)
        {
          escaped[0] = t;
        }
      }
    };
    Thread worker = new Thread(null, decode, "ghsa-rv4q-sdk-decode", DECODE_STACK_BYTES);
    worker.start();
    worker.join();
    return escaped[0];
  }

  /** Builds the definite-length BER encoding of an AND filter nested {@code depth} deep. */
  private static byte[] buildNestedAndFilter(final int depth) throws Exception
  {
    ByteStringBuilder leafBuilder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(leafBuilder);
    LDAP.writeFilter(writer, Filter.present("objectClass"));
    writer.flush();
    byte[] leaf = leafBuilder.toByteArray();

    List<byte[]> prefixes = new ArrayList<>(depth);
    int contentLen = leaf.length;
    for (int i = 0; i < depth; i++)
    {
      byte[] lengthBytes = berLength(contentLen);
      byte[] prefix = new byte[1 + lengthBytes.length];
      prefix[0] = LDAP.TYPE_FILTER_AND; // 0xA0
      System.arraycopy(lengthBytes, 0, prefix, 1, lengthBytes.length);
      prefixes.add(prefix);
      contentLen += prefix.length;
    }

    byte[] out = new byte[contentLen];
    int pos = 0;
    for (int i = prefixes.size() - 1; i >= 0; i--)
    {
      byte[] prefix = prefixes.get(i);
      System.arraycopy(prefix, 0, out, pos, prefix.length);
      pos += prefix.length;
    }
    System.arraycopy(leaf, 0, out, pos, leaf.length);
    return out;
  }

  private static byte[] berLength(final int len)
  {
    if (len < 0x80)
    {
      return new byte[] { (byte) len };
    }
    int numBytes = len <= 0xFF ? 1 : len <= 0xFFFF ? 2 : len <= 0xFFFFFF ? 3 : 4;
    byte[] out = new byte[1 + numBytes];
    out[0] = (byte) (0x80 | numBytes);
    for (int i = 0; i < numBytes; i++)
    {
      out[numBytes - i] = (byte) (len >>> (8 * i));
    }
    return out;
  }
}
