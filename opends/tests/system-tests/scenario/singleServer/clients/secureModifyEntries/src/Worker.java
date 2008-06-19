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
import javax.naming.directory.BasicAttributes;


public class Worker extends Thread {
  
  Server server;
  Client client;
  long myId = this.getId();
  
  /**
   ** Constructor for Worker  thread
   **/
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
  
  /**
   * Connect to the server
   * wait a notify from the main thread to startthe modify operations
   **/
  public void run() {
    
    String value="";
    String DNtoModify;
    String attrVal1;
    ArrayList<String> mailEXT_values= new ArrayList<String>();
    Random random_cpt= new Random();

    try {
      /* Define the list of values for mailEXT attribute */
      mailEXT_values.add("group1");
      mailEXT_values.add("group2");
      mailEXT_values.add("group3");
      
      DirContext ctx = null;
      
      // Set the properties 
     Hashtable envLdap = client.set_properties_LDAP();

      println("INFO","THREAD " + myId + " is waiting ");      
      // Waiting a notify from the main thread
      client.inc_thread_ready();
      
      
      /*
       * Start modify operations
       */
      String[] attrIds = { (String)client.attributeName };
      while (true) {
        
        // no sasl authentication
        // use a random DN to bind
        if ( client.authentication.equals("simple")) {
          
           String bindDN=(String) client.DNList.get(client.random.nextInt(client.DNList.size()));
           String bindPW="userpassword";
           envLdap.put(Context.SECURITY_PRINCIPAL, bindDN);
           envLdap.put(Context.SECURITY_CREDENTIALS, bindPW); 
           //println("INFO","THREAD " + myId + "BIND as " + bindDN);
        }
        // bind
        ctx = new InitialDirContext(envLdap);

        while (true) {
          try {
            
            //String value=String.valueOf(client.random.nextInt(10000));
            DNtoModify=(String) client.DNList.get(client.random.nextInt(client.DNList.size()));
            
            // If attributeName to modify is mailEXT, we get the new value from the list
            // group1, group2, group2
            // this list is the list used to define the dynamic groups
           
            if (client.attributeName.equals("mailEXT")) {
              attrVal1 = (String) mailEXT_values.get(random_cpt.nextInt(3));
            } else {
             
              String value_cpt=String.valueOf(random_cpt.nextInt(10000));
              long cur_date=System.currentTimeMillis();
              String scur_date = String.valueOf(cur_date);
              attrVal1 = "new description"+scur_date+"-"+value_cpt;
            }

            Attributes attrs = new BasicAttributes(attrIds[0], attrVal1, true);
            
            // if the Max_nb_mod is reached, counters are initialized
            // disconnect cnx
            // Wait the main to wake up
            if ( client.nb_mod_started_reached() == false) {
              
              if (client.operation.equals("modify")) {
                 ctx.modifyAttributes(DNtoModify, DirContext.REPLACE_ATTRIBUTE, attrs);
              } else  {
                ctx.modifyAttributes(DNtoModify, DirContext.ADD_ATTRIBUTE, attrs);
              }
            
              client.inc_mod_done();
            } else {
              ctx.close();
              client.thread_go_to_sleep();
              break;
            }
          } catch (Exception ex) {
            
            println("INFO","THREAD " + myId + " CATCH " + ex);

          }
          // update the total number of searchs
          client.inc_mod_done();
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
