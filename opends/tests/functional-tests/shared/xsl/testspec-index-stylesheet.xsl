<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="1.0"
xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="html"/>

<xsl:template match="/">

  <xsl:element name="html">
  
    <xsl:variable name="opends-url" select="'https://opends.dev.java.net/'"/>
    <xsl:variable name="opends-images" select="concat($opends-url,'public/images/')"/>
    <xsl:variable name="opends-logo" select="concat($opends-images,'opends_logo_sm.png')"/>

    <link rel="stylesheet" type="text/css" href="/branding/css/print.css" media="print"/>

  <xsl:element name="head">

    <link rel="stylesheet" type="text/css" href="/branding/css/print.css" media="print"/>
    <link rel="stylesheet" href="https://opends.dev.java.net/public/css/opends.css" type="text/css"/>

    <xsl:element name="title">
      <xsl:value-of select="'Test Specification'"/>
    </xsl:element>

  </xsl:element>

  <xsl:element name="body">

    <xsl:element name="table">
      <xsl:attribute name="class">
        <xsl:value-of select="'tertmasttable'"/>
      </xsl:attribute>
      <xsl:attribute name="cellspacing">
        <xsl:value-of select="'0'"/>
      </xsl:attribute>
      <xsl:attribute name="width">
        <xsl:value-of select="'100%'"/>
      </xsl:attribute>
      <xsl:element name="tr">
        <xsl:element name="td">
          <xsl:element name="div">
            <xsl:attribute name="class">
              <xsl:value-of select="'collectionheader'"/>
            </xsl:attribute>
            <xsl:value-of select="'Test Specifications for OpenDS'"/>
          </xsl:element>
        </xsl:element>
        <xsl:element name="td">
          <xsl:attribute name="width">
            <xsl:value-of select="'10%'"/>
          </xsl:attribute>
          <xsl:element name="a">
            <xsl:attribute name="href">
              <xsl:value-of select="$opends-url"/>
            </xsl:attribute>
              <xsl:element name="img">
              <xsl:attribute name="src">
                <xsl:value-of select="$opends-logo"/>
              </xsl:attribute>
              <xsl:attribute name="alt">
                <xsl:value-of select="'OpenDS Logo'"/>
              </xsl:attribute>
              <xsl:attribute name="align">
                <xsl:value-of select="'middle'"/>
              </xsl:attribute>
              <xsl:attribute name="border">
                <xsl:value-of select="'0'"/>
              </xsl:attribute>
              <xsl:attribute name="height">
                <xsl:value-of select="'33'"/>
              </xsl:attribute>
              <xsl:attribute name="width">
                <xsl:value-of select="'104'"/>
              </xsl:attribute>
            </xsl:element>
          </xsl:element>
        </xsl:element>
      </xsl:element>
    </xsl:element>

    <hr noshade="noshade" size="1" />

    <xsl:element name="br"/>

    <xsl:variable name="testspec" select="/qa/doc/testspec"/>

    <xsl:element name="ol">

    <xsl:for-each select="$testspec">

      <!-- Test Spec List -->
      <xsl:element name="li">
        <xsl:element name="a">
          <xsl:attribute name="href">
            <xsl:value-of select="@location"/>
          </xsl:attribute>
          <xsl:value-of select="@name"/>
        </xsl:element>
      </xsl:element>

    </xsl:for-each>

    </xsl:element>

    </xsl:element> 
     
  </xsl:element>


</xsl:template>


</xsl:stylesheet>
