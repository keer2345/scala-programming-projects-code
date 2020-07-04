package retcalc

import scala.reflect.internal.Variances

sealed trait Returns

case class FixedReturns(annualRate: Double) extends Returns
case class VariableReturns(returns: Vector[VariableReturn]) extends Returns {
  def fromUntil(monthIdFrom: String, monthIdUntils: String): VariableReturns =
    VariableReturns(
      returns
        .dropWhile(_.monthId != monthIdFrom)
        .takeWhile(_.monthId != monthIdUntils)
    )
}

case class VariableReturn(monthId: String, monthlyRate: Double)
