package lila.app
package templating

import lila.api.Env.{ current ⇒ apiEnv }

import play.api.templates.Html
import scalaz.Zero

object Environment
    extends scalaz.Identitys
    with scalaz.Options
    with scalaz.Booleans
    with StringHelper
    with MarkdownHelper
    with AssetHelper
    with RequestHelper
    with DateHelper
    with NumberHelper
    with PaginatorHelper
    with FormHelper
    with SetupHelper
    with SettingHelper
    with MessageHelper
    with lila.round.RoundHelper
    with AiHelper
    with GameHelper
    with UserHelper
    with ForumHelper
    with I18nHelper
    with BookmarkHelper
    with NotificationHelper
    with SecurityHelper
    with TeamHelper
    with AnalysisHelper
    with RelationHelper
    with TournamentHelper 
    with IRCHelper {

  implicit val LilaHtmlZero = new Zero[Html] {
    val zero = Html("")
  }

  type FormWithCaptcha = (play.api.data.Form[_], lila.common.Captcha)

  def netDomain = apiEnv.Net.Domain
  def netBaseUrl = apiEnv.Net.BaseUrl
  def netPort = apiEnv.Net.Port

  def isProd = apiEnv.isProd

  lazy val siteMenu = new lila.app.ui.SiteMenu(trans)

  lazy val lobbyMenu = new lila.app.ui.LobbyMenu(trans)
}
