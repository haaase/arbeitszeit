#!/usr/bin/env/amm
import ammonite.ops._
import ammonite.ops.ImplicitWd._
import $ivy.`io.circe::circe-generic:0.10.0`, io.circe.generic.auto._, io.circe.{ Decoder, Encoder }, io.circe.Json, io.circe.syntax._
import $ivy.`io.circe::circe-parser:0.10.0`, io.circe.parser.parse
import cats.syntax.either._ // TODO: ivy import
import java.time.{ LocalDate, LocalTime, YearMonth, Year, DayOfWeek }
import java.time.temporal.ChronoUnit.MINUTES
import java.time.format.DateTimeFormatter
import scala.util.Random

case class Entry(date: LocalDate, start: LocalTime, end: LocalTime) {
  def overlaps(other: Entry): Boolean = {
    date == other.date &&
      ((start == other.start || end == other.end || start == other.end) ||
        (start.isAfter(other.start) && start.isBefore(other.end)) || // start within the others range
        (end.isAfter(other.start) && end.isBefore(other.end))) // end within the others range
  }

  def distance(other: Entry): Long = {
    if (overlaps(other)) 0
    else {
      val (leftStart, leftEnd) = (start.atDate(date), end.atDate(date))
      val (rightStart, rightEnd) = (other.start.atDate(other.date), other.end.atDate(other.date))
      if (leftStart.isBefore(rightStart)) leftEnd.until(rightStart, MINUTES)
      else rightEnd.until(leftStart, MINUTES)
    }
  }
}

// get holidays in Hessen using webservice
def getHolidays(api: String = "https://feiertage-api.de/api/?jahr=2019&nur_land=HE"): Set[LocalDate] = {
  // helper class
  case class Holiday(datum: String, hinweis: String) {
    // https://www.baeldung.com/java-datetimeformatter
    def toDate: LocalDate = LocalDate.from(DateTimeFormatter.ISO_LOCAL_DATE.parse(datum))
  }
  // curl API
  val response = %%("curl", api).out.string
  // parse response
  parse(response).getOrElse(Json.Null).as[Map[String, Holiday]] match {
    case Left(_) =>
      println("ERROR parsing holidays JSON!"); Set()
    case Right(m: Map[String, Holiday]) => m.values.map(_.toDate).toSet
  }
}

// generate all days for a given month
def genDaysPerMonth(month: Int, year: Int): List[LocalDate] = {
  def toDate(day: Int) = LocalDate.of(year, month, day)
  (1 to YearMonth.of(year, month).lengthOfMonth).toList.map(toDate(_))
}

def genEntries(hoursPerMonth: Int = 36, minHoursInRow: Int = 3, maxHoursInRow: Int = 4, minBreakTime: Int = 1, startTime: Int = 9, endTime: Int = 17, month: Int = YearMonth.now.getMonthValue, year: Int = Year.now.getValue, holidays: Set[LocalDate] = getHolidays()): List[Entry] = {
  // gen days and filter out holidays and weekends
  val days = genDaysPerMonth(month, year).filter(
    day => !holidays.contains(day) && (day.getDayOfWeek != DayOfWeek.SATURDAY) && (day.getDayOfWeek != DayOfWeek.SUNDAY))

  def gen(remainingHours: Int, entries: List[Entry]): List[Entry] = {
    remainingHours match {
      case 0 => entries
      case remaining => {
        // pick day
        val day = Random.shuffle(days).head

        // pick length
        val length = {
          val r = Random.shuffle(minHoursInRow to maxHoursInRow).head
          if (remaining < r) remaining
          else r
        }

        // pick startTime
        val start = Random.shuffle(startTime to (endTime - length)).head

        val entry = Entry(day, LocalTime.of(start, 0), LocalTime.of(start + length, 0))
        println(s"Generated Entry: ${entry}")

        // validate entry to check that it does not overlap and that break times hold
        if (entries.exists(e => entry.distance(e) < minBreakTime)) {
          val problem = entries.find(e => entry.distance(e) < minBreakTime)
          println(s"Trying again because of conflict with $problem!")
          gen(remaining, entries) //try again
        } else {
          gen(remaining - length, entries :+ entry)
        }
      }
    }
  }
  gen(hoursPerMonth, List()).sortWith((left: Entry, right: Entry) => {
    if (left.date == right.date) left.start.compareTo(right.start) <= 0
    else left.date.compareTo(right.date) <= 0
  })
}