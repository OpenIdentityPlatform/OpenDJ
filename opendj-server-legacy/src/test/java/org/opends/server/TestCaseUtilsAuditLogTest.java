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
package org.opends.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.annotations.Test;

/** Tests for the audit logs included in test failure diagnostics. */
@SuppressWarnings("javadoc")
public class TestCaseUtilsAuditLogTest extends DirectoryServerTestCase
{
  @Test
  public void shortAuditLogIsIncludedInFull() throws IOException
  {
    final String audit = "dn: cn=Jos\u00e9\nchangetype: add\n";

    assertTrue(getAuditDiagnostics(appendDiagnosticsForLog("audit", audit)).startsWith(audit));
  }

  @Test
  public void oversizedAuditRecordIsTruncatedByBytes() throws IOException
  {
    final String ending = " last value";
    final String audit = "old audit record\n" + "\u00e9".repeat(128 * 1024) + ending;

    final String diagnostics = getAuditDiagnostics(appendDiagnosticsForLog("audit", audit));
    final int suffix = diagnostics.indexOf(ending);
    assertTrue(suffix >= 0, "Missing audit log suffix");
    final String excerpt = diagnostics.substring(0, suffix + ending.length());
    final int bytes = excerpt.getBytes(UTF_8).length;
    assertTrue(bytes <= 16 * 1024 + 128, "Audit diagnostics printed " + bytes + " bytes");
    assertTrue(excerpt.startsWith("[truncated"), "Missing audit truncation notice");
    assertFalse(excerpt.contains("\ufffd"), "Audit log suffix starts inside a UTF-8 character");
  }

  @Test
  public void rotatedAuditLogIsKeptOnDisk() throws IOException
  {
    final String audit = "previous audit record\n";

    final String diagnostics = appendDiagnosticsForLog("audit.20261127163239Z", audit);
    assertFalse(diagnostics.contains(audit), "Rotated audit record was printed");
    assertTrue(diagnostics.contains("Rotated audit logs"), "Missing rotated audit log notice");
  }

  private String getAuditDiagnostics(String diagnostics)
  {
    final String header = "audit contents:" + System.lineSeparator();
    final int start = diagnostics.indexOf(header);
    assertTrue(start >= 0, "Missing diagnostics for audit");
    return diagnostics.substring(start + header.length());
  }

  private String appendDiagnosticsForLog(String fileName, String audit) throws IOException
  {
    final Path logDirectory = Files.createDirectories(TestCaseUtils.paths.testInstanceRoot.toPath().resolve("logs"));
    final Path auditFile = Files.writeString(logDirectory.resolve(fileName), audit, UTF_8, CREATE_NEW);
    try
    {
      final StringBuilder diagnostics = new StringBuilder();
      TestCaseUtils.appendLogsContents(diagnostics);
      assertTrue(audit.equals(Files.readString(auditFile, UTF_8)), "Diagnostic changed the audit log");
      return diagnostics.toString();
    }
    finally
    {
      Files.deleteIfExists(auditFile);
    }
  }
}
