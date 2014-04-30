/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *      Copyright 2014 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.ByteStringBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test for {@link ByteStringBuilder} and {@link ByteArrayScanner} classes.
 */
@SuppressWarnings("javadoc")
public class ByteArrayTest extends DirectoryServerTestCase
{

  @Test
  public void testBuilderAppendMethodsAndScannerNextMethods() throws Exception
  {
    final boolean bo = true;
    final byte by = 80;
    final short sh = 42;
    final int i = sh + 1;
    final long l = i + 1;
    final String st = "Yay!";
    final Collection<String> col = Arrays.asList("foo", "bar", "baz");
    final CSN csn = new CSN(42424242, 13, 42);

    byte[] bytes = new ByteArrayBuilder()
        .append(bo)
        .append(by)
        .append(sh)
        .append(i)
        .append(l)
        .append(st)
        .appendStrings(col)
        .appendUTF8(i)
        .appendUTF8(l)
        .append(csn)
        .appendUTF8(csn)
        .toByteArray();

    final ByteArrayScanner scanner = new ByteArrayScanner(bytes);
    Assert.assertEquals(scanner.nextBoolean(), bo);
    Assert.assertEquals(scanner.nextByte(), by);
    Assert.assertEquals(scanner.nextShort(), sh);
    Assert.assertEquals(scanner.nextInt(), i);
    Assert.assertEquals(scanner.nextLong(), l);
    Assert.assertEquals(scanner.nextString(), st);
    Assert.assertEquals(scanner.nextStrings(new ArrayList<String>()), col);
    Assert.assertEquals(scanner.nextIntUTF8(), i);
    Assert.assertEquals(scanner.nextLongUTF8(), l);
    Assert.assertEquals(scanner.nextCSN(), csn);
    Assert.assertEquals(scanner.nextCSNUTF8(), csn);
    Assert.assertTrue(scanner.isEmpty());
  }
}
