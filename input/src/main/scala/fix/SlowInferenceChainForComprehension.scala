/*
rule = SlowInferenceChain
 */
/*
 * Copyright 2026 Polyvariant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fix

trait OtelJava[F[_]] {
  def underlying: Int
}

object OtelJava {
  def global[F[_]: Sync]: OtelJavaBuilder[F] = ???
}

trait OtelJavaBuilder[F[_]] {
  def toResource: Resource[F, OtelJava[F]]
}

object SlowInferenceChainForComprehension {
  implicit val ioSync: Sync[IO] = ???

  private def registerRuntimeTelemetry[F[_]: Sync](
      a: Int
  ): Resource[F, Unit] = ???

  def demo =
    for {
      otel <- OtelJava.global[IO].toResource
      _ <- registerRuntimeTelemetry(otel.underlying) // assert: SlowInferenceChain
    } yield ()
}
