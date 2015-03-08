import controllers._
import org.specs2.mutable._
import org.specs2.runner._
import play.api.libs.oauth.{RequestToken, OAuthCalculator}
import play.api.test.Helpers._
import play.api.test._

import models._
import integration._

class EventSpec extends Specification {

  def load(path: String): scala.xml.NodeSeq =
    scala.xml.XML.loadString(scala.io.Source.fromFile(path).mkString)

  val CREATOR = User(
    email = "test-email+creator@appdirect.com",
    firstName = "DummyCreatorFirst",
    lastName = "DummyCreatorLast",
    openId = "https://www.appdirect.com/openid/id/ec5d8eda-5cec-444d-9e30-125b6e4b67e2"
  )

  val USER = User(
    email = "test-email@appdirect.com",
    firstName = "DummyFirst",
    lastName = "DummyLast",
    openId = "https://www.appdirect.com/openid/id/ec5d8eda-5cec-444d-9e30-125b6e4b67e2"
  )

  val ACCOUNT = Account("dummy-account", AccountStatus.ACTIVE)

  "Event Request parsing" should {

    "parse Subscription Order" in {
      val xml = load("test/xml/dummyOrder.xml")

      val maybe = Event(xml)
      maybe mustNotEqual None
      maybe.get must beAnInstanceOf[SubscriptionOrder]

      val event = maybe.get.asInstanceOf[SubscriptionOrder]
      event.company.name mustEqual "Example Company Name"
      event.company.website mustEqual "http://www.example.com"
      event.company.edition mustEqual "BASIC"

      event.creator mustEqual CREATOR
    }

    "parse Subscription Change" in {
      val xml = load("test/xml/dummyChange.xml")

      val maybe = Event(xml)
      maybe mustNotEqual None
      maybe.get must beAnInstanceOf[SubscriptionChange]

      val event = maybe.get.asInstanceOf[SubscriptionChange]
      event.account mustEqual ACCOUNT
      event.edition mustEqual "PREMIUM"
      event.creator mustEqual CREATOR
    }

    "parse Subscription Cancel" in {
      val xml = load("test/xml/dummyCancel.xml")

      val maybe = Event(xml)
      maybe mustNotEqual None
      maybe.get must beAnInstanceOf[SubscriptionCancel]

      val event = maybe.get.asInstanceOf[SubscriptionCancel]
      event.account mustEqual ACCOUNT
      event.creator mustEqual CREATOR
    }

    "parse Subscription Notice" in {
      val xml = load("test/xml/dummyNotice.xml")

      val maybe = Event(xml)
      maybe mustNotEqual None
      maybe.get must beAnInstanceOf[SubscriptionNotice]

      val event = maybe.get.asInstanceOf[SubscriptionNotice]
      event.account mustEqual Account("MY_ACCOUNT", AccountStatus.FREE_TRIAL_EXPIRED)
      event.noticeType mustEqual NoticeType.DEACTIVATED
    }

    "parse User Assignment" in {
      val xml = load("test/xml/dummyAssign.xml")

      val maybe = Event(xml)
      maybe mustNotEqual None
      maybe.get must beAnInstanceOf[UserAssignment]

      val event = maybe.get.asInstanceOf[UserAssignment]
      event.account mustEqual ACCOUNT
      event.creator mustEqual CREATOR
      event.user mustEqual USER
    }

    "parse User Unassignment" in {
      val xml = load("test/xml/dummyUnassign.xml")

      val maybe = Event(xml)
      maybe mustNotEqual None
      maybe.get must beAnInstanceOf[UserUnassignment]

      val event = maybe.get.asInstanceOf[UserUnassignment]
      event.account mustEqual ACCOUNT
      event.creator mustEqual CREATOR
      event.user mustEqual USER
    }

    "gracefully handle missing creator element" in {
      val xml = load("test/xml/dummyOrderWithMissingCreator.xml")

      Event(xml) must throwA[IllegalArgumentException]("requirement failed: Missing creator element")
    }

    "gracefully handle unknown event types" in {
      val xml = load("test/xml/unknownEventType.xml")

      Event(xml) mustEqual None
    }

    "gracefully handle unknown event flags" in {
      val xml = load("test/xml/unknownEventFlag.xml")

      val maybe = Event(xml)
      maybe mustNotEqual None
      maybe.get must beAnInstanceOf[SubscriptionOrder]
    }

  }
}
