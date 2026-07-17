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
package org.opends.server.types;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.testng.annotations.Test;

import static org.opends.server.protocols.ldap.LDAPConstants.TYPE_FILTER_AND;
import static org.opends.server.protocols.ldap.LDAPResultCode.PROTOCOL_ERROR;
import static org.opends.server.util.ServerConstants.MAX_NESTED_FILTER_DEPTH;
import static org.testng.Assert.*;

/**
 * Regression test for GHSA-rv4q-c6mr-wxp7 (OPENDJ-001):
 * <em>Unauthenticated LDAP search-filter decode stack exhaustion</em>.
 *
 * <p>Before the fix, the BER decoder {@link RawFilter#decode(ASN1Reader)} —&gt;
 * {@code decodeCompoundFilter} (AND/OR) / {@code decodeNotFilter} (NOT) recursed
 * once per nesting level with <strong>no depth bound</strong>. The
 * {@code MAX_NESTED_FILTER_DEPTH} guard only protected the filter
 * <em>evaluation</em> path, which runs strictly <em>after</em> decode. An
 * anonymous client could therefore send a single small {@code SearchRequest}
 * whose filter was nested tens of thousands of levels deep; decoding it
 * overflowed the JVM stack with a {@link StackOverflowError} <strong>before</strong>
 * the evaluation guard was ever reached. Because {@code StackOverflowError} is a
 * {@link java.lang.Error} (not an {@link Exception}), it escaped the
 * {@code catch (Exception)} blocks and killed the shared request-handler thread
 * &rarr; listener-wide DoS.
 *
 * <p>After the fix, {@link RawFilter#decode(ASN1Reader)} threads a depth counter
 * and rejects over-nested filters with a {@link LDAPException}
 * ({@code PROTOCOL_ERROR}) instead of recursing into a stack overflow.
 *
 * <p>This test builds the exact malicious wire encoding (a definite-length BER
 * tree of nested {@code 0xA0} AND TLVs wrapping an {@code (objectClass=*)}
 * present leaf) and decodes it on a thread with a small stack, so that — were the
 * bound ever removed — the overflow would still be deterministic and fast on any
 * platform.
 */
@SuppressWarnings("javadoc")
public class RawFilterDecodeStackOverflowTestCase extends DirectoryServerTestCase
{
  /** Nesting depth that would overflow even an oversized default stack. */
  private static final int OVERFLOW_DEPTH = 100_000;

  /** A small, deterministic stack so any unbounded recursion overflows quickly. */
  private static final long DECODE_STACK_BYTES = 256L * 1024L;

  /**
   * A filter nested well within {@link #MAX_NESTED_FILTER_DEPTH} decodes
   * normally. This is the control that proves the harness and the wire encoding
   * are correct, so that the rejection in
   * {@link #deeplyNestedFilterIsRejectedWithoutStackOverflow()} is attributable
   * to the depth guard and not to a malformed payload.
   */
  @Test
  public void shallowNestedFilterDecodesWithoutError() throws Exception
  {
    byte[] payload = buildNestedAndFilter(MAX_NESTED_FILTER_DEPTH / 2);
    Throwable result = decodeOnBoundedStack(payload);
    assertNull(result, "A filter nested " + (MAX_NESTED_FILTER_DEPTH / 2)
        + " levels deep must decode cleanly, but threw: " + result);
  }

  /**
   * Reproduction-turned-regression check: a deeply nested AND filter must be
   * rejected with a controlled {@link LDAPException} ({@code PROTOCOL_ERROR})
   * rather than overflowing the stack with a {@link StackOverflowError}.
   *
   * <p>On the vulnerable (pre-fix) code this fails because decode recurses until
   * the stack overflows and the resulting {@code java.lang.Error} escapes. With
   * the depth guard in place it passes.
   */
  @Test
  public void deeplyNestedFilterIsRejectedWithoutStackOverflow() throws Exception
  {
    byte[] payload = buildNestedAndFilter(OVERFLOW_DEPTH);

    // Well under the ~5 MB ds-cfg-max-request-size cap: depth costs only a few
    // bytes per level, so the request-size limit never bounds the depth.
    assertTrue(payload.length < 5 * 1024 * 1024,
        "PoC payload (" + payload.length + " bytes) should be far below the 5 MB request cap");

    Throwable result = decodeOnBoundedStack(payload);

    assertNotNull(result,
        "Decoding a " + OVERFLOW_DEPTH + "-level filter must be rejected, but it succeeded");
    assertFalse(result instanceof StackOverflowError,
        "VULNERABLE: decode overflowed the stack instead of rejecting the over-nested filter: " + result);
    assertTrue(result instanceof LDAPException,
        "Expected a controlled LDAPException, but got: " + result);
    assertEquals(((LDAPException) result).getResultCode(), PROTOCOL_ERROR,
        "Over-nested filter should be rejected as a protocol error");
  }

  /**
   * Decodes {@code payload} via {@link RawFilter#decode(ASN1Reader)} on a worker
   * thread with a small stack and returns the {@link Throwable} that escaped
   * decode, or {@code null} if decode completed normally. Captures
   * {@link Throwable} (including {@link Error}) so a regression to the unbounded
   * recursion surfaces as a {@link StackOverflowError} rather than killing the
   * test JVM thread silently.
   */
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
          ASN1Reader reader = ASN1.getReader(payload);
          RawFilter.decode(reader);
        }
        catch (Throwable t)
        {
          escaped[0] = t;
        }
      }
    };

    Thread worker = new Thread(null, decode, "ghsa-rv4q-decode", DECODE_STACK_BYTES);
    worker.start();
    worker.join();
    return escaped[0];
  }

  /**
   * Builds the BER (definite-length) wire encoding of an AND filter nested
   * {@code depth} levels deep, wrapping an {@code (objectClass=*)} present leaf:
   * <pre>
   *   A0 L ( A0 L ( ... ( 87 0B "objectClass" ) ... ) )
   * </pre>
   * Constructed inside-out so the length fields are exact, which is what
   * {@link RawFilter#decode} reads. (Indefinite-length BER is not used because
   * the OpenDJ reader does not support it.)
   */
  private static byte[] buildNestedAndFilter(final int depth) throws Exception
  {
    // Innermost leaf: presence filter (objectClass=*).
    ByteStringBuilder leafBuilder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(leafBuilder);
    LDAPFilter.createPresenceFilter("objectClass").write(writer);
    writer.flush();
    byte[] leaf = leafBuilder.toByteString().toByteArray();

    // Build each AND wrapper prefix (0xA0 + definite length) from the inside out.
    List<byte[]> prefixes = new ArrayList<>(depth);
    int contentLen = leaf.length;
    for (int i = 0; i < depth; i++)
    {
      byte[] lengthBytes = berLength(contentLen);
      byte[] prefix = new byte[1 + lengthBytes.length];
      prefix[0] = TYPE_FILTER_AND; // 0xA0
      System.arraycopy(lengthBytes, 0, prefix, 1, lengthBytes.length);
      prefixes.add(prefix);
      contentLen += prefix.length;
    }

    // Assemble: outermost prefix first ... innermost prefix ... leaf.
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

  /** Encodes {@code len} as a BER definite-length field (short or long form). */
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
