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


public class ExtendedRandom extends Random {

  ArrayList<String> lastNames;
  ArrayList<String> firstNames;
  
  /*****************************************************************/
  public ExtendedRandom() {
    super();
    this.init();
  }
  
  
  /*****************************************************************/
  public ExtendedRandom(long seed) {
    super(seed);
    this.init();
  }
  
  
  /*****************************************************************/
  public void init() {
    lastNames=new ArrayList<String>();
    firstNames=new ArrayList<String>();
    
    try {
      File lastNamesFile= new File("data", "last.names");
      File firstNamesFile= new File("data", "first.names");
      LineNumberReader in=new LineNumberReader (new FileReader(lastNamesFile) );
      while ( in.ready() ) {
        String lastName = in.readLine();
        if ( ! lastName.startsWith("#") ) {
          lastNames.add(lastName);
        }
      }
      in=new LineNumberReader (new FileReader(firstNamesFile) );
      while ( in.ready() ) {
        String firstName = in.readLine();
        if ( ! firstName.startsWith("#") ) {
          firstNames.add(firstName);
        }
      }
    } catch (IOException e) {
      System.out.println (e.toString());
      System.exit(1);
    }
  }
  
  
  /*****************************************************************/
  public int nextInt(int lo, int hi)
  {
    int n = hi - lo + 1;
    int i = this.nextInt() % n;
    if (i < 0)
      i = -i;
    return lo + i;
  }
  
  
  /*****************************************************************/
  public String nextDate() {
    return( nextInt(1950,2006) + "-" + nextInt(1,12) + "-" + nextInt(1,12) );
  }
  
  
  /*****************************************************************/
  public String nextString (int lo, int hi)
  {
    int n = nextInt(lo, hi);
    byte b[] = new byte[n];
    for (int i = 0; i < n; i++)
      b[i] = (byte)nextInt('a', 'z');
    return new String(b, 0);
  }
  
  
  /*****************************************************************/
  public String nextString()
  {
    return nextString(5, 25);
  }
  
  
  /*****************************************************************/
  public String nextName()
  {
    int n = nextInt(5, 20);
    // first capital letter
    byte b[] = new byte[n];
    b[0] = (byte)nextInt('A', 'Z');
    for (int i = 1; i < n; i++)
            b[i] = (byte)nextInt('a', 'z');
    return new String(b, 0);
  }
  
  
  /*****************************************************************/
  public String nextLastName(){
    int i=this.nextInt( 0, lastNames.size()-1 );
    return ( lastNames.get(i) );
  }
  
  
  /*****************************************************************/
  public String nextFirstName(){
    int i=this.nextInt( 0, firstNames.size()-1 );
    return ( firstNames.get(i) );
  }
  
  
  /*****************************************************************/
  public static void main(String[] args)
  {
    ExtendedRandom r = new ExtendedRandom();
    System.out.println(r.nextLastName());
  }
}
