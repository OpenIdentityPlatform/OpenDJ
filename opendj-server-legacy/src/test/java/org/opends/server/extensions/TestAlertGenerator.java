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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.LinkedHashMap;

import org.opends.server.api.AlertGenerator;
import org.forgerock.opendj.ldap.DN;

/** This class defines a simple alert generator that may be used for testing purposes. */
public class TestAlertGenerator
       implements AlertGenerator
{
  /** The fully-qualified name of this class for debugging purposes. */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.TestAlertGenerator";

  /** The DN of the "configuration entry" for this alert generator. */
  private DN configEntryDN;

  /** The alert description used for testing purposes. */
  private String alertDescription;

  /** The alert type used for testing purposes. */
  private String alertType;

  /**
   * Creates a new instance of this test alert generator.
   *
   * @throws  Exception  if an unexpected problem occurs.
   */
  public TestAlertGenerator()
         throws Exception
  {
    configEntryDN    = DN.valueOf("cn=Test Alert Generator,cn=config");
    alertType        = "org.opends.server.TestAlert";
    alertDescription = "This is a test alert.";
  }

  /**
   * Retrieves the alert type for this test alert generator.
   *
   * @return  The alert type for this test alert generator.
   */
  public String getAlertType()
  {
    return alertType;
  }

  @Override
  public DN getComponentEntryDN()
  {
    return configEntryDN;
  }

  @Override
  public String getClassName()
  {
    return CLASS_NAME;
  }

  @Override
  public LinkedHashMap<String,String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<>();

    alerts.put(alertType, alertDescription);

    return alerts;
  }
}
