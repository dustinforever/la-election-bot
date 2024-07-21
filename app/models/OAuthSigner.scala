package models

import java.util.UUID

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import java.nio.charset.StandardCharsets

class OAuthSigner(
                   consumerKey: String,
                   consumerSecret: String,
                   token: String,
                   tokenSecret: String,
                   timestamp: String,
                   nonce: String
                 ) {

  private def percentEncode(s: String): String =
    java.net.URLEncoder.encode(s, "UTF-8")
      .replace("+", "%20")
      .replace("*", "%2A")
      .replace("%7E", "~")

  private def signBaseString(httpMethod: String, url: String, parameters: Map[String, String]): String = {
    val baseString = s"${httpMethod.toUpperCase}&${percentEncode(url)}&${percentEncode(parameters.map { case (k, v) => s"$k=$v" }.mkString("&"))}"
    baseString
  }

  private def sign(key: String, data: String): String = {
    val signingKey = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA1")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(signingKey)
    val rawHmac = mac.doFinal(data.getBytes("UTF-8"))
    Base64.getEncoder.encodeToString(rawHmac)
  }

  def generateAuthorizationHeader(httpMethod: String, url: String, parameters: Map[String, String]): String = {
    val oauthParameters = Map(
      "oauth_consumer_key" -> consumerKey,
      "oauth_token" -> token,
      "oauth_signature_method" -> "HMAC-SHA1",
      "oauth_timestamp" -> timestamp,
      "oauth_nonce" -> nonce,
      "oauth_version" -> "1.0"
    )

    val allParameters = parameters ++ oauthParameters

    val baseString = signBaseString(httpMethod, url, allParameters)
    val signingKey = s"${percentEncode(consumerSecret)}&${percentEncode(tokenSecret)}"
    val signature = sign(signingKey, baseString)

    val oauthHeader = oauthParameters + ("oauth_signature" -> percentEncode(signature))
    oauthHeader.map { case (k, v) => s"""$k="$v"""" }.mkString(", ")
  }
}