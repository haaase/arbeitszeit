#!/usr/bin/env amm
// This script is inspired by:
// https://joewiz.org/2014/02/13/filling-pdf-forms-with-pdftk-xfdf-and-xquery/
import ammonite.ops._
import ammonite.ops.ImplicitWd._
import java.time.{ LocalDate, LocalTime }
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import scala.annotation.tailrec
import scala.io.Source
import scala.util.matching.Regex
import $ivy.`com.github.scopt::scopt:4.0.0`, scopt.OptionParser
import $file.gen_data, gen_data.{ Entry, genEntries }
import $file.populate_pdf, populate_pdf.populatePdf

// scopt config
case class Config(
  month: Int = 1,
  year: Int = 2019,
  hoursPerMonth: Int = 36,
  name: String = "Kim Mustermann",
  institution: String = "TU Darmstadt",
  birthday: String = "01.01.2000",
  pdfForm: String = "arbeitszeit2019.pdf")

val configParser = new scopt.OptionParser[Config]("arbeitszeit") {
  head("arbeitszeit", "0.8")

  opt[Int]('m', "month")
    .action((x, c) => c.copy(month = x))

  opt[Int]('y', "year")
    .action((x, c) => c.copy(year = x))

  opt[Int]('h', "hours")
    .action((x, c) => c.copy(hoursPerMonth = x))
    .text("working hours per month")

  opt[String]('n', "name")
    .action((x, c) => c.copy(name = x))

  opt[String]('i', "institution")
    .action((x, c) => c.copy(institution = x))

  opt[String]('b', "birthday")
    .action((x, c) => c.copy(birthday = x))

  opt[String]('f', "form")
    .action((x, c) => c.copy(pdfForm = x))
    .text("the arbeitszeit pdf form")
}

// convenience
val pdfFile: Regex = "(.*)\\.pdf".r

def processEntryGroup(group: List[Entry]): Map[String, String] = {
  var i = 1
  var m: Map[String, String] = Map()
  for (entry <- group) {
    m = m ++ Map(
      s"DatumRow${i}" -> entry.date.toString,
      s"Arbeitszeit Beginn  EndeRow${i}" -> entry.start.toString,
      s"Arbeitszeit Beginn  EndeRow${i}_2" -> entry.end.toString,
      s"t&#228;gl gesamt Stunden Arbeits zeit abz&#252;glich der PausenRow${i}" -> (ChronoUnit.MINUTES.between(entry.start, entry.end).toDouble / 60).toString)
    i += 1
  }
  m
}

@main
def main(args: String*) = {
  configParser.parse(args, Config()) match {
    case Some(config) => {
      // generate entries
      val entries = genEntries(month = config.month, year = config.year, hoursPerMonth = config.hoursPerMonth, holidays = Set())

      println(s"ENTRIES: $entries, LENGTH: ${entries.length}")

      // create individual pdf document for every 10 entries
      mkdir ! pwd / 'tmp // create tmp dir
      val pdfFile(basename) = config.pdfForm // get input filename without extension
      var i = 0
      val numberOfGroups = entries.grouped(10).length
      for (group <- entries.grouped(10)) {
        println(s"GROUP: $group")
        val fields = Map(
          "EinrichtungInstitut" -> config.institution,
          "Name Vorname 1" -> config.name,
          "Name Vorname 2" -> config.birthday,
          "Aufzeichnung f&#252;r den Zeitraum vom" -> {
            // check if this is the first document
            if (i == 0) dateFormatter.format(entries(0).date.withDayOfMonth(1))
            else dateFormatter.format(group.head.date)
          },
          "bis" -> {
            // check if this is the last document
            if (i == numberOfGroups - 1) dateFormatter.format(entries(0).date.withDayOfMonth(entries(0).date.lengthOfMonth))
            else dateFormatter.format(group.last.date)
          }) ++ processEntryGroup(group)
        populatePdf(config.pdfForm, s"tmp/${basename}_populated${i}.pdf", fields)
        i += 1
      }

      // merge pdfs
      val pdfs = (ls ! pwd / 'tmp).map(_.toString)
      val cmd = List("pdftk") ++ pdfs ++ List("cat", "output", s"${basename}_populated.pdf")
      %(cmd)
      rm ! pwd / 'tmp
    }
    case None =>
  }
}
