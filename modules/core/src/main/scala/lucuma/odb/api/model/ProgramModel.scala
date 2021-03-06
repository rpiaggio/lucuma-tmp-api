// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.odb.api.model

import lucuma.core.util.Gid

import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosLong
import io.circe.Decoder
import io.circe.generic.semiauto._
import monocle.Lens


/**
 * A placeholder Program for now.
 */
final case class ProgramModel(
  id:        ProgramModel.Id,
  existence: Existence,
  name:      Option[String]
)

object ProgramModel extends ProgramOptics {

  final case class Id(value: PosLong) {

    override def toString: String =
      Gid[Id].show(this)
  }

  object Id {
    implicit val GidProgramId: Gid[Id] =
      Gid.instance('p', _.value, apply)
  }

  implicit val TopLevelProgram: TopLevelModel[Id, ProgramModel] =
    TopLevelModel.instance(_.id, ProgramModel.existence)

  /**
   * Program creation input class.
   */
  final case class Create(
    programId: Option[ProgramModel.Id],
    name:      Option[String]
  )

  object Create {

    implicit val DecoderCreate: Decoder[Create] =
      deriveDecoder[Create]

  }

  final case class ProgramEvent (
    id:       Long,
    editType: Event.EditType,
    value:    ProgramModel,
  ) extends Event.Edit[ProgramModel]

  object ProgramEvent {
    def apply(editType: Event.EditType, value: ProgramModel)(id: Long): ProgramEvent =
      ProgramEvent(id, editType, value)
  }


}

trait ProgramOptics { self: ProgramModel.type =>

  val id: Lens[ProgramModel, ProgramModel.Id] =
    Lens[ProgramModel, ProgramModel.Id](_.id)(a => b => b.copy(id = a))

  val existence: Lens[ProgramModel, Existence] =
    Lens[ProgramModel, Existence](_.existence)(a => b => b.copy(existence = a))

  val name: Lens[ProgramModel, Option[String]] =
    Lens[ProgramModel, Option[String]](_.name)(a => b => b.copy(name = a))

}