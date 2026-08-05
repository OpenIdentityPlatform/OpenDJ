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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.loggers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests the decoration of the arguments of a debug log message. */
@SuppressWarnings("javadoc")
public class DebugMessageFormatterTest extends DirectoryServerTestCase
{
  @DataProvider
  public Object[][] arrayArguments()
  {
    return new Object[][] {
      { new boolean[] { true, false },    "[ true, false ]" },
      { new byte[] { 1, -2 },             "[ 1, -2 ]" },
      { new char[] { 'a', 'b' },          "[ a, b ]" },
      { new double[] { 1.5, 2.0 },        "[ 1.5, 2.0 ]" },
      { new float[] { 1.5f, 2.0f },       "[ 1.5, 2.0 ]" },
      { new int[] { 1, 2 },               "[ 1, 2 ]" },
      { new long[] { 1L, 2L },            "[ 1, 2 ]" },
      { new short[] { 1, 2 },             "[ 1, 2 ]" },
      { new String[] { "a", "b" },        "[ a, b ]" },
      { new boolean[0],                   "[  ]" },
    };
  }

  /** Arrays are formatted element by element, whatever their component type. */
  @Test(dataProvider = "arrayArguments")
  public void testArrayArgumentIsDecorated(Object array, String expected)
  {
    assertThat(DebugMessageFormatter.format("%s", new Object[] { array })).isEqualTo(expected);
  }

  /** Lists, maps and object arrays decorate their elements in turn. */
  @Test
  public void testNestedArgumentsAreDecorated()
  {
    final Map<String, Object> map = new LinkedHashMap<>();
    map.put("key", new int[] { 1, 2 });

    assertThat(DebugMessageFormatter.format("%s", new Object[] { map })).isEqualTo("{ key=[ 1, 2 ] }");
    assertThat(DebugMessageFormatter.format("%s", new Object[] { Arrays.asList("a", new int[] { 3 }) }))
        .isEqualTo("[ a, [ 3 ] ]");
    assertThat(DebugMessageFormatter.format("%s", new Object[] { new Object[] { new char[] { 'x' } } }))
        .isEqualTo("[ [ x ] ]");
  }

  @Test
  public void testNonArrayArgumentsAreLeftAlone()
  {
    assertThat(DebugMessageFormatter.format("%s and %s", new Object[] { "text", 42 })).isEqualTo("text and 42");
    assertThat(DebugMessageFormatter.format("%s", new Object[] { null })).isEqualTo("null");
  }

  /** A format string which does not match its arguments falls back to concatenation. */
  @Test
  public void testInvalidFormatFallsBackToConcatenation()
  {
    assertThat(DebugMessageFormatter.format("%d", new Object[] { new int[] { 1 } })).isEqualTo("%d [ 1 ]");
    assertThat(DebugMessageFormatter.format(null, new Object[] { new int[] { 1 } })).isEqualTo(" [ 1 ]");
  }
}
