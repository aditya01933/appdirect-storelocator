package controllers

import java.util
import oauth.signpost.OAuth

import play.api._
import play.api.db.slick._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.oauth._
import play.api.libs.ws._
import play.api.mvc.{Action, Controller, Request}
import play.api.Play.current

import scala.collection.mutable
import scala.concurrent.Future
import scala.xml._

import integration._
import models._

object ErrorCode extends Enumeration {
  type ErrorCode = Value
  val USER_ALREADY_EXISTS, // Used when AppDirect admins try to buy subscriptions for apps they have already purchased directly from the Application Vendor. In this scenario, we'll show users an error message and prompt them to link their accounts.
      USER_NOT_FOUND,      // Used when AppDirect admins try to unassign users not found in the Application Vendor's account.
      ACCOUNT_NOT_FOUND,   // Used when AppDirect admins try to add or remove users from an account not found in the Application Vendor's records.
      MAX_USERS_REACHED,   // Used when AppDirect admins try to assign users beyond the limit of the number of seats available. AppDirect will typically prevent that from happening by monitoring app usage.
      UNAUTHORIZED,        // Returned when users try any action that is not authorized for that particular application. For example, if an application does not allow the original creator to be unassigned.
      OPERATION_CANCELED,  // Returned when a user manually interrupts the operation (clicking cancel on the account creation page, etc.).
      CONFIGURATION_ERROR, // Returned when the vendor endpoint is not currently configured.
      INVALID_RESPONSE,    // Returned when the vendor was unable to process the event fetched from AppDirect.
      UNKNOWN_ERROR        // This error code may be used when none of the other error codes apply.
        = Value
}

object Integration extends Controller {

  def event(url: String) = Action.async { implicit request =>
    Logger.info(s"Event: url=$url")
    OAuthRequest.verify(request) match {
      case Right(_) =>
        OAuthRequest.url(url).get() map { response =>
          Event(response.xml) map { event =>
            Ok(processEvent(event))
          } getOrElse {
            val eventType = (response.xml \\ "event" \ "type").text
            Logger.error(s"Configuration error while handling $eventType for $url")
            Ok(error(ErrorCode.CONFIGURATION_ERROR, s"Unknown event type $eventType"))
          }

        } recover {
          case t: Throwable =>
            Logger.error(s"Error during WS call to $url", t)
            Ok(error(ErrorCode.UNKNOWN_ERROR, s"$t"))
        }
      case Left((actualSignature, expectedSignature)) =>
        Logger.error(s"actual($actualSignature) != expected($expectedSignature)")
        Future.successful(Ok(error(ErrorCode.UNAUTHORIZED, "OAuth verification failed")))
    }
  }

  def processEvent(event: Event): NodeSeq = DB.withSession { implicit session =>
    Logger.info(s"Processing event: $event")
    event match {
      case SubscriptionOrder(flag, creator, company) =>
        if (flag != EventFlag.STATELESS) {
          val savedCompany = company.save
          creator.copy(companyId = savedCompany.id).save
        }
        success(accountId = Some(company.uuid))

      case SubscriptionChange(flag, creator, account, edition) =>
        withCompany(flag, account.id) { company =>
          company.copy(edition = edition).save
          creator.copy(companyId = company.id).save
          success()
        }

      case SubscriptionCancel(flag, creator, account) =>
        withCompany(flag, account.id) { company =>
          for (user <- Users.findByCompanyId(company.id.get))
            user.delete
          company.delete
          success()
        }

      case UserAssignment(flag, creator, account, user) =>
        withCompany(flag, account.id) { company =>
          Users.findByOpenId(user.openId).fold({
            Logger.info(s"Assigning $user to $company")
            user.copy(companyId = company.id).save
            success()
          })({ existingUser =>
            Logger.info(s"User already exists: $user")
            error(ErrorCode.USER_ALREADY_EXISTS, s"$user")
          })
        }

      case UserUnassignment(flag, creator, account, user) =>
        withCompany(flag, account.id) { company =>
          Users.findByOpenId(user.openId).fold({
            Logger.info(s"User not found: $user")
            error(ErrorCode.USER_NOT_FOUND, s"$user")
          })({ existingUser =>
            Logger.info(s"Unassigning $user from $company")
            existingUser.delete
            success()
          })
        }
    }
  }

  def withCompany(flag: EventFlag.Value, accountId: String)(found: Company => NodeSeq)(implicit session: Session): NodeSeq = {
    def notFound = {
      if (flag == EventFlag.STATELESS) Logger.error(s"Account not found: $accountId")
      error(ErrorCode.ACCOUNT_NOT_FOUND, accountId)
    }

    if (flag == EventFlag.STATELESS)
      notFound
    else
      Companies.findByUuid(accountId).fold(notFound)(found)
  }

  def success(message: Option[String] = None, accountId: Option[String] = None): NodeSeq =
    <result>
      <success>true</success>
      {optional(message)(msg => <message>{msg}</message>)}
      {optional(accountId)(id => <accountIdentifier>{id}</accountIdentifier>)}
    </result>

  def error(errorCode: ErrorCode.Value, message: String): NodeSeq =
    <result>
      <success>false</success>
      <errorCode>{errorCode.toString}</errorCode>
      <message>{message}</message>
    </result>

  def optional(value: Option[String])(toXml: String => NodeSeq): NodeSeq =
    value map toXml getOrElse NodeSeq.Empty

}

object OAuthRequest extends ApplicationConfiguration {

  private val CONSUMER_KEY = ConsumerKey(OAUTH_CONSUMER_KEY, OAUTH_CONSUMER_SECRET)
  private val CALCULATOR = OAuthCalculator(CONSUMER_KEY, RequestToken("", ""))

  def url(url: String): WSRequestHolder =
    WS.url(url).sign(CALCULATOR)

  def verify(request: Request[_]): Either[(Option[String], Option[String]), Boolean] = {
    val actualSignature = signature(request.headers.get("Authorization"))
    val expectedSignature = {
      val signedRequest = new WSRequestAdaptor(request)
      CALCULATOR.sign(signedRequest)
      signature(signedRequest.headers.get("Authorization"))
    }

    if (actualSignature == expectedSignature || request.host == "localhost")
      Right(true)
    else
      Left((actualSignature, expectedSignature))
  }

  private def signature(header: Option[String]): Option[String] =
    for (oauthHeader <- header)
      yield OAuth.oauthHeaderToParamsMap(oauthHeader).getFirst(OAuth.OAUTH_SIGNATURE)

  private class WSRequestAdaptor(request: Request[_]) extends play.api.libs.ws.WSRequest {
    val headers = mutable.Map[String, String]()
    override def allHeaders: Map[String, Seq[String]] = request.headers.toMap
    override def url: String = s"${protocol}://${request.host}${request.uri}"
    private def protocol = s"http${if (request.secure) "s" else ""}"
    override def getBody: Option[Array[Byte]] = None
    override def queryString: Map[String, Seq[String]] = request.queryString
    override def method: String = request.method
    override def header(name: String): Option[String] = request.headers.get(name)
    override def setHeader(name: String, value: String): WSRequest = {
      headers put (name, value)
      this
    }

    /* Unimplemented. */
    override def addHeader(name: String, value: String): WSRequest = ???
    override def setUrl(url: String): WSRequest = ???
    override def setHeaders(hdrs: util.Map[String, util.Collection[String]]): WSRequest = ???
    override def setHeaders(hdrs: Map[String, Seq[String]]): WSRequest = ???
    override def setQueryString(queryString: Map[String, Seq[String]]): WSRequest = ???
  }

}