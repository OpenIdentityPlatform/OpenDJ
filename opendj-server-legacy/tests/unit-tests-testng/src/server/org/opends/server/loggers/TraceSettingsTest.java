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
 *
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.loggers;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class TraceSettingsTest extends DirectoryServerTestCase
{
  @Test
  public void testParseTraceSettingsDisabled() throws Exception
  {
    TraceSettings settings = TraceSettings.parseTraceSettings("stack=2,noargs,cause,noretval");

    assertEquals(settings.getStackDepth(), 2);
    assertEquals(settings.isNoArgs(), true);
    assertEquals(settings.isIncludeCause(), true);
    assertEquals(settings.isNoRetVal(), true);
    assertEquals(settings.getLevel(), TraceSettings.Level.DISABLED);
  }

  @Test
  public void testParseTraceSettingsEnabled() throws Exception
  {
    TraceSettings settings = TraceSettings.parseTraceSettings("enabled,stack,noargs,cause,noretval");

    assertEquals(settings.getStackDepth(), DebugStackTraceFormatter.COMPLETE_STACK);
    assertEquals(settings.isNoArgs(), true);
    assertEquals(settings.isIncludeCause(), true);
    assertEquals(settings.isNoRetVal(), true);
    assertEquals(settings.getLevel(), TraceSettings.Level.ALL);
  }

  @Test
  public void testParseTraceSettingsExceptionsOnly() throws Exception
  {
    TraceSettings settings = TraceSettings.parseTraceSettings("enabled,exceptionsonly");

    assertEquals(settings.getStackDepth(), 0);
    assertEquals(settings.isNoArgs(), false);
    assertEquals(settings.isIncludeCause(), false);
    assertEquals(settings.isNoRetVal(), false);
    assertEquals(settings.getLevel(), TraceSettings.Level.EXCEPTIONS_ONLY);
  }
}
