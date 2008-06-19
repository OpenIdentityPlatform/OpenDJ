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
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchResult;
import javax.naming.directory.Attributes;


public class Client {
    
    
    static int NB_MAX_mod=100;
    static int nb_mod_started=0;
    static int nb_mod_done=0;
    static int total_nb_mod=0;
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
    static String operation;
    static String attributeName;
    static String time= new String ("0 sec.");
    static long timeTostopTest=0;
    static long maxDuration=0;
    static long duration=0;
    static long startup=0;
    static ArrayList<String> DNList;
    static long delayCnx=1000;
    static long delaySec=1;
  
    
    public Client()
    {
            
        random= new Random();
        DNList=new ArrayList<String>();

        try {

            /*
             * bind as directory manager to get the full list of DN
             * create a list of DN
             */
            Hashtable envLdap = set_properties_LDAP_simpleBind();
            
	    DirContext ctx = null;
            ctx = new InitialDirContext(envLdap);

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
       	} catch (Exception e) {

	    println ("INFO", "Failed to establish connection ");
	    e.printStackTrace();
	    System.exit(1);
        }
        
        try {
        

	    // execute the threads
            for (int i=0; i < nb_threads; i++ ) {
                Worker w = new Worker(this, server);
            }
            println ("INFO", nb_threads + " threads connected to server " + server );
            //println ("INFO", "Will search using filter \"(" + attr + " = <value> )\" (MAX =" + NB_MAX_mod + ")" );

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
            // initialize startup
            long t1=System.currentTimeMillis();
            
            // work until Max duration is reached
            while (true) {

                long new_t1=System.currentTimeMillis();

		// end of the  system test. Exit
                if ( ( timeTostopTest != 0 ) && ( new_t1 > timeTostopTest ) ) { 
                  
                    // inform all the threads it's the end
                    synchronized (this) {
			nb_mod_started=NB_MAX_mod;
		    }
                    break; 
                }
                
		// status every delayCnx
                if ( (new_t1 - t1) >= delayCnx) {

                    println("INFO",  (nb_mod_done/delaySec) + "  mod/sec.");

		    // inform all the threads the max nb searchs has been reached
		    synchronized (this) {
			nb_mod_started=NB_MAX_mod;
		    }

		    // Wait all the threads to close their cnx and sleep
		    try {
			total_nb_mod+=nb_mod_done;
			while ( nb_thread_ready() < nb_threads) {
			    // wait
			}
			// All the threads are ready, wake up all the threads
			synchronized(this) {
			    nb_thread_ready=0;
			    nb_mod_started=0;
			    nb_mod_done=0;

			    notifyAll();
			}
		    } 
		    catch ( Exception e1 ) {
			System.out.println ("E1");
			e1.printStackTrace();
		    }

                    if ( (seconds++) >= 9 ) {
                        duration=((new_t1-startup)/1000);
                        println("INFO",  "Avg rate: " + (total_nb_mod/duration) + " mod/sec. after " + getTime(duration));
                        seconds=0;
                    }
                    t1=new_t1;
                }
            }
            println ("INFO", "End of the client");
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
	println ("INFO" , "suffix " + suffix);
	
	// nb_threads
	String snb_threads = System.getProperty("nb_threads"); 
	nb_threads = Integer.parseInt(snb_threads);
	
	println ("INFO" , "nb_threads " + snb_threads);
	
	// test duration
	String sMaxDuration = System.getProperty("maxDuration"); 
	maxDuration = Long.parseLong(sMaxDuration);
	println ("INFO" , "maxDuration " + maxDuration);
        
	// credential for simple bind
	bindDN = System.getProperty("bindDN"); 
	bindPW = System.getProperty("bindPW"); 
	println ("INFO" , "bindDN " + bindDN);
	
	// Max number of searchs
	String sNB_MAX_mod = System.getProperty("NB_MAX_mod"); 
	NB_MAX_mod = Integer.parseInt(sNB_MAX_mod);
	println ("INFO" , "sNB_MAX_mod " + sNB_MAX_mod);
	
	// attribute to modify or add 
	attributeName = System.getProperty("attributeName");
	println ("INFO" , "attributeName " + attributeName);        

        // operation to perform: modify or add 
	operation = System.getProperty("operation");
	println ("INFO" , "operation " + operation);        
	// hostname
	hostname = System.getProperty("hostname");

	// protocol : SSL or TLS
	protocol = System.getProperty("protocol");
	println ("INFO" , "protocol " + protocol);        
        
        // authentication : EXTERNAL or simple
	authentication = System.getProperty("authentication");
	println ("INFO" , "authentication " + authentication);    
   
        // delay Sec  before closing conx
        String sdelaySec = System.getProperty("delaySec"); 
	delaySec =  Long.parseLong(sdelaySec);
        delayCnx = delaySec * 1000;
	println ("INFO" , "delayCnx " + delayCnx);
        
        if ( maxDuration != 0 ) {
          maxDuration= maxDuration * 1000;
        }
        timeTostopTest=( startup + maxDuration);
        println("INFO", "the test will finish at " + timeTostopTest );

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
 * Get Date
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

/* =========================================
 * return true if the number of NB_MAX_mod has been reached
 * else, increase nb_mod_started
 * ======================================= */

   public boolean nb_mod_started_reached() {

       synchronized (this) {
	   if ( nb_mod_started>=NB_MAX_mod ) {
	       return true;

	   } else {
	       nb_mod_started++;
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
 * increase the number of mod started
 * ======================================= */
   public void inc_mod_started() {

       synchronized (this) {
	   if ( nb_mod_started>=NB_MAX_mod ) {
	       try {
		   this.wait();
	       } catch ( Exception e ) {
		   e.printStackTrace();
	       }
	       
	   } else {
	       nb_mod_started++;
	   }
       }
    }       

   

/* =========================================
 * increase the number of mod done
 * ======================================= */
   public void inc_mod_done() {
      synchronized (this) {
             try {
               nb_mod_done++;
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
      
      envLdap.put(Context.SECURITY_AUTHENTICATION, authentication);
      
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
 * Print
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
}

