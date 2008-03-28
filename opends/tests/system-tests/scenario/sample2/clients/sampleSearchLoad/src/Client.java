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



public class Client {
    
    
//    static String dn="cn=Directory Manager";
//    static String password="secret12";
    static int NB_MAX_srchs=100;
    static Object lock;
    static int nb_srchs_started=0;
    static int nb_srchs_done=0;
    static int total_nb_srchs=0;
    static int nb_threads=3;
    public ArrayList<String> ValueList;
    // String dn="cn=admin,dc=com";
//    String dn="cn=Directory Manager";
//    String password="secret12";
    Random random;   
    static String attr="cn";
    public String attribute;
    
    static String hostname ;
    static int portnumber;
    static String bindDN;
    static String bindPW;
    static String suffix;
    static Server server;
    static String time= new String ("0 sec.");;
    static long duration=0;
    static long maxDuration=0;
    static long startup=System.currentTimeMillis();
    
    public Client()
    {
            
        attribute=attr;
        random=new Random();
        lock = new Object();
        
        ValueList=new ArrayList<String>();
        // println("INFO", "Getting DN from file " + DNFileName);
        try {
            println ("INFO", "Get the DNs on suffix \"" + suffix + "\" from server " + server );
            LDAPConnection  c = new LDAPConnection();
            // no bind
            c.connect( server.host, server.port);
            println ("INFO", "Connected to server " + server );
            
            // bind if needed
            if ( bindDN != null ) {
                println ("INFO", "Binding as \"" + bindDN + "\"");
                c.bind(bindDN, bindPW);
            }
            else {
                println ("INFO", "Anonymous mode (no bind)");
            }
            
            
            // no limit for nb of entries returned by a search
            Integer sizeLimit = new Integer( 0 );
            c.setOption( LDAPv3.SIZELIMIT, sizeLimit );
            LDAPSearchResults results = c.search( suffix, LDAPv3.SCOPE_SUB, "(objectclass=*)" , new String[] {attr} , false );
            while ( results.hasMoreElements() ) {
                Object o=null;
                try {
                    o=results.nextElement();
                    LDAPEntry entry=(LDAPEntry) o;
                    //  hack for oracle
                    if ( entry == null ) {
                        break;
                    }
                    // println("DEBUG", "DN="+entry.getDN());
                    LDAPAttribute a = entry.getAttribute(attr);
                    if ( a == null ) {
                        continue;
                    }
                    for ( String value: a.getStringValueArray()) {
                        ValueList.add( value );
                    }
                }
                catch (java.lang.ClassCastException e) {
                    e.printStackTrace();
                    println ("ERROR", o.toString() );
                }
            }
            ValueList.trimToSize();
            if ( ValueList.size() == 0) {
                println ("ERROR", "No values found for attribute  " + attr + " in suffix \"" + suffix + "\"");
                System.exit(1);
            }
            println("INFO", "Found " + ValueList.size() + " values for attribute " + attr + " in \"" + suffix + "\" on " + server.toString() );
            c.disconnect();
        } catch (LDAPException e) {
            int errorCode = e.getLDAPResultCode(); 
            if ( errorCode == 32) {
                println ("ERROR", "Base dn \"" + suffix + "\" not found");
            }
            else {
                e.printStackTrace();
            }
            System.exit(1);
        }
        /* try {
            File DNFile= new File(DNFileName);
            LineNumberReader in=new LineNumberReader (new FileReader(DNFile) );
            while ( in.ready() ) {
                ValueList.add(in.readLine());
            }
        } catch (IOException e) {
            println ("ERROR", e.toString());
            System.exit(1);
        }*/
        // ValueList.trimToSize();
        //println("INFO", "Found " + ValueList.size() + " DNs");
        
        
        try {
            // reinitialize startup
            startup=System.currentTimeMillis();
            long t1=System.currentTimeMillis();
            
            if ( maxDuration != 0 ) {
                maxDuration= t1 + maxDuration * 1000;
            }
            
            for (int i=0; i < nb_threads; i++ ) {
                Worker w=new Worker(this, server);
            }
            println ("INFO", nb_threads + " threads connected to server " + server );
            println ("INFO", "Will search using filter \"(" + attr + " = <value> )\" (MAX =" + NB_MAX_srchs + ")" );
            synchronized (lock) {    
                lock.notifyAll();
            }
            int seconds=0;
            while (true) {
                    
                long new_t1=System.currentTimeMillis();
                
                if ( ( maxDuration != 0 ) && ( new_t1 > maxDuration ) ) { break; }
                
                if ( (new_t1 - t1) >= 1000 ) {
                    
                    
                    println("INFO",  nb_srchs_done + " srchs/sec."); // (time = "+(new_t1-t1) + "ms)");
                    // println("DEBUG",  nb_srchs_started + " srchs/sec started"); 
                    
                    total_nb_srchs+=nb_srchs_done;
                    nb_srchs_started=0;
                    nb_srchs_done=0;
                    synchronized (lock) {    
                        lock.notifyAll();
                    } 
                    
                    if ( (seconds++) >= 9 ) {
                        
                        duration=((new_t1-startup)/1000);
                        
                        println("INFO",  "Avg rate: " + (total_nb_srchs/duration) + " srchs/sec. after " + getTime(duration));
                        seconds=0;
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
    
    public static String getTime(long d) {
        String time=new String (d + " sec.");
        if ( d > 10000 ) {
            time=new String ((d/3600) + " hours");
        } else if ( d > 300 ) {
            time=new String ((d/60) + " min.");
        }
        return time;
    }
    
    public static void main( String[] args )

    {
        String usage = "Usage: java Main [-h <host>] [-p <port>]  -b <base_dn> "
                        + "[-D <bindDN> ] [-w <bindPW> ] [-t <nb_threads>] [-M <nb_max_srchs>] [-a <attribute>] [-d duration (seconds)]";
        int portnumber = LDAPv2.DEFAULT_PORT;
        // Check for these options. -H means to print out a usage message.
        GetOpt options = new GetOpt( "h:p:b:d:D:w:H:t:M:a:", args );
        // Get the arguments specified for each option.
        hostname = options.getOptionParam( 'h' );
        String port = options.getOptionParam( 'p' );
        bindDN = options.getOptionParam( 'D' );
        bindPW = options.getOptionParam( 'w' );
        suffix = options.getOptionParam( 'b' );

        if ( options.hasOption( 't' ) ) {
            nb_threads=Integer.parseInt(options.getOptionParam( 't' ));
        }
        
        if ( options.hasOption( 'M' ) ) {
            NB_MAX_srchs=Integer.parseInt(options.getOptionParam( 'M' ));
        }
        
        if ( options.hasOption( 'a' ) ) {
            attr=options.getOptionParam( 'a' );
        }
        
        if ( options.hasOption( 'd' ) ) {
            String sMaxDuration=options.getOptionParam( 'd' );
            maxDuration = Long.parseLong(sMaxDuration);
        }

        //  option -DM to use default QA settings for Directory manager
        if ( bindDN != null && bindDN.equals("M") ) {
            bindDN="cn=Directory Manager";
            bindPW="secret12";
        }

        // Check to see if the hostname (which is mandatory)
        // is not specified or if the user simply wants to
        // see the usage message (-H).
        if ( options.hasOption( 'H' ) || ( suffix == null ) ) {
            System.out.println( usage );
            System.exit( 1 );
        }


        if ( hostname == null ) hostname="localhost";
        // If a port number was specified, convert the port value
        //  to an integer.
        if ( port != null ) {
            try {
                portnumber = java.lang.Integer.parseInt( port );
            } catch ( java.lang.Exception e ) {
                System.out.println( "Invalid port number: " + port );
                System.out.println( usage );
                System.exit( 1 );
            }
        }
        else {
            portnumber=1389;
        }
        server=new Server(hostname,portnumber);
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                duration=((System.currentTimeMillis()-startup)/1000);
                if ( duration != 0 ) 
                    println("INFO", "TOTAL: " + total_nb_srchs + " srchs (Avg rate: " + (total_nb_srchs/duration) + " srchs/sec.) after " + getTime(duration));
            }
        });
        
        
        Client c = new Client();
    }


   public static void inc_srchs_started() {
        check_srchs_started();
        nb_srchs_started++;
   }

   
   public static void inc_srchs_done() {
        nb_srchs_done++;
   }
   
   public static void check_srchs_started() {
        if ( nb_srchs_started>=NB_MAX_srchs ) {
            // println("DEBUG", "srchs=" + nb_srchs_started );
            try {
                synchronized (lock) {
                    lock.wait();
                }
            } catch ( InterruptedException e ) {
                e.printStackTrace();
            }  
        }
    }
    
    public static void wait_after_error() {
        try {
            synchronized (lock) {
                lock.wait();
            }
        } catch ( InterruptedException e ) {
            e.printStackTrace();
        }  
   }
    
   public static void sleep(int time) {
        try {  
            Thread.sleep(time);
        }
        catch ( InterruptedException e )
        {
             println( "ERROR" ,  e.toString() );
        }
    }
    
   
    public static String getDate() {
    
        // Initialize the today's date string
        String DATE_FORMAT = "yyyy/MM/dd:HH:mm:ss";
        java.text.SimpleDateFormat sdf = 
            new java.text.SimpleDateFormat(DATE_FORMAT);
        Calendar c1 = Calendar.getInstance(); // today
        return("[" + sdf.format(c1.getTime()) + "]");
   }
   
   public static void println(String level, String msg) {
        System.out.println (getDate() + " - " + level + ": " + msg );
   }

}

