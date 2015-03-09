package models

import play.api.db.slick.Config.driver.simple._
import scala.slick.lifted.Tag

case class User(
  id: Option[Long] = None,
  email: String = "",
  firstName: String = "",
  lastName: String = "",
  openId: String,
  companyId: Option[Long] = None,
  creator: Boolean = false) {

  def save(implicit s: Session): User = Users.save(this)
  def delete(implicit s: Session) = Users.delete(this)

  def name = Seq(
    s"${firstName} ${lastName}",
    email
  ) map (_.trim) find (_.nonEmpty) getOrElse (openId.replaceAll(".*/", ""))

}

object Users {

  class UserTable(tag: Tag) extends Table[User](tag, "USER") {
    def id        = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email     = column[String]("email", O.NotNull)
    def firstName = column[String]("firstName", O.NotNull)
    def lastName  = column[String]("lastName", O.NotNull)
    def openId    = column[String]("openId", O.NotNull)
    def companyId = column[Long]("companyId", O.Nullable)
    def creator   = column[Boolean]("creator", O.Nullable)
    def * = (id.?, email, firstName, lastName, openId, companyId.?, creator) <> (User.tupled, User.unapply _)
  }

  val table = TableQuery[UserTable]

  def findById(id: Long)(implicit s: Session): Option[User] =
    table.filter(_.id === id).firstOption

  def findByOpenId(openId: String)(implicit s: Session): Option[User] =
    table.filter(_.openId === openId).firstOption

  def findByCompanyId(companyId: Long)(implicit s: Session): Seq[User] =
    table.filter(_.companyId === companyId).list

  def all()(implicit s: Session): Seq[User] =
    table.list

  def save(user: User)(implicit s: Session): User = {
    require(user.openId nonEmpty, "Invalid user ID")
    val userId: Option[Long] = user.id orElse Users.findByOpenId(user.openId).flatMap(_.id)
    userId match {
      case Some(id) =>
        Users.table.filter(_.id === id).update(user.copy(id = Some(id)))
        user
      case None =>
        val id = Users.table.insert(user)
       user.copy(id = Some(id))
    }
  }

  def delete(user: User)(implicit s: Session) =
    table.filter(_.id === user.id).delete

}

case class Company(id: Option[Long] = None, name: String, website: String, edition: String, uuid: String) {

  def save(implicit s: Session): Company = Companies.save(this)
  def delete(implicit s: Session) = Companies.delete(this)

}

object Companies {

  class CompanyTable(tag: Tag) extends Table[Company](tag, "COMPANY") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.NotNull)
    def website = column[String]("website", O.NotNull)
    def edition = column[String]("edition", O.NotNull)
    def uuid = column[String]("uuid", O.NotNull)
    def * = (id.?, name, website, edition, uuid) <>(Company.tupled, Company.unapply _)
  }

  val table = TableQuery[CompanyTable]

  def findById(id: Long)(implicit s: Session): Option[Company] =
    table.filter(_.id === id).firstOption

  def findByUuid(uuid: String)(implicit s: Session): Option[Company] =
    table.filter(_.uuid === uuid).firstOption

  def all()(implicit s: Session): Seq[Company] =
    table.list

  def save(company: Company)(implicit s: Session): Company = {
    val companyId = company.id orElse findByUuid(company.uuid).flatMap(_.id)
    companyId match {
      case Some(id) =>
        table.filter(_.id === id).update(company)
        company
      case None =>
        val id = table.insert(company)
        company.copy(id = Some(id))
    }
  }

  def delete(company: Company)(implicit s: Session) =
    table.filter(_.id === company.id).delete

}

object AccountStatus extends Enumeration {
  type AccountStatus = Value
  val FREE_TRIAL,
      FREE_TRIAL_EXPIRED,
      ACTIVE,
      SUSPENDED,
      CANCELLED,
      NONE
        = Value
}

case class Account(id: String, status: AccountStatus.Value) {

  def save(implicit s: Session): Account = Accounts.save(this)
  def delete(implicit s: Session) = Accounts.delete(this)

}

object Accounts {

  implicit val accountStatusMapper = MappedColumnType.base[AccountStatus.Value, Int](
    { status => status.id },
    { int => AccountStatus(int) }
  )

  class AccountTable(tag: Tag) extends Table[Account](tag, "ACCOUNT") {
    def id = column[String]("id", O.PrimaryKey)
    def status = column[AccountStatus.Value]("status", O.Nullable)
    def * = (id, status) <>(Account.tupled, Account.unapply _)
  }

  val table = TableQuery[AccountTable]

  def findById(id: String)(implicit s: Session): Option[Account] =
    table.filter(_.id === id).firstOption

  def all()(implicit s: Session): Seq[Account] =
    table.list

  def save(account: Account)(implicit s: Session): Account = {
    findById(account.id) match {
      case Some(_) => table.filter(_.id === account.id).update(account)
      case None    => table.insert(account)
    }
    account
  }

  def delete(account: Account)(implicit s: Session) =
    table.filter(_.id === account.id).delete

}