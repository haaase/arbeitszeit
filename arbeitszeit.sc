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
import $file.gen_data, gen_data.{ Entry, genEntries }
import $file.populate_pdf, populate_pdf.populatePdf

// convenience
val pdfFile: Regex = "(.*)\\.pdf".r
case class Credentials(institution: String, name: String, birthday: String)

// read input data
// Example line: "FB 20 – Informatik;Max Mustermann;14;2;2019;14:00;16:00"
// def parseInput(inputName: String): List[Entry] = {
//   val entryLine: Regex = "(\\d?\\d);(\\d?\\d);(\\d\\d\\d\\d);(\\d?\\d):(\\d\\d);(\\d?\\d):(\\d\\d)".r
// 
//   @tailrec
//   def parse(credentials: Credentials, entries: List[Entry], input: List[String]): (Credentials, List[Entry]) = {
//     input match {
//       case entryLine(day, month, year, startHour, startMinute, endHour, endMinute) :: xs => {
//         val newEntry = Entry(LocalDate.of(year.toInt, month.toInt, day.toInt), LocalTime.of(startHour.toInt, startMinute.toInt), LocalTime.of(endHour.toInt, endMinute.toInt))
//         println(newEntry)
//         parse(credentials, entries :+ newEntry, xs)
//       }
//       case Nil => (credentials, entries)
//     }
//   }
//   parse(Credentials("", "", ""), List(), Source.fromFile(inputName).getLines.toList)
// }

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
def main(pdfForm: String) = {
  val credentials = Credentials("TU Darmstadt", "Kim Mustermensch", "01.01.2000")
  // val (credentials, entries) = parseInput(data)
  val entries = genEntries(month = 5, year = 2019)
  val pdfFile(basename) = pdfForm // get input filename without extension

  mkdir ! pwd / 'tmp // create tmp dir

  var i = 0
  for (group <- entries.grouped(10)) {
    val fields = Map(
      "EinrichtungInstitut" -> credentials.institution,
      "Name Vorname 1" -> credentials.name,
      "Name Vorname 2" -> credentials.birthday) ++ processEntryGroup(group)
    populatePdf(pdfForm, s"tmp/${basename}_populated${i}.pdf", fields)
    i += 1
  }

  // merge pdfs
  val pdfs = (ls ! pwd / 'tmp).map(_.toString)
  val cmd = List("pdftk") ++ pdfs ++ List("cat", "output", s"${basename}_populated.pdf")
  %(cmd)
  rm ! pwd / 'tmp
}}