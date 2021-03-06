// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package lucuma.odb.api.service

import lucuma.odb.api.model._
import lucuma.odb.api.repo.OdbRepo
import lucuma.core.`enum`.ObsStatus
import lucuma.core.math.Epoch
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._

object Init {

  /**
   * Initializes a (presumably) empty ODB with some demo values.
   */
  def initialize[F[_]: Sync](repo: OdbRepo[F]): F[Unit] =
    for {
      p  <- repo.program.insert(
              ProgramModel.Create(
                None,
                Some("Observing Stars in Constellation Orion for No Particular Reason")
              )
            )
      _  <- repo.program.insert(
              ProgramModel.Create(
                None,
                Some("An Empty Placeholder Program")
              )
            )
      t0 <- repo.target.insertSidereal(
              TargetModel.CreateSidereal(
                None,
                List(p.id),
                "Betelgeuse",
                RightAscensionModel.Input.unsafeFromHms("05:55:10.305"),
                DeclinationModel.Input.unsafeFromDms("07:24:25.43"),
                Some(Epoch.J2000),
                Some(ProperVelocityModel.Input.fromMilliarcsecondsPerYear(BigDecimal("27.54"), BigDecimal("11.3"))),
                Some(RadialVelocityModel.Input.fromMetersPerSecond(21884)),
                Some(ParallaxModel.Input.fromMilliarcseconds(BigDecimal("6.55")))
              )
            )
      t1 <- repo.target.insertSidereal(
              TargetModel.CreateSidereal(
                None,
                List(p.id),
                "Rigel",
                RightAscensionModel.Input.unsafeFromHms("05:14:32.272"),
                DeclinationModel.Input.unsafeFromDms("-08:12:05.90"),
                Some(Epoch.J2000),
                Some(ProperVelocityModel.Input.fromMilliarcsecondsPerYear(BigDecimal("1.31"), BigDecimal("0.5"))),
                Some(RadialVelocityModel.Input.fromMetersPerSecond(17687)),
                Some(ParallaxModel.Input.fromMilliarcseconds(BigDecimal("3.78")))
              )
            )
      a0 <- repo.asterism.insert(
              AsterismModel.CreateDefault(
                None,
                List(p.id),
                None,
                Set(t0.id, t1.id)
              )
            )
      _  <- repo.observation.insert(
              ObservationModel.Create(
                None,
                p.id,
                Some("First Observation"),
                Some(a0.id),
                Some(ObsStatus.New)
              )
            )
    } yield ()

}
