package integration

import models._
import scala.util.Try
import scala.xml.{Node, NodeSeq}

object EventFlag extends Enumeration {
  type EventFlag = Value
  val STATELESS,   // Event is for testing/monitoring purposes, nothing must be persisted.
      DEVELOPMENT, // Events generated during development
      NONE         // No event flag provided
        = Value
}

object EventType extends Enumeration {
  type EventType = Value
  val SUBSCRIPTION_ORDER,  // fired by AppDirect when a user buys an app from AppDirect.
      SUBSCRIPTION_CHANGE, // fired by AppDirect when a user upgrades/downgrades/modifies an existing subscription.
      SUBSCRIPTION_CANCEL, // fired by AppDirect when a user cancels a subscription.
      SUBSCRIPTION_NOTICE, // fired by AppDirect when a subscription goes overdue or delinquent.
      USER_ASSIGNMENT,     // fired by AppDirect when a user assigns a user to an app.
      USER_UNASSIGNMENT    // fired by AppDirect when a user unassigns a user from an app.
        = Value
}

object NoticeType extends Enumeration {
  type NoticeType = Value
  val DEACTIVATED,
      REACTIVATED,
      CLOSED,
      UPCOMING_INVOICE
        = Value
}

sealed trait Event
case class SubscriptionOrder(flag: EventFlag.Value, creator: User, company: Company) extends Event
case class SubscriptionChange(flag: EventFlag.Value, creator: User, account: Account, edition: String) extends Event
case class SubscriptionCancel(flag: EventFlag.Value, creator: User, account: Account) extends Event
case class SubscriptionNotice(flag: EventFlag.Value, noticeType: NoticeType.Value, account: Account) extends Event
case class UserAssignment(flag: EventFlag.Value, creator: User, account: Account, user: User) extends Event
case class UserUnassignment(flag: EventFlag.Value, creator: User, account: Account, user: User) extends Event

object Event {

  /*
   * Parses an XML document into one of the Event case classes.
   * Uses for-comprehensions to simplify parsing and validation.
   * Unless all fields are found parsing will fail with "None".
   */
  def apply(xml: NodeSeq): Option[Event] = for {
    eventXml   <- elem(xml \\ "event")
    typeName   <- text(eventXml \ "type")
    eventType  <- Try(EventType.withName(typeName)).toOption
    flagName   <- text(eventXml \ "flag") orElse Some(EventFlag.NONE.toString)
    flag       <- Try(EventFlag.withName(flagName)).toOption orElse Some(EventFlag.NONE)
    payload    <- elem(eventXml \ "payload")
    creatorXml <- elem(eventXml \ "creator") orElse Some(<creator />)
    event      <- parseEventType(eventType, flag, parseUser(creatorXml), payload)
  } yield event

  private def parseEventType(eventType: EventType.Value, flag: EventFlag.Value, creator: Option[User], payload: scala.xml.Node): Option[Event] =
    eventType match {
      case EventType.SUBSCRIPTION_ORDER =>
        require(creator.isDefined, "Missing creator element")
        for {
          company <- parseCompany(payload)
        } yield SubscriptionOrder(flag, creator.get, company)

      case EventType.SUBSCRIPTION_CHANGE =>
        require(creator.isDefined, "Missing creator element")
        for {
          edition <- parseEdition(payload)
          account <- parseAccount(payload)
        } yield SubscriptionChange(flag, creator.get, account, edition)

      case EventType.SUBSCRIPTION_CANCEL =>
        require(creator.isDefined, "Missing creator element")
        for {
          account <- parseAccount(payload)
        } yield SubscriptionCancel(flag, creator.get, account)

      case EventType.SUBSCRIPTION_NOTICE =>
        for {
          account    <- parseAccount(payload)
          typeName   <- text(payload \ "notice" \ "type")
          noticeType <- Try(NoticeType.withName(typeName)).toOption
        } yield SubscriptionNotice(flag, noticeType, account)

      case EventType.USER_ASSIGNMENT =>
        require(creator.isDefined, "Missing creator element")
        for {
          account <- parseAccount(payload)
          user    <- elem(payload \ "user") flatMap parseUser
        } yield UserAssignment(flag, creator.get, account, user)

      case EventType.USER_UNASSIGNMENT =>
        require(creator.isDefined, "Missing creator element")
        for {
          account <- parseAccount(payload)
          user    <- elem(payload \ "user") flatMap parseUser
        } yield UserUnassignment(flag, creator.get, account, user)

      case _ => None
    }

  private def parseUser(node: Node): Option[User] = for {
    email     <- text(node \ "email")
    firstName <- text(node \ "firstName")
    lastName  <- text(node \ "lastName")
    openId    <- text(node \ "openId")
  } yield User(email = email, firstName = firstName, lastName = lastName, openId = openId)

  private def parseCompany(payload: Node): Option[Company] = for {
    edition <- parseEdition(payload)
    company <- elem(payload \ "company")
    name    <- text(company \ "name")
    website <- text(company \ "website")
    uuid    <- text(company \ "uuid")
  } yield Company(edition = edition, name = name, website = website, uuid = uuid)

  private def parseEdition(payload: Node): Option[String] =
    text(payload \ "order" \ "editionCode")

  private def parseAccount(payload: Node): Option[Account] = for {
    id         <- text(payload \ "account" \ "accountIdentifier")
    statusName <- text(payload \ "account" \ "status") orElse Some(AccountStatus.NONE.toString)
    status     <- Try(AccountStatus.withName(statusName)).toOption
  } yield Account(id, status)

  private def text(node: NodeSeq): Option[String] =
    node.headOption map(_.text)

  private def elem(node: NodeSeq): Option[Node] =
    node.headOption

}