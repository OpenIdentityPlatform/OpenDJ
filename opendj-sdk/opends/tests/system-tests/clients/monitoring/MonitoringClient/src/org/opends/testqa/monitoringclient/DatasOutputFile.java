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

import java.math.BigDecimal;
import java.math.MathContext;
import java.text.SimpleDateFormat;

import java.util.Locale;

/**
 * Consumer who save the datas in a file.
 */
public class DatasOutputFile extends Thread {

  /**
   * Main class of the client.
   */
  private MonitoringClient client;

  /**
   * The datas retrieved by the producers.
   */
  private DatasBuffer datas;

  /**
   * The previous datas retrieved by the producers to do an average.
   */
  private DatasBuffer previousDatas;

  /**
   * The output string to write in the datas file.
   */
  private String datasOutput;

  /**
   * The name of the datas file.
   */
  private String datasFileName;

  private SimpleDateFormat sdf;

  /**
   * Indicate if the client have just been launched.
   */
  private Boolean firstRun;

  /**
   * Contructs a DataOutputFile thread whith the specified values.
   *
   * @param client        The main class of the client.
   * @param datasFileName The name of the datas file.
   */
  public DatasOutputFile (MonitoringClient client, String datasFileName) {
    this.client = client;
    this.datas = new DatasBuffer(client);
    this.previousDatas = new DatasBuffer(client);
    this.datasOutput = new String();
    this.datasFileName = datasFileName;

    sdf = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.US);

    firstRun = true;
  }

  /**
   * Retrieve the datas, format its and save its in a file.
   */
  @Override
  public void run () {

    while(true) {

      try {
        synchronized(client.getDatasBuffer()) {
          client.getDatasBuffer().wait();
        }
      } catch (InterruptedException e) {
        System.out.println(e.getLocalizedMessage());
      }

      datasRetrieving();
      datasProcessing();
      datasExploitation();

    }
  }

  /**
   * Retrieve the datas and clear the main class containers.
   */
  private void datasRetrieving() {
    previousDatas = datas.clone();
    datas = client.getDatasBuffer().clone();
  }

  /**
   * Format the datas to be easy to parse with graph generators.
   */
  private void datasProcessing() {
    datasOutput = "";

    if (firstRun) {
      datasOutput += "# DATE";
      for (Data d : datas.getAllDatas()) {
        datasOutput += " " + d.getProtocol() + "|" + d.getAttribute();
        if (d.getParameters().containsKey("output")) {
          if (d.getParameters().getProperty("output").equals("value")) {
            datasOutput += "|value";
          } else if (d.getParameters().getProperty("output").equals("diff")) {
            datasOutput += "|diff";
          } else if (d.getParameters().getProperty("output").equals("average")){
            datasOutput += "|average";
          }
        } else {
          datasOutput += "|value";
        }
      }
      datasOutput += "\n";
    }

    datasOutput += "[" + sdf.format(datas.getDate()) + "]";
    for (Data d : datas.getAllDatas()) {
      try {
        if (d.getParameters().containsKey("output") &&
                !d.getParameters().getProperty("output").equals("value")) {

          Data pData = previousDatas.getData(d.getAttribute(),
                  d.getParameters());

          if (d.getValue().equals("-1") ||
                  (!firstRun && pData.getValue().equals("-1"))) {
            datasOutput += " -1";
          } else if (firstRun) {
            datasOutput += " 0";
          } else {

            int diff = Integer.parseInt(d.getValue()) -
                  Integer.parseInt(pData.getValue());

            if (d.getParameters().getProperty("output").equals("diff")) {
              datasOutput += " " + diff;
            } else if (d.getParameters().getProperty("output").equals(
                    "average")) {
              BigDecimal res = new BigDecimal(diff).multiply(
                      new BigDecimal(client.getTimeUnit())).divide(
                      new BigDecimal(client.getInterval()), new MathContext(3));
               datasOutput += " " + res;
            }
          }
        } else {
           datasOutput += " " + d.getValue();
        }
      } catch (NumberFormatException e) {
        client.getErrorsBuffer().addError(d.getProtocol(), d.getAttribute(),
                "The attribute value: \"" + d.getValue() + "\" isn't a number");
      }
    }
//    datasOutput += " [" + sdf.format(new Date()) +"]";
    datasOutput += "\n";

  }

  /**
   * Save the datasOutput string in a file.
   */
  private void datasExploitation() {

    try {
      FileOutputStream fDatas;

      if (firstRun) {
        firstRun = false;
        fDatas = new FileOutputStream(datasFileName);
      } else {
        fDatas = new FileOutputStream(datasFileName,true);
      }

      fDatas.write(datasOutput.getBytes());
      System.out.print(datasOutput); // Optional

      fDatas.close();

    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
    }

  }
}
