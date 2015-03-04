package controllers

import play.api.Play

trait ApplicationConfiguration {

  import play.api.Play.current

  def OPENID_URL = Play.configuration.getString("appdirect.openid-url").get
  def OPENID_REALM = Play.configuration.getString("appdirect.openid-realm")
  def OAUTH_CONSUMER_KEY = Play.configuration.getString("appdirect.oauth-consumer-key").get
  def OAUTH_CONSUMER_SECRET = Play.configuration.getString("appdirect.oauth-consumer-secret").get

}
