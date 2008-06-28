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

import java.net.ConnectException;

import java.util.Date;
import java.util.Properties;
import java.util.Vector;

import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.ServiceUnavailableException;

import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

/**
 * Producer who monitor an OpenDS server with LDAP.
 */
public class LDAPMonitor extends Thread {

  /**
   * Main class of the client.
   */
  private MonitoringClient client;

  /**
   * The properties of the producer.
   */
  private Properties params;

  /**
   * Contructs a LDAPMonitor thread whith the specified values.
   *
   * @param client  The main class of the client.
   * @param params  The parameters of the thread.
   */
  public LDAPMonitor(MonitoringClient client, Properties params) {
    this.client = client;
    this.params = params;

    this.setName(params.getProperty("name"));
  }

  /**
   * Connect to the server, get the attributes to monitor,
   * and wait a notify from the main thread.
   */
  @Override
  public void run() {

    Date date = new Date();

    Vector<Data> attributesToMonitor =
            client.getDatasBuffer().getAttributesToMonitor(
            params.getProperty("name"));

    // Initialise the LDAP environnement.
    Properties envLdap = System.getProperties();
    envLdap.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    envLdap.put(Context.PROVIDER_URL, "ldap://" + params.getProperty("host") +
            ":" + params.getProperty("port")+ "/");
    envLdap.put(Context.SECURITY_AUTHENTICATION, "simple");
    envLdap.put(Context.SECURITY_PRINCIPAL, params.getProperty("bindDN"));
    envLdap.put(Context.SECURITY_CREDENTIALS, params.getProperty("bindPW"));

    while(true) {
      try {

        // Allow to desynchronize the producers.
        try {
          sleep(Integer.parseInt(params.getProperty("delay")) *
                  client.getTimeUnit());
        } catch (InterruptedException e) {
          System.out.println(e.getLocalizedMessage());
        }

        // Connect to the server
        DirContext ctx = new InitialDirContext(envLdap);

        // Simulate an important charge for the server
        try {
          sleep(Integer.parseInt(params.getProperty("charge")) *
                  client.getTimeUnit());
        } catch (InterruptedException e) {
          System.out.println(e.getLocalizedMessage());
        }

        // Retrieve the attributes
        for (Data d : attributesToMonitor) {
          SearchControls ctls = new SearchControls();
          ctls.setReturningAttributes(new String[] {d.getAttribute()});
          if (!d.getParameters().containsKey("scope")) {
            ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
          } else if (d.getParameters().getProperty("scope").equals("base")) {
            ctls.setSearchScope(SearchControls.OBJECT_SCOPE);
          } else if (d.getParameters().getProperty("scope").equals("onelevel")){
            ctls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
          } else if (d.getParameters().getProperty("scope").equals("sub")) {
            ctls.setSearchScope(SearchControls.SUBTREE_SCOPE);
          }

          try {
            NamingEnumeration answer;
            if (d.getParameters().containsKey("filter")) {
              answer = ctx.search(
                      d.getParameters().getProperty("baseDN"),
                      d.getParameters().getProperty("filter"), ctls);
            } else {
              answer = ctx.search(
                      d.getParameters().getProperty("baseDN"),
                      "(objectclass=*)", ctls);
            }

            while (answer.hasMore()) {

              SearchResult sr = (SearchResult)answer.next();
              NamingEnumeration attribs = sr.getAttributes().getAll();

              while(attribs.hasMore()) {
                Attribute attr = (Attribute)attribs.next();
                client.getDatasBuffer().setData(d, attr.get(0).toString(),
                        (int)(System.currentTimeMillis() - date.getTime()));

  /*              System.out.println(new Date() + " " +
                        d.getParameters().getProperty("protocol") + " " +
                        d.getParameters().getProperty("baseDN") + " " +
                        d.getParameters().getProperty("filter") + " " +
                        d.getAttribute() + " " +
                        attr.get(0).toString() + " " +
                        (int)(System.currentTimeMillis() - date.getTime()) +
                        " ms");*/
              }

            }
          } catch (NameNotFoundException e) {
            client.getDatasBuffer().dataError(d, "The entry " +
                    d.getParameters().getProperty("baseDN") +" specified as " +
                    "the search base does not exist in the Directory Server");
          }
        }

        // Close the LDAP connection
        ctx.close();

        // Processing of the errors
        client.getDatasBuffer().verifyDatas(params.getProperty("name"));

      } catch (CommunicationException e) {
        if (e.getCause() instanceof ConnectException) {
          client.getDatasBuffer().protocolError(params.getProperty("name"),
                  "Wrong port number");
        } else {
          client.getDatasBuffer().protocolError(params.getProperty("name"),
                  "Unknown host");
        }

      } catch (AuthenticationException e) {
        client.getDatasBuffer().protocolError(params.getProperty("name"),
                "Invalid Credentials");

      } catch (ServiceUnavailableException e) {
        client.getDatasBuffer().protocolError(params.getProperty("name"),
                "Service Unavailable");

      } catch (NamingException e) {
        client.getDatasBuffer().protocolError(params.getProperty("name"),
                e.getLocalizedMessage());
      }

      // Wait for the next run
      try {
        synchronized(MonitoringClient.lock) {
          MonitoringClient.lock.wait();
        }
      } catch (InterruptedException e) {
        System.out.println(e.getLocalizedMessage());
      }

      // Update the date
      date.setTime(System.currentTimeMillis());

    }
  }

}
