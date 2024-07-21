package controllers

import javax.inject._
import scala.concurrent._
import ExecutionContext.Implicits.global
import play.api.libs.ws._
import play.api._
import play.api.mvc._
import play.api.db.Database
import models.ResultsAPI

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents, wsClient:WSClient, database:Database) extends BaseController {

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }

  def refresh() = Action { implicit request: Request[AnyContent] =>
    println("HomeController 28- refresh")
    val results = ResultsAPI.checkForUpdate(wsClient, database)
    println("refresh done!")

    var formattedContestResults:String = results._1.map(_.formattedForTweet).mkString("\n------------------\n")

    if (results._2) formattedContestResults = "UPDATE!!!\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n\n" + formattedContestResults

    Ok(formattedContestResults)
  }

  def populate() = Action { implicit request: Request[AnyContent] =>
    println("HomeController 37- populateDb")
    ResultsAPI.populateDB(wsClient, database)
    Ok("populate done!")
  }

  def logs() = Action { implicit request: Request[AnyContent] =>
    println("HomeController 43- logs")
    val source = scala.io.Source.fromFile("logs/application.log")
    val lines = try source.mkString finally source.close()
    Ok(lines)
  }
}
