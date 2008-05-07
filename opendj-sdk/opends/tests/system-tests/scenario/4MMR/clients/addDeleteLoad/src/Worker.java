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
import netscape.ldap.*;
import netscape.ldap.util.*;

import java.util.*;
import java.io.*;
import java.lang.Thread;


public class Worker extends Thread {
  
  Server server;
  Client client;
  
  
  /*****************************************************************/
  public Worker(Client client, Server server) {
    super();
    try {
      this.server = server;
      this.client = client;
      this.start();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  
  /*****************************************************************/
  public void run() {
    while (true) {
      client.inc_ops_started();
      LDAPEntry entry = generateAnEntry();
      this.addAnEntry(entry);
      this.deleteAnEntry(entry);
      client.inc_ops_done();
    }
  }
  
  
  /*****************************************************************/
  public LDAPEntry generateAnEntry() {
    try {
      // Build random strings
      Random rand = new Random();
      int n = 1000000;
      int i = rand.nextInt( n + 1 );
      ExtendedRandom extendedRandom = new ExtendedRandom();
      String sn = extendedRandom.nextLastName();
      String cn = extendedRandom.nextFirstName();
      String uid = sn.substring(0,1) + cn.substring(0,1) + "_" + i;
      
      // Create entry attributes set
      LDAPAttributeSet attrSet = new LDAPAttributeSet();
      attrSet.add(new LDAPAttribute("objectClass", "top"));
      attrSet.add(new LDAPAttribute("objectClass", "person"));
      attrSet.add(new LDAPAttribute("objectClass", "organizationalperson"));
      attrSet.add(new LDAPAttribute("objectClass", "inetorgperson"));
      attrSet.add(new LDAPAttribute("uid", uid));
      attrSet.add(new LDAPAttribute("sn", sn));
      attrSet.add(new LDAPAttribute("cn", cn));
      attrSet.add(new LDAPAttribute("givenName", sn + " " + cn));
      attrSet.add(new LDAPAttribute("mail", uid + "@sun.com"));
      
      // Create the entry
      LDAPEntry entry = new LDAPEntry( "uid=" + uid + "," + client.suffix,
                                        attrSet);
      
      // TBD a fixer return pas global a la function
      return entry;
    }
    catch( Exception e ) {
      println( "ERROR" , e.toString() );
    }
    return null;
  }
  
  
  /*****************************************************************/
  public void addAnEntry(LDAPEntry entry) {
    try {
      //************************************
      // LDAP CONNECTION
      LDAPConnection connection = new LDAPConnection();
      connection.connect( server.host, server.port);
      // bind if needed
      if ( client.bindDN == null ) {
        println ("INFO", "Binding as anonymous");
      }
      else {
        connection.bind(client.bindDN, client.bindPW);
      }
      
      
      //************************************
      // ADD LDAP ENTRY
      try {
        connection.add(entry);
        // println("INFO","Add entry " + entry.getDN());
      } catch (LDAPException e) {
        int errorCode = e.getLDAPResultCode();
        if ( errorCode == 50 ) {
          println ("ERROR", "Insufficent access, errcode : "+ errorCode + ")");
        }
        else if ( errorCode == 68 ) {
          println ("ERROR", "Entry already exists, errcode "+ errorCode + ")");
        }
        else {
          println ("ERROR", "LDAP Returns error code " + errorCode);
        }
      }
      
      
      //************************************
      // CLOSE LDAP CONNECTION
      connection.disconnect();
    }
    catch( LDAPException e ) {
      println( "ERROR" , e.toString() );
    }
  }
  
  
  /*****************************************************************/
  public void deleteAnEntry(LDAPEntry entry) {
    try {
      //************************************
      // LDAP CONNECTION
      LDAPConnection connection = new LDAPConnection();
      connection.connect(server.host, server.port);
      // bind if needed
      if ( client.bindDN == null ) {
        println ("INFO", "Binding as anonymous");
      }
      else {
        connection.bind(client.bindDN, client.bindPW);
      }
      
      
      //************************************
      // LDAP CONNECTION
      try {
        connection.delete(entry.getDN());
        // println("INFO","Delete entry " + entry.getDN());
      } catch (LDAPException e) {
        int errorCode = e.getLDAPResultCode();
        if ( errorCode == 50 ) {
          println ("ERROR", "Insufficent access, errcode : "+ errorCode + ")");
        }
        else if ( errorCode == 68 ) {
          println ("ERROR", "Entry already exists, errcode "+ errorCode + ")");
        }
        else {
          println ("ERROR", "LDAP Returns error code " + errorCode);
        }
      }
      
      
      //************************************
      // CLOSE LDAP CONNECTION
      connection.disconnect();
    }
    catch( LDAPException e ) {
      println( "ERROR" , e.toString() );
    }
  }
  
  
  /*****************************************************************/
  private String getDate() {
    // Initialize the today's date string
    String DATE_FORMAT = "yyyy/MM/dd:HH:mm:ss";
    java.text.SimpleDateFormat sdf = 
        new java.text.SimpleDateFormat(DATE_FORMAT);
    Calendar c1 = Calendar.getInstance(); // today
    return("[" + sdf.format(c1.getTime()) + "]");
  }
  
  
  /*****************************************************************/
  private void println(String level, String msg) {
      System.out.println (getDate() + " - " + level + ": (" + server + ") "
                          + msg );
  }
	
}
