// CDDL HEADER START
//
// The contents of this file are subject to the terms of the
// Common Development and Distribution License, Version 1.0 only
// (the "License").  You may not use this file except in compliance
// with the License.
//
// You can obtain a copy of the license at
// trunk/opends/resource/legal-notices/OpenDS.LICENSE
// or https://OpenDS.dev.java.net/OpenDS.LICENSE.
// See the License for the specific language governing permissions
// and limitations under the License.
//
// When distributing Covered Code, include this CDDL HEADER in each
// file and include the License file at
// trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
// add the following below this CDDL HEADER, with the fields enclosed
// information:
//      Portions Copyright [yyyy] [name of copyright owner]
//
// CDDL HEADER END
//
//
//      Copyright 2008 Sun Microsystems, Inc.

import java.util.*;
import java.io.*;
import java.lang.Thread;
import javax.naming.*;

import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.directory.Attributes;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.Context;
import javax.naming.directory.InitialDirContext;


public class Worker extends Thread {
  
  Server server;
  Client client;
  long myId = this.getId();
  
  public Worker(Client client2, Server server2) {
    
    super();
    try {
      
      server = server2;
      client = client2;
      start();
      
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  
  public void run() {
    
    String value="";

    try {
      
      DirContext ctx = null;
      
      // Set the properties 
     Hashtable envLdap = client.set_properties_LDAP();
      
      // the thread is waiting the main to wake up
      client.inc_thread_ready();
      
      println("INFO","THREAD " + myId + " is wake up for test");
      
      while (true) {
        
        // no sasl authentication
        // use a random DN to bind
        if ( client.authentication.equals("simple")) {
          
          String bindDN=(String) client.DNList.get(client.random.nextInt(client.DNList.size()));
          String bindPW="userpassword";
          
          envLdap.put(Context.SECURITY_PRINCIPAL, bindDN);
          envLdap.put(Context.SECURITY_CREDENTIALS, bindPW);
         
        }
        // bind
        ctx = new InitialDirContext(envLdap);
        
        
        //String filter = "(objectclass=*)";
        SearchControls constraints = new SearchControls();
        String[] returnattrs = {client.attributeName};
        constraints.setReturningAttributes(returnattrs);
        constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);


        while (true) {
          try {
            String searchDN=(String) client.suffix;
            String filter="uid="+(String) client.uidList.get(client.random.nextInt(client.uidList.size()));

            // if the Max_nb_search is reached, counters are initialized
            // disconnect cnx
            // Wait the main to wake up
           
            if ( client.nb_srchs_started_reached() == false) {
              
              // Search entries
              NamingEnumeration results = ctx.search(searchDN, filter, constraints);
	      while (results != null && results.hasMore()) {
                   SearchResult res = (SearchResult) results.next();
                   Attributes uidAttrs = res.getAttributes();
		}
              results.close();
              
              client.inc_srchs_done();
            } else {
              ctx.close();
              client.thread_go_to_sleep();
              break;
            }
          } catch (Exception ex) {

            println("INFO","THREAD " + myId + " CATCH " + ex);

          }

          // update the total number of searchs
          client.inc_srchs_done();
        }

      }
    } catch (Exception e) {
      println("INFO", "Failed: expected error code 3 ");

      e.printStackTrace();
      System.exit(1);
    }
  }

  private String getDate() {
    
    // Initialize the today's date string
    String DATE_FORMAT = "yyyy/MM/dd:HH:mm:ss";
    java.text.SimpleDateFormat sdf =
     new java.text.SimpleDateFormat(DATE_FORMAT);
    Calendar c1 = Calendar.getInstance(); // today
    return("[" + sdf.format(c1.getTime()) + "]");
  }
  
  private void println(String level, String msg) {
    System.out.println(getDate() + " - " + level + ": (" + server + ") " + msg );
  }
  
}
