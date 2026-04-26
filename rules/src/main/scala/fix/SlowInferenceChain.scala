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

import scalafix.lint.Diagnostic
import scalafix.lint.LintSeverity
import scalafix.v1._

import scala.meta._

final case class SlowInferenceChainFinding(call: Tree, suggestion: String)

class SlowInferenceChain extends SemanticRule("SlowInferenceChain") {

  override def fix(
    implicit doc: SemanticDocument
  ): Patch = {
    val findings =
      doc
        .tree
        .collect {
          case call if isCandidateInvocation(call) && isChained(call) => diagnosticFor(call)
        }
        .flatten

    // if (findings.nonEmpty) {
    // debug(s"emitting ${findings.size} diagnostics")
    // findings.foreach { finding =>
    //   debug(
    //     s"error at ${finding.call.pos.startLine + 1}:${finding.call.pos.startColumn + 1} for `${finding.call.syntax}`"
    //   )
    // }
    // }

    findings.map(toPatch).asPatch
  }

  private def diagnosticFor(
    tree: Tree
  )(
    implicit
    doc: SemanticDocument
  ): Option[SlowInferenceChainFinding] = invocationSymbol(tree)
    .flatMap(_.info)
    .filter(isRiskyMethod)
    .map(_ => SlowInferenceChainFinding(tree, suggestion(tree)))

  private def toPatch(finding: SlowInferenceChainFinding): Patch = Patch.lint(
    Diagnostic(
      "",
      s"Chaining on this call can trigger very slow Scala 3 inference when an omitted type argument only affects the result through implicit/given evidence. Add an explicit type argument, e.g. `${finding.suggestion}`.",
      finding.call.pos,
      "Add an explicit type argument to the call that introduces the value being chained on.",
      LintSeverity.Error,
    )
  )

  private def isCandidateInvocation(tree: Tree): Boolean =
    tree match {
      case Term.Apply.After_4_6_0(fun, _) => !hasExplicitTypeArguments(fun)
      case _: Term.Select                 => true
      case _                              => false
    }

  private def isChained(tree: Tree): Boolean = tree.parent.exists {
    case Term.Select(`tree`, _)          => true
    case Enumerator.Generator(_, `tree`) => true
    case _                               => false
  }

  private def invocationSymbol(
    tree: Tree
  )(
    implicit
    doc: SemanticDocument
  ): Option[Symbol] = invokedTerm(tree)
    .map(_.symbol)
    .filterNot(_.isNone)

  private def invokedTerm(tree: Tree): Option[Term] =
    tree match {
      case Term.Apply.After_4_6_0(fun, _) => Some(stripTypeApplication(fun))
      case select: Term.Select            => Some(select)
      case _                              => None
    }

  private def stripTypeApplication(term: Term): Term =
    term match {
      case Term.ApplyType.After_4_6_0(inner, _) => stripTypeApplication(inner)
      case other                                => other
    }

  private def hasExplicitTypeArguments(term: Term): Boolean =
    term match {
      case _: Term.ApplyType              => true
      case Term.Apply.After_4_6_0(fun, _) => hasExplicitTypeArguments(fun)
      case Term.Select(qual, _)           => hasExplicitTypeArguments(qual)
      case _                              => false
    }

  private def isRiskyMethod(info: SymbolInformation): Boolean =
    info.signature match {
      case MethodSignature(typeParameters, parameterLists, returnType) =>
        typeParameters.exists { tparam =>
          val symbol = tparam.symbol.normalized

          semanticTypeMentions(returnType, symbol) &&
          !explicitParameters(parameterLists).exists(parameterMentions(_, symbol)) &&
          implicitParameters(parameterLists).exists(parameterMentions(_, symbol))
        }
      case _ => false
    }

  private def explicitParameters(
    parameterLists: List[List[SymbolInformation]]
  ): List[SymbolInformation] = parameterLists.flatten.filterNot(isImplicitEvidence)

  private def implicitParameters(
    parameterLists: List[List[SymbolInformation]]
  ): List[SymbolInformation] = parameterLists.flatten.filter(isImplicitEvidence)

  private def isImplicitEvidence(param: SymbolInformation): Boolean = param.isImplicit

  private def parameterMentions(
    param: SymbolInformation,
    symbol: Symbol,
  ): Boolean =
    param.signature match {
      case ValueSignature(tpe) => semanticTypeMentions(tpe, symbol)
      case _                   => false
    }

  private def semanticTypeMentions(
    tpe: SemanticType,
    symbol: Symbol,
  ): Boolean = containsSymbol(tpe, symbol.normalized)

  private def containsSymbol(value: Any, symbol: Symbol): Boolean =
    value match {
      case sym: Symbol         => sym.normalized == symbol
      case ValueSignature(tpe) => containsSymbol(tpe, symbol)
      case MethodSignature(typeParameters, parameterLists, returnType) =>
        typeParameters.exists(containsSymbol(_, symbol)) ||
        parameterLists.exists(_.exists(containsSymbol(_, symbol))) ||
        containsSymbol(returnType, symbol)
      case TypeSignature(typeParameters, lowerBound, upperBound) =>
        typeParameters.exists(containsSymbol(_, symbol)) ||
        containsSymbol(lowerBound, symbol) ||
        containsSymbol(upperBound, symbol)
      case info: SymbolInformation =>
        info.symbol.normalized == symbol || containsSymbol(info.signature, symbol)
      case tpe: SemanticType   => tpe.productIterator.exists(containsSymbol(_, symbol))
      case values: Iterable[_] => values.iterator.exists(containsSymbol(_, symbol))
      case value: Option[_]    => value.exists(containsSymbol(_, symbol))
      case _                   => false
    }

  private def suggestion(tree: Tree): String =
    tree match {
      case apply: Term.Apply =>
        s"${stripTypeApplication(apply.fun).syntax}[...]${apply.argClause.syntax}"
      case select: Term.Select => s"${select.syntax}[...]"
      case _                   => s"${tree.syntax}[...]"
    }

  private def debug(message: String): Unit = System.err.println(s"[SlowInferenceChain] $message")
}
