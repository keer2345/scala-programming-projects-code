package retcalc

sealed trait Returns

object Returns {

  def fromEquityAndInflationData(equities: Vector[EquityData], inflations: Vector[InflationData]): VariableReturns =
    VariableReturns(
      returns = equities
        .zip(inflations)
        .sliding(2)
        .collect {
          case (prevEquity, prevInflation) +: (equity, inflation) +: Vector() =>
            val inflationRate =
              inflation.value / prevInflation.value
            val totalReturn =
              (equity.value + equity.monthlyDividend) / prevEquity.value
            val realTotalReturn =
              totalReturn - inflationRate

            VariableReturn(equity.monthId, realTotalReturn)
        }
        .toVector
    )

  def monthlyRate(returns: Returns, month: Int): Double =
    returns match {
      case FixedReturns(r) =>
        r / 12
      case VariableReturns(rs) =>
        rs(month % rs.length).monthlyRate
      case OffsetReturns(rs, offset) =>
        monthlyRate(rs, month + offset)
    }
}

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

case class OffsetReturns(orig: Returns, offset: Int) extends Returns
