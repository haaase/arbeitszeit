package io.github.haaase.arbeitszeit.cli

import java.io._
import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDate}
import java.util.concurrent.Executors
import GenData._

import cats.effect._

import scala.concurrent.ExecutionContext.global
import org.http4s.client.{Client, JavaNetClientBuilder}
import scalatags.Text.all._
import scopt.OptionParser

import scala.concurrent.ExecutionContext
import scala.sys.process._
import scala.util.matching.Regex

object Arbeitszeit {

  // convenience
  val pdfFile: Regex = "(.*)\\.pdf".r
  val datePattern = "dd.MM.yyyy"
  val dateFormatter = DateTimeFormatter.ofPattern(datePattern)
  val timePattern = "HH:mm"
  val timeFormatter = DateTimeFormatter.ofPattern(timePattern)

  // config parsing
  case class Config(
                     month: Int = 1,
                     year: Int = 2019,
                     hoursPerMonth: Int = 36,
                     minHoursInRow: Int = 3,
                     maxHoursInRow: Int = 4,
                     name: String = "Kim Mustermann",
                     institution: String = "TU Darmstadt",
                     birthday: String = "01.01.2000",
                     pdfForm: String = "form.pdf",
                     useHolidays: Boolean = true,
                     out: String = "")

  val configParser = new scopt.OptionParser[Config]("arbeitszeit") {
    head("arbeitszeit", "2.0")

    note("arbeitszeit is a command line tool used to fill in arbeitszeit forms from TU Darmstadt.")

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

    opt[String]('o', "out")
      .action((x, c) => c.copy(out = x))
      .text("path to the output file")

    opt[Unit]("no-holidays").action((x, c) => c.copy(useHolidays = false))
      .text("This flags disables automatic holiday checking during entry generation.")
  }

  def main(args: Array[String]) = {
    configParser.parse(args, Config()) match {
      case Some(config) => {
        implicit val cs: ContextShift[IO] = IO.contextShift(global)
        val httpClient: Client[IO] = JavaNetClientBuilder[IO](scala.concurrent.ExecutionContext.global).create
        genPDF(httpClient, config)
      }
      case None =>
    }
  }

  def genPDF(client: Client[IO], config: Config = Config()): Unit = {
    val holidays: Set[LocalDate] = {
      if (config.useHolidays) getHolidays(client, config.year)
      else Set()
    }

    val entries = genEntries(month = config.month, year = config.year, hoursPerMonth = config.hoursPerMonth, minHoursInRow = config.minHoursInRow, maxHoursInRow = config.maxHoursInRow, holidays = holidays)
    println(s"Generated entries: ${entries.toString}")

    // create individual pdf document for every 10 entries
    Seq("mkdir", "tmp").! // create tmp dir
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
    val pdfs = Seq("ls", "tmp").!!.split("\n").toSeq.map("tmp/" + _)
    val out = if (config.out == "") f"${config.year}-${config.month}%02d_${config.name.replace(" ", "_")}.pdf" else config.out
    val cmd = Seq("pdftk") ++ pdfs ++ Seq("cat", "output", out)
    cmd.!
    Seq("rm", "-r", "tmp").!
  }

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
          val toMinutesPart = d.toMinutes % 60L
          if (toMinutesPart > 0) s"${d.toHours},${toMinutesPart}"
          else s"${d.toHours}"
        })
      i += 1
    }
    m
  }

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
    Seq("pdftk", pdfInput, "fill_form", xfdfName, "output", pdfOutput).!
    Seq("rm", xfdfName).!
  }
}
