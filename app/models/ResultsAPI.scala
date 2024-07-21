package models

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.SystemMaterializer
import org.apache.pekko.util.ByteString
import play.api.http.HttpEntity
import play.api.libs.json.{JsArray, JsValue}
import play.api.libs.ws._
import play.api.db.Database
import play.api.mvc._
import play.api.Logger

import scala.concurrent.Await
import scala.util.{Failure, Success}

object ResultsAPI {
  val hostname = "http://results.lavote.gov"
  val electionID = "4316"
  val candidatePath = "/ElectionResults/GetElectionData?electionID=" + electionID
  val resultsPath = "/ElectionResults/GetCounterData?electionID=" + electionID
  val method = "GET"

  val headers = {
    "Content-Type" -> "application-json"
  }

  val logger = Logger("ResultsApi")

  // hard-coded list of contests to track and tweet about
  // CD 4, CD 12, CD 14, DA, HLA, Senator
  val RACE_IDS_TO_TRACK = List(9368, 9372, 9373, 9054, 9399, 9021)

  val NUMBER_OF_TOP_CANDIDATES_TO_POST = 3
  val TIMEOUT_DURATION = Duration(10, TimeUnit.SECONDS)

  // get the current election results from the election results website
  // the contest information and the candidates' results are returned by separate API calls, so we call one and then the other to construct our updated Contest objects
  def retrieveElectionResults(ws:WSClient)(implicit ec: ExecutionContext):List[Contest] = {

    val resultsRequest: WSRequest = ws.url(hostname + resultsPath).addHttpHeaders(headers)

    logger.info("making the results request")

    var resultsMap:Map[Long, Long] = Map() //map of candidate IDs to vote totals
    var contests: List[Contest] = Nil

    return Await.result(atMost=TIMEOUT_DURATION, awaitable=resultsRequest.get().map { resultsResponse =>

      logger.info("request response received")

      val resultsData = (resultsResponse.json \ "Data").as[JsArray].value
      resultsMap = resultsData.map { result =>

        /*example response entry:
          { Number: 739,
          ReferenceID: 18790,
          ReferenceType: 'CAND',
          Value: 1608 }
          */

        val referenceId = (result \ "ReferenceID").as[Long]
        var votesTotal = (result \ "Value").as[Long]
        (referenceId -> votesTotal)
      }.toMap

      val candidateRequest: WSRequest = ws.url(hostname + candidatePath).addHttpHeaders(headers)

      Await.result(atMost=TIMEOUT_DURATION, awaitable=candidateRequest.get().map { response =>

        logger.info("making the candidate request")
        logger.info(hostname + candidatePath)

        val candidateData = (response.json \\ "Contests")
        if (candidateData == Nil) {
          logger.info("Candidate Json Empty")
        }
        else {

          for (contestSet <- candidateData) {
            val contestArray = contestSet.as[JsArray].value
            for (contest <- contestArray) {
              val contestId = (contest \ "ID").asOpt[Int]
              if (contestId.isDefined && RACE_IDS_TO_TRACK.contains(contestId.get)) {

                val candidates = candidatesJsonToList((contest \ "Candidates").as[JsArray])

                candidates.foreach { c =>
                  c.votes = resultsMap(c.id.getOrElse(0L))
                }

                logger.info(candidates.mkString("\n"))

                val constructedContest = Contest(contestId.map(_.toLong), (contest \ "Title").as[String], candidates)

                contests = contests :+ constructedContest
              }
            }
          }
        }
        logger.info("Contests: ")
        logger.info(contests.mkString("\n\n"))
        logger.info("~~~~~~~~~~~~~~~~~~~")
      })
      contests
    })
  }

  def candidatesJsonToList(candidatesJson:JsArray):List[Candidate] = {
    candidatesJson.value.map { c =>
      val id = (c \ "ID").asOpt[Long]
      val name = (c \ "Name").asOpt[String]
      Candidate(id, name.getOrElse("Error"), 0L)
    }.toList
  }

  def checkForUpdate(ws:WSClient, db:Database)(implicit ec: ExecutionContext):(List[Contest], Boolean) = {
    val currentResults = retrieveElectionResults(ws)
    val storedResults = Contest.getAllElectionResults(db)

    logger.info("Stored results from DB:")
    logger.info(storedResults.mkString("\n\n"))
    logger.info("******************\n\n\n")

    //compare the two

    val changedContests = currentResults.filter { currentContest =>
      val storedVersion = storedResults.find(_.id == currentContest.id)
      storedVersion.isDefined && storedVersion.get.candidates.map(_.votes).sum < currentContest.candidates.map(_.votes).sum
    }

    //if currentResults votes are higher, tweet and update the DB
    if (changedContests.nonEmpty) {
      logger.info("contests are changed!")
      logger.info(changedContests.mkString("\n\n"))

      //bulk update
      Candidate.bulkUpdate(db, changedContests.flatMap(_.candidates))
      logger.info("*******************BULK UPDATE DONE*****************************\n\n")
      //tweet
      val tweetIds:List[String] = changedContests.flatMap(c => TweetingAPI.tweet(ws, c))

      if (tweetIds.size != changedContests.size) {
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~ERROR~~~~~~~~~~~~~~~~~~~~~~~~~~")
        logger.info("missing tweet ID(s) - " + tweetIds.size + " IDs returned; " + changedContests.size + " expected")
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~`")
      }
    }

    return (currentResults, changedContests.nonEmpty)

  }

  def populateDB(ws: WSClient, db: Database)(implicit ec: ExecutionContext) = {
    val currentResults = retrieveElectionResults(ws)

    Contest.populateDB(db, currentResults)
  }
}
