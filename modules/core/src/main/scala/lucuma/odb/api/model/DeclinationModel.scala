// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.odb.api.model

import lucuma.core.math.{Angle, Declination}
import lucuma.core.optics.SplitMono
import lucuma.core.util.{Display, Enumerated}
import cats.syntax.option._

import io.circe.Decoder
import io.circe.generic.semiauto._
import monocle.Prism


object DeclinationModel {
  sealed abstract class Units(
    val angleUnit: AngleModel.Units
  ) extends Product with Serializable {

    private def fromA[A](m: SplitMono[Angle, A]): Prism[A, Declination] =
      Prism { (a: A) =>
        Declination.fromAngle.getOption(m.reverseGet(a))
      }(d => m.get(d.toAngle))

    val long: Prism[Long, Declination] =
      fromA(angleUnit.signedLong)

    private def range[A](m: SplitMono[Angle, A]): String =
      s"[${m.get(Angle.Angle270)} - ${m.get(Angle.Angle90)}]"

    def readLong(l: Long): ValidatedInput[Declination] =
      long.getOption(l).toValidNec(
        InputError.fromMessage(
          s"Could not read $l ${angleUnit.abbreviation} as Declination in range ${range(angleUnit.signedLong)}"
        )
      )

    val decimal: Prism[BigDecimal, Declination] =
      fromA(angleUnit.signedDecimal)

    def readDecimal(b: BigDecimal): ValidatedInput[Declination] =
      decimal.getOption(b).toValidNec(
        InputError.fromMessage(
          s"Could not read $b ${angleUnit.abbreviation} as Declination in range ${range(angleUnit.signedDecimal)}"
        )
      )

  }

  object Units {

    case object Microarcseconds extends Units(AngleModel.Units.Microarcseconds)
    case object Degrees         extends Units(AngleModel.Units.Degrees)

    implicit val EnumeratedDeclinationUnits: Enumerated[Units] =
      Enumerated.of(Microarcseconds, Degrees)

    implicit val DisplayDeclinationUnits: Display[Units] =
      Display.by(_.angleUnit.abbreviation, _.angleUnit.name)

  }

  final case class LongInput(
    value: Long,
    units: Units
  ) {

    val read: ValidatedInput[Declination] =
      units.readLong(value)

  }

  object LongInput {

    implicit val DecoderLongInput: Decoder[LongInput] =
      deriveDecoder[LongInput]

  }

  final case class DecimalInput(
    value: BigDecimal,
    units: Units
  ) {

    val read: ValidatedInput[Declination] =
      units.readDecimal(value)

  }

  object DecimalInput {

    implicit val DecoderDecimalInput: Decoder[DecimalInput] =
      deriveDecoder[DecimalInput]

  }

  def readDms(s: String): ValidatedInput[Declination] =
    Declination
      .fromStringSignedDMS
      .getOption(s)
      .toValidNec(
        InputError.fromMessage(s"Could not parse $s as a DMS string.")
      )

  def writeDms(d: Declination): String =
    Declination
      .fromStringSignedDMS
      .reverseGet(d)

  final case class Input(
    microarcseconds: Option[Long],
    degrees:         Option[BigDecimal],
    dms:             Option[String],
    fromLong:        Option[LongInput],
    fromDecimal:     Option[DecimalInput]
  ) {

    import Units._

    val toDeclination: ValidatedInput[Declination] =
      ValidatedInput.requireOne("declination",
        microarcseconds.map(Microarcseconds.readLong),
        degrees        .map(Degrees.readDecimal),
        dms            .map(readDms),
        fromLong       .map(_.read),
        fromDecimal    .map(_.read)
      )
  }

  object Input {

    val Empty: Input =
      Input(None, None, None, None, None)

    def fromMicroarcseconds(l: Long): Input =
      Empty.copy(microarcseconds = Some(l))

    def fromDms(s: String): Input =
      Empty.copy(dms = Some(s))

    implicit val DecoderInput: Decoder[Input] =
      deriveDecoder[Input]
  }
}
