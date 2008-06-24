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
//import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
//import javax.naming.directory.DirContext;
import javax.naming.directory.SearchResult;
import javax.naming.directory.Attributes;
import javax.naming.ldap.*;
import javax.naming.ldap.StartTlsResponse;
import javax.naming.ldap.StartTlsRequest;
import javax.net.ssl.*;

public class Client {
    
    static int NB_MAX_srchs=100;
    static int nb_srchs_started=0;
    static int nb_srchs_done=0;
    static int total_nb_srchs=0;
    static int nb_threads=3;
    static int nb_thread_ready=0;

    static Random random;   
    static String hostname ;
    static int portnumber;
    static String bindDN;
    static String bindPW;
    static String suffix;
    static Server server;
    static String authentication;
    static String protocol;   
    static String attributeName;
    static String time= new String ("0 sec.");;
    static long duration=0;
    static long maxDuration=0;
    static long startup=0;
    static long  timeTostopTest=0;
    static ArrayList<String> DNList;
    static ArrayList<String> uidList;
    static long delayCnx=1000;
    static long delaySec=1;    
    static long delayPrint=60000;
    
    public Client()
    {
        random= new Random();
        DNList=new ArrayList<String>();
	uidList=new ArrayList<String>();
        
        try {

            Hashtable envLdap = set_properties_LDAP_simpleBind();
            
	    LdapContext ctx = null;
            ctx = new InitialLdapContext(envLdap,null);
            
	    // Search options
	    String filter = "(objectclass=inetorgperson)";
            String[] attrs = { "uid"};

	    SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(attrs);

	    NamingEnumeration results = ctx.search(suffix, filter, constraints);
	    Exception exc = null;
	    int count = 0;

	    try {
		while (results != null && results.hasMore()) {
                    SearchResult res = (SearchResult) results.next();
                    DNList.add (res.getNameInNamespace()); 

                    Attributes uidAttrs = res.getAttributes();
                    uidList.add ((String) uidAttrs.get("uid").get());

		    count++;
		}
                
                DNList.trimToSize();
                if ( DNList.size() == 0) {
                  println("ERROR", "No entry found in \"" + suffix + "\"");
                  System.exit(1);
                }
	    } catch (Exception ex) {
		exc = ex;
	    }
	    if ( exc != null ) {
		throw exc;
            }
            ctx.close();
       	} catch (Exception e) {
        
	    println ("INFO", "Failed: expected error code 3 ");
	    e.printStackTrace();
	    System.exit(1);
        }
       
        
        try {
           
	    // execute the threads
            for (int i=0; i < nb_threads; i++ ) {
                Worker w = new Worker(this, server);
            }
            println ("INFO", nb_threads + " threads connected to server " + server );
        
	    // Wait until all the threads have initialized their context 
	    // and are ready to bind
            try {
                while ( nb_thread_ready() < nb_threads) {
		    // wait
		}
		// All the threads are ready, wake up all the threads
		synchronized(this) {
		    nb_thread_ready=0;
                    notifyAll();
		}
            }
            catch ( Exception e1 ) {
		System.out.println ("E1");
		e1.printStackTrace();
            }

            int seconds=0;
            long t1=System.currentTimeMillis();
            long print_t1=System.currentTimeMillis();
              
	    // work until Max duration is reached
            while (true) {

                long new_t1=System.currentTimeMillis();
                long print_t2=System.currentTimeMillis(); 
		// end of the  system test. Exit
                if ( ( timeTostopTest != 0 ) && ( new_t1 > timeTostopTest ) ) { 
                  
                    // inform all the threads it's the end
                    synchronized (this) {
			nb_srchs_started=NB_MAX_srchs;
		    }
                  break; 
                }

                   // status every delayPrint
                 if ( (print_t2 - print_t1) >= delayPrint ) {
                        duration=((print_t2-print_t1)/1000);
                        println("INFO",  "Rate: " + (total_nb_srchs/duration) + " srchs/sec");
                        print_t1=System.currentTimeMillis();
                        try {
                          synchronized(this) {
                            total_nb_srchs=0;
                          }
                        } catch ( Exception e2 ) {
                          System.out.println("E2");
                          e2.printStackTrace();
                        }
                 }  
                
		// status every second
                if ( (new_t1 - t1) >= delayCnx ) {
                 
                 //   println("INFO",  (nb_srchs_done/delaySec) + "  srch/sec.");                 
              
		    // inform all the threads the max nb searchs has been reached
		    synchronized (this) {
			nb_srchs_started=NB_MAX_srchs;
		    }

		    // Wait all the threads to close their cnx and sleep
		    try {
			total_nb_srchs+=nb_srchs_done;
			while ( nb_thread_ready() < nb_threads) {
			    // wait
			}
			// All the threads are ready, wake up all the threads
			synchronized(this) {
			    nb_thread_ready=0;
			    nb_srchs_started=0;
			    nb_srchs_done=0;

			    notifyAll();
			}
		    }
		    catch ( Exception e1 ) {
			System.out.println ("E1");
			e1.printStackTrace();
		    }

                    t1=new_t1;
                }
            }
            System.exit(0);
        }
        catch( Exception e ) {

            e.printStackTrace();
            System.exit(1);
        }
        
        
    }
    
 
    
    
/* =========================================
 * MAIN
 * ======================================= */  
    
    public static void main( String[] args )
    {
      
        startup=System.currentTimeMillis();
      
	// ===========================================
        // Get the arguments specified for each option.
	//

	// Ldap port
	String sport = System.getProperty("port"); 
	portnumber = Integer.parseInt(sport);


	// BaseDN
	suffix = System.getProperty("suffix"); 
	println ("INFO" , "CONFIG suffix " + suffix);
	
	// nb_threads
	String snb_threads = System.getProperty("nb_threads"); 
	nb_threads = Integer.parseInt(snb_threads);
	
	println ("INFO" , "CONFIG nb_threads " + snb_threads);
	
	// test duration
	String sMaxDuration = System.getProperty("maxDuration"); 
	maxDuration = Long.parseLong(sMaxDuration);
	println ("INFO" , "CONFIG maxDuration " + maxDuration);
        
	// credential for simple bind
	bindDN = System.getProperty("bindDN"); 
	bindPW = System.getProperty("bindPW"); 
	println ("INFO" , "CONFIG bindDN " + bindDN);
	
	// attribute to search 
	attributeName = System.getProperty("attributeName");
	println ("INFO" , "CONFIG attributeName " + attributeName);        
        
	// Max number of searchs
	String sNB_MAX_srchs = System.getProperty("NB_MAX_srchs"); 
	NB_MAX_srchs = Integer.parseInt(sNB_MAX_srchs);
	println ("INFO" , "CONFIG sNB_MAX_srchs " + sNB_MAX_srchs);
	
	// hostname
	hostname = System.getProperty("hostname");


	// protocol : SSL or TLS
	protocol = System.getProperty("protocol");
	println ("INFO" , "CONFIG protocol " + protocol);        
        
        // authentication : EXTERNAL or simple
	authentication = System.getProperty("authentication");
	println ("INFO" , "CONFIG authentication " + authentication);    

        // delay Sec  before closing conx
        String sdelaySec = System.getProperty("delaySec"); 
	delaySec =  Long.parseLong(sdelaySec);
        delayCnx = delaySec * 1000;
	println ("INFO" , "CONFIG delayCnx " + delayCnx);
        

        if ( maxDuration != 0 ) {
          maxDuration= maxDuration * 1000;
        }
        timeTostopTest=( startup + maxDuration);
        println("INFO", "END of the test : " + timeTostopTest );
        
        
	// ===========================================
	// Initialize the Server
        server=new Server (hostname,portnumber);

	System.out.println ("DEBUG declare server " + portnumber + " " + hostname);

        Runtime.getRuntime().addShutdownHook(new Thread() {
		
		public void run() {
                }
	    });        

        Client c = new Client();
    }


/* =========================================
 * return true if the number of NB_MAX_srchs has been reached
 * else, increase nb_srchs_started
 * ======================================= */

   public boolean nb_srchs_started_reached() {

       synchronized (this) {
	   if ( nb_srchs_started>=NB_MAX_srchs ) {
	       return true;

	   } else {
	       nb_srchs_started++;
	       return false;
	   }
       }
    }       


/* =========================================
 * thread is waiting for a notify from the main thread
 * ======================================= */    
   public void thread_go_to_sleep() {

       synchronized (this) {
	   try {
               nb_thread_ready++;

	       this.wait();
	   } catch ( Exception e ) {
	       e.printStackTrace();
	   }
       }
    }       


/* =========================================
 * increase the number of srchs started
 * ======================================= */    
   public void inc_srchs_started() {

       synchronized (this) {
	   if ( nb_srchs_started>=NB_MAX_srchs ) {
	       try {
		   this.wait();
	       } catch ( Exception e ) {
		   e.printStackTrace();
	       }
	       
	   } else {
	       nb_srchs_started++;
	   }
       }
    }       

/* =========================================
 * increase the number of srchs done
 * ======================================= */ 
   public void inc_srchs_done() {
      synchronized (this) {
             try {
               nb_srchs_done++;
	   } catch ( Exception e ) {
	       e.printStackTrace();
	   }
       }
   }

/* =========================================
 * Configure the Properties depending of the selected authentication and protocol
 * authentication : EXTERNAL or simple
 * protocol ssl, tls or clear
 * ======================================= */    

   public static Hashtable set_properties_LDAP() {
     
     String provider ;
     
     Hashtable envLdap = new Hashtable();
     envLdap.put("java.naming.factory.initial",
      "com.sun.jndi.ldap.LdapCtxFactory");
     
     if ( ! protocol.equals("starttls")) {
       envLdap.put(Context.SECURITY_AUTHENTICATION, authentication);
     }
     
     if ( protocol.equals("ssl")) {
       provider = "ldaps://"+server.host+":"+server.port+"/";
       envLdap.put(Context.SECURITY_PROTOCOL, protocol);
       
     } else {
        provider = "ldap://"+server.host+":"+server.port+"/";
     }
    
     envLdap.put(Context.PROVIDER_URL, provider);
     return envLdap;
   }

   
/* =========================================
 * Configure the Properties for a simple Bind
 * bind as directory manager
 * use the selected protocol : ssl, tls or clear
 * ======================================= */
   public static Hashtable set_properties_LDAP_simpleBind() {
     
     String provider ;
     
      Hashtable envLdap = new Hashtable();
     envLdap.put("java.naming.factory.initial",
      "com.sun.jndi.ldap.LdapCtxFactory");
     
     envLdap.put(Context.SECURITY_AUTHENTICATION, "simple");
     
     envLdap.put(Context.SECURITY_PRINCIPAL, bindDN);
     envLdap.put(Context.SECURITY_CREDENTIALS, bindPW);
     
     if ( protocol.equals("ssl")) {
       provider = "ldaps://"+server.host+":"+server.port+"/";
       envLdap.put(Context.SECURITY_PROTOCOL, protocol);
       
     } else {
        provider = "ldap://"+server.host+":"+server.port+"/";
     }
    
     envLdap.put(Context.PROVIDER_URL, provider);
     return envLdap;
   }
   
/* =========================================
 * Get Date
 * ======================================= */
    public static String getDate() {
    
        // Initialize the today's date string
        String DATE_FORMAT = "yyyy/MM/dd:HH:mm:ss";
        java.text.SimpleDateFormat sdf = 
            new java.text.SimpleDateFormat(DATE_FORMAT);
        Calendar c1 = Calendar.getInstance(); // today
        return("[" + sdf.format(c1.getTime()) + "]");
   }
   
/* =========================================
 *  print 
 * ======================================= */    
   public static void println(String level, String msg) {
        System.out.println (getDate() + " - " + level + ": " + msg );
   }

/* =========================================
 * increase the number of threads ready
 * ======================================= */
    public void inc_thread_ready() {
	synchronized (this) {
            nb_thread_ready++;

            try {
            this.wait();
            } catch (Exception e) {
		e.printStackTrace();
            }
	}

    }
/* =========================================
 * return the number of threads ready
 * ======================================= */    
    public static int nb_thread_ready() {
      
      return nb_thread_ready;
      
    }

/* =========================================
 * getTime
 * ======================================= */    
  public static String getTime(long d) {
        String time=new String (d + " sec.");
        if ( d > 10000 ) {
            time=new String ((d/3600) + " hours");
        } else if ( d > 300 ) {
            time=new String ((d/60) + " min.");
        }
        return time;
    }
}
