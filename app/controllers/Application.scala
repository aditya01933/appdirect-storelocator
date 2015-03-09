package controllers

import play.api._
import play.api.mvc._
import play.api.libs.openid._
import play.api.db.slick._
import play.api.libs.concurrent.Execution.Implicits._

import models._

case class PageContext(request: Request[_]) {
  def flash = request.flash
  def userName = request.session.get(PageContext.USER_NAME)
  def openId = request.session.get(PageContext.OPENID)
}

object PageContext {
  val USER_NAME = "userName"
  val OPENID = "openId"
}

object Application extends Controller with ApplicationConfiguration {

  import Play.current

  Logger.info(s"Config: ${OAUTH_CONSUMER_KEY}")

  def index = DBAction { implicit rs =>
    implicit val ctx = PageContext(rs.request)

    val result = for {
      openId    <- ctx.openId
      user      <- Users.findByOpenId(openId)
      companyId <- user.companyId
      company   <- Companies.findById(companyId)
      account   <- Accounts.findById(company.uuid)
      users     = Users.findByCompanyId(companyId)
    } yield Ok(views.html.dashboard(user, company, account, users))

    result getOrElse Ok(views.html.welcome()).withNewSession
  }

  def admin = DBAction { implicit rs =>
    implicit val ctx = PageContext(rs.request)
    Ok(views.html.admin(Companies.all, Users.all, Accounts.all))
  }

  def logout = Action { implicit request =>
    Redirect(routes.Application.index)
      .withNewSession
      .flashing("success" -> s"You have been logged out")
  }

  def login(url: Option[String] = None) = Action.async { implicit request =>
    Logger.info(s"Login: url=$url")
    val openid = url getOrElse OPENID_URL
    val realm = OPENID_REALM orElse Some(routes.Application.index.absoluteURL())
    val callbackUrl = routes.Application.loginVerify.absoluteURL()

    OpenID.redirectURL(openid, callbackUrl, realm = realm) map { url =>
      Redirect(url)
    } recover {
      case t: Throwable =>
        Logger.error("Login failed", t)
        Redirect(routes.Application.index)
          .flashing("error" -> s"Failed to log you in: $t")
    }
  }

  def loginVerify = Action.async { implicit request =>
    OpenID.verifiedId map { info =>
      Logger.info(s"Login: verified openID ${info.id} - ${info.attributes}")
      DB.withSession { implicit session =>
        Users.findByOpenId(info.id) match {
          case Some(user) =>
            Redirect(routes.Application.index)
              .withSession(PageContext.OPENID -> user.openId, PageContext.USER_NAME -> user.name)
              .flashing("success" -> s"You are logged in as ${user.name}")

          case None =>
            Redirect(routes.Application.index)
              .flashing("error" -> s"You do not have an account")
        }
      }

    } recover {
      case t: Throwable =>
        Logger.error("Login verification failed", t)
        Redirect(routes.Application.index)
          .flashing("error" -> s"Failed to log you in: $t")
    }
  }

}