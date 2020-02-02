package io.github.haaase.arbeitszeitweb

import java.io.File

import cats.effect._
import cats.implicits._
import org.http4s.client.{Client, JavaNetClientBuilder}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.{HttpRoutes, _}
import java.util.concurrent._
import scala.util.Properties.envOrElse

import scala.concurrent.{ExecutionContext, Future, blocking}

object Main extends IOApp {

  val blockingEc = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))
  val httpClient: Client[IO] = JavaNetClientBuilder[IO](blockingEc).create

  val pdfService = HttpRoutes.of[IO] {
    case request@GET -> Root => {
      val filename = "form.pdf"
      StaticFile.fromFile(new File(filename), blockingEc, Some(request)).getOrElseF(NotFound())
    }
  }

  val DataService = HttpRoutes.of[IO] {
    case request@GET -> Root => {
      Ok(GenData.getHolidays(httpClient).toString())
    }
  }

  object MonthParam extends OptionalQueryParamDecoderMatcher[Int]("month")

  object YearParam extends OptionalQueryParamDecoderMatcher[Int]("year")

  object HoursPerMonthParam extends OptionalQueryParamDecoderMatcher[Int]("hourspermonth")

  object MinHoursInRowParam extends OptionalQueryParamDecoderMatcher[Int]("minhoursinrow")

  object MaxHoursInRowParam extends OptionalQueryParamDecoderMatcher[Int]("maxhoursinrow")

  object NameParam extends OptionalQueryParamDecoderMatcher[String]("name")

  object InstitutionParam extends OptionalQueryParamDecoderMatcher[String]("institution")

  object BirthdayParam extends OptionalQueryParamDecoderMatcher[String]("birthday")

  val ArbeitszeitService: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request@GET -> Root / "getform" :? MonthParam(monthparam) +& YearParam(yearparam) +& HoursPerMonthParam(hpmparam) +& MinHoursInRowParam(mihirparam) +& MaxHoursInRowParam(mahirparam) +& NameParam(nameparam) +& InstitutionParam(institutionparam) +& BirthdayParam(birthdayparam) => {
      val defaultConfig = Arbeitszeit.Config()
      val config = Arbeitszeit.Config(month = monthparam.getOrElse(defaultConfig.month),
        year = yearparam.getOrElse(defaultConfig.year),
        hoursPerMonth = hpmparam.getOrElse(defaultConfig.hoursPerMonth),
        minHoursInRow = mihirparam.getOrElse(defaultConfig.minHoursInRow),
        maxHoursInRow = mahirparam.getOrElse(defaultConfig.maxHoursInRow),
        name = nameparam.getOrElse(defaultConfig.name),
        institution = institutionparam.getOrElse(defaultConfig.institution),
        birthday = birthdayparam.getOrElse(defaultConfig.birthday))

      val filename = f"${config.year}-${config.month}%02d_${config.name.replace(" ", "_")}.pdf"
      Arbeitszeit.run(httpClient, config)
      val file = new File(filename)
      file.deleteOnExit() // delete file when jvm exits
      Future {
        blocking{
          // delete file after 60s
          Thread.sleep(60000)
          file.delete()
        }
      }(blockingEc)

      StaticFile.fromFile(file, blockingEc, Some(request)).getOrElseF(NotFound()) // return file
    }
  }

  val httpApp = Router("/pdf" -> pdfService, "/date" -> DataService, "/" -> ArbeitszeitService).orNotFound

  def run(args: List[String]) =
    BlazeServerBuilder[IO]
      .bindHttp(envOrElse("PORT", "8080").toInt, "0.0.0.0")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}