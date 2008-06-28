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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Properties;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.MovingAverage;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

public class ChartGenerator {

  Properties parsedArguments;

  public ChartGenerator (Properties parsedArguments) {
    this.parsedArguments = parsedArguments;
  }


  private Hashtable<String, TimeSeriesCollection> createDataset () {

    Hashtable dataset = new Hashtable();
    SimpleDateFormat sdf = new SimpleDateFormat("[dd/MMM/yyyy:HH:mm:ss Z]",
            Locale.US);

    try {
      BufferedReader datas = new BufferedReader(new FileReader(
              parsedArguments.getProperty("filesRepository") + File.separator +
              "datas"));

      String line = datas.readLine();
      if (line == null) {
        throw new ParseException("Error during the parsing of the data file",0);
      }
      String[] attributes = line.split(" ");

      if (!attributes[0].equals("#")) {
        throw new ParseException("Error during the parsing of the data file",0);
      }

      int it = 2;
      if (parsedArguments.containsKey("attribute")) {
        while(it < attributes.length && !attributes[it].startsWith(
                parsedArguments.getProperty("attribute"))){
          it++;
        }
        if (it == attributes.length) {
          throw new ParseException("Unknown attribute " +
                  parsedArguments.getProperty("attribute"),0);
        }
      }

      if (parsedArguments.containsKey("attribute")) {
        TimeSeriesCollection tsc = new TimeSeriesCollection();
        tsc.addSeries(new TimeSeries(parsedArguments.getProperty("attribute").replace('|', ' '),
                Second.class));
        dataset.put(parsedArguments.getProperty("attribute").replace('|', ' '), tsc);
      } else {
        for (int i=0; i<attributes.length-2;i++) {
         TimeSeriesCollection tsc = new TimeSeriesCollection();
         tsc.addSeries(new TimeSeries(attributes[i+2].replace('|', ' '), Second.class));
         dataset.put(attributes[i+2].replace('|', ' '), tsc);
        }
      }

      int periodCount = 0;
      line = datas.readLine();
      while(line != null) {
        String[] values = line.split(" ");
        periodCount++;

        Date date = sdf.parse(values[0] + " " + values[1]);

        if (parsedArguments.containsKey("attribute")) {
          TimeSeriesCollection tsc = (TimeSeriesCollection)dataset.get(
                  parsedArguments.getProperty("attribute").replace('|', ' '));
          if (values[it].equals("-1") && (
                  parsedArguments.containsKey("ignore") ||
                  parsedArguments.containsKey("movingAverage"))) {
            tsc.getSeries(0).add(new Second(date), null);
          } else {
            tsc.getSeries(0).add(new Second(date), Double.parseDouble(
                    values[it]));
          }


        } else {
          for (int i=0; i<values.length-2;i++) {
            TimeSeriesCollection tsc = (TimeSeriesCollection)dataset.get(
                    attributes[i+2].replace('|', ' '));
            if (values[i+2].equals("-1") && (
                    parsedArguments.containsKey("ignore") ||
                    parsedArguments.containsKey("movingAverage"))) {
              tsc.getSeries(0).add(new Second(date), null);
            } else {
              tsc.getSeries(0).add(new Second(date), Double.parseDouble(
                      values[i+2]));
            }
          }
        }

        line = datas.readLine();
      }

      if (parsedArguments.containsKey("movingAverage")) {
        Enumeration keys = dataset.keys();
        Enumeration elements = dataset.elements();
        while (keys.hasMoreElements()) {
          String title = (String)keys.nextElement();
          TimeSeriesCollection tsc =
                  (TimeSeriesCollection)elements.nextElement();
          TimeSeries mav = MovingAverage.createMovingAverage(tsc.getSeries(0),
                  title + " - Moving Average", periodCount, 0);
          tsc.removeSeries(0);
          tsc.addSeries(mav);
        }

      }

    } catch (IOException e) {
      System.out.println("Error to open the datas file");
      System.exit(1);
    } catch (ParseException e) {
      System.out.println(e.getMessage());
      System.exit(1);
    }

    return dataset;

  }

  public Hashtable <String, JFreeChart> createCharts(
          Hashtable <String, TimeSeriesCollection> dataset) {
    Hashtable <String, JFreeChart> charts =
            new Hashtable <String, JFreeChart> ();
    Enumeration keys = dataset.keys();
    Enumeration elements = dataset.elements();

    while (keys.hasMoreElements()) {
      String title = (String)keys.nextElement();
      TimeSeriesCollection tsc = (TimeSeriesCollection)elements.nextElement();
      charts.put(title,
              ChartFactory.createTimeSeriesChart(
              title, // title
              "Date", // x-axis label
              "Values", // y-axis label
              tsc, // datas
              true, // create legend?
              true, // generate tooltips?
              false // generate URLs?
              )
      );
    }
    return charts;
  }


  public void saveChartAsPNG (Hashtable <String, JFreeChart> charts) {
    Enumeration keys = charts.keys();
    Enumeration elements = charts.elements();

    try {
      File r = new File(parsedArguments.getProperty("repository"));
      r.mkdirs();

      while (keys.hasMoreElements()) {
        String title = (String)keys.nextElement();
        JFreeChart chart = (JFreeChart)elements.nextElement();

        ChartUtilities.saveChartAsPNG(
                new File(r, title.replace(" ", "_") + ".png"),
                chart,
                Integer.parseInt(parsedArguments.getProperty("width")),
                Integer.parseInt(parsedArguments.getProperty("height"))
        );
      }

    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
    }
  }

  public static void main (String[] args) {
    ChartGenerator cg = new ChartGenerator(argumentParser(args));
    cg.saveChartAsPNG(cg.createCharts(cg.createDataset()));
  }

/**
   * Parse the command line argument.
   *
   * @param args the command line argument
   * @return the parsed argument
   */
  private static Properties argumentParser(String args[]) {

    Properties parsedArguments = new Properties();

    String usage = "Usage: java -jar ChartGernerator.java [-a <attribute>] " +
            "[-i] [-m] [-w <width>] [-h <height] [-f <filesRepository>] " +
            "[-r <chartrepositiry>]\n";

    try {

      if ( args.length == 1 && (args[0].equals("-H") ||
              args[0].equals("--help") || args[0].equals("-?"))) {
        System.out.println("This utility generate graphs from monitoring " +
                "datas\n\n" +

                usage + "\n" +

                "-a, --attribute\n" +
                "    Attribute we need to generate the chart\n" +
                "-i, --ignore\n" +
                "    Ignore the errors to generate the chart\n" +
                "-m, --movingAverage" +
                "    Generate moving average from the datas file" +
                "-w, --width\n" +
                "    Image width (in pixels)\n" +
                "-h, --height\n" +
                "    Image height (in pixels)\n" +
                "-f, --filesRepository\n" +
                "    Repository where data are to take in input\n" +
                "-r, --repository\n" +
                "    Repository of the generated charts\n"
        );
        System.exit(0);
      }

      for(int i=0; i<args.length; i++) {

        if ( (args[i].equals("-a") || args[i].equals("--attribute")) &&
                !parsedArguments.containsKey("attribute")) {
          if (!args[i+1].startsWith("-")) {
            parsedArguments.setProperty("attribute",args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-i") || args[i].equals("--ignore"))
                && !parsedArguments.containsKey("ignore")) {
            parsedArguments.setProperty("ignore","true");
            i++;

        } else if ( (args[i].equals("-m") || args[i].equals("--movingAverage"))
                && !parsedArguments.containsKey("movingAverage")) {
            parsedArguments.setProperty("movingAverage","true");
            i++;

        } else if ( (args[i].equals("-w") || args[i].equals("--width")) &&
                !parsedArguments.containsKey("width")) {
          if (!args[i+1].startsWith("-")) {
            Integer.parseInt(args[i+1]);
            parsedArguments.setProperty("width",args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-h") || args[i].equals("--height")) &&
                !parsedArguments.containsKey("height")) {
          if (!args[i+1].startsWith("-")) {
            Integer.parseInt(args[i+1]);
            parsedArguments.setProperty("height",args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;
          
        } else if ( (args[i].equals("-f") || 
                args[i].equals("--filesRepository")) &&
                !parsedArguments.containsKey("filesRepository")) {
          if (!args[i+1].startsWith("-")) {
            if (!args[i+1].endsWith(File.separator)) {
              parsedArguments.setProperty("filesRepository", args[i+1]);
            } else {
              parsedArguments.setProperty("filesRepository", 
                      args[i+1].substring(0, args[i+1].length()-1));
            }
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-r") || args[i].equals("--repository")) &&
                !parsedArguments.containsKey("repository")) {
          if (!args[i+1].startsWith("-")) {
            if (!args[i+1].endsWith(File.separator)) {
              parsedArguments.setProperty("repository", args[i+1]);
            } else {
              parsedArguments.setProperty("repository", 
                      args[i+1].substring(0, args[i+1].length()-1));
            }
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else {
          throw new IllegalArgumentException();
        }
      }

      if (!parsedArguments.containsKey("filesRepository")) {
        parsedArguments.setProperty("filesRepository",".");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("repository")) {
        parsedArguments.setProperty("repository","charts");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("width")) {
        parsedArguments.setProperty("width","1000");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("height")) {
        parsedArguments.setProperty("height","500");
//        throw new IllegalArgumentException();
      }


    } catch (IllegalArgumentException e) {
      System.out.println(usage + "See \"ChartGenerator --help\" to get " +
              "more usage help");
      System.exit(0);
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println(usage + "See \"ChartGenerator --help\" to get " +
              "more usage help");
      System.exit(0);
    }

    return parsedArguments;
  }

}
