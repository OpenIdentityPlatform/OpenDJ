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

import java.io.File;
import java.io.IOException;

import java.net.ConnectException;

import java.util.Hashtable;
import java.util.Properties;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.ServiceUnavailableException;

import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

/**
 * Main class of the monitoring client.
 */
public class MonitoringClient {

  /**
   * The parameters of the producers.
   */
  private Hashtable producersConfig;

  /**
   * The parameters of the consumers.
   */
  private String outputRepository;

  /**
   * Interval of time between each threads wake up.
   */
  private int interval;

  /**
   * Unit of time.
   */
  private int timeUnit;

  /**
   * Lock for the producers.
   */
  static Object lock;

  /**
   * Buffer for the datas.
   */
  private DatasBuffer datas;

  /**
   * Buffer for the errors.
   */
  private ErrorsBuffer errors;

  /**
   * Number of consumers.
   */
  private int nbConsumers = 1;

  /**
   * Value for the datas where an error occured.
   */
  static final String ERROR_CODE = "-1";

  /**
   * Wake up the producers very interval of time.
   *
   * @param parsedArguments  The parsed arguments
   */
  public MonitoringClient(Properties parsedArguments) {

    lock = new Object();
    datas = new DatasBuffer(this);
    errors = new ErrorsBuffer(this);
    timeUnit = Integer.parseInt(parsedArguments.getProperty("timeUnit"));
    interval = Integer.parseInt(parsedArguments.getProperty("interval")) *
            timeUnit;


    // Config
    this.init(parsedArguments);

  }


  /**
   * The main method of the MonitoringClient class.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args) {
    new MonitoringClient (MonitoringClient.argumentParser(args)).start();
  }

  /**
   * Start the producers and de consumers and wake up the producers every
   * interval of time.
   */
  public void start() {
     // Start of the producers
    if (producersConfig.containsKey("LDAP")) {
      new LDAPMonitor(this, (Properties)producersConfig.get("LDAP")).start();
    }
    if (producersConfig.containsKey("JMX")) {
      new JMXMonitor(this, (Properties)producersConfig.get("JMX")).start();
    }
    if (producersConfig.containsKey("JVM")) {
      new JMXMonitor(this, (Properties)producersConfig.get("JVM")).start();
    }
    if (producersConfig.containsKey("SNMP")) {
      new SNMPMonitor(this, (Properties)producersConfig.get("SNMP")).start();
    }

    // Start of the consumers
    new DatasOutputFile(
            this, outputRepository + File.separator + "datas").start();
    new ErrorsOutputFile(
            this, outputRepository + File.separator + "errors").start();


    while(true) {

      try {
        Thread.sleep(interval);
      } catch (InterruptedException e) {
        System.out.println(e.getLocalizedMessage());
      }

      // Wake up the producers
      synchronized(lock) {
        lock.notifyAll();
      }

      datas.timeoutExpired();
    }
  }

  /**
   * Returns the buffer for the datas.
   *
   * @return  The buffer for the datas.
   */
  public DatasBuffer getDatasBuffer() {
    return datas;
  }

  /**
   * Returns the buffer for the errors.
   *
   * @return  The buffer for the errors.
   */
  public ErrorsBuffer getErrorsBuffer() {
    return errors;
  }

  /**
   * Returns the interval.
   *
   * @return  The interval of time between each threads wake up.
   */
  public int getInterval() {
    return interval;
  }

  /**
   * Return the time unit.
   *
   * @return the time unit
   */
  public int getTimeUnit() {
    return timeUnit;
  }

  /**
   * Returns the number of consumers.
   *
   * @return  The number of consumers.
   */
  public int getNbConsumers() {
    return nbConsumers;
  }

  /**
   * Sets the properties of the producers.
   *
   * @param producersConfig The properties of the producers.
   */
  public void setProducersConfig (Hashtable producersConfig) {
    this.producersConfig = producersConfig;
  }

  /**
   * Parse the command line argument.
   *
   * @param args the command line argument
   * @return the parsed argument
   */
  public static Properties argumentParser(String args[]) {

    Properties parsedArguments = new Properties();

    String usage = "Usage: java -jar MonitoringClient.java [-h <hostname>] " +
            "[-p <LDAPport>] [-x <JMXport>] [-m <JVMport>] [-s <SNMPport] " +
            "[-D <bindDN>] -w <bindPW> [-f <configFile>] [-r <repository>]" +
            "[-i <interval>] [-u <timeUnit>]\n\n";

    try {

      if ( args.length == 1 && (args[0].equals("-H") ||
              args[0].equals("--help") || args[0].equals("-?"))) {
        System.out.println("This utility monitor an OpenDS server\n\n" +
                usage +

                "-h, --hostname\n" +
                "    Directory server hostname or IP address\n" +
                "-p, --LDAPport\n" +
                "    Directory server LDAP port number\n" +
                "-x, --JMXport\n" +
                "    Directory server JMX port number\n" +
                "-m, --JVMport\n" +
                "    JMX port number of the host JVM\n" +
                "-s, --SNMPport\n" +
                "    Directory server SNMP port number\n" +
                "-D, --bindDN\n" +
                "    DN to use to bind to the server\n" +
                "-w, --bindPassword\n" +
                "    Password to use to bind to the server\n" +
                "-f, --configFile\n" +
                "    Config file to use to monitor the server\n" +
                "-r, --repository\n" +
                "    Repository for the output files" +
                "-i, --interval\n" +
                "    Interval of time between each attributes retrieving\n" +
                "-u, --timeUnit\n" +
                "    Time unit of the interval of time (s | min | h)"
        );
        System.exit(0);
      }

      for(int i=0; i<args.length; i++) {

        if ( (args[i].equals("-h") || args[i].equals("--hostname")) &&
                !parsedArguments.containsKey("host")) {
          if (!args[i+1].startsWith("-")) {
            parsedArguments.setProperty("host",args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-p") || args[i].equals("--LDAPport")) &&
                !parsedArguments.containsKey("LDAPport")) {
          if (!args[i+1].startsWith("-")) {
            Integer.parseInt(args[i+1]);
            parsedArguments.setProperty("LDAPport",args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-x") || args[i].equals("--JMXport")) &&
                !parsedArguments.containsKey("JMXport")) {
          if (!args[i+1].startsWith("-")) {
            Integer.parseInt(args[i+1]);
            parsedArguments.setProperty("JMXport",args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-m") || args[i].equals("--JVMport")) &&
                !parsedArguments.containsKey("JVMport")) {
          if (!args[i+1].startsWith("-")) {
            Integer.parseInt(args[i+1]);
            parsedArguments.setProperty("JVMport",args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-s") || args[i].equals("--SNMPport")) &&
                !parsedArguments.containsKey("SNMPport")) {
          if (!args[i+1].startsWith("-")) {
            Integer.parseInt(args[i+1]);
            parsedArguments.setProperty("SNMPport",args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-D") || args[i].equals("--bindDN")) &&
                !parsedArguments.containsKey("bindDN")) {
          if (!args[i+1].startsWith("-")) {
            parsedArguments.setProperty("bindDN",args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-w") || args[i].equals("--bindPW")) &&
                !parsedArguments.containsKey("bindPW")) {
          if (!args[i+1].startsWith("-")) {
            parsedArguments.setProperty("bindPW", args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-f") || args[i].equals("--configFile")) &&
                !parsedArguments.containsKey("configFile")) {
          if (!args[i+1].startsWith("-")) {
            parsedArguments.setProperty("configFile", args[i+1]);
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
                      args[i+1].substring(0, args[i+1].length() -1));
            }

          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-i") || args[i].equals("--interval")) &&
                !parsedArguments.containsKey("interval")) {
          if (!args[i+1].startsWith("-")) {
            Integer.parseInt(args[i+1]);
            parsedArguments.setProperty("interval", args[i+1]);
          } else {
            throw new IllegalArgumentException();
          }
          i++;

        } else if ( (args[i].equals("-u") || args[i].equals("--timeUnit")) &&
                !parsedArguments.containsKey("timeUnit")) {
          if (!args[i+1].startsWith("-")) {
            if (args[i+1].equals("s")) {
              parsedArguments.setProperty("timeUnit", "1000");
            } else if (args[i+1].equals("min")) {
              parsedArguments.setProperty("timeUnit", "60000");
            } else if (args[i+1].equals("h")) {
              parsedArguments.setProperty("timeUnit", "3600000");
            } else {
              throw new IllegalArgumentException();
            }
            i++;

          } else {
            throw new IllegalArgumentException();
          }

        } else {
          throw new IllegalArgumentException();
        }
      }

      if (!parsedArguments.containsKey("host")) {
        parsedArguments.setProperty("host","localhost");
//        parsedArguments.setProperty("host","havmann");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("LDAPport")) {
        parsedArguments.setProperty("LDAPport", "389");
//        parsedArguments.setProperty("LDAPport", "1389");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("JVMport")) {
        parsedArguments.setProperty("JVMport","0");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("SNMPport")) {
        parsedArguments.setProperty("SNMPport","8085");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("bindDN")) {
        parsedArguments.setProperty("bindDN","cn=Directory Manager");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("bindPW")) {
//        parsedArguments.setProperty("bindPW","toto123");
        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("configFile")) {
        parsedArguments.setProperty("configFile","config.xml");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("repository")) {
        parsedArguments.setProperty("repository",".");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("interval")) {
        parsedArguments.setProperty("interval","3");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("timeUnit")) {
        parsedArguments.setProperty("timeUnit","1000");
//        throw new IllegalArgumentException();
      }
      if (!parsedArguments.containsKey("JMXport")) {
        int JMXport = MonitoringClient.getJMXport(
                parsedArguments.getProperty("host"),
                Integer.parseInt(parsedArguments.getProperty("LDAPport")),
                parsedArguments.getProperty("bindDN"),
                parsedArguments.getProperty("bindPW"));

        if (JMXport == -1) {
          JMXport = 1689;
        }

        parsedArguments.setProperty("JMXport", Integer.toString(JMXport));
//        throw new IllegalArgumentException();
      }

    } catch (IllegalArgumentException e) {
      System.out.println(usage + "See \"MonitoringClient --help\" to get " +
              "more usage help");
      System.exit(0);
    } catch (ArrayIndexOutOfBoundsException e) {
      System.out.println(usage + "See \"MonitoringClient --help\" to get " +
              "more usage help");
      System.exit(0);
    }

    return parsedArguments;
  }

  /**
   * Retrieve the JMX port.
   *
   * @param host    Directory server hostname or IP address
   * @param port    Directory server port number
   * @param bindDN  DN to use to bind to the server
   * @param bindPW  Password to use to bind to the server
   * @return  The port number of the JMX port, -1 if an error occured
   */
  private static int getJMXport (String host, int port, String bindDN,
          String bindPW) {
    int JMXport = 0;

    try {
      Properties envLdap = System.getProperties();
      envLdap.put(Context.INITIAL_CONTEXT_FACTORY,
              "com.sun.jndi.ldap.LdapCtxFactory");
      envLdap.put(Context.PROVIDER_URL, "ldap://" + host + ":" + port + "/");
      envLdap.put(Context.SECURITY_AUTHENTICATION, "simple");
      envLdap.put(Context.SECURITY_PRINCIPAL, bindDN);
      envLdap.put(Context.SECURITY_CREDENTIALS, bindPW);

      DirContext ctx = new InitialDirContext(envLdap);

      SearchControls ctls = new SearchControls();
      ctls.setReturningAttributes(new String[] {"ds-cfg-listen-port"});
      ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);

      NamingEnumeration answer = ctx.search("cn=JMX Connection Handler," +
              "cn=Connection Handlers,cn=config", "(objectclass=*)", ctls);

      while (answer.hasMore()) {

        SearchResult sr = (SearchResult)answer.next();
        NamingEnumeration attribs = sr.getAttributes().getAll();

        while(attribs.hasMore()) {
          Attribute attr = (Attribute)attribs.next();
          JMXport = Integer.parseInt(attr.get(0).toString());
        }
      }

      ctx.close();

      } catch (CommunicationException e) {
        if (e.getCause() instanceof ConnectException) {
          System.out.println("Error of the JMX port retrieving: " +
                  "Wrong port number");
        } else {
          System.out.println("Error of the JMX port retrieving: " +
                  "Unknown host");
        }
        JMXport = -1;

      } catch (AuthenticationException e) {
        System.out.println("Error of the JMX port retrieving: " +
                "Invalid Credentials");
        JMXport = -1;

      } catch (ServiceUnavailableException e) {
        System.out.println("Error of the JMX port retrieving: " +
                "Service Unavailable");
        JMXport = -1;

      } catch (NamingException e) {
        System.out.println("Error of the JMX port retrieving: " +
                e.getLocalizedMessage());
        JMXport = -1;
      }

    return JMXport;
  }

  /**
   * Set the parameters of the producers.
   *
   * @param parsedArguments  The parsed arguments
   */
  private void init (Properties parsedArguments) {
    try {

      SAXParserFactory factory = SAXParserFactory.newInstance();
      factory.setValidating(true);
      SAXParser saxParser = factory.newSAXParser();
      saxParser.parse(new File(parsedArguments.getProperty("configFile")),
              new ConfigHandler(this, parsedArguments));
    } catch (ParserConfigurationException e) {
      System.out.println(e.getLocalizedMessage());
      System.exit(0);
    } catch (SAXException e) {
      System.out.println(e.getLocalizedMessage());
      System.exit(0);
    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
      System.exit(0);
    }

    if (producersConfig.containsKey("LDAP")) {
      Properties params = (Properties)producersConfig.get("LDAP");
      params.setProperty("host", parsedArguments.getProperty("host"));
      params.setProperty("port", parsedArguments.getProperty("LDAPport"));
      params.setProperty("bindDN", parsedArguments.getProperty("bindDN"));
      params.setProperty("bindPW", parsedArguments.getProperty("bindPW"));

      if (!params.containsKey("delay")) {
        params.setProperty("delay", "0");
      }
      if (!params.containsKey("charge")) {
        params.setProperty("charge", "0");
      }

    }

    if (producersConfig.containsKey("JMX")) {
      Properties params = (Properties)producersConfig.get("JMX");
      params.setProperty("host", parsedArguments.getProperty("host"));
      params.setProperty("port", parsedArguments.getProperty("JMXport"));
      params.setProperty("bindDN", parsedArguments.getProperty("bindDN"));
      params.setProperty("bindPW", parsedArguments.getProperty("bindPW"));

      if (!params.containsKey("delay")) {
        params.setProperty("delay", "0");
      }
      if (!params.containsKey("charge")) {
        params.setProperty("charge", "0");
      }
    }

    if (producersConfig.containsKey("JVM")) {
      Properties params = (Properties)producersConfig.get("JVM");
      params.setProperty("host", parsedArguments.getProperty("host"));
      params.setProperty("port", parsedArguments.getProperty("JVMport"));

      if (!params.containsKey("delay")) {
        params.setProperty("delay", "0");
      }
      if (!params.containsKey("charge")) {
        params.setProperty("charge", "0");
      }
    }

    if (producersConfig.containsKey("SNMP")) {
      Properties params = (Properties)producersConfig.get("SNMP");
      params.setProperty("host", parsedArguments.getProperty("host"));
      params.setProperty("port", parsedArguments.getProperty("SNMPport"));
      params.setProperty("LDAPport", parsedArguments.getProperty("LDAPport"));

      if (!params.containsKey("delay")) {
        params.setProperty("delay", "0");
      }
      if (!params.containsKey("charge")) {
        params.setProperty("charge", "0");
      }
    }

    new File(parsedArguments.getProperty("repository")).mkdirs();
    outputRepository = parsedArguments.getProperty("repository");

  }

}




