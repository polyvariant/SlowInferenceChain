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

object SlowInferenceChainTopLevelReturn {
  implicit val ioSync: Sync[IO] = ???

  object IO {
    def pure[A](a: A): fix.IO[A] = ???
  }

  implicit final class IOOps[A](private val fa: fix.IO[A]) extends AnyVal {
    def flatMap[B](f: A => fix.IO[B]): fix.IO[B] = ???
    def map[B](f: A => B): fix.IO[B] = ???
  }

  private def registerRuntimeTelemetry[F[_]: Sync](
    a: Int
  ): F[Unit] = ???

  private def materializeResult[F[_]: Sync](
    a: Int
  ): F[Int] = ???

  def demo1 =
    registerRuntimeTelemetry(???).flatMap(_ => IO.pure(()))

  def demo2 =
    materializeResult(???).map(x => x)
}
