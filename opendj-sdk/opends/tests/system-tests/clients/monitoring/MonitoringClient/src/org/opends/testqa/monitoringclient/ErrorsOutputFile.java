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

package org.opends.testqa.monitoringclient;

import java.io.FileOutputStream;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Consumer who save the errors in a file.
 */
public class ErrorsOutputFile extends Thread {

  /**
   * Main class of the client.
   */
  private MonitoringClient client;

  /**
   * The errors retrieved by the producers.
   */
  private ErrorsBuffer errors;

  /**
   * The output string to write in the error file.
   */
  private String errorsOutput;

  /**
   * The name of the errors file.
   */
  private String errorsFileName;

  private SimpleDateFormat  sdf;

  /**
   * Indicate if the client have just been launched.
   */
  private Boolean firstRun;

  /**
   * Contructs a ErrorsOutputFile thread whith the specified values.
   *
   * @param client          The main class of the client.
   * @param errorsFileName  The name of the errors file.
   */
  public ErrorsOutputFile (MonitoringClient client, String errorsFileName) {
    this.client = client;
    this.errors = new ErrorsBuffer(client);
    this.errorsOutput = new String();
    this.errorsFileName = errorsFileName;

    sdf = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.US);

    firstRun = true;
  }


  /**
   * Retrieve the errors, format its and save its in a file.
   */
  @Override
  public void run () {

    while(true) {

      errorsRetrieving();
      errorsProcessing();
      errorsExploitation();

      try {
        synchronized(client.getErrorsBuffer()) {
          client.getErrorsBuffer().wait();
        }
      } catch (InterruptedException e) {
        System.out.println(e.getLocalizedMessage());
      }

    }
  }

  /**
   * Retrieve the errors.
   */
  private void errorsRetrieving() {

    errors = client.getErrorsBuffer().clone();
  }

  /**
   * Format the errors.
   */
  private void errorsProcessing() {
    errorsOutput = "";

    if (firstRun) {
      errorsOutput += "# DATE PROTOCOL|ATTRIBUTE MESSAGE\n";
    }

    while (!errors.isEmpty()) {
      Error e = errors.removeFirstError();
      errorsOutput += "[" + sdf.format(e.getDate()) + "] ";
      errorsOutput += e.getProtocol() + "|";
      if (e.getAttribute().equals("")) {
        errorsOutput += "General";
      } else {
        errorsOutput += e.getAttribute();
      }
      errorsOutput += " " + e.getMessage() + "\n";
    }
  }

  /**
   * Save the errorsOutput string in a file.
   */
  private void errorsExploitation() {

    try {
      FileOutputStream fErrors;

      if (firstRun) {
        firstRun = false;
        fErrors = new FileOutputStream(errorsFileName);
      } else {
        fErrors = new FileOutputStream(errorsFileName,true);
      }

      fErrors.write(errorsOutput.getBytes());
      System.out.print(errorsOutput); // Optional

      fErrors.close();
    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
    }

  }
}
