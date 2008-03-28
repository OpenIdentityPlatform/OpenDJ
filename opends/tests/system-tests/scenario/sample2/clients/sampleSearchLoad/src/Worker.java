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
    
    public Worker (Client client, Server server) {
        super();
        try {
            this.server = server;
            this.client = client;
            this.start();	
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    


    public void run () {
        
        String value="";
        String[] attributes =  new String []{client.attr};
        LDAPConnection connection = new LDAPConnection();
        while (true) {
            try {

                connection.connect( server.host, server.port);
                // bind if needed
                if ( client.bindDN != null ) {
                    // println ("INFO", "Binding as \"" + client.bindDN + "\"");
                    connection.bind(client.bindDN, client.bindPW);
                }
                /* try {
                    synchronized (client.lock) {
                        client.lock.wait();
                    }
                } catch ( InterruptedException ie ) {
                    ie.printStackTrace();
                }*/
            while (true) {
                    //try {
                        client.inc_srchs_started();
                        value=(String) client.ValueList.get(client.random.nextInt(client.ValueList.size()));
                        //println ("INFO", "Modifying \""+myDN+"\" (replace "+ attribute + ":" + value + ") on master " + master.toString());
                        //connection.search(client.suffix, LDAPv3.SCOPE_SUB, new String (client.attr + "=" + value ), new String []{}, false );
                        //connection.search(client.suffix, LDAPv3.SCOPE_SUB, new String (client.attr + "=" + value ), new String []{client.attr}, false );
                        LDAPSearchResults results=connection.search(client.suffix, LDAPv3.SCOPE_SUB, new String (client.attr + "=" + value ), attributes, false );
                        while ( results.hasMoreElements() ) {
                            LDAPEntry entry=results.next();
                        }
                        client.inc_srchs_done();
                        results=null;
                        
                    //} catch (LDAPException e) {
                    //        println("ERROR", e.toString());
                            // Client.wait_after_error();
                    /*        try {
                                synchronized (client.lock) {
                                    client.lock.wait();
                                }
                            } catch ( InterruptedException ie ) {
                                ie.printStackTrace();
                            }  */
                    //}

                }
            }
            catch( LDAPException e ) {
                println( "ERROR" , e.toString() );
                // make sure we are disconnected
                try {
                    connection.disconnect();
                }
                catch( LDAPException e2 ) {
                    println( "ERROR" , e2.toString() );
                }
//                System.exit(1);
            }
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
        System.out.println (getDate() + " - " + level + ": (" + server + ") " + msg );
   }
	
}
