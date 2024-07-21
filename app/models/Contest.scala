package models

import play.api.db.Database

import anorm.SqlParser._
import anorm._
import anorm.JodaParameterMetaData._

import scala.language.postfixOps
import java.text.DecimalFormat
import java.time._

import play.api.Logger

//a Contest is any individual voted-upon race, whether it be for Mayor or the passing of some ballot measure

case class Contest (
  id:Option[Long] = None,
  title:String,
  var candidates:List[Candidate] = Nil
) {

  val floatFormatter = new DecimalFormat("#.##")
  val datetimeFormatter = format.DateTimeFormatter.ofPattern("MMM dd yyyy HH:mm:ss")

  def formattedCandidates(limit:Option[Int] = None):String = {

    val totalVotes = candidates.map(_.votes).sum.toFloat

    val candidatesToList = limit match {
      case Some(i) => candidates.sortWith(_.votes > _.votes).slice(0, i)
      case None => candidates.sortWith(_.votes > _.votes)
    }

    candidatesToList.map { c =>

      val percentage = if (totalVotes == 0) 0 else (c.votes / totalVotes) * 100

      s"${c.name} - ${floatFormatter.format(percentage)}% (${c.votes} votes)"
    }.mkString("\n")

  }

  def formatted:String = {

    var formattedString =
s"""
${title}

"""

    formattedString += formattedCandidates()

    return formattedString
  }

  def formattedForTweet:String = {
    //val timestam = "Fri Nov 11 2022 00:40:01"
    val timestamp = datetimeFormatter.format(LocalDateTime.now().atZone(ZoneId.of("America/Los_Angeles")))

s"""
Election Update- ${timestamp}

${title}

${formattedCandidates(Some(ResultsAPI.NUMBER_OF_TOP_CANDIDATES_TO_POST))}
"""
  }

}

case class Candidate(
  id:Option[Long] = None,
  name:String,
  var votes:Long,
  contestId:Option[Long] = None
)




object Contest {

  val simple = {
    get[Option[Long]]("contest.contest_id") ~
      get[String]("contest.title") map {
      case id ~ title => {
        Contest(id, title)
      }
    }
  }

  // the Contest MySQL queries

  def getAllElectionResults(db: Database): List[Contest] = {
    db.withConnection { implicit connection =>
      val allCandidates = SQL(
        s"""
          select candidate.* from contest
          join candidate on candidate.contest_id = contest.contest_id
        """
      ).as(Candidate.simpleWithContestId *)

      val allContests = SQL(
        s"""
          select contest.* from contest
        """
      ).as(simple *)

      val candidatesByContestId: Map[Option[Long], List[Candidate]] = allCandidates.groupBy(_.contestId)

      allContests.map { c =>
        c.copy(candidates = candidatesByContestId(c.id))
      }
    }
  }

  // populateDB converts a list of contests retrieved from the results website into new entries in the contest and candidate tables; should only be called at the start of a new election cycle

  def populateDB(db:Database, contests:List[Contest]):Boolean = {
    if (contests.isEmpty) return false
    else {

      val valuesClause:String = contests.map(c => "(" + c.id.getOrElse(0L) + ", '" + c.title + "')").mkString(",")

      println(valuesClause)
      println("\n\n\n")

      db.withConnection { implicit connection =>
        SQL(
          s"""
          insert ignore into contest(contest_id, title)
          values ${valuesClause}
        """
        ).execute()

        val candidateValuesClause:String = contests.flatMap(contest => contest.candidates.map(candidate => "(" + candidate.id.getOrElse(0L) + ", \"" + candidate.name.replace('"', '\'') + "\", -999, " + contest.id.getOrElse(0L) + ")")).mkString(",")

        println(candidateValuesClause)

        SQL(
          s"""
          insert ignore into candidate(candidate_id, name, votes, contest_id)
          values ${candidateValuesClause}
        """
        ).execute()
      }
    }

    true
  }
}


// a Candidate is an option one can vote for in a Contest

object Candidate {


  val logger = Logger("CandidateObject")


  // forms for casting the query results into Candidate objects

  val simple = {
    get[Option[Long]]("candidate.candidate_id") ~
      get[String]("candidate.name") ~
      get[Int]("candidate.votes") map {
      case id ~ name ~ votes => {
        Candidate(id, name, votes)
      }
    }
  }

  val simpleWithContestId = {
    get[Option[Long]]("candidate.candidate_id") ~
      get[String]("candidate.name") ~
      get[Int]("candidate.votes") ~
      get[Long]("candidate.contest_id") map {
      case id ~ name ~ votes ~ contest_id => {
        Candidate(id, name, votes, Some(contest_id))
      }
    }
  }

  // the MySQL queries

  def getCandidatesForContest(db:Database, contestId:Long):List[Candidate] = {
    db.withConnection { implicit connection =>
      SQL(
        s"""
          select candidate.* from contest
          join candidate on candidate.contest_id = contest.contest_id
          where candidate.contest_id = ${contestId}
        """
      ).as(simple *)
    }
  }

  def bulkUpdate(db: Database, candidates: List[Candidate]): Boolean = {
    db.withConnection { implicit connection =>
      candidates.foreach { candidate =>
        SQL(
          s"""
            update candidate set
            votes = {votes}
            where candidate_id = {id}
          """
        ).on(
          "id" -> candidate.id.getOrElse(0L),
          "votes" -> candidate.votes
        ).execute()

        logger.info(s"SQL update done: candidate ${candidate.id} @ ${candidate.votes} votes")

      }
    }
    true
  }


}
