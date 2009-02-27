package org.opends.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 *
 * @author Sun Microsystems Inc.
 */
public class BuildStatus {
  // builds location
  private static final String ROOT_DIR = "/daily-builds";

  // constants for html
  private static final String GREEN = "background-color: rgb(51, 255, 51);";
  private static final String RED = "background-color: rgb(255, 102, 102);";
  private static final String BODY = "#BODY#";
  private static final String TABLES = "#TABLES#";

  private static final String PAGE =
          "<html>" +
          " <head>" +
          "  <meta content=\"text/html; charset=ISO-8859-1\" http-equiv=\"content-type\">" +
          "  <title>OpenDS builds status</title>" +
          " </head>" +
          " <body>" +
          "  <div style=\"text-align: center;\"><big style=\"font-weight: bold;\">Daily builds status</big><br></div>" +
          "  <br>" +
          TABLES +
          " </body>" +
          "</html>\n";

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    // a collection of builds ordered from yougest to oldest
    Collection<DailyBuild> dailyBuilds = new TreeSet<DailyBuild>(
            new Comparator<DailyBuild>() {

              public int compare(DailyBuild o1, DailyBuild o2) {
                String date1 = o1.id.substring(0, 8);
                String date2 = o2.id.substring(0, 8);
                return date2.compareTo(date1);
              }
              });
    try {
      // list directories in daily build directory
      String[] filenames = new File(ROOT_DIR).list(new FilenameFilter() {

        public boolean accept(File dir, String name) {
          for (int i = 0; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) {
              return false;
            }
          }
          return true;
        }
      });
      for (String filename : filenames) {
        try {
          // dedicated to Unix only !!!
          URL url = new URL("file:///" + ROOT_DIR + "/" + filename + "/build-" + filename + ".log");

          DailyBuild dailyBuild = new DailyBuild(filename);
          Build build = null;
          TestFailure failure = null;

          boolean skip = false;
          BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
          String line;
          boolean firstTarget = true;
          while ((line = reader.readLine()) != null) {
            if (skip) {
              skip = false;
            } else if (line.startsWith("Building with Java version")) {
              // build beginning
              firstTarget = true;
              build = new Build(JDK.valueOf(line.substring(27, 31)));
            } else if (line.startsWith("BUILD")) {
              // build ending. Status can be : FAILED SUCCESSFUL
              if (firstTarget) {
                build.setSuccess(line.substring(6, line.length()).equals("SUCCESSFUL"));
                dailyBuild.setBuild(build);
                firstTarget = false;
              } else {
                if (build.isSuccess()) {
                  build.setSuccess(line.substring(6, line.length()).equals("SUCCESSFUL"));
                }
              }
            } else if (line.contains("F A I L U R E ! ! !")) {
              // test failed
              failure = new TestFailure(line);
              skip = true; // skip next empty line
            } else if (failure != null && line.equals("   [testng] ")) {
              // end of test failure trace
              build.addFailure(failure);
              failure = null;
            } else {
              if (failure != null) {
                failure.appendLog(line.replace("<", "&lt;").replace(">", "&gt;"));
              }
            }
          }
          dailyBuilds.add(dailyBuild);

          reader.close();
        } catch (Throwable t) {
        // may have lost one build
        }
      }

      StringBuilder allBuilds = new StringBuilder();
      for (DailyBuild daily : dailyBuilds) {
        allBuilds.append(daily.toString());
      }

      String html = PAGE.replace(TABLES, allBuilds);

      // write status.html page
      FileWriter writer = new FileWriter(ROOT_DIR + "/status.html", false);
      writer.write(html);
      writer.close();
    } catch (Throwable t) {
    }
  }

  private static enum JDK {

    jdk5, jdk6
  }

  /*
   * A DailyBuild has an identifier (date) and two builds (jdk5+jdk6).
   */
  private static class DailyBuild {

    private static final String DATE = "#DATE#";
    private static final String JDK5 = "#JDK5#";
    private static final String JDK6 = "#JDK6#";
    private static final String COLOR = "#COLOR#";
    private static final String TEMPLATE = "<table style=\"width: 100%; text-align: center;\" border=\"1\" cellpadding=\"2\" cellspacing=\"2\">" +
            "<tbody>" +
            "<tr>" +
            "<td colspan=\"2\" rowspan=\"1\" style=\"" + COLOR +
            "vertical-align: middle; text-align: center;\">" + DATE +
            "</td>" +
            "</tr>" +
            "<tr>" + JDK5 + "</tr>" +
            "<tr>" + JDK6 + "</tr>" +
            "<br>";

    private static final String JDK_MISSING = "<td style=\"background-color: rgb(255, 204, 0);\">Unknown</td><td></td>";
    private Build jdk5;
    private Build jdk6;
    private String id;

    public DailyBuild(String id) {
      this.id = id;
    }

    public boolean isSuccess() {
      return this.jdk5 != null && this.jdk5.isSuccess() && this.jdk6 != null && this.jdk6.isSuccess();
    }

    public void setBuild(Build build) {
      if (build.getJDK().equals(JDK.jdk5)) {
        this.jdk5 = build;
      } else {
        this.jdk6 = build;
      }
    }

    @Override
    public String toString() {
      //String date = this.id.substring(0, 4) + "." + this.id.substring(4, 6) + "." + this.id.substring(6, 8);
      String date = "<a href=\"" + this.id + "/build-" + this.id + ".log\">" + this.id.substring(0, 4) + "." + this.id.substring(4, 6) + "." + this.id.substring(6, 8) + "</a>";
      String result = TEMPLATE.replace(DATE, date).replace(JDK5, this.jdk5 == null ? JDK_MISSING : this.jdk5.toString()).replace(JDK6, this.jdk6 == null ? JDK_MISSING : this.jdk6.toString());
      if (this.isSuccess()) {
        result = result.replace(COLOR, GREEN);
      } else {
        result = result.replace(COLOR, RED);
      }
      return result;
    }
  }

  /*
   * A Build has a JDK version (5 or 6), a success and an optional list of
   * failures.
   */
  private static class Build {

    private static final String FAILURES = "#FAILURES#";
    private static final String VERSIONS = "#VERSIONS#";
    private static final String TEMPLATE =
            "<td style=\"" + DailyBuild.COLOR + "width: 10%; vertical-align: middle; text-align: center;\">" + VERSIONS + "</td>" +
            "<td style=\"font-family: monospace; text-align: left;\">" + FAILURES + "</td>";
    private JDK jdk;
    private boolean success;
    private List<TestFailure> failures;

    public Build(JDK jdk) {
      this.jdk = jdk;
    }

    public JDK getJDK() {
      return this.jdk;
    }

    public boolean isSuccess() {
      return this.success;
    }

    public void setSuccess(boolean success) {
      this.success = success;
    }

    public void addFailure(TestFailure failure) {
      if (this.failures == null) {
        this.failures = new ArrayList<TestFailure>();
      }
      this.failures.add(failure);
    }

    @Override
    public String toString() {
      String result = TEMPLATE.replace(VERSIONS, this.jdk.name());
      if (!this.success) {
        StringBuilder sb = new StringBuilder();
        if (this.failures != null) {
          for (TestFailure failure : this.failures) {
            sb.append(failure.sb).append("<br>");
          }
        } else {
          sb.append("<b>Unknown error.</b>");
        }
        result = result.replace(FAILURES, sb).replace(DailyBuild.COLOR, RED);
      } else {
        result = result.replace(FAILURES, "").replace(DailyBuild.COLOR, GREEN);
      }
      return result;
    }
  }

  /*
   * A TestFailure encapsulates a list of relevant logs.
   */
  private static class TestFailure {

    private StringBuilder sb;
    private String title;

    public TestFailure(String title) {
      this.title = title.substring(12, title.length());
    }

    public void appendLog(String log) {
      if (this.sb == null) {
        this.sb = new StringBuilder("<p><b>").append(this.title).append("</b><br>");
      }
      this.sb.append(log.substring(12, log.length())).append("<br>");
    }
  }
}

