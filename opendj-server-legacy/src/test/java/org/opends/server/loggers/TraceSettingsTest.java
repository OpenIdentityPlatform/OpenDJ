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
 * Copyright 2014 ForgeRock AS.
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
