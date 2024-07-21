package models

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}

import javax.inject._

import com.typesafe.config.ConfigFactory

import models.ResultsAPI.TIMEOUT_DURATION
import models.OAuthSigner
import play.api.{Logger, Configuration}
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.libs.ws._
import io.bellare._

import scala.sys.process._

import scala.concurrent.{Await, ExecutionContext}

object TweetingAPI {

  val conf = ConfigFactory.load()

  val API_KEY = conf.getString("twitter.api_key")
  val API_KEY_SECRET = conf.getString("twitter.api_key_secret")
  val ACCESS_TOKEN = conf.getString("twitter.access_token")
  val ACCESS_TOKEN_SECRET = conf.getString("twitter.access_token_secret")

  val url = "https://api.twitter.com/2/tweets"
  val logger = Logger("TweetingAPI")

  def tweet(ws:WSClient, contest:Contest)(implicit ec: ExecutionContext):Option[String] = {

    //format the payload body
    var tweetParams = Map("text" -> contest.formattedForTweet)
    var tweetParamsJson = Json.obj("text" -> contest.formattedForTweet)
    var tweetId:Option[String] = None

    println(contest.formattedForTweet)


    //generate the OAuth header that the Twitter API needs
    val timestamp = System.currentTimeMillis / 1000L
    val nonce = UUID.randomUUID().toString

    val signer = new OAuthSigner(API_KEY, API_KEY_SECRET, ACCESS_TOKEN, ACCESS_TOKEN_SECRET, timestamp.toString, nonce)
    val oauthHeader = signer.generateAuthorizationHeader("POST", url, tweetParams)

    //send the request
    val request: WSRequest = ws.url(url).withHttpHeaders("Authorization" -> oauthHeader, "Content-Type" -> "application/json")

    return Await.result(atMost = TIMEOUT_DURATION, awaitable = request.post(tweetParamsJson).map { response =>

      println(response)
      println(response.json)
      logger.info(response.toString)
      logger.info(response.json.toString)

      tweetId = (response.json \ "data" \ "id").asOpt[String]

      println("Tweet ID: ")
      println(tweetId)

      tweetId
    })
  }
}
