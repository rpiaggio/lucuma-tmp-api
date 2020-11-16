// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.odb.api.model

import lucuma.odb.api.model.json.targetmath._
import lucuma.core.math.{Angle, HourAngle, RightAscension}
import lucuma.core.optics.SplitMono
import lucuma.core.util.{Display, Enumerated}
import cats.syntax.option._
import cats.syntax.validated._

import io.circe.Decoder
import io.circe.generic.semiauto._

object RightAscensionModel {

  sealed abstract class Units(
    val angleUnit: AngleModel.Units
  ) extends Product with Serializable {

    private def angleToRightAscension[A](m: SplitMono[Angle, A]): SplitMono[RightAscension, A] =
      m.imapA(
        a => RightAscension(HourAngle.fromMicroseconds((a.toMicroarcseconds + 7L)/15L)),
        _.toAngle
      )

    val long: SplitMono[RightAscension, Long] =
      angleToRightAscension(angleUnit.unsignedLong)

    def readLong(value: Long): ValidatedInput[RightAscension] =
      long.reverseGet(value).validNec[InputError]

    val decimal: SplitMono[RightAscension, BigDecimal] =
      angleToRightAscension(angleUnit.unsignedDecimal)

    def readDecimal(value: BigDecimal): ValidatedInput[RightAscension] =
      decimal.reverseGet(value).validNec[InputError]

  }

  object Units {

    case object Microarcseconds extends Units(AngleModel.Units.Microarcseconds)
    case object Degrees         extends Units(AngleModel.Units.Degrees)
    case object Hours           extends Units(AngleModel.Units.Hours)

    implicit val EnumeratedRightAscensionUnits: Enumerated[Units] =
      Enumerated.of(Microarcseconds, Degrees, Hours)

    implicit val DisplayRightAscensionUnits: Display[Units] =
      Display.by(_.angleUnit.abbreviation, _.angleUnit.name)

  }

  implicit val NumericUnitsRightAscension: NumericUnits[RightAscension, Units] =
    NumericUnits.fromRead(_.readLong(_), _.readDecimal(_))

  def readHms(s: String): ValidatedInput[RightAscension] =
    RightAscension
      .fromStringHMS
      .getOption(s)
      .toValidNec(
        InputError.fromMessage(s"Could not parse $s as an HMS string.")
      )

  def writeHms(r: RightAscension): String =
    RightAscension
      .fromStringHMS
      .reverseGet(r)

  final case class Input(
    microarcseconds: Option[Long],
    degrees:         Option[BigDecimal],
    hours:           Option[BigDecimal],
    hms:             Option[RightAscension],
    fromLong:        Option[NumericUnits.LongInput[Units]],
    fromDecimal:     Option[NumericUnits.DecimalInput[Units]]
  ) {

    import Units._

    val toRightAscension: ValidatedInput[RightAscension] =
      ValidatedInput.requireOne("right ascension",
        microarcseconds.map(Microarcseconds.readLong),
        degrees        .map(Degrees.readDecimal),
        hours          .map(Hours.readDecimal),
        hms            .map(_.validNec),
        fromLong       .map(_.read),
        fromDecimal    .map(_.read)
      )
  }

  object Input {

    val Empty: Input =
      Input(None, None, None, None, None, None)

    def fromMicroarcseconds(value: Long): Input =
      Empty.copy(microarcseconds = Some(value))

    def fromHms(s: String): ValidatedInput[Input] =
      readHms(s).map(hms => Empty.copy(hms = Some(hms)))

    def unsafeFromHms(s: String): Input =
      fromHms(s).valueOr(err => throw InputError.Exception(err))

    implicit val DecoderInput: Decoder[Input] =
      deriveDecoder[Input]

  }

}
