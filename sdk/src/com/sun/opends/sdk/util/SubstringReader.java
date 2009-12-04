package com.sun.opends.sdk.util;

/**
 * Created by IntelliJ IDEA.
 * User: boli
 * Date: Jul 13, 2009
 * Time: 3:11:50 PM
 * To change this template use File | Settings | File Templates.
 */
public class SubstringReader
{
  private String source;
  private int pos;
  private int mark;

  public SubstringReader(String s) {
    Validator.ensureNotNull(s);
    source = s;
    pos = 0;
    mark = 0;
  }

  /**
   * Attemps to read a substring of the specified length.
   * 
   * @param length The number of characters to read.
   * @return The substring.
   */
  public String read(int length)
  {
    String substring = source.substring(pos, pos + length);
    pos += length;
    return substring;
  }

  public char read()
  {
    return source.charAt(pos++);
  }

  public String getString()
  {
    return source;
  }

  public int pos()
  {
    return pos;
  }

  public int remaining()
  {
    return source.length() - pos;
  }

  /**
   * Marks the present position in the stream. Subsequent calls
   * to reset() will reposition the stream to this point.
   */
  public void mark()
  {
    mark = pos;
  }

  /**
   * Resets the stream to the most recent mark, or to the beginning
   * of the string if it has never been marked.
   */
  public void reset()
  {
    pos = mark; 
  }

  public int skipWhitespaces()
  {
    int skipped = 0;
    while(pos < source.length() && source.charAt(pos) == ' ')
    {
      skipped++;
      pos++;
    }
    return skipped;
  }
}
