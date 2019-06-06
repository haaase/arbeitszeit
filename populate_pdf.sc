#!/usr/bin/env/amm
import ammonite.ops._
import ammonite.ops.ImplicitWd._
import java.io._
import $ivy.`com.lihaoyi::scalatags:0.6.8`, scalatags.Text.all._

// generates FDF XML (xfdf) from field/value pairs
def populatePdf(pdfInput: String, pdfOutput: String, fields: Map[String, String]) = {
  // Example for a xfdf file:
  // <xfdf xmlns="http://ns.adobe.com/xfdf/">
  // <fields>
  //     <field name="EinrichtungInstitut">
  //         <value>TU Darmstadt</value>
  //     </field>
  //     <field name="Name Vorname 1">
  //         <value>Erika Musterfrau</value>: t&#228;gl gesamt Stunden Arbeits zeit abz&#252;glich der PausenRow1
  //     </field>
  // </fields>
  // </xfdf>
  val xfdf = {
    val snippet = tag("xfdf")(attr("xmlns") := "http://ns.adobe.com/xfdf/")(
      tag("fields")(
        for ((name, value) <- fields.toList) yield tag("field")(attr("name") := name)(
          tag("value")(value))))
    """<?xml version="1.0" encoding="UTF-8"?>""" + snippet.render.replace("&amp;", "&") // needed because of strange fieldnames containing escaped unicode characters
  }

  // write xfdf to filesystem
  val xfdfName = "temp.xml"
  val file = new File(xfdfName)
  val bw = new BufferedWriter(new FileWriter(file))
  bw.write(xfdf)
  bw.close()

  // populate pdf
  %("pdftk", pdfInput, "fill_form", xfdfName, "output", pdfOutput)
  rm(pwd / xfdfName)
}