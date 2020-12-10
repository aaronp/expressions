package expressions

import expressions.ast.ExpressionAst.PropertyPath

package object ast {

  type Term      = Either[PropertyPath, Value]
  type PropTerm  = Left[PropertyPath, Value]
  type ValueTerm = Right[PropertyPath, Value]

}
