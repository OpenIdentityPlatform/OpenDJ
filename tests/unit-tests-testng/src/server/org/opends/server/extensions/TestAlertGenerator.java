/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.util.LinkedHashMap;

import org.opends.server.api.AlertGenerator;
import org.opends.server.types.DN;



/**
 * This class defines a simple alert generator that may be used for testing
 * purposes.
 */
public class TestAlertGenerator
       implements AlertGenerator
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.TestAlertGenerator";



  // The DN of the "configuration entry" for this alert generator.
  private DN configEntryDN;

  // The alert description used for testing purposes.
  private String alertDescription;

  // The alert type used for testing purposes.
  private String alertType;



  /**
   * Creates a new instance of this test alert generator.
   *
   * @throws  Exception  if an unexpected problem occurs.
   */
  public TestAlertGenerator()
         throws Exception
  {
    configEntryDN    = DN.decode("cn=Test Alert Generator,cn=config");
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



  /**
   * {@inheritDoc}
   */
  public DN getComponentEntryDN()
  {
    return configEntryDN;
  }



  /**
   * {@inheritDoc}
   */
  public String getClassName()
  {
    return CLASS_NAME;
  }



  /**
   * {@inheritDoc}
   */
  public LinkedHashMap<String,String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(alertType, alertDescription);

    return alerts;
  }
}

