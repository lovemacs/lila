package controllers

import scala.util.{ Success, Failure }

import akka.pattern.ask
import play.api.http.ContentTypes
import play.api.mvc._
import play.api.templates.Html

import lila.analyse.{ TimeChart, TimePie }
import lila.app._
import lila.game.{ Pov, GameRepo, PgnRepo, PgnDump }
import lila.hub.actorApi.map.Tell
import lila.round.actorApi.AnalysisAvailable
import lila.round.{ RoomRepo, WatcherRoomRepo }
import lila.tournament.{ TournamentRepo, Tournament ⇒ Tourney }
import lila.user.{ UserRepo }
import views._

object Analyse extends LilaController {

  private def env = Env.analyse
  private def bookmarkApi = Env.bookmark.api
  private lazy val pgnDump = new PgnDump(UserRepo.named)
  private lazy val makePgn = pgnDump { gameId ⇒
    Env.api.Net.BaseUrl + routes.Round.watcher(gameId, "white").url
  } _
  private lazy val timeChart = TimeChart(Env.user.usernameOrAnonymous) _

  def computer(id: String, color: String) = Auth { implicit ctx ⇒
    me ⇒
      env.analyser.getOrGenerate(id, me.id, isGranted(_.MarkEngine)) effectFold (
        e ⇒ logerr("[analysis] " + e.getMessage),
        _ ⇒ Env.round.socketHub ! Tell(id, AnalysisAvailable)
      )
      Redirect(routes.Analyse.replay(id, color)).fuccess
  }

  def replay(id: String, color: String) = Open { implicit ctx ⇒
    OptionFuOk(GameRepo.pov(id, color)) { pov ⇒
      PgnRepo get id flatMap { pgnString ⇒
        (WatcherRoomRepo room pov.gameId map { room ⇒
          html.round.watcherRoomInner(room.decodedMessages)
        }) zip
          Env.round.version(pov.gameId) zip
          (bookmarkApi userIdsByGame pov.game) zip
          makePgn(pov.game, pgnString) zip
          (env.analyser get pov.game.id) zip
          (pov.game.tournamentId ?? TournamentRepo.byId) map {
            case (((((roomHtml, version), bookmarkers), pgn), analysis), tour) ⇒
              html.analyse.replay(
                pov,
                analysis.fold(pgn)(a ⇒ Env.analyse.annotator(pgn, a)).toString,
                roomHtml,
                bookmarkers,
                chess.OpeningExplorer openingOf pgnString,
                analysis,
                version,
                tour)
          }
      }
    }
  }

  def stats(id: String) = Open { implicit ctx ⇒
    OptionFuOk(GameRepo game id) { game ⇒
      timeChart(game) map { chart ⇒
        html.analyse.stats(
          game = game,
          timeChart = chart,
          timePies = Pov(game) map { new TimePie(_) })
      }
    }
  }

  def pgn(id: String) = Open { implicit ctx ⇒
    OptionFuResult(GameRepo game id) { game ⇒
      for {
        pgnString ← game.pgnImport.map(_.pgn).fold(PgnRepo get id)(fuccess(_))
        content ← game.pgnImport.map(_.pgn).fold(makePgn(game, pgnString) map (_.toString))(fuccess(_))
        filename ← pgnDump filename game
      } yield Ok(content).withHeaders(
        CONTENT_LENGTH -> content.size.toString,
        CONTENT_TYPE -> ContentTypes.TEXT,
        CONTENT_DISPOSITION -> ("attachment; filename=" + filename)
      )
    }
  }

  def fen(id: String) = Open { implicit ctx ⇒
    OptionOk(GameRepo game id) { game ⇒
      chess.format.Forsyth >> game.toChess
    }
  }
}
