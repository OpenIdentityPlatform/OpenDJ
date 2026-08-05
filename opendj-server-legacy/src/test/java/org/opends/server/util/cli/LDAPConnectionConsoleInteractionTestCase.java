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
package org.opends.server.util.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.messages.ToolMessages.INFO_CERTIFICATE_NAME_MISMATCH_TEXT_CLI;
import static org.opends.messages.ToolMessages.INFO_CERTIFICATE_NOT_TRUSTED_TEXT_CLI;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/** Tests the message shown when the certificate presented by a server is rejected. */
@SuppressWarnings("javadoc")
public class LDAPConnectionConsoleInteractionTestCase extends DirectoryServerTestCase
{
  private static final String HOST = "localhost";
  private static final int PORT = 4444;

  @Test
  public void testUntrustedCertificateIsReported()
  {
    assertThat(rejectionMessage(ApplicationTrustManager.Cause.NOT_TRUSTED))
        .isEqualTo(INFO_CERTIFICATE_NOT_TRUSTED_TEXT_CLI.get(HOST, PORT).toString());
  }

  @Test
  public void testHostNameMismatchIsReported()
  {
    assertThat(rejectionMessage(ApplicationTrustManager.Cause.HOST_NAME_MISMATCH))
        .isEqualTo(INFO_CERTIFICATE_NAME_MISMATCH_TEXT_CLI.get(HOST, PORT, HOST, HOST, PORT).toString());
  }

  /** The two causes must not lead to the same message: the user needs to know what is wrong. */
  @Test
  public void testTheTwoCausesAreReportedDifferently()
  {
    assertThat(rejectionMessage(ApplicationTrustManager.Cause.NOT_TRUSTED))
        .isNotEqualTo(rejectionMessage(ApplicationTrustManager.Cause.HOST_NAME_MISMATCH));
  }

  private static String rejectionMessage(ApplicationTrustManager.Cause cause)
  {
    return LDAPConnectionConsoleInteraction.getCertificateRejectionMessage(cause, HOST, PORT).toString();
  }
}
