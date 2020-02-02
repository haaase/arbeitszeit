#!/usr/bin/env amm
// This script is inspired by:
// https://joewiz.org/2014/02/13/filling-pdf-forms-with-pdftk-xfdf-and-xquery/
import ammonite.ops._
import ammonite.ops.ImplicitWd._
import java.time.{ LocalDate, LocalTime, Duration }
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import scala.annotation.tailrec
import scala.io.Source
import scala.util.matching.Regex
import $ivy.`com.github.scopt::scopt:4.0.0`, scopt.OptionParser
import $file.gen_data, gen_data.{ Entry, genEntries, getHolidays }
import $file.populate_pdf, populate_pdf.populatePdf

// scopt config
case class Config(
  month: Int = 1,
  year: Int = 2019,
  hoursPerMonth: Int = 36,
  minHoursInRow: Int = 3,
  maxHoursInRow: Int = 4,
  name: String = "Kim Mustermann",
  institution: String = "TU Darmstadt",
  birthday: String = "01.01.2000",
  pdfForm: String = "",
  useHolidays: Boolean = true)

val configParser = new scopt.OptionParser[Config]("arbeitszeit") {
  head("arbeitszeit", "1.0")

  note("arbeitszeit is an ammonite script used to fill in arbeitszeit forms from TU Darmstadt.")

  help("help")

  opt[Int]('m', "month")
    .action((x, c) => c.copy(month = x))

  opt[Int]('y', "year")
    .action((x, c) => c.copy(year = x))

  opt[Int]('h', "hours")
    .action((x, c) => c.copy(hoursPerMonth = x))
    .text("working hours per month")

  opt[Int]("min-hours")
    .action((x, c) => c.copy(minHoursInRow = x))
    .text("minimum working hours in a row")

  opt[Int]("max-hours")
    .action((x, c) => c.copy(maxHoursInRow = x))
    .text("maximum working hours in a row")

  opt[String]('n', "name")
    .action((x, c) => c.copy(name = x))

  opt[String]('i', "institution")
    .action((x, c) => c.copy(institution = x))

  opt[String]('b', "birthday")
    .action((x, c) => c.copy(birthday = x))

  opt[String]('f', "form")
    .required()
    .action((x, c) => c.copy(pdfForm = x))
    .text("path to the pdf form to be filled in")

  opt[Unit]("no-holidays").action((x, c) => c.copy(useHolidays = false))
    .text("This flags disables automatic holiday checking during entry generation.")
}

// convenience
val pdfFile: Regex = "(.*)\\.pdf".r
val datePattern = "dd.MM.yyyy";
val dateFormatter = DateTimeFormatter.ofPattern(datePattern);

val timePattern = "HH:mm";
val timeFormatter = DateTimeFormatter.ofPattern(timePattern);

// takes 10 entries and inserts them into the pdf form
def processEntryGroup(group: List[Entry]): Map[String, String] = {
  var i = 1
  var m: Map[String, String] = Map()
  for (entry <- group) {
    m = m ++ Map(
      s"DatumRow${i}" -> dateFormatter.format(entry.date),
      s"Arbeitszeit Beginn  EndeRow${i}" -> timeFormatter.format(entry.start),
      s"Arbeitszeit Beginn  EndeRow${i}_2" -> timeFormatter.format(entry.end),
      s"t&#228;gl gesamt Stunden Arbeits zeit abz&#252;glich der PausenRow${i}" -> {
        val d = Duration.between(entry.start, entry.end)
        if (d.toMinutesPart > 0) s"${d.toHours},${d.toMinutesPart}"
        else s"${d.toHours}"
      })
    i += 1
  }
  m
}

@main
def main(args: String*) = {
  configParser.parse(args, Config()) match {
    case Some(config) => {
      // generate entries
      val holidays: Set[LocalDate] = {
        if (config.useHolidays) getHolidays(config.year)
        else Set()
      }

      val entries = genEntries(month = config.month, year = config.year, hoursPerMonth = config.hoursPerMonth, minHoursInRow = config.minHoursInRow, maxHoursInRow = config.maxHoursInRow, holidays = holidays)

      // create individual pdf document for every 10 entries
      mkdir ! pwd / 'tmp // create tmp dir
      val pdfFile(basename) = config.pdfForm // get input filename without extension
      var i = 0
      var lastEntry = LocalDate.of(2000, 1, 1)
      val numberOfGroups = entries.grouped(10).length
      for (group <- entries.grouped(10)) {
        val fields = Map(
          "EinrichtungInstitut" -> config.institution,
          "Name Vorname 1" -> config.name,
          "Name Vorname 2" -> config.birthday,
          "Aufzeichnung f&#252;r den Zeitraum vom" -> {
            // check if this is the first document
            if (i == 0) dateFormatter.format(entries(0).date.withDayOfMonth(1))
            else dateFormatter.format(lastEntry)
          },
          "bis" -> {
            // check if this is the last document
            if (i == numberOfGroups - 1) dateFormatter.format(entries(0).date.withDayOfMonth(entries(0).date.lengthOfMonth))
            else dateFormatter.format(group.last.date)
          }) ++ processEntryGroup(group)
        populatePdf(config.pdfForm, s"tmp/${basename}_populated${i}.pdf", fields)
        lastEntry = group.last.date
        i += 1
      }

      // merge pdfs
      val pdfs = (ls ! pwd / 'tmp).map(_.toString)
      val cmd = List("pdftk") ++ pdfs ++ List("cat", "output", f"${config.year}-${config.month}%02d_${config.name.replace(" ", "_")}.pdf")
      %(cmd)
      rm ! pwd / 'tmp
    }
    case None =>
  }
}
