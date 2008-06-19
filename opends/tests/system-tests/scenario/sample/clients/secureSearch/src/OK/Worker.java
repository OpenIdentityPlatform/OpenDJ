package system-tests.scenario.single.clients.secureSearch.src.OK-tests.scenario.single.clients.searchLoad.src.OK;
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
import javax.naming.*;

import javax.naming.directory.SearchControls;
import	javax.naming.directory.DirContext;
import	 javax.naming.Context;
import	 javax.naming.directory.InitialDirContext;


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
	
	String provider = "ldap://"+server.host+":"+server.port+"/";
	println ("INFO","provider = " + provider);
	try {	
	       
	    Properties envLdap = System.getProperties();
	    envLdap.put("java.naming.factory.initial",
			"com.sun.jndi.ldap.LdapCtxFactory");
	    
	    envLdap.put(Context.PROVIDER_URL, provider);	
	    envLdap.put(Context.SECURITY_AUTHENTICATION, "EXTERNAL");
	    envLdap.put(Context.SECURITY_PROTOCOL, "ssl");
	    
	    while (true) {

		DirContext ctx = null;	
		ctx = new InitialDirContext(envLdap);
	
		
		String filter = "(objectclass=*)";
		
		SearchControls constraints = new SearchControls();

		Exception exc = null;
		int count = 0;
	
		    
		//    client.inc_srchs_started();
		//		    value=(String) client.ValueList.get(client.random.nextInt(client.ValueList.size()));
		    NamingEnumeration results = ctx.search(client.suffix, filter, constraints);
		    
		    
		    try {
			while (results != null && results.hasMore()) {
			    results.next();
			    println ("INFO " ," res " + results.toString());
			    count++;
			}			

		    } catch (Exception ex) {
			exc = ex;
		    }
		    if ( exc != null ) {
			throw exc;
		    }
		    //  client.inc_srchs_done();

		    ctx.close();
	    }
	} catch (Exception e) {
	    
	    println ("INFO", "Failed: expected error code 3 ");

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
        System.out.println (getDate() + " - " + level + ": (" + server + ") " + msg );
   }
	
}
